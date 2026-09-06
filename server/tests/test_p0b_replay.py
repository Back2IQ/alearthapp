from alert2iq_server.p0b.background import BackgroundModel
from alert2iq_server.p0b.detector import build_p0b_detector
from alert2iq_server.p0b.replay import generate_scenario, replay_scenario


def factory(nu: int):
    return lambda: build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                                      nu_per_cell=max(nu // 20, 5),
                                      threshold_h=3.0, attn_h=1.0)


def test_dense_network_detects_within_12s():
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=7.8, n_devices=400,
                             report_fraction=0.3, t0_ms=1_000_000, seed=7)
    res = replay_scenario(scen, factory(400))
    assert res["detected"] is True
    assert res["detect_latency_s"] is not None and res["detect_latency_s"] <= 12.0
    assert res["false_before_origin"] == 0
    assert res["origin_error_km"] < 60.0

def test_quiet_scenario_produces_no_false_alarm():
    # background-only scenario: report_fraction 0 -> no earthquake front
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=0.0, n_devices=400,
                             report_fraction=0.0, t0_ms=1_000_000, seed=3)
    res = replay_scenario(scen, factory(400))
    assert res["detected"] is False
