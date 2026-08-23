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

// 2) syntax-check lib modules if present
for (const libFile of ["lib/disasters.js", "lib/prep.js"]) {
  const libPath = new URL(libFile, here);
  if (fs.existsSync(libPath)) {
    try { new vm.Script(fs.readFileSync(libPath, "utf8"), { filename: libFile }); }
    catch (e) { errs.push(`${libFile}: ${e.message}`); }
  }
}

// 3) duplicate id="..." scan across the whole document
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
const seen = new Set(), dup = new Set();
for (const id of ids) { if (seen.has(id)) dup.add(id); else seen.add(id); }
if (dup.size) errs.push(`duplicate id(s): ${[...dup].join(", ")}`);

if (errs.length) { console.error("CHECK FAIL:\n" + errs.map(e => "  - " + e).join("\n")); process.exit(1); }
console.log(`CHECK OK — ${inline.length} inline script(s), ${ids.length} ids, no duplicates`);
