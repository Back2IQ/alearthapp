import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

// Add JavaScript engines for Wavefront, Vault, Quiz, GoBag and i18n
const jsLogic = `
/* ==========================================================================
   Alert2IQ World-Class Interactive Engines (Wavefront, Vault, Quiz, Go-Bag)
   ========================================================================== */

/* 1. 60-FPS Canvas Seismic Wavefront Engine */
let waveAnimFrame = null;
let waveOriginTs = 0;
let waveDistanceKm = 80;
let waveOriginLat = 0, waveOriginLon = 0;

function startSeismicWaveAnimation(distKm, originTs, originLat, originLon) {
  waveDistanceKm = distKm || 80;
  waveOriginTs = originTs || Date.now();
  waveOriginLat = originLat || (State.loc.lat + 0.5);
  waveOriginLon = originLon || (State.loc.lon + 0.5);
  if (waveAnimFrame) cancelAnimationFrame(waveAnimFrame);
  drawSeismicWaveFrame();
}

function stopSeismicWaveAnimation() {
  if (waveAnimFrame) {
    cancelAnimationFrame(waveAnimFrame);
    waveAnimFrame = null;
  }
}

function drawSeismicWaveFrame() {
  const canvas = $("seismicWaveCanvas");
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const w = canvas.width, h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  // Background radar circles
  ctx.strokeStyle = "rgba(168, 224, 214, 0.08)";
  ctx.lineWidth = 1;
  const userX = w * 0.72, userY = h * 0.5;
  const epiX = w * 0.22, epiY = h * 0.5;

  for (let r = 20; r < w; r += 35) {
    ctx.beginPath();
    ctx.arc(userX, userY, r, 0, Math.PI * 2);
    ctx.stroke();
  }

  // Epicenter dot
  ctx.fillStyle = "#ff5245";
  ctx.beginPath();
  ctx.arc(epiX, epiY, 6, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "rgba(255, 82, 69, 0.3)";
  ctx.beginPath();
  ctx.arc(epiX, epiY, 12, 0, Math.PI * 2);
  ctx.fill();

  // User location marker
  ctx.fillStyle = "#54e6cd";
  ctx.beginPath();
  ctx.arc(userX, userY, 5, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#e8f2f1";
  ctx.font = "10px var(--font-display)";
  ctx.fillText(t("you_label") || "YOU", userX - 10, userY - 10);

  // Time calculations
  const elapsedSec = (Date.now() - waveOriginTs) / 1000;
  const pSpeedKmS = 6.0;
  const sSpeedKmS = 3.5;
  const scale = (userX - epiX) / Math.max(1, waveDistanceKm);

  const pDistKm = elapsedSec * pSpeedKmS;
  const sDistKm = elapsedSec * sSpeedKmS;

  const pRadiusPx = pDistKm * scale;
  const sRadiusPx = sDistKm * scale;

  // Draw P-Wave (Cyan, faster)
  if (pRadiusPx < w * 1.5) {
    ctx.save();
    ctx.strokeStyle = "#54e6cd";
    ctx.lineWidth = 2.5;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.arc(epiX, epiY, Math.max(1, pRadiusPx), 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();
  }

  // Draw S-Wave (Amber/Red, destructive)
  if (sRadiusPx < w * 1.5) {
    ctx.save();
    ctx.strokeStyle = "#ff6d62";
    ctx.lineWidth = 3.5;
    ctx.beginPath();
    ctx.arc(epiX, epiY, Math.max(1, sRadiusPx), 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = "rgba(255, 109, 98, 0.08)";
    ctx.fill();
    ctx.restore();
  }

  // Distance connecting vector
  ctx.save();
  ctx.strokeStyle = "rgba(255, 255, 255, 0.15)";
  ctx.lineWidth = 1;
  ctx.setLineDash([2, 4]);
  ctx.beginPath();
  ctx.moveTo(epiX, epiY);
  ctx.lineTo(userX, userY);
  ctx.stroke();
  ctx.restore();

  // Draw distance label
  ctx.fillStyle = "rgba(159, 181, 183, 0.8)";
  ctx.font = "10px var(--font-display)";
  ctx.fillText(Math.round(waveDistanceKm) + " km", (epiX + userX) / 2 - 15, epiY - 8);

  waveAnimFrame = requestAnimationFrame(drawSeismicWaveFrame);
}

/* 2. Encrypted Emergency Vault Engine */
let selectedBloodType = "";

function initVaultUI() {
  const btnOpen = $("btnOpenVault");
  if (btnOpen) btnOpen.onclick = openVaultModal;
  const btnClose = $("btnCloseVault");
  if (btnClose) btnClose.onclick = closeVaultModal;
  const btnSave = $("btnSaveVault");
  if (btnSave) btnSave.onclick = saveVaultData;
  const btnClear = $("btnClearVault");
  if (btnClear) btnClear.onclick = clearVaultData;

  const grid = $("bloodPillGrid");
  if (grid) {
    const pills = grid.querySelectorAll(".blood-pill");
    pills.forEach(p => {
      p.onclick = () => {
        pills.forEach(x => x.setAttribute("aria-pressed", "false"));
        p.setAttribute("aria-pressed", "true");
        selectedBloodType = p.dataset.blood;
      };
    });
  }
}

function openVaultModal() {
  loadVaultData();
  const m = $("vaultModal");
  if (m) m.classList.add("show");
}

function closeVaultModal() {
  const m = $("vaultModal");
  if (m) m.classList.remove("show");
}

function saveVaultData() {
  const vAge = $("vaultAge");
  const profile = {
    fullName: ($("vaultFullName")?.value || "").trim(),
    age: parseInt(vAge?.value || "0", 10) || 0,
    gender: $("vaultGender")?.value || "",
    bloodType: selectedBloodType,
    chronicDiseases: [
      $("vaultFlagInsulin")?.checked ? "insulin diabetes" : "",
      $("vaultFlagHeart")?.checked ? "herz heart" : "",
      $("vaultFlagAsthma")?.checked ? "asthma atemwege respiratory" : ""
    ].filter(Boolean).join(", "),
    broadcastMedicalData: !!$("vaultOptInBroadcast")?.checked
  };

  const jsonStr = JSON.stringify(profile);
  if (window.AndroidBridge && AndroidBridge.saveVaultProfile) {
    AndroidBridge.saveVaultProfile(jsonStr);
  } else {
    localStorage.setItem("alert2iq_vault_mock", jsonStr);
  }

  toast(t("vault_saved") || "🔒 Notfall-Pass verschlüsselt gespeichert!");
  closeVaultModal();
}

function loadVaultData() {
  let jsonStr = "{}";
  if (window.AndroidBridge && AndroidBridge.loadVaultProfile) {
    jsonStr = AndroidBridge.loadVaultProfile();
  } else {
    jsonStr = localStorage.getItem("alert2iq_vault_mock") || "{}";
  }

  try {
    const profile = JSON.parse(jsonStr);
    if ($("vaultFullName")) $("vaultFullName").value = profile.fullName || "";
    if ($("vaultAge")) $("vaultAge").value = profile.age || "";
    if ($("vaultGender")) $("vaultGender").value = profile.gender || "";
    if ($("vaultOptInBroadcast")) $("vaultOptInBroadcast").checked = !!profile.broadcastMedicalData;

    selectedBloodType = profile.bloodType || "";
    const grid = $("bloodPillGrid");
    if (grid) {
      grid.querySelectorAll(".blood-pill").forEach(p => {
        p.setAttribute("aria-pressed", String(p.dataset.blood === selectedBloodType));
      });
    }

    const diseases = (profile.chronicDiseases || "").toLowerCase();
    if ($("vaultFlagInsulin")) $("vaultFlagInsulin").checked = diseases.includes("insulin") || diseases.includes("diabetes");
    if ($("vaultFlagHeart")) $("vaultFlagHeart").checked = diseases.includes("herz") || diseases.includes("heart");
    if ($("vaultFlagAsthma")) $("vaultFlagAsthma").checked = diseases.includes("asthma") || diseases.includes("atem") || diseases.includes("respiratory");
  } catch (e) {}
}

function clearVaultData() {
  if (!confirm(t("vault_clear_confirm") || "Möchtest du alle verschlüsselten Notfalldaten restlos vom Gerät löschen?")) return;

  if (window.AndroidBridge && AndroidBridge.clearVaultProfile) {
    AndroidBridge.clearVaultProfile();
  }
  localStorage.removeItem("alert2iq_vault_mock");
  if ($("vaultFullName")) $("vaultFullName").value = "";
  if ($("vaultAge")) $("vaultAge").value = "";
  if ($("vaultGender")) $("vaultGender").value = "";
  selectedBloodType = "";
  const grid = $("bloodPillGrid");
  if (grid) grid.querySelectorAll(".blood-pill").forEach(p => p.setAttribute("aria-pressed", "false"));
  if ($("vaultFlagInsulin")) $("vaultFlagInsulin").checked = false;
  if ($("vaultFlagHeart")) $("vaultFlagHeart").checked = false;
  if ($("vaultFlagAsthma")) $("vaultFlagAsthma").checked = false;
  if ($("vaultOptInBroadcast")) $("vaultOptInBroadcast").checked = false;

  toast(t("vault_cleared") || "🗑️ Notfall-Pass restlos gelöscht.");
  closeVaultModal();
}

/* 3. 60-Second Survival Academy Quiz Engine */
const QUIZ_SCENARIOS = [
  {
    id: 1,
    q: {
      de: "Du bist im 4. Stock eines Bürogebäudes, als starkes Rütteln einsetzt. Was ist die sicherste Handlung?",
      en: "You are on the 4th floor of an office building when strong shaking starts. What is the safest action?",
      tr: "Ofis binasının 4. katındasınız ve şiddetli sarsıntı başladı. En güvenli hareket nedir?",
      ku: "Tu li qata 4emîn a avahiyekê yî û hejek xurt dest pê kir. Çi çalakiya herî ewle ye?",
      ar: "أنت في الطابق الرابع من مبنى وبدأ اهتزاز قوي. ما هو التصرف الأكثر أماناً؟"
    },
    options: [
      {
        text: { de: "Sofort ins Treppenhaus rennen und flüchten", en: "Run into the stairwell immediately to evacuate", tr: "Hemen merdivenlere koşup tahliye olmak", ku: "Yekser ber bi pêlekanan ve bireve", ar: "الركض فوراً إلى الدرج للإخلاء" },
        correct: false
      },
      {
        text: { de: "DROP, COVER & HOLD ON unter einem stabilen Schreibtisch", en: "DROP, COVER & HOLD ON under a sturdy desk", tr: "Sağlam bir masanın altında ÇÖK, KAPAN, TUTUN", ku: "Li bin maseyek qewîn XWE BITOPÎNE Û BIGIRE", ar: "الانبطاح والاحتماء والتمسك تحت طاولة متينة" },
        correct: true
      },
      {
        text: { de: "Den Fahrstuhl nach unten nehmen", en: "Take the elevator down", tr: "Asansörle aşağı inmek", ku: "Bi asansorê dakeve jêr", ar: "استخدام المصعد للنزول" },
        correct: false
      }
    ],
    explain: {
      de: "Richtig! Treppenhäuser und Aufzüge sind die einsturzgefährdetsten Bereiche. Ducken unter stabilen Möbeln schützt vor herabstürzenden Decken und Trümmern.",
      en: "Correct! Stairwells and elevators are the most fragile areas during strong shaking. Taking cover protects against falling ceiling debris.",
      tr: "Doğru! Merdivenler ve asansörler en riskli alanlardır. Sağlam eşyaların altına sığınmak düşen parçalardan korur.",
      ku: "Rast e! Pêlekan û asansor herî zû hildiweşin. Xwe li bin tiştên qewîn parastin jiyanê rizgar dike.",
      ar: "صحيح! السلالم والمصاعد هي الأكثر عرضة للانهيار. الاحتماء تحت أثاث متين يحمي من الركام المتساقط."
    }
  },
  {
    id: 2,
    q: {
      de: "Das Hauptbeben hat aufgehört. Was tust du als Erstes vor dem Verlassen der Wohnung?",
      en: "The main shock has stopped. What is your first action before leaving the apartment?",
      tr: "Ana sarsıntı durdu. Evi terk etmeden önce yapacağınız ilk şey nedir?",
      ku: "Heja sereke sekinî. Berî derketina ji malê gava yekem çi ye?",
      ar: "توقف الاهتزاز الرئيسي. ما هو أول عمل تقوم به قبل مغادرة المنزل؟"
    },
    options: [
      {
        text: { de: "Gas-, Wasser- und Stromhauptschalter abdrehen", en: "Shut off main gas, water, and electricity switches", tr: "Doğalgaz, su ve elektrik ana vanalarını kapatmak", ku: "Şalterên gaz, av û elektrîkê bigire", ar: "إغلاق محابس الغاز والماء والكهرباء الرئيسية" },
        correct: true
      },
      {
        text: { de: "Fenster weit öffnen und nach Nachbarn rufen", en: "Open all windows wide and shout for neighbors", tr: "Camları açıp komşulara seslenmek", ku: "Pencereyan veke û bangî cîranan bike", ar: "فتح النوافذ والمناداة على الجيران" },
        correct: false
      }
    ],
    explain: {
      de: "Exzellent! Gebrochene Gasleitungen und Funkenschlag sind die Hauptursache für verheerende Folgebrände nach Erdbeben.",
      en: "Excellent! Broken gas pipes and electrical sparks cause the majority of post-earthquake fires.",
      tr: "Mükemmel! Gaz kaçakları ve elektrik kıvılcımları deprem sonrası yangınların ana nedenidir.",
      ku: "Aferîn! Xetên gazê yên şikestî sedema sereke ya agirberdanê ne.",
      ar: "ممتاز! تسرب الغاز والشرر الكهربائي هما السبب الرئيسي للحرائق بعد الزلازل."
    }
  },
  {
    id: 3,
    q: {
      de: "Du bist unter Trümmern verschüttet. Wie alarmierst du Retter am effektivsten?",
      en: "You are trapped under rubble. How do you alert rescuers most effectively?",
      tr: "Enkaz altında kaldınız. Kurtarma ekiplerine en etkili nasıl ses verirsiniz?",
      ku: "Tu di bin kavilan de mayî. Çawa herî baş dengê xwe dighînî tîman?",
      ar: "أنت محاصر تحت الأنقاض. كيف تنبه رجال الإنقاذ بأكبر قدر من الفعالية؟"
    },
    options: [
      {
        text: { de: "Ohne Unterbrechung laut schreien, bis jemand antwortet", en: "Scream continuously until someone responds", tr: "Biri cevap verene kadar sürekli bağırmak", ku: "Bê sekinîn bi qîrîn heya yek bersiv bide", ar: "الصراخ المتواصل حتى يجيب أحد" },
        correct: false
      },
      {
        text: { de: "Mund mit Stoff schützen, in 3er-Intervallen gegen Rohre/Beton klopfen", en: "Cover mouth with cloth, tap in sets of 3 against pipes or concrete", tr: "Ağzı bezle kapatıp boru veya betona 3'lü ritimle vurmak", ku: "Devê xwe bipêçe, bi 3 derban li boriyan an betonê bixe", ar: "تغطية الفم بقماش، والنقر بإيقاع ثلاثي على الأنابيب أو الخرسانة" },
        correct: true
      }
    ],
    explain: {
      de: "Perfekt! Klopfen überträgt Schall durch Beton weit besser als die Stimme, spart wertvollen Sauerstoff und verhindert Staubinhalation.",
      en: "Spot on! Tapping propagates sound through concrete far better than voice, preserves oxygen, and prevents toxic dust inhalation.",
      tr: "Harika! Borulara vurmak sesi betondan çok daha iyi iletir, oksijeni korur ve toz yutmayı engeller.",
      ku: "Rast e! Lêdana li boriyan deng baştir belav dike û nahêle toza jehrî bikeve qirikê.",
      ar: "ممتاز! النقر ينقل الصوت عبر الخرسانة بشكل أفضل بكثير من الصوت ويحفظ الأكسجين."
    }
  },
  {
    id: 4,
    q: {
      de: "Du bist an der Meeresküste und spürst ein starkes Beben. Was ist die Dringlichkeitsstufe?",
      en: "You are on the sea coast and feel strong shaking. What is the urgent priority?",
      tr: "Sahildesiniz ve şiddetli bir deprem hissettiniz. Acil öncelik nedir?",
      ku: "Tu li ber peravên deryayê yî û erdhej çêbû. Pêşengiya lezgîn çi ye?",
      ar: "أنت على شاطئ البحر وشعرت بزلزال قوي. ما هي الأولوية العاجلة؟"
    },
    options: [
      {
        text: { de: "Sofort zu Fuß auf eine Anhöhe flüchten (mind. 20m über NN)", en: "Immediately evacuate on foot to high ground (at least 20m above sea level)", tr: "Derhal yürüyerek yüksek tepelere kaçmak (en az 20m rakım)", ku: "Yekser bi piyadî ber bi girên bilind ve bireve", ar: "الإخلاء فوراً سيراً على الأقدام إلى مكان مرتفع (20 م على الأقل)" },
        correct: true
      },
      {
        text: { de: "Am Strand warten, ob das Wasser zurückweicht", en: "Wait on the beach to see if the water recedes", tr: "Suyun çekilip çekilmediğini görmek için sahilde beklemek", ku: "Li peravê bisekine ka av paşve dikişe an na", ar: "الانتظار على الشاطئ لمعرفة ما إذا كانت المياه ستتراجع" },
        correct: false
      }
    ],
    explain: {
      de: "Absolut lebenswichtig! Starke Küstenbeben können innerhalb von Minuten verheerende Tsunami-Wellen auslösen.",
      en: "Vital! Coastal strong earthquakes can trigger devastating tsunami waves within minutes.",
      tr: "Hayati! Şiddetli kıyı depremleri dakikalar içinde tsunami dalgaları üretebilir.",
      ku: "Giring e! Erdhejên li nêzî deryayê dikarin di çend xulekan de tsunamiyê çêbikin.",
      ar: "حيوي للغاية! الزلازل الساحلية القوية يمكن أن تولد موجات تسونامي خلال دقائق."
    }
  },
  {
    id: 5,
    q: {
      de: "Du bist während des Bebens im Freien auf der Straße. Wo suchst du Schutz?",
      en: "You are outdoors on a city street during shaking. Where do you seek safety?",
      tr: "Deprem anında dışarıda, sokaktasınız. Nerede güvenlik ararsınız?",
      ku: "Dema erdhejê tu li kolanê yî. Li ku derê xwe diparêzî?",
      ar: "أنت في الشارع بالخارج أثناء الاهتزاز. أين تبحث عن الأمان؟"
    },
    options: [
      {
        text: { de: "Möglichst nah an eine Hauswand stellen", en: "Stand as close as possible to a building wall", tr: "Bir bina duvarının dibine yanaşmak", ku: "Nêzîkî dîwarê avahiyê bisekine", ar: "الوقوف بالقرب من جدار المبنى" },
        correct: false
      },
      {
        text: { de: "Auf freie Fläche fernab von Fassaden, Glas und Strommasten bewegen", en: "Move into an open space away from facades, glass, and power lines", tr: "Bina cepheleri, camlar ve elektrik direklerinden uzak açık alana geçmek", ku: "Ji dîwar, cam û stûnên elektrîkê dûr bikeve cihê vekirî", ar: "التحرك إلى منطقة مفتوحة بعيداً عن الواجهات والزجاج وأعمدة الكهرباء" },
        correct: true
      }
    ],
    explain: {
      de: "Korrekt! Herabstürzende Fassadenteile, Schornsteine und Glassplitter sind im Freien die tödlichste Gefahr.",
      en: "Correct! Falling glass, bricks, and facade elements are the deadliest hazards outdoors.",
      tr: "Doğru! Düşen camlar ve cephe kaplamaları dışarıdaki en ölümcül tehlikedir.",
      ku: "Rast e! Cam û perçeyên avahiyê yên dikevin xetereya herî mezin in.",
      ar: "صحيح! الزجاج المتساقط وأجزاء الواجهات هي الخطر الأكثر فتكاً في الخارج."
    }
  }
];

let currentQuizIndex = 0;
let quizScore = 0;

function initQuizUI() {
  const btnOpen = $("btnOpenQuiz");
  if (btnOpen) btnOpen.onclick = openQuizModal;
  const btnClose = $("btnCloseQuiz");
  if (btnClose) btnClose.onclick = closeQuizModal;
}

function openQuizModal() {
  currentQuizIndex = 0;
  quizScore = 0;
  renderQuizStep();
  const m = $("quizModal");
  if (m) m.classList.add("show");
}

function closeQuizModal() {
  const m = $("quizModal");
  if (m) m.classList.remove("show");
}

function renderQuizStep() {
  const container = $("quizContainer");
  if (!container) return;

  if (currentQuizIndex >= QUIZ_SCENARIOS.length) {
    const pct = Math.round((quizScore / QUIZ_SCENARIOS.length) * 100);
    let badgeIcon = "🛡️";
    let badgeTitle = "Alert2IQ Disaster Guardian";
    if (pct < 60) { badgeIcon = "🥉"; badgeTitle = "Novice Prepared Citizen"; }
    else if (pct < 90) { badgeIcon = "🥈"; badgeTitle = "Prepared Citizen Specialist"; }

    container.innerHTML = '<div class="badge-award">' +
      '<div class="badge-icon">' + badgeIcon + '</div>' +
      '<h3 class="badge-title">' + badgeTitle + '</h3>' +
      '<p class="badge-score">' + (t("quiz_result") || "Score") + ': <strong>' + quizScore + ' / ' + QUIZ_SCENARIOS.length + ' (' + pct + '%)</strong></p>' +
      '<button class="btn accent wide" onclick="openQuizModal()">' + (t("quiz_retry") || "🔄 Drill wiederholen") + '</button>' +
      '</div>';
    return;
  }

  const s = QUIZ_SCENARIOS[currentQuizIndex];
  const qText = s.q[lang] || s.q.en;
  const expText = s.explain[lang] || s.explain.en;

  let optionsHtml = s.options.map((opt, idx) => {
    const oText = opt.text[lang] || opt.text.en;
    return '<button class="quiz-option" data-idx="' + idx + '" onclick="handleQuizAnswer(' + idx + ')">' + oText + '</button>';
  }).join("");

  container.innerHTML = '<div class="quiz-card">' +
    '<div class="quiz-step">' + (t("quiz_step") || "Szenario") + ' ' + (currentQuizIndex + 1) + ' / ' + QUIZ_SCENARIOS.length + '</div>' +
    '<h3 class="quiz-question">' + qText + '</h3>' +
    '<div id="quizOptions">' + optionsHtml + '</div>' +
    '<div class="quiz-feedback" id="quizFeedback"></div>' +
    '<button class="btn accent wide" id="btnQuizNext" style="display:none; margin-top:14px;" onclick="nextQuizStep()">' + (t("ob_next") || "Weiter") + ' →</button>' +
    '</div>';
}

window.handleQuizAnswer = function(chosenIdx) {
  const s = QUIZ_SCENARIOS[currentQuizIndex];
  const optionsEl = $("quizOptions");
  if (!optionsEl) return;
  const buttons = optionsEl.querySelectorAll(".quiz-option");
  buttons.forEach(b => b.disabled = true);

  const isCorrect = s.options[chosenIdx].correct;
  if (isCorrect) quizScore++;

  buttons.forEach((b, idx) => {
    if (s.options[idx].correct) b.classList.add("correct");
    else if (idx === chosenIdx) b.classList.add("wrong");
  });

  const fb = $("quizFeedback");
  if (fb) {
    fb.className = "quiz-feedback show " + (isCorrect ? "correct" : "wrong");
    const expText = s.explain[lang] || s.explain.en;
    fb.innerHTML = '<strong>' + (isCorrect ? "✅ " + (t("quiz_correct") || "Richtig!") : "❌ " + (t("quiz_wrong") || "Nicht optimal!")) + '</strong><br>' + expText;
  }

  const nextBtn = $("btnQuizNext");
  if (nextBtn) nextBtn.style.display = "block";
};

window.nextQuizStep = function() {
  currentQuizIndex++;
  renderQuizStep();
};

/* 4. High-Stress Tactical Action Handlers */
function initTacticalAlertActions() {
  const btnSafe = $("btnAlertSafe");
  if (btnSafe) {
    btnSafe.onclick = () => {
      toast("🛡️ " + (t("beacon_im_safe") || "Entwarnung an Kontakte gesendet"));
      closeAlert();
    };
  }

  const btnSos = $("btnAlertSos");
  if (btnSos) {
    btnSos.onclick = () => {
      toast("🆘 " + (t("beacon_need_help") || "BLE Notfall-Beacon aktiviert!"));
    };
  }
}
`;

// Inject JS Logic right before init() function
html = html.replace("function init() {", jsLogic + "\nfunction init() {");

// Wire in the initialization inside init()
const initHooks = `
  initVaultUI();
  initQuizUI();
  initTacticalAlertActions();
`;
html = html.replace("bind();", "bind();\n" + initHooks);

// Trigger Wavefront animation on openAlert()
html = html.replace("function openAlert(mag, tier, mmi, distKm, leadSec, cityName) {", `function openAlert(mag, tier, mmi, distKm, leadSec, cityName) {
  startSeismicWaveAnimation(distKm, Date.now() - (distKm / 6.0 * 1000), State.loc.lat + 0.3, State.loc.lon + 0.3);`);

html = html.replace("function closeAlert() {", `function closeAlert() {
  stopSeismicWaveAnimation();`);

// Apply RTL direction when language changes
const rtlLogic = `
  const isRtl = (lang === "ar" || lang === "ku");
  document.documentElement.setAttribute("dir", isRtl ? "rtl" : "ltr");
`;
html = html.replace("function applyLanguage() {", "function applyLanguage() {\n" + rtlLogic);

fs.writeFileSync(indexPath, html, "utf-8");
console.log("Webapp interactive engines (Wavefront, Vault, Quiz, Tactical Actions) successfully wired!");