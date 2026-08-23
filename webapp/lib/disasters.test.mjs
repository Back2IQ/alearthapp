import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const D = require("./disasters.js");

const QUAKES = [
  { id: "usgs:1", source: "USGS", mag: 5.2, place: "Adana", time: 1000, lat: 37.0, lon: 35.3, url: "u1" },
  { id: "usgs:2", source: "USGS", mag: 2.1, place: "Faraway", time: 2000, lat: 10.0, lon: 10.0, url: "u2" },
  { id: "usgs:3", source: "USGS", mag: null, place: "Bad", time: 3000, lat: 1, lon: 1, url: "u3" }, // dropped
];
const EONET = [
  { id: "e1", cat: "Wildfires", title: "Fire A", lat: 38.0, lon: 35.0, date: 1500, url: "e1u" },
  { id: "e2", cat: "Sea and Lake Ice", title: "Ice B", lat: 60.0, lon: 5.0, date: 1600, url: "e2u" },
  { id: "e3", cat: "Bogus", title: "Nope", lat: 0, lon: 0, date: 1, url: "" }, // unknown → dropped
];
const GDACS = [
  { type: "TS", title: "Tsunami C", country: "TR", lat: 37.5, lon: 27.0, date: 1800, alert: "orange", url: "g1u" },
];

test("toUnified maps and drops correctly", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  assert.equal(u.length, 5); // 2 quakes + 2 eonet + 1 gdacs (2 dropped)
  const q = u.find(d => d.id === "usgs:1");
  assert.deepEqual([q.typ, q.schwere, q.quelle], ["quake", 5.2, "USGS"]);
  assert.equal(u.find(d => d.id === "e1").typ, "wildfire");
  assert.equal(u.find(d => d.quelle === "GDACS").typ, "tsunami");
  assert.ok(!u.some(d => d.ort === "Nope"));
  assert.ok(!u.some(d => d.ort === "Bad"));
});

test("filter by type keeps only selected", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const out = D.filterDisasters(u, { typen: ["quake"], region: "global", fensterH: 48, minMag: 0 },
    { lat: 37, lon: 35, radiusKm: 500 }, 100000);
  assert.ok(out.every(d => d.typ === "quake"));
});

test("region tr excludes out-of-bounds events", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const out = D.filterDisasters(u, { typen: [], region: "tr", fensterH: 48, minMag: 0 },
    { lat: 37, lon: 35, radiusKm: 500 }, 100000);
  assert.ok(!out.some(d => d.id === "usgs:2")); // 10,10 is outside Türkiye bounds
  assert.ok(out.some(d => d.id === "usgs:1"));  // 37,35.3 is inside
});

test("minMag drops small quakes but never hazards", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const out = D.filterDisasters(u, { typen: [], region: "global", fensterH: 48, minMag: 4.0 },
    { lat: 37, lon: 35, radiusKm: 9999 }, 100000);
  assert.ok(!out.some(d => d.id === "usgs:2")); // M2.1 dropped
  assert.ok(out.some(d => d.id === "usgs:1"));  // M5.2 kept
  assert.ok(out.some(d => d.typ === "wildfire")); // hazard kept regardless of minMag
});

test("minAlert filters hazards by GDACS level, leaves quakes to minMag", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const origin = { lat: 37, lon: 35, radiusKm: 9999 };
  const red = D.filterDisasters(u, { typen: [], region: "global", fensterH: 48, minMag: 0, minAlert: "red" }, origin, 100000);
  assert.ok(!red.some(d => d.typ === "tsunami")); // orange TS dropped by red floor
  assert.ok(!red.some(d => d.quelle === "EONET")); // level-less EONET dropped by a non-"all" floor
  assert.ok(red.some(d => d.typ === "quake"));      // quakes unaffected by minAlert
  const orange = D.filterDisasters(u, { typen: [], region: "global", fensterH: 48, minMag: 0, minAlert: "orange" }, origin, 100000);
  assert.ok(orange.some(d => d.typ === "tsunami")); // orange TS meets the orange floor
});

test("region near keeps only events within the radius", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const origin = { lat: 37, lon: 35.3, radiusKm: 100 }; // usgs:1 is at 37,35.3 (~0 km); usgs:2 at 10,10 (far)
  const out = D.filterDisasters(u, { typen: [], region: "near", fensterH: 48, minMag: 0 }, origin, 100000);
  assert.ok(out.some(d => d.id === "usgs:1"));
  assert.ok(!out.some(d => d.id === "usgs:2"));
});

test("green GDACS alert is dropped by an orange floor, kept by all", () => {
  const green = [{ type: "FL", title: "Flood G", country: "TR", lat: 37.4, lon: 35.0, date: 1700, alert: "green", url: "" }];
  const u = D.toUnified([], [], green);
  const origin = { lat: 37, lon: 35, radiusKm: 9999 };
  const all = D.filterDisasters(u, { typen: [], region: "global", fensterH: 48, minMag: 0, minAlert: "all" }, origin, 100000);
  assert.ok(all.some(d => d.typ === "flood"));
  const orange = D.filterDisasters(u, { typen: [], region: "global", fensterH: 48, minMag: 0, minAlert: "orange" }, origin, 100000);
  assert.ok(!orange.some(d => d.typ === "flood")); // green < orange floor
});

test("time window drops old events", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const now = 1000 + 25 * 3600 * 1000; // 25 h after time=1000
  const out = D.filterDisasters(u, { typen: [], region: "global", fensterH: 24, minMag: 0 },
    { lat: 37, lon: 35, radiusKm: 9999 }, now);
  assert.ok(!out.some(d => d.id === "usgs:1")); // older than 24 h
});

test("sort zeit is newest-first, naehe is nearest-first, schwere is strongest-first", () => {
  const u = D.toUnified(QUAKES, EONET, GDACS);
  const origin = { lat: 37, lon: 35, radiusKm: 9999 };
  const byTime = D.sortDisasters(u, "zeit", origin);
  for (let i = 1; i < byTime.length; i++) assert.ok((byTime[i - 1].zeit ?? -1) >= (byTime[i].zeit ?? -1));
  const byNear = D.sortDisasters(u, "naehe", origin);
  const d0 = D.haversineKm(origin.lat, origin.lon, byNear[0].lat, byNear[0].lon);
  const d1 = D.haversineKm(origin.lat, origin.lon, byNear[1].lat, byNear[1].lon);
  assert.ok(d0 <= d1);
  const bySev = D.sortDisasters(u, "schwere", origin);
  assert.equal(bySev[0].id, "usgs:1"); // M5.2 is the strongest
});
