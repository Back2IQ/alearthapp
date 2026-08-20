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
