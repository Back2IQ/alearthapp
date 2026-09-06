from __future__ import annotations

from typing import AsyncIterator

import redis.asyncio as aioredis


class RedisStream:
    def __init__(self, url: str, key: str = "tda:source_events") -> None:
        self._r = aioredis.from_url(url, decode_responses=True)
        self.key = key

    @classmethod
    def from_client(cls, client, key: str = "tda:source_events") -> "RedisStream":
        obj = cls.__new__(cls)
        obj._r = client
        obj.key = key
        return obj

    async def append(self, record: dict[str, str]) -> None:
        await self._r.xadd(self.key, record)

    async def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]:
        last = start_id
        while True:
            resp = await self._r.xread({self.key: last}, block=1000, count=100)
            for _key, entries in resp or []:
                for sid, rec in entries:
                    yield sid, rec
                    last = sid
