from tda_server.p0a.epic import StationPick
from tda_server.p0a.picker import dedupe_picks


def p(st, phase, t, pd=0.5):
    return StationPick(st, phase, t, 39.0, 40.0, pd)


def test_merges_duplicate_picks_within_window():
    picks = [p("S1", "P", 1000, 0.3), p("S1", "P", 1300, 0.6)]
    out = dedupe_picks(picks, merge_window_ms=500)
    assert len(out) == 1
    assert out[0].time_ms == 1000 and out[0].pd_cm == 0.6   # earlier time, higher pd

def test_keeps_distinct_phases_and_stations():
    picks = [p("S1", "P", 1000), p("S1", "S", 1200), p("S2", "P", 1050)]
    assert len(dedupe_picks(picks, merge_window_ms=500)) == 3

def test_keeps_picks_outside_window():
    picks = [p("S1", "P", 1000), p("S1", "P", 3000)]   # 2s apart: distinct events
    assert len(dedupe_picks(picks, merge_window_ms=500)) == 2
