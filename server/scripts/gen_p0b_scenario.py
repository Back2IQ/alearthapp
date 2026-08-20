import json
import sys

from tda_server.p0b.replay import generate_scenario

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "tests/fixtures/p0b_scenario_turkiye.json"
    scen = generate_scenario(epicenter=(37.17, 37.03), mag=7.8, n_devices=400,
                             report_fraction=0.3, t0_ms=1_000_000, seed=7)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(scen, fh)
    print(f"wrote {out}: {len(scen['triggers'])} triggers, {len(scen['pings'])} pings")
