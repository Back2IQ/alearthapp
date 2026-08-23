# TP-2 (Web) · Onboarding + FAQ-Tab + Vorsorge-Tab — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a skippable first-run onboarding wizard, a FAQ tab (with "restart onboarding"), and a Vorsorge (preparedness) tab with labelled affiliate links — expanding the nav from 5 to 7 tabs — while keeping the app as simple and uncomplicated as possible.

**Architecture:** Extends the single self-contained `tda/webapp/index.html` (no build, runs from `file://`). One new pure module `lib/prep.js` (UMD, like `lib/disasters.js`) holds the only bug-prone logic — the affiliate-URL builder and the profile→settings map — with real Node unit tests. Everything else is markup + CSS + a small amount of view JS wired into the existing `switchTab`/`applyLanguage`/`init` scaffolding. No JS-referenced id is removed; the safety core (`Eew`/`Signing`/`ServerLink`/alert) is untouched except calling the existing `runEarthquakeDrill()` for the probe alarm.

**Tech Stack:** Vanilla HTML/CSS/JS, Leaflet (CDN), Web Notifications API (browser built-in). Node ≥18 for `check.mjs` + unit tests (`node:test`), no new dependencies.

## Global Constraints

- **Web-app only.** Android mirror of these tabs/onboarding is a later teilprojekt.
- **Maximale Einfachheit (binding, from the spec):** every step self-explanatory, every surface plain. Deliberately OMITTED: tick-off/readiness tracker, readiness score, FAQ deep-links, any multi-select or extra status layer. When in doubt, less.
- **Keine Pflichtfelder:** every onboarding step is skippable; one can click straight from step 1 to Finish. Defaults apply (profile *Ausgewogen*, sound on, existing location).
- **Nullkosten / self-contained:** no new dependency, no new CDN, no external asset (Google Fonts already permitted). Affiliate links are static search URLs (no trackers, no user data sent).
- **Honesty:** onboarding steps for Push and Mesh state plainly what works in the browser vs. needs the native app. The Vorsorge tab carries a visible affiliate disclosure; affiliate content never appears in any alarm/SOS/warning surface.
- **Profile values (exact):** Vorsichtig → `radiusKm 500, minMag 2.5`; Ausgewogen (default) → `radiusKm 300, minMag 3.5`; Nur Starkbeben → `radiusKm 150, minMag 5.0`.
- **Affiliate:** placeholder tag `AFFILIATE_TAG = "TDA-PLACEHOLDER-21"`; Amazon links are search URLs on `amazon.com.tr`; local-retailer alternative is a neutral web search.
- **Tabs → 7:** order `["start","map","disasters","network","vorsorge","faq","settings"]`; on the bottom bar (`<900px`) labels are hidden (icons only); on the left rail (`≥900px`) icon + label.
- **i18n scope decision (flagged to the user):** short UI labels (tab names, buttons, section headers, profile names) in all five locales (en/de/tr/ru/ar). Long-form new content (onboarding paragraphs, FAQ answers, product notes) in **en + de complete**; tr/ru/ar intentionally omit those long keys and fall back to `STR.en` via the existing `t()` fallback. No `t()` key may be missing from `STR.en`.
- **Verification:** not a git repo — replace each "Commit" step with `node check.mjs` (syntax + duplicate-id) plus `node --test lib/*.test.mjs`. UI behaviour is checked manually in a browser by the controller.

---

## File Structure

- `tda/webapp/lib/prep.js` — **Create.** Pure UMD module: `buildAffiliateUrl(query, tag)`, `profileToSettings(profile)`, and the static `PREP_CATALOG` data. One responsibility: preparedness data + link/profile helpers, DOM-free and Node-testable.
- `tda/webapp/lib/prep.test.mjs` — **Create.** Real `node:test` assertions.
- `tda/webapp/index.html` — **Modify.** Add 2 tabs (nav + sections + CSS), the onboarding overlay + its JS, the FAQ tab content, the Vorsorge tab rendering (consuming `window.Prep`), and all new i18n keys wired in `applyLanguage`.

---

## Task 1: Pure module `lib/prep.js` (+ tests)

**Files:**
- Create: `tda/webapp/lib/prep.js`
- Test: `tda/webapp/lib/prep.test.mjs`

**Interfaces:**
- Produces (global `window.Prep` / `module.exports`):
  - `buildAffiliateUrl(query, tag) -> string` — `https://www.amazon.com.tr/s?k=<encoded query>&tag=<tag>`.
  - `localSearchUrl(query) -> string` — `https://www.google.com/search?q=<encoded query>`.
  - `profileToSettings(profile) -> { radiusKm, minMag }` — `"vorsichtig"|"ausgewogen"|"starkbeben"`; unknown → Ausgewogen.
  - `PREP_CATALOG` — array of `{ typ, items:[{ key, query }] }` where `typ ∈ "all"|"quake"|"flood"|"storm"|"wildfire"`; `key` is an i18n key stem, `query` the search string.

- [ ] **Step 1: Write the failing test**

Create `tda/webapp/lib/prep.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const P = require("./prep.js");

test("buildAffiliateUrl encodes the query and appends the tag", () => {
  const u = P.buildAffiliateUrl("Erste-Hilfe-Set & Wasser", "TDA-PLACEHOLDER-21");
  assert.ok(u.startsWith("https://www.amazon.com.tr/s?k="));
  assert.ok(u.includes("tag=TDA-PLACEHOLDER-21"));
  assert.ok(u.includes("Erste-Hilfe-Set")); // encoded, not raw spaces/&
  assert.ok(!u.includes(" ")); // no raw spaces
  assert.ok(!/&(?!(k=|tag=))/.test(u.replace("?", "")) || u.split("&").length === 2); // only k & tag params
});

test("localSearchUrl builds a neutral web search", () => {
  const u = P.localSearchUrl("Trillerpfeife");
  assert.ok(u.startsWith("https://www.google.com/search?q="));
  assert.ok(u.includes("Trillerpfeife"));
});

test("profileToSettings maps the three profiles and defaults to ausgewogen", () => {
  assert.deepEqual(P.profileToSettings("vorsichtig"), { radiusKm: 500, minMag: 2.5 });
  assert.deepEqual(P.profileToSettings("ausgewogen"), { radiusKm: 300, minMag: 3.5 });
  assert.deepEqual(P.profileToSettings("starkbeben"), { radiusKm: 150, minMag: 5.0 });
  assert.deepEqual(P.profileToSettings("nonsense"), { radiusKm: 300, minMag: 3.5 });
});

test("PREP_CATALOG is well-formed", () => {
  assert.ok(Array.isArray(P.PREP_CATALOG) && P.PREP_CATALOG.length >= 4);
  for (const sec of P.PREP_CATALOG) {
    assert.ok(typeof sec.typ === "string");
    assert.ok(Array.isArray(sec.items) && sec.items.length >= 1);
    for (const it of sec.items) { assert.ok(it.key && it.query); }
  }
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tda/webapp && node --test lib/prep.test.mjs`
Expected: FAIL — `Cannot find module './prep.js'`.

- [ ] **Step 3: Write the module**

Create `tda/webapp/lib/prep.js`:

```js
// Pure, DOM-free preparedness helpers. UMD: window.Prep (browser classic script,
// NO ES modules — blocked from file://) and module.exports (Node).
(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Prep = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function buildAffiliateUrl(query, tag) {
    return "https://www.amazon.com.tr/s?k=" + encodeURIComponent(query) + "&tag=" + encodeURIComponent(tag);
  }
  function localSearchUrl(query) {
    return "https://www.google.com/search?q=" + encodeURIComponent(query);
  }

  const PROFILES = {
    vorsichtig: { radiusKm: 500, minMag: 2.5 },
    ausgewogen: { radiusKm: 300, minMag: 3.5 },
    starkbeben: { radiusKm: 150, minMag: 5.0 },
  };
  function profileToSettings(profile) {
    return PROFILES[profile] ? { ...PROFILES[profile] } : { ...PROFILES.ausgewogen };
  }

  // key = i18n stem (prep_item_<key> for the name, prep_why_<key> for the one-liner);
  // query = the Amazon/web search term. Kept small and honest (curated, not upsell).
  const PREP_CATALOG = [
    { typ: "all", items: [
      { key: "water", query: "Trinkwasser Notvorrat" },
      { key: "firstaid", query: "Erste-Hilfe-Set" },
      { key: "gobag", query: "Notfallrucksack Fluchtrucksack" },
      { key: "docs", query: "wasserdichte Dokumententasche" },
      { key: "radio", query: "Kurbelradio Notfallradio" },
      { key: "powerbank", query: "Powerbank" },
    ] },
    { typ: "quake", items: [
      { key: "whistle", query: "Trillerpfeife Notsignal" },
      { key: "helmet", query: "Schutzhelm" },
      { key: "mask", query: "FFP2 Staubmaske" },
      { key: "blanket", query: "Rettungsdecke" },
    ] },
    { typ: "flood", items: [
      { key: "drybag", query: "wasserdichte Tasche Dry Bag" },
      { key: "boots", query: "Gummistiefel" },
      { key: "lifevest", query: "Schwimmweste" },
    ] },
    { typ: "storm", items: [
      { key: "flashlight", query: "Taschenlampe LED" },
      { key: "windowfilm", query: "Fensterschutzfolie" },
    ] },
    { typ: "wildfire", items: [
      { key: "smokemask", query: "FFP3 Rauchmaske" },
      { key: "fireblanket", query: "Löschdecke" },
      { key: "goggles", query: "Schutzbrille" },
    ] },
  ];

  return { buildAffiliateUrl, localSearchUrl, profileToSettings, PREP_CATALOG };
});
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tda/webapp && node --test lib/prep.test.mjs`
Expected: PASS — `# pass 4  # fail 0`.

- [ ] **Step 5: Gate**

Run: `node check.mjs && node --test lib/disasters.test.mjs lib/prep.test.mjs`
Expected: `CHECK OK …` (now also syntax-checks `lib/prep.js` — see note), plus all tests pass.

> Note: `check.mjs` currently only syntax-checks `lib/disasters.js`. In this step, extend its lib-check to also compile `lib/prep.js`: in `check.mjs`, where it does the `lib/disasters.js` block, wrap both files — change the single `libPath` check to loop over `["lib/disasters.js","lib/prep.js"]`, compiling each that exists. Keep the rest of `check.mjs` unchanged.

---

## Task 2: 7-tab navigation shell

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: existing `switchTab`, `TABS`, `#tabbar`, `applyLanguage`, `.tab-view` sections.
- Produces: two new tab sections `#view-vorsorge` and `#view-faq` (empty placeholders here, filled in Tasks 4-5), two new tab-bar buttons, updated `TABS`, and mobile icon-only CSS.

- [ ] **Step 1: Update the `TABS` array**

In `index.html`, change (line ~2048):
```js
const TABS = ["start","map","disasters","network","vorsorge","faq","settings"];
```

- [ ] **Step 2: Add the two tab sections (placeholders)**

Immediately BEFORE the `<section class="tab-view" data-tab="settings" id="view-settings">` line (~498), insert:
```html
  <section class="tab-view" data-tab="vorsorge" id="view-vorsorge">
    <!-- filled in Task 5 -->
  </section>

  <section class="tab-view" data-tab="faq" id="view-faq">
    <!-- filled in Task 4 -->
  </section>
```

- [ ] **Step 3: Add the two tab-bar buttons**

In the `<nav class="tabbar">` block, between the `network` button and the `settings` button (~577-578), insert:
```html
    <button data-tab="vorsorge" aria-selected="false"><span class="ic">🎒</span><span id="tabVorsorge">Prepare</span></button>
    <button data-tab="faq" aria-selected="false"><span class="ic">❔</span><span id="tabFaq">FAQ</span></button>
```

- [ ] **Step 4: Add mobile icon-only CSS**

In the `<style>` block, right after the `.tabbar button[aria-selected="true"] {…}` rule (~284), add:
```css
  @media (max-width:899px) { .tabbar button span:not(.ic) { display:none; } .tabbar button { padding:8px 2px; } }
```

- [ ] **Step 5: Add + wire the tab-label i18n keys**

Add to each locale in `STR`: EN `tab_vorsorge:"Prepare", tab_faq:"FAQ",` · DE `tab_vorsorge:"Vorsorge", tab_faq:"FAQ",` · TR `tab_vorsorge:"Hazırlık", tab_faq:"SSS",` · RU `tab_vorsorge:"Подготовка", tab_faq:"Вопросы",` · AR `tab_vorsorge:"الاستعداد", tab_faq:"الأسئلة",`

In `applyLanguage()` where the other `set("tab…")` calls are (~2000), add:
```js
  set("tabVorsorge","tab_vorsorge"); set("tabFaq","tab_faq");
```

- [ ] **Step 6: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs lib/prep.test.mjs`
Expected: `CHECK OK …` (no duplicate ids) and all tests pass.

- [ ] **Step 7: Manual browser verification**

Open `index.html`: seven tabs appear; on a narrow window the bottom bar shows icons only (no wrap); on a wide window the left rail shows icon + label. Tapping Prepare/FAQ shows the (empty) sections; other tabs still work.

---

## Task 3: Onboarding overlay

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: `Settings`, `savePrefs`, `applySettingsUI`, `useGps`, `Eew.CITIES`, `setLocation`, `runEarthquakeDrill`, `quakes`, `Eew.haversineKm`, `Prep.profileToSettings`, `t`.
- Produces: `Settings.onboarded` (bool, default false), `startOnboarding()`, `finishOnboarding()`, `obGoTo(step)`, `renderObRetro()`; overlay `#onboarding`. Shown by `init()` when not onboarded.

- [ ] **Step 1: Add `onboarded` to Settings**

In the `Settings` literal (~1038), add `onboarded:false,` (it is restored by the existing `Object.assign(Settings, p.settings)` in `loadPrefs` — no extra merge line needed).

- [ ] **Step 2: Add the overlay markup**

Immediately AFTER the closing `</div>` of `#reportOverlay` (search for `id="reportOverlay"` and its matching close) — i.e. alongside the other overlays, before the settings sheet/section area — insert:
```html
<div class="overlay" id="onboarding">
  <div class="ob">
    <div class="ob-dots" id="obDots"></div>
    <div class="ob-card" data-ob="1">
      <div class="ob-mark">◎</div>
      <h2 id="ob1t">Welcome to TDA</h2>
      <p id="ob1b">TDA warns you seconds before the shaking arrives and shows every natural hazard near you.</p>
    </div>
    <div class="ob-card" data-ob="2" hidden>
      <h2 id="ob2t">Where should we watch?</h2>
      <p id="ob2b">Use your location, or pick a city. You can change this any time.</p>
      <button class="btn accent wide" id="obGps">📡 <span id="obGpsText">Use my location</span></button>
      <div class="chips" id="obCities" style="margin-top:12px"></div>
    </div>
    <div class="ob-card" data-ob="3" hidden>
      <h2 id="ob3t">Choose a protection level</h2>
      <div class="ob-profiles" id="obProfiles">
        <button class="ob-profile" data-profile="vorsichtig"><b id="obPv">Cautious</b><span id="obPvd">radius 500 km · from M 2.5</span></button>
        <button class="ob-profile" data-profile="ausgewogen" aria-pressed="true"><b id="obPa">Balanced</b><span id="obPad">radius 300 km · from M 3.5</span></button>
        <button class="ob-profile" data-profile="starkbeben"><b id="obPs">Strong only</b><span id="obPsd">radius 150 km · from M 5.0</span></button>
      </div>
      <p class="ob-note" id="obRetro"></p>
      <p class="ob-fine" id="ob3fine">Magnitude = strength; radius = how far away a quake still concerns you. Fine sliders stay in Settings.</p>
    </div>
    <div class="ob-card" data-ob="4" hidden>
      <h2 id="ob4t">Alerts</h2>
      <div class="toggle-row"><span id="ob4sound">Alarm sound</span><label class="switch"><input type="checkbox" id="obSound" checked><span class="slider"></span></label></div>
      <button class="btn wide" id="obNotify" style="margin-top:14px">🔔 <span id="obNotifyText">Enable notifications</span></button>
      <p class="ob-fine" id="ob4fine">Notifications work while the page/app is open. Full push delivery comes with the app.</p>
    </div>
    <div class="ob-card" data-ob="5" hidden>
      <h2 id="ob5t">Offline rescue (in the app)</h2>
      <p id="ob5b">If the mobile network dies, the app can carry warnings and an SOS phone-to-phone over Bluetooth. This works fully only in the native app — not in the browser.</p>
    </div>
    <div class="ob-card" data-ob="6" hidden>
      <h2 id="ob6t">This is what an alert looks like</h2>
      <p id="ob6b">Try a practice alert so you recognise the sound and screen in a real event.</p>
      <button class="btn accent wide" id="obDrill">▶ <span id="obDrillText">Show a practice alert</span></button>
    </div>
    <div class="ob-nav">
      <button class="btn ghost" id="obBack">Back</button>
      <button class="btn ghost" id="obSkip">Skip</button>
      <button class="btn accent" id="obNext">Next</button>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Add overlay CSS**

In `<style>`, before `</style>`, add:
```css
  .ob { min-height:100dvh; max-width:560px; margin:0 auto; padding:40px 22px calc(30px + env(safe-area-inset-bottom)); display:flex; flex-direction:column; }
  .ob-dots { display:flex; gap:8px; justify-content:center; margin-bottom:26px; }
  .ob-dots i { width:8px; height:8px; border-radius:50%; background:var(--border-strong); transition:background .2s, transform .2s; }
  .ob-dots i.on { background:var(--accent); transform:scale(1.25); }
  .ob-card { flex:1; }
  .ob-card h2 { font-family:var(--font-display); font-size:1.5rem; margin:0 0 12px; }
  .ob-card p { color:var(--muted); line-height:1.55; }
  .ob-mark { width:56px; height:56px; border-radius:16px; display:grid; place-items:center; font-size:1.6rem; margin-bottom:16px;
    background:linear-gradient(135deg,color-mix(in srgb,var(--accent) 55%,transparent),color-mix(in srgb,var(--accent-2) 45%,transparent)); }
  .ob-profiles { display:flex; flex-direction:column; gap:10px; margin:6px 0 14px; }
  .ob-profile { text-align:left; padding:14px 16px; border-radius:14px; border:1px solid var(--border); background:var(--panel-strong); color:var(--text); cursor:pointer; display:flex; flex-direction:column; gap:3px; }
  .ob-profile[aria-pressed="true"] { border-color:color-mix(in srgb,var(--accent) 55%,transparent); background:color-mix(in srgb,var(--accent) 14%,transparent); }
  .ob-profile b { font-family:var(--font-display); }
  .ob-profile span { font-size:.82rem; color:var(--muted); }
  .ob-note { color:var(--accent) !important; font-size:.88rem; min-height:1.2em; }
  .ob-fine { font-size:.78rem; color:var(--faint) !important; }
  .ob-nav { display:flex; gap:10px; margin-top:20px; }
  .ob-nav .btn { flex:1; }
```

- [ ] **Step 4: Add the onboarding controller JS**

After `renderStartNearest()` (~2072), add:
```js
let obStep = 1;
const OB_STEPS = 6;
function renderObRetro() {
  const el = $("obRetro"); if (!el) return;
  if (!quakes.length) { el.textContent = ""; return; }
  const within = quakes.filter(q => Eew.haversineKm(State.loc.lat, State.loc.lon, q.lat, q.lon) <= Settings.radiusKm);
  const warned = within.filter(q => (q.mag || 0) >= Settings.minMag).length;
  el.textContent = t("ob_retro").replace("%1", warned).replace("%2", quakes.length);
}
function obGoTo(step) {
  obStep = Math.max(1, Math.min(OB_STEPS, step));
  for (const c of document.querySelectorAll("#onboarding .ob-card")) c.hidden = (+c.dataset.ob !== obStep);
  const dots = $("obDots"); dots.innerHTML = "";
  for (let i = 1; i <= OB_STEPS; i++) { const d = document.createElement("i"); if (i === obStep) d.className = "on"; dots.appendChild(d); }
  $("obBack").style.visibility = obStep === 1 ? "hidden" : "visible";
  $("obNext").textContent = obStep === OB_STEPS ? t("ob_finish") : t("ob_next");
  if (obStep === 3) renderObRetro();
}
function startOnboarding() {
  $("onboarding").classList.add("show");
  // seed profile buttons + city chips to current state
  applyObProfile(profileGuess());
  renderObCities();
  obGoTo(1);
}
function finishOnboarding() {
  Settings.onboarded = true; savePrefs();
  $("onboarding").classList.remove("show");
}
function profileGuess() {
  if (Settings.radiusKm >= 450 && Settings.minMag <= 3) return "vorsichtig";
  if (Settings.radiusKm <= 200 && Settings.minMag >= 4.5) return "starkbeben";
  return "ausgewogen";
}
function applyObProfile(profile) {
  const s = Prep.profileToSettings(profile);
  Settings.radiusKm = s.radiusKm; Settings.minMag = s.minMag;
  for (const b of $("obProfiles").querySelectorAll("button")) b.setAttribute("aria-pressed", String(b.dataset.profile === profile));
  applySettingsUI(); savePrefs(); renderObRetro();
}
function renderObCities() {
  const c = $("obCities"); if (!c) return; c.innerHTML = "";
  Eew.CITIES.forEach(city => {
    const b = document.createElement("button"); b.className = "chip-btn"; b.textContent = city.name;
    b.onclick = () => setLocation(city.name, city.lat, city.lon, "preset", city.id);
    c.appendChild(b);
  });
}
```

- [ ] **Step 5: Wire the onboarding buttons in `bind()`**

In `bind()` (after the tabbar binding, ~2076), add:
```js
  $("obNext").onclick = () => { if (obStep === OB_STEPS) finishOnboarding(); else obGoTo(obStep + 1); };
  $("obBack").onclick = () => obGoTo(obStep - 1);
  $("obSkip").onclick = finishOnboarding;
  $("obGps").onclick = useGps;
  $("obProfiles").onclick = e => { const b = e.target.closest("button"); if (b) applyObProfile(b.dataset.profile); };
  $("obSound").onchange = e => { Settings.sound = e.target.checked; savePrefs(); };
  $("obNotify").onclick = async () => { try { if (window.Notification) { const r = await Notification.requestPermission(); $("obNotifyText").textContent = t(r === "granted" ? "ob_notify_on" : "ob_notify_off"); } } catch(e){} };
  $("obDrill").onclick = runEarthquakeDrill;
```

- [ ] **Step 6: Trigger onboarding on first run**

In `init()`, at the very end (after the `fetchServerHazards…` line, ~2152), add:
```js
  if (!Settings.onboarded) startOnboarding();
```

- [ ] **Step 7: Add + wire i18n (short labels all 5; long-form en+de)**

Add to `STR.en`:
```js
    ob_next:"Next", ob_finish:"Done", ob_back:"Back", ob_skip:"Skip",
    ob1_t:"Welcome to TDA", ob1_b:"TDA warns you seconds before the shaking arrives and shows every natural hazard near you.",
    ob2_t:"Where should we watch?", ob2_b:"Use your location, or pick a city. You can change this any time.", ob_gps:"Use my location",
    ob3_t:"Choose a protection level", p_cautious:"Cautious", p_cautious_d:"radius 500 km · from M 2.5", p_balanced:"Balanced", p_balanced_d:"radius 300 km · from M 3.5", p_strong:"Strong only", p_strong_d:"radius 150 km · from M 5.0",
    ob_retro:"This level would have warned you at %1 of %2 recent quakes nearby (last 24 h — hindsight, not a forecast).",
    ob3_fine:"Magnitude = strength; radius = how far away a quake still concerns you. Fine sliders stay in Settings.",
    ob4_t:"Alerts", ob4_sound:"Alarm sound", ob_notify:"Enable notifications", ob_notify_on:"Notifications on", ob_notify_off:"Notifications off",
    ob4_fine:"Notifications work while the page/app is open. Full push delivery comes with the app.",
    ob5_t:"Offline rescue (in the app)", ob5_b:"If the mobile network dies, the app can carry warnings and an SOS phone-to-phone over Bluetooth. This works fully only in the native app — not in the browser.",
    ob6_t:"This is what an alert looks like", ob6_b:"Try a practice alert so you recognise the sound and screen in a real event.", ob_drill:"Show a practice alert",
```
Add to `STR.de`:
```js
    ob_next:"Weiter", ob_finish:"Fertig", ob_back:"Zurück", ob_skip:"Überspringen",
    ob1_t:"Willkommen bei TDA", ob1_b:"TDA warnt dich Sekunden vor der Erschütterung und zeigt jede Naturgefahr in deiner Nähe.",
    ob2_t:"Wo sollen wir wachen?", ob2_b:"Nutze deinen Standort oder wähle eine Stadt. Du kannst das jederzeit ändern.", ob_gps:"Meinen Standort verwenden",
    ob3_t:"Schutzniveau wählen", p_cautious:"Vorsichtig", p_cautious_d:"Radius 500 km · ab M 2.5", p_balanced:"Ausgewogen", p_balanced_d:"Radius 300 km · ab M 3.5", p_strong:"Nur Starkbeben", p_strong_d:"Radius 150 km · ab M 5.0",
    ob_retro:"Dieses Niveau hätte dich bei %1 von %2 Beben in deiner Nähe gewarnt (letzte 24 Std. — Rückschau, keine Vorhersage).",
    ob3_fine:"Magnitude = Stärke; Radius = wie weit weg dich ein Beben noch betrifft. Fein-Regler bleiben in den Einstellungen.",
    ob4_t:"Alarme", ob4_sound:"Alarmton", ob_notify:"Benachrichtigungen erlauben", ob_notify_on:"Benachrichtigungen an", ob_notify_off:"Benachrichtigungen aus",
    ob4_fine:"Benachrichtigungen funktionieren, solange die Seite/App offen ist. Vollständige Push-Zustellung kommt mit der App.",
    ob5_t:"Offline-Rettung (in der App)", ob5_b:"Wenn das Mobilnetz ausfällt, kann die App Warnungen und einen SOS von Handy zu Handy per Bluetooth tragen. Das funktioniert vollständig nur in der nativen App — nicht im Browser.",
    ob6_t:"So sieht ein Alarm aus", ob6_b:"Probiere einen Übungsalarm, damit du Ton und Bildschirm im Ernstfall wiedererkennst.", ob_drill:"Probe-Alarm zeigen",
```
Add the SHORT profile/nav keys to TR/RU/AR too (long-form ob*_b/ob_retro/*_fine fall back to en):
- TR: `ob_next:"İleri", ob_finish:"Bitti", ob_skip:"Atla", p_cautious:"Dikkatli", p_balanced:"Dengeli", p_strong:"Sadece güçlü", ob_notify:"Bildirimlere izin ver", ob_drill:"Prova alarmı göster",`
- RU: `ob_next:"Далее", ob_finish:"Готово", ob_skip:"Пропустить", p_cautious:"Осторожный", p_balanced:"Сбалансированный", p_strong:"Только сильные", ob_notify:"Включить уведомления", ob_drill:"Показать пробную тревогу",`
- AR: `ob_next:"التالي", ob_finish:"تم", ob_skip:"تخطٍّ", p_cautious:"حذر", p_balanced:"متوازن", p_strong:"القوية فقط", ob_notify:"تفعيل الإشعارات", ob_drill:"عرض تنبيه تجريبي",`

In `applyLanguage()`, after the tab-label sets, add (reuse the existing `set(id,key)` helper already defined at the top of `applyLanguage` — do NOT define a new one):
```js
  set("ob1t","ob1_t"); set("ob1b","ob1_b"); set("ob2t","ob2_t"); set("ob2b","ob2_b"); set("obGpsText","ob_gps");
  set("ob3t","ob3_t"); set("obPv","p_cautious"); set("obPvd","p_cautious_d"); set("obPa","p_balanced"); set("obPad","p_balanced_d"); set("obPs","p_strong"); set("obPsd","p_strong_d"); set("ob3fine","ob3_fine");
  set("ob4t","ob4_t"); set("ob4sound","ob4_sound"); set("obNotifyText","ob_notify"); set("ob4fine","ob4_fine");
  set("ob5t","ob5_t"); set("ob5b","ob5_b"); set("ob6t","ob6_t"); set("ob6b","ob6_b"); set("obDrillText","ob_drill");
  set("obBack","ob_back"); set("obSkip","ob_skip");
  if ($("onboarding").classList.contains("show")) obGoTo(obStep);
```

- [ ] **Step 8: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs lib/prep.test.mjs`
Expected: `CHECK OK …` and all tests pass.

- [ ] **Step 9: Manual browser verification**

Clear `localStorage` (or private window) → onboarding appears on load. Click straight through with **Next** only (no input) to **Done** — it finishes (proves no mandatory fields). Reload → onboarding does NOT reappear. Re-open (Task 4's button) → step 3 shows the live "would have warned you at X of Y" line and changes with the profile; the chosen profile is reflected in Settings (radius/min-mag). Step 4 "Enable notifications" prompts the browser. Step 6 "practice alert" shows the alert overlay.

---

## Task 4: FAQ tab

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: `startOnboarding` (Task 3), `switchTab`, `t`.
- Produces: FAQ content in `#view-faq`.

- [ ] **Step 1: Fill the FAQ section**

Replace the placeholder inside `#view-faq` with:
```html
    <div class="panel">
      <h2 id="faqTitle">FAQ</h2>
      <button class="btn accent wide" id="faqRestart" style="margin-bottom:14px">↻ <span id="faqRestartText">Restart onboarding</span></button>
      <details class="faq-item"><summary id="faqQ1">How early does TDA warn?</summary><p id="faqA1"></p></details>
      <details class="faq-item"><summary id="faqQ2">What do magnitude, depth and radius mean?</summary><p id="faqA2"></p></details>
      <details class="faq-item"><summary id="faqQ3">Why do false alarms happen — and how does TDA catch them?</summary><p id="faqA3"></p></details>
      <details class="faq-item"><summary id="faqQ4">What happens offline / if the network fails?</summary><p id="faqA4"></p></details>
      <details class="faq-item"><summary id="faqQ5">Is my location data safe?</summary><p id="faqA5"></p></details>
      <details class="faq-item"><summary id="faqQ6">Where does the data come from?</summary><p id="faqA6"></p></details>
    </div>
```

- [ ] **Step 2: Add FAQ CSS**

In `<style>`, add:
```css
  .faq-item { border-top:1px solid var(--border); padding:4px 0; }
  .faq-item:first-of-type { border-top:0; }
  .faq-item > summary { list-style:none; cursor:pointer; padding:12px 0; font-weight:600; color:var(--text); display:flex; align-items:center; gap:9px; }
  .faq-item > summary::-webkit-details-marker { display:none; }
  .faq-item > summary::before { content:"+"; color:var(--accent); font-family:var(--font-display); }
  .faq-item[open] > summary::before { content:"–"; }
  .faq-item p { color:var(--muted); line-height:1.55; margin:0 0 12px; padding-left:18px; }
```

- [ ] **Step 3: Wire the restart button**

In `bind()`, add:
```js
  $("faqRestart").onclick = () => startOnboarding();
```

- [ ] **Step 4: Add + wire i18n (en+de full; short label all 5)**

Add to `STR.en`:
```js
    faq_title:"FAQ", faq_restart:"Restart onboarding",
    faq_q1:"How early does TDA warn?", faq_a1:"From a few seconds up to tens of seconds, depending on how far you are from the epicentre — the further away, the more warning. It can never warn before the quake itself begins; it races the shaking, not time.",
    faq_q2:"What do magnitude, depth and radius mean?", faq_a2:"Magnitude is the quake's strength (each step up is much stronger). Depth is how far below ground it started (shallower is felt more). Radius is the distance around you within which a quake concerns you.",
    faq_q3:"Why do false alarms happen — and how does TDA catch them?", faq_a3:"City-wide simultaneous shaking with no travelling wavefront is usually fireworks or traffic, not a quake. TDA checks for a spreading wavefront before alarming, and marks anything it filters out.",
    faq_q4:"What happens offline / if the network fails?", faq_a4:"The map and live feeds need internet. The core early-warning logic runs locally. Phone-to-phone warning and SOS when the network is down work fully only in the native app, not in the browser.",
    faq_q5:"Is my location data safe?", faq_a5:"The detection network only ever shares a coarse grid cell plus a timestamp — never your exact position. Address search and reverse-geocoding go directly to OpenStreetMap.",
    faq_q6:"Where does the data come from?", faq_a6:"Earthquakes from USGS and EMSC (and AFAD where available); other hazards from NASA EONET and GDACS. USGS/EMSC/EONET load directly; GDACS/AFAD are optional and need the local server.",
```
Add to `STR.de`:
```js
    faq_title:"FAQ", faq_restart:"Onboarding erneut starten",
    faq_q1:"Wie früh warnt TDA?", faq_a1:"Von wenigen bis zu einigen zehn Sekunden — je weiter du vom Epizentrum entfernt bist, desto mehr Vorwarnung. Vor dem Beben selbst kann sie nie warnen; sie ist schneller als die Erschütterung, nicht als die Zeit.",
    faq_q2:"Was bedeuten Magnitude, Tiefe und Radius?", faq_a2:"Magnitude ist die Stärke des Bebens (jede Stufe ist deutlich stärker). Tiefe ist, wie weit unter der Erde es begann (flacher = stärker spürbar). Radius ist die Entfernung um dich, innerhalb derer dich ein Beben betrifft.",
    faq_q3:"Warum kommt es zu Fehlalarmen — und wie erkennt TDA sie?", faq_a3:"Stadtweite, gleichzeitige Erschütterung ohne wandernde Wellenfront ist meist Feuerwerk oder Verkehr, kein Beben. TDA prüft auf eine sich ausbreitende Wellenfront, bevor es Alarm gibt, und kennzeichnet, was es herausfiltert.",
    faq_q4:"Was passiert offline / wenn das Netz ausfällt?", faq_a4:"Karte und Live-Daten brauchen Internet. Die Kern-Frühwarnlogik läuft lokal. Warnung und SOS von Handy zu Handy bei totem Netz funktionieren vollständig nur in der nativen App, nicht im Browser.",
    faq_q5:"Sind meine Standortdaten sicher?", faq_a5:"Das Detektionsnetz teilt nur eine grobe Rasterzelle plus Zeitstempel — nie deine genaue Position. Adresssuche und Reverse-Geocoding gehen direkt an OpenStreetMap.",
    faq_q6:"Woher kommen die Daten?", faq_a6:"Erdbeben von USGS und EMSC (und AFAD, wo verfügbar); andere Gefahren von NASA EONET und GDACS. USGS/EMSC/EONET laden direkt; GDACS/AFAD sind optional und brauchen den lokalen Server.",
```
Add the short `faq_title`/`faq_restart` to TR/RU/AR (answers fall back to en):
- TR: `faq_title:"SSS", faq_restart:"Tanıtımı yeniden başlat",`
- RU: `faq_title:"Вопросы", faq_restart:"Заново пройти введение",`
- AR: `faq_title:"الأسئلة الشائعة", faq_restart:"إعادة التعريف",`

In `applyLanguage()` add:
```js
  set("faqTitle","faq_title"); set("faqRestartText","faq_restart");
  for (let i=1;i<=6;i++){ set("faqQ"+i,"faq_q"+i); set("faqA"+i,"faq_a"+i); }
```

- [ ] **Step 5: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs lib/prep.test.mjs`
Expected: `CHECK OK …` and all tests pass.

- [ ] **Step 6: Manual browser verification**

FAQ tab shows six collapsible questions; opening one reveals the answer; "Restart onboarding" opens the wizard at step 1.

---

## Task 5: Vorsorge (preparedness) tab

**Files:**
- Modify: `tda/webapp/index.html`

**Interfaces:**
- Consumes: `window.Prep` (`PREP_CATALOG`, `buildAffiliateUrl`, `localSearchUrl`), `t`, `applyLanguage`.
- Produces: `AFFILIATE_TAG`, `renderPrep()`, content in `#view-vorsorge`.

- [ ] **Step 1: Fill the Vorsorge section**

Replace the placeholder inside `#view-vorsorge` with:
```html
    <div class="panel">
      <h2 id="prepTitle">Prepare</h2>
      <p class="hint" id="prepDisclosure"></p>
      <div id="prepList"></div>
    </div>
```

- [ ] **Step 2: Add Vorsorge CSS**

In `<style>`, add:
```css
  .prep-sec { margin-top:18px; }
  .prep-sec:first-child { margin-top:6px; }
  .prep-sec h3 { font-family:var(--font-display); font-size:.72rem; letter-spacing:.14em; text-transform:uppercase; color:var(--muted); margin:0 0 8px; }
  .prep-item { padding:11px 0; border-top:1px solid var(--border); }
  .prep-item:first-child { border-top:0; }
  .prep-name { font-weight:600; }
  .prep-why { font-size:.8rem; color:var(--faint); margin:2px 0 8px; }
  .prep-links { display:flex; gap:10px; flex-wrap:wrap; }
  .prep-links a { font-size:.82rem; padding:7px 12px; border-radius:100px; border:1px solid var(--border); color:var(--text); text-decoration:none; background:var(--panel-strong); }
  .prep-links a.amz { border-color:color-mix(in srgb,var(--accent) 45%,transparent); }
```

- [ ] **Step 3: Add the render logic**

Near the other render functions, add:
```js
const AFFILIATE_TAG = "TDA-PLACEHOLDER-21"; // replace with a real Amazon Associates tag to earn
function renderPrep() {
  if (!window.Prep) return;
  $("prepDisclosure").textContent = t("prep_disclosure");
  const box = $("prepList"); box.innerHTML = "";
  for (const sec of Prep.PREP_CATALOG) {
    const s = document.createElement("div"); s.className = "prep-sec";
    const h = document.createElement("h3"); h.textContent = t("prep_sec_" + sec.typ); s.appendChild(h);
    for (const it of sec.items) {
      const d = document.createElement("div"); d.className = "prep-item";
      d.innerHTML = `<div class="prep-name">${t("prep_item_" + it.key)}</div>
        <div class="prep-why">${t("prep_why_" + it.key)}</div>
        <div class="prep-links">
          <a class="amz" target="_blank" rel="noopener nofollow sponsored" href="${Prep.buildAffiliateUrl(it.query, AFFILIATE_TAG)}">${t("prep_amazon")}</a>
          <a target="_blank" rel="noopener" href="${Prep.localSearchUrl(it.query)}">${t("prep_local")}</a>
        </div>`;
      s.appendChild(d);
    }
    box.appendChild(s);
  }
}
```

- [ ] **Step 4: Render on tab-show and language change**

In `switchTab`, next to the disasters line (~2055), add:
```js
  if (name === "vorsorge" && typeof renderPrep === "function") renderPrep();
```
In `applyLanguage()`, add: `S("prepTitle","prep_title"); if (State.activeTab === "vorsorge") renderPrep();`

- [ ] **Step 5: Add i18n (en+de full; short label all 5)**

Add to `STR.en`:
```js
    prep_title:"Prepare", prep_amazon:"Search on Amazon", prep_local:"Local retailer",
    prep_disclosure:"Recommendation links — if you buy, TDA may earn a small commission at no extra cost to you. Shown only here, never during an alert.",
    prep_sec_all:"For every disaster", prep_sec_quake:"Earthquake", prep_sec_flood:"Flood", prep_sec_storm:"Storm", prep_sec_wildfire:"Wildfire",
    prep_item_water:"Drinking water", prep_why_water:"A stored supply for several days.",
    prep_item_firstaid:"First-aid kit", prep_why_firstaid:"Treat injuries when help is delayed.",
    prep_item_gobag:"Go-bag", prep_why_gobag:"Grab-and-leave essentials in one bag.",
    prep_item_docs:"Document pouch", prep_why_docs:"Waterproof copies of IDs and papers.",
    prep_item_radio:"Hand-crank radio", prep_why_radio:"Get news when power and network are down.",
    prep_item_powerbank:"Power bank", prep_why_powerbank:"Keep your phone — your lifeline — alive.",
    prep_item_whistle:"Whistle", prep_why_whistle:"Signal rescuers if you are trapped.",
    prep_item_helmet:"Helmet", prep_why_helmet:"Protect your head from falling debris.",
    prep_item_mask:"FFP2 dust mask", prep_why_mask:"Breathe through concrete dust.",
    prep_item_blanket:"Emergency blanket", prep_why_blanket:"Keep warm outdoors after a quake.",
    prep_item_drybag:"Dry bag", prep_why_drybag:"Keep essentials dry in a flood.",
    prep_item_boots:"Rubber boots", prep_why_boots:"Wade safely through water.",
    prep_item_lifevest:"Life vest", prep_why_lifevest:"Stay afloat in rising water.",
    prep_item_flashlight:"Flashlight", prep_why_flashlight:"See in a blackout.",
    prep_item_windowfilm:"Window safety film", prep_why_windowfilm:"Hold glass together in a storm.",
    prep_item_smokemask:"FFP3 smoke mask", prep_why_smokemask:"Filter wildfire smoke.",
    prep_item_fireblanket:"Fire blanket", prep_why_fireblanket:"Smother small fires.",
    prep_item_goggles:"Safety goggles", prep_why_goggles:"Protect eyes from smoke and ash.",
```
Add to `STR.de`:
```js
    prep_title:"Vorsorge", prep_amazon:"Bei Amazon suchen", prep_local:"Lokaler Händler",
    prep_disclosure:"Empfehlungs-Links — bei einem Kauf kann TDA eine kleine Provision erhalten, ohne Mehrkosten für dich. Nur hier zu sehen, nie während eines Alarms.",
    prep_sec_all:"Für jede Katastrophe", prep_sec_quake:"Erdbeben", prep_sec_flood:"Überschwemmung", prep_sec_storm:"Sturm", prep_sec_wildfire:"Waldbrand",
    prep_item_water:"Trinkwasser", prep_why_water:"Ein Vorrat für mehrere Tage.",
    prep_item_firstaid:"Erste-Hilfe-Set", prep_why_firstaid:"Verletzungen versorgen, wenn Hilfe sich verzögert.",
    prep_item_gobag:"Notfallrucksack", prep_why_gobag:"Das Wichtigste griffbereit in einer Tasche.",
    prep_item_docs:"Dokumentenmappe", prep_why_docs:"Wasserdichte Kopien von Ausweisen und Papieren.",
    prep_item_radio:"Kurbelradio", prep_why_radio:"Nachrichten ohne Strom und Netz.",
    prep_item_powerbank:"Powerbank", prep_why_powerbank:"Hält dein Handy — deine Lebensader — am Leben.",
    prep_item_whistle:"Trillerpfeife", prep_why_whistle:"Rettern signalisieren, wenn du verschüttet bist.",
    prep_item_helmet:"Schutzhelm", prep_why_helmet:"Schützt den Kopf vor herabfallenden Trümmern.",
    prep_item_mask:"FFP2-Staubmaske", prep_why_mask:"Atmen im Betonstaub.",
    prep_item_blanket:"Rettungsdecke", prep_why_blanket:"Wärme im Freien nach dem Beben.",
    prep_item_drybag:"Wasserdichte Tasche", prep_why_drybag:"Wichtiges trocken halten bei Flut.",
    prep_item_boots:"Gummistiefel", prep_why_boots:"Sicher durchs Wasser waten.",
    prep_item_lifevest:"Schwimmweste", prep_why_lifevest:"Über Wasser bleiben bei steigendem Pegel.",
    prep_item_flashlight:"Taschenlampe", prep_why_flashlight:"Sehen bei Stromausfall.",
    prep_item_windowfilm:"Fensterschutzfolie", prep_why_windowfilm:"Hält Glas im Sturm zusammen.",
    prep_item_smokemask:"FFP3-Rauchmaske", prep_why_smokemask:"Filtert Waldbrandrauch.",
    prep_item_fireblanket:"Löschdecke", prep_why_fireblanket:"Erstickt kleine Brände.",
    prep_item_goggles:"Schutzbrille", prep_why_goggles:"Schützt die Augen vor Rauch und Asche.",
```
Add the short labels to TR/RU/AR (items/why fall back to en):
- TR: `prep_title:"Hazırlık", prep_amazon:"Amazon'da ara", prep_local:"Yerel satıcı", prep_disclosure:"Öneri bağlantıları — satın alırsan TDA küçük bir komisyon kazanabilir, sana ek ücret olmadan. Yalnızca burada, asla alarm sırasında.", prep_sec_all:"Her afet için", prep_sec_quake:"Deprem", prep_sec_flood:"Sel", prep_sec_storm:"Fırtına", prep_sec_wildfire:"Orman yangını",`
- RU: `prep_title:"Подготовка", prep_amazon:"Искать на Amazon", prep_local:"Местный магазин", prep_disclosure:"Рекомендательные ссылки — при покупке TDA может получить небольшую комиссию без доплаты для вас. Только здесь, никогда во время тревоги.", prep_sec_all:"Для любой катастрофы", prep_sec_quake:"Землетрясение", prep_sec_flood:"Наводнение", prep_sec_storm:"Шторм", prep_sec_wildfire:"Пожар",`
- AR: `prep_title:"الاستعداد", prep_amazon:"ابحث في أمازون", prep_local:"متجر محلي", prep_disclosure:"روابط توصية — عند الشراء قد يحصل TDA على عمولة صغيرة دون تكلفة إضافية عليك. تظهر هنا فقط، وليس أثناء التنبيه.", prep_sec_all:"لكل كارثة", prep_sec_quake:"زلزال", prep_sec_flood:"فيضان", prep_sec_storm:"عاصفة", prep_sec_wildfire:"حريق",`

(The `prep_item_*`/`prep_why_*` long keys exist only in en+de; tr/ru/ar fall back to en per the Global Constraints i18n decision.)

- [ ] **Step 6: Gate**

Run: `cd tda/webapp && node check.mjs && node --test lib/disasters.test.mjs lib/prep.test.mjs`
Expected: `CHECK OK …` and all tests pass.

- [ ] **Step 7: Manual browser verification**

Vorsorge tab shows the disclosure line then sections per hazard type; each item has a name, a one-liner, and two links; "Search on Amazon" opens an `amazon.com.tr` search with `tag=TDA-PLACEHOLDER-21`; "Local retailer" opens a neutral web search. Confirm no affiliate content appears on the alert overlay or any warning surface.

---

## Self-Review

**Spec coverage:**
- Onboarding (6 skippable steps, no mandatory fields, profiles 500/2.5·300/3.5·150/5.0, live retrospective, probe alarm, honest push/mesh) → Task 3. ✅
- FAQ tab + restart → Task 4. ✅
- Vorsorge tab (per-hazard list, disclosure, placeholder-tag affiliate search links + local alt, guardrails) → Tasks 1+5. ✅
- 7 tabs, mobile icon-only → Task 2. ✅
- Pure logic + tests (`buildAffiliateUrl`, `profileToSettings`) → Task 1. ✅
- Maximale Einfachheit; no tracker/score/deep-links → honoured (nothing of the sort is built). ✅
- i18n scope (short labels all 5; long-form en+de + fallback) → Global Constraints + each task. ✅

**Placeholder scan:** no TBD/TODO; every code step carries complete code; the only intentional runtime placeholder is `AFFILIATE_TAG = "TDA-PLACEHOLDER-21"` (a spec-mandated value, not a plan gap).

**Type consistency:** `profileToSettings` return `{radiusKm,minMag}` matches `applyObProfile`. `PREP_CATALOG` shape `{typ, items:[{key,query}]}` matches `renderPrep` and the test. Tab names `["start","map","disasters","network","vorsorge","faq","settings"]` match the `data-tab` attributes, the tab-bar buttons, and `switchTab`. i18n key stems (`prep_item_`+key / `prep_why_`+key / `prep_sec_`+typ, `ob*`, `faq_*`) are consistent between markup, render code, and the `STR` additions. The `S(id,key)` helper is defined once in `applyLanguage` (Task 3 Step 7) and reused in Tasks 4-5 — Task 3 must land before 4/5 (it does, by order).
