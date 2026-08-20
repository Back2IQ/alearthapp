from datetime import datetime, timezone
from tda_server.alert.payload import build_payload, derive_tier
from tda_server.domain.events import CanonicalEvent, EventState, SourceEvent
from tda_server.fusion.correlator import Transition


def se(source: str, mag: float) -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source, f"{source}1", t, 41.0, 29.0, 10.0, mag, "ml", t)


def test_p0b_only_is_tier_p0():
    ev = CanonicalEvent.from_source(se("p0b", 5.0))
    assert derive_tier(ev) == "P0"
    p = build_payload(Transition(ev, "new"), now_ms=0)
    assert p["tier"] == "P0"

def test_single_catalog_is_p1():
    ev = CanonicalEvent.from_source(se("emsc", 5.0))
    assert derive_tier(ev) == "P1"

def test_confirmed_is_p2():
    ev = CanonicalEvent.from_source(se("p0b", 5.0))
    ev.merge(se("emsc", 5.1))
    ev.state = EventState.CONFIRMED
    assert derive_tier(ev) == "P2"
    p = build_payload(Transition(ev, "confirm"), now_ms=0)
    assert p["tier"] == "P2"
