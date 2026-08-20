import base64
from datetime import datetime, timezone

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.alert.publisher import FakeTransport, Publisher
from tda_server.domain.events import SourceEvent
from tda_server.fusion.correlator import Correlator
from tda_server.pipeline import run_consumer
from tda_server.stream.base import InMemoryStream, serialize_source_event


def priv_b64() -> str:
    k = Ed25519PrivateKey.generate()
    raw = k.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    return base64.b64encode(raw).decode()


def se(source: str, eid: str, mag: float) -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source=source, source_event_id=eid, origin_time=t,
                       lat=40.7, lon=29.1, depth_km=10.0, magnitude=mag,
                       mag_type="ml", received_at=t)


async def test_stream_to_publish_end_to_end():
    stream = InMemoryStream()
    await stream.append(serialize_source_event(se("emsc", "e1", 5.4)))
    await stream.append(serialize_source_event(se("usgs", "u1", 5.5)))
    ft = FakeTransport()
    n = await run_consumer(stream, Correlator(), Publisher(ft, priv_b64()),
                           stop_after=2)
    assert n == 2
    # "new" (v1) und "confirm" (v2) wurden publiziert
    versions = {d["ver"] for _, d in ft.sent}
    assert versions == {"1", "2"}
    states = {d["state"] for _, d in ft.sent}
    assert "confirmed" in states
