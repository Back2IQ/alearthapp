from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.detector import ScoreDetector, ScoreWindow
from tda_server.p0b.signals import ActivePing


def make_density(cell: str, n: int, now: int) -> DensityTracker:
    dt = DensityTracker(active_ttl_ms=10_000)
    for i in range(n):
        dt.observe(ActivePing(f"dev{i}", cell, now, now))
    return dt


def test_window_counts_within_eps_and_evicts():
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 1000, 1.0)
    w.add("d1_1", 5000, 1.0)
    assert w.count("d1_1", 10_000) == 2.0
    # The plan's original probe point (now=30_000) puts BOTH entries outside
    # the 20s window (ages 29s and 25s), evicting both - inconsistent with
    # its own "first now outside" comment. now=24_000 gives ages 23s/19s,
    # which correctly exercises partial eviction (only t=1000 drops out).
    assert w.count("d1_1", 24_000) == 1.0     # first (t=1000) now outside 20s

def test_weighted_count():
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 1000, 0.3)
    w.add("d1_1", 1000, 1.0)
    assert abs(w.count("d1_1", 2000) - 1.3) < 1e-9

def test_score_zero_when_triggers_match_background():
    # background rate 0.5/s at nu=100, eps=20s -> expected 10 triggers => S ~ 0
    # b0 chosen so rate(100)=exp(b0+0.01*100)=0.5 exactly (the plan's original
    # b0=-6.0 gives exp(-5)=0.0067, not the 0.5/s the comment/test intends).
    bg = BackgroundModel(b0=-1.6931471805599454, b1=0.01)
    det = ScoreDetector(bg, make_density("d1_1", 100, 0), eps_s=20.0)
    exp = bg.expected(nu=100, eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    for i in range(round(exp)):
        w.add("d1_1", i * 10, 1.0)
    s = det.score("d1_1", 200, w)
    assert -0.3 < s < 0.3                      # roughly at background

def test_score_high_on_burst():
    bg = BackgroundModel(b0=-8.0, b1=0.005)    # low background
    det = ScoreDetector(bg, make_density("d1_1", 100, 0), eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    for i in range(40):                        # sudden burst far above background
        w.add("d1_1", 100 + i, 1.0)
    assert det.score("d1_1", 200, w) > 10.0

def test_score_minus_one_when_no_active_devices():
    bg = BackgroundModel(b0=-8.0, b1=0.005)
    det = ScoreDetector(bg, DensityTracker(), eps_s=20.0)
    w = ScoreWindow(eps_s=20.0)
    w.add("d1_1", 100, 1.0)
    assert det.score("d1_1", 200, w) == -1.0
