from __future__ import annotations

from dataclasses import dataclass

from alert2iq_server.geo.cells import affected_cells


@dataclass(frozen=True)
class IntensityObs:
    lat: float
    lon: float
    mmi: float


def plum_cells(observations: list[IntensityObs], *, radius_km: float = 30.0,
               min_mmi: float = 2.5, site_factor: float = 0.0) -> dict[str, float]:
    """PLUM (Kodera 2018): project each observed intensity >= min_mmi
    unattenuated onto all cells within radius_km; per cell keep the max."""
    out: dict[str, float] = {}
    for obs in observations:
        if obs.mmi < min_mmi:
            continue
        value = obs.mmi + site_factor
        for cell in affected_cells(obs.lat, obs.lon, radius_km):
            if value > out.get(cell, float("-inf")):
                out[cell] = value
    return out


def plum_triggered(observations: list[IntensityObs], *, radius_km: float = 30.0,
                   min_mmi: float = 2.5) -> bool:
    return any(o.mmi >= min_mmi for o in observations)
