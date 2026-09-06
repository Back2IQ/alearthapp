import fs from "fs";
import path from "path";

const indexPath = path.resolve("webapp/index.html");
let html = fs.readFileSync(indexPath, "utf-8");

const keysEN = {
  lblHeaderVault: "Pass", lblHeaderQuiz: "Academy",
  lblAlertSafe: "I AM SAFE", lblAlertSafeSub: "Notify Contacts",
  lblAlertSos: "SOS RESCUE", lblAlertSosSub: "Start BLE Beacon",
  vaultModalTitle: "Emergency Vault",
  vaultModalDesc: "Your vital medical data is encrypted locally on this device only (AES-GCM-256). It is never uploaded to any cloud server.",
  lblVaultName: "Full Name / Initials", lblVaultAge: "Age", lblVaultGender: "Gender",
  lblVaultBlood: "Blood Type", lblVaultTriage: "Life-Safety Triage Flags",
  lblFlagInsulinTitle: "Insulin / Diabetes", lblFlagInsulinDesc: "Priority for emergency glucose/insulin supply",
  lblFlagHeartTitle: "Heart Condition", lblFlagHeartDesc: "Requires cardiovascular monitoring",
  lblFlagAsthmaTitle: "Respiratory / Asthma", lblFlagAsthmaDesc: "High risk in heavy debris dust & smoke",
  lblVaultOptInText: "<strong>Art. 9 GDPR Consent:</strong> In a severe disaster, broadcast my blood type & vital flags over local Bluetooth (BLE Mesh) to nearby first responders within 30–50m.",
  lblSaveVault: "Save Encrypted Vault", lblClearVault: "Erase All Emergency Data (GDPR)",
  quizModalTitle: "Survival Academy", gobag_title: "Smart Go-Bag Readiness",
  gobag_of: "of", gobag_packed_label: "items packed",
  vault_btn_quick: "Emergency Pass", quiz_btn_quick: "Survival Drill",
  exp_expired: "Expired", exp_soon: "Renew soon", exp_ok: "Valid",
  quiz_result: "Score", quiz_retry: "🔄 Retake Drill", quiz_step: "Scenario",
  quiz_correct: "Correct!", quiz_wrong: "Not optimal!",
  vault_saved: "🔒 Emergency Pass saved with AES-GCM-256 encryption!",
  vault_cleared: "🗑️ Emergency Pass completely erased from device.",
  vault_clear_confirm: "Do you really want to completely erase your encrypted emergency pass from this device?"
};

const keysDE = {
  lblHeaderVault: "Notfall-Pass", lblHeaderQuiz: "Academy",
  lblAlertSafe: "ICH BIN IN SICHERHEIT", lblAlertSafeSub: "Kontakte beruhigen",
  lblAlertSos: "SOS-NOTSIGNAL", lblAlertSosSub: "BLE-Beacon starten",
  vaultModalTitle: "Verschlüsselter Notfall-Tresor",
  vaultModalDesc: "Deine medizinischen Notfalldaten verbleiben ausschließlich verschlüsselt auf deinem Smartphone (AES-GCM-256). Sie werden niemals auf Server hochgeladen.",
  lblVaultName: "Vollständiger Name / Initialen", lblVaultAge: "Alter", lblVaultGender: "Geschlecht",
  lblVaultBlood: "Blutgruppe", lblVaultTriage: "Lebensrettende Triage-Flags",
  lblFlagInsulinTitle: "Insulin / Diabetes", lblFlagInsulinDesc: "Priorität für Notfall-Glukose / Insulin",
  lblFlagHeartTitle: "Herzerkrankung", lblFlagHeartDesc: "Erfordert kardiovaskuläres Monitoring",
  lblFlagAsthmaTitle: "Atemwege / Asthma", lblFlagAsthmaDesc: "Hohes Risiko bei dichtem Trümmerstaub",
  lblVaultOptInText: "<strong>Art. 9 DSGVO Einwilligung:</strong> Im Katastrophenfall Blutgruppe & vitale Flags per Nahbereich-Bluetooth (BLE Mesh) an Ersthelfer im Umkreis von 30–50m senden.",
  lblSaveVault: "Verschlüsselt speichern", lblClearVault: "Notfall-Pass restlos löschen (DSGVO)",
  quizModalTitle: "Survival Academy", gobag_title: "Smart Notfallrucksack-Status",
  gobag_of: "von", gobag_packed_label: "Gegenständen gepackt",
  vault_btn_quick: "Notfall-Pass", quiz_btn_quick: "Survival-Drill",
  exp_expired: "Abgelaufen", exp_soon: "Erneuern", exp_ok: "Haltbar",
  quiz_result: "Ergebnis", quiz_retry: "🔄 Drill wiederholen", quiz_step: "Szenario",
  quiz_correct: "Richtig!", quiz_wrong: "Nicht optimal!",
  vault_saved: "🔒 Notfall-Pass mit AES-GCM-256 verschlüsselt gespeichert!",
  vault_cleared: "🗑️ Notfall-Pass restlos vom Gerät gelöscht.",
  vault_clear_confirm: "Möchtest du alle verschlüsselten Notfalldaten restlos vom Gerät löschen?"
};

const keysTR = {
  lblHeaderVault: "Acil Kart", lblHeaderQuiz: "Akademi",
  lblAlertSafe: "GÜVENDİYİM", lblAlertSafeSub: "Kişileri Bilgilendir",
  lblAlertSos: "SOS İMDAT", lblAlertSosSub: "BLE Fenerini Başlat",
  vaultModalTitle: "Şifreli Acil Durum Kasası",
  vaultModalDesc: "Hayati tıbbi verileriniz yalnızca bu cihazda yerel olarak şifrelenir (AES-GCM-256). Asla bulut sunucularına yüklenmez.",
  lblVaultName: "Ad Soyad / Baş Harfler", lblVaultAge: "Yaş", lblVaultGender: "Cinsiyet",
  lblVaultBlood: "Kan Grubu", lblVaultTriage: "Acil Triyaj Belirteçleri",
  lblFlagInsulinTitle: "İnsülin / Diyabet", lblFlagInsulinDesc: "Acil glikoz/insülin tedariği önceliği",
  lblFlagHeartTitle: "Kalp Rahatsızlığı", lblFlagHeartDesc: "Kardiyovasküler takip gerektirir",
  lblFlagAsthmaTitle: "Solunum / Astım", lblFlagAsthmaDesc: "Yoğun enkaz tozu ve dumanda yüksek risk",
  lblVaultOptInText: "<strong>Madde 9 KVKK/GDPR Onayı:</strong> Ağır bir afette kan grubumu ve hayati belirteçlerimi Bluetooth (BLE) üzerinden 30–50m menzildeki kurtarma ekiplerine ilet.",
  lblSaveVault: "Şifreli Kaydet", lblClearVault: "Tüm Verileri Sil (KVKK)",
  quizModalTitle: "Hayatta Kalma Akademisi", gobag_title: "Akıllı Deprem Çantası Durumu",
  gobag_of: "/", gobag_packed_label: "eşya hazırlandı",
  vault_btn_quick: "Acil Durum Kartı", quiz_btn_quick: "Tatbikat Yap",
  exp_expired: "Günü Geçmiş", exp_soon: "Yenileyin", exp_ok: "Geçerli",
  quiz_result: "Puan", quiz_retry: "🔄 Tekrar Dene", quiz_step: "Senaryo",
  quiz_correct: "Doğru!", quiz_wrong: "Hatalı!",
  vault_saved: "🔒 Acil Durum Kartı AES-GCM-256 ile şifrelendi!",
  vault_cleared: "🗑️ Acil durum verileri tamamen silindi.",
  vault_clear_confirm: "Tüm acil durum verilerini kalıcı olarak silmek istiyor musunuz?"
};

const keysKU = {
  lblHeaderVault: "Karta Lezgîn", lblHeaderQuiz: "Akademî",
  lblAlertSafe: "EZ LI CIHÊ EWLE ME", lblAlertSafeSub: "Agahî bide kesan",
  lblAlertSos: "HAWAR (SOS)", lblAlertSosSub: "Sînyala BLE bide",
  vaultModalTitle: "Kasa Lezgîn a Şîfrekirî",
  vaultModalDesc: "Agahiyên te yên tenduristiyê tenê li ser vî cîhazî şîfrekirî ne (AES-GCM-256). Qet naçin ser ti serveran.",
  lblVaultName: "Nav û Paşnav", lblVaultAge: "Temen", lblVaultGender: "Zayend",
  lblVaultBlood: "Koma Xwînê", lblVaultTriage: "Nîşaneyên Lezgîn",
  lblFlagInsulinTitle: "Însulîn / Şekir", lblFlagInsulinDesc: "Pêşengiya însulînê",
  lblFlagHeartTitle: "Nexweşiya Dil", lblFlagHeartDesc: "Şopandina dil hewce ye",
  lblFlagAsthmaTitle: "Bêhnçikîn / Astim", lblFlagAsthmaDesc: "Xetereya li hember toza kavilan",
  lblVaultOptInText: "<strong>Destûra GDPR:</strong> Di karesatê de koma xwînê bi riya Bluetooth (BLE) bighîne tîmên rizgarkirinê.",
  lblSaveVault: "Şîfrekirî tomar bike", lblClearVault: "Hemû daneyan paqij bike",
  quizModalTitle: "Akademiya Rizgariyê", gobag_title: "Rewşa Çantaya Lezgîn",
  gobag_of: "/", gobag_packed_label: "tişt hatine amadekirin",
  vault_btn_quick: "Karta Lezgîn", quiz_btn_quick: "Drîl bike",
  exp_expired: "Qediyaye", exp_soon: "Nû bike", exp_ok: "Bê pirsgirêk",
  quiz_result: "Encam", quiz_retry: "🔄 Dîsa biceribîne", quiz_step: "Senaryo",
  quiz_correct: "Rast e!", quiz_wrong: "Çewt e!",
  vault_saved: "🔒 Daneyên lezgîn hatin şîfrekirin!",
  vault_cleared: "🗑️ Daneyên lezgîn hatin paqijkirin.",
  vault_clear_confirm: "Tu dixwazî hemû daneyan ji ser telefonê paqij bikî?"
};

const keysAR = {
  lblHeaderVault: "بطاقة الطوارئ", lblHeaderQuiz: "الأكاديمية",
  lblAlertSafe: "أنا في أمان", lblAlertSafeSub: "طمأنة جهات الاتصال",
  lblAlertSos: "نداء استغاثة (SOS)", lblAlertSosSub: "بدء إشارة BLE",
  vaultModalTitle: "خزنة الطوارئ المشفرة",
  vaultModalDesc: "يتم تشفير بياناتك الطبية الحيوية محلياً على هذا الجهاز فقط (AES-GCM-256). لا يتم تحميلها أبداً على خوادم سحابية.",
  lblVaultName: "الاسم الكامل / الأحرف الأولى", lblVaultAge: "العمر", lblVaultGender: "الجنس",
  lblVaultBlood: "فصيلة الدم", lblVaultTriage: "علامات الفرز الطبي الحرجة",
  lblFlagInsulinTitle: "إنسولين / السكري", lblFlagInsulinDesc: "أولوية لإمدادات الجلوكوز / الإنسولين",
  lblFlagHeartTitle: "أمراض القلب", lblFlagHeartDesc: "يتطلب مراقبة القلب والأوعية الدموية",
  lblFlagAsthmaTitle: "الجهاز التنفسي / الربو", lblFlagAsthmaDesc: "خطر كبير وسط غبار الأنقاض والدخان",
  lblVaultOptInText: "<strong>الموافقة وفقاً للمادة 9 من GDPR:</strong> في الكوارث الشديدة، بث فصيلة الدم والعلامات الحيوية عبر البلوتوث (BLE) للمسعفين ضمن 30-50م.",
  lblSaveVault: "حفظ مشفر", lblClearVault: "مسح جميع بيانات الطوارئ",
  quizModalTitle: "أكاديمية البقاء", gobag_title: "حالة حقيبة الطوارئ الذكية",
  gobag_of: "من", gobag_packed_label: "عناصر جاهزة",
  vault_btn_quick: "بطاقة الطوارئ", quiz_btn_quick: "تدريب البقاء",
  exp_expired: "منتهي الصلاحية", exp_soon: "تجديد قريباً", exp_ok: "صالح",
  quiz_result: "النتيجة", quiz_retry: "🔄 إعادة التدريب", quiz_step: "السيناريو",
  quiz_correct: "صحيح!", quiz_wrong: "غير مثالي!",
  vault_saved: "🔒 تم حفظ بطاقة الطوارئ بتشفير AES-GCM-256!",
  vault_cleared: "🗑️ تم مسح بيانات الطوارئ تماماً من الجهاز.",
  vault_clear_confirm: "هل تريد حقاً مسح بطاقة الطوارئ المشفرة تماماً من هذا الجهاز؟"
};

// Function to inject keys into STR object
function injectTranslations(objName, keyMap) {
  let searchStr = `  ${objName}: {`;
  let idx = html.indexOf(searchStr);
  if (idx === -1) {
    searchStr = `  ${objName}:{`;
    idx = html.indexOf(searchStr);
  }
  if (idx !== -1) {
    let injection = "\n";
    for (const [k, v] of Object.entries(keyMap)) {
      injection += `    ${k}: ${JSON.stringify(v)},\n`;
    }
    html = html.slice(0, idx + searchStr.length) + injection + html.slice(idx + searchStr.length);
  }
}

injectTranslations("en", keysEN);
injectTranslations("de", keysDE);
injectTranslations("tr", keysTR);
injectTranslations("ku", keysKU);
injectTranslations("ar", keysAR);

fs.writeFileSync(indexPath, html, "utf-8");
console.log("i18n strings for all 5 languages successfully injected!");