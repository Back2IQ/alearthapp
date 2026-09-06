from alert2iq_server.p0a.discriminate import (
    SourceZone, in_source_zone, is_blast, is_teleseism, local_time_features,
)

# East Anatolian + North Anatolian fault boxes (coarse)
ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0), SourceZone(39.5, 41.5, 26.0, 42.0)]


def test_in_and_out_of_source_zone():
    assert in_source_zone(37.2, 37.0, ZONES) is True      # Kahramanmaras
    assert in_source_zone(10.0, 100.0, ZONES) is False    # far away

def test_teleseism_outside_zones():
    assert is_teleseism(10.0, 100.0, ZONES) is True
    assert is_teleseism(37.2, 37.0, ZONES) is False

def test_blast_needs_all_indicators():
    # shallow + daytime weekday + high P/S ratio -> blast
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=5.0,
                    local_hour=13, weekday=2) is True
    # deep or nighttime or low ratio -> not classified as blast
    assert is_blast(origin_ms=0, depth_km=10.0, ps_amp_ratio=5.0,
                    local_hour=13, weekday=2) is False
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=5.0,
                    local_hour=3, weekday=2) is False
    assert is_blast(origin_ms=0, depth_km=0.5, ps_amp_ratio=1.0,
                    local_hour=13, weekday=2) is False

def test_local_time_features_turkey_offset():
    # 1_755_691_200_000 ms = 2025-08-20 12:00:00 UTC -> 15:00 local (UTC+3),
    # which is a Wednesday (weekday=2). Plan comment/test-bug fix: the plan's
    # docstring said "Thursday(weekday=3)" but datetime.weekday() for this
    # timestamp is verifiably 2 (Wed) - corrected here, not silently.
    h, wd = local_time_features(1_755_691_200_000, tz_offset_h=3)
    assert h == 15 and wd == 2
