import numpy as np
from alert2iq_server.p0a import magnitude
from alert2iq_server.p0a.recalibrate import PdMwModel, apply_to_magnitude_module, fit_pd_mw


def test_fit_recovers_known_relation():
    rng = np.random.default_rng(0)
    a_t, b_t, c_t = -3.2, 0.75, -1.3
    mw = rng.uniform(4, 7, 500)
    log_r = np.log10(rng.uniform(10, 200, 500))
    log_pd = a_t + b_t * mw + c_t * log_r + rng.normal(0, 0.01, 500)
    a, b, c = fit_pd_mw(log_pd, log_r, mw)
    assert abs(a - a_t) < 0.1 and abs(b - b_t) < 0.05 and abs(c - c_t) < 0.05

def test_model_magnitude_inverts_fit():
    m = PdMwModel(a=-3.2, b=0.75, c=-1.3)
    # forward then invert should round-trip
    mw, r = 6.0, 50.0
    log_pd = -3.2 + 0.75 * mw - 1.3 * np.log10(r)
    assert abs(m.magnitude(10 ** log_pd, r) - mw) < 1e-6

def test_apply_overrides_module_constants():
    orig = (magnitude.PD_A, magnitude.PD_B, magnitude.PD_C)
    try:
        apply_to_magnitude_module(PdMwModel(a=-3.2, b=0.75, c=-1.3))
        assert magnitude.PD_A == -3.2 and magnitude.PD_B == 0.75
    finally:
        magnitude.PD_A, magnitude.PD_B, magnitude.PD_C = orig
