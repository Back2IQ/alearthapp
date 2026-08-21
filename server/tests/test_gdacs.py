import base64
import json
from datetime import datetime, timezone
from pathlib import Path

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.adapters.gdacs import parse_gdacs_feed
from tda_server.alert.hazard_payload import build_hazard_payload
from tda_server.alert.payload import sign_payload, verify_payload
from tda_server.domain.hazards import (
    HAZARD_TYPES, HazardEvent, deserialize_hazard, hazard_confidence, serialize_hazard,
)

FIX = Path(__file__).parent / "fixtures" / "gdacs_events.json"
NOW = datetime(2026, 8, 21, 12, 0, 0, tzinfo=timezone.utc)


def keypair_b64() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    pr = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pu = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(pr).decode(), base64.b64encode(pu).decode()


def a_hazard(htype: str = "FL", level: str = "red") -> HazardEvent:
    return HazardEvent("gdacs", f"gdacs:{htype}:1:0", htype, level, 39.0, 35.0,
                       "Flood in Türkiye", "Turkey", 1755777600000, 1755781200000,
                       "https://gdacs.org/x")


def test_parse_real_feed():
    doc = json.loads(FIX.read_text(encoding="utf-8"))
    events = parse_gdacs_feed(doc, NOW)
    assert len(events) > 0
    for e in events:
        assert e.source == "gdacs"
        assert e.hazard_type in HAZARD_TYPES
        assert e.alert_level in {"green", "orange", "red"}
        assert e.event_ms > 0 and e.received_ms == int(NOW.timestamp() * 1000)
        assert -90 <= e.lat <= 90 and -180 <= e.lon <= 180


def test_malformed_feature_skipped_not_batch_abort():
    doc = {"features": [
        {"properties": {"eventtype": "FL", "eventid": 5, "alertlevel": "Red",
                        "fromdate": "2026-08-20T00:00:00", "name": "Flood"},
         "geometry": {"coordinates": [35.0, 39.0]}},
        {"properties": {"eventtype": "TC"}, "geometry": None},  # malformed
    ]}
    events = parse_gdacs_feed(doc, NOW)
    assert len(events) == 1 and events[0].hazard_type == "FL"


def test_hazard_confidence_mapping():
    assert hazard_confidence("red", "FL") == "push"
    assert hazard_confidence("orange", "TC") == "opt_in"
    assert hazard_confidence("green", "WF") == "map"
    # earthquakes are context only, never a second alarm
    assert hazard_confidence("red", "EQ") == "map"


def test_serialize_roundtrip():
    h = a_hazard()
    assert deserialize_hazard(serialize_hazard(h)) == h


def test_build_and_sign_hazard_payload():
    priv, pub = keypair_b64()
    p = build_hazard_payload(a_hazard("FL", "red"), now_ms=1755781200000)
    assert all(isinstance(v, str) for v in p.values())
    assert p["type"] == "hazard" and p["hazard"] == "FL" and p["conf"] == "push"
    assert len(str(p).encode()) < 1024
    signed = sign_payload(p, priv)
    assert verify_payload(signed, pub) is True
    signed["alert"] = "green"
    assert verify_payload(signed, pub) is False


def test_gdacs_earthquake_payload_is_context_not_push():
    p = build_hazard_payload(a_hazard("EQ", "red"), now_ms=0)
    assert p["conf"] == "map"
