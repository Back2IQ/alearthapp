import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

// Enhanced Go-Bag Checklist Catalog with Expiry Data
const goBagLogic = `
const GOBAG_DATA = [
  {
    catKey: "water_food",
    catTitle: { de: "💧 Wasser & Notverpflegung", en: "💧 Water & Food", tr: "💧 Su ve Gıda", ku: "💧 Av û Xwarin", ar: "💧 الماء والغذاء" },
    items: [
      { id: "water_3l", title: { de: "3L Trinkwasser pro Person (mind. 3 Tage)", en: "3L Drinking Water per person (min. 3 days)", tr: "Kişi başı 3L içme suyu (en az 3 gün)", ku: "Ji bo her kesekî 3L av (kêmasî 3 roj)", ar: "3 لتر ماء شرب للشخص (3 أيام على الأقل)" }, expiry: Date.now() + 180 * 86400000 },
      { id: "water_filter", title: { de: "Mobiler Wasserfilter / Entkeimungstabletten", en: "Portable Water Filter / Purification Tablets", tr: "Taşınabilir su filtresi / arıtma tabletleri", ku: "Parzûna avê ya gerok / heban", ar: "فلتر مياه محمول / أقراص تنقية" }, expiry: Date.now() + 365 * 86400000 },
      { id: "food_bars", title: { de: "Haltbare Notrationen / Energieriegel (hohe Kaloriendichte)", en: "Long-life Emergency Rations / Energy Bars", tr: "Dayanıklı acil durum gıda barları", ku: "Xwarinên enerjiyê yên bi hêz", ar: "حصص طوارئ طويلة الأجل / ألواح طاقة" }, expiry: Date.now() + 90 * 86400000 }
    ]
  },
  {
    catKey: "first_aid",
    catTitle: { de: "🩹 Erste Hilfe & Medizin", en: "🩹 First Aid & Medical", tr: "🩹 İlk Yardım ve Medikal", ku: "🩹 Alîkariya Yekem û Derman", ar: "🩹 الإسعافات الأولية والطبية" },
    items: [
      { id: "first_aid_kit", title: { de: "DIN-Verbandsset & Druckverbände", en: "First Aid Kit & Pressure Bandages", tr: "İlk yardım çantası ve baskı bandajları", ku: "Çantaya alîkariya yekem", ar: "حقيبة إسعافات أولية وضمادات ضاغطة" }, expiry: Date.now() + 300 * 86400000 },
      { id: "personal_meds", title: { de: "Persönliche Notfallmedikamente (Insulin, Asthma, Herz)", en: "Personal Critical Medications (Insulin, Inhaler, Heart)", tr: "Kişisel acil durum ilaçları (İnsülin, Astım, Kalp)", ku: "Dermanên şexsî yên lezgîn", ar: "أدوية شخصية حرجة (إنسولين، ربو، قلب)" }, expiry: Date.now() + 25 * 86400000 },
      { id: "ffp3_masks", title: { de: "FFP3-Staubmasken gegen giftigen Trümmerstaub", en: "FFP3 Respirator Masks against debris dust", tr: "Enkaz tozuna karşı FFP3 toz maskeleri", ku: "Maskên FFP3 li hember tozê", ar: "كمامات FFP3 ضد غبار الأنقاض" }, expiry: Date.now() + 500 * 86400000 }
    ]
  },
  {
    catKey: "tech_power",
    catTitle: { de: "🔦 Licht, Energie & Werkzeuge", en: "🔦 Light, Power & Tools", tr: "🔦 Işık, Enerji ve Aletler", ku: "🔦 Ronahî, Hêz û Amûr", ar: "🔦 الإضاءة والطاقة والأدوات" },
    items: [
      { id: "crank_radio", title: { de: "Kurbel-Taschenlampe & Solar-Notfallradio", en: "Hand-crank Flashlight & Solar Emergency Radio", tr: "Dinamolu el feneri ve güneş enerjili radyo", ku: "Lampeya destan û radyoya rojê", ar: "مصباح يدوي بمولد يدوي وراديو طوارئ شمسي" }, expiry: 0 },
      { id: "powerbank", title: { de: "Powerbank (20.000 mAh) mit Ladekabel", en: "Powerbank (20,000 mAh) with cables", tr: "20.000 mAh Powerbank ve şarj kabloları", ku: "Powerbank (20.000 mAh) û kablo", ar: "بنك طاقة (20000 مللي أمبير) مع الكابلات" }, expiry: Date.now() + 60 * 86400000 },
      { id: "whistle", title: { de: "Lautstarke Signalpfeife & Multitool", en: "Loud Emergency Whistle & Multitool", tr: "Yüksek sesli acil durum düdüğü ve çok amaçlı çakı", ku: "Fîka hawarê û çeqûya piralî", ar: "صفارة طوارئ عالية الصوت وأداة متعددة" }, expiry: 0 }
    ]
  },
  {
    catKey: "docs_cash",
    catTitle: { de: "📄 Dokumente & Notfall-Bargeld", en: "📄 Documents & Cash", tr: "📄 Belgeler ve Acil Durum Nakiti", ku: "📄 Belge û Pereyê Lezgîn", ar: "📄 الوثائق والنقود للطوارئ" },
    items: [
      { id: "doc_copies", title: { de: "Wasserdichte Kopien von Ausweis, Pass & Urkunden", en: "Waterproof copies of ID, Passport & Records", tr: "Kimlik ve pasaportun su geçirmez fotokopileri", ku: "Kopiyên nasnameyê yên di kîsika bêav de", ar: "نسخ مقاومة للماء من الهوية وجواز السفر" }, expiry: 0 },
      { id: "cash_small", title: { de: "Notfall-Bargeld in kleinen Stückelungen", en: "Emergency Cash in small bills", tr: "Küçük kupürlü acil durum nakiti", ku: "Pereyê qirşî yê lezgîn", ar: "نقود طوارئ بفئات صغيرة" }, expiry: 0 }
    ]
  }
];

function getPackedState() {
  try {
    return JSON.parse(localStorage.getItem("alert2iq_gobag_packed") || "{}");
  } catch (e) { return {}; }
}

function setPackedItem(id, isPacked) {
  const state = getPackedState();
  state[id] = isPacked;
  localStorage.setItem("alert2iq_gobag_packed", JSON.stringify(state));
  renderPrep();
}

function renderPrep() {
  const box = $("prepList");
  if (!box) return;

  const packedState = getPackedState();
  let totalItems = 0;
  let packedItems = 0;

  GOBAG_DATA.forEach(cat => {
    cat.items.forEach(it => {
      totalItems++;
      if (packedState[it.id]) packedItems++;
    });
  });

  const pct = totalItems ? Math.round((packedItems / totalItems) * 100) : 0;
  const circumference = 251.2; // 2 * Math.PI * 40
  const strokeOffset = circumference - (pct / 100) * circumference;

  let gaugeColor = "#f2bc69";
  if (pct >= 80) gaugeColor = "#54e6cd";
  else if (pct < 40) gaugeColor = "#ff6d62";

  let out = \`
    <!-- Go-Bag Readiness Gauge Hero -->
    <div class="readiness-hero">
      <div class="readiness-ring">
        <svg viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="40" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="8"/>
          <circle cx="50" cy="50" r="40" fill="none" stroke="\${gaugeColor}" stroke-width="8"
            stroke-dasharray="\${circumference}" stroke-dashoffset="\${strokeOffset}" stroke-linecap="round" style="transition:all 0.5s ease;"/>
        </svg>
        <span class="readiness-score-text" style="color:\${gaugeColor}">\${pct}%</span>
      </div>
      <div class="readiness-info">
        <h3>\${t("gobag_title") || "Smart Go-Bag Readiness"}</h3>
        <p>\${packedItems} \${t("gobag_of") || "von"} \${totalItems} \${t("gobag_packed_label") || "Gegenständen gepackt"}</p>
        <div style="margin-top:8px; display:flex; gap:8px;">
          <button class="btn accent small" onclick="openVaultModal()">🔒 \${t("vault_btn_quick") || "Notfall-Pass"}</button>
          <button class="btn ghost small" onclick="openQuizModal()">🎓 \${t("quiz_btn_quick") || "Survival-Drill"}</button>
        </div>
      </div>
    </div>
  \`;

  const now = Date.now();

  GOBAG_DATA.forEach(cat => {
    const catTitle = cat.catTitle[lang] || cat.catTitle.en;
    out += \`<div class="gobag-cat"><div class="gobag-cat-head"><span>\${catTitle}</span></div>\`;

    cat.items.forEach(it => {
      const itTitle = it.title[lang] || it.title.en;
      const isPacked = !!packedState[it.id];

      let expiryBadge = "";
      if (it.expiry > 0) {
        const diffDays = Math.round((it.expiry - now) / 86400000);
        if (diffDays <= 0) {
          expiryBadge = \`<span class="expiry-badge expired">\${t("exp_expired") || "Abgelaufen"}</span>\`;
        } else if (diffDays <= 30) {
          expiryBadge = \`<span class="expiry-badge soon">\${diffDays}d (\${t("exp_soon") || "Erneuern"})</span>\`;
        } else {
          expiryBadge = \`<span class="expiry-badge ok">\${diffDays}d \${t("exp_ok") || "Haltbar"}</span>\`;
        }
      }

      out += \`
        <div class="gobag-item">
          <label>
            <input type="checkbox" \${isPacked ? "checked" : ""} onchange="setPackedItem('\${it.id}', this.checked)">
            <span style="\${isPacked ? 'text-decoration:line-through; opacity:0.6;' : ''}">\${itTitle}</span>
          </label>
          \${expiryBadge}
        </div>
      \`;
    });

    out += \`</div>\`;
  });

  box.innerHTML = out;
}
`;

// Replace renderPrep in index.html
html = html.replace(/function renderPrep\(\) \{[\s\S]*?\}\n\n\/\* =====/m, goBagLogic + "\n\n/* =====");

fs.writeFileSync(indexPath, html, "utf-8");
console.log("Go-Bag smart readiness tracker successfully injected!");