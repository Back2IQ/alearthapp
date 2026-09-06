import asyncio
from datetime import datetime, timezone

import fakeredis.aioredis

from alert2iq_server.domain.events import SourceEvent
from alert2iq_server.stream.base import InMemoryStream, deserialize_source_event, serialize_source_event
from alert2iq_server.stream.redis_stream import RedisStream


def sample() -> SourceEvent:
    t = datetime(2026, 8, 20, 12, 0, 0, tzinfo=timezone.utc)
    return SourceEvent(source="usgs", source_event_id="u1", origin_time=t,
                       lat=38.1, lon=27.2, depth_km=None, magnitude=4.4,
                       mag_type="mb", received_at=t)


def test_serialize_roundtrip_none_depth():
    se = sample()
    assert deserialize_source_event(serialize_source_event(se)) == se


async def test_inmemory_append_then_read():
    s = InMemoryStream()
    await s.append({"a": "1"})
    it = s.read()
    sid, rec = await asyncio.wait_for(anext(it), timeout=1)
    assert rec == {"a": "1"} and sid


async def test_redis_stream_roundtrip():
    r = fakeredis.aioredis.FakeRedis(decode_responses=True)
    s = RedisStream.from_client(r)
    await s.append(serialize_source_event(sample()))
    sid, rec = await asyncio.wait_for(anext(s.read()), timeout=2)
    assert deserialize_source_event(rec) == sample()
