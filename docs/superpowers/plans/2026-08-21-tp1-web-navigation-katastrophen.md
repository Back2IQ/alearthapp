# TP-1 (Web) · Navigations-Schale + globaler Katastrophen-Tab — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single long-scroll web-app screen with a five-tab shell and add a new global "Disasters" tab that lists *all* natural hazards with type/region/severity filters and a 24 h / 48 h window.

**Architecture:** The web app is one self-contained `index.html` (runs from `file://`, no build step). This plan (a) extracts the one bug-prone piece of logic — merging + filtering + sorting the unified disaster list — into a tiny dual-mode module `lib/disasters.js` that is `require`-able in Node (real unit tests) **and** loadable as a classic `<script>` in the browser; (b) adds a fixed tab bar + `switchTab()` controller and moves the existing panels into five `<section>` views; (c) builds the Disasters tab UI on top of the module; (d) converts the settings bottom-sheet into the Settings tab and gates the "Live server" test panel behind a developer switch. A `check.mjs` gate syntax-checks every inline script and scans for duplicate element IDs.

**Tech Stack:** Vanilla HTML/CSS/JS, Leaflet (CDN), Web Crypto (Ed25519). Node ≥ 18 for `check.mjs` + the disasters unit test (built-in `node:test`, `node:vm`, `node:fs` — no new dependencies).

## Global Constraints

- **Web-app only.** Android TP-1 (the Kotlin mirror of this tab structure) is a separate follow-up plan. This plan must produce a working, browser-verifiable web app on its own.
- **Nullkosten.** No new runtime dependency, no paid service, no new CDN. Only Node built-ins for tests.
- **Ehrlichkeit als Feature.** Every disaster row carries a source badge; if a source fails to load, the others still render and a visible note names the failed source. Nothing is silently dropped.
- **Self-contained + file://.** `lib/disasters.js` must be a **classic script** (no ES-module `import`/`export`; ES modules are blocked from `file://`). It exposes `window.Disasters` in the browser and `module.exports` in Node via a UMD-style wrapper.
- **No touching the safety core.** Do not change `Eew`, `Signing`, `ServerLink`, the alert/countdown code, or the server. Only the fetch adapters' *output* is consumed (read-only) by the new module.
- **i18n:** every new user-visible string gets keys in `en`, `tr`, `ru` (complete) and `ar` for the tab/label core (falls back to `en`), matching the existing `STR` pattern.
- **Verification reality:** the web app has no DOM test harness. Automated gate = `node check.mjs` (syntax + duplicate-ID) + the `lib/disasters.js` unit test. UI behaviour is verified by explicit manual browser steps. No commits are made (this is not a git repo) — replace each "Commit" step with a `node check.mjs` run.

**Two-scale severity, by design (physical units are not cross-comparable):** wind km/h, flood metres, and Richter magnitude cannot share one slider. So TP-1 uses **two severity controls**: a *Minimum magnitude* slider for **earthquakes** (Richter) and a *Minimum alert level* control for **hazards** (GDACS green/orange/red — the normalized cross-hazard scale, already present in the feed, no server change). EONET hazards carry no alert level, so a non-"all" alert floor also excludes them. **Parked follow-up (not TP-1, needs a proxy change):** forward GDACS `severity` (value + unit) so rows can *display* the raw physical value ("Wind 140 km/h", "+3 m") — a display enrichment, still not a cross-type filter.

---

## File Structure

- `tda/webapp/lib/disasters.js` — **Create.** Pure, DOM-free logic: `toUnified`, `filterDisasters`, `sortDisasters`, `severityScore`, `haversineKm`. UMD wrapper. One responsibility: turn raw source arrays into a filtered, sorted unified list.
- `tda/webapp/lib/disasters.test.mjs` — **Create.** Real `node:test` assertions for the module.
- `tda/webapp/check.mjs` — **Create.** Repeatable gate: syntax-check every inline `<script>` in `index.html` + `lib/disasters.js`, and fail on duplicate `id="…"`.
- `tda/webapp/index.html` — **Modify.** Add tab CSS + tab bar + `switchTab()`; wrap existing panels into five `<section class="tab-view">`; build the Disasters tab; convert the settings sheet into the Settings tab; add the developer switch and gate the demo/live-server panel; load `lib/disasters.js`; add i18n keys.

---

## Task 1: Verification harness (`check.mjs`)

**Files:**
- Create: `tda/webapp/check.mjs`

**Interfaces:**
- Consumes: `tda/webapp/index.html` (read), `tda/webapp/lib/disasters.js` (read, optional — skipped if absent).
- Produces: CLI gate. Exit 0 + `CHECK OK …` on success; exit 1 + `CHECK FAIL:` list on any inline-script syntax error or duplicate element ID. Every later task runs `node check.mjs` as its gate.

- [ ] **Step 1: Write the harness**

Create `tda/webapp/check.mjs`:

```js
// Repeatable web-app gate: inline-<script> syntax + duplicate element IDs.
// No dependencies — Node built-ins only. Run: node check.mjs
import fs from "node:fs";
import vm from "node:vm";

const here = new URL(".", import.meta.url);
const htmlPath = new URL("index.html", here);
const html = fs.readFileSync(htmlPath, "utf8");
const errs = [];

// 1) syntax-check every INLINE <script> (skip those with a src= attribute)
const inline = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m => m[1]);
inline.forEach((src, i) => {
  try { new vm.Script(src, { filename: `index.html#inline-${i}.js` }); }
  catch (e) { errs.push(`inline script #${i}: ${e.message}`); }
});

// 2) syntax-check lib/disasters.js if present
const libPath = new URL("lib/disasters.js", here);
if (fs.existsSync(libPath)) {
  try { new vm.Script(fs.readFileSync(libPath, "utf8"), { filename: "lib/disasters.js" }); }
  catch (e) { errs.push(`lib/disasters.js: ${e.message}`); }
}

// 3) duplicate id="..." scan across the whole document
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
const seen = new Set(), dup = new Set();
for (const id of ids) { if (seen.has(id)) dup.add(id); else seen.add(id); }
if (dup.size) errs.push(`duplicate id(s): ${[...dup].join(", ")}`);

if (errs.length) { console.error("CHECK FAIL:\n" + errs.map(e => "  - " + e).join("\n")); process.exit(1); }
console.log(`CHECK OK — ${inline.length} inline script(s), ${ids.length} ids, no duplicates`);
```

- [ ] **Step 2: Run it against the current file (must PASS)**

Run: `cd tda/webapp && node check.mjs`
Expected: `CHECK OK — 1 inline script(s), <N> ids, no duplicates` (exit 0).

- [ ] **Step 3: Prove it catches a duplicate ID**

Temporarily duplicate an id (e.g. add `<span id="toast"></span>` just before `</body>` in `index.html`), then run `node check.mjs`.
Expected: `CHECK FAIL:` … `duplicate id(s): toast` (exit 1). **Remove the temporary span afterward** and re-run — back to `CHECK OK`.

- [ ] **Step 4: Gate**

Run: `node check.mjs`
Expected: `CHECK OK …`, exit 0.

---

## Task 2: Pure disasters model (`lib/disasters.js` + tests)

**Files:**
- Create: `tda/webapp/lib/disasters.js`
- Test: `tda/webapp/lib/disasters.test.mjs`

**Interfaces:**
- Consumes: raw source arrays in the shapes the existing fetchers already produce —
  - quakes: `{ id, source, mag, place, time, lat, lon, depth, url }` (from `fetchQuakes` → `quakes[]`)
  - eonet: `{ id, cat, title, lat, lon, date, url }` (from `fetchHazards` → `hazards[]`; `cat` is the EONET category title)
  - gdacs: `{ type, title, country, lat, lon, date, alert, url }` (from `fetchServerHazards` → `gdacsHazards[]`; `type` ∈ TS/TC/FL/VO/WF/DR; `alert` ∈ green/orange/red)
- Produces (global `window.Disasters` / `module.exports`):
  - `toUnified(quakes, eonet, gdacs) -> Disaster[]` where `Disaster = { id, typ, schwere, ort, zeit, lat, lon, quelle, url }`, `typ ∈ "quake"|"tsunami"|"flood"|"storm"|"volcano"|"wildfire"|"drought"|"ice"|"landslide"|"temp"`, `schwere` = magnitude number for quakes / alert string for gdacs / `null` for eonet, `zeit` = epoch ms or `null`.
  - `filterDisasters(list, filter, origin, nowMs) -> Disaster[]`, `filter = { typen:string[], region:"global"|"tr"|"near", fensterH:24|48, minMag:number, minAlert:"all"|"orange"|"red" }`, `origin = { lat, lon, radiusKm }`. `minMag` filters earthquakes (Richter); `minAlert` filters hazards by GDACS alert level (EONET hazards, which have no level, are excluded when `minAlert !== "all"`).
  - `sortDisasters(list, sort, origin) -> Disaster[]`, `sort ∈ "zeit"|"naehe"|"schwere"`.
  - `severityScore(d) -> number`, `haversineKm(lat1,lon1,lat2,lon2) -> number`.

- [ ] **Step 1: Write the failing test**

Create `tda/webapp/lib/disasters.test.mjs`:

```js
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tda/webapp && node --test lib/disasters.test.mjs`
Expected: FAIL — `Cannot find module './disasters.js'`.

- [ ] **Step 3: Write the module**

Create `tda/webapp/lib/disasters.js`:

```js
// Pure, DOM-free disaster-list logic. UMD: window.Disasters (browser, classic
// script — NO ES modules, they are blocked from file://) and module.exports (Node).
(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Disasters = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const EARTH_R_KM = 6371.0;
  function haversineKm(lat1, lon1, lat2, lon2) {
    const r = Math.PI / 180, la1 = lat1 * r, la2 = lat2 * r, dLat = (lat2 - lat1) * r, dLon = (lon2 - lon1) * r;
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
    return EARTH_R_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  // Türkiye + immediate neighbours bounding box (same as index.html TR_REGION).
  const TR = { minlat: 33.0, maxlat: 43.5, minlon: 24.0, maxlon: 47.0 };
  const EONET_TYPES = {
    "Wildfires": "wildfire", "Severe Storms": "storm", "Volcanoes": "volcano",
    "Floods": "flood", "Sea and Lake Ice": "ice", "Drought": "drought",
    "Landslides": "landslide", "Temperature Extremes": "temp",
  };
  const GDACS_TYPES = { TS: "tsunami", TC: "storm", FL: "flood", VO: "volcano", WF: "wildfire", DR: "drought" };
  const ALERT_RANK = { green: 1, orange: 1.5, red: 2 }; // orange must stay below a M5+ quake in severityScore

  function toUnified(quakes, eonet, gdacs) {
    const out = [];
    (quakes || []).forEach(q => {
      if (q.mag == null || q.lat == null || q.lon == null) return;
      out.push({ id: q.id, typ: "quake", schwere: q.mag, ort: q.place || "", zeit: q.time == null ? null : q.time,
        lat: q.lat, lon: q.lon, quelle: q.source || "USGS", url: q.url || "" });
    });
    (eonet || []).forEach(h => {
      const typ = EONET_TYPES[h.cat]; if (!typ) return;
      if (h.lat == null || h.lon == null) return;
      out.push({ id: h.id, typ, schwere: null, ort: h.title || "", zeit: h.date == null ? null : h.date,
        lat: h.lat, lon: h.lon, quelle: "EONET", url: h.url || "" });
    });
    (gdacs || []).forEach(h => {
      const typ = GDACS_TYPES[h.type]; if (!typ) return;
      if (h.lat == null || h.lon == null) return;
      out.push({ id: "gdacs:" + h.type + ":" + h.lat + "," + h.lon + ":" + (h.date ?? ""), typ,
        schwere: h.alert || null, ort: h.title || h.country || "", zeit: h.date == null ? null : h.date,
        lat: h.lat, lon: h.lon, quelle: "GDACS", url: h.url || "" });
    });
    return out;
  }

  function inTR(d) { return d.lat >= TR.minlat && d.lat <= TR.maxlat && d.lon >= TR.minlon && d.lon <= TR.maxlon; }

  function filterDisasters(list, filter, origin, nowMs) {
    const typen = filter.typen || [];
    const winMs = (filter.fensterH || 48) * 3600 * 1000;
    const minMag = filter.minMag || 0;
    const minAlertRank = (filter.minAlert && filter.minAlert !== "all") ? (ALERT_RANK[filter.minAlert] || 0) : 0;
    return (list || []).filter(d => {
      if (typen.length && !typen.includes(d.typ)) return false;
      if (d.zeit != null && nowMs - d.zeit > winMs) return false;
      // earthquakes filtered by Richter magnitude; hazards by GDACS alert level.
      if (d.typ === "quake") { if (typeof d.schwere === "number" && d.schwere < minMag) return false; }
      else if (minAlertRank > 0 && (ALERT_RANK[d.schwere] || 0) < minAlertRank) return false;
      if (filter.region === "tr" && !inTR(d)) return false;
      if (filter.region === "near" && origin && haversineKm(origin.lat, origin.lon, d.lat, d.lon) > origin.radiusKm) return false;
      return true;
    });
  }

  // Mixed-type severity so quakes and alert-level hazards interleave sensibly.
  function severityScore(d) {
    if (d.typ === "quake" && typeof d.schwere === "number") return d.schwere;
    if (typeof d.schwere === "string") return 3.5 + (ALERT_RANK[d.schwere] || 0); // green 4.5 / orange 5.0 / red 5.5
    return 0; // unranked hazards (EONET) sort last
  }

  function sortDisasters(list, sort, origin) {
    const arr = (list || []).slice();
    if (sort === "naehe" && origin) {
      arr.sort((a, b) => haversineKm(origin.lat, origin.lon, a.lat, a.lon) - haversineKm(origin.lat, origin.lon, b.lat, b.lon));
    } else if (sort === "schwere") {
      arr.sort((a, b) => severityScore(b) - severityScore(a));
    } else { // "zeit"
      arr.sort((a, b) => (b.zeit ?? -1) - (a.zeit ?? -1));
    }
    return arr;
  }

  return { toUnified, filterDisasters, sortDisasters, severityScore, haversineKm };
});
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tda/webapp && node --test lib/disasters.test.mjs`
Expected: PASS — `# pass 7  # fail 0`.

- [ ] **Step 5: Gate**

Run: `node check.mjs`
Expected: `CHECK OK …` (now reports `lib/disasters.js` checked too, exit 0).

---

## Task 3: Tab shell — bar, controller, and relocation of existing content

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: existing `State`, `Settings`, `savePrefs`/`loadPrefs`, `map`, `$`, `applyLanguage`.
- Produces: `switchTab(name)`, `State.activeTab` (persisted), five `<section class="tab-view" data-tab="…">`, a `<nav class="tabbar">`, and `renderStartNearest()`. Settings content now lives in the `settings` tab (the bottom-sheet is removed); the gear button calls `switchTab("settings")`.

- [ ] **Step 1: Add tab-shell CSS**

In `index.html`, immediately before the closing `</style>` (line ~266, after the `@keyframes sp` rule), insert:

```css
  /* ---- tab shell ---- */
  .tab-view { display:none; }
  .tab-view.active { display:block; }
  .tabbar { position:fixed; left:0; right:0; bottom:0; z-index:40; display:flex; gap:2px;
    padding:6px 8px calc(6px + env(safe-area-inset-bottom)); background:color-mix(in srgb,var(--bg2) 90%,transparent);
    backdrop-filter:blur(16px) saturate(1.2); -webkit-backdrop-filter:blur(16px) saturate(1.2); border-top:1px solid var(--border); }
  .tabbar button { flex:1; border:0; background:transparent; color:var(--muted); font-family:var(--font-body);
    font-size:.64rem; font-weight:600; padding:6px 2px; border-radius:13px; cursor:pointer;
    display:flex; flex-direction:column; align-items:center; gap:3px; }
  .tabbar button .ic { font-size:1.15rem; line-height:1; }
  .tabbar button[aria-selected="true"] { color:var(--accent); background:color-mix(in srgb,var(--accent) 15%,transparent); }
  .wrap { padding-bottom:98px; }            /* clear the fixed tab bar */
  @media (min-width:900px) {
    .tabbar { top:0; bottom:0; right:auto; width:92px; flex-direction:column; justify-content:flex-start;
      gap:6px; border-top:0; border-right:1px solid var(--border); padding-top:20px; }
    .wrap { margin-left:92px; padding-bottom:48px; }
  }
```

- [ ] **Step 2: Wrap existing panels into five tab sections**

In `index.html`, inside `<div class="wrap" id="startScreen">`, the `<div class="brand">…</div>` (lines ~272-279) **stays at the top, outside the tab views**. Everything after the brand up to the closing `</div>` of `#startScreen` is reorganised into five sections. Wrap the existing panels (identified by their HTML comments) exactly like this — move the existing markup unchanged into the matching section:

```html
  <!-- brand block stays here, unchanged -->

  <section class="tab-view active" data-tab="start" id="view-start">
    <!-- STATUS HERO  (existing #hero panel) -->
    <div class="panel" id="startNearestPanel" style="display:none">
      <h2 id="startRecentTitle">Most recent nearby</h2>
      <div id="startNearest" class="q-item" style="cursor:pointer"></div>
    </div>
    <!-- LOCATION  (existing location panel) -->
  </section>

  <section class="tab-view" data-tab="map" id="view-map">
    <!-- MAP  (existing map panel) -->
  </section>

  <section class="tab-view" data-tab="disasters" id="view-disasters">
    <!-- QUAKE LIST  (existing #quakeList panel, MOVED here unchanged) — replaced by the filter UI in Task 4 -->
  </section>

  <section class="tab-view" data-tab="network" id="view-network">
    <!-- SEISMOGRAM  (existing seismogram panel) -->
    <!-- DETECTION NETWORK  (existing network panel) -->
    <!-- SEQUENCE  (existing #seqPanel) -->
    <!-- DEMO / TEST  (existing <details class="demo">) — gated in Task 5 -->
  </section>

  <section class="tab-view" data-tab="settings" id="view-settings">
    <!-- Settings content is moved here in Step 4 -->
  </section>

  <nav class="tabbar" id="tabbar" role="tablist">
    <button data-tab="start" aria-selected="true"><span class="ic">⌂</span><span id="tabStart">Start</span></button>
    <button data-tab="map" aria-selected="false"><span class="ic">◎</span><span id="tabMap">Map</span></button>
    <button data-tab="disasters" aria-selected="false"><span class="ic">⚠</span><span id="tabDisasters">Disasters</span></button>
    <button data-tab="network" aria-selected="false"><span class="ic">⚡</span><span id="tabNetwork">Network</span></button>
    <button data-tab="settings" aria-selected="false"><span class="ic">⚙</span><span id="tabSettings">Settings</span></button>
  </nav>
```

**Move** the existing `<!-- QUAKE LIST -->` panel (the `#quakeList` panel, lines ~332-337) **unchanged** into `#view-disasters` — it stays the interim content there so `renderQuakeList`/`renderFeed` keep working until Task 4 replaces it. Do **not** delete it in this task. Delete only the standalone `<p class="hint" … id="footNote">…</p>` footnote (lines ~413-415); it belongs to no tab (its `applyLanguage` line `set("footNote","footnote")` is `if(el)`-guarded, so leaving that line is harmless).

- [ ] **Step 3: Load the disasters module**

In `<head>`, right after the Leaflet `<script src="…leaflet.js"></script>` (line ~12), add:

```html
<script src="lib/disasters.js"></script>
```

- [ ] **Step 4: Convert the settings sheet into the Settings tab**

Move the **inner** content of `<div class="sheet" id="settingsSheet"><div class="box">…</div></div>` (lines ~476-541) into `#view-settings`. Concretely: take everything from `<h3 … id="setTitle">` through the `<button class="btn accent wide" id="btnSetDone">` and place it inside `#view-settings`, wrapped in a `<div class="panel">`. Drop the `.sheet`, `.box`, and `.grab` wrappers. Change the "Done" button so it returns to Start (see Step 6). Remove the now-empty `#settingsSheet` element.

- [ ] **Step 5: Add the tab controller + Start one-liner + state persistence**

In the JS, add near `openSheet`/`closeSheet` (lines ~1779-1780). First, **replace** `openSheet`/`closeSheet` with:

```js
const TABS = ["start","map","disasters","network","settings"];
function switchTab(name) {
  if (!TABS.includes(name)) name = "start";
  State.activeTab = name;
  for (const v of document.querySelectorAll(".tab-view")) v.classList.toggle("active", v.dataset.tab === name);
  for (const b of $("tabbar").querySelectorAll("button")) b.setAttribute("aria-selected", String(b.dataset.tab === name));
  if (name === "map" && map) setTimeout(() => { try { map.invalidateSize(); } catch(e){} }, 60); // Leaflet needs this after unhide
  if (name === "disasters" && typeof renderDisasters === "function") renderDisasters();
  savePrefs();
}
function renderStartNearest() {
  const panel = $("startNearestPanel"), box = $("startNearest");
  if (!panel || !box || !quakes.length) { if (panel) panel.style.display = "none"; return; }
  let best = null, bestD = Infinity;
  for (const q of quakes) {
    const d = Eew.haversineKm(State.loc.lat, State.loc.lon, q.lat, q.lon);
    if (d < bestD) { bestD = d; best = q; }
  }
  if (!best) { panel.style.display = "none"; return; }
  panel.style.display = "block";
  box.innerHTML = `<div class="q-mag" style="background:${magColor(best.mag)}">${(best.mag||0).toFixed(1)}</div>
    <div class="q-info"><div class="q-place">${best.place || "—"}</div>
    <div class="q-meta">${fmtAgo(best.time)} · ${fmtDist(bestD)}</div></div>`;
  box.onclick = () => { switchTab("map"); if (map) { map.setView([best.lat, best.lon], 7); } };
}
```

Add `renderStartNearest();` inside `renderFeed()` (after `renderHazards();`, line ~1470).

Add `activeTab:"start"` to the `State` object literal (line ~828-833). In `savePrefs()` (line ~850), add `activeTab: State.activeTab` to the persisted object. In `loadPrefs()` (line ~838-848), add: `if (p.activeTab) State.activeTab = p.activeTab;`.

- [ ] **Step 6: Rewire the gear button, Done button, and tab bar clicks**

In `bind()` (lines ~1781-1784), replace the settings-sheet wiring:

```js
  $("btnSettings").onclick = () => switchTab("settings");
  $("btnSetDone").onclick = () => switchTab("start");
  $("tabbar").onclick = e => { const b = e.target.closest("button"); if (b) switchTab(b.dataset.tab); };
```

Remove the line `$("settingsSheet").onclick = e => { … };` (the sheet no longer exists).

In `init()` (lines ~1835-1846), after `applyLanguage();`, add:

```js
  switchTab(State.activeTab || "start");
```

- [ ] **Step 7: Add tab-label i18n keys**

In `STR.en` add (near `app_name`): `tab_start:"Start", tab_map:"Map", tab_disasters:"Disasters", tab_network:"Network", tab_settings:"Settings", start_recent:"Most recent nearby",`
In `STR.tr`: `tab_start:"Başlangıç", tab_map:"Harita", tab_disasters:"Afetler", tab_network:"Ağ", tab_settings:"Ayarlar", start_recent:"En yakın son olay",`
In `STR.ru`: `tab_start:"Начало", tab_map:"Карта", tab_disasters:"Бедствия", tab_network:"Сеть", tab_settings:"Настройки", start_recent:"Ближайшее событие",`
In `STR.ar`: `tab_start:"البداية", tab_map:"الخريطة", tab_disasters:"الكوارث", tab_network:"الشبكة", tab_settings:"الإعدادات", start_recent:"الأقرب حديثًا",`

In `applyLanguage()` (after the `set("appName",…)` line ~1733), add:

```js
  set("tabStart","tab_start"); set("tabMap","tab_map"); set("tabDisasters","tab_disasters");
  set("tabNetwork","tab_network"); set("tabSettings","tab_settings"); set("startRecentTitle","start_recent");
  renderStartNearest();
```

- [ ] **Step 8: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs`
Expected: `CHECK OK …` and `# pass 7 # fail 0`.

- [ ] **Step 9: Manual browser verification**

Open `index.html` in a browser. Confirm: (a) five tabs at the bottom; tapping each shows only that section, no long scroll; (b) the **Map** tab renders the Leaflet map fully (not a grey sliver — proves `invalidateSize`); (c) the gear icon opens the **Settings** tab; "Done" returns to **Start**; (d) reload the page — the last-open tab is restored; (e) the Start tab shows the "Most recent nearby" one-liner when quake data has loaded, and tapping it jumps to the Map centred on that quake.

---

## Task 4: Disasters tab — filters, window, unified list

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: `window.Disasters` (Task 2), `quakes[]`, `hazards[]`, `gdacsHazards[]`, `Settings`, `savePrefs`, `switchTab`, `fmtAgo`, `fmtDist`, `magColor`.
- Produces: `Settings.disasterFilter` (persisted), `renderDisasters()`, `DISASTER_ICON`, and the source-failure notice via a new `sourceErrors` set.

- [ ] **Step 1: Replace the interim quake-list panel with the filter UI**

Replace the entire `<!-- QUAKE LIST -->` panel now sitting inside `#view-disasters` (the `<div class="panel">` containing `#quakeList`, `#listTitle`, `#listSource`, moved there in Task 3) with:

```html
    <div class="panel">
      <h2 id="katTitle">Recent disasters</h2>
      <div class="field-label" id="fType">Type</div>
      <div class="chips" id="katTypeChips"></div>
      <div class="field-label" id="fRegion" style="margin-top:12px">Region</div>
      <div class="seg-3" id="katRegionSeg">
        <button data-region="global" aria-pressed="true" id="rgGlobal">Global</button>
        <button data-region="tr" aria-pressed="false" id="rgTr">TR + neighbours</button>
        <button data-region="near" aria-pressed="false" id="rgNear">Near me</button>
      </div>
      <div style="display:flex; gap:12px; margin-top:12px">
        <div style="flex:1">
          <div class="field-label" id="fTime">Period</div>
          <div class="seg" id="katWindowSeg">
            <button data-win="24" aria-pressed="true" id="win24">24 h</button>
            <button data-win="48" aria-pressed="false" id="win48">48 h</button>
          </div>
        </div>
        <div style="flex:1">
          <div class="field-label" id="fSort">Sort</div>
          <select id="katSortSelect">
            <option value="zeit" id="sortTime">Time</option>
            <option value="naehe" id="sortNear">Distance</option>
            <option value="schwere" id="sortSev">Severity</option>
          </select>
        </div>
      </div>
      <div style="display:flex; gap:12px; margin-top:14px">
        <div style="flex:1">
          <div class="set-row"><span class="name" id="fMinMag">Min. magnitude</span><span class="val" id="katMinMagVal">M 0.0</span></div>
          <input type="range" id="katMinMag" min="0" max="7" step="0.5" value="0">
          <p class="set-desc" id="descMinMag2">Earthquakes only (Richter).</p>
        </div>
        <div style="flex:1">
          <div class="field-label" id="fMinAlert">Min. hazard alert</div>
          <div class="seg-3" id="katAlertSeg">
            <button data-alert="all" aria-pressed="true" id="alAll">All</button>
            <button data-alert="orange" aria-pressed="false" id="alOrange">Orange+</button>
            <button data-alert="red" aria-pressed="false" id="alRed">Red</button>
          </div>
          <p class="set-desc" id="descMinAlert">Storms, floods, tsunami… (GDACS level).</p>
        </div>
      </div>
    </div>
    <div class="panel">
      <div id="katSourceNote" class="hint" style="display:none; color:var(--warn)"></div>
      <div id="disasterList"><p class="q-empty" id="katLoading">Loading…</p></div>
    </div>
```

- [ ] **Step 2: Add filter state + icon map + render logic**

Add `disasterFilter` to the `Settings` literal (line ~836-837):

```js
  , disasterFilter:{ typen:[], region:"global", fensterH:24, minMag:0, minAlert:"all", sort:"zeit" }
```

And in `loadPrefs()` merge-guard (after the `Settings.seis = Object.assign(...)` line ~842) add:

```js
    Settings.disasterFilter = Object.assign({ typen:[], region:"global", fensterH:24, minMag:0, minAlert:"all", sort:"zeit" }, Settings.disasterFilter);
```

Add near `renderFeed` (line ~1465), a module block:

```js
/* ---- Disasters tab: unified, filtered, sorted list of all hazards ---- */
const DISASTER_ICON = { quake:"🟠", tsunami:"🌊", flood:"💧", storm:"🌀", volcano:"🌋", wildfire:"🔥", drought:"🏜", ice:"🧊", landslide:"⛰", temp:"🌡" };
const DISASTER_TYPES = ["quake","tsunami","flood","storm","volcano","wildfire"];
const sourceErrors = new Set(); // names of sources that failed to load, shown honestly
function disasterTypeLabel(typ) {
  const k = { quake:"ty_quake", tsunami:"ty_tsunami", flood:"ty_flood", storm:"ty_storm", volcano:"ty_volcano", wildfire:"ty_wildfire", drought:"hz_drought", ice:"hz_ice", landslide:"hz_landslide", temp:"hz_temp" }[typ];
  return k ? t(k) : typ;
}
function renderKatTypeChips() {
  const c = $("katTypeChips"); if (!c) return; c.innerHTML = "";
  const f = Settings.disasterFilter;
  const mkChip = (typ, label, pressed) => {
    const b = document.createElement("button"); b.className = "chip-btn"; b.dataset.typ = typ;
    b.textContent = label; b.setAttribute("aria-pressed", String(pressed)); return b;
  };
  c.appendChild(mkChip("__all", t("ty_all"), f.typen.length === 0));
  for (const typ of DISASTER_TYPES) c.appendChild(mkChip(typ, `${DISASTER_ICON[typ]} ${disasterTypeLabel(typ)}`, f.typen.includes(typ)));
}
function renderDisasters() {
  if (!window.Disasters) return;
  renderKatTypeChips();
  const f = Settings.disasterFilter, origin = { lat:State.loc.lat, lon:State.loc.lon, radiusKm:Settings.radiusKm };
  const unified = Disasters.toUnified(quakes, hazards, gdacsHazards);
  const filtered = Disasters.filterDisasters(unified, f, origin, Date.now());
  const sorted = Disasters.sortDisasters(filtered, f.sort, origin);
  const note = $("katSourceNote");
  if (sourceErrors.size) { note.style.display = "block"; note.textContent = t("src_down").replace("%1", [...sourceErrors].join(", ")); }
  else note.style.display = "none";
  const box = $("disasterList"); box.innerHTML = "";
  if (!sorted.length) { box.innerHTML = `<p class="q-empty">${quakes.length || hazards.length || gdacsHazards.length ? t("kat_empty") : t("list_loading")}</p>`; return; }
  for (const d of sorted.slice(0, 60)) {
    const dist = Eew.haversineKm(State.loc.lat, State.loc.lon, d.lat, d.lon);
    const badge = d.typ === "quake" ? `M ${(+d.schwere).toFixed(1)}` : (typeof d.schwere === "string" ? (t("hz_alert_" + d.schwere) || d.schwere) : t("hz_" + d.typ) || "");
    const row = document.createElement("div"); row.className = "q-item";
    row.innerHTML = `<div class="q-mag" style="background:${d.typ==="quake"?magColor(d.schwere):"var(--panel-strong)"};font-size:1.3rem">${DISASTER_ICON[d.typ]||"⚠"}</div>
      <div class="q-info"><div class="q-place">${badge ? badge + " · " : ""}${d.ort || disasterTypeLabel(d.typ)}</div>
      <div class="q-meta">${d.zeit ? fmtAgo(d.zeit) + " · " : ""}${fmtDist(dist)} · ${d.quelle}</div></div>`;
    row.onclick = () => { switchTab("map"); if (map) map.setView([d.lat, d.lon], 6); };
    box.appendChild(row);
  }
}
```

- [ ] **Step 3: Reflect filter UI state + wire the controls**

Add a `applyKatUI()` and bind. Add to `applySettingsUI()` end (line ~1717) a call `applyKatUI();`, and define it near `renderDisasters`:

```js
function applyKatUI() {
  const f = Settings.disasterFilter;
  for (const b of $("katRegionSeg").querySelectorAll("button")) b.setAttribute("aria-pressed", String(b.dataset.region === f.region));
  for (const b of $("katWindowSeg").querySelectorAll("button")) b.setAttribute("aria-pressed", String(+b.dataset.win === f.fensterH));
  for (const b of $("katAlertSeg").querySelectorAll("button")) b.setAttribute("aria-pressed", String(b.dataset.alert === f.minAlert));
  $("katSortSelect").value = f.sort;
  $("katMinMag").value = f.minMag;
  $("katMinMagVal").textContent = `M ${f.minMag.toFixed(1)}`;
}
```

In `bind()` add:

```js
  $("katTypeChips").onclick = e => {
    const b = e.target.closest("button"); if (!b) return;
    const f = Settings.disasterFilter, typ = b.dataset.typ;
    if (typ === "__all") f.typen = [];
    else { const i = f.typen.indexOf(typ); if (i >= 0) f.typen.splice(i, 1); else f.typen.push(typ); }
    savePrefs(); renderDisasters();
  };
  $("katRegionSeg").onclick = e => { const b = e.target.closest("button"); if (!b) return; Settings.disasterFilter.region = b.dataset.region; applyKatUI(); savePrefs(); renderDisasters(); };
  $("katWindowSeg").onclick = e => { const b = e.target.closest("button"); if (!b) return; Settings.disasterFilter.fensterH = parseInt(b.dataset.win, 10); applyKatUI(); savePrefs(); renderDisasters(); };
  $("katSortSelect").onchange = e => { Settings.disasterFilter.sort = e.target.value; savePrefs(); renderDisasters(); };
  $("katMinMag").oninput = e => { Settings.disasterFilter.minMag = parseFloat(e.target.value); $("katMinMagVal").textContent = `M ${Settings.disasterFilter.minMag.toFixed(1)}`; renderDisasters(); };
  $("katMinMag").onchange = savePrefs;
  $("katAlertSeg").onclick = e => { const b = e.target.closest("button"); if (!b) return; Settings.disasterFilter.minAlert = b.dataset.alert; applyKatUI(); savePrefs(); renderDisasters(); };
```

- [ ] **Step 4: Track source failures + refresh the tab on new data**

Update the three fetchers to record failures and refresh the Disasters tab:
- In `fetchUsgs`/`fetchEmsc`/`fetchAfad`: on success `sourceErrors.delete("USGS"/"EMSC"/"AFAD")`; on the caught failure path add the name. Since `fetchQuakes` uses `Promise.allSettled`, set them there: after the settle (line ~1513-1517), add per-source `if (u.status==="rejected") sourceErrors.add("USGS"); else sourceErrors.delete("USGS");` and likewise EMSC/AFAD.
- In `fetchHazards` catch → `sourceErrors.add("EONET")`, success → `sourceErrors.delete("EONET")`. In `fetchServerHazards` catch/`!r.ok` → `sourceErrors.add("GDACS")`, success → `sourceErrors.delete("GDACS")`.
- At the end of `renderFeed()`, `fetchHazards()`, and `fetchServerHazards()`, add: `if (State.activeTab === "disasters") renderDisasters();`

- [ ] **Step 5: Fix the two old `#quakeList` error references**

The old error path wrote to `#quakeList`, which no longer exists. In `fetchQuakes` (line ~1518) change `$("quakeList").innerHTML = …` to write into `$("disasterList")`. In `renderQuakeList`'s former callers: `renderFeed()` no longer calls `renderQuakeList`. Remove the `renderQuakeList` function (lines ~1452-1464) and its call in `renderFeed` (line ~1469). The map markers still come from `renderQuakeMarkers(filteredQuakes())` — leave that untouched.

- [ ] **Step 6: Add Disasters-tab i18n keys**

Add to each `STR` locale (en/tr/ru complete; ar core). English:
```js
    kat_title:"Recent disasters", f_type:"Type", f_region:"Region", f_time:"Period", f_sort:"Sort", f_minmag:"Min. magnitude",
    ty_all:"All", ty_quake:"Quakes", ty_tsunami:"Tsunami", ty_flood:"Flood", ty_storm:"Storm", ty_volcano:"Volcano", ty_wildfire:"Wildfire",
    rg_global:"Global", rg_tr:"TR + neighbours", rg_near:"Near me",
    sort_time:"Time", sort_near:"Distance", sort_severity:"Severity",
    f_minalert:"Min. hazard alert", al_all:"All", al_orange:"Orange+", al_red:"Red",
    desc_minmag2:"Earthquakes only (Richter).", desc_minalert:"Storms, floods, tsunami… (GDACS level).",
    kat_empty:"No events for this filter.", src_down:"%1 source unavailable — showing the rest.",
```
Turkish:
```js
    kat_title:"Son afetler", f_type:"Tür", f_region:"Bölge", f_time:"Süre", f_sort:"Sırala", f_minmag:"En düşük büyüklük",
    ty_all:"Tümü", ty_quake:"Depremler", ty_tsunami:"Tsunami", ty_flood:"Sel", ty_storm:"Fırtına", ty_volcano:"Yanardağ", ty_wildfire:"Orman yangını",
    rg_global:"Küresel", rg_tr:"TR + komşular", rg_near:"Yakınımda",
    sort_time:"Zaman", sort_near:"Uzaklık", sort_severity:"Şiddet",
    f_minalert:"En düşük afet alarmı", al_all:"Tümü", al_orange:"Turuncu+", al_red:"Kırmızı",
    desc_minmag2:"Yalnızca depremler (Richter).", desc_minalert:"Fırtına, sel, tsunami… (GDACS düzeyi).",
    kat_empty:"Bu filtre için olay yok.", src_down:"%1 kaynağı erişilemiyor — diğerleri gösteriliyor.",
```
Russian:
```js
    kat_title:"Недавние бедствия", f_type:"Тип", f_region:"Регион", f_time:"Период", f_sort:"Сортировка", f_minmag:"Мин. магнитуда",
    ty_all:"Все", ty_quake:"Землетрясения", ty_tsunami:"Цунами", ty_flood:"Наводнение", ty_storm:"Шторм", ty_volcano:"Вулкан", ty_wildfire:"Пожар",
    rg_global:"Глобально", rg_tr:"Турция + соседи", rg_near:"Рядом",
    sort_time:"Время", sort_near:"Расстояние", sort_severity:"Сила",
    f_minalert:"Мин. уровень тревоги", al_all:"Все", al_orange:"Оранжевый+", al_red:"Красный",
    desc_minmag2:"Только землетрясения (Рихтер).", desc_minalert:"Штормы, наводнения, цунами… (уровень GDACS).",
    kat_empty:"Нет событий для этого фильтра.", src_down:"Источник %1 недоступен — показаны остальные.",
```
Arabic (core):
```js
    kat_title:"الكوارث الأخيرة", f_type:"النوع", f_region:"المنطقة", f_time:"الفترة", f_sort:"الترتيب", f_minmag:"أدنى قوة",
    ty_all:"الكل", ty_quake:"الزلازل", ty_tsunami:"تسونامي", ty_flood:"فيضان", ty_storm:"عاصفة", ty_volcano:"بركان", ty_wildfire:"حريق",
    rg_global:"عالمي", rg_tr:"تركيا والجوار", rg_near:"قربي",
    f_minalert:"أدنى مستوى إنذار", al_all:"الكل", al_orange:"برتقالي+", al_red:"أحمر",
    kat_empty:"لا أحداث لهذا المرشح.", src_down:"المصدر %1 غير متاح — عرض الباقي.",
```

In `applyLanguage()` add label wiring:
```js
  set("katTitle","kat_title"); set("fType","f_type"); set("fRegion","f_region"); set("fTime","f_time"); set("fSort","f_sort"); set("fMinMag","f_minmag");
  set("rgGlobal","rg_global"); set("rgTr","rg_tr"); set("rgNear","rg_near");
  set("sortTime","sort_time"); set("sortNear","sort_near"); set("sortSev","sort_severity");
  set("fMinAlert","f_minalert"); set("alAll","al_all"); set("alOrange","al_orange"); set("alRed","al_red");
  set("descMinMag2","desc_minmag2"); set("descMinAlert","desc_minalert");
  applyKatUI(); if (State.activeTab === "disasters") renderDisasters();
```

- [ ] **Step 7: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs`
Expected: `CHECK OK …` and `# pass 7 # fail 0`.

- [ ] **Step 8: Manual browser verification**

On the **Disasters** tab confirm: (a) it shows global events of multiple types by default; (b) tapping a **Type** chip narrows the list; "All" resets; (c) **Region → TR + neighbours** removes far-away events; (d) toggling **24 h ↔ 48 h** changes the count; (e) the **Min. magnitude** slider drops small quakes but keeps hazards; (e2) **Min. hazard alert → Red** drops orange/level-less hazards but keeps quakes; (f) **Sort** by Distance puts the nearest first; (g) every row shows a source badge (USGS/EMSC/AFAD/EONET/GDACS); (h) tapping a row jumps to the Map centred on it. To see the honest source note: with the local proxy stopped, GDACS/AFAD fail → the amber "source unavailable" line names them while USGS/EMSC events still list.

---

## Task 5: Developer switch + gated Live-Server test panel

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: `Settings`, `savePrefs`, the existing `<details class="demo">` block (now inside `#view-network` after Task 3).
- Produces: `Settings.devMode` (persisted, default `false`), `applyDevMode()`, a labelled note on the panel, and a settings toggle.

- [ ] **Step 1: Add the developer toggle to the Settings tab**

Inside `#view-settings`, just before the `#btnSetDone` button, add:

```html
      <div class="set-group">
        <div class="toggle-row">
          <span id="lblDev">Developer / test mode</span>
          <label class="switch"><input type="checkbox" id="devSwitch"><span class="slider"></span></label>
        </div>
        <p class="set-desc" id="descDev">Shows the live-server test panel in the Network tab.</p>
      </div>
```

- [ ] **Step 2: Label the demo panel honestly**

In the `<details class="demo">` block (now in `#view-network`), give the `<summary>` a note. Change the `#srvHint` empty paragraph area by adding, right after `<summary id="demoTitle">…</summary>`, a note element inside `.demo-body` at the top:

```html
      <p class="hint" id="devPanelNote" style="color:var(--warn); margin-top:0">Test tool — triggers demo/drill alerts only.</p>
```

- [ ] **Step 3: Add `devMode` state + gate logic**

Add to the `Settings` literal: `, devMode:false` (line ~836-837). Add `applyDevMode()` near `applySettingsUI` (line ~1706):

```js
function applyDevMode() {
  const demo = document.querySelector("details.demo");
  if (demo) demo.style.display = Settings.devMode ? "" : "none";
  const sw = $("devSwitch"); if (sw) sw.checked = Settings.devMode;
}
```

(`Settings.devMode` is already restored by the existing `Object.assign(Settings, p.settings)` in `loadPrefs` — no extra merge line needed.)

- [ ] **Step 4: Wire the toggle + call on init and language change**

In `bind()` add:

```js
  $("devSwitch").onchange = e => { Settings.devMode = e.target.checked; applyDevMode(); savePrefs(); };
```

In `init()` after `applySettingsUI();` (line ~1839) add `applyDevMode();`.
In `applyLanguage()` add: `set("lblDev","lbl_dev"); set("descDev","desc_dev"); set("devPanelNote","dev_panel_note");`

- [ ] **Step 5: Add developer i18n keys**

English: `lbl_dev:"Developer / test mode", desc_dev:"Shows the live-server test panel in the Network tab.", dev_panel_note:"Test tool — triggers demo/drill alerts only.",`
Turkish: `lbl_dev:"Geliştirici / test modu", desc_dev:"Ağ sekmesinde canlı sunucu test panelini gösterir.", dev_panel_note:"Test aracı — yalnızca demo/tatbikat alarmı üretir.",`
Russian: `lbl_dev:"Режим разработчика", desc_dev:"Показывает тестовую панель сервера во вкладке «Сеть».", dev_panel_note:"Тестовый инструмент — только демо-сигналы.",`
Arabic: `lbl_dev:"وضع المطور / الاختبار", desc_dev:"يعرض لوحة اختبار الخادم في تبويب الشبكة.", dev_panel_note:"أداة اختبار — تطلق تنبيهات تجريبية فقط.",`

- [ ] **Step 6: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs`
Expected: `CHECK OK …` and `# pass 7 # fail 0`.

- [ ] **Step 7: Manual browser verification**

Fresh load (clear `localStorage` key `tda_prefs` first, or use a private window): the **Network** tab shows the seismogram + detection network but **no** "Demo & test" / "Live server" panel. Turn on **Settings → Developer / test mode**: the "Demo & test" panel appears in the Network tab, topped with the amber "Test tool — triggers demo/drill alerts only." note. Reload — the developer choice persists. Turn it off — the panel disappears again.

---

## Self-Review

**Spec coverage** (against `2026-08-21-tp1-navigation-katastrophen-design.md`):
- Tab shell, 5 tabs, one visible, persisted `activeTab` → Task 3. ✅
- Content relocation, no long-scroll → Task 3 Step 2. ✅
- Global Disasters tab, unified model, type/region/severity filters, 24/48 h, sort, source badges, honest source-down note, empty/loading states → Tasks 2 + 4. ✅
- Developer switch gating the Live-Server panel → Task 5. ✅
- Region default Global, tab bar bottom/left-responsive, dev switch in Settings → Tasks 3-5. ✅
- Acceptance "24 ↔ 48 h changes count", "filters reduce visibly", "each row a source badge", "dev panel hidden without switch" → covered by the manual-verification steps and the unit test. ✅
- Android mirror → **out of scope for this plan** (stated in Global Constraints; separate follow-up plan). ✅

**Severity model (user-confirmed):** two controls — magnitude slider for quakes, GDACS alert-level filter for hazards (Global Constraints). Raw physical values (wind km/h, flood m) are a parked proxy-side follow-up, not TP-1.

**Placeholder scan:** no TBD/TODO; every code step carries complete code; the Task 3 relocation references existing comment-delimited blocks rather than repeating their markup (a move, not new code). ✅

**Type consistency:** `Disaster` shape `{id,typ,schwere,ort,zeit,lat,lon,quelle,url}` is identical in `lib/disasters.js`, the test, and `renderDisasters`. `Settings.disasterFilter` fields `{typen,region,fensterH,minMag,minAlert,sort}` match between the literal, `loadPrefs`, `applyKatUI`, the bindings, and `filterDisasters`/`sortDisasters`. `switchTab` tab names `["start","map","disasters","network","settings"]` match the `data-tab` attributes and the tab-bar buttons. ✅
