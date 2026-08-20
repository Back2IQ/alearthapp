# P0a SeedLink Access & Coverage Decision

**Status:** blocking gate result for Plan B (Task 1). Probed live from the dev
sandbox on 2026-08-20 using `scripts/test_seedlink_access.py` plus a raw-socket
`INFO STREAMS` query (no `slinktool` binary available in this environment; a
dependency-free Python fallback was used instead, per the script's own
docstring). All findings below are from real network probes, not assumptions.

## Endpoint reachability (`scripts/test_seedlink_access.py`)

| Endpoint | Host:Port | Result |
|---|---|---|
| GEOFON | geofon.gfz.de:18000 | **OPEN** — `SeedLink v4.0 [HMB SeedLink v0.2 (2026.110)] :: SLPROTO:4.0` |
| KOERI  | eida.koeri.boun.edu.tr:18000 | **CLOSED/REFUSED** — `WinError 10061`, connection actively refused |
| IRIS/EarthScope | rtserve.earthscope.org:18000 | **OPEN** — `SeedLink v4.0 (RingServer/4.5.6)` |

KOERI's SeedLink port is not openly reachable from this network. This
confirms the plan's blocking-precondition research (RECHERCHE-P0A §3): the
KOERI free/open port is not available; the AFAD/KOERI partnership channel
remains the only path to Turkish-network SeedLink and is out of this plan's
scope.

## Stream enumeration (`INFO STREAMS`, raw SeedLink protocol query)

### GEOFON (complete enumeration — response ended naturally, not truncated)

- 365 stations parsed across 32 networks (`1D 2Q AW BW CK CP CX CZ DK EE EI FN
  GE GR GX HE HN IO IS IU JS M1 MK MN NU PL RO SK SX TT WM YU`).
- Exactly **one** station in/near Turkey: `GE.ISP` — description
  `"GEOFON/MedNet/KOERI Station Isparta, Turkey"`.
- **Important caveat found in the probe:** `GE.ISP`'s station element has
  `begin_seq="0" end_seq="0"` and **no `<stream>` sub-elements** (self-closing
  tag), unlike every other active station in the listing. That means the
  station is registered on GEOFON's SeedLink server but was **not actively
  streaming any channel** at probe time. Real-time waveform availability from
  this single station cannot be assumed without a follow-up probe closer to
  go-live.
- No other Turkish-region stations (no other `GE` stations fall inside
  Turkey; nearby regional stations are Cyprus (`GE.CSS`) and Greece
  (`GE.KARP`, `GE.KTHA`, `GE.THERA`, `GE.KERA`) — useful for teleseism
  discrimination context, not for local Turkish EEW).
- No `KO` or `TU` network codes appear anywhere in the GEOFON stream list.
- Station lat/lon were **not** retrieved: GEOFON's SeedLink `INFO STREAMS`
  XML does not carry coordinates, and a follow-up FDSNWS-station HTTP lookup
  failed in this sandbox on a local TLS certificate verification error
  (`CERTIFICATE_VERIFY_FAILED`) — an environment limitation, not a coverage
  finding. Coordinates need to be pulled on the target VM before Task 12/8
  fixture work if `GE.ISP` is to be used directly.

### IRIS/EarthScope ringserver (partial — response capped at 3 MB probe budget)

- 1650 stations parsed across at least 68 networks before the probe's byte
  cap was hit (alphabetically incomplete: enumeration reached network code
  `HV`). No `KO`/`TU` network code or any station description containing
  "Turk" appeared in the enumerated portion. This is a **global** GSN/FDSN
  ring server, not a Turkey-focused feed; treated here only as a secondary
  reachability data point, not re-probed to full completion since KOERI is
  the endpoint that actually matters for Turkish coverage and it is already
  conclusively closed.

## Coverage decision

**(a) GEOFON-only, and thin even there.** Real, freely reachable, real-time
SeedLink access today provides exactly **one** candidate Turkish-region
station (`GE.ISP`, Isparta), and that station showed **no active stream** at
probe time. This is per the plan's own expectation ("nur wenige
GEOFON-Stationen") but even thinner than "few" — effectively zero usable
near-field stations on the North Anatolian / East Anatolian fault zones from
this feed alone.

- **(b) KOERI port:** closed/refused — not available.
- **(c) AFAD/partnership:** out of scope, unchanged.

**Decision:** P0a runs in **Schattenbetrieb** (`P0aDetector(shadow=True)`,
the default) for all zones. No zone is scharfgeschaltet by this plan. A
single non-streaming station cannot satisfy the association requirement
(≥4 stations, Task 4/9) or serve as a real "near station" set for Task 7's
dedupe context — `near_stations` inputs used by tests and any future
production wiring must come from a richer feed (KOERI/AFAD partnership) or
be revisited once `GE.ISP` (or additional GEOFON/EIDA mirror stations) are
confirmed actively streaming on the target VM.

**Consequence for Task 12 replay:** the Kahramanmaraş shadow replay uses a
**curated fixture pick list** (documented separately in
`tests/fixtures/kahramanmaras_picks.json`), not a live pull from this
single-station feed — consistent with this coverage result.

## ARM benchmark (Task 2)

**Important caveat: this was run in the dev sandbox, not the target ARM VM.**
`platform.machine()` reports `AMD64` on Windows (MINGW64), Python 3.14.2 —
this is x86_64, not the Oracle Always-Free ARM instance the plan targets.
Numbers below are a smoke test that the stack runs at all on 3.14, **not** an
ARM go/no-go — that measurement still needs to be taken on the real VM before
station count/window size are fixed, per the plan's own instruction (§Task 2,
"ARM-Benchmark ist Go/No-Go ... keine ARM-Referenzdaten publiziert").

### Install (`pip install -e ".[p0a]"`)

- `obspy==1.5.0` and `seisbench==0.12.5` **installed successfully** — both
  ship `cp314-win_amd64` wheels, so no build/compiler blocker on this
  platform/Python combination (unexpectedly good news given Python 3.14 is
  very new; ARM wheel availability for the same versions is still unverified
  and must be checked on the actual VM, not assumed from this x86_64 result).
- `numpy>=1.26` already present (2.5.2).

### PyOcto (`pip install pyocto`)

**FAILED — confirms the plan's predicted risk exactly.** No prebuilt wheel;
pip fell back to a source build via `scikit-build-core`/CMake, which failed
because no C++ compiler/`nmake` is available in this environment:
```
CMake Error: CMAKE_CXX_COMPILER not set, after EnableLanguage
```
This is the documented ARM risk (Recherche §1, Risiko 2) reproduced here on
x86_64 too — PyOcto needs a real C++ toolchain wherever it's built, which the
target VM must provide (or the build will fail there as well). **Verdict for
now: GaMMA/grid-search fallback path, as the plan anticipates.**

### GaMMA fallback (`pip install gamma`)

**Finding, not a success:** the PyPI package literally named `gamma`
installed cleanly but is an **unrelated, essentially empty package**
(single near-empty `__init__.py`, pulls in an unrelated `monty`/materials
-science dependency chain) — it is **not** the AI4EPS/GaMMA seismic
associator referenced by the plan. That project is not published to PyPI
under a matching importable name; installing the real GaMMA would require
building from `https://github.com/AI4EPS/GaMMA` source, which was not
attempted here (out of the probe's time budget, and Task 9's `_associate_gamma`
stub already treats "gamma unavailable" as an expected, tested fallback
branch to grid-search). This package was installed then immediately
uninstalled again to avoid a misleading dependency in the environment.
**Verdict: GaMMA is not actually available in this environment either** —
the grid-search fallback (Task 9) is therefore the real, currently-working
associator path, exactly as the plan designs for.

### PhaseNet throughput (`scripts/bench_arm.py`, x86_64 dev machine)

```
PhaseNet: 20 windows (30s @ 100Hz) in 0.10s = 5 ms/window/3ch
```
5 ms/window/3-channel on x86_64/CPU is comfortably inside any plausible
latency budget — but this number **must be re-measured on the target ARM
VM** before it informs station-count/window-size decisions; x86_64 desktop
throughput is not a stand-in for an Oracle Always-Free ARM core.

### Go/No-Go summary

| Component | Install/build result (this environment) | Verdict |
|---|---|---|
| obspy, seisbench | OK (wheels for cp314) | usable, pending ARM wheel confirmation |
| PyOcto | build FAILED (no C++ toolchain) | fallback required, matches plan's prediction |
| GaMMA (real AI4EPS lib) | not installed (PyPI name squatted by unrelated package) | fallback required |
| Grid-search associator (Task 9) | pure numpy, no external deps | **the working associator path today** |

**No fabricated benchmark numbers.** The PhaseNet throughput figure above is
real but explicitly x86_64, not ARM — recorded as a smoke-test data point,
not a Go decision. ARM re-measurement remains an open action item for
whoever provisions the target Oracle VM.

## Vor P0a-Scharfschaltung zwingend zu beheben

P0a runs in Schattenbetrieb only (see Coverage decision above); none of the
following block shadow operation, but ALL must be fixed before any zone is
scharfgeschaltet (armed for real alerting). Each requires real seismic/
picker work, not a code-only patch, and is intentionally left unfixed here.

- **FUND 2 — `is_blast` is inert.** The blast/explosion discriminator is
  gated on `ps_amp_ratio`, but the current picker never populates it — every
  call sees `ps_amp_ratio=None`, so the blast filter can never fire and
  quarry/mining blasts are not screened out. Before scharfschalten: compute
  a real P/S amplitude ratio in the picker (or a downstream enrichment step)
  and feed it into the discriminator; verify against known regional blast
  events, not just synthetic ones.
- **FUND 8 — `plum_triggered` fires on a single MMI2.5+ observation, and
  mislabels its magnitude as instrumental.** One observation at or above
  MMI2.5 is enough to trigger the PLUM path today, which is thin evidence
  for a real felt-intensity confirmation (one noisy/misplaced sensor could
  trip it). Worse, the resulting event is tagged `mag_type="pd"` —
  instrumental — even though PLUM's magnitude is intensity-derived, not a
  Pd/tau_c measurement; after FUND 3's merge-precedence fix
  (`domain/events.py`), an instrumental mag_type now wins over the p0b
  proxy by design, so a thinly-evidenced, mislabeled PLUM reading could
  incorrectly out-rank a real p0b crowd signal too. Before scharfschalten:
  require a minimum observation count (not a single station/point) for
  `plum_triggered`, and give PLUM-derived magnitudes an honest
  intensity-derived `mag_type` distinct from instrumental Pd/tau_c readings.
- **FUND 9 — the live picker never delivers `pd_cm`.** All P0a magnitude
  fixtures/replays (e.g. `tests/test_p0a_replay.py`) supply `pd_cm` directly
  in the picklist, but the real `PhaseNetPicker` wrapper does not extract
  peak displacement from the waveform — in the live path `pd_cm` is
  `None`, which drives the Pd-inversion magnitude estimate down to
  approximately its floor value regardless of the true earthquake size, i.e.
  a systematic UNDER-warning in production, the opposite failure mode from
  FUND 8. Before scharfschalten: extract Pd (peak displacement, cm) from the
  real waveform window in the picker and wire it into the magnitude
  estimate; verify against a real event with known catalog magnitude, not
  only against fixture picklists that assume `pd_cm` is already present.
