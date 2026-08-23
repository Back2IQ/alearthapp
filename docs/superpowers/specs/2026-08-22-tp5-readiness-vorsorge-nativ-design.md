# TP-5 · Readiness/Vorsorge nativ — Design

**Datum:** 2026-08-22
**Teilprojekt:** TP-5 (nativer Android-Client `tda/android`)
**Ziel (ein Satz):** Der Android-Client bekommt eine untere Tab-Navigation und einen „Bereitschaft"-Bereich, der aus einem Minimal-Profil + einer Ausrüstungs-Checkliste einen ehrlichen Readiness-Score und eine Lückenliste mit gekennzeichneten Affiliate-Such-Links erzeugt, samt Brücke aus dem Ernstfall.

---

## Kontext

Baut auf dem bestehenden Client auf (`tda/android`, Kotlin, Gradle 8.11 / JDK 17). Ist-Stand relevant:
- `MainActivity` ist heute **ein einziger Scroll-Screen** (Technik-/Test-Konsole): Status-Chip, Stadt/Sprache/Theme/Farbenblind, Live-Seismogramm (`SeismogramView`), Nachbeben-Sequenz, Test-Szenarien (`TestScenarios`), Server-Verbindung (`ServerLink`). **Keine Tab-Navigation, kein Profil, keine Vorsorge.**
- `MainActivity` trägt den **Alarmempfang**: `observeEventBus()` beobachtet `EventBus.alarm` und startet `AlertActivity` + postet die Full-Screen-Intent-Notification (`postAlarmFullScreen`), plus Status/Sequenz/Disturbance. Dieser Fluss ist in TP-3 verifiziert und **darf nicht brechen**.
- `Prefs` (SharedPreferences `tda_prefs`): Sprache, Theme, Farbenblind, Stadt + die acht TP-3-Parameter.
- Die **Web-App** hat die Vorsorge-Logik bereits (`webapp/lib/prep.js`): `buildAffiliateUrl(query, tag) → amazon.com.tr/s?k=…&tag=…`, `localSearchUrl`, `PREP_CATALOG` (Gefahrentypen all/quake/flood/storm/wildfire mit Item-Keys + Suchbegriffen), Platzhalter-Tag `TDA-PLACEHOLDER-21`. Diese Logik wird nach Kotlin **portiert** (nicht neu erfunden).
- Material-Lib ist vorhanden (`com.google.android.material:material:1.12.0` → `BottomNavigationView`); JUnit-Test-Sourceset existiert (aus TP-3).

## Gestaltungsgrundsatz (bindend)

**So einfach wie möglich, ehrlich vor Umsatz.** Der Score personalisiert nur so weit wie nötig (Checkliste + drei Profil-Schalter). Affiliate erscheint **nur** im Bereitschaft-Bereich, nie in Alarm-/SOS-/Beacon-Flächen; jeder Link klar gekennzeichnet; keine Preise/Bewertungen vortäuschen; kein Standort-/Nutzerdatenabfluss (statische Such-Links, kein Tracker, kein Amazon-WebView).

## Umfang

**Drin (TP-5):**
1. **Navigations-Schale** — untere Tab-Leiste + Fragmente; Alarm-Plumbing bleibt in der Host-Activity.
2. **Minimal-Profil** — Haustier / Kinder / Auto + Haushaltsgröße.
3. **Readiness-Score + Lücken-/Checkliste** — aus Katalog × Anwendbarkeit × „hab ich".
4. **Vorsorge-Katalog mit Affiliate** — gekennzeichnete `amazon.com.tr`-Such-Links + lokale Websuche.
5. **Post-Event-Brücke** — Knopf nach „Ich bin in Sicherheit" → Bereitschaft-Tab (Beben-Fokus).

**Nicht in TP-5 (eigene Folge-Specs):** Erinnerungen (WorkManager: „Batterien/Wasser/Dokumente prüfen"), Wirkungs-Tab (ehrliche Fassung: belegte Opferzahlen + eigene Metriken).
**Nicht anfassen:** `Signing`, `ServerLink`-Verifikationskern, `Eew`-Formeln, der TP-3-Alarm-/Beacon-Kern (außer dem kleinen Post-Event-Knopf in `BeaconActivity`).

---

## Architektur

### B · Navigations-Schale

`MainActivity` wird zum **Fragment-Host**:
- Layout `activity_main.xml` wird ersetzt durch einen Rahmen mit `FragmentContainerView` (oben, `layout_weight`) + `com.google.android.material.bottomnavigation.BottomNavigationView` (unten). Menü `res/menu/bottom_nav.xml` mit drei Einträgen.
- Drei Fragmente:
  - **`HomeFragment`** — Status-Chip, `SeismogramView`, Nachbeben-Sequenz (die bisherigen Produkt-Teile). Der bisherige `activity_main`-Inhalt wird auf die Fragmente aufgeteilt: Status/Seismogramm/Sequenz → Home.
  - **`ReadinessFragment`** — neuer Bereitschaft-Screen (Abschnitt C/D).
  - **`SettingsFragment`** — Stadt/Sprache/Theme/Farbenblind + ausklappbarer **Entwickler-Bereich** (`test scenarios` + Server-Verbindung), hinter einem „Entwickler"-Ausklapp-Header.
- **Alarm-Plumbing bleibt in `MainActivity`:** `observeEventBus()`, `postAlarmFullScreen`, `launchAlert`, Status-/Sequenz-/Disturbance-Verteilung bleiben in der Host-Activity und schreiben in einen geteilten Zustand, den `HomeFragment` beobachtet (einfachste Variante: `MainActivity` hält die `EventBus`-Collector und aktualisiert das sichtbare `HomeFragment` über eine schlanke Callback-/`findFragment`-Referenz; kein neues Architektur-Framework). So bleibt der TP-3-Fluss tab-unabhängig aktiv.
- Startet die Activity mit Extra `EXTRA_OPEN_TAB` (String), wird der genannte Tab vorausgewählt (für die Post-Event-Brücke).

### C · Minimal-Profil + reine Readiness-Logik

**Profil (in `Prefs`):** `hasPet: Boolean` (default false), `hasKids: Boolean` (false), `hasCar: Boolean` (false), `householdSize: Int` (default 1, Bereich 1–12).

**`ReadinessCatalog`** (Kotlin-Port + Erweiterung von `PREP_CATALOG`, reine Daten):
```
data class PrepItem(val key: String, val query: String, val group: Group)
enum class Group { BASE, QUAKE, PET, KIDS, CAR }
object ReadinessCatalog {
  val ITEMS: List<PrepItem>            // BASE (Wasser, Erste-Hilfe, Notgepäck, Dokumente, Kurbelradio, Powerbank)
                                       // QUAKE (Trillerpfeife, Helm, FFP2, Rettungsdecke)
                                       // PET (Transportbox, Tierfutter-Vorrat), KIDS (Kinder-Erste-Hilfe, Windeln/Bedarf),
                                       // CAR (Starthilfe, Warnweste, Abschleppseil)
  fun applicable(hasPet: Boolean, hasKids: Boolean, hasCar: Boolean): List<PrepItem>  // BASE+QUAKE immer, Gruppen je Schalter
}
```
Die i18n-Stämme bleiben kompatibel: `prep_item_<key>` (Name), `prep_why_<key>` (Nutzen-Satz).

**`ReadinessScore`** (rein, testbar):
```
data class Readiness(val score: Int, val missingCount: Int, val total: Int)
object ReadinessScore {
  fun compute(applicable: List<PrepItem>, ownedKeys: Set<String>): Readiness
  // score = round(100 * owned/total) über die anwendbaren Positionen; total=applicable.size;
  // missingCount = total - (anwendbare, die in ownedKeys sind); leere Liste → score 0, missing 0.
}
```

**`Affiliate`** (rein, testbar; Port aus prep.js):
```
object Affiliate {
  const val TAG = "TDA-PLACEHOLDER-21"
  fun amazonSearchUrl(query: String, tag: String = TAG): String  // https://www.amazon.com.tr/s?k=<enc>&tag=<enc>
  fun localSearchUrl(query: String): String                      // https://www.google.com/search?q=<enc>
}
```
`ownedKeys` (Set<String>) persistiert in `Prefs` (als String-Set).

### D · `ReadinessFragment` (UI)

- **Kopf:** großer Score (`63/100`) + Zeile „dir fehlen N Dinge für 72 Stunden"; darunter die drei Profil-Schalter (Haustier/Kinder/Auto) + Haushaltsgröße; Änderung rechnet Score/Liste neu.
- **Affiliate-Hinweis** (Pflicht, sichtbar oben): „Werbung/Affiliate-Link: Wenn du über diese Links kaufst, erhalten wir evtl. eine Provision — für dich ändert sich der Preis nicht."
- **Liste** (dynamisch aus `applicable`): je Position Name + Nutzen-Satz + Checkbox „hab ich" (schreibt `ownedKeys`) + Knopf „Bei Amazon suchen" (`Affiliate.amazonSearchUrl`, öffnet Browser-Intent) + „Lokal suchen" (`localSearchUrl`). Keine Preise, keine Bewertungen, kein WebView.

### E · Post-Event-Brücke

In `BeaconActivity.onSafe()` (nach „Ich bin in Sicherheit", **nur dort**, nie im aktiven Beacon/Alarm) ein zusätzlicher Knopf **„Für die Nachbeben-Tage vorbereiten"** → `Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, "readiness").addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP)`. `MainActivity` wählt beim Start diesen Tab vor.

### F · Reine Logik + Tests (JVM)

- `ReadinessScoreTest` — Score-Mathematik (0/leer, teils, voll), `missingCount`, Rundung.
- `ReadinessCatalogTest` — `applicable`: BASE+QUAKE immer dabei; PET/KIDS/CAR nur bei gesetztem Schalter; keine Duplikate.
- `AffiliateTest` — korrekte Enkodierung, Tag angehängt, kein Doppel-Encoding; `localSearchUrl` neutral.

### G · i18n

Alle neuen Strings in **TR/EN/KU/AR**: Tab-Titel (Start/Bereitschaft/Einstellungen), Entwickler-Bereich-Header, Score-/„fehlen N Dinge"-Text, Profil-Schalter, Affiliate-Hinweis, „Bei Amazon suchen"/„Lokal suchen", Post-Event-Knopf, sowie `prep_item_<key>`/`prep_why_<key>` für alle Katalog-Positionen.

---

## Berührte Dateien

**Neu:** `ReadinessCatalog.kt`, `ReadinessScore.kt`, `Affiliate.kt`, `HomeFragment.kt`, `ReadinessFragment.kt`, `SettingsFragment.kt`, Layouts `fragment_home.xml`/`fragment_readiness.xml`/`fragment_settings.xml`/`view_prep_item.xml`, `res/menu/bottom_nav.xml`, Tests `ReadinessScoreTest.kt`/`ReadinessCatalogTest.kt`/`AffiliateTest.kt`.
**Geändert:** `MainActivity.kt` (Fragment-Host + BottomNav + `EXTRA_OPEN_TAB`; Alarm-Plumbing bleibt), `activity_main.xml` (Host-Layout), `Prefs.kt` (Profil + `ownedKeys`), `BeaconActivity.kt` (Post-Event-Knopf in `onSafe`), `res/layout/activity_beacon.xml` (Knopf), `strings.xml` + `values-tr|ku|ar/strings.xml`, ggf. `app/build.gradle` (`androidx.fragment:fragment-ktx`, falls nicht transitiv vorhanden).

## Akzeptanzkriterien

- **Navigation:** untere Leiste mit Start/Bereitschaft/Einstellungen; Tabwechsel behält Zustand; Test-Szenarien + Server nur im Entwickler-Bereich der Einstellungen.
- **Alarm-Fluss intakt:** ein Testbeben (Dev-Bereich) löst weiterhin `AlertActivity`/FSI aus, egal welcher Tab offen ist (TP-3 nicht gebrochen).
- **Score:** Profil-Schalter/Checkbox-Änderung aktualisiert Score + „fehlen N Dinge" sofort; Logik durch `ReadinessScoreTest`/`ReadinessCatalogTest` bewiesen.
- **Affiliate:** „Bei Amazon suchen" öffnet `amazon.com.tr`-Suche mit Tag; „Lokal suchen" neutrale Websuche; Hinweis sichtbar; nichts davon in Alarm-/Beacon-Flächen; kein WebView.
- **Post-Event-Brücke:** nach „Ich bin in Sicherheit" führt der Knopf in den Bereitschaft-Tab; erscheint nie im aktiven Beacon/Alarm.
- **Persistenz:** Profil + „hab ich" überleben Neustart.
- **Nullkosten/Ehrlichkeit:** keine Bezahl-API, keine Preise vorgetäuscht, kein Datenabfluss.
- **i18n:** kein Schlüssel fehlt in `values/`; TR/KU/AR ergänzt; Platzhalter konsistent.
- **Gate:** `./gradlew testDebugUnitTest` grün (neue reine-Logik-Tests + die 21 aus TP-3) **und** `./gradlew assembleDebug` grün.

## Grenzen / nicht in TP-5

- **Erinnerungen** (WorkManager) und **Wirkungs-Tab** → eigene Folge-Specs.
- **Echte Affiliate-Einnahmen** brauchen ein Amazon-Associates-Konto; Tag ist Platzhalter. **Amazon-Native-App-Politik** (Store-Freigabe, kein Amazon-WebView, eigener Inhalt) beim späteren Release beachten.
- Kein Umbau des Sicherheits-/Signatur-Kerns; kein neues Architektur-Framework (kein DI/Room) — Fragmente lesen/schreiben `Prefs` direkt.
