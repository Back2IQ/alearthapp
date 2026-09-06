import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

// Bio-Seismology & Fault-Stress HTML Component
const bioAndFaultHtml = `
  <!-- BIO-SEISMOLOGY & PET ANOMALY WATCH -->
  <div class="panel" id="bioAnomalyPanel" style="margin-top:14px;">
    <h2><span id="lblBioTitle">🐾 Bio-Sensorik — Haustier-Unruhe melden</span></h2>
    <p style="font-size:0.8rem; color:var(--muted); margin:0 0 12px;" id="lblBioDesc">
      Tiere spüren piezoelektrische Ionen & Infraschall Stunden vor dem Hauptbeben. Melde auffällige Unruhe deines Tieres.
    </p>

    <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:10px;">
      <button class="btn ghost small" id="btnReportDog">🐕 <span id="lblDogBtn">Hund unruhig</span></button>
      <button class="btn ghost small" id="btnReportCat">🐈 <span id="lblCatBtn">Katze panisch</span></button>
    </div>
  </div>

  <!-- FAULT-STRESS & MICRO-SWARM RADAR -->
  <div class="panel" id="faultStressPanel" style="margin-top:14px;">
    <h2><span id="lblFaultTitle">🌋 Fault-Stress Radar — Tektonischer Spannungsmonitor</span></h2>
    <p style="font-size:0.8rem; color:var(--muted); margin:0 0 12px;" id="lblFaultDesc">
      Überwachung der Hauptverwerfungen und Vorbeben-Mikroschwärme.
    </p>

    <div style="display:flex; flex-direction:column; gap:8px;" id="faultList">
      <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 12px; background:rgba(255,255,255,0.03); border:1px solid var(--border); border-radius:8px; font-size:0.82rem;">
        <div>
          <strong style="font-family:var(--font-display); color:var(--text);">Nordanatolische Verwerfung (Marmara)</strong>
          <div style="font-size:0.7rem; color:var(--muted);">Letzter Großbruch: 1766 · Defizit: 6.2 m</div>
        </div>
        <span class="expiry-badge soon">🟡 Höchstes Defizit</span>
      </div>

      <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 12px; background:rgba(255,255,255,0.03); border:1px solid var(--border); border-radius:8px; font-size:0.82rem;">
        <div>
          <strong style="font-family:var(--font-display); color:var(--text);">Hellenischer & Ägäischer Bogen</strong>
          <div style="font-size:0.7rem; color:var(--muted);">Geschwindigkeit: 35 mm/Jahr</div>
        </div>
        <span class="expiry-badge ok">🟢 Normal</span>
      </div>
    </div>
  </div>
`;

// Insert into index.html
html = html.replace('<div class="panel" id="guardianCirclePanel"', bioAndFaultHtml + '\n<div class="panel" id="guardianCirclePanel"');

// Add JS Handlers
const bioJs = `
function initBioAndFaultUI() {
  const btnDog = $("btnReportDog");
  if (btnDog) {
    btnDog.onclick = () => {
      toast("🐾 Hund-Unruhe gemeldet. Vielen Dank für deinen Beitrag zur Bio-Sensorik!");
    };
  }

  const btnCat = $("btnReportCat");
  if (btnCat) {
    btnCat.onclick = () => {
      toast("🐾 Katze-Unruhe gemeldet. Vielen Dank für deinen Beitrag zur Bio-Sensorik!");
    };
  }
}
`;

html = html.replace("bind();", "bind();\n  initBioAndFaultUI();");

fs.writeFileSync(indexPath, html, "utf-8");
console.log("Bio-Seismology & Fault-Stress components successfully injected into webapp!");