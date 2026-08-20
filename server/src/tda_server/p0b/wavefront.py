from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center


@dataclass(frozen=True)
class CellHit:
    cell: str
    first_ms: int


def wavefront_consistent(hits: list[CellHit], *, v_s_kms: float = 3.5,
                         v_band: tuple[float, float] = (2.5, 5.0),
                         tol_s: float = 4.0, min_cells: int = 3) -> bool:
    """A real earthquake front: trigger delay grows consistently with distance
    from the origin at an S-/surface-wave speed. Citywide-simultaneous noise
    (fireworks, cheering) shows ~0 delay regardless of distance.

    CAVEAT (FUND 5, honesty note - not yet remedied by real data): the
    default tol_s=4.0 was calibrated against SYNTHETIC ~300ms trigger jitter
    only (see replay/scenario generation). Real phone-trigger jitter -
    human reaction time, app wake latency, OS scheduling, clock sync error -
    is expected to be in the SECOND range, not milliseconds, and is very
    likely distance/device-population dependent. Against real jitter of that
    magnitude, a real but distant earthquake risks being rejected as
    "not a consistent wavefront" (a missed detection), or tol_s set too loose
    risks accepting citywide-simultaneous noise as a front. tol_s remains a
    caller-supplied parameter for exactly this reason: it MUST be
    recalibrated against MEASURED real-world trigger jitter (ideally as a
    function of distance from origin) before this check is armed
    (scharfgeschaltet) for real alerting. No such real-jitter recalibration
    has been performed - do not assume tol_s=4.0 is validated beyond the
    synthetic-jitter scenarios it was tuned against."""
    if len(hits) < min_cells:
        return False
    origin = min(hits, key=lambda h: h.first_ms)
    ola, olo = detect_cell_center(origin.cell)
    dist = np.array([haversine_km(*detect_cell_center(h.cell), ola, olo) for h in hits])
    dt = np.array([(h.first_ms - origin.first_ms) / 1000.0 for h in hits])
    if dist.max() < 1.0:
        return False                              # all cells co-located: no leverage
    # fit dt = dist / v  (through origin); slope = 1/v
    slope = float(np.sum(dist * dt) / np.sum(dist * dist))
    if slope <= 0:
        return False
    v_impl = 1.0 / slope
    if not (v_band[0] <= v_impl <= v_band[1]):
        return False
    resid = dt - slope * dist
    rms = float(np.sqrt(np.mean(resid ** 2)))
    return rms <= tol_s
