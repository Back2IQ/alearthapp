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
    (fireworks, cheering) shows ~0 delay regardless of distance."""
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
