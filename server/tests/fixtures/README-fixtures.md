# p0a test fixtures

## `kahramanmaras_window.mseed`

**Real, recorded waveform data — not synthetic.** Fetched live during Plan B
Task 8 from GEOFON's public FDSNWS dataselect service:

```
GET https://geofon.gfz.de/fdsnws/dataselect/1/query
    ?net=GE&sta=ISP&loc=00&cha=BH*
    &start=2023-02-06T01:17:00&end=2023-02-06T01:20:00
```

- Station: `GE.ISP` (Isparta, Turkey; lat 37.8433, lon 30.5093, elev 1100 m) —
  the one Turkish-region station found reachable via GEOFON in the Task 1
  coverage probe (see `docs/p0a-coverage.md`). Confirmed active at this
  location/channel/time via GEOFON's FDSNWS-station service (`loc=00`,
  channels `BHE/BHN/BHZ`, operating since 2014-01-16 with no end date at
  query time) — the live SeedLink `INFO STREAMS` listing showed this station
  registered but not actively streaming at probe time; this fixture instead
  comes from GEOFON's **archived dataselect** service, a separate, always-on
  HTTP endpoint that serves historical recordings regardless of current
  live-stream status.
- Window: ~3 minutes around the 2023-02-06T01:17:35 UTC M7.8 Kahramanmaraş
  mainshock origin time (2023-02-06T01:16:46Z to 2023-02-06T01:20:04Z,
  slightly different per channel due to independent record boundaries).
- 3 channels (BHZ/BHN/BHE), 20 Hz, STEIM2-encoded MiniSEED, 29696 bytes,
  fetched once and committed as a static fixture (no live network access
  needed to run the test that consumes it).
- Distance from `GE.ISP` to the Kahramanmaraş epicenter (37.17N, 37.03E) is
  roughly 620 km — well outside the near-field EPIC association range used
  elsewhere in Plan B's synthetic test fixtures, but it is a **real** P-wave
  arrival recording of a real large earthquake, which is what
  `test_p0a_picker.py` needs to check the PhaseNet wrapper against genuine
  seismic signal rather than noise.

This file is intentionally kept small (single station, ~3 minute window) —
it exists to prove the `PhaseNetPicker` wrapper runs against a real signal,
not to serve as a multi-station association fixture (that is
`kahramanmaras_picks.json`, added in Task 12, which is a curated pick list,
not raw waveforms).
