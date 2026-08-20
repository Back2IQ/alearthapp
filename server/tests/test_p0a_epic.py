from tda_server.geo.cells import haversine_km
from tda_server.p0a.epic import StationPick, epic_alarm, travel_time_rms


def pick(st, lat, lon, origin=(39.0, 40.0), t0=1_000_000, vp=6.0, jitter_ms=0):
    d = haversine_km(lat, lon, *origin)
    return StationPick(st, "P", t0 + int(1000 * d / vp) + jitter_ms, lat, lon, 0.8)


NEAR = {"S1", "S2", "S3", "S4", "S5"}


def test_alarm_when_all_criteria_met():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(sorted(NEAR))]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is True

def test_no_alarm_too_few_stations():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(["S1", "S2", "S3"])]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_no_alarm_below_near_fraction():
    # 4 far stations trigger but none of the 5 near stations -> fails 40% rule
    far = {"F1", "F2", "F3", "F4"}
    picks = [pick(s, 45.0 + i * 0.1, 50.0) for i, s in enumerate(sorted(far))]
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_no_alarm_high_rms():
    picks = [pick(s, 39.0 + i * 0.1, 40.0, jitter_ms=5000 * (i % 2))
             for i, s in enumerate(sorted(NEAR))]  # large inconsistent residuals
    assert epic_alarm(picks, NEAR, origin_lat=39.0, origin_lon=40.0,
                      origin_ms=1_000_000) is False

def test_rms_zero_for_perfect_picks():
    picks = [pick(s, 39.0 + i * 0.1, 40.0) for i, s in enumerate(sorted(NEAR))]
    assert travel_time_rms(picks, 39.0, 40.0, 1_000_000, 6.0) < 0.05
