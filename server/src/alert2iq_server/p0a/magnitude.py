from __future__ import annotations

import math

import numpy as np

# Wu & Zhao 2006 Pd attenuation coefficients; Wu et al. 2007 tau_c.
# Module-level so Turkish recalibration (Task 11) can override them.
PD_A = -3.463
PD_B = 0.729
PD_C = -1.374
TAUC_A = 4.218
TAUC_B = 6.166
DAMAGING_PD_CM = 0.5


def pd_from_displacement(disp_cm: np.ndarray) -> float:
    return float(np.max(np.abs(disp_cm)))


def magnitude_from_pd(pd_cm: float, r_km: float) -> float:
    """Invert log10(Pd) = PD_A + PD_B*M + PD_C*log10(R) for M."""
    return (math.log10(pd_cm) - PD_A - PD_C * math.log10(r_km)) / PD_B


def tauc_magnitude(tauc_s: float) -> float:
    return TAUC_A * math.log10(tauc_s) + TAUC_B


def combined_magnitude(pd_cm: float, r_km: float,
                       tauc_s: float | None) -> tuple[float, float, float]:
    m_pd = magnitude_from_pd(pd_cm, r_km)
    if tauc_s is None:
        return m_pd, m_pd, m_pd
    m_tc = tauc_magnitude(tauc_s)
    m = (m_pd + m_tc) / 2.0
    return m, min(m_pd, m_tc), max(m_pd, m_tc)


def is_damaging_pd(pd_cm: float) -> bool:
    return pd_cm > DAMAGING_PD_CM
