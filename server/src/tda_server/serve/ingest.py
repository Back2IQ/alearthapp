"""Production HTTP ingest for phone crowdsourcing signals (POST /trigger, /ping).

Mirrors serve/http_proxy.py: a stdlib ThreadingHTTPServer in a background
thread next to the WS bridge. Accepted signals are handed to the asyncio
pipeline via injected sync callbacks (see TriggerIngestor / start_ingest,
Task 3). received_ms and attest_ok are set server-side, never trusted from
the client body.
"""
from __future__ import annotations

from tda_server.p0b.signals import ActivePing, PhoneTrigger


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
