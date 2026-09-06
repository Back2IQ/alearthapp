import json
from datetime import datetime, timezone
from pathlib import Path

from alert2iq_server.adapters.afad import _remember_seen, parse_afad_response

FIX = Path(__file__).parent / "fixtures" / "afad_filter.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)


def test_parse_real_response():
    items = json.loads(FIX.read_text(encoding="utf-8"))
    events = parse_afad_response(items, NOW)
    assert len(events) == len(items) and len(events) > 0
    first = events[0]
    assert first.source == "afad"
    assert first.origin_time.tzinfo is not None
    assert 25.0 < first.lon < 46.0 and 34.0 < first.lat < 43.5  # Türkei-Region


def test_item_missing_fields_skipped():
    assert parse_afad_response([{"eventID": "1"}], NOW) == []


def test_offsetless_date_is_interpreted_as_trt_not_utc():
    # AFAD's real feed sends offsetless local time (TRT = UTC+3, no DST).
    # See tests/fixtures/afad_filter.json, e.g. "date":"2026-08-19T02:38:36".
    item = {
        "eventID": "725942",
        "latitude": "37.15033",
        "longitude": "30.25883",
        "depth": "71.26",
        "type": "ML",
        "magnitude": "2",
        "date": "2026-08-19T02:38:36",
    }
    events = parse_afad_response([item], NOW)
    assert len(events) == 1
    assert events[0].origin_time == datetime(2026, 8, 18, 23, 38, 36, tzinfo=timezone.utc)


def test_remember_seen_caps_unbounded_growth():
    seen: set[str] = {f"id{i}" for i in range(10_001)}  # already past the cap
    is_new = _remember_seen(seen, "fresh-id", cap=10_000)
    assert is_new is True
    assert "fresh-id" in seen
    assert len(seen) < 10_001


def test_remember_seen_still_dedupes_within_window():
    seen: set[str] = set()
    assert _remember_seen(seen, "a", cap=10_000) is True
    assert _remember_seen(seen, "a", cap=10_000) is False
