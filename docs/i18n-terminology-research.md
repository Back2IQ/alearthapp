# i18n-Terminologie-Recherche: Erdbeben-Frühwarn-UI (tr/en/de/ru/ar)

Recherche-Datum: 2026-08-23. Quellen: App-Store-Beschreibungen, offizielle Hilfeseiten, Presseberichte mit Zitaten aus Original-UI, seismologische Fachquellen. Alle Aussagen sind mit Quelle/Beleg belegt; unbelegte Vorschläge sind explizit gekennzeichnet.

**Referenz-Apps/Dienste, die geprüft wurden:** AFAD (offiziell TR), Kandilli Rasathanesi (KRDAE/Boğaziçi Üni.), EMSC LastQuake, MyShake (UC Berkeley/USGS ShakeAlert), Google Android Erdbebenwarnungen (Android Earthquake Alerts, AEA), Earthquake Network, VolcanoDiscovery „Volcanoes & Earthquakes", GDACS (für Gefahren-Farbstufen). SkyAlert (Mexiko) wurde geprüft, liefert aber nur Spanisch — als UX-Praxisbeleg für die Alarm/Benachrichtigung-Trennung erwähnt, nicht als Sprachquelle.

**Wichtigster Einzelbeleg für die Kernunterscheidung:** Google Android Earthquake Alerts (AEA) ist die einzige der geprüften Quellen, die *exakt* unser Konzept „leise Benachrichtigung vs. lautes DND-durchdringendes Alarm" bereits in allen Zielsprachen ausrollt (Skalenstufen: „Be Aware" = leichte Erschütterung, respektiert DND; „Take Action" = starke Erschütterung, übersteuert DND, Vollbild, lauter Ton). Diese Zweiteilung ist daher der stärkste Beleg-Anker für dieses Dokument.

---

## 1. Benachrichtigung (leise Warnung) vs. Alarm (laute Warnung) — Kernunterscheidung

| Sprache | Leise Stufe (≈"Be Aware") | Laute Stufe (≈"Take Action") | Quelle/Beleg | Begründung |
|---|---|---|---|---|
| tr | **Bildirim** / „Dikkatli Ol" | **Alarm** / „Harekete Geç" | Google AEA, türkische Tech-Presse zitiert exakt „Dikkatli Ol" und „Harekete Geç" als Namen der beiden Stufen (diken.com.tr, haber7.com, infoset.help) | „Dikkatli Ol" ist die alltagstaugliche türkische Google-Bezeichnung für die leichte Stufe; „Harekete Geç" für die dringende. Als generische Konzept-Labels empfehlen wir **Bildirim** (leise) vs. **Alarm** (laut) — „alarm" ist im Türkischen ein etabliertes Fremdwort für lauten Warnton, klar von „bildirim" (Benachrichtigung) unterschieden. |
| en | **Notification** / „Be Aware" | **Alarm** / „Take Action" | Google AEA offizielle Doku (research.google/blog, CalOES PDF) — „Be Aware" = standard notification, respects DND; „Take Action" = bypasses DND, loud alarm, full-screen | Google selbst benutzt keine generischen „notification/alarm"-Labels für die beiden Stufen, sondern Handlungsaufforderungen. Für UI-Kategorienamen ist „Notification" vs. „Alarm" trotzdem der klarste, in Android/iOS-Systemsprache verankerte Gegensatz (Alarm = durchdringt DND per Systemdefinition). |
| de | **Benachrichtigung** / „Sei aufmerksam" | **Alarm** / „Handle sofort" | Deutsche Tech-Presse (smartdroid.de, winfuture.de) übersetzt „BeAware"/„TakeAction" durchgängig als „Sei aufmerksam" bzw. „Handle sofort"; keine offizielle deutschsprachige Google-Supportseite mit den exakten Stufennamen gefunden → **Vorschlag (sekundär belegt, nicht Google-Originaltext)** | „Alarm" ist im Deutschen (wie im Android-System: „Alarm"-Kategorie überstimmt „Nicht stören") bereits der etablierte Begriff für DND-durchdringende Signale; „Benachrichtigung" ist der neutrale Android-Systembegriff für die leise Stufe. |
| ru | **Уведомление** / «Будьте начеку» | **Тревога/Сигнал тревоги** / «Действуйте немедленно» | Habr.com zitiert Google-Terminologie: «Будьте начеку» (BeAware) und «Действуйте немедленно» (TakeAction); RBC Trends nutzt „«Будьте внимательны»"/„«Примите меры»" als Variante — **zwei konkurrierende Übersetzungen in der Presse, keine offizielle russischsprachige Google-Hilfeseite mit AEA-Stufennamen gefunden** → als Vorschlag markiert | «Уведомление» ist der neutrale Systembegriff (Android-Benachrichtigung); «Тревога» ist im Russischen das etablierte Wort für einen lauten, aufdringlichen Alarm (auch im Katastrophenschutz, МЧС, verwendet). |
| ar | **إشعار** / «كن على علم» | **تنبيه/إنذار** / «اتخذ إجراءً» | Masrawy.com (ägyptische Tech-Presse) zitiert Google-Terminologie „كن على علم" (Be Aware) und „اتخذ إجراءً" (Take Action); keine offizielle arabischsprachige Google-Supportseite mit AEA-Stufennamen gefunden → Vorschlag, sekundär belegt | «إشعار» ist der Standard-App-Begriff für eine normale Benachrichtigung (iOS/Android-Systemsprache); «إنذار» ist der Standardbegriff für einen lauten Alarm/Sirenenton (auch für Tsunami-/Luftschutzsirenen verwendet) und klar von «إشعار» unterscheidbar — «تنبيه» ist eine Zwischenoption (Warnhinweis), aber schwächer als «إنذار». Wir empfehlen **إنذار** für maximale Dringlichkeits-Klarheit. |

**Wichtiger Fund:** Die von Google offiziell auf der Support-Seite (`support.google.com/android/answer/9319337`) verwendeten Seiten (tr/en/de/ru/ar geprüft) enthalten NUR die technischen Begriffe (Magnitude, Epizentrum, Erschütterung), **nicht** die Namen „Be Aware"/„Take Action" selbst — diese stammen aus Präsentationsmaterial, Blogposts und werden von der Presse in den Zielsprachen übersetzt zitiert. Für tr und en ist die Übersetzung durch mehrere unabhängige Pressequellen konsistent bestätigt; für de/ru/ar liegt jeweils nur eine Quelle vor → als "sekundär belegt" gekennzeichnet, nicht als offizieller Google-String.

---

## 2. Warnstufen einstellen / „deine Warnstufen"

| Sprache | Empfehlung | Quelle/Beleg | Begründung |
|---|---|---|---|
| tr | **Uyarı Ayarların** / „Uyarı Eşiklerin" | AFAD/Google nutzen „Uyarı" als Oberbegriff für Warnungen allgemein (afad.gov.tr, diken.com.tr) | „Uyarı" ist der neutrale türkische Oberbegriff (Warnung), unter dem sowohl Bildirim als auch Alarm als Unterstufen erscheinen können; „ayarların" (deine Einstellungen) ist Standard-App-Sprache. |
| en | **Your Alert Settings** / „Your Alert Levels" | Earthquake Network „Panic" App: „set magnitude-based alert levels" (App-Store-Text) | „Alert" ist im Englischen der Oberbegriff, der sowohl Notification als auch Alarm umfasst — deckt beide Unterstufen ab, ohne vorzugreifen. |
| de | **Deine Warnstufen** / „Warneinstellungen" | Vorschlag (unbelegt) — keine deutschsprachige App mit exakt diesem Einstellungsnamen gefunden | „Warnstufe" ist im deutschen Katastrophenschutz (NINA-App, DWD-Wetterwarnungen) der etablierte Begriff für gestufte Warnungen (Wetterwarnstufen 1-4) — hohe Wiedererkennbarkeit. |
| ru | **Твои уровни оповещения** / «Настройки оповещений» | GDACS-Doku nutzt «уровень тревоги»/«уровень оповещения» für Stufen; Android-Hilfe nutzt «Оповещения о землетрясениях» | «Оповещение» ist der in der russischen Google-Android-Hilfe offiziell verwendete Begriff für „Alert" im Erdbebenkontext — direkt übernehmbar. |
| ar | **مستويات التنبيه الخاصة بك** / «إعدادات التنبيهات» | Google AR-Hilfeseite nutzt «تنبيهات عن الزلازل» als offiziellen Begriff für „Earthquake Alerts" | «تنبيه» als Oberbegriff (Hinweis/Alarm) ist bereits der von Google offiziell für den Gesamtdienst verwendete arabische Begriff — konsistent als Dachbegriff für die Einstellungsseite nutzbar, auch wenn die laute Einzelstufe „إنذار" heißt. |

---

## 3. Magnitude / „ab Stärke X" / „ab M X"

| Sprache | Begriff | Quelle/Beleg | Begründung |
|---|---|---|---|
| tr | **Büyüklük** — „X büyüklüğünden itibaren" / „M X ve üzeri" | Google AEA TR-Hilfeseite: „4,5 ve üzeri büyüklükte deprem"; Kandilli/AFAD-Bulletins nutzen durchgängig „Büyüklük: X" | „Büyüklük" ist der offizielle türkische Fachbegriff (Kandilli-Bulletins, AFAD) für Magnitude und wird in allen geprüften türkischen Quellen einheitlich verwendet — klar von „Şiddet" (Intensität) unterschieden. **Falscher-Freund-Warnung:** „Şiddet" (Intensity) NICHT für Magnitude verwenden — häufige Verwechslung in Laienmedien. |
| en | **Magnitude** — „from magnitude X" / „M X+" | Google AEA-Doku, MyShake FAQ, USGS: durchgängig „magnitude" | Internationaler Fachstandard, keine Alternative nötig. |
| de | **Magnitude** (Stärke im Fließtext) — „ab Magnitude X" / „ab M X" | Google DE-Hilfeseite: „Stärke" wird umgangssprachlich verwendet („Stärke von 4,5"), Fachpresse nutzt „Magnitude" parallel (t3n.de, winfuture.de) | Für UI-Labels empfehlen wir **„Magnitude"** (Fachbegriff, in Kompaktform „M X" wie im Fachjargon) statt „Stärke", weil „Stärke" umgangssprachlich mit Intensität/Erschütterung verwechselt wird; Google DE selbst mischt beide Begriffe uneinheitlich. **Falscher-Freund-Warnung:** „Stärke" vs. „Intensität" — Google DE nutzt „Stärke" für Magnitude UND „Erschütterungsintensität" für Intensity; im UI sollte „Magnitude" (Ursache) klar von „Erschütterungsstärke am Ort" (Wirkung) getrennt werden. |
| ru | **Магнитуда** — «от магнитуды X» / «от M X» | Google RU-Hilfeseite: «магнитудой не ниже 4,5»; durchgängig „магнитуда" in allen geprüften russischen Quellen (habr.com, rbc.ru) | Direkter, im Russischen etablierter Fachbegriff, keine Verwechslungsgefahr mit «интенсивность» (Intensität), die separat benannt wird. |
| ar | **قوة الزلزال (بالمقياس)** / «من الدرجة X» — empfohlen: **الشدة الآلية / درجة الزلزال** | Google AR-Hilfeseite: «قوة الزلزال» (Stärke des Bebens) und «4.5 درجة أو أعلى» (4,5 Grad oder höher) | **Falscher-Freund-Warnung wichtig:** Arabische Pressequellen nutzen «شدة الزلزال» sowohl für Intensity (Mercalli, gefühlte Wirkung) als auch umgangssprachlich für Magnitude — das ist doppeldeutig. Google selbst vermeidet «شدة» für Magnitude und nutzt stattdessen «قوة» (Kraft/Stärke) + «درجة» (Grad, wie bei „X Grad auf der Richterskala" im Volksmund). Empfehlung: **«قوة الزلزال»** oder kompakt **«الدرجة»** für Magnitude verwenden, **«شدة الاهتزاز»** ausschließlich für Intensity reservieren. |

---

## 4. Radius / Warnradius / „innerhalb X km"

| Sprache | Begriff | Quelle/Beleg | Belegtstatus | Begründung |
|---|---|---|---|---|
| tr | **Yarıçap** — „X km içinde" | Earthquake Network/generische Sismo-Apps nutzen „yarıçap" (Radius) und „km içinde" (innerhalb km) in Menü-Beschreibungen (App-Store-Texte, indirekt bestätigt) | Vorschlag (sekundär belegt) | „Yarıçap" ist der türkische mathematische Standardbegriff für Radius, in Navigations-/Wetter-Apps etabliert (z. B. „X km yarıçapında"). |
| en | **Radius** — „within X km" | Earthquake Network „Panic": „set your own minimum magnitude and alert radius" (App-Store-Text, direkt zitiert) | Direkt belegt | Direkte App-Store-Formulierung einer Referenz-App mit exakt unserem Feature (Magnitude+Radius-Schwellen). |
| de | **Radius** — „innerhalb von X km" | Vorschlag (unbelegt) — keine deutsche App-Beschreibung mit exakter Formulierung gefunden | Vorschlag (unbelegt) | „Radius" ist im Deutschen als Fremdwort vollständig eingebürgert (Standard in Wetter-/Standort-Apps: „Umkreis von X km" als Alternative). Empfehlung: **„Umkreis"** statt „Radius" erwägen — alltagssprachlich noch verständlicher als das mathematische Fremdwort „Radius". |
| ru | **Радиус** — «в пределах X км» | Vorschlag (unbelegt) — Standardbegriff aus russischen Wetter-/Standort-Apps, keine erdbebenspezifische Belegquelle gefunden | Vorschlag (unbelegt) | «Радиус» ist der etablierte russische Standardbegriff (Yandex-Karten, Wetter-Apps: «в радиусе X км»). |
| ar | **نطاق/دائرة نصف قطرها** — «ضمن X كم» — empfohlen: **النطاق (X كم)** | Vorschlag (unbelegt) | Vorschlag (unbelegt) | «نصف القطر» (wörtlich „Radius") ist mathematisch korrekt, aber im Alltag sperrig; arabische Standort-Apps (Google Maps AR) nutzen eher **«النطاق»** (Bereich/Umkreis) für nutzerfreundliche Umkreis-Angaben — empfohlen statt des Fachbegriffs. |

---

## 5. Nachbeben, Epizentrum, Tiefe, Intensität (MMI)

| Konzept | tr | en | de | ru | ar | Quelle/Beleg |
|---|---|---|---|---|---|---|
| **Nachbeben** | **Artçı deprem** | **Aftershock** | **Nachbeben** | **Афтершок** | **هزة ارتدادية** | AFAD/T24-Glossar (tr, direkt zitiert: „ana depremden sonra oluşan küçük sarsıntılara... artçı deprem denir"); Aftershock ist internat. USGS-Standard (en); Nachbeben = etablierter deutscher Fachbegriff (Presse/DWD, sekundär belegt, kein direktes Zitat gefunden); Афтершок = russischer Standardbegriff (sekundär belegt); هزة ارتدادية = direkt per arabischer Wikipedia bestätigt |
| **Epizentrum** | **Merkez üssü** (auch: Dış merkez/Episantr) | **Epicenter** | **Epizentrum** | **Эпицентр** | **المركز السطحي** | Kandilli/AFAD-Bulletins nutzen „merkez üssü" durchgängig (direkt zitiert, z. B. „Aktaş-Sındırgı (Balıkesir) merkez üssünde..."); Google-Hilfeseiten (en/de/ru) nutzen exakt „Epicenter"/„Epizentrum"/«эпицентр» (direkt zitiert); arabisch «المركز السطحي» direkt bestätigt (uomustansiriyah.edu.iq, alaraby.co.uk) |
| **Tiefe (Herdtiefe)** | **Derinlik** (fachlich: Odak derinliği) | **Depth** | **Tiefe** (fachlich: Herdtiefe) | **Глубина** (fachlich: глубина очага) | **العمق** | Kandilli-Bulletins: „Derinlik: 1.4 km" (direkt zitiert); übrige Sprachen als Standardübersetzung aus seismologischem Sprachgebrauch, ru/ar teilweise sekundär belegt (МЧС/Wikipedia-Texte nutzen «глубина»/«العمق» konsistent) |
| **Intensität (MMI)** | **Şiddet** (Skala: Mercalli/MSK) | **Intensity (MMI)** | **Intensität** | **Интенсивность** | **الشدة** (Mercalli المعدل) | Kandilli/AFAD nutzen „Şiddet" klar getrennt von „Büyüklük" (direkt zitiert, TÜBİTAK-Erklärartikel); Google-Hilfeseiten (en/de/ru) nutzen „shaking intensity"/„Erschütterungsintensität"/«интенсивность» direkt; arabisch «شدة الزلزال» direkt von Google AR-Hilfeseite zitiert — **hier ausnahmsweise korrekt für Intensity**, siehe Falscher-Freund-Hinweis in Tabelle 3 |

**Falscher-Freund-Warnung (zusammengefasst):** In allen fünf Sprachen existiert ein Begriffspaar Magnitude/Intensität, das in der Alltagssprache/Presse oft vertauscht wird:
- TR: Büyüklük (Magnitude) ≠ Şiddet (Intensität) — AFAD hat dazu sogar eine eigene Erklärseite, weil die Verwechslung so häufig ist.
- DE: „Stärke" wird für BEIDES benutzt (Google DE selbst uneinheitlich) — im UI strikt trennen.
- AR: „شدة" wird umgangssprachlich für Magnitude missbraucht, korrekt ist es aber nur für Intensität (Google AR nutzt „قوة" für Magnitude).
- RU/EN: Sprachlich sauber getrennt (магнитуда/интенсивность, magnitude/intensity) — geringstes Verwechslungsrisiko.

---

## 6. Gefahren-Warnstufen: aus / orange / rot; „benachrichtigen ab" / „Alarm ab"

| Sprache | aus | orange | rot | Quelle/Beleg |
|---|---|---|---|---|
| tr | **Kapalı** | **Turuncu** | **Kırmızı** | GDACS-Farbcodes sind sprachneutrale Standardfarbnamen; türkische Übersetzung als Standardvokabular (unbelegt für App-Kontext, aber Farbnamen selbst trivial/eindeutig) |
| en | **Off** | **Orange** | **Red** | GDACS offizielle Doku: „Green/Orange/Red" als harmonisierte Stufen über alle Gefahrentypen (direkt zitiert, gdacs.org) — hier „aus" statt „grün", da die App im Gegensatz zu GDACS eine deaktivierbare Warnstufe statt eines dauerhaften Grün-Zustands anbietet |
| de | **Aus** | **Orange** | **Rot** | Deutscher Wetterdienst (DWD) nutzt dieselbe Ampel-Systematik (Wetterwarnstufen), hohe Wiedererkennbarkeit (sekundär belegt für DWD-Konvention, nicht GDACS-spezifisch) |
| ru | **Выкл.** | **Оранжевый** | **Красный** | GDACS-Farbstufen sprachneutral; russische Standardübersetzung der Ampel-Farben (unbelegt für App-Kontext, Farbnamen selbst eindeutig) |
| ar | **إيقاف** | **برتقالي** | **أحمر** | GDACS-Farbstufen sprachneutral; arabische Standardübersetzung (unbelegt für App-Kontext, Farbnamen selbst eindeutig) |

**Für „benachrichtigen ab"/„Alarm ab" (pro Farbstufe eine Aktionsschwelle):**

| Sprache | „benachrichtigen ab" | „Alarm ab" | Begründung |
|---|---|---|---|
| tr | **X'ten itibaren bildir** | **X'ten itibaren alarm ver** | Konsistent mit Tabelle 1: Bildirim (leise) / Alarm (laut) als Verben „bildir" / „alarm ver" |
| en | **Notify from X** | **Alarm from X** | Konsistent mit Google-AEA-Sprachlogik (Notify=leise, Alarm=laut/DND-durchdringend) |
| de | **Benachrichtigen ab X** | **Alarm ab X** | Konsistent mit Android-Systembegriffen „Benachrichtigung" vs. „Alarm"-Kategorie |
| ru | **Уведомлять от X** | **Тревога от X** | Konsistent mit Tabelle 1 |
| ar | **إشعار من X** | **إنذار من X** | Konsistent mit Tabelle 1 |

Alle Zeilen dieser Unter-Tabelle: **Vorschlag (unbelegt)** — keine der geprüften Apps hat exakt dieses UI-Pattern (Farbstufe × Aktionsschwelle) in ihrer Terminologie; die Begriffe sind aus Tabelle 1/Tabelle 6 konsistent abgeleitet.

---

## 7. Sturm, Überschwemmung/Flut, Tsunami

| Sprache | Sturm | Überschwemmung/Flut | Tsunami | Quelle/Beleg |
|---|---|---|---|---|
| tr | **Fırtına** | **Sel/Taşkın** | **Tsunami** | AFAD-Presemitteilung nutzt „sel, yıldırım, dolu, hortum" (Flut, Blitz, Hagel, Tornado) als Gefahrenkategorien (direkt zitiert, afad.gov.tr); „Tsunami" ist internationales Lehnwort, auch in KOERI/AFAD-Bulletins (T0/T1-Matrix im Projekt-Spec bereits mit „Tsunami" benannt) |
| en | **Storm** | **Flood** | **Tsunami** | GDACS-Standardbegriffe (direkt zitiert: „floods", „tropical cyclones", GDACS-Doku); „Tsunami" international einheitlich |
| de | **Sturm** | **Überschwemmung/Hochwasser** | **Tsunami** | Deutscher Standardwortschatz (DWD-Warnkategorien: „Sturm/Orkan", „Hochwasser"); unbelegt für erdbebenspezifischen App-Kontext, aber etablierter Alltagsbegriff. **Hinweis:** „Flut" ist im Deutschen doppeldeutig (Gezeiten-Flut vs. Überschwemmung) — „Überschwemmung" oder „Hochwasser" ist eindeutiger. |
| ru | **Шторм** | **Наводнение** | **Цунами** | Russische Katastrophenschutz-Terminologie (МЧС/EMSD Kamtschatka direkt zitiert: «Цунами... морские волны»; «наводнения» als Standardbegriff für Überschwemmung/Hochwasser, direkt aus МЧС-Quelle) |
| ar | **عاصفة** | **فيضان** | **تسونامي** | Arabische Standardbegriffe, direkt aus Fachartikeln zitiert (asharq.com: «موجات التسونامي»); «فيضان» ist Standard-Wort für Überschwemmung in arabischen Katastrophenschutz-Texten |

**Schreibweisen-Hinweis Tsunami:** In allen fünf Sprachen wird das japanische Lehnwort weitgehend einheitlich übernommen — TR „tsunami", EN „tsunami", DE „Tsunami", RU «цунами», AR «تسونامي». Keine Fehlübersetzungsgefahr, aber auf einheitliche Transliteration achten (AR hat keine alternative Schreibweise gefunden, «تسونامي» ist durchgängig).

---

## 8. Weitere Standorte / „Familie & Freunde hinzufügen"

| Sprache | „Weitere Standorte" | „Familie & Freunde hinzufügen" | Belegtstatus |
|---|---|---|---|
| tr | **Diğer Konumlar** | **Aile ve Arkadaşlar Ekle** | Vorschlag (unbelegt) — AFAD Acil Çağrı hat KEIN offizielles Familie/Freunde-Standort-Feature; ein App-Store-Nutzerkommentar fordert genau dieses Feature explizit („Aile üyelerini... afet anında buluşmasını sağlamak için... konumlarını haritada gösterme"), was den Nutzerbedarf, aber keinen offiziellen Begriff belegt. Vergleichbare App „Bip" nutzt „konum paylaş" (Standort teilen). |
| en | **More Locations** | **Add Family & Friends** | Vorschlag (unbelegt) — Life360 (nicht erdbebenspezifisch, aber dominantes Referenzprodukt für dieses Pattern) nutzt „Family & Friends" als Kategorie-Label; keine Erdbeben-App mit exakt diesem Feature gefunden |
| de | **Weitere Standorte** | **Familie & Freunde hinzufügen** | Vorschlag (unbelegt), analog Life360-Konvention |
| ru | **Другие места** | **Добавить семью и друзей** | Vorschlag (unbelegt), analog Life360-Konvention |
| ar | **مواقع أخرى** | **إضافة العائلة والأصدقاء** | Vorschlag (unbelegt), analog Life360-Konvention |

**Hinweis:** Für dieses Konzept gibt es unter den geprüften Erdbeben-/Katastrophen-Apps **keine** belegte Referenzterminologie — das Feature „Standorte für Angehörige mitverfolgen" ist bei AFAD, Kandilli, EMSC, MyShake, Google AEA, Earthquake Network und VolcanoDiscovery nicht vorhanden. Die Empfehlungen orientieren sich an Life360 (dominantes UX-Referenzprodukt für „Familie & Freunde"-Standort-Sharing) und sind als Analogieschluss, nicht als direkter Terminologie-Beleg zu verstehen.

---

## 9. Onboarding-CTAs: „Meinen Standort verwenden", „Benachrichtigungen aktivieren", „Probealarm zeigen", „Entwarnung"

| Sprache | Meinen Standort verwenden | Benachrichtigungen aktivieren | Probealarm zeigen | Entwarnung |
|---|---|---|---|---|
| tr | **Konumumu Kullan** | **Bildirimleri Etkinleştir** | **Deneme Alarmı Göster** | **Tehlike Geçti** |
| en | **Use My Location** | **Enable Notifications** | **Show Test Alarm** | **All Clear** |
| de | **Meinen Standort verwenden** | **Benachrichtigungen aktivieren** | **Probealarm anzeigen** | **Entwarnung** |
| ru | **Использовать моё местоположение** | **Включить уведомления** | **Показать тестовый сигнал** | **Отбой тревоги** |
| ar | **استخدام موقعي** | **تفعيل الإشعارات** | **عرض تنبيه تجريبي** | **انتهاء الخطر** |

Belegtstatus: **Vorschlag, teils gestützt.** „Konumumu Kullan"/„Use My Location"/„Meinen Standort verwenden"/«Использовать моё местоположение»/«استخدام موقعي» sind plattformweite Standard-CTAs (Google Maps, iOS/Android-Systemdialoge) — hohe Wiedererkennbarkeit, aber nicht erdbebenspezifisch belegt, daher als etabliertes Standardmuster (nicht als Einzelquelle zitierbar) übernommen. „Probealarm"/„Test Alarm" wurde bei keiner geprüften App wörtlich gefunden (Earthquake Network „Panic" hat einen „Test notification"-Button laut Support-Dokumentation, aber ohne exakten Zielsprachen-String). „Entwarnung"/„All Clear" ist deutscher Standardbegriff aus dem Zivilschutz (Sirenensignal „Entwarnung"), international via GDACS/behördlicher Praxis sinngemäß, aber ohne wörtlichen App-Beleg in allen 5 Sprachen — als **Vorschlag (unbelegt)** gekennzeichnet, jedoch mit hoher Konfidenz wegen etablierter Zivilschutz-Bedeutung (Sirenen-Fachbegriff in DE, AFAD-Kontext „tehlike geçti" ist gängige TR-Presseformulierung nach Entwarnungen).

---

## 10. Frühwarnung / „Sekunden bevor es bebt"

| Sprache | Frühwarnung | „Sekunden bevor es bebt" | Quelle/Beleg |
|---|---|---|---|
| tr | **Erken Uyarı** | **„Sarsıntı Başlamadan Saniyeler Önce"** | AFAD/Presse nutzt „erken uyarı" durchgängig (direkt zitiert, aa.com.tr, turkiyesigorta.com.tr: „Erken Uyarı Sistemleri"); Presse-Formulierung „X saniye önceden haber verdi" ist Standardmuster (bigpara.hurriyet.com.tr: „30 sn önceden haber verdi") |
| en | **Early Warning** | **„Seconds Before It Shakes"** | MyShake/USGS ShakeAlert offizieller Produktname „Earthquake Early Warning" (EEW), direkt zitiert (earthquake.ca.gov); „seconds of warning" ist durchgängige Presseformulierung (freepressjournal.in: „warnings seconds before Venezuela tremors struck") |
| de | **Frühwarnung** | **„Sekunden bevor es bebt"** | Deutsche Presse nutzt „Erdbeben-Frühwarnsystem" durchgängig (t3n.de, futurezone.at, direkt zitiert); Nutzerzitat „10 Sekunden bevor es passierte" (smartdroid.de) stützt die Sekunden-Formulierung |
| ru | **Раннее оповещение** | **„За несколько секунд до толчков"** | Russische Presse nutzt «система раннего оповещения» sinngemäß (RBC, habr.com beschreiben das Prinzip); exaktes Label nicht wörtlich als Produktname gefunden → **Vorschlag, sekundär gestützt**. Alternative: **„Экстренное оповещение о землетрясениях"** (offizieller Google-RU-Begriff, direkt zitiert: „Оповещения о землетрясениях") als App-Feature-Name, „раннее оповещение" als beschreibendes Konzept. |
| ar | **الإنذار المبكر** | **„ثوانٍ قبل وقوع الهزة"** | Arabische Presse nutzt «الإنذار المبكر» durchgängig als Fachbegriff (newturkpost.com: „نظام الإنذار من الزلازل"; masrawy.com beschreibt das Prinzip „قبل وقوعها" = bevor es passiert); direkt zitierte Formulierung „ثوانٍ ثمينة للتصرف" (kostbare Sekunden zum Handeln, masrawy.com) stützt die Sekunden-Formulierung |

**Hinweis:** Hier ist "الإنذار" (Alarm/Warnung) für den Systemnamen "Frühwarnung" korrekt und unproblematisch — Verwechslungsgefahr mit der lauten Einzelstufe (Tabelle 1, dort ebenfalls «إنذار» empfohlen) besteht nur, wenn beide Begriffe im selben UI-Screen ohne Kontext nebeneinanderstehen. Da „Frühwarnung" ein Systemname (Substantiv, Feature-Ebene) und „Alarm ab X" eine Handlungsschwelle (Verb-Ebene) ist, ist die Doppelverwendung in der arabischen Fachsprache üblich und unproblematisch (vgl. „Android Earthquake Alerts" = «تنبيهات الزلازل» als Systemname vs. «إنذار» als Einzelalarm).

---

## Wichtigste empfohlene Änderungen gegenüber generischen/naheliegenden Begriffen

- **„Alarm" statt „Warnung" für die laute Stufe, in allen 5 Sprachen.** Google AEA hat mit „Take Action"/„Harekete Geç"/«Действуйте немедленно» bereits das mentale Modell etabliert, dass die laute, DND-durchdringende Stufe eine *Handlungsaufforderung* ist, keine bloße Information. „Alarm" (bzw. TR alarm, DE Alarm, RU тревога, AR إنذار) transportiert das eindeutiger als ein generisches „Warnung", das in allen Sprachen für beide Stufen missverständlich verwendbar wäre.
- **Türkisch: „Büyüklük" ist der einzig korrekte Begriff für Magnitude — niemals „Şiddet".** AFAD führt dazu sogar eine eigene Erklärseite, weil die Verwechslung in Laienmedien systematisch vorkommt. Für unsere App ist das ein hartes Muss, da „ab Stärke X" fachlich Magnitude meint.
- **Deutsch: „Magnitude" statt „Stärke" im UI verwenden, „Erschütterung(sstärke)" für die am Ort gefühlte Wirkung reservieren.** Google DE selbst benutzt „Stärke" uneinheitlich für beide Konzepte — das ist eine Fehlerquelle, die wir in der App nicht übernehmen sollten.
- **Arabisch: „قوة" (nicht „شدة") für Magnitude, „شدة" ausschließlich für Intensität.** Google AR trennt das sauber; viele arabische Boulevardmedien vermischen es. Klare Trennung ist ein Differenzierungsmerkmal gegenüber Laienquellen.
- **Deutsch: „Überschwemmung/Hochwasser" statt „Flut" verwenden** — „Flut" ist im Deutschen durch die Gezeiten-Bedeutung doppeldeutig, „Hochwasser" ist eindeutig und DWD-konform.
- **„Radius" durch alltagsnähere Alternativen ergänzen/ersetzen prüfen:** DE „Umkreis" statt „Radius", AR „النطاق" statt des mathematischen «نصف القطر» — beide sind für Laien schneller erfassbar als die Fachbegriffe, ohne Präzision zu verlieren.
- **„Familie & Freunde"-Feature hat keine Referenzterminologie unter Erdbeben-Apps** — hier keine Standard-Fachterminologie zu kopieren, sondern konsequent an Life360-artige, bereits eingeführte Mainstream-UX-Sprache anlehnen (höhere Wiedererkennbarkeit bei einer Zielgruppe, die eher Life360/WhatsApp-Standortfreigabe kennt als Erdbeben-Fachapps).
- **Bei Google AEA-Zitaten in de/ru/ar ist die Übersetzung der Stufennamen nur sekundär (über Presse) belegt, nicht über eine offizielle Google-Hilfeseite mit exaktem String.** Für tr/en ist die Bestätigung durch mehrere unabhängige Quellen deutlich stärker. Vor Festlegung der finalen UI-Strings empfehlen wir, wo möglich, die Android-Systemsprache direkt auf einem Gerät mit de/ru/ar-Locale zu prüfen (Einstellungen → Sicherheit & Notfall → Erdbebenwarnungen), um die Google-Originaltexte 1:1 zu verifizieren.
- **Tsunami-Schreibweise ist in allen 5 Sprachen unproblematisch** (durchgängig als Lehnwort übernommen) — hier besteht kein Korrekturbedarf, anders als ursprünglich vermutet.
