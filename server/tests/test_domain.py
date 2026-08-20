from datetime import datetime, timezone

from tda_server.domain.events import CanonicalEvent, EventState, SourceEvent


def se(source: str, mag: float, eid: str = "x1") -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)


def test_source_event_is_frozen():
    ev = se("emsc", 5.0)
    try:
        ev.magnitude = 6.0  # type: ignore[misc]
        assert False, "should be frozen"
    except AttributeError:
        pass


def test_canonical_from_source():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    assert c.state is EventState.DETECTED
    assert c.magnitude == 5.0 and c.mag_low == 5.0 and c.mag_high == 5.0
    assert c.version == 1 and "emsc" in c.sources


def test_merge_escalates_magnitude_to_max():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    changed = c.merge(se("usgs", 5.6))
    assert changed is True
    assert c.magnitude == 5.6          # escalate fast: max gewinnt
    assert (c.mag_low, c.mag_high) == (5.0, 5.6)


def test_merge_lower_magnitude_does_not_deescalate():
    c = CanonicalEvent.from_source(se("emsc", 5.6))
    changed = c.merge(se("usgs", 5.1))
    assert c.magnitude == 5.6          # de-escalate slow: Maximum bleibt
    assert c.mag_low == 5.1
    assert changed is False            # kein publikationswürdiger Sprung


def test_merge_small_change_not_flagged():
    c = CanonicalEvent.from_source(se("emsc", 5.0))
    assert c.merge(se("usgs", 5.1)) is False   # < 0.2 Differenz


def test_merge_exact_delta_escalates_despite_float_rounding():
    # 2.3 - 2.1 == 0.19999999999999973 in binary float, must still count
    # as an escalation-worthy 0.2 jump.
    c = CanonicalEvent.from_source(se("emsc", 2.1))
    assert c.merge(se("usgs", 2.3)) is True


def test_merge_below_delta_still_not_flagged():
    c = CanonicalEvent.from_source(se("emsc", 2.1))
    assert c.merge(se("usgs", 2.2)) is False   # 0.1 Differenz bleibt False
