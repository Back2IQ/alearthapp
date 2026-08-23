"""Production HTTP ingest for phone crowdsourcing signals (POST /trigger, /ping).

Mirrors serve/http_proxy.py: a stdlib ThreadingHTTPServer in a background
thread next to the WS bridge. Accepted signals are handed to the asyncio
pipeline via injected sync callbacks (see TriggerIngestor / start_ingest,
Task 3). received_ms and attest_ok are set server-side, never trusted from
the client body.
"""
from __future__ import annotations

import json
import logging
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable

from tda_server.p0b.gateway import (
    AttestationVerifier, RateLimiter, TriggerGate, evaluate_trigger,
)
from tda_server.p0b.signals import ActivePing, PhoneTrigger, serialize_ping, serialize_trigger

log = logging.getLogger(__name__)

_MAX_BODY_BYTES = 2048


def parse_trigger_body(raw: dict, received_ms: int) -> PhoneTrigger:
    """Build a PhoneTrigger from a JSON body. attest_ok is always False here;
    the server sets the authoritative value in evaluate_trigger. Raises
    ValueError on any missing or non-numeric field."""
    try:
        return PhoneTrigger(
            device_hash=str(raw["device_hash"]),
            cell=str(raw["cell"]),
            trigger_ms=int(raw["trigger_ms"]),
            clock_unc_ms=int(raw["clock_unc_ms"]),
            received_ms=received_ms,
            attest_ok=False,
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"bad trigger body: {exc}") from exc


def parse_ping_body(raw: dict, received_ms: int) -> ActivePing:
    """Build an ActivePing from a JSON body. Raises ValueError on bad input."""
    try:
        return ActivePing(
            device_hash=str(raw["device_hash"]),
            cell=str(raw["cell"]),
            ping_ms=int(raw["ping_ms"]),
            received_ms=received_ms,
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"bad ping body: {exc}") from exc


class TriggerIngestor:
    def __init__(
        self,
        *,
        verifier: AttestationVerifier,
        limiter: RateLimiter,
        gate: TriggerGate,
        submit_trigger: Callable[[dict], None],
        submit_ping: Callable[[dict], None],
        ping_limiter: RateLimiter,
    ) -> None:
        self.verifier = verifier
        self.limiter = limiter
        self.gate = gate
        self.submit_trigger = submit_trigger
        self.submit_ping = submit_ping
        self.ping_limiter = ping_limiter

    def handle_trigger(self, raw: dict, token: str, received_ms: int) -> None:
        t = parse_trigger_body(raw, received_ms)          # ValueError -> 400
        accepted = evaluate_trigger(t, token, verifier=self.verifier,
                                    limiter=self.limiter, gate=self.gate)
        if accepted is not None:
            self.submit_trigger(serialize_trigger(accepted))

    def handle_ping(self, raw: dict, received_ms: int) -> None:
        p = parse_ping_body(raw, received_ms)             # ValueError -> 400
        if self.ping_limiter.allow(p.device_hash, received_ms):
            self.submit_ping(serialize_ping(p))


def _make_handler(ingestor: TriggerIngestor, clock: Callable[[], int]):
    class _Handler(BaseHTTPRequestHandler):
        def _send(self, code: int, body: bytes = b"") -> None:
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if body:
                self.wfile.write(body)

        def _read_json(self) -> dict:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > _MAX_BODY_BYTES:
                raise ValueError("bad content length")
            return json.loads(self.rfile.read(length).decode("utf-8"))

        def do_POST(self) -> None:  # noqa: N802 - stdlib naming
            path = self.path.split("?", 1)[0].rstrip("/")
            try:
                raw = self._read_json()
                now = clock()
                if path == "/trigger":
                    ingestor.handle_trigger(raw, token=str(raw.get("attest_token", "")),
                                            received_ms=now)
                elif path == "/ping":
                    ingestor.handle_ping(raw, received_ms=now)
                else:
                    self._send(404, b'{"error":"not found"}')
                    return
            except ValueError:
                self._send(400, b'{"error":"bad request"}')
                return
            except Exception:  # noqa: BLE001 - never crash the ingest thread
                self._send(500, b'{"error":"internal"}')
                return
            self._send(202)

        def do_GET(self) -> None:  # noqa: N802
            if self.path.rstrip("/") in ("", "/health"):
                self._send(200, b'{"ok":true}')
            else:
                self._send(404, b'{"error":"not found"}')

        def log_message(self, *args) -> None:  # keep the console quiet
            pass

    return _Handler


def start_ingest(host: str, port: int, ingestor: TriggerIngestor,
                 clock: Callable[[], int] | None = None) -> ThreadingHTTPServer:
    """Start the ingest HTTP server in a daemon thread and return it.
    `clock` returns the current epoch-ms used to stamp received_ms; defaults
    to time.time()-based ms (injectable for tests)."""
    if clock is None:
        import time
        clock = lambda: int(time.time() * 1000)  # noqa: E731
    server = ThreadingHTTPServer((host, port), _make_handler(ingestor, clock))
    threading.Thread(target=server.serve_forever, name="tda-ingest", daemon=True).start()
    log.info("TDA ingest on http://%s:%d (/trigger, /ping)", host, port)
    return server
