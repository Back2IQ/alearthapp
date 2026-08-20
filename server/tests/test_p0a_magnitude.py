import math
import numpy as np
from tda_server.p0a.magnitude import (
    combined_magnitude, is_damaging_pd, magnitude_from_pd,
    pd_from_displacement, tauc_magnitude,
)


def test_pd_from_displacement_is_peak_abs():
    disp = np.array([0.0, -0.4, 0.2, -0.1])
    assert pd_from_displacement(disp) == 0.4

def test_magnitude_from_pd_inverts_wu_zhao():
    # forward: log10(Pd) = -3.463 + 0.729*M - 1.374*log10(R)
    m_true, r = 6.0, 50.0
    log_pd = -3.463 + 0.729 * m_true - 1.374 * math.log10(r)
    pd = 10 ** log_pd
    assert abs(magnitude_from_pd(pd, r) - m_true) < 1e-6

def test_tauc_magnitude_wu2007():
    # tauc=1s -> M = 6.166; tauc=10 -> M = 4.218+6.166
    assert abs(tauc_magnitude(1.0) - 6.166) < 1e-9
    assert abs(tauc_magnitude(10.0) - (4.218 + 6.166)) < 1e-9

def test_combined_averages_and_brackets():
    m, lo, hi = combined_magnitude(pd_cm=0.6, r_km=40.0, tauc_s=1.2)
    assert lo <= m <= hi and hi > lo

def test_combined_pd_only_when_no_tauc():
    m, lo, hi = combined_magnitude(pd_cm=0.6, r_km=40.0, tauc_s=None)
    assert abs(m - magnitude_from_pd(0.6, 40.0)) < 1e-9

def test_is_damaging_threshold():
    assert is_damaging_pd(0.6) is True
    assert is_damaging_pd(0.4) is False
