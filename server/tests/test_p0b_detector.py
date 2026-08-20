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


from datetime import timezone
from tda_server.domain.events import SourceEvent
from tda_server.p0b.detector import P0bDetector, build_p0b_detector
from tda_server.p0b.signals import PhoneTrigger, coarsen_cell


def burst_triggers(origin=(41.0, 29.0), n_cells=5, t0=100_000, v_kms=3.5):
    from tda_server.geo.cells import haversine_km
    from tda_server.p0b.signals import detect_cell_center
    ocell = coarsen_cell(*origin)
    ola, olo = detect_cell_center(ocell)
    trigs = []
    for k in range(n_cells):
        lat = origin[0] + 0.1 * k
        cell = coarsen_cell(lat, origin[1])
        la, lo = detect_cell_center(cell)
        d = haversine_km(la, lo, ola, olo)
        tms = t0 + int(1000 * d / v_kms)
        for j in range(8):               # several devices per cell
            trigs.append(PhoneTrigger(f"dev{k}_{j}", cell, tms + j, 40, tms + 100, True))
    return trigs


def test_detector_emits_source_event_on_consistent_burst():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    trigs = burst_triggers()
    for t in trigs:
        det.observe_trigger(t)
    ev, attn = det.evaluate(now_ms=max(t.trigger_ms for t in trigs) + 500)
    assert isinstance(ev, SourceEvent)
    assert ev.source == "p0b" and ev.mag_type == "p0b_proxy"
    assert ev.origin_time.tzinfo is timezone.utc or ev.origin_time.tzinfo is not None
    assert 40.9 < ev.lat < 41.6

def test_citywide_simultaneous_does_not_emit():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    # every cell triggers at the same instant: high score but no valid front
    for k in range(5):
        cell = coarsen_cell(41.0 + 0.1 * k, 29.0)
        for j in range(8):
            det.observe_trigger(PhoneTrigger(f"d{k}_{j}", cell, 100_000 + j, 40,
                                             100_100, True))
    ev, _attn = det.evaluate(now_ms=101_000)
    assert ev is None

def test_attention_signal_below_alarm():
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=100, threshold_h=50.0, attn_h=1.0)
    trigs = burst_triggers(n_cells=2)     # weak: raises attention, not alarm
    for t in trigs:
        det.observe_trigger(t)
    ev, attn = det.evaluate(now_ms=max(t.trigger_ms for t in trigs) + 500)
    assert ev is None and attn is not None and attn.level >= 1.0
