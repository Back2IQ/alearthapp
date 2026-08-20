from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from tda_server.p0a import magnitude


def fit_pd_mw(log_pd: np.ndarray, log_r: np.ndarray,
              mw: np.ndarray) -> tuple[float, float, float]:
    """Least-squares fit of log10(Pd) = a + b*Mw + c*log10(R)."""
    x = np.column_stack([np.ones_like(mw, dtype=float), mw.astype(float),
                         log_r.astype(float)])
    coef, *_ = np.linalg.lstsq(x, log_pd.astype(float), rcond=None)
    return float(coef[0]), float(coef[1]), float(coef[2])


@dataclass(frozen=True)
class PdMwModel:
    a: float
    b: float
    c: float

    def magnitude(self, pd_cm: float, r_km: float) -> float:
        return (math.log10(pd_cm) - self.a - self.c * math.log10(r_km)) / self.b


def apply_to_magnitude_module(model: PdMwModel) -> None:
    """Replace the California-default Pd coefficients with regional Turkish ones.
    Mandatory calibration step; also the shared calibration anchor for P0b."""
    magnitude.PD_A = model.a
    magnitude.PD_B = model.b
    magnitude.PD_C = model.c
