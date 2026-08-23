import pytest
from tda_server.serve.ingest import parse_ping_body, parse_trigger_body


def test_parse_trigger_body_ok():
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1755691200000", "clock_unc_ms": "1000"}
    t = parse_trigger_body(raw, received_ms=1755691200300)
    assert t.device_hash == "abc" and t.cell == "d410_289"
    assert t.trigger_ms == 1755691200000 and t.clock_unc_ms == 1000
    assert t.received_ms == 1755691200300 and t.attest_ok is False


def test_parse_trigger_body_rejects_missing_field():
    with pytest.raises(ValueError):
        parse_trigger_body({"device_hash": "abc", "cell": "d410_289"}, received_ms=1)


def test_parse_trigger_body_rejects_non_numeric():
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "soon", "clock_unc_ms": "1000"}
    with pytest.raises(ValueError):
        parse_trigger_body(raw, received_ms=1)


def test_parse_ping_body_ok():
    p = parse_ping_body({"device_hash": "abc", "cell": "d410_289",
                         "ping_ms": "1755691200000"}, received_ms=1755691200100)
    assert p.device_hash == "abc" and p.ping_ms == 1755691200000
    assert p.received_ms == 1755691200100
