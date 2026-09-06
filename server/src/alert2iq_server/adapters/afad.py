from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone

import httpx

from alert2iq_server.domain.events import SourceEvent
from alert2iq_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
AFAD_URL = "https://deprem.afad.gov.tr/apiv2/event/filter"

# AFAD's "date" field is documented to be local Turkey time (TRT, UTC+3,
# no DST) when it carries no offset - confirmed against the real fixture
# (tests/fixtures/afad_filter.json, e.g. "date":"2026-08-19T02:38:36").
# This should be re-verified against a live response if/when direct
# access to the AFAD endpoint is available again.
AFAD_TZ_OFFSET_H = 3
AFAD_TZ = timezone(timedelta(hours=AFAD_TZ_OFFSET_H))


def _remember_seen(seen: set[str], event_id: str, cap: int = 10_000) -> bool:
    """Record event_id in the dedupe set; returns True if it was new.
    Feeds only ever return a 30-60 min window, so dedupe never needs to
    recall ids across a reset - a full clear once the set exceeds cap
    keeps memory bounded without a more elaborate LRU."""
    if len(seen) > cap:
        seen.clear()
    if event_id in seen:
        return False
    seen.add(event_id)
    return True


def parse_afad_response(items: list[dict], received_at: datetime) -> list[SourceEvent]:
    out: list[SourceEvent] = []
    for it in items:
        try:
            raw_date = str(it["date"])
            origin = datetime.fromisoformat(raw_date.replace("Z", "+00:00"))
            if origin.tzinfo is None:
                # offsetless AFAD dates are TRT, not UTC - see AFAD_TZ_OFFSET_H above
                origin = origin.replace(tzinfo=AFAD_TZ)
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
    # follow_redirects: the live endpoint now 302s deprem.afad.gov.tr ->
    # servisnet.afad.gov.tr, which httpx does not follow by default.
    async with httpx.AsyncClient(follow_redirects=True, timeout=20) as client:
        while True:
            try:
                now = datetime.now(timezone.utc)
                now_trt = now.astimezone(AFAD_TZ)  # AFAD expects the query window in TRT
                params = {
                    "start": (now_trt - timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%S"),
                    "end": now_trt.strftime("%Y-%m-%dT%H:%M:%S"),
                    "minmag": "2",
                }
                resp = await client.get(url_base, params=params)
                resp.raise_for_status()
                for se in parse_afad_response(resp.json(), now):
                    if _remember_seen(seen, se.source_event_id):
                        await stream.append(serialize_source_event(se))
            except Exception as exc:  # noqa: BLE001 - poll loop by design
                log.warning("afad poll failed: %s", exc)
            await asyncio.sleep(interval_s)
