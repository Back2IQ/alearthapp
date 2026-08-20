import asyncio
from tda_server.p0b.gateway import (
    AllowlistVerifier, RateLimiter, TriggerGate, run_trigger_gateway,
)
from tda_server.p0b.signals import PhoneTrigger, deserialize_trigger
from tda_server.stream.base import InMemoryStream


def trig(dev: str, tms: int, unc: int = 50, rec: int | None = None) -> PhoneTrigger:
    return PhoneTrigger(dev, "d410_289", tms, unc, tms + 100 if rec is None else rec, False)


def test_rate_limiter_sliding_window():
    lim = RateLimiter(max_per_window=2, window_ms=1000)
    assert lim.allow("d", 0) and lim.allow("d", 100)
    assert not lim.allow("d", 200)          # third within 1s -> blocked
    assert lim.allow("d", 1200)             # window rolled forward

def test_gate_rejects_bad_clock():
    gate = TriggerGate(clock_unc_max_ms=2000, server_skew_max_ms=15000)
    assert gate.accept(trig("d", 1000, unc=50))
    assert not gate.accept(trig("d", 1000, unc=5000))            # too uncertain
    assert not gate.accept(trig("d", 1000, unc=50, rec=1000000)) # implausible skew

async def test_gateway_writes_only_accepted():
    stream = InMemoryStream()
    verifier = AllowlistVerifier({"good"})
    limiter = RateLimiter(max_per_window=10, window_ms=1000)
    gate = TriggerGate()
    async def incoming():
        yield trig("good", 1000), "tok"      # accepted
        yield trig("bad", 1100), "tok"       # attest fails
        yield trig("good", 1200, unc=9000), "tok"  # clock gate fails
    await run_trigger_gateway(incoming(), stream, verifier=verifier,
                              limiter=limiter, gate=gate)
    got = []
    async def drain():
        async for _sid, rec in stream.read():
            got.append(deserialize_trigger(rec))
            if len(got) == 1:
                return
    await asyncio.wait_for(drain(), timeout=1)
    assert len(got) == 1 and got[0].device_hash == "good" and got[0].attest_ok is True
