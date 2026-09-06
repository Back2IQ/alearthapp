from datetime import datetime, timedelta, timezone

from alert2iq_server.domain.events import EventState, SourceEvent
from alert2iq_server.fusion.correlator import Correlator

T0 = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)


def se(source: str, eid: str, mag: float, dt_s: float = 0.0,
       lat: float = 40.7, lon: float = 29.1) -> SourceEvent:
    t = T0 + timedelta(seconds=dt_s)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=lat, lon=lon, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)


def test_new_event_yields_new_transition():
    c = Correlator()
    tr = c.ingest(se("emsc", "e1", 5.2))
    assert tr is not None and tr.kind == "new"
    assert tr.event.version == 1


def test_second_source_confirms_and_bumps_version():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("usgs", "u9", 5.3, dt_s=20))
    assert tr is not None and tr.kind == "confirm"
    assert tr.event.state is EventState.CONFIRMED
    assert tr.event.version == 2
    assert set(tr.event.sources) == {"emsc", "usgs"}


def test_escalation_transition_on_big_jump():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.0))
    c.ingest(se("usgs", "u9", 5.1, dt_s=10))      # confirm
    tr = c.ingest(se("afad", "a3", 6.0, dt_s=30)) # +0.9 -> escalate
    assert tr is not None and tr.kind == "escalate"
    assert tr.event.magnitude == 6.0


def test_far_event_is_separate():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("usgs", "u9", 5.2, lat=36.0, lon=36.0))
    assert tr is not None and tr.kind == "new"


def test_same_source_update_no_duplicate_event():
    c = Correlator()
    c.ingest(se("emsc", "e1", 5.2))
    tr = c.ingest(se("emsc", "e1", 5.25, dt_s=60))
    assert tr is None                # kleines Update, nichts zu publizieren
    assert len(c.events) == 1
