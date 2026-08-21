from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

import httpx

from tda_server.domain.hazards import HAZARD_TYPES, HazardEvent, serialize_hazard
from tda_server.stream.base import EventStream

log = logging.getLogger(__name__)
# GDACS multi-hazard GeoJSON, free, no API key (EU JRC / UN OCHA).
GDACS_URL = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/EVENTS4APP"


def _to_ms(value: str) -> int:
    dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    if dt.tzinfo is None:  # GDACS timestamps are UTC without offset
        dt = dt.replace(tzinfo=timezone.utc)
    return int(dt.timestamp() * 1000)


def parse_gdacs_feed(doc: dict, received_at: datetime) -> list[HazardEvent]:
    """Parse a GDACS GeoJSON FeatureCollection into HazardEvents. Per-feature
    guard: one malformed feature is skipped, never aborts the batch."""
    out: list[HazardEvent] = []
    for feat in doc.get("features", []):
        try:
            p = feat["properties"]
            etype = str(p["eventtype"]).upper()
            if etype not in HAZARD_TYPES:
                continue
            coords = feat["geometry"]["coordinates"]
            title = p.get("name") or p.get("eventname") or p.get("htmldescription") or etype
            url = p.get("url")
            if isinstance(url, dict):
                url = url.get("report") or next(iter(url.values()), "")
            out.append(
                HazardEvent(
                    source="gdacs",
                    source_event_id=f'gdacs:{etype}:{p["eventid"]}:{p.get("episodeid", "")}',
                    hazard_type=etype,
                    alert_level=(str(p.get("alertlevel", "")).lower() or "green"),
                    lat=float(coords[1]),
                    lon=float(coords[0]),
                    title=str(title)[:140],
                    country=str(p.get("country", "")),
                    event_ms=_to_ms(p["fromdate"]),
                    received_ms=int(received_at.timestamp() * 1000),
                    url=str(url or ""),
                )
            )
        except (KeyError, TypeError, ValueError, IndexError):
            continue
    return out


async def run_gdacs(stream: EventStream, url: str = GDACS_URL,
                    interval_s: float = 300.0, seen: set[str] | None = None) -> None:
    seen = set() if seen is None else seen
    async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
        while True:
            try:
                resp = await client.get(url)
                resp.raise_for_status()
                for h in parse_gdacs_feed(resp.json(), datetime.now(timezone.utc)):
                    if h.source_event_id not in seen:
                        seen.add(h.source_event_id)
                        await stream.append(serialize_hazard(h))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("gdacs poll failed: %s", exc)
            await asyncio.sleep(interval_s)
