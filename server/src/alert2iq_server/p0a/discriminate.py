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
