from __future__ import annotations

import base64

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

from tda_server.domain.events import CanonicalEvent, EventState
from tda_server.fusion.correlator import Transition


def derive_tier(ev: CanonicalEvent) -> str:
    """Map canonical-event state to an alert tier.

    Plan A only ever reaches DETECTED (single catalog source) or CONFIRMED
    (>=2 independent catalog sources), so those are the only branches wired
    up here: DETECTED -> "P1", CONFIRMED -> "P2". This is deliberately
    extensible: P0 (self-detection, station picker) and P0b (crowd reports)
    land in later plans and will add their own EventState branches above the
    CONFIRMED check without touching the P1/P2 logic below.
    """
    if set(ev.sources) == {"p0b"}:
        return "P0"
    if ev.state is EventState.CONFIRMED:
        return "P2"
    return "P1"


def build_payload(tr: Transition, *, test: bool = False, now_ms: int) -> dict[str, str]:
    ev = tr.event
    origin_ms = int(ev.origin_time.timestamp() * 1000)
    return {
        "v": "1",
        "id": ev.event_id,
        "ver": str(ev.version),
        "state": ev.state.value,
        "tier": derive_tier(ev),
        "test": "1" if test else "0",
        "origin_ts": str(origin_ms),
        "lat": f"{ev.lat:.4f}",
        "lon": f"{ev.lon:.4f}",
        "depth_km": "" if ev.depth_km is None else f"{ev.depth_km:.1f}",
        "mag": f"{ev.magnitude:.1f}",
        "mag_hi": f"{ev.mag_high:.1f}",
        "src": ",".join(sorted(ev.sources)),
        "issued_ts": str(now_ms),
    }


def canonical_bytes(payload: dict[str, str]) -> bytes:
    lines = [f"{k}={payload[k]}" for k in sorted(payload) if k != "sig"]
    return "\n".join(lines).encode("utf-8")


def sign_payload(payload: dict[str, str], private_key_b64: str) -> dict[str, str]:
    raw = base64.b64decode(private_key_b64)
    key = Ed25519PrivateKey.from_private_bytes(raw)
    sig = key.sign(canonical_bytes(payload))
    out = dict(payload)
    out["sig"] = base64.b64encode(sig).decode()
    return out


def verify_payload(payload: dict[str, str], public_key_b64: str) -> bool:
    if "sig" not in payload:
        return False
    key = Ed25519PublicKey.from_public_bytes(base64.b64decode(public_key_b64))
    try:
        key.verify(base64.b64decode(payload["sig"]), canonical_bytes(payload))
        return True
    except Exception:
        return False
