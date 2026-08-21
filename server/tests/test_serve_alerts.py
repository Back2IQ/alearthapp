from __future__ import annotations

import base64

import pytest
from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.alert.payload import verify_payload
from tda_server.serve.alerts import scenario_alerts


def _keypair() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(priv_raw).decode(), base64.b64encode(pub_raw).decode()


def test_quake_scenario_produces_p0_then_p2_signed_payloads():
    priv_b64, pub_b64 = _keypair()
    payloads = scenario_alerts("quake", priv_b64, now_ms=2_000_000)

    assert len(payloads) >= 2
    assert payloads[0]["tier"] == "P0"
    assert payloads[-1]["tier"] == "P2"
    for p in payloads:
        assert verify_payload(p, pub_b64) is True


def test_tampered_payload_fails_verification():
    priv_b64, pub_b64 = _keypair()
    payloads = scenario_alerts("quake", priv_b64, now_ms=2_000_000)

    tampered = dict(payloads[0])
    tampered["mag"] = "9.9"
    assert verify_payload(tampered, pub_b64) is False


def test_firework_scenario_produces_no_alerts():
    priv_b64, _pub_b64 = _keypair()
    payloads = scenario_alerts("firework", priv_b64, now_ms=2_000_000)
    assert payloads == []


def test_unknown_kind_raises_value_error():
    priv_b64, _pub_b64 = _keypair()
    with pytest.raises(ValueError):
        scenario_alerts("bogus", priv_b64, now_ms=2_000_000)
