"""Probe SeedLink endpoints for reachability, available Turkish streams, latency.
Run manually; records findings into docs/p0a-coverage.md. Respects access terms:
INFO/handshake only, no bulk pull. slinktool is the canonical tool; this is a
dependency-free socket fallback that just checks the port answers."""
import socket
import sys

ENDPOINTS = {
    "GEOFON": ("geofon.gfz.de", 18000),
    "KOERI": ("eida.koeri.boun.edu.tr", 18000),   # unknown until tested (research)
    "IRIS": ("rtserve.earthscope.org", 18000),
}


def probe(host: str, port: int, timeout: float = 5.0) -> str:
    try:
        with socket.create_connection((host, port), timeout=timeout) as s:
            s.sendall(b"HELLO\r")
            s.settimeout(timeout)
            data = s.recv(1024)
        return f"OPEN: {data.decode(errors='replace').strip()[:120]}"
    except Exception as exc:  # noqa: BLE001 - probe reports every failure verbatim
        return f"CLOSED/ERROR: {exc}"


if __name__ == "__main__":
    for name, (host, port) in ENDPOINTS.items():
        print(f"{name} {host}:{port} -> {probe(host, port)}")
    print("\nNext: run `slinktool -Q <host>:<port>` to list streams and confirm "
          "Turkish (KO/TU/GE) network coverage; record in docs/p0a-coverage.md.")
