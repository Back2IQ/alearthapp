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
