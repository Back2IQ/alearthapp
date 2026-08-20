from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import AsyncIterator, Protocol

from tda_server.domain.events import SourceEvent


class EventStream(Protocol):
    async def append(self, record: dict[str, str]) -> None: ...
    def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]: ...


def serialize_source_event(se: SourceEvent) -> dict[str, str]:
    return {
        "source": se.source,
        "source_event_id": se.source_event_id,
        "origin_ts": str(int(se.origin_time.timestamp() * 1000)),
        "lat": repr(se.lat),
        "lon": repr(se.lon),
        "depth_km": "" if se.depth_km is None else repr(se.depth_km),
        "magnitude": repr(se.magnitude),
        "mag_type": se.mag_type,
        "received_ts": str(int(se.received_at.timestamp() * 1000)),
    }


def deserialize_source_event(d: dict[str, str]) -> SourceEvent:
    def ts(ms: str) -> datetime:
        return datetime.fromtimestamp(int(ms) / 1000, tz=timezone.utc)

    return SourceEvent(
        source=d["source"],
        source_event_id=d["source_event_id"],
        origin_time=ts(d["origin_ts"]),
        lat=float(d["lat"]),
        lon=float(d["lon"]),
        depth_km=None if d["depth_km"] == "" else float(d["depth_km"]),
        magnitude=float(d["magnitude"]),
        mag_type=d["mag_type"],
        received_at=ts(d["received_ts"]),
    )


class InMemoryStream:
    def __init__(self) -> None:
        self._items: list[tuple[str, dict[str, str]]] = []
        self._new = asyncio.Event()

    async def append(self, record: dict[str, str]) -> None:
        self._items.append((str(len(self._items) + 1), dict(record)))
        self._new.set()

    async def read(self, start_id: str = "0") -> AsyncIterator[tuple[str, dict[str, str]]]:
        idx = int(start_id)
        while True:
            while idx < len(self._items):
                yield self._items[idx]
                idx += 1
            self._new.clear()
            await self._new.wait()
