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
