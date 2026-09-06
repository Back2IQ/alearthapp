import pytest
from alert2iq_server.serve.ingest import parse_ping_body, parse_trigger_body


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


from alert2iq_server.p0b.gateway import AllowlistVerifier, RateLimiter, TriggerGate
from alert2iq_server.serve.ingest import TriggerIngestor


def make_ingestor():
    trig_out, ping_out = [], []
    ing = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=100, window_ms=1000),
        gate=TriggerGate(),
        submit_trigger=trig_out.append,
        submit_ping=ping_out.append,
        ping_limiter=RateLimiter(max_per_window=100, window_ms=1000),
    )
    return ing, trig_out, ping_out


def test_ingestor_accepts_valid_trigger():
    ing, trig_out, _ = make_ingestor()
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1000", "clock_unc_ms": "1000"}
    ing.handle_trigger(raw, token="", received_ms=1100)
    assert len(trig_out) == 1
    assert trig_out[0]["device_hash"] == "abc" and trig_out[0]["attest_ok"] == "0"
    assert trig_out[0]["received_ms"] == "1100"


def test_ingestor_drops_bad_clock_silently():
    ing, trig_out, _ = make_ingestor()
    raw = {"device_hash": "abc", "cell": "d410_289",
           "trigger_ms": "1000", "clock_unc_ms": "9000"}   # over gate limit
    ing.handle_trigger(raw, token="", received_ms=1100)
    assert trig_out == []                                    # gated out, no raise


def test_ingestor_raises_on_malformed():
    ing, _, _ = make_ingestor()
    with pytest.raises(ValueError):
        ing.handle_trigger({"device_hash": "abc"}, token="", received_ms=1)


def test_ingestor_accepts_ping():
    ing, _, ping_out = make_ingestor()
    ing.handle_ping({"device_hash": "abc", "cell": "d410_289", "ping_ms": "1000"},
                    received_ms=1100)
    assert len(ping_out) == 1 and ping_out[0]["device_hash"] == "abc"


import json
import time
import urllib.request

from alert2iq_server.serve.ingest import start_ingest
from alert2iq_server.stream.base import InMemoryStream


def test_start_ingest_end_to_end_localhost():
    stream_records = []
    ing = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=100, window_ms=1000),
        gate=TriggerGate(),
        submit_trigger=stream_records.append,
        submit_ping=lambda d: None,
        ping_limiter=RateLimiter(max_per_window=100, window_ms=1000),
    )
    server = start_ingest("127.0.0.1", 0, ing, clock=lambda: 1000)
    port = server.server_address[1]
    body = json.dumps({"device_hash": "abc", "cell": "d410_289",
                       "trigger_ms": "1000", "clock_unc_ms": "1000"}).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{port}/trigger", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=2) as resp:
        assert resp.status == 202
    server.shutdown()
    assert len(stream_records) == 1 and stream_records[0]["device_hash"] == "abc"
