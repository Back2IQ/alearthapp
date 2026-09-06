import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

// Guardian Circle & CAP/B2B HTML Component
const growthAndB2BHtml = `
  <!-- GUARDIAN CIRCLE (FAMILY SAFETY NETWORK) -->
  <div class="panel" id="guardianCirclePanel" style="margin-top:14px;">
    <h2><span id="lblGuardianCircleTitle">🛡️ Guardian Circle — Familien-Sicherheitsnetzwerk</span></h2>
    <p style="font-size:0.8rem; color:var(--muted); margin:0 0 12px;" id="lblGuardianCircleDesc">
      Lade deine Familie & engen Kontakte ein. Im Ernstfall siehst du sofort auf 1 Blick, wer in Sicherheit ist.
    </p>

    <div style="display:flex; align-items:center; justify-content:space-between; padding:10px 14px; background:rgba(84,230,205,0.08); border:1px solid rgba(84,230,205,0.25); border-radius:10px; margin-bottom:12px;">
      <div>
        <div style="font-family:var(--font-display); font-weight:700; font-size:0.9rem;" id="lblCircleName">Familie Kiran</div>
        <div style="font-size:0.72rem; color:var(--muted);" id="lblCircleCode">Code: <strong id="valCircleCode" style="color:var(--calm);">BK2IQ-9A8F</strong></div>
      </div>
      <button class="btn accent small" id="btnInviteGuardian">📲 <span id="lblInviteBtn">Einladen</span></button>
    </div>

    <div id="guardianMemberList">
      <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid rgba(255,255,255,0.05); font-size:0.82rem;">
        <span><strong>Du</strong> (Lokales Gerät)</span>
        <span class="expiry-badge ok">🟢 In Sicherheit</span>
      </div>
      <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 0; border-bottom:1px solid rgba(255,255,255,0.05); font-size:0.82rem;">
        <span>Mama (+49170***)</span>
        <span class="expiry-badge soon">🟡 Ausstehend</span>
      </div>
    </div>
  </div>

  <!-- B2B & CAP V1.2 ENTERPRISE ECOSYSTEM PANEL (Developers / Industrial Automation) -->
  <div class="panel" id="enterpriseCapPanel" style="margin-top:14px;">
    <h2><span id="lblCapTitle">🏢 Enterprise & Government Interface (OASIS CAP v1.2)</span></h2>
    <p style="font-size:0.8rem; color:var(--muted); margin:0 0 12px;" id="lblCapDesc">
      Schnittstellen für Katastrophenschutz-Behörden (AFAD/EMSC/GDACS) und Industrie-Gebäudeautomatisierung.
    </p>
    <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
      <button class="btn ghost small" id="btnExportCap">📄 <span id="lblExportCap">CAP v1.2 XML/JSON</span></button>
      <button class="btn ghost small" id="btnTestWebhook">⚡ <span id="lblTestWebhook">Industrie-Webhook</span></button>
    </div>
  </div>
`;

// Insert after hero panel in index.html
html = html.replace('<div class="loc-actions">', growthAndB2BHtml + '\n<div class="loc-actions">');

// Add JS Handlers for Guardian Circle Invite & CAP export
const growthJs = `
function initGrowthAndB2BUI() {
  const btnInvite = $("btnInviteGuardian");
  if (btnInvite) {
    btnInvite.onclick = () => {
      const code = $("valCircleCode") ? $("valCircleCode").textContent : "BK2IQ-9A8F";
      if (window.AndroidBridge && AndroidBridge.shareGuardianInvite) {
        AndroidBridge.shareGuardianInvite(code);
      } else {
        const text = "🛡️ Tritt meinem Alert2IQ Notfall-Familiennetzwerk bei: https://back2iq.com/alert2iq/guardian?invite=" + code;
        navigator.clipboard?.writeText(text);
        toast("📋 Einladungs-Link in Zwischenablage kopiert!");
      }
    };
  }

  const btnCap = $("btnExportCap");
  if (btnCap) {
    btnCap.onclick = () => {
      const sampleCap = {
        "cap": {
          "version": "1.2",
          "identifier": "urn:oid:2.49.0.0.792.0.alert2iq.eq_2026",
          "sender": "alert2iq-engine@back2iq.com",
          "sent": new Date().toISOString(),
          "status": "Actual",
          "msgType": "Alert",
          "scope": "Public",
          "info": [{
            "category": ["Geo"],
            "event": "Earthquake Early Warning",
            "urgency": "Immediate",
            "severity": "Severe",
            "headline": "M6.5 Earthquake Warning — Alert2IQ OASIS CAP v1.2",
            "instruction": "DROP, COVER & HOLD ON immediately."
          }]
        }
      };
      prompt("OASIS CAP v1.2 Standard JSON Output (für AFAD / EMSC / GDACS):", JSON.stringify(sampleCap, null, 2));
    };
  }

  const btnWeb = $("btnTestWebhook");
  if (btnWeb) {
    btnWeb.onclick = () => {
      toast("⚡ Industrie-Webhook ausgelöst: Gasventile schalten auf STOP.");
    };
  }
}
`;

html = html.replace("bind();", "bind();\n  initGrowthAndB2BUI();");

fs.writeFileSync(indexPath, html, "utf-8");
console.log("Growth & B2B/CAP components successfully injected into webapp!");