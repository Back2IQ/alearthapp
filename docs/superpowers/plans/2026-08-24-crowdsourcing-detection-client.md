# Crowdsourcing Detection Client + HTTP Trigger-Ingest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Android-Handy, das lädt und ruhig liegt, erkennt Boden-Erschütterungen selbst und meldet sie anonym per HTTP an den Python-Server, dessen bereits gebauter p0b-Detektor daraus Sekunden-EEW ableitet.

**Architecture:** Serverseitig ein schlanker stdlib-HTTP-Eingang (`serve/ingest.py`, Muster wie `serve/http_proxy.py`), der `POST /trigger` und `POST /ping` über die bestehende, jetzt geteilte Gateway-Annahmelogik in die vorhandenen p0b-Streams schiebt. Clientseitig reine, testbare Kotlin-Erkennungsfunktionen (Ruhe/Rütteln/Rasterzelle/anonyme Kennung) plus ein an das Ladekabel gekoppelter Foreground-Service und ein okhttp-Melder — alles im bestehenden `app.alearthapp`-Stil.

**Tech Stack:** Python 3.12 (stdlib `http.server`, keine neue Abhängigkeit), pytest. Kotlin/Android (JUnit 4 JVM-Unit-Tests, okhttp wie `PushRegistrar`, Foreground-Service wie `AlarmService`).

## Global Constraints

- **Draht-Vertrag verbindlich** (`server/src/tda_server/p0b/signals.py`): `PhoneTrigger(device_hash, cell, trigger_ms, clock_unc_ms, received_ms, attest_ok)`, `ActivePing(device_hash, cell, ping_ms, received_ms)`; Serialisierung ist `dict[str, str]` (alle Werte Strings). **`received_ms` und `attest_ok` setzt der Server**, nie der Client.
- **Rasterzelle:** `coarsen_cell(lat, lon) = "d{floor(lat/0.1)}_{floor(lon/0.1)}"` (0,1°-Raster). Die Kotlin-Variante MUSS byte-identische Strings erzeugen (Paritätstest gegen `coarsen_cell(41.02, 28.97) == "d410_289"` und `coarsen_cell(41.08, 28.93) == "d410_289"`).
- **Nur die Zell-ID verlässt das Gerät**, nie Rohkoordinaten.
- **Keine neue Python-Abhängigkeit** (stdlib `http.server`; `pyproject.toml` unverändert).
- **Opt-in:** `Prefs.crowdsourcingEnabled` Default **false**; der Service ist No-op solange aus.
- **Anonyme Kennung rotiert täglich** und bei Neuinstallation; kein Konto, keine Ad-ID/IMEI.
- **`clock_unc_ms` Default 1000** (unter dem Gateway-Limit `TriggerGate.clock_unc_max_ms = 2000`).
- **Python-Stil:** Zeilenlänge 100, Typannotationen, Code/Kommentare Englisch, TDD (rot → grün → commit).
- **Kotlin-Stil:** wie umliegender Code (`object` für zustandslose Helfer, `Prefs`-`var`-Muster, KDoc-Kommentare auf Deutsch wie im Bestand).
- **Git:** Arbeit bleibt auf Branch `feat/crowdsourcing-client`; **kein Push, kein Merge nach main ohne ausdrückliche Freigabe**. Commit-Messages enden mit `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Arbeitsverzeichnis Server: `tda/server/` (Tests: `.venv/Scripts/python -m pytest`). Arbeitsverzeichnis App: `tda/android/` (Tests: `./gradlew testDebugUnitTest`).

---

## File Structure

**Server (neu/geändert):**
- `server/src/tda_server/p0b/gateway.py` — MODIFY: reine `evaluate_trigger(...)` extrahieren, `run_trigger_gateway` darauf umstellen.
- `server/src/tda_server/serve/ingest.py` — CREATE: Parser, `TriggerIngestor`, HTTP-Handler, `start_ingest`.
- `server/scripts/serve_local.py` — MODIFY: Ingest neben Proxy/WS starten.
- `server/tests/test_ingest.py` — CREATE.
- `server/tests/test_p0b_gateway.py` — MODIFY: Test für `evaluate_trigger`.

**Client (neu/geändert), Paket `app.alearthapp`:**
- `.../GeoCell.kt` — CREATE: `coarsenCell`.
- `.../AnonDeviceId.kt` — CREATE: rotierende Kennung (reine Logik + Prefs-Anbindung).
- `.../StillnessDetector.kt` — CREATE: Ruhe-Erkennung (rein).
- `.../ShakeDetector.kt` — CREATE: Rütteln-Erkennung (rein).
- `.../CrowdReport.kt` — CREATE: reine JSON-Body-Bauer + okhttp-Melder (`TriggerReporter`, `ActivePinger`).
- `.../QuakeSensorService.kt` — CREATE: Foreground-Service, verdrahtet Detektoren + Melder.
- `.../PowerConnectionReceiver.kt` — CREATE: startet/stoppt Service am Ladekabel.
- `.../Prefs.kt` — MODIFY: `crowdsourcingEnabled`, anonyme-ID-Speicher, `lastPingMs`.
- `android/app/src/main/AndroidManifest.xml` — MODIFY: Service + Receiver + `ACCESS_COARSE_LOCATION`.
- `.../MainActivity.kt` bzw. Einstellungs-/Onboarding-Anbindung — MODIFY: Schalter + Service-Start.
- `android/app/src/main/res/values*/strings.xml` — MODIFY: neue Strings (5 Sprachen).
- Tests unter `android/app/src/test/java/app/alearthapp/`: `GeoCellTest.kt`, `AnonDeviceIdTest.kt`, `StillnessDetectorTest.kt`, `ShakeDetectorTest.kt`, `CrowdReportTest.kt`.

---

### Task 1: Geteilte Annahmelogik `evaluate_trigger` (Server)

**Files:**
- Modify: `server/src/tda_server/p0b/gateway.py`
- Test: `server/tests/test_p0b_gateway.py`

**Interfaces:**
- Consumes: `PhoneTrigger`, `TriggerGate`, `RateLimiter`, `AttestationVerifier` (bestehend in `gateway.py`).
- Produces: `evaluate_trigger(trigger: PhoneTrigger, token: str, *, verifier: AttestationVerifier, limiter: RateLimiter, gate: TriggerGate) -> PhoneTrigger | None` — gibt den Trigger mit server-gesetztem `attest_ok` zurück, wenn Gate **und** Rate-Limit greifen; sonst `None`. Genutzt von `run_trigger_gateway` (async) und `TriggerIngestor` (sync, Task 3).

- [ ] **Step 1: Failing Test schreiben**

An `server/tests/test_p0b_gateway.py` anhängen:
```python
from tda_server.p0b.gateway import evaluate_trigger


def test_evaluate_trigger_accepts_and_stamps_attest():
    verifier = AllowlistVerifier({"good"})
    limiter = RateLimiter(max_per_window=10, window_ms=1000)
    gate = TriggerGate()
    out = evaluate_trigger(trig("good", 1000), "tok",
                           verifier=verifier, limiter=limiter, gate=gate)
    assert out is not None and out.attest_ok is True and out.device_hash == "good"


def test_evaluate_trigger_rejects_bad_clock():
    verifier = AllowlistVerifier({"good"})
    limiter = RateLimiter(max_per_window=10, window_ms=1000)
    gate = TriggerGate()
    assert evaluate_trigger(trig("good", 1000, unc=9000), "tok",
                            verifier=verifier, limiter=limiter, gate=gate) is None


def test_evaluate_trigger_unknown_device_is_accepted_but_not_attested():
    # attestation failing must NOT drop the trigger (spec: never gate on a
    # binary attest verdict); it only sets attest_ok=False for weighting.
    verifier = AllowlistVerifier({"good"})
    limiter = RateLimiter(max_per_window=10, window_ms=1000)
    gate = TriggerGate()
    out = evaluate_trigger(trig("stranger", 1000), "tok",
                           verifier=verifier, limiter=limiter, gate=gate)
    assert out is not None and out.attest_ok is False
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_p0b_gateway.py -q`
Expected: `ImportError: cannot import name 'evaluate_trigger'`

- [ ] **Step 3: Implementieren**

In `server/src/tda_server/p0b/gateway.py` — `evaluate_trigger` VOR `run_trigger_gateway` einfügen und `run_trigger_gateway` darauf umstellen:
```python
def evaluate_trigger(
    trigger: PhoneTrigger,
    token: str,
    *,
    verifier: AttestationVerifier,
    limiter: RateLimiter,
    gate: TriggerGate,
) -> PhoneTrigger | None:
    """Shared accept decision for one trigger, used by both the async gateway
    and the sync HTTP ingest. Returns the trigger with server-authoritative
    attest_ok set when the clock gate AND rate limit pass; None otherwise.
    A failing attestation only sets attest_ok=False (weighting), never drops."""
    if not (gate.accept(trigger) and limiter.allow(trigger.device_hash, trigger.received_ms)):
        return None
    ok = verifier.verify(trigger.device_hash, token)
    return replace(trigger, attest_ok=ok)


async def run_trigger_gateway(
    incoming: AsyncIterator[tuple[PhoneTrigger, str]],
    stream: EventStream,
    *,
    verifier: AttestationVerifier,
    limiter: RateLimiter,
    gate: TriggerGate,
) -> None:
    async for trigger, token in incoming:
        accepted = evaluate_trigger(trigger, token, verifier=verifier,
                                    limiter=limiter, gate=gate)
        if accepted is not None:
            await stream.append(serialize_trigger(accepted))
```

- [ ] **Step 4: Tests ausführen — müssen bestehen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_p0b_gateway.py -q`
Expected: alle grün (die bestehenden Gateway-Tests + die 3 neuen).

- [ ] **Step 5: Commit**

```bash
git add server/src/tda_server/p0b/gateway.py server/tests/test_p0b_gateway.py
git commit -m "refactor(p0b): extract evaluate_trigger shared by gateway and ingest

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Ingest-Body-Parser (Server)

**Files:**
- Create: `server/src/tda_server/serve/ingest.py`
- Test: `server/tests/test_ingest.py`

**Interfaces:**
- Consumes: `PhoneTrigger`, `ActivePing` (`p0b/signals.py`).
- Produces:
  - `parse_trigger_body(raw: dict, received_ms: int) -> PhoneTrigger` — baut aus dem JSON-Body einen `PhoneTrigger` mit `attest_ok=False` (Server setzt es später über `evaluate_trigger`) und `received_ms`. Wirft `ValueError` bei fehlenden/nicht-numerischen Feldern.
  - `parse_ping_body(raw: dict, received_ms: int) -> ActivePing` — analog. Wirft `ValueError`.

- [ ] **Step 1: Failing Test schreiben**

`server/tests/test_ingest.py`:
```python
import pytest
from tda_server.serve.ingest import parse_ping_body, parse_trigger_body


def test_parse_trigger_body_ok():
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1755691200000", "clock_unc_ms": "1000"}
    t = parse_trigger_body(raw, received_ms=1755691200300)
    assert t.device_hash == "abc" and t.cell == "d410_289"
    assert t.trigger_ms == 1755691200000 and t.clock_unc_ms == 1000
    assert t.received_ms == 1755691200300 and t.attest_ok is False


def test_parse_trigger_body_rejects_missing_field():
    with pytest.raises(ValueError):
        parse_trigger_body({"device_hash": "abc", "cell": "d410_289"}, received_ms=1)


def test_parse_trigger_body_rejects_non_numeric():
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "soon", "clock_unc_ms": "1000"}
    with pytest.raises(ValueError):
        parse_trigger_body(raw, received_ms=1)


def test_parse_ping_body_ok():
    p = parse_ping_body({"device_hash": "abc", "cell": "d410_289",
                         "ping_ms": "1755691200000"}, received_ms=1755691200100)
    assert p.device_hash == "abc" and p.ping_ms == 1755691200000
    assert p.received_ms == 1755691200100
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_ingest.py -q`
Expected: `ModuleNotFoundError: No module named 'tda_server.serve.ingest'`

- [ ] **Step 3: Implementieren**

`server/src/tda_server/serve/ingest.py` (nur die Parser in diesem Task):
```python
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
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_ingest.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add server/src/tda_server/serve/ingest.py server/tests/test_ingest.py
git commit -m "feat(ingest): JSON body parsers for /trigger and /ping

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `TriggerIngestor` + HTTP-Handler + `start_ingest` (Server)

**Files:**
- Modify: `server/src/tda_server/serve/ingest.py`
- Test: `server/tests/test_ingest.py`

**Interfaces:**
- Consumes: `parse_trigger_body`, `parse_ping_body` (Task 2); `evaluate_trigger`, `RateLimiter`, `TriggerGate`, `AllowlistVerifier`, `AttestationVerifier` (Task 1 / `gateway.py`); `serialize_trigger`, `serialize_ping` (`signals.py`).
- Produces:
  - `TriggerIngestor(*, verifier, limiter, gate, submit_trigger: Callable[[dict], None], submit_ping: Callable[[dict], None], ping_limiter: RateLimiter)`.
    - `handle_trigger(raw: dict, token: str, received_ms: int) -> None` — parst, entscheidet via `evaluate_trigger`; bei Annahme ruft `submit_trigger(serialize_trigger(accepted))`. `ValueError` propagiert (→ 400 im Handler).
    - `handle_ping(raw: dict, received_ms: int) -> None` — parst, leicht ratenbegrenzt, ruft `submit_ping(serialize_ping(ping))`.
  - `start_ingest(host: str, port: int, ingestor: TriggerIngestor) -> ThreadingHTTPServer` — Daemon-Thread wie `start_http_proxy`.

- [ ] **Step 1: Failing Test schreiben**

An `server/tests/test_ingest.py` anhängen:
```python
from tda_server.p0b.gateway import AllowlistVerifier, RateLimiter, TriggerGate
from tda_server.serve.ingest import TriggerIngestor


def make_ingestor():
    trig_out, ping_out = [], []
    ing = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=100, window_ms=1000),
        gate=TriggerGate(),
        submit_trigger=trig_out.append,
        submit_ping=ping_out.append,
        ping_limiter=RateLimiter(max_per_window=100, window_ms=1000),
    )
    return ing, trig_out, ping_out


def test_ingestor_accepts_valid_trigger():
    ing, trig_out, _ = make_ingestor()
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1000", "clock_unc_ms": "1000"}
    ing.handle_trigger(raw, token="", received_ms=1100)
    assert len(trig_out) == 1
    assert trig_out[0]["device_hash"] == "abc" and trig_out[0]["attest_ok"] == "0"
    assert trig_out[0]["received_ms"] == "1100"


def test_ingestor_drops_bad_clock_silently():
    ing, trig_out, _ = make_ingestor()
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1000", "clock_unc_ms": "9000"}   # over gate limit
    ing.handle_trigger(raw, token="", received_ms=1100)
    assert trig_out == []                                    # gated out, no raise


def test_ingestor_raises_on_malformed():
    ing, _, _ = make_ingestor()
    with pytest.raises(ValueError):
        ing.handle_trigger({"device_hash": "abc"}, token="", received_ms=1)


def test_ingestor_accepts_ping():
    ing, _, ping_out = make_ingestor()
    ing.handle_ping({"device_hash": "abc", "cell": "d410_289", "ping_ms": "1000"},
                    received_ms=1100)
    assert len(ping_out) == 1 and ping_out[0]["device_hash"] == "abc"
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_ingest.py -q`
Expected: `ImportError: cannot import name 'TriggerIngestor'`

- [ ] **Step 3: Implementieren**

An `server/src/tda_server/serve/ingest.py` anhängen (Importe oben ergänzen):
```python
import json
import logging
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable

from tda_server.p0b.gateway import (
    AttestationVerifier, RateLimiter, TriggerGate, evaluate_trigger,
)
from tda_server.p0b.signals import serialize_ping, serialize_trigger

log = logging.getLogger(__name__)

_MAX_BODY_BYTES = 2048


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
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_ingest.py -q`
Expected: `8 passed`

- [ ] **Step 5: Commit**

```bash
git add server/src/tda_server/serve/ingest.py server/tests/test_ingest.py
git commit -m "feat(ingest): TriggerIngestor + stdlib HTTP handler + start_ingest

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Ingest in `serve_local.py` verdrahten (Server)

**Files:**
- Modify: `server/scripts/serve_local.py`
- Test: `server/tests/test_ingest.py` (Smoke, dass `start_ingest` einen laufenden Server liefert)

**Interfaces:**
- Consumes: `TriggerIngestor`, `start_ingest` (Task 3); `RateLimiter`, `TriggerGate`, `AllowlistVerifier` (`gateway.py`); `InMemoryStream` (`stream/base.py`) für den lokalen Lauf.
- Produces: keine neuen Symbole; `serve_local` startet zusätzlich den Ingest-Port (`TDA_INGEST_PORT`, Default `8002`). Die Submit-Callbacks hängen die Signale an lokale Streams (im lokalen Runner reicht `InMemoryStream`; die produktive Pipeline-Verdrahtung nutzt dieselben Streams, die `run_p0b_pipeline` liest).

- [ ] **Step 1: Failing Test schreiben (End-to-End über echten Socket)**

An `server/tests/test_ingest.py` anhängen:
```python
import time
import urllib.request

from tda_server.stream.base import InMemoryStream


def test_start_ingest_end_to_end_localhost():
    stream_records = []
    ing = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=100, window_ms=1000),
        gate=TriggerGate(),
        submit_trigger=stream_records.append,
        submit_ping=lambda d: None,
        ping_limiter=RateLimiter(max_per_window=100, window_ms=1000),
    )
    server = start_ingest("127.0.0.1", 0, ing, clock=lambda: 1000)
    port = server.server_address[1]
    body = json.dumps({"device_hash": "abc", "cell": "d410_289",
                       "trigger_ms": "1000", "clock_unc_ms": "1000"}).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{port}/trigger", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=2) as resp:
        assert resp.status == 202
    server.shutdown()
    assert len(stream_records) == 1 and stream_records[0]["device_hash"] == "abc"
```

- [ ] **Step 2: Test ausführen — muss bestehen (start_ingest existiert bereits aus Task 3)**

Run: `cd tda/server && .venv/Scripts/python -m pytest tests/test_ingest.py::test_start_ingest_end_to_end_localhost -q`
Expected: `1 passed` (bindet an Port 0 = freier Port; kein fester Port nötig).

- [ ] **Step 3: `serve_local.py` erweitern**

In `server/scripts/serve_local.py` die Importe und den `__main__`-Block ergänzen (bestehende Zeilen unverändert lassen):
```python
from tda_server.p0b.gateway import AllowlistVerifier, RateLimiter, TriggerGate
from tda_server.serve.ingest import TriggerIngestor, start_ingest
from tda_server.stream.base import InMemoryStream
```
Und innerhalb `if __name__ == "__main__":` nach dem Start des HTTP-Proxys:
```python
    ingest_port = int(os.environ.get("TDA_INGEST_PORT", "8002"))
    # Local runner: signals land in in-memory streams. A production entrypoint
    # passes the SAME streams that run_p0b_pipeline consumes (density<-pings,
    # detector<-triggers) plus loop-thread-safe submit callbacks.
    _trigger_stream = InMemoryStream()
    _ping_stream = InMemoryStream()
    _loop = asyncio.new_event_loop()

    def _submit(stream):
        def _cb(record):
            asyncio.run_coroutine_threadsafe(stream.append(record), _loop)
        return _cb

    ingestor = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=6, window_ms=60_000),
        gate=TriggerGate(),
        submit_trigger=_submit(_trigger_stream),
        submit_ping=_submit(_ping_stream),
        ping_limiter=RateLimiter(max_per_window=4, window_ms=60_000),
    )
    start_ingest(host, ingest_port, ingestor)
    print(f"starting TDA ingest on http://{host}:{ingest_port} (/trigger, /ping)")
```
> Hinweis für den Implementierer: `serve_local` ruft am Ende `asyncio.run(serve(...))`, das seinen eigenen Loop erstellt. Damit `run_coroutine_threadsafe` in `_loop` etwas bewirkt, muss `_loop` laufen. Für den **lokalen Runner** genügt es, die Ingest-Signale zu empfangen und zu loggen; die echte Loop-/Pipeline-Verdrahtung ist ein Deploy-Schritt (Oracle) außerhalb dieses Plans. Ersetze die zwei `InMemoryStream()`-Zeilen NICHT durch Pipeline-Objekte in diesem Task — halte den lokalen Runner lauffähig und dokumentiere die Produktivverdrahtung als Kommentar (wie oben).

- [ ] **Step 4: Manueller Smoke-Lauf (Beleg)**

Run: `cd tda/server && .venv/Scripts/python -c "import ast; ast.parse(open('scripts/serve_local.py').read()); print('serve_local.py parses OK')"`
Expected: `serve_local.py parses OK` (reiner Syntax-/Import-Struktur-Check; der volle Serverlauf gehört zum Deploy).

- [ ] **Step 5: Commit**

```bash
git add server/scripts/serve_local.py server/tests/test_ingest.py
git commit -m "feat(serve): start crowdsourcing ingest alongside proxy + ws bridge

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: `GeoCell.coarsenCell` mit Python-Parität (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/GeoCell.kt`
- Test: `android/app/src/test/java/app/alearthapp/GeoCellTest.kt`

**Interfaces:**
- Produces: `object GeoCell { const val DETECT_CELL_DEG = 0.1; fun coarsenCell(lat: Double, lon: Double): String }` — Ergebnis byte-identisch zu `signals.coarsen_cell`.

- [ ] **Step 1: Failing Test schreiben**

`android/app/src/test/java/app/alearthapp/GeoCellTest.kt`:
```kotlin
package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoCellTest {
    @Test fun matchesPythonReferenceValues() {
        // parity with server coarsen_cell (p0b/signals.py test values)
        assertEquals("d410_289", GeoCell.coarsenCell(41.02, 28.97))
        assertEquals("d410_289", GeoCell.coarsenCell(41.08, 28.93))
    }

    @Test fun handlesNegativeCoordinates() {
        // floor rounds toward negative infinity, like Python math.floor
        assertEquals("d-1_-1", GeoCell.coarsenCell(-0.05, -0.05))
    }
}
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.GeoCellTest`
Expected: Kompilierfehler „unresolved reference: GeoCell".

- [ ] **Step 3: Implementieren**

`android/app/src/main/java/app/alearthapp/GeoCell.kt`:
```kotlin
package app.alearthapp

import kotlin.math.floor

/**
 * Rundet eine Rohposition auf die anonyme Detektionszelle (0,1°-Raster).
 * MUSS byte-identische Strings zu `p0b/signals.py::coarsen_cell` liefern —
 * nur diese Zell-ID verlässt das Gerät, nie die Rohkoordinate.
 */
object GeoCell {
    const val DETECT_CELL_DEG = 0.1

    fun coarsenCell(lat: Double, lon: Double): String {
        val la = floor(lat / DETECT_CELL_DEG).toInt()
        val lo = floor(lon / DETECT_CELL_DEG).toInt()
        return "d${la}_$lo"
    }
}
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.GeoCellTest`
Expected: `BUILD SUCCESSFUL`, 2 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/GeoCell.kt android/app/src/test/java/app/alearthapp/GeoCellTest.kt
git commit -m "feat(client): GeoCell.coarsenCell with server parity

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: `AnonDeviceId` — rotierende anonyme Kennung (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/AnonDeviceId.kt`
- Test: `android/app/src/test/java/app/alearthapp/AnonDeviceIdTest.kt`

**Interfaces:**
- Produces:
  - `data class AnonId(val hash: String, val dayEpoch: Long)`
  - `object AnonDeviceId { fun rotate(stored: AnonId?, todayEpochDay: Long, randomHash: () -> String): AnonId }` — reine Logik: gibt die gespeicherte ID zurück, wenn `stored != null && stored.dayEpoch == todayEpochDay`; sonst eine neue ID mit `randomHash()` und `todayEpochDay`.
  - (Die Prefs-/Krypto-Anbindung `current(ctx): String` kommt in Task 9, wenn Prefs erweitert ist.)

- [ ] **Step 1: Failing Test schreiben**

`android/app/src/test/java/app/alearthapp/AnonDeviceIdTest.kt`:
```kotlin
package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnonDeviceIdTest {
    @Test fun sameDayKeepsId() {
        val stored = AnonId("AAAA", 20000L)
        val out = AnonDeviceId.rotate(stored, todayEpochDay = 20000L) { "BBBB" }
        assertEquals("AAAA", out.hash)
        assertEquals(20000L, out.dayEpoch)
    }

    @Test fun nextDayRotatesId() {
        val stored = AnonId("AAAA", 20000L)
        val out = AnonDeviceId.rotate(stored, todayEpochDay = 20001L) { "BBBB" }
        assertEquals("BBBB", out.hash)
        assertEquals(20001L, out.dayEpoch)
    }

    @Test fun noStoredIdCreatesOne() {
        val out = AnonDeviceId.rotate(null, todayEpochDay = 20001L) { "CCCC" }
        assertEquals("CCCC", out.hash)
        assertNotEquals("", out.hash)
    }
}
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.AnonDeviceIdTest`
Expected: „unresolved reference: AnonId / AnonDeviceId".

- [ ] **Step 3: Implementieren**

`android/app/src/main/java/app/alearthapp/AnonDeviceId.kt`:
```kotlin
package app.alearthapp

/** Anonyme Gerätekennung + ihr Ausgabetag (Tage seit Epoch). */
data class AnonId(val hash: String, val dayEpoch: Long)

/**
 * Rotierende anonyme Kennung: erfüllt den `device_hash` des Draht-Vertrags für
 * Kurzzeit-Dedup/Reputation, ohne Verfolgbarkeit über Tage. Kein Konto, keine
 * Ad-ID/IMEI. Die Rotationsentscheidung ist reine Logik (hier), die Erzeugung
 * der Zufalls-ID + Prefs-Persistenz sitzt in [Prefs] (siehe Task 9).
 */
object AnonDeviceId {
    fun rotate(stored: AnonId?, todayEpochDay: Long, randomHash: () -> String): AnonId {
        if (stored != null && stored.dayEpoch == todayEpochDay) return stored
        return AnonId(randomHash(), todayEpochDay)
    }
}
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.AnonDeviceIdTest`
Expected: `BUILD SUCCESSFUL`, 3 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/AnonDeviceId.kt android/app/src/test/java/app/alearthapp/AnonDeviceIdTest.kt
git commit -m "feat(client): rotating anonymous device id logic

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: `StillnessDetector` — Ruhe-Erkennung (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/StillnessDetector.kt`
- Test: `android/app/src/test/java/app/alearthapp/StillnessDetectorTest.kt`

**Interfaces:**
- Produces:
  - `data class SensorConfig(val stillVar: Double = 0.05, val motionVar: Double = 0.5, val settleMs: Long = 60_000, val shakeDelta: Double = 1.2, val shakeMinSamples: Int = 3, val refractoryMs: Long = 30_000)` — geteilte Startwerte für Ruhe **und** Rütteln (Task 8).
  - `enum class Stillness { SETTLED, UNSETTLED }`
  - `class StillnessDetector(private val cfg: SensorConfig, private val windowSize: Int = 50)` mit `fun onSample(tMs: Long, magnitude: Double): Stillness`. Führt einen Ring der letzten `windowSize` Magnituden; „SETTLED", sobald die Fenster-Varianz für ≥ `settleMs` unter `stillVar` blieb; jede Varianz über `motionVar` setzt sofort zurück auf UNSETTLED und startet die Ruhe-Uhr neu.

- [ ] **Step 1: Failing Test schreiben**

`android/app/src/test/java/app/alearthapp/StillnessDetectorTest.kt`:
```kotlin
package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class StillnessDetectorTest {
    private val g = 9.81

    @Test fun becomesSettledAfterQuietWindow() {
        val det = StillnessDetector(SensorConfig(settleMs = 1000), windowSize = 10)
        var state = Stillness.UNSETTLED
        // 200 quiet samples at 50ms spacing = 10s of stillness, tiny noise
        for (i in 0 until 200) {
            val jitter = if (i % 2 == 0) 0.001 else -0.001
            state = det.onSample(i * 50L, g + jitter)
        }
        assertEquals(Stillness.SETTLED, state)
    }

    @Test fun motionResetsToUnsettled() {
        val det = StillnessDetector(SensorConfig(settleMs = 1000), windowSize = 10)
        for (i in 0 until 200) det.onSample(i * 50L, g + 0.001)
        // a big jolt: fill the window with high-variance samples
        var state = Stillness.SETTLED
        for (i in 200 until 220) {
            val spike = if (i % 2 == 0) g + 3.0 else g - 3.0
            state = det.onSample(i * 50L, spike)
        }
        assertEquals(Stillness.UNSETTLED, state)
    }
}
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.StillnessDetectorTest`
Expected: „unresolved reference".

- [ ] **Step 3: Implementieren**

`android/app/src/main/java/app/alearthapp/StillnessDetector.kt`:
```kotlin
package app.alearthapp

/** Geteilte, feinjustierbare Startwerte für Ruhe- und Rütteln-Erkennung. */
data class SensorConfig(
    val stillVar: Double = 0.05,
    val motionVar: Double = 0.5,
    val settleMs: Long = 60_000,
    val shakeDelta: Double = 1.2,
    val shakeMinSamples: Int = 3,
    val refractoryMs: Long = 30_000,
)

enum class Stillness { SETTLED, UNSETTLED }

/**
 * Erkennt, ob das Handy ruhig liegt: gleitende Varianz der Beschleunigungs-
 * Magnitude über die letzten [windowSize] Samples. SETTLED, sobald die Varianz
 * für ≥ settleMs unter stillVar blieb; ein Ausschlag über motionVar setzt sofort
 * zurück (Handy angefasst). Reine Logik — Sensor-Anbindung im Service (Task 11).
 */
class StillnessDetector(private val cfg: SensorConfig, private val windowSize: Int = 50) {
    private val mags = ArrayDeque<Double>()
    private var quietSinceMs: Long? = null

    fun onSample(tMs: Long, magnitude: Double): Stillness {
        mags.addLast(magnitude)
        while (mags.size > windowSize) mags.removeFirst()
        if (mags.size < windowSize) return Stillness.UNSETTLED
        val variance = variance(mags)
        if (variance > cfg.motionVar) {
            quietSinceMs = null
            return Stillness.UNSETTLED
        }
        if (variance <= cfg.stillVar) {
            val since = quietSinceMs ?: tMs.also { quietSinceMs = it }
            if (tMs - since >= cfg.settleMs) return Stillness.SETTLED
        }
        return Stillness.UNSETTLED
    }

    private fun variance(xs: Collection<Double>): Double {
        val mean = xs.sum() / xs.size
        return xs.sumOf { (it - mean) * (it - mean) } / xs.size
    }
}
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.StillnessDetectorTest`
Expected: `BUILD SUCCESSFUL`, 2 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/StillnessDetector.kt android/app/src/test/java/app/alearthapp/StillnessDetectorTest.kt
git commit -m "feat(client): stillness detector (rolling variance state machine)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: `ShakeDetector` — Rütteln-Erkennung (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/ShakeDetector.kt`
- Test: `android/app/src/test/java/app/alearthapp/ShakeDetectorTest.kt`

**Interfaces:**
- Consumes: `SensorConfig` (Task 7).
- Produces: `class ShakeDetector(private val cfg: SensorConfig, private val baseline: Double = 9.81)` mit `fun onSample(tMs: Long, magnitude: Double): Long?` — gibt `trigger_ms` (Zeitstempel des ersten auffälligen Samples) zurück, sobald ≥ `shakeMinSamples` aufeinanderfolgende Samples um mehr als `shakeDelta` von der Baseline abweichen; danach `refractoryMs` Sperrzeit (in der `null` zurückkommt). Sonst `null`.

- [ ] **Step 1: Failing Test schreiben**

`android/app/src/test/java/app/alearthapp/ShakeDetectorTest.kt`:
```kotlin
package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShakeDetectorTest {
    private val g = 9.81

    @Test fun quietGivesNoTrigger() {
        val det = ShakeDetector(SensorConfig())
        for (i in 0 until 100) assertNull(det.onSample(i * 20L, g + 0.01))
    }

    @Test fun sustainedShakeFiresOnceAtFirstSample() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3, refractoryMs = 30_000))
        assertNull(det.onSample(1000L, g + 2.0))   // sample 1 above delta
        assertNull(det.onSample(1020L, g + 2.0))   // sample 2
        val fired = det.onSample(1040L, g + 2.0)   // sample 3 -> trigger
        assertEquals(1000L, fired)                 // trigger_ms = first shaky sample
    }

    @Test fun refractorySuppressesImmediateSecondTrigger() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3, refractoryMs = 30_000))
        det.onSample(1000L, g + 2.0); det.onSample(1020L, g + 2.0); det.onSample(1040L, g + 2.0)
        // more shaking within refractory window -> no new trigger
        for (i in 0 until 5) assertNull(det.onSample(2000L + i * 20L, g + 2.0))
    }

    @Test fun singleSpikeDoesNotFire() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3))
        assertNull(det.onSample(1000L, g + 5.0))   // one tap
        assertNull(det.onSample(1020L, g + 0.01))  // back to quiet
        assertNull(det.onSample(1040L, g + 0.01))
    }
}
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.ShakeDetectorTest`
Expected: „unresolved reference".

- [ ] **Step 3: Implementieren**

`android/app/src/main/java/app/alearthapp/ShakeDetector.kt`:
```kotlin
package app.alearthapp

import kotlin.math.abs

/**
 * Erkennt ein anhaltendes Rütteln (nicht einen einzelnen Klaps): ≥ shakeMinSamples
 * aufeinanderfolgende Samples, die um mehr als shakeDelta von der Ruhelage
 * (Schwerkraft) abweichen → gibt den Zeitstempel des ERSTEN auffälligen Samples
 * als trigger_ms zurück. Danach refractoryMs Sperrzeit gegen Dubletten desselben
 * Ereignisses. Bewusst großzügig — der Server sortiert Fehlalarme aus.
 */
class ShakeDetector(private val cfg: SensorConfig, private val baseline: Double = 9.81) {
    private var run = 0
    private var runStartMs = 0L
    private var suppressUntilMs = 0L

    fun onSample(tMs: Long, magnitude: Double): Long? {
        if (tMs < suppressUntilMs) {
            // still shaking during refractory: keep suppressing, don't re-arm mid-burst
            if (abs(magnitude - baseline) <= cfg.shakeDelta) run = 0
            return null
        }
        if (abs(magnitude - baseline) > cfg.shakeDelta) {
            if (run == 0) runStartMs = tMs
            run++
            if (run >= cfg.shakeMinSamples) {
                val triggerMs = runStartMs
                run = 0
                suppressUntilMs = tMs + cfg.refractoryMs
                return triggerMs
            }
        } else {
            run = 0
        }
        return null
    }
}
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.ShakeDetectorTest`
Expected: `BUILD SUCCESSFUL`, 4 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/ShakeDetector.kt android/app/src/test/java/app/alearthapp/ShakeDetectorTest.kt
git commit -m "feat(client): shake detector (sustained-deviation with refractory)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: `Prefs`-Erweiterung — Opt-in, anonyme ID, Ping-Stempel (Client)

**Files:**
- Modify: `android/app/src/main/java/app/alearthapp/Prefs.kt`
- Modify: `android/app/src/main/java/app/alearthapp/AnonDeviceId.kt` (Prefs-Anbindung `current`)

**Interfaces:**
- Consumes: `AnonId`, `AnonDeviceId.rotate` (Task 6).
- Produces:
  - `Prefs.crowdsourcingEnabled: Boolean` (Default false).
  - `Prefs.anonIdHash: String`, `Prefs.anonIdDay: Long` (Roh-Speicher der rotierenden Kennung).
  - `Prefs.lastPingMs: Long` (Default 0) — wann zuletzt ein Aktiv-Ping gesendet wurde.
  - `AnonDeviceId.current(ctx: Context, todayEpochDay: Long): String` — lädt/rotiert die Kennung über Prefs (16 Zufallsbytes → Base64, `randomHash`), persistiert und gibt `hash` zurück.

- [ ] **Step 1: Implementieren (Prefs)**

In `Prefs.kt` neue Keys bei den anderen `private const val KEY_*` ergänzen:
```kotlin
    private const val KEY_CROWD_ENABLED = "crowdsourcing_enabled"
    private const val KEY_ANON_ID_HASH = "anon_id_hash"
    private const val KEY_ANON_ID_DAY = "anon_id_day"
    private const val KEY_LAST_PING_MS = "last_ping_ms"
```
Und die zugehörigen `var`-Properties am Ende des `object Prefs` (vor der schließenden Klammer):
```kotlin
    /** Opt-in: Handy horcht am Strom und meldet Erschütterungen. Standard aus. */
    var crowdsourcingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CROWD_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_CROWD_ENABLED, v).apply() }

    /** Rohspeicher der rotierenden anonymen Kennung (siehe [AnonDeviceId]). */
    var anonIdHash: String
        get() = prefs.getString(KEY_ANON_ID_HASH, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ANON_ID_HASH, v).apply() }

    var anonIdDay: Long
        get() = prefs.getLong(KEY_ANON_ID_DAY, -1L)
        set(v) { prefs.edit().putLong(KEY_ANON_ID_DAY, v).apply() }

    /** Zeitstempel (ms) des zuletzt gesendeten Aktiv-Pings. */
    var lastPingMs: Long
        get() = prefs.getLong(KEY_LAST_PING_MS, 0L)
        set(v) { prefs.edit().putLong(KEY_LAST_PING_MS, v).apply() }
```

- [ ] **Step 2: Implementieren (`AnonDeviceId.current`)**

An `AnonDeviceId.kt` anhängen (Importe oben ergänzen):
```kotlin
import android.content.Context
import android.util.Base64
import java.security.SecureRandom

// innerhalb object AnonDeviceId:
    /** Lädt die Kennung aus Prefs, rotiert bei Tageswechsel, persistiert, gibt hash. */
    fun current(ctx: Context, todayEpochDay: Long): String {
        Prefs.init(ctx)
        val stored = if (Prefs.anonIdHash.isNotEmpty() && Prefs.anonIdDay >= 0)
            AnonId(Prefs.anonIdHash, Prefs.anonIdDay) else null
        val next = rotate(stored, todayEpochDay) { randomHash() }
        if (next.hash != Prefs.anonIdHash || next.dayEpoch != Prefs.anonIdDay) {
            Prefs.anonIdHash = next.hash
            Prefs.anonIdDay = next.dayEpoch
        }
        return next.hash
    }

    private fun randomHash(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
```

- [ ] **Step 3: Bestehende Unit-Tests weiterhin grün (kein Regress)**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.AnonDeviceIdTest --tests app.alearthapp.GeoCellTest`
Expected: `BUILD SUCCESSFUL` (die reine `rotate`-Logik ist unverändert; `current` nutzt Android-APIs und wird im Service-Task real geprüft).

- [ ] **Step 4: App kompiliert**

Run: `cd tda/android && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/Prefs.kt android/app/src/main/java/app/alearthapp/AnonDeviceId.kt
git commit -m "feat(client): prefs for crowdsourcing opt-in, anon id, ping stamp

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 10: `CrowdReport` — JSON-Bauer + okhttp-Melder (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/CrowdReport.kt`
- Test: `android/app/src/test/java/app/alearthapp/CrowdReportTest.kt`

**Interfaces:**
- Produces:
  - `object CrowdReport`
    - `fun triggerJson(deviceHash: String, cell: String, triggerMs: Long, clockUncMs: Long): String` — reiner Body-Bauer; Felder exakt `device_hash, cell, trigger_ms, clock_unc_ms` (letzte zwei als Strings, konform zum `dict[str, str]`-Vertrag).
    - `fun pingJson(deviceHash: String, cell: String, pingMs: Long): String` — Felder `device_hash, cell, ping_ms`.
    - `fun postTrigger(ctx: Context, deviceHash: String, cell: String, triggerMs: Long, clockUncMs: Long)` — okhttp-POST an `{backendUrl}/trigger`, No-op bei leerem `backendUrl` (Muster wie `PushRegistrar`, best-effort, kein Retry).
    - `fun postPing(ctx: Context, deviceHash: String, cell: String, pingMs: Long)` — okhttp-POST an `{backendUrl}/ping`.
- Consumes: `Prefs.backendUrl`.

- [ ] **Step 1: Failing Test schreiben**

`android/app/src/test/java/app/alearthapp/CrowdReportTest.kt`:
```kotlin
package app.alearthapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdReportTest {
    @Test fun triggerJsonMatchesWireContract() {
        val o = JSONObject(CrowdReport.triggerJson("abc", "d410_289", 1000L, 1000L))
        assertEquals("abc", o.getString("device_hash"))
        assertEquals("d410_289", o.getString("cell"))
        assertEquals("1000", o.getString("trigger_ms"))   // strings per dict[str,str]
        assertEquals("1000", o.getString("clock_unc_ms"))
    }

    @Test fun pingJsonMatchesWireContract() {
        val o = JSONObject(CrowdReport.pingJson("abc", "d410_289", 2000L))
        assertEquals("abc", o.getString("device_hash"))
        assertEquals("d410_289", o.getString("cell"))
        assertEquals("2000", o.getString("ping_ms"))
    }
}
```
> `org.json` ist in JVM-Unit-Tests von Android nicht standardmäßig verfügbar; der Implementierer stellt es bereit, indem er in `android/app/build.gradle` unter `testImplementation` `'org.json:json:20240303'` ergänzt (analog zum bestehenden `junit`-Eintrag). Das ist Teil dieses Tasks.

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.CrowdReportTest`
Expected: „unresolved reference: CrowdReport".

- [ ] **Step 3: Implementieren**

Erst `android/app/build.gradle` — unter den `dependencies` bei den Test-Zeilen ergänzen:
```gradle
  testImplementation 'org.json:json:20240303'
```
Dann `android/app/src/main/java/app/alearthapp/CrowdReport.kt`:
```kotlin
package app.alearthapp

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Meldet anonyme Crowdsourcing-Signale an den Server: eine Erschütterung
 * (POST /trigger) und periodische „ich horche"-Pings (POST /ping). Best-effort,
 * kein Retry (eine späte Meldung nützt nichts). No-op ohne [Prefs.backendUrl].
 * Nur die Zell-ID wird gesendet, nie Rohkoordinaten (Aufrufer rundet vorher).
 */
object CrowdReport {
    private val client by lazy { OkHttpClient() }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun triggerJson(deviceHash: String, cell: String, triggerMs: Long, clockUncMs: Long): String =
        JSONObject()
            .put("device_hash", deviceHash)
            .put("cell", cell)
            .put("trigger_ms", triggerMs.toString())
            .put("clock_unc_ms", clockUncMs.toString())
            .toString()

    fun pingJson(deviceHash: String, cell: String, pingMs: Long): String =
        JSONObject()
            .put("device_hash", deviceHash)
            .put("cell", cell)
            .put("ping_ms", pingMs.toString())
            .toString()

    fun postTrigger(ctx: Context, deviceHash: String, cell: String,
                    triggerMs: Long, clockUncMs: Long) =
        post(ctx, "/trigger", triggerJson(deviceHash, cell, triggerMs, clockUncMs))

    fun postPing(ctx: Context, deviceHash: String, cell: String, pingMs: Long) =
        post(ctx, "/ping", pingJson(deviceHash, cell, pingMs))

    private fun post(ctx: Context, path: String, body: String) {
        Prefs.init(ctx)
        val base = Prefs.backendUrl.trimEnd('/')
        if (base.isEmpty()) return
        val req = Request.Builder().url("$base$path").post(body.toRequestBody(JSON)).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* best-effort */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `cd tda/android && ./gradlew testDebugUnitTest --tests app.alearthapp.CrowdReportTest`
Expected: `BUILD SUCCESSFUL`, 2 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/CrowdReport.kt android/app/src/test/java/app/alearthapp/CrowdReportTest.kt android/app/build.gradle
git commit -m "feat(client): CrowdReport JSON builders + okhttp trigger/ping sender

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 11: `QuakeSensorService` + `PowerConnectionReceiver` + Manifest (Client)

**Files:**
- Create: `android/app/src/main/java/app/alearthapp/QuakeSensorService.kt`
- Create: `android/app/src/main/java/app/alearthapp/PowerConnectionReceiver.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/app/alearthapp/NotificationChannels.kt` (leiser Kanal, falls noch keiner passt — sonst bestehenden SERVICE-Kanal nutzen)

**Interfaces:**
- Consumes: `SensorConfig`, `StillnessDetector`, `Stillness` (Task 7); `ShakeDetector` (Task 8); `GeoCell.coarsenCell` (Task 5); `AnonDeviceId.current` (Task 9); `CrowdReport.postTrigger/postPing` (Task 10); `Prefs.crowdsourcingEnabled/lastPingMs`.
- Produces:
  - `class QuakeSensorService : Service(), SensorEventListener` mit `companion object { fun start(ctx); fun stop(ctx) }` (Muster wie `AlarmService.start`). Registriert den Beschleunigungssensor, füttert Stillness+Shake, holt bei „SETTLED" grobe Position (Last-Known, `ACCESS_COARSE_LOCATION`) → `coarsenCell`, sendet bei Trigger `postTrigger` und alle ~30 min `postPing`. No-op/`stopSelf`, wenn `!Prefs.crowdsourcingEnabled`.
  - `class PowerConnectionReceiver : BroadcastReceiver` — auf `ACTION_POWER_CONNECTED` → `QuakeSensorService.start`; auf `ACTION_POWER_DISCONNECTED` → `QuakeSensorService.stop`.

**Hinweis (bekanntes Risiko, im Test zu prüfen):** Start eines Foreground-Service aus einem Hintergrund-Broadcast ist ab Android 12 eingeschränkt. Falls `startForegroundService` aus dem Receiver eine `ForegroundServiceStartNotAllowedException` wirft, im `catch` NICHT abstürzen (loggen und ignorieren); der Service startet dann beim nächsten App-Öffnen (MainActivity ruft `QuakeSensorService.start` bei aktivem Opt-in und ladendem Gerät). Beide Pfade gehören in diesen Task.

- [ ] **Step 1: `QuakeSensorService` implementieren**

`android/app/src/main/java/app/alearthapp/QuakeSensorService.kt`:
```kotlin
package app.alearthapp

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Läuft NUR während das Handy lädt (an-/abgeschaltet vom [PowerConnectionReceiver]).
 * Horcht am Beschleunigungssensor, erkennt über [StillnessDetector]/[ShakeDetector]
 * ein Rütteln bei ruhigem Gerät und meldet es anonym via [CrowdReport]. Die
 * eigentliche Erkennungslogik ist rein und andernorts getestet — hier nur der
 * Android-Rand. No-op, solange [Prefs.crowdsourcingEnabled] aus ist.
 */
class QuakeSensorService : Service(), SensorEventListener {

    companion object {
        private const val NOTIF_ID = 4300
        private const val PING_INTERVAL_MS = 30 * 60_000L
        private const val CLOCK_UNC_MS = 1000L

        fun start(ctx: Context) {
            val i = Intent(ctx, QuakeSensorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) { /* FGS-from-background restriction: retry on app open */ }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, QuakeSensorService::class.java))
        }
    }

    private var sensorManager: SensorManager? = null
    private lateinit var cfg: SensorConfig
    private lateinit var stillness: StillnessDetector
    private lateinit var shake: ShakeDetector
    private var settled = false

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NotificationChannels.ensure(this)
        cfg = SensorConfig()
        stillness = StillnessDetector(cfg)
        shake = ShakeDetector(cfg)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.crowdsourcingEnabled) { stopSelf(); return START_NOT_STICKY }
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run { stopSelf() }   // no accelerometer -> feature inactive
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tMs = System.currentTimeMillis()
        val mag = sqrt(
            (event.values[0] * event.values[0] +
             event.values[1] * event.values[1] +
             event.values[2] * event.values[2]).toDouble()
        )
        settled = stillness.onSample(tMs, mag) == Stillness.SETTLED
        if (!settled) return
        maybePing(tMs)
        val triggerMs = shake.onSample(tMs, mag) ?: return
        val cell = currentCell() ?: return
        val hash = AnonDeviceId.current(this, tMs / 86_400_000L)
        CrowdReport.postTrigger(this, hash, cell, triggerMs, CLOCK_UNC_MS)
    }

    private fun maybePing(tMs: Long) {
        if (tMs - Prefs.lastPingMs < PING_INTERVAL_MS) return
        val cell = currentCell() ?: return
        val hash = AnonDeviceId.current(this, tMs / 86_400_000L)
        CrowdReport.postPing(this, hash, cell, tMs)
        Prefs.lastPingMs = tMs
    }

    /** Grobe Position (Last-Known) → 0,1°-Zelle; null ohne Permission/Fix. */
    private fun currentCell(): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull() ?: return null
        return GeoCell.coarsenCell(loc.latitude, loc.longitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_status_ready)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.crowd_svc_running))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}
```
> `R.string.crowd_svc_running` wird in Task 12 angelegt; damit dieser Task für sich kompiliert, legt der Implementierer den String-Eintrag `crowd_svc_running` in `res/values/strings.xml` (Standard/Englisch) direkt hier mit an — die restlichen 4 Sprachen kommen in Task 12.

- [ ] **Step 2: `PowerConnectionReceiver` implementieren**

`android/app/src/main/java/app/alearthapp/PowerConnectionReceiver.kt`:
```kotlin
package app.alearthapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Koppelt den [QuakeSensorService] ans Ladekabel: Strom rein → Service an,
 * Strom raus → Service aus. Nur wenn der Nutzer das Mithelfen aktiviert hat.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                if (Prefs.crowdsourcingEnabled) QuakeSensorService.start(context)
            Intent.ACTION_POWER_DISCONNECTED ->
                QuakeSensorService.stop(context)
        }
    }
}
```

- [ ] **Step 3: Manifest ergänzen**

In `android/app/src/main/AndroidManifest.xml`:
- Bei den `<uses-permission>`: `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`
- Innerhalb `<application>` (neben `AlarmService`):
```xml
        <service
            android:name=".QuakeSensorService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="crowdsourced_earthquake_sensing" />
        </service>

        <receiver
            android:name=".PowerConnectionReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.ACTION_POWER_CONNECTED" />
                <action android:name="android.intent.action.ACTION_POWER_DISCONNECTED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 4: App kompiliert + bestehende Unit-Tests grün**

Run: `cd tda/android && ./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; alle bisherigen Client-Tests (GeoCell, AnonDeviceId, Stillness, Shake, CrowdReport) grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/QuakeSensorService.kt android/app/src/main/java/app/alearthapp/PowerConnectionReceiver.kt android/app/src/main/AndroidManifest.xml android/app/src/main/res/values/strings.xml
git commit -m "feat(client): charging-gated accelerometer sensor service + power receiver

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 12: Opt-in-Schalter, Onboarding-Einladung, i18n (Client)

**Files:**
- Modify: `android/app/src/main/java/app/alearthapp/MainActivity.kt` (Schalter-Anbindung + Service-Start bei aktivem Opt-in & ladendem Gerät)
- Modify: `android/app/src/main/res/values/strings.xml` und `values-tr/`, `values-de/`, `values-ru/`, `values-ar/` — neue Strings
- Modify: die Web-/Einstellungs-Oberfläche, in der die App ihre Schalter zeigt (bestehendes Muster für `dndBypassOptIn` spiegeln), oder ein natives Settings-Element analog zu den vorhandenen Schaltern.

**Interfaces:**
- Consumes: `Prefs.crowdsourcingEnabled` (Task 9); `QuakeSensorService.start` (Task 11); `BatteryManager` (Ladezustand).
- Produces: sichtbarer Schalter „Mithelfen, andere zu warnen"; beim Einschalten wird `ACCESS_COARSE_LOCATION` angefragt und — falls das Gerät gerade lädt — `QuakeSensorService.start` gerufen; String-Ressourcen `crowd_title`, `crowd_desc`, `crowd_svc_running`, `crowd_ob_title`, `crowd_ob_body` in allen 5 Sprachen.

- [ ] **Step 1: Strings anlegen (5 Sprachen)**

In `res/values/strings.xml` (Englisch) — und sinngemäß übersetzt in `values-tr`, `values-de`, `values-ru`, `values-ar`:
```xml
    <string name="crowd_title">Help warn others</string>
    <string name="crowd_desc">When your phone is charging and lying still, it can sense shaking and warn people nearby. Anonymous, costs almost no battery.</string>
    <string name="crowd_svc_running">Listening for earthquakes while charging</string>
    <string name="crowd_ob_title">Help warn others</string>
    <string name="crowd_ob_body">Turn this on and your phone helps detect earthquakes for everyone — only while it charges. No account, no tracking.</string>
```
Deutsche Fassung (`values-de`), als Referenz für die Terminologie:
```xml
    <string name="crowd_title">Mithelfen, andere zu warnen</string>
    <string name="crowd_desc">Wenn dein Handy lädt und ruhig liegt, kann es Erschütterungen spüren und Menschen in der Nähe warnen. Anonym, kostet fast keinen Akku.</string>
    <string name="crowd_svc_running">Horcht beim Laden nach Erdbeben</string>
    <string name="crowd_ob_title">Hilf mit, andere zu warnen</string>
    <string name="crowd_ob_body">Schalte es ein, und dein Handy hilft, Erdbeben für alle zu erkennen — nur während es lädt. Kein Konto, keine Verfolgung.</string>
```
> TR/RU/AR sinngemäß übersetzen; die AR-Terminologie folgt der bestehenden `i18n-terminology-research.md` (z. B. „Erdbeben" = زلزال). Diese drei Übersetzungen sind vom Nutzer gegenzulesen (Merker im Abschlussbericht).

- [ ] **Step 2: Schalter + Service-Start in `MainActivity` anbinden**

Der Implementierer verdrahtet den Schalter im bestehenden Einstellungs-/Bridge-Muster (wie `dndBypassOptIn` über `WebBridge` gesetzt wird). Kernlogik beim Einschalten:
```kotlin
// Pseudocode-Anker für die vorhandene Settings-Anbindung:
fun onCrowdsourcingToggled(enabled: Boolean) {
    Prefs.crowdsourcingEnabled = enabled
    if (enabled) {
        requestCoarseLocationPermission()      // bestehendes Permission-Muster nutzen
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        if (bm.isCharging) QuakeSensorService.start(this)
    } else {
        QuakeSensorService.stop(this)
    }
}
```
> Falls die Schalter aus der WebView kommen: eine `WebBridge`-Methode `setCrowdsourcing(enable: Boolean)` analog zu `setDndBypass` ergänzen und aus der Web-UI aufrufen. Andernfalls ein natives Settings-Element analog zu den bestehenden nativen Schaltern. Der Implementierer wählt den Weg, der zum vorhandenen Settings-Aufbau passt, und hält die i18n-Strings bereit.

- [ ] **Step 3: App kompiliert + i18n-Vollständigkeit prüfen**

Run: `cd tda/android && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (fehlt eine Übersetzung, meckert der Build bei `MissingTranslation` nicht automatisch — der Implementierer prüft manuell, dass alle 5 `values*`-Dateien die 5 neuen Keys enthalten).

- [ ] **Step 4: Gesamter Unit-Test-Lauf grün**

Run: `cd tda/android && ./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, alle Client-Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/app/alearthapp/MainActivity.kt android/app/src/main/java/app/alearthapp/WebBridge.kt android/app/src/main/res/values*/strings.xml
git commit -m "feat(client): crowdsourcing opt-in toggle + onboarding invite + i18n (5 langs)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review (vom Plan-Autor durchgeführt)

**1. Spec-Abdeckung:**
- Client A1 QuakeSensorService → Task 11. A2 Stillness → Task 7. A3 Shake → Task 8.
  A4 coarsenCell → Task 5. A5 AnonDeviceId → Task 6+9. A6 Reporter/Pinger → Task 10+11.
  A7 Settings/Onboarding → Task 12. A8 Manifest → Task 11.
- Server B1 ingest.py → Task 2+3. B2 serve_local → Task 4. Geteilte Annahme → Task 1.
- Testing-Abschnitt: alle im Spec genannten Tests haben einen Task. ✅ Keine Lücke.

**2. Platzhalter-Scan:** Kein „TBD/TODO/später ausfüllen". Konkrete Schwellen-Startwerte
in `SensorConfig`. Zwei bewusste Umsetzer-Hinweise (FGS-from-BG-Fallback, WebBridge- vs.
natives-Settings) sind benannte Entscheidungen mit vorgegebenem Vorgehen, keine offenen
Lücken.

**3. Typkonsistenz:** `SensorConfig`/`Stillness`/`AnonId`/`GeoCell.coarsenCell`/
`CrowdReport.*`/`evaluate_trigger`/`parse_*`/`TriggerIngestor` sind über die Tasks
konsistent benannt und signiert (in Task 7 definiert, in Task 8/11 konsumiert usw.).

**Bewusst außerhalb dieses Plans (Spec „Aufgeschoben"):** Play-Integrity-Attestierung,
Socket-Zweitkanal/Pushy, echte SNTP-Uhrmessung, Aufmerksamkeits-Modus, Oracle-Deploy
(die Produktiv-Loop-/Pipeline-Verdrahtung des Ingests ist ein Deploy-Schritt).
