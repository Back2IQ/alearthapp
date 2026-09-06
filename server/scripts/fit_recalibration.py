"""Fit regional Pd-Mw from an AFAD/KOERI event list (CSV: log_pd,log_r,mw) and
print the coefficients to set as PD_A/PD_B/PD_C. Data access per Task 1."""
import csv
import sys

import numpy as np

from alert2iq_server.p0a.recalibrate import fit_pd_mw

if __name__ == "__main__":
    rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
    log_pd = np.array([float(r["log_pd"]) for r in rows])
    log_r = np.array([float(r["log_r"]) for r in rows])
    mw = np.array([float(r["mw"]) for r in rows])
    a, b, c = fit_pd_mw(log_pd, log_r, mw)
    print(f"PD_A={a:.4f} PD_B={b:.4f} PD_C={c:.4f}  (n={len(rows)})")
