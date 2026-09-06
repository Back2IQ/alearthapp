import time
from alert2iq_server.serve.fast_udp_ingest import (
    FastUdpProtocol,
    pack_64byte_packet,
    unpack_64byte_packet,
)


def test_pack_unpack_64byte_roundtrip():
    dev_hash = "dev-salt-12345678"
    geohash = "sq93jk"
    peak_accel = 0.42
    ts = time.time() * 1000.0

    packed = pack_64byte_packet(dev_hash, geohash, peak_accel, ts)
    assert len(packed) == 64

    unpacked = unpack_64byte_packet(packed)
    assert unpacked is not None
    assert unpacked["geohash"] == geohash
    assert abs(unpacked["peak_accel"] - peak_accel) < 1e-4


def test_early_exit_15_packet_trigger():
    tripped = []

    def _on_trip(cell, peak, count):
        tripped.append((cell, peak, count))

    proto = FastUdpProtocol(_on_trip)
    geohash = "sq93jk"
    now = time.time() * 1000.0

    # Send 14 packets - should not trip yet
    for i in range(14):
        pkt = pack_64byte_packet(f"dev-{i}", geohash, 0.5, now)
        proto.datagram_received(pkt, ("127.0.0.1", 1234))

    assert len(tripped) == 0

    # Send 15th packet - triggers Early-Exit Short-Circuit immediately
    pkt15 = pack_64byte_packet("dev-15", geohash, 0.6, now)
    proto.datagram_received(pkt15, ("127.0.0.1", 1234))

    assert len(tripped) == 1
    assert tripped[0][0] == geohash
    assert tripped[0][2] == 15
