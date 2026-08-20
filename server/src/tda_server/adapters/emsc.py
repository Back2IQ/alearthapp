from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

import websockets

from tda_server.domain.events import SourceEvent
from tda_server.stream.base import EventStream, serialize_source_event

log = logging.getLogger(__name__)
EMSC_WS_URL = "wss://www.seismicportal.eu/standing_order/websocket"


def parse_emsc_message(text: str, received_at: datetime) -> SourceEvent | None:
    try:
        msg = json.loads(text)
        if msg.get("action") not in ("create", "update"):
            # "delete" (retraction) and unknown actions must not surface
            # as a valid, alertable/confirmable SourceEvent
            return None
        props = msg["data"]["properties"]
        origin = datetime.fromisoformat(props["time"].replace("Z", "+00:00"))
        depth = props.get("depth")
        return SourceEvent(
            source="emsc",
            source_event_id=str(props["unid"]),
            origin_time=origin.astimezone(timezone.utc),
            lat=float(props["lat"]),
            lon=float(props["lon"]),
            depth_km=None if depth is None else float(depth),
            magnitude=float(props["mag"]),
            mag_type=str(props.get("magtype", "")),
            received_at=received_at,
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        return None


async def run_emsc(stream: EventStream, url: str = EMSC_WS_URL) -> None:
    backoff = 1.0
    while True:
        try:
            async with websockets.connect(url, ping_interval=20) as ws:
                log.info("emsc connected")
                backoff = 1.0
                async for text in ws:
                    se = parse_emsc_message(text, datetime.now(timezone.utc))
                    if se is not None:
                        await stream.append(serialize_source_event(se))
        except Exception as exc:  # noqa: BLE001 - reconnect loop by design
            log.warning("emsc connection lost (%s); retry in %.0fs", exc, backoff)
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, 60.0)
