import asyncio
import base64
import os

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.p0b.gateway import AllowlistVerifier, RateLimiter, TriggerGate
from tda_server.serve.http_proxy import start_http_proxy
from tda_server.serve.ingest import TriggerIngestor, start_ingest
from tda_server.serve.ws_bridge import serve
from tda_server.stream.base import InMemoryStream


def _load_or_generate_keys() -> tuple[str, str]:
    priv_b64 = os.environ.get("TDA_SIGNING_KEY")
    pub_b64 = os.environ.get("TDA_PUBLIC_KEY")
    if priv_b64 and pub_b64:
        return priv_b64, pub_b64

    priv = Ed25519PrivateKey.generate()
    priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    priv_b64 = base64.b64encode(priv_raw).decode()
    pub_b64 = base64.b64encode(pub_raw).decode()
    print("No TDA_SIGNING_KEY/TDA_PUBLIC_KEY set - generated a fresh Ed25519 keypair:")
    print(f"TDA_SIGNING_KEY={priv_b64}")
    print(f"TDA_PUBLIC_KEY={pub_b64}")
    return priv_b64, pub_b64


if __name__ == "__main__":
    priv_key_b64, pub_key_b64 = _load_or_generate_keys()
    host = os.environ.get("TDA_WS_HOST", "0.0.0.0")
    port = int(os.environ.get("TDA_WS_PORT", "8000"))
    http_port = int(os.environ.get("TDA_HTTP_PORT", "8001"))
    start_http_proxy(host, http_port)
    print(f"starting TDA HTTP proxy on http://{host}:{http_port} (/hazards, /afad)")

    ingest_port = int(os.environ.get("TDA_INGEST_PORT", "8002"))
    # Local runner: signals land in in-memory streams. A production entrypoint
    # passes the SAME streams that run_p0b_pipeline consumes (density<-pings,
    # detector<-triggers) plus loop-thread-safe submit callbacks.
    _trigger_stream = InMemoryStream()
    _ping_stream = InMemoryStream()
    _loop = asyncio.new_event_loop()

    def _submit(stream):
        def _cb(record):
            asyncio.run_coroutine_threadsafe(stream.append(record), _loop)
        return _cb

    ingestor = TriggerIngestor(
        verifier=AllowlistVerifier(set()),
        limiter=RateLimiter(max_per_window=6, window_ms=60_000),
        gate=TriggerGate(),
        submit_trigger=_submit(_trigger_stream),
        submit_ping=_submit(_ping_stream),
        ping_limiter=RateLimiter(max_per_window=4, window_ms=60_000),
    )
    start_ingest(host, ingest_port, ingestor)
    print(f"starting TDA ingest on http://{host}:{ingest_port} (/trigger, /ping)")

    print(f"starting TDA WebSocket bridge on ws://{host}:{port}")
    asyncio.run(serve(host, port, priv_key_b64=priv_key_b64, pub_key_b64=pub_key_b64))
