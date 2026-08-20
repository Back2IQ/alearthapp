from tda_server.p0b.signals import (
    ActivePing, PhoneTrigger, coarsen_cell, detect_cell_center,
    deserialize_ping, deserialize_trigger, serialize_ping, serialize_trigger,
)

def test_coarsen_cell_hides_raw_position():
    # two nearby raw positions collapse into the same 0.1-degree detection cell
    assert coarsen_cell(41.02, 28.97) == coarsen_cell(41.08, 28.93)
    assert coarsen_cell(41.02, 28.97) == "d410_289"

def test_detect_cell_center_roundtrips_into_cell():
    lat, lon = detect_cell_center("d410_289")
    assert coarsen_cell(lat, lon) == "d410_289"
    assert 41.0 <= lat < 41.1 and 28.9 <= lon < 29.0

def test_trigger_is_frozen():
    t = PhoneTrigger("dev1", "d410_289", 1000, 50, 1200, True)
    try:
        t.trigger_ms = 2000  # type: ignore[misc]
        assert False, "should be frozen"
    except AttributeError:
        pass

def test_trigger_roundtrip_all_strings():
    t = PhoneTrigger("devA", "d410_289", 1755691200000, 40, 1755691200300, True)
    d = serialize_trigger(t)
    assert all(isinstance(v, str) for v in d.values())
    assert deserialize_trigger(d) == t

def test_ping_roundtrip():
    p = ActivePing("devA", "d410_289", 1755691200000, 1755691200100)
    assert deserialize_ping(serialize_ping(p)) == p
