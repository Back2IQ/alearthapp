import base64
from datetime import datetime, timezone

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.alert.payload import verify_payload
from tda_server.alert.publisher import FakeTransport, Publisher
from tda_server.domain.events import CanonicalEvent, SourceEvent
from tda_server.fusion.correlator import Transition


def keys() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    pr = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pu = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(pr).decode(), base64.b64encode(pu).decode()


def tr(mag: float) -> Transition:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    se = SourceEvent(source="emsc", source_event_id="e1", origin_time=t,
                     lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                     mag_type="ml", received_at=t)
    return Transition(CanonicalEvent.from_source(se), "new")


async def test_publishes_signed_payload_to_cell_topics():
    priv, pub = keys()
    ft = FakeTransport()
    n = await Publisher(ft, priv).publish(tr(5.5), now_ms=1)
    assert n > 0 and len(ft.sent) == n
    topic, data = ft.sent[0]
    assert topic.startswith("cell_c")
    assert verify_payload(data, pub) is True


async def test_below_min_mag_not_published():
    priv, _ = keys()
    ft = FakeTransport()
    assert await Publisher(ft, priv).publish(tr(3.5), now_ms=1) == 0
    assert ft.sent == []


async def test_same_event_version_published_once():
    priv, _ = keys()
    ft = FakeTransport()
    p = Publisher(ft, priv)
    t1 = tr(5.5)
    n1 = await p.publish(t1, now_ms=1)
    n2 = await p.publish(t1, now_ms=2)   # gleiche (id, version)
    assert n1 > 0 and n2 == 0
