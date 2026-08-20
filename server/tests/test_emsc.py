import json
from datetime import datetime, timezone
from pathlib import Path

from tda_server.adapters.emsc import parse_emsc_message

FIX = Path(__file__).parent / "fixtures" / "emsc_create.json"
NOW = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)


def test_parse_real_create_message():
    se = parse_emsc_message(FIX.read_text(encoding="utf-8"), NOW)
    assert se is not None
    assert se.source == "emsc"
    raw = json.loads(FIX.read_text(encoding="utf-8"))["data"]["properties"]
    assert se.source_event_id == str(raw["unid"])
    assert se.magnitude == float(raw["mag"])
    assert se.received_at == NOW


def test_parse_garbage_returns_none():
    assert parse_emsc_message("not json", NOW) is None
    assert parse_emsc_message('{"action":"ping"}', NOW) is None


def test_delete_action_is_rejected_even_with_full_properties():
    raw = json.loads(FIX.read_text(encoding="utf-8"))
    raw["action"] = "delete"
    assert parse_emsc_message(json.dumps(raw), NOW) is None


def test_create_action_still_accepted():
    raw = json.loads(FIX.read_text(encoding="utf-8"))
    assert raw["action"] == "create"
    assert parse_emsc_message(json.dumps(raw), NOW) is not None
