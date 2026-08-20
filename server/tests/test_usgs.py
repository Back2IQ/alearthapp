import json
from datetime import datetime, timezone
from pathlib import Path

from tda_server.adapters.usgs import parse_usgs_feed

FIX = Path(__file__).parent / "fixtures" / "usgs_all_hour.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)


def test_parse_real_feed():
    doc = json.loads(FIX.read_text(encoding="utf-8"))
    events = parse_usgs_feed(doc, NOW)
    assert len(events) == len(doc["features"])
    first, raw = events[0], doc["features"][0]
    assert first.source == "usgs"
    assert first.source_event_id.startswith(raw["id"])
    assert first.lat == raw["geometry"]["coordinates"][1]
    assert first.origin_time.tzinfo is not None


def test_feature_without_mag_is_skipped():
    doc = {"features": [{"id": "x", "properties": {"time": 0, "mag": None,
                                                   "magType": None, "updated": 1},
                         "geometry": {"coordinates": [1.0, 2.0, 3.0]}}]}
    assert parse_usgs_feed(doc, NOW) == []
