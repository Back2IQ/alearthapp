from __future__ import annotations

import logging
from collections import deque
from dataclasses import replace
from typing import AsyncIterator, Protocol

from tda_server.p0b.signals import PhoneTrigger, serialize_trigger
from tda_server.stream.base import EventStream

log = logging.getLogger(__name__)


class AttestationVerifier(Protocol):
    def verify(self, device_hash: str, token: str) -> bool: ...


class AllowlistVerifier:
    """Test/stub verifier. Real Play-Integrity + StrongBox path is Plan C/F.
    Never let a binary Play-Integrity verdict alone gate a device (spec 3)."""

    def __init__(self, allowed: set[str]) -> None:
        self._allowed = allowed

    def verify(self, device_hash: str, token: str) -> bool:
        return device_hash in self._allowed


class RateLimiter:
    def __init__(self, max_per_window: int, window_ms: int) -> None:
        self.max = max_per_window
        self.window = window_ms
        self._hits: dict[str, deque[int]] = {}

    def allow(self, device_hash: str, now_ms: int) -> bool:
        dq = self._hits.setdefault(device_hash, deque())
        while dq and dq[0] <= now_ms - self.window:
            dq.popleft()
        if len(dq) >= self.max:
            return False
        dq.append(now_ms)
        return True


class TriggerGate:
    def __init__(self, clock_unc_max_ms: int = 2000, server_skew_max_ms: int = 15000) -> None:
        self.clock_unc_max = clock_unc_max_ms
        self.server_skew_max = server_skew_max_ms

    def accept(self, t: PhoneTrigger) -> bool:
        if t.clock_unc_ms > self.clock_unc_max:
            return False
        if abs(t.trigger_ms - t.received_ms) > self.server_skew_max:
            return False
        return True


async def run_trigger_gateway(
    incoming: AsyncIterator[tuple[PhoneTrigger, str]],
    stream: EventStream,
    *,
    verifier: AttestationVerifier,
    limiter: RateLimiter,
    gate: TriggerGate,
) -> None:
    async for trigger, token in incoming:
        ok = verifier.verify(trigger.device_hash, token)
        if not (gate.accept(trigger) and limiter.allow(trigger.device_hash, trigger.received_ms)):
            continue
        # attest_ok is authoritative from the server, not from the client claim
        await stream.append(serialize_trigger(replace(trigger, attest_ok=ok)))
