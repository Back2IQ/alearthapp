from __future__ import annotations

import math

import numpy as np

SECONDS_PER_YEAR = 365.25 * 24 * 3600


def gpd_fit_mom(exceed: np.ndarray) -> tuple[float, float]:
    """Method-of-moments GPD fit over threshold exceedances.
    mean m = sigma/(1-xi); var v = sigma^2/((1-xi)^2 (1-2xi))."""
    m = float(np.mean(exceed))
    v = float(np.var(exceed))
    if v <= 0:
        return 0.0, max(m, 1e-9)
    xi = 0.5 * (1.0 - m * m / v)
    sigma = 0.5 * m * (m * m / v + 1.0)
    return xi, max(sigma, 1e-9)


def return_level(u: float, xi: float, sigma: float, zeta_u: float,
                 n_per_year: float, target_per_year: float = 1.0) -> float:
    """POT return level: value exceeded target_per_year times per year."""
    p = target_per_year / n_per_year          # per-observation exceedance prob
    ratio = p / zeta_u
    if abs(xi) < 1e-6:
        return u - sigma * math.log(ratio)
    return u + (sigma / xi) * (ratio ** (-xi) - 1.0)


def calibrate_threshold(scores: np.ndarray, *, eval_interval_s: float,
                        quantile: float = 0.95, target_per_year: float = 1.0) -> float:
    scores = np.asarray(scores, dtype=float)
    u = float(np.quantile(scores, quantile))
    exceed = scores[scores > u] - u
    if exceed.size < 50:
        # too few exceedances for a stable tail fit: fall back to a high quantile
        return float(np.quantile(scores, 1.0 - target_per_year /
                                 (SECONDS_PER_YEAR / eval_interval_s)))
    xi, sigma = gpd_fit_mom(exceed)
    zeta_u = exceed.size / scores.size
    n_per_year = SECONDS_PER_YEAR / eval_interval_s
    return return_level(u, xi, sigma, zeta_u, n_per_year, target_per_year)
