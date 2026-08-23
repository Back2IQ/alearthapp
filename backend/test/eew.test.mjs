import { test } from "node:test";
import assert from "node:assert/strict";
import { haversineKm, mmi } from "../src/eew.js";

test("haversineKm: identical points -> ~0", () => {
  const d = haversineKm(37.0, 35.32, 37.0, 35.32);
  assert.ok(d < 1e-6, `expected ~0, got ${d}`);
});

test("haversineKm: Adana <-> Izmir ~730km (tolerance)", () => {
  const d = haversineKm(37.0, 35.32, 38.42, 27.14);
  assert.ok(Math.abs(d - 730) <= 20, `expected 730±20, got ${d}`);
});

test("mmi: monotonically decreasing with distance", () => {
  const mag = 6.0;
  const near = mmi(mag, 5);
  const mid = mmi(mag, 50);
  const far = mmi(mag, 500);
  assert.ok(near > mid, `near(${near}) should be > mid(${mid})`);
  assert.ok(mid > far, `mid(${mid}) should be > far(${far})`);
});

test("mmi: clamped to [1, 12] at extremes", () => {
  const lo = mmi(1.0, 5000); // tiny mag, huge distance -> should clamp to 1
  const hi = mmi(9.5, 1); // huge mag, distance 1 -> should clamp to 12
  assert.ok(lo >= 1 && lo <= 12, `lo out of range: ${lo}`);
  assert.equal(lo, 1);
  assert.ok(hi >= 1 && hi <= 12, `hi out of range: ${hi}`);
  assert.equal(hi, 12);
});
