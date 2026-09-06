import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

// 1. Add World-Class Aerospace & High-Stress Glassmorphism Styles
const extraStyles = `
  /* ==========================================================================
     Alert2IQ World-Class Aerospace & High-Stress Tactical Design System
     ========================================================================== */
  [dir="rtl"] { direction: rtl; text-align: right; }
  [dir="rtl"] .brand .settings-btn { margin-left: 0; margin-right: auto; }
  [dir="rtl"] .hero .pulse::after { animation-direction: reverse; }
  [dir="rtl"] .header-actions { margin-left: 0; margin-right: auto; }

  .header-actions { display:flex; align-items:center; gap:8px; margin-left:auto; }
  .hud-badge { display:inline-flex; align-items:center; gap:5px; padding:5px 10px; border-radius:20px; font-family:var(--font-display); font-size:0.75rem; font-weight:600; background:rgba(84,230,205,0.08); border:1px solid rgba(84,230,205,0.25); color:var(--calm); cursor:pointer; transition:all .2s ease; }
  .hud-badge:hover { background:rgba(84,230,205,0.18); border-color:var(--calm); transform:translateY(-1px); }
  .hud-badge.warn { background:rgba(242,188,105,0.12); border-color:rgba(242,188,105,0.35); color:var(--warn); }
  .hud-badge.alarm { background:rgba(255,82,69,0.14); border-color:rgba(255,82,69,0.45); color:var(--confirm); }

  /* 60-FPS Seismic Wavefront Simulation Canvas */
  .wave-canvas-wrap { position:relative; width:100%; height:200px; margin:16px 0; border-radius:var(--radius); overflow:hidden; background:radial-gradient(circle at center, rgba(12,28,35,0.95), #050b10); border:1px solid var(--border-strong); box-shadow:inset 0 0 30px rgba(0,0,0,0.8); }
  #seismicWaveCanvas { width:100%; height:100%; display:block; }
  .wave-legend { position:absolute; bottom:8px; left:10px; right:10px; display:flex; justify-content:space-between; font-size:0.7rem; font-family:var(--font-display); color:var(--muted); pointer-events:none; }
  .legend-p { color:#54e6cd; display:inline-flex; align-items:center; gap:4px; }
  .legend-p::before { content:""; display:inline-block; width:8px; height:8px; border-radius:50%; background:#54e6cd; }
  .legend-s { color:#ff6d62; display:inline-flex; align-items:center; gap:4px; }
  .legend-s::before { content:""; display:inline-block; width:8px; height:8px; border-radius:50%; background:#ff6d62; }

  /* High-Stress Big Action Buttons (Min 64px) */
  .tactical-actions { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:20px; width:100%; }
  .btn-tactical { min-height:64px; border-radius:12px; font-family:var(--font-display); font-size:1.05rem; font-weight:700; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px; border:none; cursor:pointer; transition:all .15s ease; box-shadow:0 8px 24px rgba(0,0,0,0.4); }
  .btn-tactical:active { transform:scale(0.97); }
  .btn-tactical.safe { background:linear-gradient(135deg, #10b981, #059669); color:#ffffff; }
  .btn-tactical.sos { background:linear-gradient(135deg, #ef4444, #b91c1c); color:#ffffff; animation:sos-glow 1.5s infinite alternate; }
  .btn-tactical .subtext { font-size:0.7rem; font-weight:500; opacity:0.85; text-transform:uppercase; letter-spacing:0.05em; }
  @keyframes sos-glow { 0% { box-shadow:0 0 15px rgba(239,68,68,0.4); } 100% { box-shadow:0 0 35px rgba(239,68,68,0.85); } }

  /* Encrypted Emergency Vault Drawer & Modals */
  .drawer-overlay { position:fixed; inset:0; z-index:100; background:rgba(2,10,13,0.78); backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px); display:none; place-items:center; padding:16px; opacity:0; transition:opacity .25s ease; }
  .drawer-overlay.show { display:grid; opacity:1; }
  .drawer-modal { width:100%; max-width:520px; max-height:88vh; overflow-y:auto; background:var(--panel-strong); border:1px solid var(--border-strong); border-radius:var(--radius); padding:24px; box-shadow:var(--shadow); position:relative; animation:slide-up .25s cubic-bezier(0.16, 1, 0.3, 1); }
  @keyframes slide-up { from { transform:translateY(24px); opacity:0; } to { transform:translateY(0); opacity:1; } }
  
  .vault-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; border-bottom:1px solid var(--border); padding-bottom:12px; }
  .vault-title { font-family:var(--font-display); font-size:1.15rem; font-weight:700; margin:0; display:flex; align-items:center; gap:8px; }
  .vault-seal { display:inline-flex; align-items:center; gap:4px; font-size:0.7rem; font-weight:600; padding:3px 8px; border-radius:4px; background:rgba(84,230,205,0.12); color:var(--calm); border:1px solid rgba(84,230,205,0.3); }

  .pill-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:8px; margin:10px 0 16px; }
  .blood-pill { padding:8px 0; text-align:center; font-family:var(--font-display); font-weight:600; font-size:0.85rem; border-radius:8px; border:1px solid var(--border); background:rgba(255,255,255,0.03); color:var(--text); cursor:pointer; transition:all .15s ease; }
  .blood-pill[aria-pressed="true"] { background:var(--calm); color:#051417; border-color:var(--calm); font-weight:700; box-shadow:0 0 12px rgba(84,230,205,0.4); }

  .triage-toggle-card { display:flex; align-items:center; justify-content:space-between; padding:12px 14px; background:rgba(0,0,0,0.25); border:1px solid var(--border); border-radius:10px; margin-bottom:8px; }
  .triage-toggle-info { display:flex; align-items:center; gap:10px; }
  .triage-toggle-info .icon { font-size:1.25rem; }
  .triage-toggle-info .title { font-family:var(--font-display); font-weight:600; font-size:0.88rem; }
  .triage-toggle-info .desc { font-size:0.72rem; color:var(--muted); }

  .optin-box { background:rgba(242,188,105,0.07); border:1px dashed rgba(242,188,105,0.4); border-radius:10px; padding:14px; margin:16px 0; }
  .optin-box label { display:flex; align-items:flex-start; gap:10px; font-size:0.8rem; color:var(--text); cursor:pointer; }
  .optin-box input[type="checkbox"] { margin-top:2px; accent-color:var(--warn); }

  /* Go-Bag Readiness SVG Gauge */
  .readiness-hero { display:flex; align-items:center; gap:20px; padding:18px; background:rgba(0,0,0,0.2); border-radius:12px; margin-bottom:18px; border:1px solid var(--border); }
  .readiness-ring { width:88px; height:88px; position:relative; flex-shrink:0; display:grid; place-items:center; }
  .readiness-ring svg { transform:rotate(-90deg); width:100%; height:100%; }
  .readiness-score-text { position:absolute; font-family:var(--font-display); font-weight:700; font-size:1.25rem; }
  .readiness-info h3 { margin:0 0 4px; font-family:var(--font-display); font-size:1rem; font-weight:600; }
  .readiness-info p { margin:0; font-size:0.8rem; color:var(--muted); }

  .gobag-cat { margin-bottom:14px; background:rgba(255,255,255,0.02); border:1px solid var(--border); border-radius:10px; padding:12px; }
  .gobag-cat-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; font-family:var(--font-display); font-weight:600; font-size:0.85rem; color:var(--calm); }
  .gobag-item { display:flex; align-items:center; justify-content:space-between; padding:6px 0; border-bottom:1px solid rgba(255,255,255,0.05); font-size:0.82rem; }
  .gobag-item:last-child { border-bottom:none; }
  .gobag-item label { display:flex; align-items:center; gap:8px; cursor:pointer; flex:1; min-width:0; }
  .expiry-badge { font-size:0.68rem; font-weight:600; padding:2px 6px; border-radius:4px; }
  .expiry-badge.ok { background:rgba(16,185,129,0.15); color:#34d399; }
  .expiry-badge.soon { background:rgba(242,188,105,0.18); color:#f2bc69; }
  .expiry-badge.expired { background:rgba(239,68,68,0.2); color:#f87171; }

  /* 60-Second Survival Academy Quiz Cards */
  .quiz-card { background:rgba(0,0,0,0.28); border:1px solid var(--border); border-radius:12px; padding:18px; margin-bottom:16px; }
  .quiz-step { font-family:var(--font-display); font-size:0.7rem; font-weight:700; text-transform:uppercase; color:var(--faint); letter-spacing:0.1em; margin-bottom:6px; }
  .quiz-question { font-family:var(--font-display); font-size:0.98rem; font-weight:600; margin:0 0 14px; line-height:1.35; }
  .quiz-option { width:100%; text-align:left; padding:11px 14px; margin-bottom:8px; border-radius:8px; border:1px solid var(--border); background:rgba(255,255,255,0.03); color:var(--text); font-size:0.85rem; cursor:pointer; transition:all .15s ease; font-family:var(--font-body); display:block; }
  [dir="rtl"] .quiz-option { text-align:right; }
  .quiz-option:hover:not([disabled]) { background:rgba(84,230,205,0.1); border-color:var(--calm); }
  .quiz-option.correct { background:rgba(16,185,129,0.25) !important; border-color:#10b981 !important; color:#ffffff !important; font-weight:600; }
  .quiz-option.wrong { background:rgba(239,68,68,0.25) !important; border-color:#ef4444 !important; color:#ffffff !important; }
  .quiz-feedback { padding:10px 12px; border-radius:8px; font-size:0.8rem; line-height:1.4; margin-top:10px; display:none; animation:fade-in .2s ease; }
  .quiz-feedback.show { display:block; }
  .quiz-feedback.correct { background:rgba(16,185,129,0.15); border:1px solid rgba(16,185,129,0.3); color:#a7f3d0; }
  .quiz-feedback.wrong { background:rgba(239,68,68,0.15); border:1px solid rgba(239,68,68,0.3); color:#fecaca; }
  @keyframes fade-in { from { opacity:0; } to { opacity:1; } }

  .badge-award { text-align:center; padding:24px 16px; background:linear-gradient(135deg, rgba(84,230,205,0.15), rgba(12,28,35,0.9)); border:1px solid var(--border-strong); border-radius:14px; }
  .badge-icon { font-size:3.5rem; margin-bottom:10px; filter:drop-shadow(0 0 20px rgba(84,230,205,0.6)); }
  .badge-title { font-family:var(--font-display); font-size:1.3rem; font-weight:700; color:var(--calm); margin:0 0 6px; }
  .badge-score { font-size:0.9rem; color:var(--muted); margin-bottom:16px; }
`;

// Inject Extra Styles right before </style>
html = html.replace("</style>", extraStyles + "\n</style>");

// 2. Inject Header Quick-Access Badges
const headerBadges = `
      <div class="header-actions">
        <button class="hud-badge" id="btnOpenVault" title="Encrypted Emergency Vault">🔒 <span id="lblHeaderVault">Pass</span></button>
        <button class="hud-badge" id="btnOpenQuiz" title="Survival Academy Drill">🎓 <span id="lblHeaderQuiz">Academy</span></button>
      </div>
`;
html = html.replace('<div class="brand">', '<div class="brand">' + headerBadges);

// 3. Inject 60-FPS Wavefront Canvas and Tactical High-Stress Actions into #alertScreen
const waveCanvasAndTactical = `
    <!-- Real-time 60-FPS Seismic Wavefront Visualizer -->
    <div class="wave-canvas-wrap">
      <canvas id="seismicWaveCanvas" width="480" height="200"></canvas>
      <div class="wave-legend">
        <span class="legend-p">P-Wave (~6.0 km/s)</span>
        <span class="legend-s">S-Wave (~3.5 km/s)</span>
      </div>
    </div>

    <!-- High-Stress Tactical Action Controls (min 64px) -->
    <div class="tactical-actions">
      <button class="btn-tactical safe" id="btnAlertSafe">
        <span>🛡️ <span id="lblAlertSafe">I AM SAFE</span></span>
        <span class="subtext" id="lblAlertSafeSub">Notify Contacts</span>
      </button>
      <button class="btn-tactical sos" id="btnAlertSos">
        <span>🆘 <span id="lblAlertSos">SOS RESCUE</span></span>
        <span class="subtext" id="lblAlertSosSub">Start BLE Beacon</span>
      </button>
    </div>
`;
html = html.replace('<div class="instruct" id="instruct">DROP · COVER · HOLD ON</div>', '<div class="instruct" id="instruct">DROP · COVER · HOLD ON</div>\n' + waveCanvasAndTactical);

// 4. Inject Encrypted Emergency Vault Drawer Modal
const vaultModalHtml = `
<!-- ENCRYPTED EMERGENCY VAULT DRAWER (AES-GCM-256) -->
<div class="drawer-overlay" id="vaultModal">
  <div class="drawer-modal">
    <div class="vault-header">
      <h2 class="vault-title"><span>🛡️</span> <span id="vaultModalTitle">Emergency Vault</span></h2>
      <span class="vault-seal">🔒 AES-GCM-256</span>
      <button class="btn ghost small" id="btnCloseVault">✕</button>
    </div>

    <p style="font-size:0.8rem; color:var(--muted); margin:0 0 16px;" id="vaultModalDesc">
      Your vital medical data is encrypted locally on this device only. It is never uploaded to any cloud server.
    </p>

    <div class="field-row" style="margin-bottom:12px;">
      <label style="font-size:0.78rem; font-family:var(--font-display); font-weight:600; color:var(--muted);" id="lblVaultName">Full Name / Initials</label>
      <input type="text" id="vaultFullName" placeholder="e.g. Deniz K." autocomplete="off" style="width:100%; margin-top:4px;">
    </div>

    <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:12px;">
      <div>
        <label style="font-size:0.78rem; font-family:var(--font-display); font-weight:600; color:var(--muted);" id="lblVaultAge">Age</label>
        <input type="number" id="vaultAge" placeholder="e.g. 34" min="1" max="120" style="width:100%; margin-top:4px;">
      </div>
      <div>
        <label style="font-size:0.78rem; font-family:var(--font-display); font-weight:600; color:var(--muted);" id="lblVaultGender">Gender</label>
        <select id="vaultGender" style="width:100%; margin-top:4px; height:42px; background:var(--panel); border:1px solid var(--border); color:var(--text); border-radius:8px; padding:0 8px;">
          <option value="">—</option>
          <option value="M">Male</option>
          <option value="F">Female</option>
          <option value="D">Diverse / Other</option>
        </select>
      </div>
    </div>

    <label style="font-size:0.78rem; font-family:var(--font-display); font-weight:600; color:var(--muted);" id="lblVaultBlood">Blood Type</label>
    <div class="pill-grid" id="bloodPillGrid">
      <button class="blood-pill" data-blood="0-">0-</button>
      <button class="blood-pill" data-blood="0+">0+</button>
      <button class="blood-pill" data-blood="A-">A-</button>
      <button class="blood-pill" data-blood="A+">A+</button>
      <button class="blood-pill" data-blood="B-">B-</button>
      <button class="blood-pill" data-blood="B+">B+</button>
      <button class="blood-pill" data-blood="AB-">AB-</button>
      <button class="blood-pill" data-blood="AB+">AB+</button>
    </div>

    <label style="font-size:0.78rem; font-family:var(--font-display); font-weight:600; color:var(--muted); margin-top:6px; display:block;" id="lblVaultTriage">Life-Safety Triage Flags</label>
    
    <div class="triage-toggle-card">
      <div class="triage-toggle-info">
        <span class="icon">💉</span>
        <div>
          <div class="title" id="lblFlagInsulinTitle">Insulin / Diabetes</div>
          <div class="desc" id="lblFlagInsulinDesc">Priority for emergency glucose/insulin supply</div>
        </div>
      </div>
      <label class="switch"><input type="checkbox" id="vaultFlagInsulin"><span class="slider"></span></label>
    </div>

    <div class="triage-toggle-card">
      <div class="triage-toggle-info">
        <span class="icon">❤️</span>
        <div>
          <div class="title" id="lblFlagHeartTitle">Heart Condition</div>
          <div class="desc" id="lblFlagHeartDesc">Requires cardiovascular monitoring</div>
        </div>
      </div>
      <label class="switch"><input type="checkbox" id="vaultFlagHeart"><span class="slider"></span></label>
    </div>

    <div class="triage-toggle-card">
      <div class="triage-toggle-info">
        <span class="icon">🫁</span>
        <div>
          <div class="title" id="lblFlagAsthmaTitle">Respiratory / Asthma</div>
          <div class="desc" id="lblFlagAsthmaDesc">High risk in heavy debris dust & smoke</div>
        </div>
      </div>
      <label class="switch"><input type="checkbox" id="vaultFlagAsthma"><span class="slider"></span></label>
    </div>

    <div class="optin-box">
      <label>
        <input type="checkbox" id="vaultOptInBroadcast">
        <span id="lblVaultOptInText">
          <strong>Art. 9 GDPR Consent:</strong> In a severe disaster, broadcast my blood type & vital flags over local Bluetooth (BLE Mesh) to nearby first responders within 30–50m.
        </span>
      </label>
    </div>

    <div style="display:flex; flex-direction:column; gap:8px; margin-top:16px;">
      <button class="btn accent wide" id="btnSaveVault" style="min-height:48px;">🔒 <span id="lblSaveVault">Save Encrypted Vault</span></button>
      <button class="btn ghost wide" id="btnClearVault" style="color:#ef4444; border-color:rgba(239,68,68,0.3); font-size:0.8rem;">🗑️ <span id="lblClearVault">Erase All Emergency Data (GDPR)</span></button>
    </div>
  </div>
</div>

<!-- SURVIVAL ACADEMY MODAL -->
<div class="drawer-overlay" id="quizModal">
  <div class="drawer-modal">
    <div class="vault-header">
      <h2 class="vault-title"><span>🎓</span> <span id="quizModalTitle">Survival Academy</span></h2>
      <span class="vault-seal">60s Interactive Drill</span>
      <button class="btn ghost small" id="btnCloseQuiz">✕</button>
    </div>

    <div id="quizContainer">
      <!-- Quiz Cards dynamically rendered here -->
    </div>
  </div>
</div>
`;

// Insert Modals right before the end of body
html = html.replace('<!-- DISTURBANCE MODAL -->', vaultModalHtml + '\n<!-- DISTURBANCE MODAL -->');

fs.writeFileSync(indexPath, html, "utf-8");
console.log("Webapp HTML structure successfully enhanced with World-Class components!");