from __future__ import annotations

from datetime import timezone

from alert2iq_server.p0a.epic import StationPick


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
