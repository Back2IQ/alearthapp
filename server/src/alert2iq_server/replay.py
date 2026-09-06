from __future__ import annotations

import asyncio
import json
import sys

from alert2iq_server.alert.publisher import FakeTransport, Publisher
from alert2iq_server.fusion.correlator import Correlator
from alert2iq_server.pipeline import run_consumer
from alert2iq_server.stream.base import InMemoryStream


async def main(path: str, signing_key_b64: str) -> None:
    stream = InMemoryStream()
    records = [json.loads(line) for line in open(path, encoding="utf-8")
               if line.strip()]
    for rec in records:
        await stream.append(rec)
    ft = FakeTransport()
    await run_consumer(stream, Correlator(), Publisher(ft, signing_key_b64),
                       stop_after=len(records))
    for topic, data in ft.sent:
        print(f'{topic} id={data["id"]} v={data["ver"]} state={data["state"]} '
              f'mag={data["mag"]}')
    print(f"records={len(records)} publishes={len(ft.sent)}")


if __name__ == "__main__":
    import os
    key = os.environ.get("TDA_SIGNING_KEY") or sys.exit("TDA_SIGNING_KEY missing")
    asyncio.run(main(sys.argv[1], key))
