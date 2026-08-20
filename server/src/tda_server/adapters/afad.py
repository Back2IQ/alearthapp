from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone

import httpx

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
AFAD_URL = "https://deprem.afad.gov.tr/apiv2/event/filter"


def parse_afad_response(items: list[dict], received_at: datetime) -> list[SourceEvent]:
    out: list[SourceEvent] = []
    for it in items:
        try:
            raw_date = str(it["date"])
            origin = datetime.fromisoformat(raw_date.replace("Z", "+00:00"))
            if origin.tzinfo is None:
                origin = origin.replace(tzinfo=timezone.utc)
            out.append(SourceEvent(
                source="afad",
                source_event_id=str(it["eventID"]),
                origin_time=origin.astimezone(timezone.utc),
                lat=float(it["latitude"]),
                lon=float(it["longitude"]),
                depth_km=float(it["depth"]) if it.get("depth") is not None else None,
                magnitude=float(it["magnitude"]),
                mag_type=str(it.get("type") or ""),
                received_at=received_at,
            ))
        except (KeyError, TypeError, ValueError):
            continue
    return out


async def run_afad(stream: EventStream, url_base: str = AFAD_URL,
                   interval_s: float = 60.0, seen: set[str] | None = None) -> None:
    seen = set() if seen is None else seen
    async with httpx.AsyncClient(timeout=20) as client:
        while True:
            try:
                now = datetime.now(timezone.utc)
                params = {
                    "start": (now - timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%S"),
                    "end": now.strftime("%Y-%m-%dT%H:%M:%S"),
                    "minmag": "2",
                }
                resp = await client.get(url_base, params=params)
                resp.raise_for_status()
                for se in parse_afad_response(resp.json(), now):
                    if se.source_event_id not in seen:
                        seen.add(se.source_event_id)
                        await stream.append(serialize_source_event(se))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("afad poll failed: %s", exc)
            await asyncio.sleep(interval_s)
