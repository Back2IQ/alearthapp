from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

import httpx

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
USGS_FEED_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson"


def parse_usgs_feed(doc: dict, received_at: datetime) -> list[SourceEvent]:
    out: list[SourceEvent] = []
    for feat in doc.get("features", []):
        try:
            props = feat.get("properties", {}) or {}
            coords = (feat.get("geometry") or {}).get("coordinates", [None, None, None])
            if props.get("mag") is None or props.get("time") is None:
                continue
            # updated-timestamp in the id makes revisions distinct source events
            out.append(SourceEvent(
                source="usgs",
                source_event_id=f'{feat["id"]}:{props.get("updated", 0)}',
                origin_time=datetime.fromtimestamp(props["time"] / 1000, tz=timezone.utc),
                lat=float(coords[1]),
                lon=float(coords[0]),
                depth_km=None if coords[2] is None else float(coords[2]),
                magnitude=float(props["mag"]),
                mag_type=str(props.get("magType") or ""),
                received_at=received_at,
            ))
        except (KeyError, TypeError, ValueError):
            # one malformed feature must not take down the whole poll batch
            continue
    return out


async def run_usgs(stream: EventStream, url: str = USGS_FEED_URL,
                   interval_s: float = 60.0, seen: set[str] | None = None) -> None:
    seen = set() if seen is None else seen
    async with httpx.AsyncClient(timeout=20) as client:
        while True:
            try:
                resp = await client.get(url)
                resp.raise_for_status()
                for se in parse_usgs_feed(resp.json(), datetime.now(timezone.utc)):
                    if se.source_event_id not in seen:
                        seen.add(se.source_event_id)
                        await stream.append(serialize_source_event(se))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("usgs poll failed: %s", exc)
            await asyncio.sleep(interval_s)
