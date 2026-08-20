from __future__ import annotations

import asyncio
import logging
import time

from tda_server.adapters.afad import run_afad
from tda_server.adapters.emsc import run_emsc
from tda_server.adapters.usgs import run_usgs
from tda_server.alert.publisher import FakeTransport, FcmTransport, Publisher, Transport
from tda_server.config import Config
from tda_server.fusion.correlator import Correlator
from tda_server.stream.base import EventStream, InMemoryStream, deserialize_source_event
from tda_server.stream.redis_stream import RedisStream

log = logging.getLogger(__name__)


async def run_consumer(stream: EventStream, correlator: Correlator,
                       publisher: Publisher, *, start_id: str = "0",
                       stop_after: int | None = None) -> int:
    processed = 0
    async for _sid, record in stream.read(start_id):
        se = deserialize_source_event(record)
        tr = correlator.ingest(se)
        if tr is not None:
            await publisher.publish(tr, now_ms=int(time.time() * 1000))
        processed += 1
        if stop_after is not None and processed >= stop_after:
            break
    return processed


def build_transport(cfg: Config) -> Transport:
    if cfg.fcm_project_id and cfg.fcm_credentials:
        return FcmTransport(cfg.fcm_project_id, cfg.fcm_credentials)
    log.warning("no FCM config - using FakeTransport (dry run)")
    return FakeTransport()


async def main() -> None:
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s %(message)s")
    cfg = Config.from_env()
    stream: EventStream = (RedisStream(cfg.redis_url) if cfg.redis_url
                           else InMemoryStream())
    publisher = Publisher(build_transport(cfg), cfg.signing_key_b64,
                          min_mag=cfg.min_publish_mag)
    await asyncio.gather(
        run_emsc(stream),
        run_usgs(stream),
        run_afad(stream),
        run_consumer(stream, Correlator(), publisher, start_id="$"
                     if cfg.redis_url else "0"),
    )


if __name__ == "__main__":
    asyncio.run(main())
