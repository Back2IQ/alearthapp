# Plan B2: P0b Crowdsourcing-Detektion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aus dem im MVP nur *gesammelten* P0b-Sensorstrom (Phone-Trigger + Active-Pings) einen echtzeitfähigen Crowdsourcing-Detektor bauen, der nach der belegten Finazzi-Methode ein Erdbeben Sekunden nach Bruchbeginn erkennt, als `SourceEvent(source="p0b")` in die bestehende Fusion (Plan A) einspeist und damit einen signierten P0-Alarm auslöst.

**Architecture:** Ein Trigger-Gateway nimmt attestierte, ratenbegrenzte Phone-Signale entgegen (vergröberte Rasterposition + NTP-Zeit + Uhr-Unsicherheit) und schreibt sie in einen append-only Strom. Ein Density-Tracker führt pro Rasterzelle die Live-Netzgröße `ν_t` (aktuell online = idle+laden). Ein offline gefittetes Poisson-Hintergrundmodell `λ⁰(t)=exp(β₀+β₁·ν_t)` liefert die *dichteabhängige* Grundrate; ein Score-Detektor `S=N_ε/(ε·λ⁰)−1` testet in gleitendem Fenster auf Signifikanz, mit einer per Extremwert-Statistik (verallgemeinerte Pareto) auf **1 Fehlalarm/Jahr** kalibrierten Schwelle `h`. Ein Wellenfront-Konsistenzcheck (S-Wellen-Geschwindigkeit) und räumliches Clustering trennen Beben von stadtweit-gleichzeitigen Störungen (Feuerwerk, Torjubel). Bei Alarm bildet der Detektor einen Cluster, schätzt Ursprung/Intensität grob und emittiert ein `SourceEvent`, das der bestehende Korrelator zu einem kanonischen Ereignis führt und der bestehende Publisher als P0-Payload signiert verschickt.

**Tech Stack:** Python 3.12, asyncio, numpy (Poisson-Regression + GPD, ARM-Wheels vorhanden — **kein** scipy/statsmodels), cryptography (Attest-Verifikation, aus Plan A), pytest + pytest-asyncio. Baut auf Plan-A-Modulen (`domain.events`, `geo.cells`, `fusion.correlator`, `alert.payload`, `alert.publisher`, `stream.base`).

## Voraussetzungen & Einordnung in die Verstärkungs-Sequenz

Dies ist **Stufe 1** der sich gegenseitig verstärkenden Sequenz (B2 → B). P0b hängt an keinem Fremd-Datenzugang, nur an der Nutzerzahl — deshalb zuerst: es liefert Tag-1-Sekunden-EEW und startet das Schwungrad (Alarm → Vertrauen → mehr Nutzer → dichteres Netz → kürzere Detektion). Stufe 2 (Plan B, P0a) setzt die Stations-Picks als unabhängigen Bestätigungs-/Kalibrierkanal darauf; die Fusion (Plan A) ist bereits der gemeinsame Konvergenzpunkt beider.

**Was dieser Plan voraussetzt (harte Abhängigkeiten):**
- **Plan A ist ausgeführt** (mit den fünf Deep-Dive-Korrekturen, insbesondere Korrektur 1 — Tier NICHT hartkodiert). Dieser Plan liefert Korrektur 1 konkret nach (Task 11): `tier` wird aus Quellen/Zustand abgeleitet, `p0b`-only ⇒ `"P0"`.
- **Der P0b-Sammel-Client existiert** als MVP-Baustein „P0b Sensorik — nur Sammeln" (Sensing bei idle+laden, Trigger-Upload mit Attestierung, Active-Ping). Dieser Plan implementiert die **Server-Seite** (Gateway + Detektor). Wo der Client-Sammelteil noch fehlt, ist er ein **paralleler Client-Task in Plan C** — dieser Plan definiert das Wire-Format (Task 1) als Vertrag zwischen beiden. Solange der reale Client fehlt, speisen die Tests aufgezeichnete/synthetische Trigger im exakten Wire-Format ein (klar als Szenario gekennzeichnet, nie als Produktions-Alarm).

**Verschobene Lücken (bewusst, im Self-Review zu nennen):**
- **Play-Integrity-Attestierung** wird als Schnittstelle (`AttestationVerifier`-Protokoll) modelliert und mit einem Hardware-Schlüssel-Fallback-Stub implementiert; die echte Google-Play-Integrity-Anbindung + StrongBox-Pfad ist Plan-C/Plan-F-Scope (§3 „Attestierungs-Fallback"). Der Detektor darf nie allein auf binärem Play-Integrity-Verdikt beruhen.
- **Aufmerksamkeits-Modus-Rückkanal zum Client** (Geräte im Umkreis erhöhen Abtastrate) wird serverseitig als Signal erzeugt (Task 10), aber die Client-Umsetzung ist Plan C.

---

## Global Constraints

- Spec ist `../../../../UMSETZUNGSPLAN.md` (Repo-Wurzel `Earthquake/`); dieser Plan implementiert die P0b-**Detektion** — die Freischaltung des im MVP nur gesammelten Sensorstroms (§2.2, §3, §8-Zeile „P0b Sensorik — nur Sammeln").
- **Wissenschaftliche Grundlage ist fixiert** (RECHERCHE-P0A.md §4, Finazzi & Fassò 2017 / Finazzi et al. 2022 / Finazzi, Bossu & Cotton 2024):
  - Hintergrund (kein Beben): Poisson-Prozess `λ⁰(t) = exp(β₀ + β₁·ν_t)`, `ν_t` = Zahl aktuell aktiver Geräte.
  - Detektor: Score `S(ε,t) = N_ε^t / (ε·λ⁰(t)) − 1`, Alarm wenn `S > h`.
  - Schwelle `h`: aus dem Extremwert-Tail (verallgemeinerte Pareto) auf **~1 Fehlalarm/Jahr** kalibriert — deckt sich mit dem Projekt-Anspruch „1 Fehlalarm/Jahr".
  - Detektionsverzögerung: 1–2 s bei >300 aktiven Phones, bis ~10 s bei <50; Erkennung ~90 % ab Report-Fraktion φ > 0,25.
- **Signifikanz IMMER relativ zur Live-Netzdichte, NIE absoluter Schwellwert** (§3): jede Detektionsentscheidung geht durch `λ⁰(ν_t)`.
- **Kein absoluter Trigger-Schwellwert ersetzt die Statistik**, ABER es gibt eine **dichteunabhängige physikalische Mindestschwelle**: MEMS-Rauschen ⇒ P0b sieht nur stärkere Nahfeldereignisse, grob ab MMI ~IV (§11 „Schwellen-Startwerte"). Diese Grenze ist eine Eigenschaft der Sensorik, kein Detektor-Tuning.
- **Trigger tragen: vergröberte Rasterposition (nie Rohkoordinate), NTP-Zeit, Uhr-Unsicherheit** (§3, §7.2 Datenminimierung). Rohposition darf den Server nie erreichen.
- **Zeit-Gate:** Ein Gerät mit unplausibler/nicht synchronisierbarer Uhr wird geringer gewichtet oder ausgeschlossen; serverseitige Ankunftszeit ist Gegenprüfung (§3 „Zeitsynchronisation").
- **Reputationsgewichtung:** neue/auffällige Geräte zählen weniger; Reputation bindet an Hardware-Attest, nicht an App-Instanz-ID (§3 „Angriffs-Schutz", „Reinstall-Härtung").
- **Nullkosten (hart):** läuft auf der Oracle-„Always Free"-ARM-VM. **numpy ist erlaubt** (ARM-Wheels), **scipy/statsmodels sind es nicht** (Poisson-Regression + GPD selbst implementieren, wenige Zeilen). Keine kostenpflichtigen Managed-Services.
- **Ressourcen-Isolation (§6.1):** Der Trigger-Ingest ist der wahrscheinlichste Überlast-/Angriffspfad. Das Gateway ist zustandslos und ratenbegrenzt; eine Trigger-Flut darf den Detektor/Publisher nicht aushungern (Rate-Limit + Backpressure am Gateway, nicht im Detektor).
- **Payload ≤ 1 KB, nur String-Werte; alle Zeiten UTC, Wire-Format Unix-Millisekunden als String** (aus Plan A, unverändert).
- **Geo-Rasterzellen: 0,5°-Grid, Zell-ID `c{floor(lat/0.5)}_{floor(lon/0.5)}`** — identische Formel wie Plan A (`geo.cells.cell_id`). Die Detektion arbeitet zusätzlich auf einem **feineren Detektionsraster** (Task 3) für die räumliche Auflösung; das grobe 0,5°-Publish-Raster bleibt für den FCM-Fanout.
- **Keine Mock-Ereignisse in Produktion:** jeder synthetische Trigger-Burst in Tests ist als Szenario markiert; ein daraus resultierender Alarm trägt in keinem Produktionspfad `test="0"`.
- Python: Zeilenlänge 100, Typannotationen überall, keine globalen Singletons; Code/Kommentare Englisch. TDD strikt (rot → grün → commit).
- Arbeitsverzeichnis: `tda/server/`.

## Wissenschaftliche Notation (verbindlich für alle Tasks)

| Symbol | Bedeutung | Startwert / Quelle |
|---|---|---|
| `ν_t` | aktive Geräte in einer Detektionszelle zur Zeit t | gemessen (Density-Tracker) |
| `λ⁰(t)` | erwartete Fehl-Trigger-Rate/s ohne Beben | `exp(β₀+β₁·ν_t)`, β aus Fit |
| `ε` | Score-Fensterlänge | 20 s (Finazzi ~30 s; kürzer = schneller, gegen `h` kalibriert) |
| `N_ε^t` | reputationsgewichtete Triggerzahl im Fenster [t−ε, t] | gemessen |
| `S` | Score | `N_ε/(ε·λ⁰) − 1` |
| `h` | Alarmschwelle | GPD-Return-Level für 1 Fehlalarm/Jahr |
| `φ` | Report-Fraktion (Anteil auslösender aktiver Geräte) | Diagnose; ~90 % Erkennung ab φ>0,25 |
| `v_s` | S-/Oberflächenwellen-Geschwindigkeit (Phone-Trigger) | 3,5 km/s (Band 3,0–4,5) |

## File Structure (Zielbild dieses Plans)

```
tda/server/src/tda_server/p0b/
  __init__.py
  signals.py        # PhoneTrigger, ActivePing, Wire-Format (serialize/deserialize), coarsen_cell
  gateway.py        # AttestationVerifier-Protokoll, run_trigger_gateway (rate-limit, clock-gate)
  density.py        # DensityTracker (nu_t je Detektionszelle, gleitendes Fenster)
  background.py     # fit_poisson (Newton-Raphson), BackgroundModel.rate(nu)
  threshold.py      # gpd_fit_mom, return_level, calibrate_threshold
  detector.py       # ScoreDetector (S je Zelle), P0bDetector (Orchestrierung → SourceEvent)
  wavefront.py      # wavefront_consistent (S-Wellen-Front-Check)
  cluster.py        # form_cluster (räumliche Zusammenfassung co-triggernder Zellen)
  reputation.py     # ReputationStore, trigger_weight
  replay.py         # SzenarioGenerator + Replay-Harness
tda/server/scripts/
  fit_background.py      # β₀,β₁ aus aufgezeichnetem Ruhe-Strom fitten
  calibrate_threshold.py # h aus Score-Historie (GPD) auf 1/Jahr fitten
  gen_p0b_scenario.py    # synthetischen Trigger-Burst (Türkei-Geometrie) erzeugen
tda/server/tests/
  test_p0b_signals.py test_p0b_gateway.py test_p0b_density.py
  test_p0b_background.py test_p0b_threshold.py test_p0b_detector.py
  test_p0b_wavefront.py test_p0b_cluster.py test_p0b_reputation.py
  test_p0b_tier.py test_p0b_pipeline.py test_p0b_replay.py
  fixtures/p0b_scenario_turkiye.json   # aufgezeichnetes/synthetisches Szenario
```

---

### Task 1: P0b-Signalmodell + Wire-Format (Vertrag zum Client)

**Files:**
- Create: `server/src/tda_server/p0b/__init__.py`, `server/src/tda_server/p0b/signals.py`
- Test: `server/tests/test_p0b_signals.py`

**Interfaces:**
- Consumes: `geo.cells.cell_id`, `geo.cells.CELL_DEG` (Plan A, Task 3)
- Produces:
  - `PhoneTrigger(device_hash: str, cell: str, trigger_ms: int, clock_unc_ms: int, received_ms: int, attest_ok: bool)` (frozen dataclass) — `cell` ist die **vergröberte** Detektionszelle, nie Rohkoordinate.
  - `ActivePing(device_hash: str, cell: str, ping_ms: int, received_ms: int)` (frozen dataclass) — periodisches „ich bin aktiv (idle+laden)"-Signal.
  - `DETECT_CELL_DEG = 0.1` (feineres Detektionsraster als das 0,5°-Publish-Raster)
  - `coarsen_cell(lat: float, lon: float, deg: float = DETECT_CELL_DEG) -> str` — Format `d{floor(lat/deg)}_{floor(lon/deg)}`; der Client ruft dies auf und sendet NUR die Zell-ID.
  - `detect_cell_center(cell: str, deg: float = DETECT_CELL_DEG) -> tuple[float, float]` — Zellmittelpunkt (für Ursprungsschätzung), invers zu `coarsen_cell`.
  - `serialize_trigger(t: PhoneTrigger) -> dict[str, str]`, `deserialize_trigger(d) -> PhoneTrigger`
  - `serialize_ping(p: ActivePing) -> dict[str, str]`, `deserialize_ping(d) -> ActivePing`

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_signals.py`:
```python
from tda_server.p0b.signals import (
    ActivePing, PhoneTrigger, coarsen_cell, detect_cell_center,
    deserialize_ping, deserialize_trigger, serialize_ping, serialize_trigger,
)

def test_coarsen_cell_hides_raw_position():
    # two nearby raw positions collapse into the same 0.1-degree detection cell
    assert coarsen_cell(41.02, 28.97) == coarsen_cell(41.08, 28.93)
    assert coarsen_cell(41.02, 28.97) == "d410_289"

def test_detect_cell_center_roundtrips_into_cell():
    lat, lon = detect_cell_center("d410_289")
    assert coarsen_cell(lat, lon) == "d410_289"
    assert 41.0 <= lat < 41.1 and 28.9 <= lon < 29.0

def test_trigger_is_frozen():
    t = PhoneTrigger("dev1", "d410_289", 1000, 50, 1200, True)
    try:
        t.trigger_ms = 2000  # type: ignore[misc]
        assert False, "should be frozen"
    except AttributeError:
        pass

def test_trigger_roundtrip_all_strings():
    t = PhoneTrigger("devA", "d410_289", 1755691200000, 40, 1755691200300, True)
    d = serialize_trigger(t)
    assert all(isinstance(v, str) for v in d.values())
    assert deserialize_trigger(d) == t

def test_ping_roundtrip():
    p = ActivePing("devA", "d410_289", 1755691200000, 1755691200100)
    assert deserialize_ping(serialize_ping(p)) == p
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_signals.py -q`
Expected: `ModuleNotFoundError: tda_server.p0b`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/__init__.py`: leer.

`src/tda_server/p0b/signals.py`:
```python
from __future__ import annotations

import math
from dataclasses import dataclass

DETECT_CELL_DEG = 0.1


@dataclass(frozen=True)
class PhoneTrigger:
    device_hash: str
    cell: str
    trigger_ms: int
    clock_unc_ms: int
    received_ms: int
    attest_ok: bool


@dataclass(frozen=True)
class ActivePing:
    device_hash: str
    cell: str
    ping_ms: int
    received_ms: int


def coarsen_cell(lat: float, lon: float, deg: float = DETECT_CELL_DEG) -> str:
    return f"d{math.floor(lat / deg)}_{math.floor(lon / deg)}"


def detect_cell_center(cell: str, deg: float = DETECT_CELL_DEG) -> tuple[float, float]:
    body = cell[1:]  # strip leading 'd'
    lat_idx, lon_idx = body.split("_")
    lat = (int(lat_idx) + 0.5) * deg
    lon = (int(lon_idx) + 0.5) * deg
    return lat, lon


def serialize_trigger(t: PhoneTrigger) -> dict[str, str]:
    return {
        "device_hash": t.device_hash,
        "cell": t.cell,
        "trigger_ms": str(t.trigger_ms),
        "clock_unc_ms": str(t.clock_unc_ms),
        "received_ms": str(t.received_ms),
        "attest_ok": "1" if t.attest_ok else "0",
    }


def deserialize_trigger(d: dict[str, str]) -> PhoneTrigger:
    return PhoneTrigger(
        device_hash=d["device_hash"],
        cell=d["cell"],
        trigger_ms=int(d["trigger_ms"]),
        clock_unc_ms=int(d["clock_unc_ms"]),
        received_ms=int(d["received_ms"]),
        attest_ok=d["attest_ok"] == "1",
    )


def serialize_ping(p: ActivePing) -> dict[str, str]:
    return {
        "device_hash": p.device_hash,
        "cell": p.cell,
        "ping_ms": str(p.ping_ms),
        "received_ms": str(p.received_ms),
    }


def deserialize_ping(d: dict[str, str]) -> ActivePing:
    return ActivePing(
        device_hash=d["device_hash"],
        cell=d["cell"],
        ping_ms=int(d["ping_ms"]),
        received_ms=int(d["received_ms"]),
    )
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_signals.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): phone trigger/active-ping signal model + wire format"
```

---

### Task 2: Trigger-Gateway — Attestierung, Rate-Limit, Uhr-Gate

**Files:**
- Create: `server/src/tda_server/p0b/gateway.py`
- Test: `server/tests/test_p0b_gateway.py`

**Interfaces:**
- Consumes: `PhoneTrigger`, `serialize_trigger` (Task 1); `EventStream` (Plan A, `stream.base`)
- Produces:
  - `AttestationVerifier` (Protocol): `verify(device_hash: str, token: str) -> bool` — echte Play-Integrity-/StrongBox-Anbindung ist Plan C/F; hier Schnittstelle + `AllowlistVerifier` (Test-Stub).
  - `RateLimiter(max_per_window: int, window_ms: int)` mit `allow(device_hash: str, now_ms: int) -> bool` (Sliding-Window pro Gerät).
  - `TriggerGate(clock_unc_max_ms: int = 2000, server_skew_max_ms: int = 15000)` mit `accept(t: PhoneTrigger) -> bool` — verwirft Trigger mit zu großer Uhr-Unsicherheit oder wenn `|trigger_ms − received_ms|` den Server-Skew-Rahmen sprengt (implausible Uhr).
  - `async run_trigger_gateway(incoming: AsyncIterator[tuple[PhoneTrigger, str]], stream: EventStream, *, verifier, limiter, gate) -> None` — verifiziert Attest, prüft Rate-Limit + Uhr-Gate, schreibt akzeptierte Trigger (mit gesetztem `attest_ok`) in den Strom; verworfene werden gezählt/geloggt, nie in den Strom.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_gateway.py`:
```python
import asyncio
from tda_server.p0b.gateway import (
    AllowlistVerifier, RateLimiter, TriggerGate, run_trigger_gateway,
)
from tda_server.p0b.signals import PhoneTrigger, deserialize_trigger
from tda_server.stream.base import InMemoryStream


def trig(dev: str, tms: int, unc: int = 50, rec: int | None = None) -> PhoneTrigger:
    return PhoneTrigger(dev, "d410_289", tms, unc, tms + 100 if rec is None else rec, False)


def test_rate_limiter_sliding_window():
    lim = RateLimiter(max_per_window=2, window_ms=1000)
    assert lim.allow("d", 0) and lim.allow("d", 100)
    assert not lim.allow("d", 200)          # third within 1s -> blocked
    assert lim.allow("d", 1200)             # window rolled forward

def test_gate_rejects_bad_clock():
    gate = TriggerGate(clock_unc_max_ms=2000, server_skew_max_ms=15000)
    assert gate.accept(trig("d", 1000, unc=50))
    assert not gate.accept(trig("d", 1000, unc=5000))            # too uncertain
    assert not gate.accept(trig("d", 1000, unc=50, rec=1000000)) # implausible skew

async def test_gateway_writes_only_accepted():
    stream = InMemoryStream()
    verifier = AllowlistVerifier({"good"})
    limiter = RateLimiter(max_per_window=10, window_ms=1000)
    gate = TriggerGate()
    async def incoming():
        yield trig("good", 1000), "tok"      # accepted
        yield trig("bad", 1100), "tok"       # attest fails
        yield trig("good", 1200, unc=9000), "tok"  # clock gate fails
    await run_trigger_gateway(incoming(), stream, verifier=verifier,
                              limiter=limiter, gate=gate)
    got = []
    async def drain():
        async for _sid, rec in stream.read():
            got.append(deserialize_trigger(rec))
            if len(got) == 1:
                return
    await asyncio.wait_for(drain(), timeout=1)
    assert len(got) == 1 and got[0].device_hash == "good" and got[0].attest_ok is True
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_gateway.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/gateway.py`:
```python
from __future__ import annotations

import logging
from collections import deque
from dataclasses import replace
from typing import AsyncIterator, Protocol

from tda_server.p0b.signals import PhoneTrigger, serialize_trigger
from tda_server.stream.base import EventStream

log = logging.getLogger(__name__)


class AttestationVerifier(Protocol):
    def verify(self, device_hash: str, token: str) -> bool: ...


class AllowlistVerifier:
    """Test/stub verifier. Real Play-Integrity + StrongBox path is Plan C/F.
    Never let a binary Play-Integrity verdict alone gate a device (spec 3)."""

    def __init__(self, allowed: set[str]) -> None:
        self._allowed = allowed

    def verify(self, device_hash: str, token: str) -> bool:
        return device_hash in self._allowed


class RateLimiter:
    def __init__(self, max_per_window: int, window_ms: int) -> None:
        self.max = max_per_window
        self.window = window_ms
        self._hits: dict[str, deque[int]] = {}

    def allow(self, device_hash: str, now_ms: int) -> bool:
        dq = self._hits.setdefault(device_hash, deque())
        while dq and dq[0] <= now_ms - self.window:
            dq.popleft()
        if len(dq) >= self.max:
            return False
        dq.append(now_ms)
        return True


class TriggerGate:
    def __init__(self, clock_unc_max_ms: int = 2000, server_skew_max_ms: int = 15000) -> None:
        self.clock_unc_max = clock_unc_max_ms
        self.server_skew_max = server_skew_max_ms

    def accept(self, t: PhoneTrigger) -> bool:
        if t.clock_unc_ms > self.clock_unc_max:
            return False
        if abs(t.trigger_ms - t.received_ms) > self.server_skew_max:
            return False
        return True


async def run_trigger_gateway(
    incoming: AsyncIterator[tuple[PhoneTrigger, str]],
    stream: EventStream,
    *,
    verifier: AttestationVerifier,
    limiter: RateLimiter,
    gate: TriggerGate,
) -> None:
    async for trigger, token in incoming:
        ok = verifier.verify(trigger.device_hash, token)
        if not (gate.accept(trigger) and limiter.allow(trigger.device_hash, trigger.received_ms)):
            continue
        # attest_ok is authoritative from the server, not from the client claim
        await stream.append(serialize_trigger(replace(trigger, attest_ok=ok)))
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_gateway.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): trigger gateway with attestation, rate-limit, clock gate"
```

---

### Task 3: Density-Tracker — Live-Netzgröße ν_t je Detektionszelle

**Files:**
- Create: `server/src/tda_server/p0b/density.py`
- Test: `server/tests/test_p0b_density.py`

**Interfaces:**
- Consumes: `ActivePing` (Task 1)
- Produces:
  - `DensityTracker(active_ttl_ms: int = 2_700_000)` — ein Gerät gilt `active_ttl_ms` (Default 45 min = 1,5× das 30-min-Ping-Intervall) nach seinem letzten Ping als online.
    - `observe(ping: ActivePing) -> None`
    - `nu(cell: str, now_ms: int) -> int` — Zahl aktuell online Geräte in der Zelle.
    - `prune(now_ms: int) -> None` — entfernt abgelaufene Einträge (gegen Speicherwachstum).
  - `ν_t` (die Live-Netzdichte) ist die einzige zulässige Bezugsgröße für Signifikanz (§3).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_density.py`:
```python
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.signals import ActivePing


def ping(dev: str, cell: str, ms: int) -> ActivePing:
    return ActivePing(dev, cell, ms, ms + 50)


def test_counts_distinct_active_devices():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.observe(ping("b", "d1_1", 100))
    dt.observe(ping("a", "d1_1", 200))     # same device re-ping, still 2 distinct
    assert dt.nu("d1_1", 300) == 2

def test_expired_devices_drop_out():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    assert dt.nu("d1_1", 900) == 1
    assert dt.nu("d1_1", 1100) == 0        # ttl passed

def test_cells_are_independent():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.observe(ping("b", "d2_2", 0))
    assert dt.nu("d1_1", 100) == 1 and dt.nu("d2_2", 100) == 1

def test_prune_frees_expired():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.prune(2000)
    assert dt.nu("d1_1", 2000) == 0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_density.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/density.py`:
```python
from __future__ import annotations

from tda_server.p0b.signals import ActivePing


class DensityTracker:
    """Live network size nu_t per detection cell: distinct devices whose last
    active ping is within active_ttl_ms. This is the ONLY admissible baseline
    for significance (spec 3: relative to live density, never absolute)."""

    def __init__(self, active_ttl_ms: int = 2_700_000) -> None:
        self.active_ttl_ms = active_ttl_ms
        # cell -> device_hash -> last_ping_ms
        self._last: dict[str, dict[str, int]] = {}

    def observe(self, ping: ActivePing) -> None:
        self._last.setdefault(ping.cell, {})[ping.device_hash] = ping.ping_ms

    def nu(self, cell: str, now_ms: int) -> int:
        devs = self._last.get(cell)
        if not devs:
            return 0
        cutoff = now_ms - self.active_ttl_ms
        return sum(1 for last in devs.values() if last > cutoff)

    def prune(self, now_ms: int) -> None:
        cutoff = now_ms - self.active_ttl_ms
        for cell in list(self._last):
            devs = self._last[cell]
            for dev in [d for d, last in devs.items() if last <= cutoff]:
                del devs[dev]
            if not devs:
                del self._last[cell]
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_density.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): live density tracker (nu_t per detection cell)"
```

---

### Task 4: Hintergrundmodell — Poisson-Regression λ⁰(ν)=exp(β₀+β₁ν)

**Files:**
- Create: `server/src/tda_server/p0b/background.py`
- Modify: `server/pyproject.toml` (numpy zu dependencies)
- Test: `server/tests/test_p0b_background.py`

**Interfaces:**
- Produces:
  - `fit_poisson(nu: np.ndarray, counts: np.ndarray, iters: int = 50, tol: float = 1e-8) -> tuple[float, float]` — Newton-Raphson-MLE für Log-Link-Poisson, gibt `(β₀, β₁)`. Kein scipy.
  - `BackgroundModel(b0: float, b1: float)` mit `rate(nu: float) -> float` (= `exp(β₀+β₁·ν)`, Trigger/s) und `expected(nu: float, eps_s: float) -> float` (= `rate·ε`).
  - Klassenmethode `BackgroundModel.fit(nu, counts, bin_s) -> BackgroundModel` — `counts` sind Trigger je Zeitbin der Länge `bin_s`; intern auf Rate/s normiert.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_background.py`:
```python
import numpy as np
from tda_server.p0b.background import BackgroundModel, fit_poisson


def test_fit_recovers_known_coefficients():
    rng = np.random.default_rng(42)
    b0_true, b1_true = -6.0, 0.01
    nu = rng.integers(20, 500, size=4000).astype(float)
    lam = np.exp(b0_true + b1_true * nu)        # expected count per bin
    counts = rng.poisson(lam).astype(float)
    b0, b1 = fit_poisson(nu, counts)
    assert abs(b0 - b0_true) < 0.2
    assert abs(b1 - b1_true) < 0.002

def test_rate_monotone_in_density():
    m = BackgroundModel(b0=-6.0, b1=0.01)
    assert m.rate(500) > m.rate(50)             # denser network -> more false triggers
    assert m.expected(nu=100, eps_s=20) == m.rate(100) * 20

def test_fit_from_binned_counts_normalises_per_second():
    # 10 devices, bin length 10s, counts imply a stable per-second rate
    nu = np.full(200, 100.0)
    counts = np.full(200, 5.0)                    # 5 triggers per 10s bin
    m = BackgroundModel.fit(nu, counts, bin_s=10.0)
    assert abs(m.rate(100) - 0.5) < 0.05         # ~0.5 triggers/s at nu=100
```

- [ ] **Step 2: numpy zu pyproject.toml hinzufügen, Test ausführen — muss fehlschlagen**

`pyproject.toml` `dependencies`-Liste um `"numpy>=1.26"` ergänzen, dann:
```bash
.venv/Scripts/python -m pip install -e ".[dev]"
.venv/Scripts/python -m pytest tests/test_p0b_background.py -q
```
Expected: `ModuleNotFoundError: tda_server.p0b.background`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/background.py`:
```python
from __future__ import annotations

import numpy as np


def fit_poisson(nu: np.ndarray, counts: np.ndarray,
                iters: int = 50, tol: float = 1e-8) -> tuple[float, float]:
    """Newton-Raphson MLE for a log-link Poisson GLM: counts ~ Poisson(exp(b0+b1*nu)).
    Design matrix X = [1, nu]. No scipy (ARM-friendly)."""
    x = np.column_stack([np.ones_like(nu, dtype=float), nu.astype(float)])
    y = counts.astype(float)
    beta = np.array([np.log(max(y.mean(), 1e-6)), 0.0])
    for _ in range(iters):
        eta = x @ beta
        mu = np.exp(eta)
        grad = x.T @ (y - mu)
        w = mu
        hess = (x.T * w) @ x                       # X^T W X
        step = np.linalg.solve(hess, grad)
        beta = beta + step
        if np.max(np.abs(step)) < tol:
            break
    return float(beta[0]), float(beta[1])


class BackgroundModel:
    def __init__(self, b0: float, b1: float) -> None:
        self.b0 = b0
        self.b1 = b1

    def rate(self, nu: float) -> float:
        """Expected false-trigger rate per second at live density nu."""
        return float(np.exp(self.b0 + self.b1 * nu))

    def expected(self, nu: float, eps_s: float) -> float:
        return self.rate(nu) * eps_s

    @classmethod
    def fit(cls, nu: np.ndarray, counts: np.ndarray, bin_s: float) -> "BackgroundModel":
        # fit on per-bin counts, then shift intercept to per-second rate
        b0, b1 = fit_poisson(np.asarray(nu), np.asarray(counts))
        b0_per_s = b0 - np.log(bin_s)
        return cls(b0=float(b0_per_s), b1=b1)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_background.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): Poisson background model (Newton-Raphson, numpy only)"
```

---

### Task 5: Score-Detektor — S = N_ε/(ε·λ⁰) − 1 je Zelle

**Files:**
- Create: `server/src/tda_server/p0b/detector.py` (Teil 1: `ScoreDetector`)
- Test: `server/tests/test_p0b_detector.py` (Teil 1)

**Interfaces:**
- Consumes: `BackgroundModel` (Task 4), `DensityTracker` (Task 3)
- Produces:
  - `ScoreWindow(eps_s: float)` mit `add(cell: str, ts_ms: int, weight: float) -> None` und `count(cell: str, now_ms: int) -> float` (reputationsgewichtete Summe `N_ε` im Fenster [now−ε, now]); `evict(now_ms)`.
  - `ScoreDetector(background: BackgroundModel, density: DensityTracker, eps_s: float = 20.0)` mit `score(cell: str, now_ms: int, window: ScoreWindow) -> float` — `S = N_ε/(ε·λ⁰(ν)) − 1`, wobei `λ⁰` aus der **Live-Dichte** kommt. Bei `ν=0` (kein aktives Gerät) gibt `score` `−1.0` (kein Signal möglich).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_detector.py`:
```python
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.detector import ScoreDetector, ScoreWindow
from tda_server.p0b.signals import ActivePing


def make_density(cell: str, n: int, now: int) -> DensityTracker:
    dt = DensityTracker(active_ttl_ms=10_000)
    for i in range(n):
        dt.observe(ActivePing(f"dev{i}", cell, now, now))
    return dt


def test_window_counts_within_eps_and_evicts():
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 1000, 1.0)
    w.add("d1_1", 5000, 1.0)
    assert w.count("d1_1", 10_000) == 2.0
    assert w.count("d1_1", 30_000) == 1.0     # first (t=1000) now outside 20s

def test_weighted_count():
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 1000, 0.3)
    w.add("d1_1", 1000, 1.0)
    assert abs(w.count("d1_1", 2000) - 1.3) < 1e-9

def test_score_zero_when_triggers_match_background():
    # background rate 0.5/s at nu=100, eps=20s -> expected 10 triggers => S ~ 0
    bg = BackgroundModel(b0=-6.0, b1=0.01)   # rate(100)=exp(-6+1)=exp(-5)? adjust below
    det = ScoreDetector(bg, make_density("d1_1", 100, 0), eps_s=20.0)
    exp = bg.expected(nu=100, eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    for i in range(round(exp)):
        w.add("d1_1", i * 10, 1.0)
    s = det.score("d1_1", 200, w)
    assert -0.3 < s < 0.3                      # roughly at background

def test_score_high_on_burst():
    bg = BackgroundModel(b0=-8.0, b1=0.005)    # low background
    det = ScoreDetector(bg, make_density("d1_1", 100, 0), eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    for i in range(40):                        # sudden burst far above background
        w.add("d1_1", 100 + i, 1.0)
    assert det.score("d1_1", 200, w) > 10.0

def test_score_minus_one_when_no_active_devices():
    bg = BackgroundModel(b0=-8.0, b1=0.005)
    det = ScoreDetector(bg, DensityTracker(), eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 100, 1.0)
    assert det.score("d1_1", 200, w) == -1.0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_detector.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/detector.py` (Teil 1 — `P0bDetector` folgt in Task 10):
```python
from __future__ import annotations

from collections import defaultdict, deque

from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.density import DensityTracker


class ScoreWindow:
    """Sliding window of (timestamp, weight) trigger events per cell."""

    def __init__(self, eps_s: float) -> None:
        self.eps_ms = int(eps_s * 1000)
        self._ev: dict[str, deque[tuple[int, float]]] = defaultdict(deque)

    def add(self, cell: str, ts_ms: int, weight: float) -> None:
        self._ev[cell].append((ts_ms, weight))

    def count(self, cell: str, now_ms: int) -> float:
        dq = self._ev.get(cell)
        if not dq:
            return 0.0
        cutoff = now_ms - self.eps_ms
        while dq and dq[0][0] <= cutoff:
            dq.popleft()
        return sum(w for _ts, w in dq)

    def evict(self, now_ms: int) -> None:
        cutoff = now_ms - self.eps_ms
        for cell in list(self._ev):
            dq = self._ev[cell]
            while dq and dq[0][0] <= cutoff:
                dq.popleft()
            if not dq:
                del self._ev[cell]


class ScoreDetector:
    def __init__(self, background: BackgroundModel, density: DensityTracker,
                 eps_s: float = 20.0) -> None:
        self.bg = background
        self.density = density
        self.eps_s = eps_s

    def score(self, cell: str, now_ms: int, window: ScoreWindow) -> float:
        nu = self.density.nu(cell, now_ms)
        if nu <= 0:
            return -1.0
        n_eps = window.count(cell, now_ms)
        expected = self.bg.expected(nu, self.eps_s)
        if expected <= 0:
            return -1.0
        return n_eps / expected - 1.0
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_detector.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): score detector S=N/(eps*lambda0)-1 over live density"
```

---

### Task 6: Schwellenkalibrierung — GPD-Tail auf 1 Fehlalarm/Jahr

**Files:**
- Create: `server/src/tda_server/p0b/threshold.py`
- Test: `server/tests/test_p0b_threshold.py`

**Interfaces:**
- Produces:
  - `gpd_fit_mom(exceed: np.ndarray) -> tuple[float, float]` — Methoden-der-Momente-Schätzer der verallgemeinerten Pareto-Verteilung über Überschreitungen `exceed = scores[scores>u] − u`: `ξ = 0.5·(1 − m²/v)`, `σ = 0.5·m·(m²/v + 1)` mit `m=mean`, `v=var`. Kein scipy.
  - `return_level(u: float, xi: float, sigma: float, zeta_u: float, n_per_year: float, target_per_year: float = 1.0) -> float` — POT-Return-Level `h`: Überschreitungswahrscheinlichkeit `p = target/n_per_year`, `h = u + (σ/ξ)·((p/ζ_u)^(−ξ) − 1)` (bzw. `u − σ·ln(p/ζ_u)` für ξ→0).
  - `calibrate_threshold(scores: np.ndarray, *, eval_interval_s: float, quantile: float = 0.95, target_per_year: float = 1.0) -> float` — nimmt eine Score-Historie aus dem **Ruhebetrieb** (keine echten Beben), wählt `u` als `quantile`, fittet GPD, rechnet `n_per_year` aus dem Auswerteintervall und gibt `h`.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_threshold.py`:
```python
import numpy as np
from tda_server.p0b.threshold import calibrate_threshold, gpd_fit_mom, return_level


def test_gpd_fit_recovers_scale_for_exponential_tail():
    # exponential exceedances => GPD with xi≈0, sigma≈scale
    rng = np.random.default_rng(0)
    exceed = rng.exponential(scale=2.0, size=50_000)
    xi, sigma = gpd_fit_mom(exceed)
    assert abs(xi) < 0.1
    assert abs(sigma - 2.0) < 0.2

def test_return_level_monotone_in_rarity():
    h_common = return_level(1.0, xi=0.1, sigma=2.0, zeta_u=0.05,
                            n_per_year=1_000_000, target_per_year=100)
    h_rare = return_level(1.0, xi=0.1, sigma=2.0, zeta_u=0.05,
                          n_per_year=1_000_000, target_per_year=1)
    assert h_rare > h_common               # 1/yr threshold higher than 100/yr

def test_calibrate_threshold_gives_rare_level():
    rng = np.random.default_rng(1)
    # 1 sample/s of quiet-time scores over a simulated ~11.6 day window
    scores = rng.exponential(scale=1.0, size=1_000_000) - 1.0
    h = calibrate_threshold(scores, eval_interval_s=1.0, quantile=0.95,
                            target_per_year=1.0)
    # empirically almost no quiet-time score should exceed h
    assert (scores > h).sum() <= 5
    assert h > np.quantile(scores, 0.99)
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_threshold.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/threshold.py`:
```python
from __future__ import annotations

import math

import numpy as np

SECONDS_PER_YEAR = 365.25 * 24 * 3600


def gpd_fit_mom(exceed: np.ndarray) -> tuple[float, float]:
    """Method-of-moments GPD fit over threshold exceedances.
    mean m = sigma/(1-xi); var v = sigma^2/((1-xi)^2 (1-2xi))."""
    m = float(np.mean(exceed))
    v = float(np.var(exceed))
    if v <= 0:
        return 0.0, max(m, 1e-9)
    xi = 0.5 * (1.0 - m * m / v)
    sigma = 0.5 * m * (m * m / v + 1.0)
    return xi, max(sigma, 1e-9)


def return_level(u: float, xi: float, sigma: float, zeta_u: float,
                 n_per_year: float, target_per_year: float = 1.0) -> float:
    """POT return level: value exceeded target_per_year times per year."""
    p = target_per_year / n_per_year          # per-observation exceedance prob
    ratio = p / zeta_u
    if abs(xi) < 1e-6:
        return u - sigma * math.log(ratio)
    return u + (sigma / xi) * (ratio ** (-xi) - 1.0)


def calibrate_threshold(scores: np.ndarray, *, eval_interval_s: float,
                        quantile: float = 0.95, target_per_year: float = 1.0) -> float:
    scores = np.asarray(scores, dtype=float)
    u = float(np.quantile(scores, quantile))
    exceed = scores[scores > u] - u
    if exceed.size < 50:
        # too few exceedances for a stable tail fit: fall back to a high quantile
        return float(np.quantile(scores, 1.0 - target_per_year /
                                 (SECONDS_PER_YEAR / eval_interval_s)))
    xi, sigma = gpd_fit_mom(exceed)
    zeta_u = exceed.size / scores.size
    n_per_year = SECONDS_PER_YEAR / eval_interval_s
    return return_level(u, xi, sigma, zeta_u, n_per_year, target_per_year)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_threshold.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): GPD tail threshold calibration for 1 false alarm/year"
```

---

### Task 7: Wellenfront-Konsistenz — Beben vs. stadtweit-gleichzeitige Störung

**Files:**
- Create: `server/src/tda_server/p0b/wavefront.py`
- Test: `server/tests/test_p0b_wavefront.py`

**Interfaces:**
- Consumes: `geo.cells.haversine_km` (Plan A, Task 3); `detect_cell_center` (Task 1)
- Produces:
  - `CellHit(cell: str, first_ms: int)` — erste Triggerzeit je Zelle im Cluster.
  - `wavefront_consistent(hits: list[CellHit], *, v_s_kms: float = 3.5, v_band: tuple[float, float] = (2.5, 5.0), tol_s: float = 4.0, min_cells: int = 3) -> bool` — passt die Menge {Zelle → erste Triggerzeit} zu einer von *einem* Ursprung mit S-Wellen-Geschwindigkeit auslaufenden Front? Verfahren: den Ursprung als die Zelle mit frühester Zeit annehmen, erwartete Verzögerung `d/v_s` gegen die beobachtete `Δt` prüfen; Beben ⇒ **wächst konsistent mit Distanz** (Korrelation Distanz/Δt hoch, Rest-Streuung < tol, implizite Geschwindigkeit im Band). Stadtweit-gleichzeitig (Feuerwerk/Jubel) ⇒ Δt ≈ 0 unabhängig von Distanz ⇒ False.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_wavefront.py`:
```python
from tda_server.p0b.signals import detect_cell_center
from tda_server.p0b.wavefront import CellHit, wavefront_consistent
from tda_server.geo.cells import haversine_km


def hit_at(cell: str, origin_cell: str, v_kms: float, t0: int) -> CellHit:
    la, lo = detect_cell_center(cell)
    ola, olo = detect_cell_center(origin_cell)
    d = haversine_km(la, lo, ola, olo)
    return CellHit(cell, t0 + int(1000 * d / v_kms))


def test_earthquake_front_is_consistent():
    origin = "d410_289"
    cells = ["d410_289", "d411_289", "d412_290", "d413_291", "d414_292"]
    hits = [hit_at(c, origin, 3.5, 100_000) for c in cells]
    assert wavefront_consistent(hits) is True

def test_citywide_simultaneous_is_rejected():
    # fireworks/cheering: every cell triggers at the same instant, any distance
    cells = ["d410_289", "d411_289", "d412_290", "d413_291", "d414_292"]
    hits = [CellHit(c, 100_000) for c in cells]
    assert wavefront_consistent(hits) is False

def test_implausible_speed_rejected():
    origin = "d410_289"
    cells = ["d410_289", "d411_289", "d412_290", "d413_291"]
    hits = [hit_at(c, origin, 15.0, 100_000) for c in cells]   # 15 km/s: not S-wave
    assert wavefront_consistent(hits) is False

def test_too_few_cells_rejected():
    hits = [CellHit("d410_289", 100_000), CellHit("d411_289", 101_000)]
    assert wavefront_consistent(hits, min_cells=3) is False
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_wavefront.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/wavefront.py`:
```python
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center


@dataclass(frozen=True)
class CellHit:
    cell: str
    first_ms: int


def wavefront_consistent(hits: list[CellHit], *, v_s_kms: float = 3.5,
                         v_band: tuple[float, float] = (2.5, 5.0),
                         tol_s: float = 4.0, min_cells: int = 3) -> bool:
    """A real earthquake front: trigger delay grows consistently with distance
    from the origin at an S-/surface-wave speed. Citywide-simultaneous noise
    (fireworks, cheering) shows ~0 delay regardless of distance."""
    if len(hits) < min_cells:
        return False
    origin = min(hits, key=lambda h: h.first_ms)
    ola, olo = detect_cell_center(origin.cell)
    dist = np.array([haversine_km(*detect_cell_center(h.cell), ola, olo) for h in hits])
    dt = np.array([(h.first_ms - origin.first_ms) / 1000.0 for h in hits])
    if dist.max() < 1.0:
        return False                              # all cells co-located: no leverage
    # fit dt = dist / v  (through origin); slope = 1/v
    slope = float(np.sum(dist * dt) / np.sum(dist * dist))
    if slope <= 0:
        return False
    v_impl = 1.0 / slope
    if not (v_band[0] <= v_impl <= v_band[1]):
        return False
    resid = dt - slope * dist
    rms = float(np.sqrt(np.mean(resid ** 2)))
    return rms <= tol_s
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_wavefront.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): S-wave front consistency check (reject citywide-simultaneous)"
```

---

### Task 8: Räumliches Clustering — co-triggernde Zellen zusammenfassen

**Files:**
- Create: `server/src/tda_server/p0b/cluster.py`
- Test: `server/tests/test_p0b_cluster.py`

**Interfaces:**
- Consumes: `detect_cell_center` (Task 1), `haversine_km` (Plan A), `CellHit` (Task 7)
- Produces:
  - `form_cluster(hits: list[CellHit], *, link_km: float = 40.0) -> list[CellHit]` — Single-Linkage-Clustering über Zellmittelpunkte; gibt die **größte** zusammenhängende Gruppe zurück (die anderen sind separate Störungen/Ereignisse). Verhindert, dass zwei gleichzeitige, aber räumlich getrennte Störungen als ein Beben verschmelzen.
  - `cluster_origin(cluster: list[CellHit]) -> tuple[float, float, int]` — Ursprungsschätzung: Mittelpunkt der frühest triggernden Zelle + deren `first_ms` als grobe Herdzeit (`lat, lon, origin_ms`).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_cluster.py`:
```python
from tda_server.p0b.cluster import cluster_origin, form_cluster
from tda_server.p0b.wavefront import CellHit


def test_largest_connected_group_wins():
    # group A: 4 adjacent cells near (41,29); group B: 2 cells far away (36,36)
    a = [CellHit("d410_289", 100_000), CellHit("d411_289", 100_400),
         CellHit("d412_290", 100_800), CellHit("d413_290", 101_200)]
    b = [CellHit("d360_360", 100_100), CellHit("d361_360", 100_200)]
    cluster = form_cluster(a + b, link_km=40.0)
    assert set(h.cell for h in cluster) == set(h.cell for h in a)

def test_origin_is_earliest_cell_center():
    a = [CellHit("d411_289", 100_400), CellHit("d410_289", 100_000),
         CellHit("d412_290", 100_800)]
    lat, lon, origin_ms = cluster_origin(a)
    assert origin_ms == 100_000
    assert 41.0 <= lat < 41.1 and 28.9 <= lon < 29.0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_cluster.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/cluster.py`:
```python
from __future__ import annotations

from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center
from tda_server.p0b.wavefront import CellHit


def form_cluster(hits: list[CellHit], *, link_km: float = 40.0) -> list[CellHit]:
    """Single-linkage spatial clustering; return the largest connected group."""
    if not hits:
        return []
    centers = [detect_cell_center(h.cell) for h in hits]
    n = len(hits)
    parent = list(range(n))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i in range(n):
        for j in range(i + 1, n):
            if haversine_km(*centers[i], *centers[j]) <= link_km:
                parent[find(i)] = find(j)

    groups: dict[int, list[CellHit]] = {}
    for i in range(n):
        groups.setdefault(find(i), []).append(hits[i])
    return max(groups.values(), key=len)


def cluster_origin(cluster: list[CellHit]) -> tuple[float, float, int]:
    earliest = min(cluster, key=lambda h: h.first_ms)
    lat, lon = detect_cell_center(earliest.cell)
    return lat, lon, earliest.first_ms
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_cluster.py -q`
Expected: `2 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): single-linkage spatial clustering + origin estimate"
```

---

### Task 9: Reputationsgewichtung — neue/auffällige Geräte zählen weniger

**Files:**
- Create: `server/src/tda_server/p0b/reputation.py`
- Test: `server/tests/test_p0b_reputation.py`

**Interfaces:**
- Produces:
  - `ReputationStore(base: float = 0.1, cap: float = 1.0, gain: float = 0.05)` — Reputation ist an das **Hardware-Attest** gebunden (Schlüssel = `attest_id`, nicht App-Instanz-ID) → Reinstall gibt keinen frischen neutralen Score (§3 „Reinstall-Härtung").
    - `weight(attest_id: str, attest_ok: bool) -> float` — aktuelle Gewichtung im Bereich [0, cap]; nicht-attestierte Geräte gedeckelt bei `base`.
    - `reward(attest_id: str) -> None` — nach einem gegen P2-Wahrheit **bestätigten** Beitrag (Lernschleife, §3): Reputation += gain (bis cap).
    - `penalize(attest_id: str, factor: float = 0.5) -> None` — nach Beitrag zu einem **Fehlalarm**: Reputation ×= factor.
  - `trigger_weight(store: ReputationStore, attest_id: str, attest_ok: bool) -> float` — Bequemlichkeits-Wrapper für den Detektor (die `weight`, die in `ScoreWindow.add` fließt).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_reputation.py`:
```python
from tda_server.p0b.reputation import ReputationStore, trigger_weight


def test_new_device_starts_low():
    s = ReputationStore(base=0.1)
    assert s.weight("newdev", attest_ok=True) == 0.1

def test_unattested_is_capped_at_base():
    s = ReputationStore(base=0.1)
    for _ in range(100):
        s.reward("dev")
    assert s.weight("dev", attest_ok=False) == 0.1     # no attest -> capped low
    assert s.weight("dev", attest_ok=True) == 1.0      # attested -> earned rep

def test_reward_and_cap():
    s = ReputationStore(base=0.1, cap=1.0, gain=0.3)
    for _ in range(10):
        s.reward("dev")
    assert s.weight("dev", attest_ok=True) == 1.0      # capped

def test_penalize_reduces_weight():
    s = ReputationStore(base=0.1, gain=0.3)
    for _ in range(5):
        s.reward("dev")
    before = s.weight("dev", attest_ok=True)
    s.penalize("dev", factor=0.5)
    assert s.weight("dev", attest_ok=True) < before

def test_reputation_survives_reinstall_key_is_attest_id():
    s = ReputationStore()
    for _ in range(5):
        s.reward("hw-attest-42")
    # a reinstall keeps the same hardware attest id -> same (non-reset) score
    assert s.weight("hw-attest-42", attest_ok=True) > 0.1
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_reputation.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/reputation.py`:
```python
from __future__ import annotations


class ReputationStore:
    """Per-device reputation, keyed by hardware attest id (not app instance id),
    so reinstalling does not reset to a fresh neutral score (spec 3)."""

    def __init__(self, base: float = 0.1, cap: float = 1.0, gain: float = 0.05) -> None:
        self.base = base
        self.cap = cap
        self.gain = gain
        self._rep: dict[str, float] = {}

    def _raw(self, attest_id: str) -> float:
        return self._rep.get(attest_id, self.base)

    def weight(self, attest_id: str, attest_ok: bool) -> float:
        rep = self._raw(attest_id)
        if not attest_ok:
            return min(rep, self.base)          # unattested devices cannot earn weight
        return max(0.0, min(rep, self.cap))

    def reward(self, attest_id: str) -> None:
        self._rep[attest_id] = min(self.cap, self._raw(attest_id) + self.gain)

    def penalize(self, attest_id: str, factor: float = 0.5) -> None:
        self._rep[attest_id] = max(0.0, self._raw(attest_id) * factor)


def trigger_weight(store: ReputationStore, attest_id: str, attest_ok: bool) -> float:
    return store.weight(attest_id, attest_ok)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_reputation.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): attest-bound reputation weighting (reinstall-hardened)"
```

---

### Task 10: P0b-Detektor-Orchestrierung — vom Triggerstrom zum SourceEvent

**Files:**
- Modify: `server/src/tda_server/p0b/detector.py` (Teil 2: `P0bDetector`, `AttentionSignal`)
- Test: `server/tests/test_p0b_detector.py` (Teil 2, ergänzen)

**Interfaces:**
- Consumes: alle vorigen P0b-Module; `SourceEvent` (Plan A, `domain.events`)
- Produces:
  - `AttentionSignal(cell: str, level: float, at_ms: int)` — provisorischer Cluster (Score über *Wecker*-Schwelle `h_attn` < `h`), der den Aufmerksamkeits-Modus auslöst (Umfeld weckt, §3), aber **keinen** Alarm.
  - `P0bDetector(...)` mit Feldern: `background`, `density`, `reputation`, `detector` (ScoreDetector), `threshold_h`, `attn_h`, `min_intensity_mmi: float = 4.0`, `eps_s`. Methoden:
    - `observe_trigger(t: PhoneTrigger) -> None` — schreibt in Density-unabhängige `ScoreWindow` mit Reputationsgewicht; merkt erste Triggerzeit je Zelle.
    - `evaluate(now_ms: int) -> tuple[SourceEvent | None, AttentionSignal | None]` — pro Zelle Score rechnen; oberhalb `attn_h` → AttentionSignal; oberhalb `h` **und** Wellenfront-konsistenter Cluster (≥ min_cells) → `SourceEvent(source="p0b", ...)` mit grober Magnitude aus Cluster-Ausdehnung (Proxy, ehrlich als Untergrenze), Ursprung/Herdzeit aus `cluster_origin`. Gibt bei mehreren Alarmen den stärksten zurück.
  - `estimate_magnitude(cluster: list[CellHit]) -> float` — grober Proxy: Nahfeld-Radius → Magnitude-Untergrenze (Kalibrierung ist Plan-B/Nachkalibrierungs-Schritt; hier konservativer Startwert, damit die Fusion einen Wert hat). Nie über M-Sättigung hinaus behaupten.

**Wichtig (Ehrlichkeit):** Die P0b-Magnitude ist ein grober Proxy, kein instrumenteller Messwert. Der emittierte `SourceEvent` trägt `mag_type="p0b_proxy"`. Die Fusion (Plan A) eskaliert Magnitude nach dem Maximum — sobald ein Katalog-/Stations-Wert eintrifft, gewinnt der genauere. P0b liefert v. a. **Zeit und Ort schnell**, nicht die präzise Stärke.

- [ ] **Step 1: Failing Test schreiben (an test_p0b_detector.py anhängen)**

```python
from datetime import timezone
from tda_server.domain.events import SourceEvent
from tda_server.p0b.detector import P0bDetector, build_p0b_detector
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.signals import PhoneTrigger, coarsen_cell


def burst_triggers(origin=(41.0, 29.0), n_cells=5, t0=100_000, v_kms=3.5):
    from tda_server.geo.cells import haversine_km
    from tda_server.p0b.signals import detect_cell_center
    ocell = coarsen_cell(*origin)
    ola, olo = detect_cell_center(ocell)
    trigs = []
    for k in range(n_cells):
        lat = origin[0] + 0.1 * k
        cell = coarsen_cell(lat, origin[1])
        la, lo = detect_cell_center(cell)
        d = haversine_km(la, lo, ola, olo)
        tms = t0 + int(1000 * d / v_kms)
        for j in range(8):               # several devices per cell
            trigs.append(PhoneTrigger(f"dev{k}_{j}", cell, tms + j, 40, tms + 100, True))
    return trigs


def test_detector_emits_source_event_on_consistent_burst():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    trigs = burst_triggers()
    for t in trigs:
        det.observe_trigger(t)
    ev, attn = det.evaluate(now_ms=max(t.trigger_ms for t in trigs) + 500)
    assert isinstance(ev, SourceEvent)
    assert ev.source == "p0b" and ev.mag_type == "p0b_proxy"
    assert ev.origin_time.tzinfo is timezone.utc or ev.origin_time.tzinfo is not None
    assert 40.9 < ev.lat < 41.6

def test_citywide_simultaneous_does_not_emit():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    # every cell triggers at the same instant: high score but no valid front
    for k in range(5):
        cell = coarsen_cell(41.0 + 0.1 * k, 29.0)
        for j in range(8):
            det.observe_trigger(PhoneTrigger(f"d{k}_{j}", cell, 100_000 + j, 40,
                                             100_100, True))
    ev, _attn = det.evaluate(now_ms=101_000)
    assert ev is None

def test_attention_signal_below_alarm():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=50.0, attn_h=1.0)
    trigs = burst_triggers(n_cells=2)     # weak: raises attention, not alarm
    for t in trigs:
        det.observe_trigger(t)
    ev, attn = det.evaluate(now_ms=max(t.trigger_ms for t in trigs) + 500)
    assert ev is None and attn is not None and attn.level >= 1.0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_detector.py -q`
Expected: FAIL (`build_p0b_detector`/`P0bDetector` fehlen)

- [ ] **Step 3: Implementieren (an detector.py anhängen)**

```python
from dataclasses import dataclass
from datetime import datetime, timezone

from tda_server.domain.events import SourceEvent
from tda_server.p0b.cluster import cluster_origin, form_cluster
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.reputation import ReputationStore, trigger_weight
from tda_server.p0b.signals import ActivePing, PhoneTrigger
from tda_server.p0b.wavefront import CellHit, wavefront_consistent


@dataclass(frozen=True)
class AttentionSignal:
    cell: str
    level: float
    at_ms: int


def estimate_magnitude(cluster: list[CellHit]) -> float:
    """Coarse lower-bound proxy from felt-area extent. NOT an instrumental value;
    Turkish calibration is a Plan B step. Never claim beyond point-source saturation."""
    from tda_server.geo.cells import haversine_km
    from tda_server.p0b.signals import detect_cell_center
    centers = [detect_cell_center(h.cell) for h in cluster]
    olat, olon = detect_cell_center(min(cluster, key=lambda h: h.first_ms).cell)
    radius_km = max((haversine_km(la, lo, olat, olon) for la, lo in centers), default=0.0)
    # felt radius -> rough magnitude floor; conservative, capped at saturation
    mag = 4.0 + 0.9 * (radius_km / 30.0)
    return round(min(mag, 7.0), 1)


class P0bDetector:
    def __init__(self, *, detector: "ScoreDetector", reputation: ReputationStore,
                 threshold_h: float, attn_h: float, eps_s: float = 20.0,
                 min_cells: int = 3) -> None:
        self.detector = detector
        self.reputation = reputation
        self.threshold_h = threshold_h
        self.attn_h = attn_h
        self.window = ScoreWindow(eps_s=eps_s)
        self.min_cells = min_cells
        self._first_hit: dict[str, int] = {}

    def observe_trigger(self, t: PhoneTrigger) -> None:
        w = trigger_weight(self.reputation, t.device_hash, t.attest_ok)
        self.window.add(t.cell, t.trigger_ms, w)
        prev = self._first_hit.get(t.cell)
        if prev is None or t.trigger_ms < prev:
            self._first_hit[t.cell] = t.trigger_ms

    def evaluate(self, now_ms: int) -> tuple[SourceEvent | None, AttentionSignal | None]:
        hot: list[tuple[str, float]] = []
        attn: AttentionSignal | None = None
        for cell in list(self._first_hit):
            s = self.detector.score(cell, now_ms, self.window)
            if s >= self.attn_h and (attn is None or s > attn.level):
                attn = AttentionSignal(cell, s, now_ms)
            if s >= self.threshold_h:
                hot.append((cell, s))
        if len(hot) < self.min_cells:
            return None, attn
        hits = [CellHit(cell, self._first_hit[cell]) for cell, _s in hot]
        cluster = form_cluster(hits)
        if len(cluster) < self.min_cells or not wavefront_consistent(
                cluster, min_cells=self.min_cells):
            return None, attn
        lat, lon, origin_ms = cluster_origin(cluster)
        ev = SourceEvent(
            source="p0b",
            source_event_id=f"p0b:{origin_ms}:{cluster[0].cell}",
            origin_time=datetime.fromtimestamp(origin_ms / 1000, tz=timezone.utc),
            lat=lat, lon=lon, depth_km=None,
            magnitude=estimate_magnitude(cluster),
            mag_type="p0b_proxy",
            received_at=datetime.fromtimestamp(now_ms / 1000, tz=timezone.utc),
        )
        return ev, attn


def build_p0b_detector(background: BackgroundModel, *, nu_per_cell: int,
                       threshold_h: float, attn_h: float, eps_s: float = 20.0,
                       min_cells: int = 3) -> P0bDetector:
    """Test/helper constructor: fixed synthetic density per cell."""
    density = DensityTracker(active_ttl_ms=10_000_000)
    # seed nu_per_cell active devices into every cell that later triggers
    class _SeededDensity(DensityTracker):
        def nu(self, cell: str, now_ms: int) -> int:
            return nu_per_cell
    det = ScoreDetector(background, _SeededDensity(), eps_s=eps_s)
    return P0bDetector(detector=det, reputation=ReputationStore(),
                       threshold_h=threshold_h, attn_h=attn_h, eps_s=eps_s,
                       min_cells=min_cells)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_detector.py -q`
Expected: alle Detector-Tests grün (Task 5 + Task 10)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): P0bDetector orchestration -> SourceEvent + attention mode"
```

---

### Task 11: Tier-Ableitung nachziehen — p0b-only ⇒ P0 (Plan-A-Korrektur 1)

**Files:**
- Modify: `server/src/tda_server/alert/payload.py` (`build_payload`)
- Test: `server/tests/test_p0b_tier.py`

**Interfaces:**
- Ändert `build_payload` (Plan A, Task 5) so, dass `tier` **abgeleitet** wird statt `"P1"` hartkodiert (Plan-A-Deep-Dive-Korrektur 1): `derive_tier(ev: CanonicalEvent) -> str`:
  - Quellen == {"p0b"} (nur Crowdsourcing) → `"P0"`
  - Zustand `CONFIRMED` (≥ 2 unabhängige Quellen konvergiert) → `"P2"`
  - sonst (eine Katalog-/Stationsquelle) → `"P1"`
- Produces: `derive_tier(ev) -> str` (neue Hilfsfunktion in `payload.py`).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_tier.py`:
```python
from datetime import datetime, timezone
from tda_server.alert.payload import build_payload, derive_tier
from tda_server.domain.events import CanonicalEvent, EventState, SourceEvent
from tda_server.fusion.correlator import Transition


def se(source: str, mag: float) -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source, f"{source}1", t, 41.0, 29.0, 10.0, mag, "ml", t)


def test_p0b_only_is_tier_p0():
    ev = CanonicalEvent.from_source(se("p0b", 5.0))
    assert derive_tier(ev) == "P0"
    p = build_payload(Transition(ev, "new"), now_ms=0)
    assert p["tier"] == "P0"

def test_single_catalog_is_p1():
    ev = CanonicalEvent.from_source(se("emsc", 5.0))
    assert derive_tier(ev) == "P1"

def test_confirmed_is_p2():
    ev = CanonicalEvent.from_source(se("p0b", 5.0))
    ev.merge(se("emsc", 5.1))
    ev.state = EventState.CONFIRMED
    assert derive_tier(ev) == "P2"
    p = build_payload(Transition(ev, "confirm"), now_ms=0)
    assert p["tier"] == "P2"
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_tier.py -q`
Expected: FAIL (`derive_tier` fehlt; `tier` noch hartkodiert)

- [ ] **Step 3: Implementieren (payload.py anpassen)**

In `src/tda_server/alert/payload.py` ergänzen und `build_payload` ändern:
```python
from tda_server.domain.events import CanonicalEvent, EventState


def derive_tier(ev: CanonicalEvent) -> str:
    if set(ev.sources) == {"p0b"}:
        return "P0"
    if ev.state is EventState.CONFIRMED:
        return "P2"
    return "P1"
```
In `build_payload` die Zeile `"tier": "P1",` ersetzen durch `"tier": derive_tier(ev),`.

- [ ] **Step 4: Test ausführen — muss bestehen (auch Plan-A-Payload-Test bleibt grün)**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_tier.py tests/test_payload.py -q`
Expected: alle grün

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix(alert): derive tier from state/sources (p0b->P0), Plan-A correction 1"
```

---

### Task 12: Pipeline-Verdrahtung — Triggerstrom → Detektor → Korrelator → Publisher

**Files:**
- Create: `server/src/tda_server/p0b/pipeline.py`
- Test: `server/tests/test_p0b_pipeline.py`

**Interfaces:**
- Consumes: `EventStream` (Plan A), `Correlator` (Plan A), `deserialize_trigger`/`deserialize_ping` (Task 1), `P0bDetector` (Task 10), `DensityTracker` (Task 3)
- Produces:
  - `async run_p0b_pipeline(trigger_stream, ping_stream, detector: P0bDetector, density: DensityTracker, correlator: Correlator, on_transition, *, tick_s: float = 1.0, clock) -> None` — konsumiert Ping-Strom (→ Density), Trigger-Strom (→ Detektor), ruft periodisch `detector.evaluate(now)`; ein emittierter `SourceEvent` geht durch `correlator.ingest` → resultierende `Transition` an `on_transition` (im Produktivpfad: `build_payload` + `Publisher.publish`). **Trennung von Uhr/Takt über `clock`-Callable, damit deterministisch testbar.**
  - Diese Verdrahtung ist der Punkt, an dem P0b in die **gemeinsame Fusion** mündet: ein P0b-`SourceEvent` erzeugt ein kanonisches Ereignis (Tier P0); ein späterer Katalog-/Stations-`SourceEvent` desselben Ereignisses löst im Korrelator `confirm` aus (→ Tier P2). Das ist die Verstärkung mit Plan A/B.

**Hinweis:** Nach Bestätigung eines Ereignisses muss der Server Trigger-Uploads im Gebiet drosseln (§3 „Das Netz gehört dann der Zustellung"). Diese Drossel wird als `on_transition`-Nebenwirkung angerissen (Aufruf eines `throttle(area)`-Hooks bei `kind in {"confirm"}`), die konkrete Client-Drossel ist Plan C.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_pipeline.py`:
```python
import asyncio
from tda_server.fusion.correlator import Correlator, Transition
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.detector import build_p0b_detector
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.pipeline import run_p0b_pipeline
from tda_server.p0b.signals import PhoneTrigger, serialize_trigger, coarsen_cell
from tda_server.stream.base import InMemoryStream
from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center


async def test_pipeline_emits_transition_from_trigger_burst():
    trigger_stream = InMemoryStream()
    ping_stream = InMemoryStream()
    origin = (41.0, 29.0)
    ocell = coarsen_cell(*origin)
    ola, olo = detect_cell_center(ocell)
    t0 = 100_000
    last = t0
    for k in range(5):
        cell = coarsen_cell(origin[0] + 0.1 * k, origin[1])
        la, lo = detect_cell_center(cell)
        d = haversine_km(la, lo, ola, olo)
        tms = t0 + int(1000 * d / 3.5)
        last = max(last, tms)
        for j in range(8):
            await trigger_stream.append(serialize_trigger(
                PhoneTrigger(f"dev{k}_{j}", cell, tms + j, 40, tms + 100, True)))

    detector = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                                  nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    correlator = Correlator()
    got: list[Transition] = []

    clock_val = {"t": last + 1000}
    def clock() -> int:
        return clock_val["t"]

    async def on_transition(tr: Transition) -> None:
        got.append(tr)

    task = asyncio.create_task(run_p0b_pipeline(
        trigger_stream, ping_stream, detector, DensityTracker(),
        correlator, on_transition, tick_s=0.01, clock=clock))
    await asyncio.sleep(0.1)
    task.cancel()
    assert any(tr.event.source == "p0b" or "p0b" in tr.event.sources for tr in got)
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_pipeline.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/pipeline.py`:
```python
from __future__ import annotations

import asyncio
import logging
from typing import Awaitable, Callable

from tda_server.fusion.correlator import Correlator, Transition
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.detector import P0bDetector
from tda_server.p0b.signals import deserialize_ping, deserialize_trigger
from tda_server.stream.base import EventStream

log = logging.getLogger(__name__)


async def _consume_pings(ping_stream: EventStream, density: DensityTracker) -> None:
    async for _sid, rec in ping_stream.read():
        density.observe(deserialize_ping(rec))


async def _consume_triggers(trigger_stream: EventStream, detector: P0bDetector) -> None:
    async for _sid, rec in trigger_stream.read():
        detector.observe_trigger(deserialize_trigger(rec))


async def run_p0b_pipeline(
    trigger_stream: EventStream,
    ping_stream: EventStream,
    detector: P0bDetector,
    density: DensityTracker,
    correlator: Correlator,
    on_transition: Callable[[Transition], Awaitable[None]],
    *,
    tick_s: float = 1.0,
    clock: Callable[[], int],
) -> None:
    # detector's own density comes from its ScoreDetector; the pipeline-level
    # DensityTracker feeds it when the detector is built to share it (production).
    pings = asyncio.create_task(_consume_pings(ping_stream, density))
    trigs = asyncio.create_task(_consume_triggers(trigger_stream, detector))
    try:
        while True:
            now = clock()
            ev, _attn = detector.evaluate(now)
            if ev is not None:
                tr = correlator.ingest(ev)
                if tr is not None:
                    await on_transition(tr)
            await asyncio.sleep(tick_s)
    finally:
        pings.cancel()
        trigs.cancel()
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_pipeline.py -q`
Expected: `1 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): wire trigger/ping streams -> detector -> correlator"
```

---

### Task 13: Replay-Harness + Türkei-Szenario-Abnahmetest (Latenz)

**Files:**
- Create: `server/src/tda_server/p0b/replay.py`, `server/scripts/gen_p0b_scenario.py`, `server/tests/fixtures/p0b_scenario_turkiye.json`
- Test: `server/tests/test_p0b_replay.py`

**Interfaces:**
- Produces:
  - `generate_scenario(*, epicenter: tuple[float, float], mag: float, n_devices: int, report_fraction: float, v_kms: float = 3.5, radius_km: float = 120.0, t0_ms: int, seed: int) -> dict` — erzeugt ein **synthetisches, physikalisch konsistentes** Trigger-/Ping-Szenario (Wellenfront ab Epizentrum, φ-Anteil auslösender Geräte, plus Hintergrund-Fehltrigger). Als JSON-Fixture speicherbar. Klar als *Szenario* markiert (`"kind": "synthetic_scenario"`), nie Produktions-Alarm.
  - `replay_scenario(scenario: dict, detector_factory) -> dict` — spielt Pings+Trigger zeitgeordnet in einen Detektor, gibt `{"detected": bool, "detect_latency_s": float | None, "false_before_origin": int, "origin_error_km": float | None}`.
- **Akzeptanzkriterium (aus RECHERCHE-P0A §4 / Spec):** Bei Epizentrum SO-Türkei (Kahramanmaraş ≈ 37,17 N, 37,03 O), n_devices=400, φ=0,3 detektiert der Detektor **innerhalb ≤ 12 s** nach Herdzeit (belegter Realwert Earthquake Network M7.8), Ursprungsfehler < 60 km, kein Fehlalarm vor Herdzeit. Bei φ=0,1 / n_devices=40 darf die Latenz höher liegen (~10 s Größenordnung) — als separater, *nicht-blockierender* Diagnosefall geloggt (dünnes Netz, Spec-erwartetes Verhalten).

**Wichtig:** Dies ist der wissenschaftliche Abnahme-Gate des Plans. Der `report_fraction`/`n_devices`-Sweep macht die Netzdichte-Abhängigkeit **messbar und öffentlich** (Spec §3 „Netzdichte offen" + Lernschleife), statt sie zu behaupten.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0b_replay.py`:
```python
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.detector import build_p0b_detector
from tda_server.p0b.replay import generate_scenario, replay_scenario


def factory(nu: int):
    return lambda: build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                                      nu_per_cell=max(nu // 20, 5),
                                      threshold_h=3.0, attn_h=1.0)


def test_dense_network_detects_within_12s():
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=7.8, n_devices=400,
                             report_fraction=0.3, t0_ms=1_000_000, seed=7)
    res = replay_scenario(scen, factory(400))
    assert res["detected"] is True
    assert res["detect_latency_s"] is not None and res["detect_latency_s"] <= 12.0
    assert res["false_before_origin"] == 0
    assert res["origin_error_km"] < 60.0

def test_quiet_scenario_produces_no_false_alarm():
    # background-only scenario: report_fraction 0 -> no earthquake front
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=0.0, n_devices=400,
                             report_fraction=0.0, t0_ms=1_000_000, seed=3)
    res = replay_scenario(scen, factory(400))
    assert res["detected"] is False
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0b_replay.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0b/replay.py`:
```python
from __future__ import annotations

import numpy as np

from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import ActivePing, PhoneTrigger, coarsen_cell, detect_cell_center


def generate_scenario(*, epicenter: tuple[float, float], mag: float, n_devices: int,
                      report_fraction: float, v_kms: float = 3.5, radius_km: float = 120.0,
                      t0_ms: int, seed: int) -> dict:
    rng = np.random.default_rng(seed)
    elat, elon = epicenter
    # scatter devices in a box around the epicenter (~radius_km)
    deg = radius_km / 111.0
    lats = elat + rng.uniform(-deg, deg, n_devices)
    lons = elon + rng.uniform(-deg, deg, n_devices)
    pings, triggers = [], []
    for i in range(n_devices):
        cell = coarsen_cell(lats[i], lons[i])
        pings.append({"device_hash": f"dev{i}", "cell": cell,
                      "ping_ms": t0_ms - 60_000, "received_ms": t0_ms - 60_000})
        d = haversine_km(lats[i], lons[i], elat, elon)
        # background false trigger (rare), any time in window
        if rng.random() < 0.01:
            bt = t0_ms + int(rng.uniform(-30_000, -5_000))
            triggers.append(_trig(f"dev{i}", cell, bt))
        # earthquake trigger for the report_fraction that feel it, within felt radius
        if d <= radius_km and mag >= 4.0 and rng.random() < report_fraction:
            arr = t0_ms + int(1000 * d / v_kms) + int(rng.normal(0, 300))
            triggers.append(_trig(f"dev{i}", cell, arr))
    triggers.sort(key=lambda t: t["trigger_ms"])
    return {"kind": "synthetic_scenario", "epicenter": [elat, elon], "mag": mag,
            "t0_ms": t0_ms, "n_devices": n_devices, "report_fraction": report_fraction,
            "pings": pings, "triggers": triggers}


def _trig(dev: str, cell: str, tms: int) -> dict:
    return {"device_hash": dev, "cell": cell, "trigger_ms": tms,
            "clock_unc_ms": 40, "received_ms": tms + 120, "attest_ok": True}


def replay_scenario(scenario: dict, detector_factory) -> dict:
    detector = detector_factory()
    t0 = scenario["t0_ms"]
    triggers = scenario["triggers"]
    detected_ms: int | None = None
    origin_err: float | None = None
    false_before = 0
    # feed triggers in time order, evaluating after each
    for tr in triggers:
        detector.observe_trigger(PhoneTrigger(
            tr["device_hash"], tr["cell"], tr["trigger_ms"], tr["clock_unc_ms"],
            tr["received_ms"], tr["attest_ok"]))
        ev, _attn = detector.evaluate(tr["trigger_ms"])
        if ev is not None and detected_ms is None:
            detected_ms = tr["trigger_ms"]
            elat, elon = scenario["epicenter"]
            origin_err = haversine_km(ev.lat, ev.lon, elat, elon)
            if detected_ms < t0:
                false_before += 1
    return {
        "detected": detected_ms is not None and detected_ms >= t0,
        "detect_latency_s": None if detected_ms is None else (detected_ms - t0) / 1000.0,
        "false_before_origin": false_before,
        "origin_error_km": origin_err,
    }
```

`scripts/gen_p0b_scenario.py`:
```python
import json
import sys

from tda_server.p0b.replay import generate_scenario

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "tests/fixtures/p0b_scenario_turkiye.json"
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=7.8, n_devices=400,
                             report_fraction=0.3, t0_ms=1_000_000, seed=7)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(scen, fh)
    print(f"wrote {out}: {len(scen['triggers'])} triggers, {len(scen['pings'])} pings")
```

- [ ] **Step 4: Fixture erzeugen + Test ausführen — muss bestehen**

```bash
.venv/Scripts/python scripts/gen_p0b_scenario.py tests/fixtures/p0b_scenario_turkiye.json
.venv/Scripts/python -m pytest tests/test_p0b_replay.py -q
```
Expected: `2 passed`. Schlägt der Latenz-Assert fehl, ist das **kein** Testfix-Anlass: `eps_s`, `threshold_h` und `min_cells` gegen die Szenario-Dichte prüfen — die Kalibrierung ist das Ergebnis, nicht der Test.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0b): replay harness + Turkiye scenario acceptance test (<=12s)"
```

---

## Self-Review (Plan-Autor)

**Spec-Abdeckung:**
- §2.2 P0b (Phone-Cluster, Sekunden, Freischaltung pro Region) → Tasks 3–13 (der Detektor IST die Freischaltung).
- §3 Phone-Pipeline (idle+laden, Selbstkalibrierung, NTP+coarse cell, Signifikanz vs. Live-Dichte) → Tasks 1, 3, 5 (Client-Selbstkalibrierung ist Plan C; hier serverseitig vorausgesetzt).
- §3 Aufmerksamkeits-Modus → Task 10 (`AttentionSignal`); Client-Umsetzung Plan C (deklariert).
- §3 Fehlalarm-Filter Wellenfront (wellenart-spezifisch, S-Welle) → Task 7.
- §3 Zeitsynchronisation (Uhr-Unsicherheit, Ausschluss) → Task 2 (`TriggerGate`).
- §3 Angriffs-Schutz (Attest, Rate-Limit, Reputation, Reinstall-Härtung) → Tasks 2, 9.
- §3 Lernschleife (Benotung gegen P2) → Task 9 (`reward`/`penalize`); Anbindung an P2-Wahrheit ist Plan F (Betrieb).
- §11 „1 Fehlalarm/Jahr" + „dichteunabhängige Mindestschwelle MMI IV" → Tasks 6, 10.
- Fusion/Tier (Plan-A-Korrektur 1, p0b→P0) → Task 11.
- Replay als Grundeigenschaft (§6) + belegte Türkei-Latenz → Task 13.

**Bewusste Auslassungen (verschobene Lücken):** echte Play-Integrity/StrongBox-Attestierung (Interface + Stub hier; Plan C/F); Client-Sammelteil + Aufmerksamkeits-Rückkanal + Trigger-Drossel-Client (Plan C); P2-Wahrheits-Anbindung der Lernschleife + Persistenz von Reputation/Background/Threshold über Neustart (Plan F); regionale Nachkalibrierung der P0b-Proxy-Magnitude (Plan B, gemeinsamer Kalibrierschritt).

**Platzhalter-Scan:** keine TODO/TBD; jeder Schritt trägt vollständigen Code + erwartete Ausgabe.

**Typkonsistenz:** `SourceEvent`-Signatur exakt wie Plan A Task 2 (source, source_event_id, origin_time, lat, lon, depth_km, magnitude, mag_type, received_at). `Transition`/`Correlator.ingest` wie Plan A Task 4. `build_payload`-Änderung additiv (Task 11), Plan-A-Payload-Test bleibt grün. `cell_id`/`haversine_km`/`CELL_DEG` aus Plan A Task 3. Detektionsraster `d…` (0,1°) getrennt vom Publish-Raster `c…` (0,5°) — kein Namenskonflikt.

**Reihenfolge-Verstärkung:** Task 11 + 12 stellen sicher, dass ein P0b-`SourceEvent` durch dieselbe Fusion läuft wie Katalog/Stationen — damit ist der Andockpunkt für Plan B (P0a) gebaut: eine spätere Stations-Pick desselben Ereignisses löst `confirm` (P0→P2) aus. Das ist die in der Sequenz zugesagte gegenseitige Verstärkung, hier konkret verdrahtet.
