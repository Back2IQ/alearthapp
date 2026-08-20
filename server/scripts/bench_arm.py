"""Measure the P0a stack on the target ARM VM. No published ARM benchmarks exist
(research 1). Prints throughput/latency so station count and window size are set
from measurement, not assumption. Run ON the VM."""
import time

import numpy as np


def bench_phasenet(n_windows: int = 20, fs: int = 100, win_s: int = 30) -> None:
    import seisbench.models as sbm
    from obspy import Stream, Trace, UTCDateTime
    model = sbm.PhaseNet.from_pretrained("geofon")
    samples = fs * win_s
    t0 = time.perf_counter()
    for _ in range(n_windows):
        traces = [Trace(data=np.random.randn(samples).astype("float32"),
                        header={"sampling_rate": fs, "starttime": UTCDateTime(),
                                "network": "GE", "station": "TEST", "channel": ch})
                  for ch in ("HHZ", "HHN", "HHE")]
        model.annotate(Stream(traces))
    dt = time.perf_counter() - t0
    print(f"PhaseNet: {n_windows} windows ({win_s}s @ {fs}Hz) in {dt:.2f}s "
          f"= {dt / n_windows * 1000:.0f} ms/window/3ch")


def bench_associator() -> None:
    try:
        import pyocto  # noqa: F401
        print("PyOcto import: OK (native build succeeded)")
    except Exception as exc:  # noqa: BLE001
        print(f"PyOcto import FAILED ({exc}) -> use GaMMA fallback (Task 6)")


if __name__ == "__main__":
    bench_associator()
    bench_phasenet()
    print("\nRecord ms/window and go/no-go in docs/p0a-coverage.md. "
          "Target: annotate a 30s window across all live stations within the "
          "latency budget (spec 4). If over budget: fewer stations or shorter window.")
