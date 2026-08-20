import base64

from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

priv = Ed25519PrivateKey.generate()
priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
print("TDA_SIGNING_KEY=" + base64.b64encode(priv_raw).decode())
print("TDA_PUBLIC_KEY=" + base64.b64encode(pub_raw).decode())
