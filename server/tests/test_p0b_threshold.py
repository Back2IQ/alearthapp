import numpy as np
from alert2iq_server.p0b.threshold import calibrate_threshold, gpd_fit_mom, return_level


def test_gpd_fit_recovers_scale_for_exponential_tail():
    # exponential exceedances => GPD with xi≈0, sigma≈scale
    rng = np.random.default_rng(0)
    exceed = rng.exponential(scale=2.0, size=50_000)
    xi, sigma = gpd_fit_mom(exceed)
    assert abs(xi) < 0.1
    assert abs(sigma - 2.0) < 0.2

def test_return_level_monotone_in_rarity():
    h_common = return_level(1.0, xi=0.1, sigma=2.0, zeta_u=0.05,
                            n_per_year=1_000_000, target_per_year=100)
    h_rare = return_level(1.0, xi=0.1, sigma=2.0, zeta_u=0.05,
                          n_per_year=1_000_000, target_per_year=1)
    assert h_rare > h_common               # 1/yr threshold higher than 100/yr

def test_calibrate_threshold_gives_rare_level():
    rng = np.random.default_rng(1)
    # 1 sample/s of quiet-time scores over a simulated ~11.6 day window
    scores = rng.exponential(scale=1.0, size=1_000_000) - 1.0
    h = calibrate_threshold(scores, eval_interval_s=1.0, quantile=0.95,
                            target_per_year=1.0)
    # empirically almost no quiet-time score should exceed h
    assert (scores > h).sum() <= 5
    assert h > np.quantile(scores, 0.99)

def test_calibrate_threshold_n_parallel_cells_raises_threshold():
    # FUND 4: calibrate_threshold's target_per_year is a PER-CELL rate; a
    # real deployment scores thousands of cells per tick, so the systemwide
    # false-alarm rate is ~n_parallel_cells x the per-cell rate. Passing the
    # number of concurrently-scored cells must tighten (raise) h so the
    # SYSTEMWIDE rate, not just the per-cell rate, stays near target_per_year.
    rng = np.random.default_rng(2)
    scores = rng.exponential(scale=1.0, size=1_000_000) - 1.0
    h_single_cell = calibrate_threshold(scores, eval_interval_s=1.0, quantile=0.95,
                                        target_per_year=1.0, n_parallel_cells=1)
    h_many_cells = calibrate_threshold(scores, eval_interval_s=1.0, quantile=0.95,
                                       target_per_year=1.0, n_parallel_cells=10_000)
    assert h_many_cells > h_single_cell
