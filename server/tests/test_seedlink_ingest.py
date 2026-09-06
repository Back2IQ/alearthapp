import struct
from alert2iq_server.adapters.seedlink_ingest import parse_mini_seed_header


def test_parse_mini_seed_header_valid():
    # Build a valid 64-byte mock MiniSEED header
    seq = b"000001"
    quality = b"D"
    reserved = b" "
    station = b"ISK  "
    location = b"00"
    channel = b"BHZ"
    network = b"KO"
    time_header = struct.pack(">HHBBBBHHHH", 2026, 249, 12, 30, 45, 0, 0, 100, 100, 1)
    padding = struct.pack(">4i", 1000, 5000, -2000, 300)
    data = seq + quality + reserved + station + location + channel + network + time_header + padding

    pkt = parse_mini_seed_header(data)
    assert pkt is not None
    assert pkt.network == "KO"
    assert pkt.station == "ISK"
    assert pkt.channel == "BHZ"
    assert pkt.peak_amplitude == 5000.0


def test_parse_mini_seed_header_invalid_short():
    assert parse_mini_seed_header(b"short") is None
