import math
from alert2iq_server.domain.polarization import evaluate_polarization


def test_vertical_p_wave_vector_detected():
    res = evaluate_polarization(ax=0.2, ay=0.2, az=14.5, sta_lta=5.5, gravity_norm=9.81)
    assert res.is_p_wave is True
    assert res.confidence_score > 0.0
    assert res.dip_angle_deg < 45.0


def test_horizontal_table_bump_rejected():
    res = evaluate_polarization(ax=12.0, ay=10.0, az=9.81, sta_lta=5.5, gravity_norm=9.81)
    assert res.is_p_wave is False
    assert res.confidence_score == 0.0


def test_low_sta_lta_ratio_rejected():
    res = evaluate_polarization(ax=0.1, ay=0.1, az=10.0, sta_lta=2.0, gravity_norm=9.81)
    assert res.is_p_wave is False
