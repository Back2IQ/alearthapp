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
