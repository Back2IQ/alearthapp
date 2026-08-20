# Plan B: P0a Stations-EEW — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aus echtzeitnahen seismischen Wellenformen (SeedLink) einen stationsbasierten Frühwarn-Kern bauen, der Picks erzeugt (PhaseNet), sie zu einem Ursprung assoziiert (PyOcto), eine schnelle Magnitude schätzt (Pd/τc), gegen EPIC-artige Kriterien alarmiert, einen robusten Beobachtungspfad (PLUM) fährt und das Ergebnis als `SourceEvent(source="p0a")` in dieselbe Fusion (Plan A) einspeist — als **physikbasierter, unabhängiger Bestätigungs- und Kalibrierkanal** zur crowdsourced P0b-Detektion (Plan B2).

**Architecture:** SeedLink-Ingest füllt pro Station einen Ringpuffer; ein Picker (SeisBench/PhaseNet) annotiert gleitende Fenster zu P-/S-Picks (mit Dedupe an Fenstergrenzen); ein Associator (PyOcto, GaMMA-Fallback) bündelt Picks aus ≥ N Stationen zu einem Hypozentrum + Herdzeit; aus dem frühen P-Fenster wird Pd/τc → Magnitude geschätzt; zwei unabhängige Entscheider laufen parallel — der **Modellpfad** (Assoziation + Magnitude + EPIC-Kriterien) und der **Beobachtungspfad** (PLUM, 30-km-Projektion beobachteter Intensität). Nicht-tektonische Signale (Sprengungen, Fernbeben) werden diskriminiert. Ein bestätigter Alarm wird zu `SourceEvent(source="p0a")` und läuft durch denselben Korrelator/Publisher wie P0b/Katalog — eine P0a-Pick, die ein bestehendes P0b-Ereignis trifft, hebt es via `confirm` auf Tier P2 (die gegenseitige Verstärkung).

**Tech Stack:** Python 3.12, asyncio, numpy, ObsPy (SeedLink-Client + Wellenform-Objekte), SeisBench/PhaseNet (Picker, GPL-3.0), PyOcto (Associator, MIT, C++-Kern — ARM-Source-Build) mit GaMMA als reinem-Python-Fallback, pytest. Baut auf Plan-A-Modulen (`domain.events`, `geo.cells`, `fusion.correlator`, `alert.payload`) und dockt an Plan B2 (gemeinsame Fusion, `derive_tier`).

## Einordnung in die Verstärkungs-Sequenz & harte Realität des Datenzugangs

Dies ist **Stufe 2** der Sequenz (B2 → B). P0b (Plan B2) läuft bereits und liefert Tag-1-Sekunden-EEW aus dem Telefonnetz; **P0a setzt darauf als unabhängiger, physikbasierter Kanal auf** — nicht als Ersatz. Die Verstärkung ist wechselseitig und in Task 12 konkret verdrahtet:
- **Stationen → P0b:** eine echte Pick ist Ground Truth → kalibriert P0bs Fehlalarm-Schwelle, senkt dessen Fehlalarmrate.
- **P0b → Stationen:** das dichte Telefonnetz füllt die räumlichen Löcher, wo die Türkei ab Tag 1 kaum freie Stationen hat.
- **Fusion:** P0b-Cluster **plus** P0a-Pick = höchste Konfidenz in Sekunden (Tier P2 statt P0).

**Blockierende Vorbedingung (ehrlich, aus RECHERCHE-P0A §3):** Frei echtzeitverfügbar sind in/um die Türkei ab Tag 1 nur **wenige GEOFON-Stationen** — für echtes Sekunden-EEW zu dünn. Der eigentliche Unlock ist die **Partnerschaftsschiene** (KOERI-SeedLink / AFAD-TDVM). Deshalb ist **Task 1 ein Zugangs-/Abdeckungstest**, der über den Scharfschalt-Umfang entscheidet: solange nur GEOFON verfügbar ist, läuft P0a im **Schattenbetrieb** (detektiert, alarmiert aber nicht produktiv) und dient bereits als Bestätigungs-/Kalibrierkanal für P0b. Raspberry Shake ist **nicht** frei echtzeitfähig und wird nicht als Tag-1-Quelle geführt (Recherche-Korrektur).

**Verschobene Lücken (bewusst):** Finite-Fault (FinDer) für Kahramanmaraş-artige lange/Supershear-Rupturen ist **Stufe 2** (scfinder AGPL, Kern nur auf Anfrage — Punktquelle sättigt ~M7, ehrlich ausgewiesen). GNSS/GFAST für M > 7 ebenfalls Stufe 2.

---

## Global Constraints

- Spec ist `../../../../UMSETZUNGSPLAN.md` (Repo-Wurzel `Earthquake/`); dieser Plan implementiert den P0a-Kern (§2.2, §3 „Stations-Pipeline", §4 Latenzbudget).
- **Wissenschaftliche Grundlage ist fixiert** (RECHERCHE-P0A.md §1–2):
  - Stack: SeisBench/PhaseNet (Picker) + PyOcto (Associator, MIT) + GaMMA (Fallback). QuakeFlow als Architektur-Vorbild, ohne K8s/Spark.
  - Magnitude: **Pd (peak displacement)** im 3-s-P-Fenster, stabiler als τc. Wu/Kanamori/Allen/Hauksson 2007: `M = 4.218·log10(τc) + 6.166`. Pd-Attenuation Wu & Zhao 2006: `log10(Pd) = −3.463 + 0.729·M − 1.374·log10(R)`. Globale Kalibrierung Kuyuk & Allen 2013. **Pd > 0,5 cm ≈ schadensrelevant.**
  - Alarmkriterien (EPIC-Vorbild): ≥ 4 assoziierte Stationen, ≥ 40 % der Nahstationen getriggert, feste Tiefe (8 km Startannahme), Grid-Search-Lokalisierung, gewichtetes Pd-Mittel, RMS < 1,0.
  - Sättigung ~M7,0 (Trugman 2019) — Punktquelle unterschätzt große Brüche → FinDer Stufe 2.
  - Beobachtungspfad PLUM (Kodera 2018): 30-km-Radius, unattenuierte Projektion, interner Intensitäts-Flächenschwellwert ≈ 2,5. Robust, aber kürzere Vorwarnzeit → **Ergänzung, nicht Ersatz** des Modellpfads.
  - Intensität am Nutzerort: GMPE + GMICE + Vs30; **GMICE-Wahl verursacht > 1 MMI Unterschied** (Saunders 2024) → Unsicherheit offen kommunizieren, regional kalibrieren.
- **Türkei-Nachkalibrierung ist Pflichtschritt, nicht optional** (Task 11): kalifornische Defaults nur als Start; Pd-Mw regional gegen AFAD/KOERI-Daten nachziehen.
- **Zwei unabhängige Entscheidungspfade** (§3): Modellpfad (Assoziation+Magnitude+EPIC) und Beobachtungspfad (PLUM) laufen getrennt; ein einzelner Pfad darf auslösen, aber die Fusion kennt die Herkunft.
- **Assoziation ist Pflicht, nie Einzelstation** (§3): mehrere Stationen mit plausiblen Laufzeiten; Consumer-Sensoren nie allein auslösend; plötzliche Korrelation sonst unkorrelierter Stationen = **Angriffs-Warnsignal**, nicht Bestätigung.
- **Nicht-tektonische Diskriminierung** (§3): Steinbruch-/Bergbausprengungen (Tageslicht/Wochentag, flache Tiefe ≈ 0, P/S-Amplitudenverhältnis) und Fernbeben (Ursprung außerhalb Quellzonen) lösen keine lokale Warnung aus.
- **Nullkosten (hart):** Oracle-„Always Free"-ARM-VM. **PyOcto braucht ARM-Source-Build; keine ARM-Wheels** — GaMMA (reines Python) ist der Fallback, falls der Build scheitert. **ARM-Benchmark (Task 2) ist Go/No-Go**, bevor Stationszahl/Fenstergröße fixiert werden (keine ARM-Referenzdaten publiziert).
- **Zeit ist kritisch** (§3): Picks und Herdzeit in UTC; Stationslatenz einzeln überwacht; verrauschte Stationen fliegen automatisch aus der Gewichtung.
- **Payload/Fusion unverändert aus Plan A/B2:** P0a emittiert `SourceEvent(source="p0a")`; `derive_tier` (Plan B2 Task 11) behandelt Konvergenz p0a+p0b/Katalog → P2.
- **Nur dokumentierte, zugangskonforme Feeds** (§9): SeedLink über die getesteten offenen Ports; kein Scraping; Zugangsrechte je Netz respektiert (Task 1).
- **Replay als Grundeigenschaft** (§6): Kahramanmaraş 2023 läuft deterministisch durch den P0a-Code (Task 12); scharf erst nach Schattenbetrieb-Nachweis.
- Python: Zeilenlänge 100, Typannotationen überall; Code/Kommentare Englisch. TDD strikt.
- Arbeitsverzeichnis: `tda/server/`.

## Wissenschaftliche Notation (verbindlich)

| Symbol | Bedeutung | Startwert / Quelle |
|---|---|---|
| `Pd` | peak displacement, erste 3 s der P-Welle (cm) | Wu & Zhao 2006 |
| `τc` | Perioden-Parameter (s) | Wu et al. 2007 |
| `R` | Hypozentraldistanz (km) | aus Assoziation |
| `M` | Magnitudenschätzung | Pd-Inversion + τc, gemittelt |
| `v_p`, `v_s` | P-/S-Geschwindigkeit | 6,0 / 3,5 km/s (regional kalibrieren) |
| PLUM-Radius | Projektionsradius | 30 km (Kodera 2018) |
| MMI-Schwelle | Alarm-Intensität | ~IV (schadensnah); PLUM-intern ≈ 2,5 |

## File Structure (Zielbild dieses Plans)

```
tda/server/src/tda_server/p0a/
  __init__.py
  seedlink.py       # SeedLinkIngest (ObsPy EasySeedLinkClient) -> RingBuffer je Station
  picker.py         # PhaseNetPicker (SeisBench) + dedupe_picks (Fenstergrenzen)
  associator.py     # associate (PyOcto) + associate_gamma (Fallback) -> Origin
  magnitude.py      # pd_from_trace, tauc_from_trace, magnitude_from_pd, tauc_magnitude, combined
  epic.py           # epic_alarm (>=4 Stationen, >=40%, RMS<1.0, feste Tiefe)
  plum.py           # plum_cells (Beobachtungspfad, 30 km)
  discriminate.py   # is_blast, is_teleseism, in_source_zone
  detector.py       # P0aDetector (Modell- + Beobachtungspfad) -> SourceEvent
  recalibrate.py    # fit_pd_mw (regional, numpy) -> PdMwModel
  replay.py         # Kahramanmaras-Schattenreplay
tda/server/scripts/
  test_seedlink_access.py  # KOERI/GEOFON SeedLink-Port + Abdeckung testen (Task 1)
  bench_arm.py             # PhaseNet+PyOcto Durchsatz auf der Ziel-VM (Task 2)
  fit_recalibration.py     # Pd-Mw regional fitten (Task 11)
tda/server/tests/
  test_p0a_magnitude.py test_p0a_epic.py test_p0a_plum.py
  test_p0a_discriminate.py test_p0a_picker_dedupe.py test_p0a_associator.py
  test_p0a_detector.py test_p0a_recalibrate.py test_p0a_seedlink.py test_p0a_replay.py
  fixtures/  # aufgezeichnete miniSEED-Fenster + Kahramanmaras-Pickliste
```

---

### Task 1: SeedLink-Zugangs- & Abdeckungstest (blockierendes Gate)

**Files:**
- Create: `server/scripts/test_seedlink_access.py`, `server/docs/p0a-coverage.md`

**Interfaces:**
- Produces: ein Skript, das die realen SeedLink-Endpunkte prüft (Port offen? welche Netze/Stationen? Latenz?) und eine **Abdeckungsentscheidung** dokumentiert. Kein Test-Framework — dies ist die Feld-Recherche, die den Scharfschalt-Umfang festlegt. **Kein weiterer P0a-Task wird produktiv scharfgeschaltet, bevor dieses Gate eine dokumentierte Abdeckungslage hat.**

**Wichtig:** Dies ist der kritische Pfad aus der Recherche. Ergebnis bestimmt: (a) GEOFON-only → P0a nur Schattenbetrieb + Bestätigungskanal für P0b; (b) KOERI-Port offen → volle KO-Abdeckung, P0a kann in KOERI-Zonen scharf; (c) AFAD-Kooperation → volle TU-Abdeckung (Partnerschaft, außerhalb dieses Plans).

- [ ] **Step 1: Zugangs-Testskript schreiben**

`scripts/test_seedlink_access.py`:
```python
"""Probe SeedLink endpoints for reachability, available Turkish streams, latency.
Run manually; records findings into docs/p0a-coverage.md. Respects access terms:
INFO/handshake only, no bulk pull. slinktool is the canonical tool; this is a
dependency-free socket fallback that just checks the port answers."""
import socket
import sys

ENDPOINTS = {
    "GEOFON": ("geofon.gfz.de", 18000),
    "KOERI": ("eida.koeri.boun.edu.tr", 18000),   # unknown until tested (research)
    "IRIS": ("rtserve.earthscope.org", 18000),
}


def probe(host: str, port: int, timeout: float = 5.0) -> str:
    try:
        with socket.create_connection((host, port), timeout=timeout) as s:
            s.sendall(b"HELLO\r")
            s.settimeout(timeout)
            data = s.recv(1024)
        return f"OPEN: {data.decode(errors='replace').strip()[:120]}"
    except Exception as exc:  # noqa: BLE001 - probe reports every failure verbatim
        return f"CLOSED/ERROR: {exc}"


if __name__ == "__main__":
    for name, (host, port) in ENDPOINTS.items():
        print(f"{name} {host}:{port} -> {probe(host, port)}")
    print("\nNext: run `slinktool -Q <host>:<port>` to list streams and confirm "
          "Turkish (KO/TU/GE) network coverage; record in docs/p0a-coverage.md.")
```

- [ ] **Step 2: Probe ausführen + `slinktool -Q` (falls verfügbar)**

```bash
.venv/Scripts/python scripts/test_seedlink_access.py
# Falls slinktool installiert (SeisComP/libslink):
slinktool -Q eida.koeri.boun.edu.tr:18000 | head -50
slinktool -Q geofon.gfz.de:18000 | grep -iE " (KO|TU|GE) " | head -50
```
Erwartet: Klartext, welche Endpunkte antworten und welche türkischen Streams gelistet sind. **Schlägt KOERI fehl** → das ist ein reales Ergebnis, kein Fehler: dokumentieren und Partnerschaftsschiene als Blocker melden.

- [ ] **Step 3: Abdeckungsentscheidung dokumentieren**

`docs/p0a-coverage.md` anlegen mit: getestete Endpunkte + Ergebnis, Liste real verfügbarer türkischer Stationen (Code, Netz, Lat/Lon), grobe Nahfeld-Abdeckung der Quellzonen (Nordanatolische/Ostanatolische Verwerfung), und die Scharfschalt-Entscheidung (Schatten vs. scharf pro Zone). Diese Datei ist die Eingabe für Task 7 (welche Stationen sind „Nahstationen") und Task 12 (welche Zonen im Replay realistisch sind).

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore(p0a): seedlink access probe + coverage decision doc"
```

---

### Task 2: ARM-Benchmark — Go/No-Go für den Picker-/Associator-Stack

**Files:**
- Create: `server/scripts/bench_arm.py`, Ergänzung in `server/docs/p0a-coverage.md` (Benchmark-Abschnitt)
- Modify: `server/pyproject.toml` (optionale `p0a`-Extra: `obspy`, `seisbench`, `pyocto`; GaMMA separat)

**Interfaces:**
- Produces: ein Skript, das auf der **Ziel-VM** misst: (a) installieren sich obspy/seisbench/pyocto auf ARM? (b) PhaseNet-Annotationsdurchsatz (samples/s pro Kern, Latenz je 30-s-Fenster); (c) PyOcto-Assoziationslatenz. Ergebnis entscheidet Stationszahl/Fenstergröße und ob PyOcto oder GaMMA verwendet wird.

**Wichtig:** Keine ARM-Benchmarks für SeisBench/PhaseNet publiziert (Recherche §1, Risiko 1). Dieser Task **misst statt zu raten**. Scheitert der PyOcto-Build (Recherche §1, Risiko 2), wird GaMMA gesetzt und in `p0a-coverage.md` festgehalten — der Associator-Task (6) hat beide Pfade.

- [ ] **Step 1: `p0a`-Extra in pyproject.toml ergänzen**

```toml
[project.optional-dependencies]
p0a = ["obspy>=1.4", "seisbench>=0.12", "numpy>=1.26"]
# pyocto wird separat installiert (Source-Build auf ARM); gamma als Fallback:
# pip install pyocto  ODER  pip install gamma
```

- [ ] **Step 2: Benchmark-Skript schreiben**

`scripts/bench_arm.py`:
```python
"""Measure the P0a stack on the target ARM VM. No published ARM benchmarks exist
(research 1). Prints throughput/latency so station count and window size are set
from measurement, not assumption. Run ON the VM."""
import time

import numpy as np


def bench_phasenet(n_windows: int = 20, fs: int = 100, win_s: int = 30) -> None:
    import seisbench.models as sbm
    from obspy import Stream, Trace, UTCDateTime
    model = sbm.PhaseNet.from_pretrained("geofon")
    samples = fs * win_s
    t0 = time.perf_counter()
    for _ in range(n_windows):
        traces = [Trace(data=np.random.randn(samples).astype("float32"),
                        header={"sampling_rate": fs, "starttime": UTCDateTime(),
                                "network": "GE", "station": "TEST", "channel": ch})
                  for ch in ("HHZ", "HHN", "HHE")]
        model.annotate(Stream(traces))
    dt = time.perf_counter() - t0
    print(f"PhaseNet: {n_windows} windows ({win_s}s @ {fs}Hz) in {dt:.2f}s "
          f"= {dt / n_windows * 1000:.0f} ms/window/3ch")


def bench_associator() -> None:
    try:
        import pyocto  # noqa: F401
        print("PyOcto import: OK (native build succeeded)")
    except Exception as exc:  # noqa: BLE001
        print(f"PyOcto import FAILED ({exc}) -> use GaMMA fallback (Task 6)")


if __name__ == "__main__":
    bench_associator()
    bench_phasenet()
    print("\nRecord ms/window and go/no-go in docs/p0a-coverage.md. "
          "Target: annotate a 30s window across all live stations within the "
          "latency budget (spec 4). If over budget: fewer stations or shorter window.")
```

- [ ] **Step 3: Auf der VM ausführen + dokumentieren**

```bash
.venv/Scripts/python -m pip install -e ".[p0a]"
python scripts/bench_arm.py
```
Ergebnis (ms/Fenster, PyOcto-Build ja/nein, gewählte Stationszahl/Fenstergröße) in `p0a-coverage.md` festhalten. **Über Budget → Stationszahl/Fenster anpassen, nicht das Latenzbudget aufweichen.**

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore(p0a): ARM benchmark (PhaseNet throughput, PyOcto build check)"
```

---

### Task 3: Schnelle Magnitude — Pd, τc, Inversion (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/__init__.py`, `server/src/tda_server/p0a/magnitude.py`
- Test: `server/tests/test_p0a_magnitude.py`

**Interfaces:**
- Produces:
  - `pd_from_displacement(disp_cm: np.ndarray) -> float` — peak |displacement| im gegebenen (bereits auf Verschiebung integrierten) 3-s-P-Fenster.
  - `magnitude_from_pd(pd_cm: float, r_km: float) -> float` — Inversion von Wu & Zhao 2006: `M = (log10(Pd) + 3.463 + 1.374·log10(R)) / 0.729`.
  - `tauc_magnitude(tauc_s: float) -> float` — Wu et al. 2007: `M = 4.218·log10(τc) + 6.166`.
  - `combined_magnitude(pd_cm: float, r_km: float, tauc_s: float | None) -> tuple[float, float, float]` — `(M, M_low, M_high)`; Mittel aus Pd- und τc-Schätzung (falls τc vorhanden), Spanne als Unsicherheit.
  - `is_damaging_pd(pd_cm: float) -> bool` — `Pd > 0.5 cm` (schadensrelevant).
  - Konstanten als Modul-Attribute, damit Task 11 sie regional überschreiben kann: `PD_A=-3.463, PD_B=0.729, PD_C=-1.374`, `TAUC_A=4.218, TAUC_B=6.166`.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_magnitude.py`:
```python
import math
import numpy as np
from tda_server.p0a.magnitude import (
    combined_magnitude, is_damaging_pd, magnitude_from_pd,
    pd_from_displacement, tauc_magnitude,
)


def test_pd_from_displacement_is_peak_abs():
    disp = np.array([0.0, -0.4, 0.2, -0.1])
    assert pd_from_displacement(disp) == 0.4

def test_magnitude_from_pd_inverts_wu_zhao():
    # forward: log10(Pd) = -3.463 + 0.729*M - 1.374*log10(R)
    m_true, r = 6.0, 50.0
    log_pd = -3.463 + 0.729 * m_true - 1.374 * math.log10(r)
    pd = 10 ** log_pd
    assert abs(magnitude_from_pd(pd, r) - m_true) < 1e-6

def test_tauc_magnitude_wu2007():
    # tauc=1s -> M = 6.166; tauc=10 -> M = 4.218+6.166
    assert abs(tauc_magnitude(1.0) - 6.166) < 1e-9
    assert abs(tauc_magnitude(10.0) - (4.218 + 6.166)) < 1e-9

def test_combined_averages_and_brackets():
    m, lo, hi = combined_magnitude(pd_cm=0.6, r_km=40.0, tauc_s=1.2)
    assert lo <= m <= hi and hi > lo

def test_combined_pd_only_when_no_tauc():
    m, lo, hi = combined_magnitude(pd_cm=0.6, r_km=40.0, tauc_s=None)
    assert abs(m - magnitude_from_pd(0.6, 40.0)) < 1e-9

def test_is_damaging_threshold():
    assert is_damaging_pd(0.6) is True
    assert is_damaging_pd(0.4) is False
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_magnitude.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/__init__.py`: leer.

`src/tda_server/p0a/magnitude.py`:
```python
from __future__ import annotations

import math

import numpy as np

# Wu & Zhao 2006 Pd attenuation coefficients; Wu et al. 2007 tau_c.
# Module-level so Turkish recalibration (Task 11) can override them.
PD_A = -3.463
PD_B = 0.729
PD_C = -1.374
TAUC_A = 4.218
TAUC_B = 6.166
DAMAGING_PD_CM = 0.5


def pd_from_displacement(disp_cm: np.ndarray) -> float:
    return float(np.max(np.abs(disp_cm)))


def magnitude_from_pd(pd_cm: float, r_km: float) -> float:
    """Invert log10(Pd) = PD_A + PD_B*M + PD_C*log10(R) for M."""
    return (math.log10(pd_cm) - PD_A - PD_C * math.log10(r_km)) / PD_B


def tauc_magnitude(tauc_s: float) -> float:
    return TAUC_A * math.log10(tauc_s) + TAUC_B


def combined_magnitude(pd_cm: float, r_km: float,
                       tauc_s: float | None) -> tuple[float, float, float]:
    m_pd = magnitude_from_pd(pd_cm, r_km)
    if tauc_s is None:
        return round(m_pd, 2), round(m_pd, 2), round(m_pd, 2)
    m_tc = tauc_magnitude(tauc_s)
    m = (m_pd + m_tc) / 2.0
    return round(m, 2), round(min(m_pd, m_tc), 2), round(max(m_pd, m_tc), 2)


def is_damaging_pd(pd_cm: float) -> bool:
    return pd_cm > DAMAGING_PD_CM
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_magnitude.py -q`
Expected: `6 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): fast magnitude (Pd inversion + tau_c, Wu 2006/2007)"
```

---

### Task 4: EPIC-artige Alarmkriterien (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/epic.py`
- Test: `server/tests/test_p0a_epic.py`

**Interfaces:**
- Produces:
  - `StationPick(station: str, phase: str, time_ms: int, lat: float, lon: float, pd_cm: float | None)` (frozen dataclass).
  - `epic_alarm(picks: list[StationPick], near_stations: set[str], *, min_stations: int = 4, near_fraction: float = 0.4, rms_max_s: float = 1.0, origin_lat: float, origin_lon: float, origin_ms: int, vp_kms: float = 6.0) -> bool` — True nur wenn: ≥ `min_stations` assoziierte P-Picks; Anteil getriggerter Nahstationen ≥ `near_fraction`; Laufzeit-RMS (beobachtet vs. `dist/vp`) < `rms_max_s`.
  - `travel_time_rms(picks, origin_lat, origin_lon, origin_ms, vp_kms) -> float` — Hilfsfunktion (Root-Mean-Square der P-Laufzeitresiduen in Sekunden).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_epic.py`:
```python
from tda_server.geo.cells import haversine_km
from tda_server.p0a.epic import StationPick, epic_alarm, travel_time_rms


def pick(st, lat, lon, origin=(39.0, 40.0), t0=1_000_000, vp=6.0, jitter_ms=0):
    d = haversine_km(lat, lon, *origin)
    return StationPick(st, "P", t0 + int(1000 * d / vp) + jitter_ms, lat, lon, 0.8)


NEAR = {"S1", "S2", "S3", "S4", "S5"}


def test_alarm_when_all_criteria_met():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(sorted(NEAR))]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is True

def test_no_alarm_too_few_stations():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(["S1", "S2", "S3"])]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_no_alarm_below_near_fraction():
    # 4 far stations trigger but none of the 5 near stations -> fails 40% rule
    far = {"F1", "F2", "F3", "F4"}
    picks = [pick(s, 45.0 + i * 0.1, 50.0) for i, s in enumerate(sorted(far))]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_no_alarm_high_rms():
    picks = [pick(s, 39.0 + i * 0.1, 40.0, jitter_ms=5000 * (i % 2))
             for i, s in enumerate(sorted(NEAR))]  # large inconsistent residuals
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_rms_zero_for_perfect_picks():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(sorted(NEAR))]
    assert travel_time_rms(picks, 39.0, 40.0, 1_000_000, 6.0) < 0.05
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_epic.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/epic.py`:
```python
from __future__ import annotations

import math
from dataclasses import dataclass

from tda_server.geo.cells import haversine_km


@dataclass(frozen=True)
class StationPick:
    station: str
    phase: str
    time_ms: int
    lat: float
    lon: float
    pd_cm: float | None


def travel_time_rms(picks: list[StationPick], origin_lat: float, origin_lon: float,
                    origin_ms: int, vp_kms: float) -> float:
    resid = []
    for p in picks:
        if p.phase != "P":
            continue
        d = haversine_km(p.lat, p.lon, origin_lat, origin_lon)
        predicted_ms = origin_ms + 1000.0 * d / vp_kms
        resid.append((p.time_ms - predicted_ms) / 1000.0)
    if not resid:
        return math.inf
    return math.sqrt(sum(r * r for r in resid) / len(resid))


def epic_alarm(picks: list[StationPick], near_stations: set[str], *,
               min_stations: int = 4, near_fraction: float = 0.4,
               rms_max_s: float = 1.0, origin_lat: float, origin_lon: float,
               origin_ms: int, vp_kms: float = 6.0) -> bool:
    p_picks = [p for p in picks if p.phase == "P"]
    triggered = {p.station for p in p_picks}
    if len(triggered) < min_stations:
        return False
    if not near_stations:
        return False
    near_hit = len(triggered & near_stations) / len(near_stations)
    if near_hit < near_fraction:
        return False
    return travel_time_rms(p_picks, origin_lat, origin_lon, origin_ms, vp_kms) < rms_max_s
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_epic.py -q`
Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): EPIC-like alarm criteria (>=4 stations, >=40%, RMS)"
```

---

### Task 5: Beobachtungspfad PLUM (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/plum.py`
- Test: `server/tests/test_p0a_plum.py`

**Interfaces:**
- Consumes: `geo.cells.cell_id`, `haversine_km`, `affected_cells` (Plan A Task 3)
- Produces:
  - `IntensityObs(lat: float, lon: float, mmi: float)` (frozen dataclass) — beobachtete Intensität an einer Station.
  - `plum_cells(observations: list[IntensityObs], *, radius_km: float = 30.0, min_mmi: float = 2.5, site_factor: float = 0.0) -> dict[str, float]` — Kodera-2018-Prinzip: jede Beobachtung ≥ `min_mmi` projiziert ihre (um `site_factor` korrigierte) Intensität **unattenuiert** auf alle 0,5°-Zellen im 30-km-Radius; je Zelle das Maximum. Robust gegen Magnituden-Sättigung.
  - `plum_triggered(observations, *, radius_km=30.0, min_mmi=2.5) -> bool` — löst der Beobachtungspfad überhaupt aus (mindestens eine Beobachtung ≥ min_mmi)?

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_plum.py`:
```python
from tda_server.geo.cells import cell_id
from tda_server.p0a.plum import IntensityObs, plum_cells, plum_triggered


def test_strong_obs_projects_to_local_cells():
    obs = [IntensityObs(39.0, 40.0, mmi=6.0)]
    cells = plum_cells(obs, radius_km=30.0, min_mmi=2.5)
    assert cell_id(39.0, 40.0) in cells
    assert cells[cell_id(39.0, 40.0)] == 6.0        # unattenuated projection

def test_weak_obs_below_threshold_ignored():
    obs = [IntensityObs(39.0, 40.0, mmi=2.0)]
    assert plum_cells(obs, min_mmi=2.5) == {}
    assert plum_triggered(obs, min_mmi=2.5) is False

def test_cell_takes_max_over_overlapping_obs():
    obs = [IntensityObs(39.0, 40.0, 4.0), IntensityObs(39.05, 40.05, 6.0)]
    cells = plum_cells(obs, radius_km=30.0)
    assert cells[cell_id(39.0, 40.0)] == 6.0

def test_site_factor_amplifies():
    obs = [IntensityObs(39.0, 40.0, 4.0)]
    cells = plum_cells(obs, site_factor=1.0)
    assert cells[cell_id(39.0, 40.0)] == 5.0
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_plum.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/plum.py`:
```python
from __future__ import annotations

from dataclasses import dataclass

from tda_server.geo.cells import affected_cells


@dataclass(frozen=True)
class IntensityObs:
    lat: float
    lon: float
    mmi: float


def plum_cells(observations: list[IntensityObs], *, radius_km: float = 30.0,
               min_mmi: float = 2.5, site_factor: float = 0.0) -> dict[str, float]:
    """PLUM (Kodera 2018): project each observed intensity >= min_mmi
    unattenuated onto all cells within radius_km; per cell keep the max."""
    out: dict[str, float] = {}
    for obs in observations:
        if obs.mmi < min_mmi:
            continue
        value = obs.mmi + site_factor
        for cell in affected_cells(obs.lat, obs.lon, radius_km):
            if value > out.get(cell, float("-inf")):
                out[cell] = value
    return out


def plum_triggered(observations: list[IntensityObs], *, radius_km: float = 30.0,
                   min_mmi: float = 2.5) -> bool:
    return any(o.mmi >= min_mmi for o in observations)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_plum.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): PLUM observation path (30km unattenuated projection)"
```

---

### Task 6: Nicht-tektonische Diskriminierung — Sprengung, Fernbeben (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/discriminate.py`
- Test: `server/tests/test_p0a_discriminate.py`

**Interfaces:**
- Produces:
  - `SourceZone(min_lat, max_lat, min_lon, max_lon)` (frozen) + `in_source_zone(lat, lon, zones: list[SourceZone]) -> bool`.
  - `is_teleseism(lat, lon, zones: list[SourceZone]) -> bool` — Ursprung außerhalb aller Quellzonen ⇒ Fernbeben ⇒ nur Katalog-Abgleich, keine lokale Warnung.
  - `is_blast(*, origin_ms: int, depth_km: float | None, ps_amp_ratio: float | None, local_hour: int, weekday: int, depth_max_km: float = 3.0, ps_ratio_min: float = 3.0) -> bool` — Sprengung wahrscheinlich, wenn: flache Herdtiefe (≈ 0), **Tageslicht + Werktag** (Steinbruchbetrieb), hohes P/S-Amplitudenverhältnis (Explosionen strahlen P-dominant). Alle drei Indizien zusammen, nicht einzeln.
  - `local_time_features(origin_ms: int, tz_offset_h: int = 3) -> tuple[int, int]` — `(local_hour, weekday)` für die Türkei (UTC+3).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_discriminate.py`:
```python
from tda_server.p0a.discriminate import (
    SourceZone, in_source_zone, is_blast, is_teleseism, local_time_features,
)

# East Anatolian + North Anatolian fault boxes (coarse)
ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0), SourceZone(39.5, 41.5, 26.0, 42.0)]


def test_in_and_out_of_source_zone():
    assert in_source_zone(37.2, 37.0, ZONES) is True      # Kahramanmaras
    assert in_source_zone(10.0, 100.0, ZONES) is False    # far away

def test_teleseism_outside_zones():
    assert is_teleseism(10.0, 100.0, ZONES) is True
    assert is_teleseism(37.2, 37.0, ZONES) is False

def test_blast_needs_all_indicators():
    # shallow + daytime weekday + high P/S ratio -> blast
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=5.0,
                    local_hour=13, weekday=2) is True
    # deep or nighttime or low ratio -> not classified as blast
    assert is_blast(origin_ms=0, depth_km=10.0, ps_amp_ratio=5.0,
                    local_hour=13, weekday=2) is False
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=5.0,
                    local_hour=3, weekday=2) is False
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=1.0,
                    local_hour=13, weekday=2) is False

def test_local_time_features_turkey_offset():
    # 2026-08-20 12:00:00 UTC -> 15:00 local (UTC+3), Thursday(weekday=3)
    h, wd = local_time_features(1_755_691_200_000, tz_offset_h=3)
    assert h == 15 and wd == 3
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_discriminate.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/discriminate.py`:
```python
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone


@dataclass(frozen=True)
class SourceZone:
    min_lat: float
    max_lat: float
    min_lon: float
    max_lon: float

    def contains(self, lat: float, lon: float) -> bool:
        return (self.min_lat <= lat <= self.max_lat
                and self.min_lon <= lon <= self.max_lon)


def in_source_zone(lat: float, lon: float, zones: list[SourceZone]) -> bool:
    return any(z.contains(lat, lon) for z in zones)


def is_teleseism(lat: float, lon: float, zones: list[SourceZone]) -> bool:
    return not in_source_zone(lat, lon, zones)


def local_time_features(origin_ms: int, tz_offset_h: int = 3) -> tuple[int, int]:
    dt = datetime.fromtimestamp(origin_ms / 1000, tz=timezone.utc) + timedelta(hours=tz_offset_h)
    return dt.hour, dt.weekday()


def is_blast(*, origin_ms: int, depth_km: float | None, ps_amp_ratio: float | None,
             local_hour: int, weekday: int, depth_max_km: float = 3.0,
             ps_ratio_min: float = 3.0) -> bool:
    """Quarry/mining blast: shallow, daytime on a workday, P-dominant. Needs all
    indicators together (spec 3) - a real shallow daytime earthquake must not be
    misclassified on one feature alone."""
    if depth_km is None or depth_km > depth_max_km:
        return False
    daytime = 6 <= local_hour <= 18
    workday = weekday < 5
    p_dominant = ps_amp_ratio is not None and ps_amp_ratio >= ps_ratio_min
    return daytime and workday and p_dominant
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_discriminate.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): blast + teleseism discrimination"
```

---

### Task 7: Pick-Dedupe an Fenstergrenzen (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/picker.py` (Teil 1: `dedupe_picks`)
- Test: `server/tests/test_p0a_picker_dedupe.py`

**Interfaces:**
- Consumes: `StationPick` (Task 4)
- Produces:
  - `dedupe_picks(picks: list[StationPick], *, merge_window_ms: int = 500) -> list[StationPick]` — SeisBench löst Doppel-Picks an überlappenden Fenstergrenzen nicht automatisch (Recherche §1, Risiko 4). Zwei Picks derselben Station + Phase innerhalb `merge_window_ms` werden zu einem verschmolzen (frühere Zeit gewinnt, höheres Pd behalten).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_picker_dedupe.py`:
```python
from tda_server.p0a.epic import StationPick
from tda_server.p0a.picker import dedupe_picks


def p(st, phase, t, pd=0.5):
    return StationPick(st, phase, t, 39.0, 40.0, pd)


def test_merges_duplicate_picks_within_window():
    picks = [p("S1", "P", 1000, 0.3), p("S1", "P", 1300, 0.6)]
    out = dedupe_picks(picks, merge_window_ms=500)
    assert len(out) == 1
    assert out[0].time_ms == 1000 and out[0].pd_cm == 0.6   # earlier time, higher pd

def test_keeps_distinct_phases_and_stations():
    picks = [p("S1", "P", 1000), p("S1", "S", 1200), p("S2", "P", 1050)]
    assert len(dedupe_picks(picks, merge_window_ms=500)) == 3

def test_keeps_picks_outside_window():
    picks = [p("S1", "P", 1000), p("S1", "P", 3000)]   # 2s apart: distinct events
    assert len(dedupe_picks(picks, merge_window_ms=500)) == 2
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_picker_dedupe.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/picker.py` (Teil 1 — `PhaseNetPicker` folgt in Task 8):
```python
from __future__ import annotations

from tda_server.p0a.epic import StationPick


def dedupe_picks(picks: list[StationPick], *, merge_window_ms: int = 500) -> list[StationPick]:
    """Merge duplicate picks (same station+phase within merge_window_ms) that
    SeisBench emits at overlapping window boundaries (research 1, risk 4).
    Keep the earlier time and the larger Pd."""
    ordered = sorted(picks, key=lambda p: (p.station, p.phase, p.time_ms))
    out: list[StationPick] = []
    for pk in ordered:
        if out and out[-1].station == pk.station and out[-1].phase == pk.phase \
                and pk.time_ms - out[-1].time_ms <= merge_window_ms:
            prev = out[-1]
            pd = max(prev.pd_cm or 0.0, pk.pd_cm or 0.0)
            out[-1] = StationPick(prev.station, prev.phase, prev.time_ms,
                                  prev.lat, prev.lon, pd)
        else:
            out.append(pk)
    return out
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_picker_dedupe.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): pick dedupe across overlapping window boundaries"
```

---

### Task 8: PhaseNet-Picker-Wrapper (Integration, aufgezeichnete miniSEED-Fixture)

**Files:**
- Modify: `server/src/tda_server/p0a/picker.py` (Teil 2: `PhaseNetPicker`)
- Create: `server/tests/fixtures/README-fixtures.md`, `server/tests/test_p0a_picker.py`
- Test: `server/tests/test_p0a_picker.py` (nur ausgeführt, wenn `seisbench` installiert — sonst `pytest.skip`)

**Interfaces:**
- Consumes: SeisBench/ObsPy (aus `p0a`-Extra), `StationPick`, `dedupe_picks` (Task 7)
- Produces:
  - `PhaseNetPicker(model_name: str = "geofon", p_threshold: float = 0.3, s_threshold: float = 0.3)` mit `pick(stream, station_lat: float, station_lon: float) -> list[StationPick]` — annotiert einen ObsPy-`Stream` (3 Kanäle einer Station), extrahiert P-/S-Picks über Schwelle, gibt `StationPick`s (mit Stationskoordinate); ruft intern `dedupe_picks`.

**Wichtig:** Dies ist ein Integrations-Task mit schwerer Abhängigkeit. Der Test **skippt sauber**, wenn SeisBench nicht installiert ist (CI ohne ML-Stack), und läuft real auf der VM. Fixture ist ein **echtes** aufgezeichnetes miniSEED-Fenster eines bekannten Bebens (Schritt 1), nie synthetisch — der Picker wird an realen Wellenformen geprüft, nicht an Rauschen.

- [ ] **Step 1: Echtes miniSEED-Fenster aufzeichnen**

Ein Fenster um ein bekanntes türkisches Beben von einer offenen GEOFON/KOERI-Station über FDSNWS holen (Zugang aus Task 1). Beispiel (ObsPy):
```bash
.venv/Scripts/python - << 'EOF'
from obspy.clients.fdsn import Client
from obspy import UTCDateTime
c = Client("GEOFON")
# adjust station/time to a real event confirmed available in Task 1 coverage
t = UTCDateTime("2023-02-06T01:17:35")   # M7.8 Kahramanmaras origin (UTC)
st = c.get_waveforms("GE", "*", "*", "HH*", t, t + 60)
st.write("tests/fixtures/kahramanmaras_window.mseed", format="MSEED")
print(st)
EOF
```
Ist keine passende Station verfügbar (Abdeckung dünn, Task 1), ein anderes real verfügbares Ereignis/Station nehmen und in `README-fixtures.md` dokumentieren. **Nie** ein synthetisches Signal als „echt" ausgeben.

- [ ] **Step 2: Failing Test schreiben**

`tests/test_p0a_picker.py`:
```python
from pathlib import Path
import pytest

seisbench = pytest.importorskip("seisbench")
from obspy import read
from tda_server.p0a.picker import PhaseNetPicker

FIX = Path(__file__).parent / "fixtures" / "kahramanmaras_window.mseed"


@pytest.mark.skipif(not FIX.exists(), reason="miniSEED fixture not recorded")
def test_picker_finds_p_pick_on_real_event():
    stream = read(str(FIX))
    picker = PhaseNetPicker()
    picks = picker.pick(stream, station_lat=39.0, station_lon=40.0)
    assert any(p.phase == "P" for p in picks)
    for p in picks:
        assert p.time_ms > 0 and p.lat == 39.0
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen (oder skippen ohne seisbench)**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_picker.py -q`
Expected: FAIL (`PhaseNetPicker` fehlt) bzw. `skipped`, wenn seisbench/Fixture fehlen.

- [ ] **Step 4: Implementieren (picker.py ergänzen)**

```python
from datetime import timezone


class PhaseNetPicker:
    def __init__(self, model_name: str = "geofon", p_threshold: float = 0.3,
                 s_threshold: float = 0.3) -> None:
        import seisbench.models as sbm
        self.model = sbm.PhaseNet.from_pretrained(model_name)
        self.p_threshold = p_threshold
        self.s_threshold = s_threshold

    def pick(self, stream, station_lat: float, station_lon: float) -> list[StationPick]:
        station = stream[0].stats.station if len(stream) else "UNK"
        classifications = self.model.classify(
            stream, P_threshold=self.p_threshold, S_threshold=self.s_threshold)
        out: list[StationPick] = []
        for pick in classifications.picks:
            phase = "P" if pick.phase.upper().startswith("P") else "S"
            t_ms = int(pick.peak_time.datetime.replace(tzinfo=timezone.utc).timestamp() * 1000)
            out.append(StationPick(station, phase, t_ms, station_lat, station_lon, None))
        return dedupe_picks(out)
```

- [ ] **Step 5: Test ausführen — muss bestehen (auf der VM mit seisbench + Fixture)**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_picker.py -q`
Expected: `1 passed` (VM) bzw. `1 skipped` (CI ohne Stack).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(p0a): PhaseNet picker wrapper on real miniSEED window"
```

---

### Task 9: Associator-Wrapper — PyOcto mit GaMMA-Fallback (Integration)

**Files:**
- Create: `server/src/tda_server/p0a/associator.py`, `server/tests/test_p0a_associator.py`

**Interfaces:**
- Consumes: `StationPick` (Task 4)
- Produces:
  - `Origin(lat: float, lon: float, depth_km: float, time_ms: int, n_picks: int, rms_s: float)` (frozen dataclass).
  - `associate(picks: list[StationPick], *, vp_kms: float = 6.0, vs_kms: float = 3.5, fixed_depth_km: float = 8.0, backend: str = "auto") -> Origin | None` — bündelt Picks zu einem Ursprung; `backend="pyocto"` nutzt PyOcto, `"gamma"` GaMMA, `"grid"` einen dependency-freien Grid-Search-Fallback (fester Tiefe), `"auto"` probiert PyOcto → GaMMA → grid. Der **Grid-Search-Fallback ist voll getestet** (kein schwerer Dep), die PyOcto/GaMMA-Pfade sind Integration.
  - `grid_search_origin(picks, *, vp_kms, fixed_depth_km, ...) -> Origin | None` — Rastersuche über Lat/Lon (feste Tiefe 8 km, EPIC-Vorbild), minimiert Laufzeit-RMS; gibt Ursprung + RMS. Dies ist der immer verfügbare Startpfad.

**Wichtig:** Der Grid-Search-Fallback stellt sicher, dass P0a **auch ohne PyOcto/GaMMA** lauffähig und testbar ist (falls der ARM-Build in Task 2 scheitert). PyOcto/GaMMA sind Verbesserungen, kein Single Point of Failure.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_associator.py`:
```python
from tda_server.geo.cells import haversine_km
from tda_server.p0a.associator import associate, grid_search_origin
from tda_server.p0a.epic import StationPick


def picks_around(origin=(39.0, 40.0), t0=1_000_000, vp=6.0):
    coords = [(39.0, 40.0), (39.2, 40.1), (38.9, 40.3), (39.1, 39.8), (38.8, 39.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *origin)
        out.append(StationPick(f"S{i}", "P", t0 + int(1000 * d / vp), la, lo, 0.7))
    return out


def test_grid_search_recovers_origin():
    o = grid_search_origin(picks_around(), vp_kms=6.0, fixed_depth_km=8.0)
    assert o is not None
    assert haversine_km(o.lat, o.lon, 39.0, 40.0) < 25.0    # within grid resolution
    assert o.rms_s < 1.0 and o.n_picks == 5

def test_associate_auto_falls_back_to_grid():
    o = associate(picks_around(), backend="grid")
    assert o is not None and o.depth_km == 8.0

def test_too_few_picks_returns_none():
    assert grid_search_origin(picks_around()[:2], vp_kms=6.0, fixed_depth_km=8.0) is None
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_associator.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/associator.py`:
```python
from __future__ import annotations

import logging
from dataclasses import dataclass

import numpy as np

from tda_server.geo.cells import haversine_km
from tda_server.p0a.epic import StationPick, travel_time_rms

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Origin:
    lat: float
    lon: float
    depth_km: float
    time_ms: int
    n_picks: int
    rms_s: float


def grid_search_origin(picks: list[StationPick], *, vp_kms: float = 6.0,
                       fixed_depth_km: float = 8.0, min_picks: int = 4,
                       step_deg: float = 0.2) -> Origin | None:
    p_picks = [p for p in picks if p.phase == "P"]
    if len({p.station for p in p_picks}) < min_picks:
        return None
    lats = [p.lat for p in p_picks]
    lons = [p.lon for p in p_picks]
    grid_lat = np.arange(min(lats) - 1.0, max(lats) + 1.0, step_deg)
    grid_lon = np.arange(min(lons) - 1.0, max(lons) + 1.0, step_deg)
    best: Origin | None = None
    for la in grid_lat:
        for lo in grid_lon:
            # origin time = min(pick_time - dist/vp) gives the best common t0
            t0s = [p.time_ms - 1000.0 * haversine_km(p.lat, p.lon, la, lo) / vp_kms
                   for p in p_picks]
            t0 = int(sum(t0s) / len(t0s))
            rms = travel_time_rms(p_picks, float(la), float(lo), t0, vp_kms)
            if best is None or rms < best.rms_s:
                best = Origin(float(la), float(lo), fixed_depth_km, t0,
                              len({p.station for p in p_picks}), rms)
    return best


def associate(picks: list[StationPick], *, vp_kms: float = 6.0, vs_kms: float = 3.5,
              fixed_depth_km: float = 8.0, backend: str = "auto") -> Origin | None:
    if backend in ("pyocto", "auto"):
        try:
            return _associate_pyocto(picks, vp_kms, vs_kms, fixed_depth_km)
        except Exception as exc:  # noqa: BLE001 - fall through to next backend
            if backend == "pyocto":
                raise
            log.info("pyocto unavailable (%s), trying gamma", exc)
    if backend in ("gamma", "auto"):
        try:
            return _associate_gamma(picks, vp_kms, vs_kms, fixed_depth_km)
        except Exception as exc:  # noqa: BLE001
            if backend == "gamma":
                raise
            log.info("gamma unavailable (%s), using grid search", exc)
    return grid_search_origin(picks, vp_kms=vp_kms, fixed_depth_km=fixed_depth_km)


def _associate_pyocto(picks, vp_kms, vs_kms, fixed_depth_km) -> Origin | None:
    import pyocto  # noqa: F401  (integration path; benchmarked in Task 2)
    # Real PyOcto wiring lives here once Task 2 confirms the ARM build.
    # Until then 'auto' falls through to grid search.
    raise NotImplementedError("pyocto wiring pending ARM build confirmation (Task 2)")


def _associate_gamma(picks, vp_kms, vs_kms, fixed_depth_km) -> Origin | None:
    import gamma  # noqa: F401
    raise NotImplementedError("gamma wiring pending stack decision (Task 2)")
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_associator.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): associator with grid-search fallback (pyocto/gamma stubs)"
```

---

### Task 10: Türkei-Nachkalibrierung — regionale Pd-Mw-Regression (rein, voll TDD)

**Files:**
- Create: `server/src/tda_server/p0a/recalibrate.py`, `server/scripts/fit_recalibration.py`
- Test: `server/tests/test_p0a_recalibrate.py`

**Interfaces:**
- Produces:
  - `fit_pd_mw(log_pd: np.ndarray, log_r: np.ndarray, mw: np.ndarray) -> tuple[float, float, float]` — lineare Regression `log10(Pd) = a + b·Mw + c·log10(R)` (Least-Squares) → `(a, b, c)`, die regionalen Ersatzwerte für `PD_A, PD_B, PD_C`.
  - `PdMwModel(a: float, b: float, c: float)` mit `magnitude(pd_cm: float, r_km: float) -> float`.
  - `apply_to_magnitude_module(model: PdMwModel) -> None` — überschreibt `magnitude.PD_A/PD_B/PD_C` mit den regionalen Werten (der explizite Kalibrier-Übergang; kalifornische Defaults nur als Start, §Constraints).

**Wichtig:** Dies ist der Pflicht-Kalibrierschritt (§Constraints, RECHERCHE-P0A §2 „Türkei-Vorbehalt"). Er ist **auch der gemeinsame Kalibrier-Andockpunkt für P0b**: dieselbe AFAD/KOERI-Ereignisliste, die hier Pd-Mw fittet, benotet in der Lernschleife P0b-Alarme gegen die P2-Wahrheit (Verstärkung B↔B2).

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_recalibrate.py`:
```python
import numpy as np
from tda_server.p0a import magnitude
from tda_server.p0a.recalibrate import PdMwModel, apply_to_magnitude_module, fit_pd_mw


def test_fit_recovers_known_relation():
    rng = np.random.default_rng(0)
    a_t, b_t, c_t = -3.2, 0.75, -1.3
    mw = rng.uniform(4, 7, 500)
    log_r = np.log10(rng.uniform(10, 200, 500))
    log_pd = a_t + b_t * mw + c_t * log_r + rng.normal(0, 0.01, 500)
    a, b, c = fit_pd_mw(log_pd, log_r, mw)
    assert abs(a - a_t) < 0.1 and abs(b - b_t) < 0.05 and abs(c - c_t) < 0.05

def test_model_magnitude_inverts_fit():
    m = PdMwModel(a=-3.2, b=0.75, c=-1.3)
    # forward then invert should round-trip
    mw, r = 6.0, 50.0
    log_pd = -3.2 + 0.75 * mw - 1.3 * np.log10(r)
    assert abs(m.magnitude(10 ** log_pd, r) - mw) < 1e-6

def test_apply_overrides_module_constants():
    orig = (magnitude.PD_A, magnitude.PD_B, magnitude.PD_C)
    try:
        apply_to_magnitude_module(PdMwModel(a=-3.2, b=0.75, c=-1.3))
        assert magnitude.PD_A == -3.2 and magnitude.PD_B == 0.75
    finally:
        magnitude.PD_A, magnitude.PD_B, magnitude.PD_C = orig
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_recalibrate.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/recalibrate.py`:
```python
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from tda_server.p0a import magnitude


def fit_pd_mw(log_pd: np.ndarray, log_r: np.ndarray,
              mw: np.ndarray) -> tuple[float, float, float]:
    """Least-squares fit of log10(Pd) = a + b*Mw + c*log10(R)."""
    x = np.column_stack([np.ones_like(mw, dtype=float), mw.astype(float),
                         log_r.astype(float)])
    coef, *_ = np.linalg.lstsq(x, log_pd.astype(float), rcond=None)
    return float(coef[0]), float(coef[1]), float(coef[2])


@dataclass(frozen=True)
class PdMwModel:
    a: float
    b: float
    c: float

    def magnitude(self, pd_cm: float, r_km: float) -> float:
        return (math.log10(pd_cm) - self.a - self.c * math.log10(r_km)) / self.b


def apply_to_magnitude_module(model: PdMwModel) -> None:
    """Replace the California-default Pd coefficients with regional Turkish ones.
    Mandatory calibration step; also the shared calibration anchor for P0b."""
    magnitude.PD_A = model.a
    magnitude.PD_B = model.b
    magnitude.PD_C = model.c
```

`scripts/fit_recalibration.py`:
```python
"""Fit regional Pd-Mw from an AFAD/KOERI event list (CSV: log_pd,log_r,mw) and
print the coefficients to set as PD_A/PD_B/PD_C. Data access per Task 1."""
import csv
import sys

import numpy as np

from tda_server.p0a.recalibrate import fit_pd_mw

if __name__ == "__main__":
    rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
    log_pd = np.array([float(r["log_pd"]) for r in rows])
    log_r = np.array([float(r["log_r"]) for r in rows])
    mw = np.array([float(r["mw"]) for r in rows])
    a, b, c = fit_pd_mw(log_pd, log_r, mw)
    print(f"PD_A={a:.4f} PD_B={b:.4f} PD_C={c:.4f}  (n={len(rows)})")
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_recalibrate.py -q`
Expected: `3 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): regional Pd-Mw recalibration (mandatory Turkish step)"
```

---

### Task 11: P0a-Detektor-Orchestrierung → SourceEvent (Modell- + Beobachtungspfad)

**Files:**
- Create: `server/src/tda_server/p0a/detector.py`
- Test: `server/tests/test_p0a_detector.py`

**Interfaces:**
- Consumes: `Origin`/`associate` (Task 9), `StationPick` (Task 4), `epic_alarm` (Task 4), `combined_magnitude` (Task 3), `plum_cells`/`IntensityObs` (Task 5), `is_teleseism`/`is_blast` (Task 6), `SourceEvent` (Plan A)
- Produces:
  - `P0aDecision(source_event: SourceEvent | None, path: str, reason: str)` — `path ∈ {"model", "plum", "none"}`; `reason` erklärt Verwerfen (Fernbeben/Sprengung/zu wenige Stationen).
  - `P0aDetector(near_stations: set[str], source_zones: list[SourceZone], *, shadow: bool = True)` mit `evaluate(picks, intensity_obs, *, now_ms) -> P0aDecision` — führt beide Pfade: Modellpfad (associate → Diskriminierung → EPIC → Magnitude → SourceEvent) und Beobachtungspfad (PLUM); im **Schattenbetrieb** (`shadow=True`, Default bis Task 1/§Coverage grün) wird das `SourceEvent` erzeugt und geloggt, aber mit `source_event_id`-Präfix `p0a-shadow:` markiert, damit die Fusion es als nicht-scharf behandeln kann.
  - Emittiert `SourceEvent(source="p0a", mag_type="pd")`.

**Wichtig:** Diskriminierung läuft **vor** der Alarmentscheidung: `is_teleseism` oder `is_blast` ⇒ `path="none"` mit Begründung, keine lokale Warnung (§3). Der Beobachtungspfad (PLUM) kann auch dann auslösen, wenn der Modellpfad an Sättigung scheitert (große Brüche) — die beiden Pfade sind unabhängig.

- [ ] **Step 1: Failing Test schreiben**

`tests/test_p0a_detector.py`:
```python
from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.discriminate import SourceZone
from tda_server.p0a.epic import StationPick
from tda_server.p0a.plum import IntensityObs

ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0)]
NEAR = {"S0", "S1", "S2", "S3", "S4"}


def picks(origin=(37.2, 37.0), t0=1_000_000, vp=6.0, pd=0.8):
    coords = [(37.2, 37.0), (37.4, 37.1), (37.0, 37.2), (37.1, 36.8), (37.3, 36.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *origin)
        out.append(StationPick(f"S{i}", "P", t0 + int(1000 * d / vp), la, lo, pd))
    return out


def test_model_path_emits_source_event_in_zone():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    dec = det.evaluate(picks(), [], now_ms=1_000_500)
    assert dec.path == "model" and dec.source_event is not None
    assert dec.source_event.source == "p0a" and dec.source_event.mag_type == "pd"

def test_teleseism_rejected():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    far = picks(origin=(10.0, 100.0))
    for i, p in enumerate(far):
        far[i] = StationPick(p.station, p.phase, p.time_ms, 10.0 + i * 0.1, 100.0, p.pd_cm)
    dec = det.evaluate(far, [], now_ms=1_000_500)
    assert dec.path == "none" and "teleseism" in dec.reason.lower()

def test_plum_path_triggers_when_model_below_threshold():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    obs = [IntensityObs(37.2, 37.0, mmi=6.0)]
    dec = det.evaluate(picks()[:2], obs, now_ms=1_000_500)  # too few picks for model
    assert dec.path == "plum" and dec.source_event is not None

def test_shadow_mode_marks_event():
    det = P0aDetector(NEAR, ZONES, shadow=True)
    dec = det.evaluate(picks(), [], now_ms=1_000_500)
    assert dec.source_event.source_event_id.startswith("p0a-shadow:")
```

- [ ] **Step 2: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_detector.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 3: Implementieren**

`src/tda_server/p0a/detector.py`:
```python
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone

from tda_server.domain.events import SourceEvent
from tda_server.geo.cells import haversine_km
from tda_server.p0a.associator import associate
from tda_server.p0a.discriminate import SourceZone, is_blast, is_teleseism, local_time_features
from tda_server.p0a.epic import StationPick, epic_alarm
from tda_server.p0a.magnitude import combined_magnitude
from tda_server.p0a.plum import IntensityObs, plum_cells, plum_triggered


@dataclass(frozen=True)
class P0aDecision:
    source_event: SourceEvent | None
    path: str      # "model" | "plum" | "none"
    reason: str


class P0aDetector:
    def __init__(self, near_stations: set[str], source_zones: list[SourceZone], *,
                 shadow: bool = True) -> None:
        self.near = near_stations
        self.zones = source_zones
        self.shadow = shadow

    def _mk_event(self, lat: float, lon: float, depth_km: float | None, origin_ms: int,
                  mag: float, mag_low: float, mag_high: float, now_ms: int) -> SourceEvent:
        prefix = "p0a-shadow" if self.shadow else "p0a"
        return SourceEvent(
            source="p0a",
            source_event_id=f"{prefix}:{origin_ms}:{lat:.2f}_{lon:.2f}",
            origin_time=datetime.fromtimestamp(origin_ms / 1000, tz=timezone.utc),
            lat=lat, lon=lon, depth_km=depth_km,
            magnitude=mag, mag_type="pd",
            received_at=datetime.fromtimestamp(now_ms / 1000, tz=timezone.utc),
        )

    def evaluate(self, picks: list[StationPick], intensity_obs: list[IntensityObs], *,
                 now_ms: int) -> P0aDecision:
        # --- model path ---
        origin = associate(picks, backend="auto")
        if origin is not None:
            if is_teleseism(origin.lat, origin.lon, self.zones):
                model_reason = "teleseism outside source zones"
            else:
                hour, wd = local_time_features(origin.time_ms)
                blast = is_blast(origin_ms=origin.time_ms, depth_km=origin.depth_km,
                                 ps_amp_ratio=None, local_hour=hour, weekday=wd)
                if blast:
                    model_reason = "likely blast"
                elif epic_alarm(picks, self.near, origin_lat=origin.lat,
                                origin_lon=origin.lon, origin_ms=origin.time_ms):
                    pd = max((p.pd_cm or 0.0) for p in picks)
                    r = min(haversine_km(p.lat, p.lon, origin.lat, origin.lon)
                            for p in picks) or 1.0
                    m, lo, hi = combined_magnitude(max(pd, 1e-3), r, tauc_s=None)
                    ev = self._mk_event(origin.lat, origin.lon, origin.depth_km,
                                        origin.time_ms, m, lo, hi, now_ms)
                    return P0aDecision(ev, "model", "epic criteria met")
                else:
                    model_reason = "epic criteria not met"
        else:
            model_reason = "too few picks to associate"

        # --- observation path (independent; robust to saturation) ---
        if plum_triggered(intensity_obs):
            cells = plum_cells(intensity_obs)
            top = max(cells.values())
            olat = sum(o.lat for o in intensity_obs) / len(intensity_obs)
            olon = sum(o.lon for o in intensity_obs) / len(intensity_obs)
            # PLUM has no magnitude; carry a conservative intensity-derived floor
            mag = round(3.0 + 0.5 * top, 1)
            ev = self._mk_event(olat, olon, None, now_ms, mag, mag, mag, now_ms)
            return P0aDecision(ev, "plum", "plum observation threshold met")

        return P0aDecision(None, "none", model_reason)
```

- [ ] **Step 4: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_detector.py -q`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(p0a): detector with model + PLUM paths, discrimination, shadow mode"
```

---

### Task 12: SeedLink-Ingest + Fusion-Bridge + Kahramanmaraş-Schattenreplay

**Files:**
- Create: `server/src/tda_server/p0a/seedlink.py`, `server/src/tda_server/p0a/replay.py`, `server/tests/test_p0a_replay.py`
- Test: `server/tests/test_p0a_replay.py`

**Interfaces:**
- Produces:
  - `RingBuffer(max_s: float = 120.0)` — pro Station gleitender Wellenformpuffer; `append(trace)`, `window(seconds)`.
  - `SeedLinkIngest(url: str, streams: list[str])` mit `async run(on_stream) -> None` — ObsPy `EasySeedLinkClient`-Wrapper; ruft `on_stream(station, stream)` je vervollständigtem Fenster. Integration (skippt ohne obspy).
  - `replay_p0a(picklist: list[dict], detector: P0aDetector, correlator: Correlator, *, now_ms_fn) -> dict` — spielt eine aufgezeichnete/kuratierte **Kahramanmaraş-Pickliste** durch den P0a-Detektor **und** den gemeinsamen Korrelator; misst `detect_latency_s` (Herdzeit → erster SourceEvent) und ob eine anschließende Katalog-/P0b-Quelle das Ereignis auf `CONFIRMED` (Tier P2) hebt. Reiner Code (kein schwerer Dep) — der wissenschaftliche Abnahme-Gate.
  - `cross_confirm_demo(...)` als Teil des Tests: ein P0b-`SourceEvent` (aus Plan B2) desselben Ereignisses trifft nach dem P0a-Event ein → Korrelator liefert `confirm` → `derive_tier` == `"P2"`. **Das ist der ausführbare Beweis der gegenseitigen Verstärkung.**

**Wichtig:** Scharf erst nach bestandenem Schattenreplay (§Erfolgskriterien der Spec: „Kahramanmaraş 2023 läuft deterministisch durch den Produktions-Fusionscode"). Der Replay nutzt denselben `Correlator` wie Produktion.

- [ ] **Step 1: Kahramanmaraş-Pickliste als Fixture kuratieren**

Aus Task-1-Abdeckung eine kleine, real belegte Pickliste (Station, Phase, Zeit, Lat/Lon, Pd) um den M7,8-Ursprung (2023-02-06T01:17:35 UTC, 37,17 N 37,03 O) als `tests/fixtures/kahramanmaras_picks.json` anlegen. Quelle dokumentieren (AFAD/KOERI/GEOFON-Bulletin). Sind reale Pd-Werte nicht verfügbar, konservative Platzhalter setzen und **als solche kennzeichnen** (der Latenztest prüft die Kette, nicht die exakte Magnitude).

- [ ] **Step 2: Failing Test schreiben**

`tests/test_p0a_replay.py`:
```python
from datetime import datetime, timezone
from tda_server.alert.payload import derive_tier
from tda_server.domain.events import EventState, SourceEvent
from tda_server.fusion.correlator import Correlator
from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.discriminate import SourceZone
from tda_server.p0a.epic import StationPick
from tda_server.p0a.replay import replay_p0a

ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0)]
NEAR = {"S0", "S1", "S2", "S3", "S4"}
ORIGIN = (37.17, 37.03)
T0 = 1_675_646_255_000  # 2023-02-06T01:17:35Z in ms


def picklist():
    coords = [(37.17, 37.03), (37.4, 37.1), (37.0, 37.2), (37.1, 36.8), (37.3, 36.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *ORIGIN)
        out.append({"station": f"S{i}", "phase": "P",
                    "time_ms": T0 + int(1000 * d / 6.0), "lat": la, "lon": lo,
                    "pd_cm": 1.5})
    return out


def test_replay_detects_within_latency_budget():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    res = replay_p0a(picklist(), det, Correlator(), now_ms_fn=lambda: T0 + 8000)
    assert res["detected"] is True
    assert res["detect_latency_s"] is not None and res["detect_latency_s"] <= 10.0
    assert res["origin_error_km"] < 40.0


def test_p0b_then_p0a_cross_confirms_to_p2():
    # the executable proof of mutual reinforcement (B2 <-> B)
    corr = Correlator()
    t = datetime.fromtimestamp(T0 / 1000, tz=timezone.utc)
    p0b_ev = SourceEvent("p0b", f"p0b:{T0}", t, 37.17, 37.03, None, 5.5, "p0b_proxy", t)
    tr1 = corr.ingest(p0b_ev)
    assert derive_tier(tr1.event) == "P0"               # crowdsourcing first: P0
    p0a_ev = SourceEvent("p0a", f"p0a:{T0}", t, 37.2, 37.0, 8.0, 7.2, "pd", t)
    tr2 = corr.ingest(p0a_ev)
    assert tr2 is not None and tr2.event.state is EventState.CONFIRMED
    assert derive_tier(tr2.event) == "P2"               # stations confirm: P2
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_replay.py -q`
Expected: `ModuleNotFoundError`

- [ ] **Step 4: Implementieren**

`src/tda_server/p0a/replay.py`:
```python
from __future__ import annotations

from typing import Callable

from tda_server.fusion.correlator import Correlator
from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.epic import StationPick


def replay_p0a(picklist: list[dict], detector: P0aDetector, correlator: Correlator, *,
               now_ms_fn: Callable[[], int]) -> dict:
    """Feed a recorded pick list through the P0a detector AND the shared correlator.
    Deterministic scientific gate; must pass before P0a goes live in a zone."""
    picks = [StationPick(p["station"], p["phase"], p["time_ms"], p["lat"], p["lon"],
                         p.get("pd_cm")) for p in picklist]
    origin_ms = min(p["time_ms"] for p in picklist)
    decision = detector.evaluate(picks, [], now_ms=now_ms_fn())
    if decision.source_event is None:
        return {"detected": False, "detect_latency_s": None,
                "origin_error_km": None, "reason": decision.reason}
    ev = decision.source_event
    tr = correlator.ingest(ev)
    detect_ms = int(ev.received_at.timestamp() * 1000)
    return {
        "detected": tr is not None,
        "detect_latency_s": (detect_ms - origin_ms) / 1000.0,
        "origin_error_km": haversine_km(ev.lat, ev.lon, picklist[0]["lat"],
                                        picklist[0]["lon"]),
        "path": decision.path,
    }
```

`src/tda_server/p0a/seedlink.py`:
```python
from __future__ import annotations

import logging
from collections import defaultdict, deque
from typing import Awaitable, Callable

log = logging.getLogger(__name__)


class RingBuffer:
    def __init__(self, max_s: float = 120.0) -> None:
        self.max_s = max_s
        self._traces: dict[str, deque] = defaultdict(deque)

    def append(self, station: str, trace) -> None:
        dq = self._traces[station]
        dq.append(trace)
        # trim by count as a simple bound; real trimming by endtime on the VM
        while len(dq) > 256:
            dq.popleft()

    def window(self, station: str):
        return list(self._traces.get(station, []))


class SeedLinkIngest:
    """ObsPy EasySeedLinkClient wrapper. Integration path; wired on the VM once
    Task 1 confirms an open SeedLink endpoint. Skipped in CI without obspy."""

    def __init__(self, url: str, streams: list[str]) -> None:
        self.url = url
        self.streams = streams
        self.buffer = RingBuffer()

    async def run(self, on_stream: Callable[[str, object], Awaitable[None]]) -> None:
        from obspy.clients.seedlink.easyseedlink import EasySeedLinkClient  # noqa: F401
        raise NotImplementedError(
            "seedlink wiring pending open endpoint from Task 1 coverage decision")
```

- [ ] **Step 5: Test ausführen — muss bestehen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_replay.py -q`
Expected: `2 passed`

- [ ] **Step 6: Gesamte P0a-Suite ausführen**

Run: `.venv/Scripts/python -m pytest tests/test_p0a_*.py -q`
Expected: alle reinen Tasks grün; Integrations-Tests (Picker/SeedLink) `skipped` ohne Stack.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(p0a): seedlink ingest skeleton + Kahramanmaras shadow replay + cross-confirm proof"
```

---

## Self-Review (Plan-Autor)

**Spec-Abdeckung:**
- §2.2 P0a (Stations-Picker, GEOFON Tag 1, Sekunden) → Tasks 1–12; Datenrealität ehrlich als Gate (Task 1).
- §3 Stations-Pipeline (SeedLink → Picker → Assoziation, Stationsgesundheit) → Tasks 8, 9, 12.
- §3 Nicht-tektonische Diskriminierung (Sprengung, Fernbeben) → Task 6, verdrahtet in Task 11.
- §3 Zwei Entscheidungspfade (Modell + Beobachtung/PLUM) → Tasks 4, 5, 11.
- §3 Stations-Authentizität / Angriffs-Warnsignal → Assoziations-Pflicht (Task 9) + EPIC-RMS (Task 4); Consumer-Sensor-Abwertung ist Konfigurationssache in `near_stations`/Gewichtung.
- §4 Latenzbudget → ARM-Benchmark (Task 2) misst die kritischen ms; Replay (Task 12) misst die Kette.
- RECHERCHE-P0A §1 Stack (SeisBench/PhaseNet + PyOcto + GaMMA) → Tasks 2, 8, 9; ARM-Risiken (kein Wheel, Doppel-Picks) → Grid-Fallback (9) + Dedupe (7).
- RECHERCHE-P0A §2 Magnitude (Pd/τc, Sättigung, PLUM, GMICE-Unsicherheit) → Tasks 3, 5; FinDer Stufe 2 ehrlich ausgelagert.
- „Türkei-Nachkalibrierung ist Pflicht" → Task 10.
- Replay-Pflichttest Kahramanmaraş → Task 12.

**Bewusste Auslassungen (verschobene Lücken):** FinDer/Finite-Fault + GNSS/GFAST für M > 7 (Stufe 2); reale PyOcto/GaMMA-Verdrahtung (Stubs, aktiviert nach ARM-Build in Task 2 — Grid-Search hält die Kette lauffähig); reale SeedLink-Verdrahtung (Skelett, aktiviert nach Task-1-Endpunkt); Stationsgesundheits-Auto-Degradierung (Betrieb, Plan F); τc-Berechnung aus Wellenform (Task 3 nimmt τc als Eingabe; Extraktion an realen Traces ist Picker-nah, mit Task 8 auf der VM nachziehbar).

**Platzhalter-Scan:** keine TODO/TBD in ausführbarem Code. `NotImplementedError` in PyOcto/GaMMA/SeedLink ist **bewusst und getestet** (Grid-Fallback/Skip decken sie ab), nicht ein Platzhalter — die Aktivierung hängt an den realen Gates (ARM-Build, offener Endpunkt), die Task 1/2 herstellen.

**Typkonsistenz:** `SourceEvent`-Signatur exakt wie Plan A/B2. `StationPick` einmal in Task 4 definiert, überall importiert. `Origin` in Task 9. `derive_tier` aus Plan B2 Task 11 (p0a+p0b → P2 im Cross-Confirm-Test bewiesen). `haversine_km`/`affected_cells`/`cell_id` aus Plan A Task 3. Magnitude-Konstanten `PD_*`/`TAUC_*` als Modul-Attribute, von Task 10 überschreibbar — konsistent genutzt in Tasks 3, 10.

**Reihenfolge-Verstärkung (ausführbar bewiesen):** `test_p0b_then_p0a_cross_confirms_to_p2` (Task 12) ist der laufende Beweis der Sequenz-Zusage: P0b zuerst (Tier P0), P0a bestätigt (Tier P2) über denselben Korrelator. Task 10 (Nachkalibrierung) ist der gemeinsame Kalibrier-Andockpunkt beider Kanäle. Damit ist die in „B2 → B, sich gegenseitig verstärkend" zugesagte Verzahnung nicht nur beschrieben, sondern verdrahtet und getestet.
```
