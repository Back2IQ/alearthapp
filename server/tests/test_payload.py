import base64
from datetime import datetime, timezone

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from alert2iq_server.alert.payload import build_payload, canonical_bytes, sign_payload, verify_payload
from alert2iq_server.domain.events import CanonicalEvent, EventState, SourceEvent
from alert2iq_server.fusion.correlator import Transition


def make_transition() -> Transition:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    se = SourceEvent(source="emsc", source_event_id="e1", origin_time=t,
                     lat=40.7123, lon=29.0567, depth_km=9.96, magnitude=5.55,
                     mag_type="ml", received_at=t)
    return Transition(CanonicalEvent.from_source(se), "new")


def make_confirmed_transition() -> Transition:
    """Two independent sources -> CONFIRMED state (Korrektur 1: tier P2)."""
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    se1 = SourceEvent(source="emsc", source_event_id="e1", origin_time=t,
                      lat=40.7123, lon=29.0567, depth_km=9.96, magnitude=5.55,
                      mag_type="ml", received_at=t)
    se2 = SourceEvent(source="usgs", source_event_id="u1", origin_time=t,
                      lat=40.7123, lon=29.0567, depth_km=9.96, magnitude=5.6,
                      mag_type="mb", received_at=t)
    ev = CanonicalEvent.from_source(se1)
    ev.merge(se2)
    ev.state = EventState.CONFIRMED
    ev.version = 2
    return Transition(ev, "confirm")


def keypair_b64() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    from cryptography.hazmat.primitives import serialization as ser
    priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(priv_raw).decode(), base64.b64encode(pub_raw).decode()


def test_payload_fields_are_strings_and_formatted():
    p = build_payload(make_transition(), now_ms=1787227205000)
    assert all(isinstance(v, str) for v in p.values())
    # 5.55 as an IEEE-754 double is 5.549999...; %.1f correctly rounds to 5.5.
    assert p["lat"] == "40.7123" and p["mag"] == "5.5" and p["test"] == "0"
    # 2026-08-20T12:00:00Z (see make_transition) in Unix ms.
    assert p["origin_ts"] == "1787227200000"
    assert len(str(p).encode()) < 1024


def test_single_source_event_gets_p1_tier():
    p = build_payload(make_transition(), now_ms=0)
    assert p["state"] == "detected"
    assert p["tier"] == "P1"


def test_confirmed_event_gets_p2_tier():
    p = build_payload(make_confirmed_transition(), now_ms=0)
    assert p["state"] == "confirmed"
    assert p["tier"] == "P2"


def test_canonical_bytes_sorted_and_excludes_sig():
    p = {"b": "2", "a": "1", "sig": "zzz"}
    assert canonical_bytes(p) == b"a=1\nb=2"


def test_sign_and_verify_roundtrip():
    priv_b64, pub_b64 = keypair_b64()
    p = sign_payload(build_payload(make_transition(), now_ms=0), priv_b64)
    assert verify_payload(p, pub_b64) is True
    p["mag"] = "9.9"
    assert verify_payload(p, pub_b64) is False
