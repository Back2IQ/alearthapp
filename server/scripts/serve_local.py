import asyncio
import base64
import os

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.serve.ws_bridge import serve


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
    print(f"starting TDA WebSocket bridge on ws://{host}:{port}")
    asyncio.run(serve(host, port, priv_key_b64=priv_key_b64, pub_key_b64=pub_key_b64))
