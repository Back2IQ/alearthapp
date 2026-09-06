from __future__ import annotations

import numpy as np


def fit_poisson(nu: np.ndarray, counts: np.ndarray,
                iters: int = 50, tol: float = 1e-8) -> tuple[float, float]:
    """Newton-Raphson MLE for a log-link Poisson GLM: counts ~ Poisson(exp(b0+b1*nu)).
    Design matrix X = [1, nu]. No scipy (ARM-friendly)."""
    x = np.column_stack([np.ones_like(nu, dtype=float), nu.astype(float)])
    y = counts.astype(float)
    beta = np.array([np.log(max(y.mean(), 1e-6)), 0.0])
    for _ in range(iters):
        eta = x @ beta
        mu = np.exp(eta)
        grad = x.T @ (y - mu)
        w = mu
        hess = (x.T * w) @ x                       # X^T W X
        # least-squares solve: identical to np.linalg.solve for a well-posed
        # (non-singular) Hessian, but degrades gracefully to the minimum-norm
        # solution when nu has (near-)zero variance in the fitting window and
        # the design becomes collinear/singular (b0, b1 individually
        # unidentifiable, but exp(b0+b1*nu) at the observed nu stays correct).
        step, *_ = np.linalg.lstsq(hess, grad, rcond=None)
        beta = beta + step
        if np.max(np.abs(step)) < tol:
            break
    return float(beta[0]), float(beta[1])


class BackgroundModel:
    def __init__(self, b0: float, b1: float) -> None:
        self.b0 = b0
        self.b1 = b1

    def rate(self, nu: float) -> float:
        """Expected false-trigger rate per second at live density nu."""
        return float(np.exp(self.b0 + self.b1 * nu))

    def expected(self, nu: float, eps_s: float) -> float:
        return self.rate(nu) * eps_s

    @classmethod
    def fit(cls, nu: np.ndarray, counts: np.ndarray, bin_s: float) -> "BackgroundModel":
        # fit on per-bin counts, then shift intercept to per-second rate
        b0, b1 = fit_poisson(np.asarray(nu), np.asarray(counts))
        b0_per_s = b0 - np.log(bin_s)
        return cls(b0=float(b0_per_s), b1=b1)
