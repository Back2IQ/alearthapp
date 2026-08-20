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

See the "ARM Benchmark" section below, added by Task 2.
