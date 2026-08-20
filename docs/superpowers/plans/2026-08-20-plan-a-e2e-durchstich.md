# Plan A: Ende-zu-Ende-Durchstich — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Katalogmeldung (EMSC/USGS/AFAD) → kanonisches Ereignis mit Zustandsmaschine → signierter FCM-Topic-Publish → minimaler Android-Client zeigt Warnung mit S-Wellen-Countdown.

**Architecture:** Asyncio-Python-Server: Quelladapter schreiben normalisierte `SourceEvent`s in einen append-only Ereignisstrom; ein Konsument korreliert sie zu `CanonicalEvent`s (escalate-fast/de-escalate-slow), berechnet betroffene Geo-Rasterzellen und publiziert signierte, autarke Payloads (<1 KB, nur String-Werte) an regionale FCM-Topics. Der Android-Client abonniert die Zellen seiner Orte, verifiziert die Ed25519-Signatur und rechnet den Countdown lokal.

**Tech Stack:** Python 3.12, asyncio, httpx, websockets, cryptography (Ed25519), Redis Streams (fakeredis in Tests), pytest + pytest-asyncio · Android: Kotlin, minSdk 26, FCM, BouncyCastle.

## Korrekturen aus dem Spec-Deep-Dive — VOR Ausführung anwenden

Die Spec wurde nach diesem Plan überarbeitet (Vollrevision 3). Fünf Abweichungen sind vor bzw. während der Implementierung zu berücksichtigen; sie überschreiben die entsprechenden Stellen weiter unten:

1. **`tier` NICHT hartkodieren (Task 5, `build_payload`).** `tier="P1"` ist falsch für bestätigte Ereignisse. Ableiten aus dem Zustand: `EventState.DETECTED/ALERTED → "P0"` (in Plan A nicht erreicht), **eine Katalogquelle → `"P1"`**, **Konvergenz mehrerer Quellen (`CONFIRMED`) → `"P2"`**. `build_payload` bekommt den Tier aus `tr.event.state`/Quellenzahl, nicht als Literal. Der Test in Task 5 prüft beide Fälle.
2. **TEST-Alarme in eigenen Notification-Channel (Task 13).** Nicht denselben `CHANNEL_ID`/Ton wie echte Alarme mit bloßem Titelpräfix — Spec §8 verlangt eine **unverwechselbare** Signatur. Eigener Channel `tda_test` mit eigenem Ton/Icon für `test="1"`.
3. **i18n ab Tag 1, auch im Minimal-Client (Task 12/13).** Keine hartkodierten UI-Strings in Kotlin — `strings.xml`/Resource-IDs verwenden. Setzt das Muster für alle späteren Screens.
4. **Geo-Zellgröße vor Task 3 gegen FCM-Fanout prüfen.** 0,5° ≈ 55 km ist über Istanbul (~15 Mio.) zu groß (Spec §4 fordert *kleinere* Zellen gegen Fanout-Latenz). Zellgröße/-strategie mit grober Fanout-Schätzung (Bevölkerungsdichte × Abo-Rate) festlegen, bevor Formel + Testvektoren in Python und Kotlin einbetoniert werden; ein **Lasttest-Task** (realistische Abonnentenzahl pro Zelle) ergänzt Task 14, bevor `FcmTransport` produktiv läuft.
5. **Schlüsselmanagement ist in Plan A bewusst vereinfacht (Env-Var + Compile-Konstante) — als verschobene Lücke deklarieren.** Spec §6.2 fordert getrennten Secret-Store + Rotation/Revocation über separaten Kanal; das wird NICHT im Durchstich gelöst, ist aber vor MVP-Produktivbetrieb nachzuziehen und im Self-Review als bewusste Auslassung zu nennen.

Größere fehlende Stränge (kein Plan A-Scope, brauchen eigene Pläne, siehe Spec §11 „Plan-Landkarte"): **P0a Stations-Picker** (der eigentliche USP), Bürgermeldungen+Reputation, Offline-Sicherheitspaket, nativer Alarm-/Sensor-Layer, Schlüsselmanagement.

## Global Constraints

- Spec ist `../../../../UMSETZUNGSPLAN.md` (Repo-Wurzel `Earthquake/`); dieser Plan implementiert den P1/P2-Anteil des MVP (§8) als Durchstich.
- **Nullkosten (hart):** Alles läuft auf Gratis-Ebenen. Zielhosting ist eine **Oracle-Cloud-„Always Free"-ARM-VM** (Python-Dienst + selbstgehostetes PostgreSQL/Redis); Push via FCM (gratis); Karten später via MapLibre/OSM (kein Google-Maps-Billing). Keine kostenpflichtigen Managed-Services einführen. Der Android-Client ist **nativ Kotlin** (nicht das RN/Expo-Scaffold — das dient nur als UX-/Schema-Referenz).
- **Keine Mock-Ereignisse in Produktion:** Jeder künstlich erzeugte Alarm trägt `test="1"` und wird im Client sichtbar als TEST angezeigt. Test-Fixtures stammen aus real aufgezeichneten API-Antworten.
- **Kein Scraping**; nur die dokumentierten offenen Feeds: EMSC-WebSocket, USGS GeoJSON, AFAD event-service. Poll-Intervalle in Entwicklung ≥ 60 s.
- **Payload ≤ 1 KB**, ausschließlich String-Werte (FCM-data-Anforderung + deterministische Signierung).
- Alle Zeiten UTC, Wire-Format Unix-Millisekunden als String.
- Geo-Rasterzellen: 0,5°-Grid, Zell-ID `c{floor(lat/0.5)}_{floor(lon/0.5)}` — identische Formel und Testvektoren in Python und Kotlin.
- Python: Zeilenlänge 100, Typannotationen überall, keine globalen Singletons; Code/Kommentare Englisch.
- TDD strikt: Test zuerst, rot sehen, minimal implementieren, grün sehen, committen.
- Arbeitsverzeichnis für alle Server-Tasks: `tda/server/`; Android-Tasks: `tda/android/`.

## File Structure (Zielbild dieses Plans)

```
tda/
  docs/superpowers/plans/2026-08-20-plan-a-e2e-durchstich.md   (dieses Dokument)
  server/
    pyproject.toml
    src/tda_server/
      __init__.py
      config.py            # Env-Konfiguration
      domain/__init__.py
      domain/events.py     # SourceEvent, CanonicalEvent, EventState
      geo/__init__.py
      geo/cells.py         # haversine, cell_id, affected_cells, alert_radius_km
      fusion/__init__.py
      fusion/correlator.py # Korrelation + Zustandsmaschine + Transition
      alert/__init__.py
      alert/payload.py     # Payload-Bau, Kanonisierung, Ed25519 sign/verify
      alert/publisher.py   # Transport-Protokoll, FakeTransport, FcmTransport, Publisher
      stream/__init__.py
      stream/base.py       # EventStream-Protokoll + InMemoryStream
      stream/redis_stream.py
      adapters/__init__.py
      adapters/emsc.py     # WebSocket-Client + Parser
      adapters/usgs.py     # GeoJSON-Poller + Parser
      adapters/afad.py     # event-service-Poller + Parser
      pipeline.py          # Verdrahtung + main
      replay.py            # Replay-CLI über aufgezeichneten Strom
    scripts/
      gen_signing_key.py
      gen_test_vector.py   # Testvektor für Kotlin-Seite
      send_test_alert.py   # signierten TEST-Alarm an Topic senden
    tests/
      fixtures/            # real aufgezeichnete API-Antworten
      test_domain.py test_cells.py test_correlator.py test_payload.py
      test_stream.py test_emsc.py test_usgs.py test_afad.py
      test_publisher.py test_pipeline.py
  android/                 # minimaler Empfangs-Client (Tasks 12–13)
```

---

### Task 1: Repo- und Server-Gerüst

**Files:**
- Create: `tda/.gitignore`, `tda/server/pyproject.toml`, `tda/server/src/tda_server/__init__.py`, `tda/server/tests/test_smoke.py`

**Interfaces:**
- Produces: installierbares Paket `tda_server`, lauffähiges `pytest`.

- [ ] **Step 1: Git-Repo initialisieren**

```bash
cd tda && git init -b main
```

- [ ] **Step 2: .gitignore anlegen**

```gitignore
__pycache__/
*.pyc
.venv/
.pytest_cache/
*.egg-info/
.idea/
local.properties
build/
.gradle/
google-services.json
*.jks
secrets/
```

- [ ] **Step 3: pyproject.toml anlegen**

```toml
[project]
name = "tda-server"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
  "httpx>=0.27",
  "websockets>=12.0",
  "cryptography>=42.0",
  "redis>=5.0",
]

[project.optional-dependencies]
dev = ["pytest>=8.0", "pytest-asyncio>=0.23", "fakeredis>=2.23"]

[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[tool.setuptools.packages.find]
where = ["src"]

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

- [ ] **Step 4: Paket + Smoke-Test anlegen**

`src/tda_server/__init__.py`:
```python
__version__ = "0.1.0"
```

`tests/test_smoke.py`:
```python
import tda_server

def test_package_importable():
    assert tda_server.__version__ == "0.1.0"
```

- [ ] **Step 5: venv anlegen, installieren, Test ausführen**

```bash
cd tda/server && python -m venv .venv && .venv/Scripts/python -m pip install -e ".[dev]"
.venv/Scripts/python -m pytest -q
```
Expected: `1 passed`

- [ ] **Step 6: Commit**

```bash
cd tda && git add -A && git commit -m "chore: scaffold tda server package"
```

---

### Task 2: Domain-Modell — SourceEvent, CanonicalEvent, Zustände

**Files:**
- Create: `server/src/tda_server/domain/__init__.py`, `server/src/tda_server/domain/events.py`
- Test: `server/tests/test_domain.py`

**Interfaces:**
- Produces:
  - `SourceEvent(source: str, source_event_id: str, origin_time: datetime, lat: float, lon: float, depth_km: float | None, magnitude: float, mag_type: str, received_at: datetime)` (frozen dataclass)
  - `EventState` (str-Enum): `DETECTED`, `ALERTED`, `CONFIRMED`, `RETRACTED`
  - `CanonicalEvent(event_id: str, state: EventState, origin_time, lat, lon, depth_km, magnitude: float, mag_low: float, mag_high: float, version: int, sources: dict[str, SourceEvent])` mit Methode `merge(se: SourceEvent) -> bool` (True, wenn sich Magnitude ≥ 0,2 oder Kernfelder geändert haben → Versionssprung durch Aufrufer)

- [ ] **Step 1: Failing Test schreiben**

`tests/test_domain.py`:
```python
from datetime import datetime, timezone
from tda_server.domain.events import CanonicalEvent, EventState, SourceEvent

def se(source: str, mag: float, eid: str = "x1") -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)

def test_source_event_is_frozen():
    ev = se("emsc", 5.0)
    try:
        ev.magnitude = 6.0  # type: ignore[misc]
        assert False, "should be frozen"
    except AttributeError:
        pass

def test_canonical_from_source():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    assert c.state is EventState.DETECTED
    assert c.magnitude == 5.0 and c.mag_low == 5.0 and c.mag_high == 5.0
    assert c.version == 1 and "emsc" in c.sources

def test_merge_escalates_magnitude_to_max():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    changed = c.merge(se("usgs", 5.6))
    assert changed is True
    assert c.magnitude == 5.6          # escalate fast: max gewinnt
    assert (c.mag_low, c.mag_high) == (5.0, 5.6)

def test_merge_lower_magnitude_does_not_deescalate():
    c = CanonicalEvent.from_source(se("emsc", 5.6))
    changed = c.merge(se("usgs", 5.1))
    assert c.magnitude == 5.6          # de-escalate slow: Maximum bleibt
    assert c.mag_low == 5.1
    assert changed is False            # kein publikationswürdiger Sprung

def test_merge_small_change_not_flagged():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    assert c.merge(se("usgs", 5.1)) is False   # < 0.2 Differenz
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_domain.py -q`
Expected: FAIL / ERROR mit `ModuleNotFoundError: tda_server.domain`

- [ ] **Step 3: Implementieren**

`src/tda_server/domain/__init__.py`: leer.

`src/tda_server/domain/events.py`:
```python
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum


class EventState(str, Enum):
    DETECTED = "detected"
    ALERTED = "alerted"
    CONFIRMED = "confirmed"
    RETRACTED = "retracted"


@dataclass(frozen=True)
class SourceEvent:
    source: str
    source_event_id: str
    origin_time: datetime
    lat: float
    lon: float
    depth_km: float | None
    magnitude: float
    mag_type: str
    received_at: datetime


@dataclass
class CanonicalEvent:
    event_id: str
    state: EventState
    origin_time: datetime
    lat: float
    lon: float
    depth_km: float | None
    magnitude: float
    mag_low: float
    mag_high: float
    version: int
    sources: dict[str, SourceEvent] = field(default_factory=dict)

    ESCALATION_DELTA = 0.2

    @classmethod
    def from_source(cls, se: SourceEvent) -> "CanonicalEvent":
        return cls(
            event_id=f"{se.source}:{se.source_event_id}",
            state=EventState.DETECTED,
            origin_time=se.origin_time,
            lat=se.lat,
            lon=se.lon,
            depth_km=se.depth_km,
            magnitude=se.magnitude,
            mag_low=se.magnitude,
            mag_high=se.magnitude,
            version=1,
            sources={se.source: se},
        )

    def merge(self, se: SourceEvent) -> bool:
        """Merge a source reading. Returns True if the alert-relevant estimate
        escalated (magnitude max rose by >= ESCALATION_DELTA). Early magnitudes
        are lower bounds: the max wins, de-escalation needs catalog consensus
        (handled at P2, not here)."""
        self.sources[se.source] = se
        self.mag_low = min(self.mag_low, se.magnitude)
        old_max = self.mag_high
        self.mag_high = max(self.mag_high, se.magnitude)
        escalated = (self.mag_high - old_max) >= self.ESCALATION_DELTA
        self.magnitude = self.mag_high
        return escalated
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_domain.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: domain model with escalate-fast merge"
```

---

### Task 3: Geo-Zellen, Haversine, Alarmradius

**Files:**
- Create: `server/src/tda_server/geo/__init__.py`, `server/src/tda_server/geo/cells.py`
- Test: `server/tests/test_cells.py`

**Interfaces:**
- Produces:
  - `haversine_km(lat1, lon1, lat2, lon2) -> float`
  - `cell_id(lat: float, lon: float) -> str` — 0,5°-Grid, Format `c{lat_idx}_{lon_idx}` (floor-Division, negative Indizes erlaubt)
  - `affected_cells(lat: float, lon: float, radius_km: float) -> set[str]`
  - `alert_radius_km(magnitude: float) -> float` — Tabelle: `<4.0 → 0`, `4.0–4.9 → 150`, `5.0–5.9 → 300`, `6.0–6.9 → 600`, `>=7.0 → 1000`
- **Testvektoren (identisch in Kotlin, Task 12):** `cell_id(41.0, 29.0) == "c82_58"`, `cell_id(40.99, 28.99) == "c81_57"`, `cell_id(-1.0, -1.0) == "c-2_-2"`

- [ ] **Step 1: Failing Test schreiben**

`tests/test_cells.py`:
```python
from tda_server.geo.cells import affected_cells, alert_radius_km, cell_id, haversine_km

def test_haversine_istanbul_ankara():
    d = haversine_km(41.01, 28.98, 39.93, 32.86)
    assert 340 < d < 360   # ~350 km

def test_cell_id_vectors():
    assert cell_id(41.0, 29.0) == "c82_58"
    assert cell_id(40.99, 28.99) == "c81_57"
    assert cell_id(-1.0, -1.0) == "c-2_-2"

def test_affected_cells_contains_center_and_scales():
    small = affected_cells(41.0, 29.0, 10)
    big = affected_cells(41.0, 29.0, 300)
    assert cell_id(41.0, 29.0) in small
    assert small < big

def test_alert_radius_table():
    assert alert_radius_km(3.9) == 0
    assert alert_radius_km(4.0) == 150
    assert alert_radius_km(5.5) == 300
    assert alert_radius_km(6.2) == 600
    assert alert_radius_km(7.8) == 1000
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_cells.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/geo/__init__.py`: leer.

`src/tda_server/geo/cells.py`:
```python
from __future__ import annotations

import math

CELL_DEG = 0.5
_EARTH_R_KM = 6371.0


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * _EARTH_R_KM * math.asin(math.sqrt(a))


def cell_id(lat: float, lon: float) -> str:
    return f"c{math.floor(lat / CELL_DEG)}_{math.floor(lon / CELL_DEG)}"


def affected_cells(lat: float, lon: float, radius_km: float) -> set[str]:
    """All 0.5-degree cells whose center could lie within radius_km.
    Bounding-box scan with a half-cell margin; coarse on purpose - the client
    filters precisely against its own thresholds."""
    lat_margin = radius_km / 111.0 + CELL_DEG
    lon_scale = max(0.2, math.cos(math.radians(lat)))
    lon_margin = radius_km / (111.0 * lon_scale) + CELL_DEG
    cells: set[str] = set()
    la = lat - lat_margin
    while la <= lat + lat_margin:
        lo = lon - lon_margin
        while lo <= lon + lon_margin:
            cells.add(cell_id(la, lo))
            lo += CELL_DEG
        la += CELL_DEG
    return cells


def alert_radius_km(magnitude: float) -> float:
    if magnitude < 4.0:
        return 0.0
    if magnitude < 5.0:
        return 150.0
    if magnitude < 6.0:
        return 300.0
    if magnitude < 7.0:
        return 600.0
    return 1000.0
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_cells.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: geo cells, haversine, alert radius table"
```

---

### Task 4: Korrelator — Quellereignisse zu kanonischen Ereignissen

**Files:**
- Create: `server/src/tda_server/fusion/__init__.py`, `server/src/tda_server/fusion/correlator.py`
- Test: `server/tests/test_correlator.py`

**Interfaces:**
- Consumes: `SourceEvent`, `CanonicalEvent`, `EventState` (Task 2), `haversine_km` (Task 3)
- Produces:
  - `Transition(event: CanonicalEvent, kind: str)` — NamedTuple; `kind ∈ {"new", "escalate", "confirm"}`
  - `Correlator(window_s: float = 180.0, radius_km: float = 120.0)` mit `ingest(se: SourceEvent) -> Transition | None`. Regeln: gleiche Quelle+ID → Update desselben Ereignisses; sonst räumlich-zeitliche Übereinstimmung → Merge; sonst neues Ereignis. `confirm` beim Eintreffen der **zweiten unabhängigen Quelle** (Zustand → `CONFIRMED`). `None`, wenn nichts Publikationswürdiges passiert ist.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_correlator.py`:
```python
from datetime import datetime, timedelta, timezone
from tda_server.domain.events import EventState, SourceEvent
from tda_server.fusion.correlator import Correlator

T0 = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)

def se(source: str, eid: str, mag: float, dt_s: float = 0.0,
       lat: float = 40.7, lon: float = 29.1) -> SourceEvent:
    t = T0 + timedelta(seconds=dt_s)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=lat, lon=lon, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)

def test_new_event_yields_new_transition():
    c = Correlator()
    tr = c.ingest(se("emsc", "e1", 5.2))
    assert tr is not None and tr.kind == "new"
    assert tr.event.version == 1

def test_second_source_confirms_and_bumps_version():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("usgs", "u9", 5.3, dt_s=20))
    assert tr is not None and tr.kind == "confirm"
    assert tr.event.state is EventState.CONFIRMED
    assert tr.event.version == 2
    assert set(tr.event.sources) == {"emsc", "usgs"}

def test_escalation_transition_on_big_jump():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.0))
    c.ingest(se("usgs", "u9", 5.1, dt_s=10))      # confirm
    tr = c.ingest(se("afad", "a3", 6.0, dt_s=30)) # +0.9 -> escalate
    assert tr is not None and tr.kind == "escalate"
    assert tr.event.magnitude == 6.0

def test_far_event_is_separate():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("usgs", "u9", 5.2, lat=36.0, lon=36.0))
    assert tr is not None and tr.kind == "new"

def test_same_source_update_no_duplicate_event():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("emsc", "e1", 5.25, dt_s=60))
    assert tr is None                # kleines Update, nichts zu publizieren
    assert len(c.events) == 1
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_correlator.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/fusion/__init__.py`: leer.

`src/tda_server/fusion/correlator.py`:
```python
from __future__ import annotations

from typing import NamedTuple

from tda_server.domain.events import CanonicalEvent, EventState, SourceEvent
from tda_server.geo.cells import haversine_km


class Transition(NamedTuple):
    event: CanonicalEvent
    kind: str  # "new" | "escalate" | "confirm"


class Correlator:
    def __init__(self, window_s: float = 180.0, radius_km: float = 120.0) -> None:
        self.window_s = window_s
        self.radius_km = radius_km
        self.events: list[CanonicalEvent] = []
        self._by_source_id: dict[tuple[str, str], CanonicalEvent] = {}

    def ingest(self, se: SourceEvent) -> Transition | None:
        ev = self._by_source_id.get((se.source, se.source_event_id))
        if ev is None:
            ev = self._match(se)
        if ev is None:
            ev = CanonicalEvent.from_source(se)
            self.events.append(ev)
            self._by_source_id[(se.source, se.source_event_id)] = ev
            return Transition(ev, "new")

        self._by_source_id[(se.source, se.source_event_id)] = ev
        known_sources = set(ev.sources)
        escalated = ev.merge(se)
        confirmed = (
            se.source not in known_sources
            and len(ev.sources) >= 2
            and ev.state is not EventState.CONFIRMED
        )
        if confirmed:
            ev.state = EventState.CONFIRMED
        if confirmed or escalated:
            ev.version += 1
            return Transition(ev, "escalate" if escalated else "confirm")
        return None

    def _match(self, se: SourceEvent) -> CanonicalEvent | None:
        for ev in self.events:
            dt = abs((se.origin_time - ev.origin_time).total_seconds())
            if dt > self.window_s:
                continue
            if haversine_km(se.lat, se.lon, ev.lat, ev.lon) <= self.radius_km:
                return ev
        return None
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_correlator.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: correlator with confirm/escalate transitions"
```

---

### Task 5: Payload — Bau, Kanonisierung, Ed25519-Signatur

**Files:**
- Create: `server/src/tda_server/alert/__init__.py`, `server/src/tda_server/alert/payload.py`, `server/scripts/gen_signing_key.py`
- Test: `server/tests/test_payload.py`

**Interfaces:**
- Consumes: `Transition` (Task 4), `cell_id/alert_radius_km/affected_cells` (Task 3)
- Produces:
  - `build_payload(tr: Transition, *, test: bool = False, now_ms: int) -> dict[str, str]` — Felder (alle String): `v="1"`, `id`, `ver`, `state`, `tier="P1"`, `test` ("0"/"1"), `origin_ts` (Unix ms), `lat`/`lon` (Format `%.4f`), `depth_km` (`%.1f` oder `""`), `mag`/`mag_hi` (`%.1f`), `src` (kommagetrennte Quellen, sortiert), `issued_ts`
  - `canonical_bytes(payload: dict[str, str]) -> bytes` — Zeilen `key=value`, Keys sortiert, `\n`-getrennt, UTF-8, Feld `sig` ausgenommen
  - `sign_payload(payload, private_key_b64: str) -> dict[str, str]` — fügt `sig` (base64, Ed25519 über `canonical_bytes`) hinzu
  - `verify_payload(payload_with_sig, public_key_b64: str) -> bool`
  - Script `gen_signing_key.py`: druckt `TDA_SIGNING_KEY=<b64 private>` und `TDA_PUBLIC_KEY=<b64 public>`

- [ ] **Step 1: Failing Test schreiben**

`tests/test_payload.py`:
```python
import base64
from datetime import datetime, timezone
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from tda_server.alert.payload import build_payload, canonical_bytes, sign_payload, verify_payload
from tda_server.domain.events import CanonicalEvent, SourceEvent
from tda_server.fusion.correlator import Transition

def make_transition() -> Transition:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    se = SourceEvent(source="emsc", source_event_id="e1", origin_time=t,
                     lat=40.7123, lon=29.0567, depth_km=9.96, magnitude=5.55,
                     mag_type="ml", received_at=t)
    return Transition(CanonicalEvent.from_source(se), "new")

def keypair_b64() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    from cryptography.hazmat.primitives import serialization as ser
    priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(priv_raw).decode(), base64.b64encode(pub_raw).decode()

def test_payload_fields_are_strings_and_formatted():
    p = build_payload(make_transition(), now_ms=1755691205000)
    assert all(isinstance(v, str) for v in p.values())
    assert p["lat"] == "40.7123" and p["mag"] == "5.6" and p["test"] == "0"
    assert p["origin_ts"] == "1755691200000"
    assert len(str(p).encode()) < 1024

def test_canonical_bytes_sorted_and_excludes_sig():
    p = {"b": "2", "a": "1", "sig": "zzz"}
    assert canonical_bytes(p) == b"a=1\nb=2"

def test_sign_and_verify_roundtrip():
    priv_b64, pub_b64 = keypair_b64()
    p = sign_payload(build_payload(make_transition(), now_ms=0), priv_b64)
    assert verify_payload(p, pub_b64) is True
    p["mag"] = "9.9"
    assert verify_payload(p, pub_b64) is False
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_payload.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/alert/__init__.py`: leer.

`src/tda_server/alert/payload.py`:
```python
from __future__ import annotations

import base64

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

from tda_server.fusion.correlator import Transition


def build_payload(tr: Transition, *, test: bool = False, now_ms: int) -> dict[str, str]:
    ev = tr.event
    origin_ms = int(ev.origin_time.timestamp() * 1000)
    return {
        "v": "1",
        "id": ev.event_id,
        "ver": str(ev.version),
        "state": ev.state.value,
        "tier": "P1",
        "test": "1" if test else "0",
        "origin_ts": str(origin_ms),
        "lat": f"{ev.lat:.4f}",
        "lon": f"{ev.lon:.4f}",
        "depth_km": "" if ev.depth_km is None else f"{ev.depth_km:.1f}",
        "mag": f"{ev.magnitude:.1f}",
        "mag_hi": f"{ev.mag_high:.1f}",
        "src": ",".join(sorted(ev.sources)),
        "issued_ts": str(now_ms),
    }


def canonical_bytes(payload: dict[str, str]) -> bytes:
    lines = [f"{k}={payload[k]}" for k in sorted(payload) if k != "sig"]
    return "\n".join(lines).encode("utf-8")


def sign_payload(payload: dict[str, str], private_key_b64: str) -> dict[str, str]:
    raw = base64.b64decode(private_key_b64)
    key = Ed25519PrivateKey.from_private_bytes(raw)
    sig = key.sign(canonical_bytes(payload))
    out = dict(payload)
    out["sig"] = base64.b64encode(sig).decode()
    return out


def verify_payload(payload: dict[str, str], public_key_b64: str) -> bool:
    if "sig" not in payload:
        return False
    key = Ed25519PublicKey.from_public_bytes(base64.b64decode(public_key_b64))
    try:
        key.verify(base64.b64decode(payload["sig"]), canonical_bytes(payload))
        return True
    except Exception:
        return False
```

`scripts/gen_signing_key.py`:
```python
import base64

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

priv = Ed25519PrivateKey.generate()
priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
print("TDA_SIGNING_KEY=" + base64.b64encode(priv_raw).decode())
print("TDA_PUBLIC_KEY=" + base64.b64encode(pub_raw).decode())
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_payload.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: signed self-contained alert payload"
```

---

### Task 6: Ereignisstrom — Protokoll, InMemory, Redis Streams

**Files:**
- Create: `server/src/tda_server/stream/__init__.py`, `server/src/tda_server/stream/base.py`, `server/src/tda_server/stream/redis_stream.py`
- Test: `server/tests/test_stream.py`

**Interfaces:**
- Produces:
  - `EventStream` (Protocol): `async append(record: dict[str, str]) -> None`, `async read(start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]` (blockierend ab Stromende; `read` liefert `(stream_id, record)`)
  - `InMemoryStream()` — für Tests/Replay
  - `RedisStream(url: str, key: str = "tda:source_events")` — XADD/XREAD-Implementierung
  - `serialize_source_event(se: SourceEvent) -> dict[str, str]` und `deserialize_source_event(d) -> SourceEvent`

- [ ] **Step 1: Failing Test schreiben**

`tests/test_stream.py`:
```python
import asyncio
from datetime import datetime, timezone
import fakeredis.aioredis
from tda_server.domain.events import SourceEvent
from tda_server.stream.base import InMemoryStream, deserialize_source_event, serialize_source_event
from tda_server.stream.redis_stream import RedisStream

def sample() -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source="usgs", source_event_id="u1", origin_time=t,
                       lat=38.1, lon=27.2, depth_km=None, magnitude=4.4,
                       mag_type="mb", received_at=t)

def test_serialize_roundtrip_none_depth():
    se = sample()
    assert deserialize_source_event(serialize_source_event(se)) == se

async def test_inmemory_append_then_read():
    s = InMemoryStream()
    await s.append({"a": "1"})
    it = s.read()
    sid, rec = await asyncio.wait_for(anext(it), timeout=1)
    assert rec == {"a": "1"} and sid

async def test_redis_stream_roundtrip():
    r = fakeredis.aioredis.FakeRedis(decode_responses=True)
    s = RedisStream.from_client(r)
    await s.append(serialize_source_event(sample()))
    sid, rec = await asyncio.wait_for(anext(s.read()), timeout=2)
    assert deserialize_source_event(rec) == sample()
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_stream.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/stream/__init__.py`: leer.

`src/tda_server/stream/base.py`:
```python
from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import AsyncIterator, Protocol

from tda_server.domain.events import SourceEvent


class EventStream(Protocol):
    async def append(self, record: dict[str, str]) -> None: ...
    def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]: ...


def serialize_source_event(se: SourceEvent) -> dict[str, str]:
    return {
        "source": se.source,
        "source_event_id": se.source_event_id,
        "origin_ts": str(int(se.origin_time.timestamp() * 1000)),
        "lat": repr(se.lat),
        "lon": repr(se.lon),
        "depth_km": "" if se.depth_km is None else repr(se.depth_km),
        "magnitude": repr(se.magnitude),
        "mag_type": se.mag_type,
        "received_ts": str(int(se.received_at.timestamp() * 1000)),
    }


def deserialize_source_event(d: dict[str, str]) -> SourceEvent:
    def ts(ms: str) -> datetime:
        return datetime.fromtimestamp(int(ms) / 1000, tz=timezone.utc)

    return SourceEvent(
        source=d["source"],
        source_event_id=d["source_event_id"],
        origin_time=ts(d["origin_ts"]),
        lat=float(d["lat"]),
        lon=float(d["lon"]),
        depth_km=None if d["depth_km"] == "" else float(d["depth_km"]),
        magnitude=float(d["magnitude"]),
        mag_type=d["mag_type"],
        received_at=ts(d["received_ts"]),
    )


class InMemoryStream:
    def __init__(self) -> None:
        self._items: list[tuple[str, dict[str, str]]] = []
        self._new = asyncio.Event()

    async def append(self, record: dict[str, str]) -> None:
        self._items.append((str(len(self._items) + 1), dict(record)))
        self._new.set()

    async def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]:
        idx = int(start_id)
        while True:
            while idx < len(self._items):
                yield self._items[idx]
                idx += 1
            self._new.clear()
            await self._new.wait()
```

`src/tda_server/stream/redis_stream.py`:
```python
from __future__ import annotations

from typing import AsyncIterator

import redis.asyncio as aioredis


class RedisStream:
    def __init__(self, url: str, key: str = "tda:source_events") -> None:
        self._r = aioredis.from_url(url, decode_responses=True)
        self.key = key

    @classmethod
    def from_client(cls, client, key: str = "tda:source_events") -> "RedisStream":
        obj = cls.__new__(cls)
        obj._r = client
        obj.key = key
        return obj

    async def append(self, record: dict[str, str]) -> None:
        await self._r.xadd(self.key, record)

    async def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]:
        last = start_id
        while True:
            resp = await self._r.xread({self.key: last}, block=1000, count=100)
            for _key, entries in resp or []:
                for sid, rec in entries:
                    yield sid, rec
                    last = sid
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_stream.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: append-only event stream (in-memory + redis)"
```

---

### Task 7: EMSC-Adapter (WebSocket)

**Files:**
- Create: `server/src/tda_server/adapters/__init__.py`, `server/src/tda_server/adapters/emsc.py`, `server/tests/fixtures/emsc_create.json`
- Test: `server/tests/test_emsc.py`

**Interfaces:**
- Consumes: `SourceEvent` (Task 2)
- Produces:
  - `parse_emsc_message(text: str, received_at: datetime) -> SourceEvent | None` (None bei Nicht-Ereignis-Nachrichten)
  - `run_emsc(stream: EventStream, url: str = EMSC_WS_URL) -> None` — verbindet, parst, `append`t; Reconnect mit Exponential-Backoff (1→60 s). `EMSC_WS_URL = "wss://www.seismicportal.eu/standing_order/websocket"`

- [ ] **Step 1: Echte Fixture aufzeichnen**

EMSC-Nachrichten haben die dokumentierte Form `{"action": "create"|"update", "data": {GeoJSON-Feature}}`. Fixture aus einer realen Verbindung aufzeichnen (läuft ~1–3 Min, bis weltweit ein Beben gemeldet wird):

```bash
.venv/Scripts/python - << 'EOF'
import asyncio, websockets
async def main():
    async with websockets.connect(
        "wss://www.seismicportal.eu/standing_order/websocket") as ws:
        msg = await ws.recv()
        open("tests/fixtures/emsc_create.json", "w", encoding="utf-8").write(msg)
        print(msg[:300])
asyncio.run(main())
EOF
```

Falls in 5 Minuten nichts kommt: Skript abbrechen, erneut versuchen (weltweit gibt es im Schnitt alle paar Minuten ein M≥4-Beben). Fixture-Struktur prüfen: `data.properties` muss `time, lat, lon, depth, mag, magtype, unid` enthalten — weicht die reale Struktur ab, **Parser an die Realität anpassen, nie umgekehrt**.

- [ ] **Step 2: Failing Test schreiben**

`tests/test_emsc.py`:
```python
import json
from datetime import datetime, timezone
from pathlib import Path
from tda_server.adapters.emsc import parse_emsc_message

FIX = Path(__file__).parent / "fixtures" / "emsc_create.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)

def test_parse_real_create_message():
    se = parse_emsc_message(FIX.read_text(encoding="utf-8"), NOW)
    assert se is not None
    assert se.source == "emsc"
    raw = json.loads(FIX.read_text(encoding="utf-8"))["data"]["properties"]
    assert se.source_event_id == str(raw["unid"])
    assert se.magnitude == float(raw["mag"])
    assert se.received_at == NOW

def test_parse_garbage_returns_none():
    assert parse_emsc_message("not json", NOW) is None
    assert parse_emsc_message('{"action":"ping"}', NOW) is None
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_emsc.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 4: Implementieren**

`src/tda_server/adapters/__init__.py`: leer.

`src/tda_server/adapters/emsc.py`:
```python
from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

import websockets

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
EMSC_WS_URL = "wss://www.seismicportal.eu/standing_order/websocket"


def parse_emsc_message(text: str, received_at: datetime) -> SourceEvent | None:
    try:
        msg = json.loads(text)
        props = msg["data"]["properties"]
        origin = datetime.fromisoformat(props["time"].replace("Z", "+00:00"))
        depth = props.get("depth")
        return SourceEvent(
            source="emsc",
            source_event_id=str(props["unid"]),
            origin_time=origin.astimezone(timezone.utc),
            lat=float(props["lat"]),
            lon=float(props["lon"]),
            depth_km=None if depth is None else float(depth),
            magnitude=float(props["mag"]),
            mag_type=str(props.get("magtype", "")),
            received_at=received_at,
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        return None


async def run_emsc(stream: EventStream, url: str = EMSC_WS_URL) -> None:
    backoff = 1.0
    while True:
        try:
            async with websockets.connect(url, ping_interval=20) as ws:
                log.info("emsc connected")
                backoff = 1.0
                async for text in ws:
                    se = parse_emsc_message(text, datetime.now(timezone.utc))
                    if se is not None:
                        await stream.append(serialize_source_event(se))
        except Exception as exc:  # noqa: BLE001 - reconnect loop by design
            log.warning("emsc connection lost (%s); retry in %.0fs", exc, backoff)
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, 60.0)
```

- [ ] **Step 5: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_emsc.py -q`
Expected: `2 passed`

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: EMSC websocket adapter with real fixture"
```

---

### Task 8: USGS-Adapter (GeoJSON-Poller)

**Files:**
- Create: `server/src/tda_server/adapters/usgs.py`, `server/tests/fixtures/usgs_all_hour.json`
- Test: `server/tests/test_usgs.py`

**Interfaces:**
- Produces:
  - `parse_usgs_feed(doc: dict, received_at: datetime) -> list[SourceEvent]`
  - `run_usgs(stream: EventStream, url: str = USGS_FEED_URL, interval_s: float = 60.0, seen: set[str] | None = None) -> None` — pollt, dedupliziert über Feature-ID + `updated`-Zeitstempel. `USGS_FEED_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson"`

- [ ] **Step 1: Echte Fixture aufzeichnen**

```bash
curl -s "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson" -o tests/fixtures/usgs_all_hour.json
```
Prüfen: Datei enthält `"features": [...]` mit mindestens einem Eintrag (sonst nach ein paar Minuten erneut — leere Stunde ist selten). Struktur je Feature: `id`, `properties.time` (ms), `properties.mag`, `properties.magType`, `properties.updated`, `geometry.coordinates = [lon, lat, depth]`.

- [ ] **Step 2: Failing Test schreiben**

`tests/test_usgs.py`:
```python
import json
from datetime import datetime, timezone
from pathlib import Path
from tda_server.adapters.usgs import parse_usgs_feed

FIX = Path(__file__).parent / "fixtures" / "usgs_all_hour.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)

def test_parse_real_feed():
    doc = json.loads(FIX.read_text(encoding="utf-8"))
    events = parse_usgs_feed(doc, NOW)
    assert len(events) == len(doc["features"])
    first, raw = events[0], doc["features"][0]
    assert first.source == "usgs"
    assert first.source_event_id.startswith(raw["id"])
    assert first.lat == raw["geometry"]["coordinates"][1]
    assert first.origin_time.tzinfo is not None

def test_feature_without_mag_is_skipped():
    doc = {"features": [{"id": "x", "properties": {"time": 0, "mag": None,
                                                   "magType": None, "updated": 1},
                         "geometry": {"coordinates": [1.0, 2.0, 3.0]}}]}
    assert parse_usgs_feed(doc, NOW) == []
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_usgs.py -q`
Expected: `ImportError`

- [ ] **Step 4: Implementieren**

`src/tda_server/adapters/usgs.py`:
```python
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

import httpx

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
USGS_FEED_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson"


def parse_usgs_feed(doc: dict, received_at: datetime) -> list[SourceEvent]:
    out: list[SourceEvent] = []
    for feat in doc.get("features", []):
        props = feat.get("properties", {})
        coords = feat.get("geometry", {}).get("coordinates", [None, None, None])
        if props.get("mag") is None or props.get("time") is None:
            continue
        # updated-timestamp in the id makes revisions distinct source events
        out.append(SourceEvent(
            source="usgs",
            source_event_id=f'{feat["id"]}:{props.get("updated", 0)}',
            origin_time=datetime.fromtimestamp(props["time"] / 1000, tz=timezone.utc),
            lat=float(coords[1]),
            lon=float(coords[0]),
            depth_km=None if coords[2] is None else float(coords[2]),
            magnitude=float(props["mag"]),
            mag_type=str(props.get("magType") or ""),
            received_at=received_at,
        ))
    return out


async def run_usgs(stream: EventStream, url: str = USGS_FEED_URL,
                   interval_s: float = 60.0, seen: set[str] | None = None) -> None:
    seen = set() if seen is None else seen
    async with httpx.AsyncClient(timeout=20) as client:
        while True:
            try:
                resp = await client.get(url)
                resp.raise_for_status()
                for se in parse_usgs_feed(resp.json(), datetime.now(timezone.utc)):
                    if se.source_event_id not in seen:
                        seen.add(se.source_event_id)
                        await stream.append(serialize_source_event(se))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("usgs poll failed: %s", exc)
            await asyncio.sleep(interval_s)
```

- [ ] **Step 5: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_usgs.py -q`
Expected: `2 passed`

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: USGS feed adapter with real fixture"
```

---

### Task 9: AFAD-Adapter (event-service-Poller)

**Files:**
- Create: `server/src/tda_server/adapters/afad.py`, `server/tests/fixtures/afad_filter.json`
- Test: `server/tests/test_afad.py`

**Interfaces:**
- Produces:
  - `parse_afad_response(items: list[dict], received_at: datetime) -> list[SourceEvent]`
  - `run_afad(stream, url_base: str = AFAD_URL, interval_s: float = 60.0, seen: set[str] | None = None) -> None` — pollt rollierendes Zeitfenster (letzte 30 Min). `AFAD_URL = "https://deprem.afad.gov.tr/apiv2/event/filter"`

**Wichtig:** Die AFAD-Feldnamen sind öffentlich weniger stabil dokumentiert als USGS/EMSC. Schritt 1 zeichnet die reale Antwort auf; **der Parser wird an die tatsächliche Antwort angepasst** (erwartete Felder laut bekannter API-Version: `eventID`, `date` (ISO, UTC), `latitude`, `longitude`, `depth`, `magnitude`, `type`). Weicht die Realität ab, gilt die Realität; die Zugangsprüfung gemäß Spec §9 bleibt davon unberührt (Entwicklung nutzt moderate Poll-Raten).

- [ ] **Step 1: Echte Fixture aufzeichnen**

```bash
curl -s "https://deprem.afad.gov.tr/apiv2/event/filter?start=2026-08-19T00:00:00&end=2026-08-20T00:00:00&minmag=2" -o tests/fixtures/afad_filter.json
head -c 500 tests/fixtures/afad_filter.json
```
Erwartet: JSON-Array von Ereignisobjekten. Ist die Antwort leer, Zeitfenster vergrößern (die Türkei hat täglich M≥2-Beben). Schlägt der Endpunkt fehl (403/Timeout), Task pausieren und als Blocker melden — **nicht** auf Scraping ausweichen.

- [ ] **Step 2: Failing Test schreiben**

`tests/test_afad.py`:
```python
import json
from datetime import datetime, timezone
from pathlib import Path
from tda_server.adapters.afad import parse_afad_response

FIX = Path(__file__).parent / "fixtures" / "afad_filter.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)

def test_parse_real_response():
    items = json.loads(FIX.read_text(encoding="utf-8"))
    events = parse_afad_response(items, NOW)
    assert len(events) == len(items) and len(events) > 0
    first = events[0]
    assert first.source == "afad"
    assert first.origin_time.tzinfo is not None
    assert 25.0 < first.lon < 46.0 and 34.0 < first.lat < 43.5  # Türkei-Region

def test_item_missing_fields_skipped():
    assert parse_afad_response([{"eventID": "1"}], NOW) == []
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_afad.py -q`
Expected: `ImportError`

- [ ] **Step 4: Implementieren**

`src/tda_server/adapters/afad.py`:
```python
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone

import httpx

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
AFAD_URL = "https://deprem.afad.gov.tr/apiv2/event/filter"


def parse_afad_response(items: list[dict], received_at: datetime) -> list[SourceEvent]:
    out: list[SourceEvent] = []
    for it in items:
        try:
            raw_date = str(it["date"])
            origin = datetime.fromisoformat(raw_date.replace("Z", "+00:00"))
            if origin.tzinfo is None:
                origin = origin.replace(tzinfo=timezone.utc)
            out.append(SourceEvent(
                source="afad",
                source_event_id=str(it["eventID"]),
                origin_time=origin.astimezone(timezone.utc),
                lat=float(it["latitude"]),
                lon=float(it["longitude"]),
                depth_km=float(it["depth"]) if it.get("depth") is not None else None,
                magnitude=float(it["magnitude"]),
                mag_type=str(it.get("type") or ""),
                received_at=received_at,
            ))
        except (KeyError, TypeError, ValueError):
            continue
    return out


async def run_afad(stream: EventStream, url_base: str = AFAD_URL,
                   interval_s: float = 60.0, seen: set[str] | None = None) -> None:
    seen = set() if seen is None else seen
    async with httpx.AsyncClient(timeout=20) as client:
        while True:
            try:
                now = datetime.now(timezone.utc)
                params = {
                    "start": (now - timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%S"),
                    "end": now.strftime("%Y-%m-%dT%H:%M:%S"),
                    "minmag": "2",
                }
                resp = await client.get(url_base, params=params)
                resp.raise_for_status()
                for se in parse_afad_response(resp.json(), now):
                    if se.source_event_id not in seen:
                        seen.add(se.source_event_id)
                        await stream.append(serialize_source_event(se))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("afad poll failed: %s", exc)
            await asyncio.sleep(interval_s)
```

- [ ] **Step 5: Test ausführen — muss bestehen (Parser ggf. an reale Fixture anpassen)**

Run: `.venv/Scripts/python -m pytest tests/test_afad.py -q`
Expected: `2 passed`. Schlägt `test_parse_real_response` wegen anderer Feldnamen fehl: reale Feldnamen aus der Fixture ablesen, Parser anpassen, Test erneut.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: AFAD event-service adapter with real fixture"
```

---

### Task 10: Publisher — Transport-Protokoll, Fake, FCM

**Files:**
- Create: `server/src/tda_server/alert/publisher.py`
- Test: `server/tests/test_publisher.py`

**Interfaces:**
- Consumes: `Transition` (Task 4), `build_payload/sign_payload` (Task 5), `affected_cells/alert_radius_km` (Task 3)
- Produces:
  - `Transport` (Protocol): `async send(topic: str, data: dict[str, str]) -> None`
  - `FakeTransport()` mit `sent: list[tuple[str, dict[str, str]]]`
  - `FcmTransport(project_id: str, credentials_path: str)` — FCM HTTP v1, `POST /v1/projects/{pid}/messages:send`, `android.priority="HIGH"`; OAuth2-Token via `google-auth` **nur wenn konfiguriert** (Import lazy, damit Tests ohne Dependency laufen)
  - `Publisher(transport, private_key_b64: str, min_mag: float = 4.0)` mit `async publish(tr: Transition, *, test: bool = False, now_ms: int) -> int` (Anzahl Topics; 0 wenn unter `min_mag` oder (event_id, version) schon publiziert — Idempotenz). Topic-Name: `cell_{cell_id}` .

- [ ] **Step 1: Failing Test schreiben**

`tests/test_publisher.py`:
```python
import base64
from datetime import datetime, timezone
from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from tda_server.alert.payload import verify_payload
from tda_server.alert.publisher import FakeTransport, Publisher
from tda_server.domain.events import CanonicalEvent, SourceEvent
from tda_server.fusion.correlator import Transition

def keys() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    pr = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pu = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(pr).decode(), base64.b64encode(pu).decode()

def tr(mag: float) -> Transition:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    se = SourceEvent(source="emsc", source_event_id="e1", origin_time=t,
                     lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                     mag_type="ml", received_at=t)
    return Transition(CanonicalEvent.from_source(se), "new")

async def test_publishes_signed_payload_to_cell_topics():
    priv, pub = keys()
    ft = FakeTransport()
    n = await Publisher(ft, priv).publish(tr(5.5), now_ms=1)
    assert n > 0 and len(ft.sent) == n
    topic, data = ft.sent[0]
    assert topic.startswith("cell_c")
    assert verify_payload(data, pub) is True

async def test_below_min_mag_not_published():
    priv, _ = keys()
    ft = FakeTransport()
    assert await Publisher(ft, priv).publish(tr(3.5), now_ms=1) == 0
    assert ft.sent == []

async def test_same_event_version_published_once():
    priv, _ = keys()
    ft = FakeTransport()
    p = Publisher(ft, priv)
    t1 = tr(5.5)
    n1 = await p.publish(t1, now_ms=1)
    n2 = await p.publish(t1, now_ms=2)   # gleiche (id, version)
    assert n1 > 0 and n2 == 0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_publisher.py -q`
Expected: `ImportError`

- [ ] **Step 3: Implementieren**

`src/tda_server/alert/publisher.py`:
```python
from __future__ import annotations

import logging
from typing import Protocol

import httpx

from tda_server.alert.payload import build_payload, sign_payload
from tda_server.fusion.correlator import Transition
from tda_server.geo.cells import affected_cells, alert_radius_km

log = logging.getLogger(__name__)


class Transport(Protocol):
    async def send(self, topic: str, data: dict[str, str]) -> None: ...


class FakeTransport:
    def __init__(self) -> None:
        self.sent: list[tuple[str, dict[str, str]]] = []

    async def send(self, topic: str, data: dict[str, str]) -> None:
        self.sent.append((topic, data))


class FcmTransport:
    def __init__(self, project_id: str, credentials_path: str) -> None:
        self.url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
        self.credentials_path = credentials_path
        self._client = httpx.AsyncClient(timeout=10)

    def _token(self) -> str:
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account

        creds = service_account.Credentials.from_service_account_file(
            self.credentials_path,
            scopes=["https://www.googleapis.com/auth/firebase.messaging"],
        )
        creds.refresh(Request())
        return creds.token

    async def send(self, topic: str, data: dict[str, str]) -> None:
        body = {"message": {"topic": topic, "data": data,
                            "android": {"priority": "HIGH"}}}
        resp = await self._client.post(
            self.url, json=body,
            headers={"Authorization": f"Bearer {self._token()}"})
        resp.raise_for_status()


class Publisher:
    def __init__(self, transport: Transport, private_key_b64: str,
                 min_mag: float = 4.0) -> None:
        self.transport = transport
        self.private_key_b64 = private_key_b64
        self.min_mag = min_mag
        self._published: set[tuple[str, int]] = set()

    async def publish(self, tr: Transition, *, test: bool = False,
                      now_ms: int) -> int:
        ev = tr.event
        key = (ev.event_id, ev.version)
        if key in self._published:
            return 0
        radius = alert_radius_km(ev.magnitude)
        if ev.magnitude < self.min_mag or radius == 0:
            return 0
        payload = sign_payload(
            build_payload(tr, test=test, now_ms=now_ms), self.private_key_b64)
        cells = sorted(affected_cells(ev.lat, ev.lon, radius))
        for cell in cells:
            await self.transport.send(f"cell_{cell}", payload)
        self._published.add(key)
        log.info("published %s v%s to %d cells", ev.event_id, ev.version, len(cells))
        return len(cells)
```

Hinweis: `google-auth` wird erst gebraucht, wenn `FcmTransport` real genutzt wird (Task 13). Dann: `pip install google-auth` und in `pyproject.toml` unter `dependencies` ergänzen: `"google-auth>=2.29"`.

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_publisher.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: idempotent publisher with fake and FCM transports"
```

---

### Task 11: Pipeline-Verdrahtung, Konfiguration, Replay-CLI

**Files:**
- Create: `server/src/tda_server/config.py`, `server/src/tda_server/pipeline.py`, `server/src/tda_server/replay.py`
- Test: `server/tests/test_pipeline.py`

**Interfaces:**
- Consumes: alles Bisherige
- Produces:
  - `Config.from_env()` — liest `TDA_SIGNING_KEY` (Pflicht), `TDA_REDIS_URL` (optional; ohne → InMemory), `TDA_FCM_PROJECT_ID`/`TDA_FCM_CREDENTIALS` (optional; ohne → FakeTransport mit Log), `TDA_MIN_PUBLISH_MAG` (Default 4.0)
  - `run_consumer(stream, correlator, publisher, *, start_id="0", stop_after: int | None = None) -> int` — liest Strom, korreliert, publiziert; `stop_after` (Anzahl Records) für Tests/Replay
  - `python -m tda_server.pipeline` — startet Adapter (EMSC, USGS, AFAD) + Konsument
  - `python -m tda_server.replay <file.jsonl>` — spielt aufgezeichnete `SourceEvent`-Records durch den Konsumenten mit FakeTransport und druckt Transitions

- [ ] **Step 1: Failing Test schreiben**

`tests/test_pipeline.py`:
```python
import base64
from datetime import datetime, timezone
from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from tda_server.alert.publisher import FakeTransport, Publisher
from tda_server.domain.events import SourceEvent
from tda_server.fusion.correlator import Correlator
from tda_server.pipeline import run_consumer
from tda_server.stream.base import InMemoryStream, serialize_source_event

def priv_b64() -> str:
    k = Ed25519PrivateKey.generate()
    raw = k.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    return base64.b64encode(raw).decode()

def se(source: str, eid: str, mag: float) -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)

async def test_stream_to_publish_end_to_end():
    stream = InMemoryStream()
    await stream.append(serialize_source_event(se("emsc", "e1", 5.4)))
    await stream.append(serialize_source_event(se("usgs", "u1", 5.5)))
    ft = FakeTransport()
    n = await run_consumer(stream, Correlator(), Publisher(ft, priv_b64()),
                           stop_after=2)
    assert n == 2
    # "new" (v1) und "confirm" (v2) wurden publiziert
    versions = {d["ver"] for _, d in ft.sent}
    assert versions == {"1", "2"}
    states = {d["state"] for _, d in ft.sent}
    assert "confirmed" in states
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_pipeline.py -q`
Expected: `ImportError`

- [ ] **Step 3: Implementieren**

`src/tda_server/config.py`:
```python
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    signing_key_b64: str
    redis_url: str | None
    fcm_project_id: str | None
    fcm_credentials: str | None
    min_publish_mag: float

    @classmethod
    def from_env(cls) -> "Config":
        key = os.environ.get("TDA_SIGNING_KEY")
        if not key:
            raise SystemExit("TDA_SIGNING_KEY missing (run scripts/gen_signing_key.py)")
        return cls(
            signing_key_b64=key,
            redis_url=os.environ.get("TDA_REDIS_URL"),
            fcm_project_id=os.environ.get("TDA_FCM_PROJECT_ID"),
            fcm_credentials=os.environ.get("TDA_FCM_CREDENTIALS"),
            min_publish_mag=float(os.environ.get("TDA_MIN_PUBLISH_MAG", "4.0")),
        )
```

`src/tda_server/pipeline.py`:
```python
from __future__ import annotations

import asyncio
import logging
import time

from tda_server.adapters.afad import run_afad
from tda_server.adapters.emsc import run_emsc
from tda_server.adapters.usgs import run_usgs
from tda_server.alert.publisher import FakeTransport, FcmTransport, Publisher, Transport
from tda_server.config import Config
from tda_server.fusion.correlator import Correlator
from tda_server.stream.base import EventStream, InMemoryStream, deserialize_source_event
from tda_server.stream.redis_stream import RedisStream

log = logging.getLogger(__name__)


async def run_consumer(stream: EventStream, correlator: Correlator,
                       publisher: Publisher, *, start_id: str = "0",
                       stop_after: int | None = None) -> int:
    processed = 0
    async for _sid, record in stream.read(start_id):
        se = deserialize_source_event(record)
        tr = correlator.ingest(se)
        if tr is not None:
            await publisher.publish(tr, now_ms=int(time.time() * 1000))
        processed += 1
        if stop_after is not None and processed >= stop_after:
            break
    return processed


def build_transport(cfg: Config) -> Transport:
    if cfg.fcm_project_id and cfg.fcm_credentials:
        return FcmTransport(cfg.fcm_project_id, cfg.fcm_credentials)
    log.warning("no FCM config - using FakeTransport (dry run)")
    return FakeTransport()


async def main() -> None:
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s %(message)s")
    cfg = Config.from_env()
    stream: EventStream = (RedisStream(cfg.redis_url) if cfg.redis_url
                           else InMemoryStream())
    publisher = Publisher(build_transport(cfg), cfg.signing_key_b64,
                          min_mag=cfg.min_publish_mag)
    await asyncio.gather(
        run_emsc(stream),
        run_usgs(stream),
        run_afad(stream),
        run_consumer(stream, Correlator(), publisher, start_id="$"
                     if cfg.redis_url else "0"),
    )


if __name__ == "__main__":
    asyncio.run(main())
```

`src/tda_server/replay.py`:
```python
from __future__ import annotations

import asyncio
import json
import sys

from tda_server.alert.publisher import FakeTransport, Publisher
from tda_server.fusion.correlator import Correlator
from tda_server.pipeline import run_consumer
from tda_server.stream.base import InMemoryStream


async def main(path: str, signing_key_b64: str) -> None:
    stream = InMemoryStream()
    records = [json.loads(line) for line in open(path, encoding="utf-8")
               if line.strip()]
    for rec in records:
        await stream.append(rec)
    ft = FakeTransport()
    await run_consumer(stream, Correlator(), Publisher(ft, signing_key_b64),
                       stop_after=len(records))
    for topic, data in ft.sent:
        print(f'{topic} id={data["id"]} v={data["ver"]} state={data["state"]} '
              f'mag={data["mag"]}')
    print(f"records={len(records)} publishes={len(ft.sent)}")


if __name__ == "__main__":
    import os
    key = os.environ.get("TDA_SIGNING_KEY") or sys.exit("TDA_SIGNING_KEY missing")
    asyncio.run(main(sys.argv[1], key))
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest -q`
Expected: alle Tests grün (mindestens 25 passed).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: pipeline wiring, config, replay CLI"
```

---

### Task 12: Android-Minimal-Client — Projekt, Zellen, Countdown (reine Logik zuerst)

**Files:**
- Create: `tda/android/settings.gradle.kts`, `tda/android/build.gradle.kts`, `tda/android/gradle.properties`, `tda/android/app/build.gradle.kts`, `tda/android/app/src/main/AndroidManifest.xml`, `tda/android/app/src/main/java/dev/tda/alert/CellIds.kt`, `.../Alerts.kt`, `.../MainActivity.kt`
- Test: `tda/android/app/src/test/java/dev/tda/alert/CellIdsTest.kt`, `.../AlertsTest.kt`

**Interfaces:**
- Consumes: Zell-Formel und Testvektoren (Task 3), Payload-Felder (Task 5)
- Produces:
  - `CellIds.cellId(lat: Double, lon: Double): String` — identisch zur Python-Formel
  - `CellIds.topicsFor(lat: Double, lon: Double): List<String>` — eigene Zelle + 8 Nachbarn, Präfix `cell_`
  - `Alerts.sWaveEtaMs(originTs: Long, epiLat: Double, epiLon: Double, myLat: Double, myLon: Double): Long` — Ankunftszeit S-Welle (3,5 km/s)
  - `Alerts.distanceKm(...)`: Haversine

**Voraussetzung (manuell, dokumentieren im Commit):** Android Studio / SDK 34 installiert; JDK 17.

- [ ] **Step 1: Gradle-Projekt anlegen**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "tda-android"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2g
android.useAndroidX=true
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // id("com.google.gms.google-services")  // aktivieren, sobald google-services.json vorliegt (Task 13)
}

android {
    namespace = "dev.tda.alert"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.tda.alert"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:label="TDA" android:theme="@android:style/Theme.Material.Light">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".TdaMessagingService" android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 2: Failing Tests schreiben (reine Logik, JVM-Unit-Tests)**

`app/src/test/java/dev/tda/alert/CellIdsTest.kt`:
```kotlin
package dev.tda.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellIdsTest {
    @Test fun vectorsMatchServer() {
        // identische Vektoren wie server tests/test_cells.py
        assertEquals("c82_58", CellIds.cellId(41.0, 29.0))
        assertEquals("c81_57", CellIds.cellId(40.99, 28.99))
        assertEquals("c-2_-2", CellIds.cellId(-1.0, -1.0))
    }

    @Test fun topicsContainOwnCellAndNeighbors() {
        val topics = CellIds.topicsFor(41.0, 29.0)
        assertEquals(9, topics.size)
        assertTrue(topics.contains("cell_c82_58"))
        assertTrue(topics.all { it.startsWith("cell_c") })
    }
}
```

`app/src/test/java/dev/tda/alert/AlertsTest.kt`:
```kotlin
package dev.tda.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    @Test fun istanbulAnkaraDistance() {
        val d = Alerts.distanceKm(41.01, 28.98, 39.93, 32.86)
        assertTrue(d in 340.0..360.0)
    }

    @Test fun sWaveEtaIsOriginPlusTravel() {
        // 350 km / 3.5 km/s = 100 s
        val eta = Alerts.sWaveEtaMs(1_000_000L, 41.01, 28.98, 39.93, 32.86)
        val travel = eta - 1_000_000L
        assertTrue(travel in 97_000L..103_000L)
    }

    @Test fun zeroDistanceEtaEqualsOrigin() {
        assertEquals(1_000_000L, Alerts.sWaveEtaMs(1_000_000L, 41.0, 29.0, 41.0, 29.0))
    }
}
```

- [ ] **Step 3: Tests ausführen — müssen fehlschlagen**

Run: `cd tda/android && ./gradlew :app:testDebugUnitTest` (Windows: `gradlew.bat`; beim ersten Lauf Gradle-Wrapper erzeugen: `gradle wrapper --gradle-version 8.7`)
Expected: Compile-FEHLER (Klassen existieren nicht)

- [ ] **Step 4: Implementieren**

`app/src/main/java/dev/tda/alert/CellIds.kt`:
```kotlin
package dev.tda.alert

import kotlin.math.floor

object CellIds {
    private const val CELL_DEG = 0.5

    fun cellId(lat: Double, lon: Double): String =
        "c${floor(lat / CELL_DEG).toInt()}_${floor(lon / CELL_DEG).toInt()}"

    /** Own cell plus 8 neighbors - covers threshold radius around the place. */
    fun topicsFor(lat: Double, lon: Double): List<String> {
        val out = mutableListOf<String>()
        for (dLat in -1..1) for (dLon in -1..1) {
            out.add("cell_" + cellId(lat + dLat * CELL_DEG, lon + dLon * CELL_DEG))
        }
        return out.distinct()
    }
}
```

`app/src/main/java/dev/tda/alert/Alerts.kt`:
```kotlin
package dev.tda.alert

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Alerts {
    private const val EARTH_R_KM = 6371.0
    private const val S_WAVE_KM_PER_S = 3.5

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) +
                cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_R_KM * asin(sqrt(a))
    }

    fun sWaveEtaMs(originTs: Long, epiLat: Double, epiLon: Double,
                   myLat: Double, myLon: Double): Long {
        val travelMs = (distanceKm(epiLat, epiLon, myLat, myLon) /
                S_WAVE_KM_PER_S * 1000.0).toLong()
        return originTs + travelMs
    }
}
```

`app/src/main/java/dev/tda/alert/MainActivity.kt` (minimal, zeigt Konfigurationsort):
```kotlin
package dev.tda.alert

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.textSize = 18f
        tv.setPadding(48, 48, 48, 48)
        tv.text = "TDA minimal client\nPlace: Istanbul (41.01, 28.98)\n" +
                "Topics: " + CellIds.topicsFor(41.01, 28.98).joinToString("\n")
        setContentView(tv)
    }
}
```

- [ ] **Step 5: Tests ausführen — müssen bestehen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 5 Tests grün

- [ ] **Step 6: Commit**

```bash
cd tda && git add -A && git commit -m "feat(android): minimal client scaffold, cell/countdown logic with shared vectors"
```

---

### Task 13: Android-Empfang — FCM, Signaturprüfung, Notification + Testalarm-Skript

**Files:**
- Create: `tda/android/app/src/main/java/dev/tda/alert/TdaMessagingService.kt`, `.../PayloadVerifier.kt`, `tda/server/scripts/gen_test_vector.py`, `tda/server/scripts/send_test_alert.py`
- Modify: `tda/android/app/build.gradle.kts` (google-services aktivieren), `tda/android/app/src/main/java/dev/tda/alert/MainActivity.kt` (Topic-Subscribe)
- Test: `tda/android/app/src/test/java/dev/tda/alert/PayloadVerifierTest.kt`

**Interfaces:**
- Consumes: Payload-Format + Kanonisierung (Task 5: `key=value`, sortiert, `\n`, ohne `sig`), Topics (Task 12)
- Produces:
  - `PayloadVerifier.verify(data: Map<String, String>, publicKeyB64: String): Boolean`
  - `TdaMessagingService` — verifiziert, berechnet Countdown, zeigt High-Priority-Notification; TEST-Payloads (`test="1"`) werden mit Präfix „TEST" angezeigt
  - Server-Skript `send_test_alert.py` — publiziert signierten TEST-Alarm an eine Zelle via echtem FCM

**Manuelle Voraussetzung (einmalig, vom Menschen):** Firebase-Projekt anlegen (console.firebase.google.com), Android-App `dev.tda.alert` registrieren, `google-services.json` nach `tda/android/app/` legen, Service-Account-JSON (Rolle „Firebase Admin SDK") als Pfad in `TDA_FCM_CREDENTIALS`, Projekt-ID in `TDA_FCM_PROJECT_ID`. Ohne diese Dateien: Task pausieren und als Blocker melden.

- [ ] **Step 1: Testvektor auf dem Server erzeugen**

`server/scripts/gen_test_vector.py`:
```python
"""Emit a signed payload + public key as JSON for the Kotlin unit test."""
import base64
import json

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.alert.payload import canonical_bytes

priv = Ed25519PrivateKey.generate()
payload = {
    "v": "1", "id": "test:tv1", "ver": "1", "state": "detected", "tier": "P1",
    "test": "1", "origin_ts": "1755691200000", "lat": "40.7000",
    "lon": "29.1000", "depth_km": "10.0", "mag": "5.5", "mag_hi": "5.5",
    "src": "emsc", "issued_ts": "1755691205000",
}
sig = priv.sign(canonical_bytes(payload))
payload["sig"] = base64.b64encode(sig).decode()
pub = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
print(json.dumps({"publicKeyB64": base64.b64encode(pub).decode(),
                  "payload": payload}, indent=2))
```

Run: `cd tda/server && .venv/Scripts/python scripts/gen_test_vector.py > ../android/app/src/test/resources/test_vector.json`

- [ ] **Step 2: Failing Test schreiben**

`app/src/test/java/dev/tda/alert/PayloadVerifierTest.kt`:
```kotlin
package dev.tda.alert

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadVerifierTest {
    private fun vector(): Pair<String, MutableMap<String, String>> {
        val text = javaClass.classLoader!!.getResource("test_vector.json")!!.readText()
        val obj = JSONObject(text)
        val p = obj.getJSONObject("payload")
        val map = mutableMapOf<String, String>()
        p.keys().forEach { k -> map[k] = p.getString(k) }
        return obj.getString("publicKeyB64") to map
    }

    @Test fun validSignatureVerifies() {
        val (pub, payload) = vector()
        assertTrue(PayloadVerifier.verify(payload, pub))
    }

    @Test fun tamperedPayloadFails() {
        val (pub, payload) = vector()
        payload["mag"] = "9.9"
        assertFalse(PayloadVerifier.verify(payload, pub))
    }
}
```

Zusätzlich in `app/build.gradle.kts` unter `dependencies` ergänzen (JSON im JVM-Test):
```kotlin
    testImplementation("org.json:json:20240303")
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: Compile-FEHLER (`PayloadVerifier` fehlt)

- [ ] **Step 4: Implementieren**

`app/src/main/java/dev/tda/alert/PayloadVerifier.kt`:
```kotlin
package dev.tda.alert

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object PayloadVerifier {
    // In the JVM unit test android.util.Base64 is unavailable - fall back to java.util.
    private fun b64decode(s: String): ByteArray = try {
        Base64.decode(s, Base64.DEFAULT)
    } catch (e: RuntimeException) {
        java.util.Base64.getDecoder().decode(s)
    }

    fun canonicalBytes(data: Map<String, String>): ByteArray =
        data.filterKeys { it != "sig" }
            .toSortedMap()
            .map { (k, v) -> "$k=$v" }
            .joinToString("\n")
            .toByteArray(Charsets.UTF_8)

    fun verify(data: Map<String, String>, publicKeyB64: String): Boolean {
        val sigB64 = data["sig"] ?: return false
        return try {
            val pub = Ed25519PublicKeyParameters(b64decode(publicKeyB64), 0)
            val signer = Ed25519Signer()
            signer.init(false, pub)
            val msg = canonicalBytes(data)
            signer.update(msg, 0, msg.size)
            signer.verifySignature(b64decode(sigB64))
        } catch (e: Exception) {
            false
        }
    }
}
```

`app/src/main/java/dev/tda/alert/TdaMessagingService.kt`:
```kotlin
package dev.tda.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TdaMessagingService : FirebaseMessagingService() {
    companion object {
        const val CHANNEL_ID = "tda_alerts"
        // Public key of the server signing key; replace after gen_signing_key.py.
        const val PUBLIC_KEY_B64 = "REPLACE_WITH_TDA_PUBLIC_KEY"
        // Plan A: fixed demo place (Istanbul); Plan C makes this configurable.
        const val PLACE_LAT = 41.01
        const val PLACE_LON = 28.98
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val data = msg.data
        if (!PayloadVerifier.verify(data, PUBLIC_KEY_B64)) return

        val originTs = data["origin_ts"]?.toLongOrNull() ?: return
        val lat = data["lat"]?.toDoubleOrNull() ?: return
        val lon = data["lon"]?.toDoubleOrNull() ?: return
        val mag = data["mag"] ?: "?"
        val isTest = data["test"] == "1"

        val etaMs = Alerts.sWaveEtaMs(originTs, lat, lon, PLACE_LAT, PLACE_LON)
        val remainingS = (etaMs - System.currentTimeMillis()) / 1000
        val dist = Alerts.distanceKm(lat, lon, PLACE_LAT, PLACE_LON).toInt()

        val title = (if (isTest) "TEST — " else "") + "Deprem M$mag"
        val text = if (remainingS > 0) {
            "S-Welle in ~${remainingS}s · ${dist} km entfernt"
        } else {
            "Erschütterung bereits eingetroffen · ${dist} km entfernt"
        }
        notify(title, text, data["id"].hashCode())
    }

    private fun notify(title: String, text: String, id: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TDA Alerts",
                NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        nm.notify(id, n)
    }
}
```

Dazu in `app/build.gradle.kts`: `implementation("androidx.core:core-ktx:1.13.1")` ergänzen und die Zeile `// id("com.google.gms.google-services")` aktivieren (google-services.json muss vorliegen). In `MainActivity.onCreate` Topic-Subscribe ergänzen:
```kotlin
        com.google.firebase.messaging.FirebaseMessaging.getInstance().let { fm ->
            CellIds.topicsFor(41.01, 28.98).forEach { fm.subscribeToTopic(it) }
        }
```

`server/scripts/send_test_alert.py`:
```python
"""Send a signed TEST alert through real FCM to one cell topic.

Usage: TDA_SIGNING_KEY=... TDA_FCM_PROJECT_ID=... TDA_FCM_CREDENTIALS=...
       python scripts/send_test_alert.py [lat] [lon] [mag]
"""
import asyncio
import os
import sys
import time
from datetime import datetime, timezone

from tda_server.alert.publisher import FcmTransport, Publisher
from tda_server.domain.events import CanonicalEvent, SourceEvent
from tda_server.fusion.correlator import Transition


async def main() -> None:
    lat = float(sys.argv[1]) if len(sys.argv) > 1 else 41.01
    lon = float(sys.argv[2]) if len(sys.argv) > 2 else 28.98
    mag = float(sys.argv[3]) if len(sys.argv) > 3 else 5.5
    now = datetime.now(timezone.utc)
    se = SourceEvent(source="tdatest", source_event_id=f"t{int(time.time())}",
                     origin_time=now, lat=lat, lon=lon, depth_km=10.0,
                     magnitude=mag, mag_type="test", received_at=now)
    tr = Transition(CanonicalEvent.from_source(se), "new")
    transport = FcmTransport(os.environ["TDA_FCM_PROJECT_ID"],
                             os.environ["TDA_FCM_CREDENTIALS"])
    pub = Publisher(transport, os.environ["TDA_SIGNING_KEY"])
    n = await pub.publish(tr, test=True, now_ms=int(time.time() * 1000))
    print(f"TEST alert published to {n} topics")


if __name__ == "__main__":
    asyncio.run(main())
```

- [ ] **Step 5: Unit-Tests ausführen — müssen bestehen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: alle Tests grün (7)

- [ ] **Step 6: Ende-zu-Ende-Handprobe (Gerät/Emulator)**

1. `scripts/gen_signing_key.py` ausführen; `TDA_PUBLIC_KEY` in `TdaMessagingService.PUBLIC_KEY_B64` eintragen; `TDA_SIGNING_KEY` in die Umgebung.
2. App auf Gerät/Emulator installieren (`./gradlew :app:installDebug`), öffnen (Topics abonnieren), Benachrichtigungen erlauben.
3. `python scripts/send_test_alert.py 41.01 28.98 5.5` ausführen.
Expected: Binnen Sekunden erscheint die Notification `TEST — Deprem M5.5` mit Countdown-/Distanzzeile. Screenshot in `docs/` ablegen.

- [ ] **Step 7: Commit**

```bash
cd tda && git add -A && git commit -m "feat(android): FCM receive, signature verify, test alert e2e"
```

---

### Task 14: Live-Smoke-Test & Abschlussnachweis

**Files:**
- Create: `tda/server/docs/smoke-2026-08-XX.md` (Protokoll)

**Interfaces:**
- Consumes: gesamte Pipeline

- [ ] **Step 1: Pipeline im Dry-Run gegen Live-Feeds starten**

```bash
cd tda/server
export TDA_SIGNING_KEY=<aus gen_signing_key.py>
.venv/Scripts/python -m tda_server.pipeline
```
20–30 Minuten laufen lassen (weltweit passieren in dieser Zeit mehrere M≥4-Beben).

- [ ] **Step 2: Protokoll erstellen**

In `docs/smoke-2026-08-XX.md` festhalten (echte Logauszüge einfügen):
- Verbindungsaufbau aller drei Adapter (Logzeilen)
- Mindestens ein kanonisches Ereignis mit ≥2 Quellen (`confirm`-Transition im Log)
- `published ... to N cells`-Zeilen des Publishers (FakeTransport-Dry-Run)
- Auffälligkeiten (Parse-Fehler, Reconnects)

- [ ] **Step 3: Vollständige Testsuite als Abschlussnachweis**

Run: `.venv/Scripts/python -m pytest -q` und `cd ../android && ./gradlew :app:testDebugUnitTest`
Expected: alles grün. Ausgabe ins Protokoll kopieren.

- [ ] **Step 4: Commit**

```bash
cd tda && git add -A && git commit -m "docs: live smoke test protocol for plan A"
```

---

## Self-Review (durchgeführt)

1. **Spec-Abdeckung (Plan-A-Anteil):** P1/P2-Kataloge ✓ (Tasks 7–9), Zustandsmaschine mit escalate-fast ✓ (2, 4), Topic-Broadcast + Client-Regel-Grundlage ✓ (3, 10, 12), signierte autarke Payloads ✓ (5), Replay-Fundament ✓ (6, 11), TEST-Kennzeichnung ✓ (13), Countdown clientseitig ✓ (12–13). Bewusst NICHT in Plan A: P0a/P0b, Sequenz-Modus, Socket-Zweitkanal, De-Eskalation/RETRACTED-Fluss, Leader-Election, Kanarien (Pläne B–D).
2. **Platzhalter-Scan:** Ein bewusster Platzhalter bleibt: `PUBLIC_KEY_B64 = "REPLACE_WITH_TDA_PUBLIC_KEY"` — wird in Task 13 Step 6.1 mit dem echten Schlüssel gefüllt; kein TBD sonst.
3. **Typ-/Namenskonsistenz geprüft:** `Transition(event, kind)` einheitlich; `serialize_source_event`-Feldnamen zwischen Task 6 und 11 identisch; Zell-Testvektoren Python↔Kotlin identisch; Payload-Kanonisierung (`key=value`, sortiert, ohne `sig`) in Task 5 und `PayloadVerifier.canonicalBytes` identisch.
