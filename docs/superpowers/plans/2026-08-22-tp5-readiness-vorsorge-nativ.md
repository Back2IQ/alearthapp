# TP-5 Readiness/Vorsorge nativ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Android-Client bekommt eine untere Tab-Navigation und einen „Bereitschaft"-Bereich, der aus Minimal-Profil + Ausrüstungs-Checkliste einen Readiness-Score und eine Lückenliste mit gekennzeichneten Affiliate-Such-Links erzeugt, plus eine Brücke aus dem Ernstfall.

**Architecture:** Reine, JVM-testbare Logik (`Affiliate`, `ReadinessCatalog`, `ReadinessScore`) wie bei `Eew`/`SafetyState`; darüber eine Fragment-Schale — `MainActivity` wird Host mit `BottomNavigationView` und behält den TP-3-Alarmempfang (EventBus→AlertActivity/FSI), während Status/Seismogramm/Sequenz, Einstellungen+Entwickler-Bereich und der neue Bereitschaft-Screen in Fragmente wandern. Persistenz in `Prefs`.

**Tech Stack:** Kotlin, Gradle 8.11 / JDK 17, AndroidX (appcompat bringt `androidx.fragment` transitiv), Material `BottomNavigationView` (Lib vorhanden), kotlinx-coroutines, JUnit4. compileSdk 36, minSdk 26.

## Global Constraints

- **Nullkosten:** statische Such-Links, keine Bezahl-API, kein Tracker, kein Amazon-WebView.
- **Ehrlichkeit vor Umsatz:** Affiliate NUR im Bereitschaft-Bereich, nie in Alarm-/SOS-/Beacon-Flächen; jeder Link als „Werbung/Affiliate" gekennzeichnet; keine Preise/Bewertungen vortäuschen; kein Standort-/Nutzerdatenabfluss.
- **Affiliate-Tag Platzhalter:** `TDA-PLACEHOLDER-21`; Amazon-Domain `https://www.amazon.com.tr/s?k=<query>&tag=<tag>`; lokal `https://www.google.com/search?q=<query>`.
- **TP-3-Alarmfluss darf nicht brechen:** `EventBus.alarm` → `launchAlert` + `postAlarmFullScreen` bleiben in der Host-`MainActivity`, tab-unabhängig.
- **Nicht anfassen:** `Signing`, `ServerLink`-Verifikationskern, `Eew`-Formeln, der TP-3-Alarm-/Beacon-Kern (außer dem Post-Event-Knopf in `BeaconActivity`).
- **Kein neues Architektur-Framework** (kein DI/Room/ViewModel) — Fragmente lesen/schreiben `Prefs` direkt.
- **i18n:** alle neuen Strings in TR/EN/KU/AR.
- **Kein git-commit pro Task** (Repo committet nur auf Nutzer-Wunsch). „Gate" = `testDebugUnitTest`/`assembleDebug`.

**Arbeitsverzeichnis aller Befehle:** `c:/Users/HP/Documents/projects/Earthquake/tda/android`. Empfohlene Reihenfolge: 1 → 2 → 3 → 4 → 5 → 6 → 7.

---

### Task 1: `Affiliate` (reine URL-Logik, Port aus prep.js)

**Files:**
- Create: `app/src/main/java/app/tda/Affiliate.kt`
- Test: `app/src/test/java/app/tda/AffiliateTest.kt`

**Interfaces:**
- Produces: `object Affiliate { const val TAG: String; fun amazonSearchUrl(query: String, tag: String = TAG): String; fun localSearchUrl(query: String): String }`

- [ ] **Step 1: Failing test**

Create `app/src/test/java/app/tda/AffiliateTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffiliateTest {
    @Test fun amazonUrlHasHostQueryAndTag() {
        val u = Affiliate.amazonSearchUrl("Erste-Hilfe-Set")
        assertTrue(u.startsWith("https://www.amazon.com.tr/s?k="))
        assertTrue(u.contains("Erste-Hilfe-Set"))
        assertTrue(u.contains("&tag=TDA-PLACEHOLDER-21"))
    }

    @Test fun amazonUrlEncodesSpaces() {
        val u = Affiliate.amazonSearchUrl("Trinkwasser Notvorrat")
        assertTrue(u.contains("Trinkwasser+Notvorrat") || u.contains("Trinkwasser%20Notvorrat"))
        assertTrue(!u.contains("Trinkwasser Notvorrat")) // kein rohes Leerzeichen
    }

    @Test fun localUrlIsNeutralSearch() {
        val u = Affiliate.localSearchUrl("Gummistiefel")
        assertTrue(u.startsWith("https://www.google.com/search?q="))
        assertTrue(u.contains("Gummistiefel"))
    }

    @Test fun customTagIsUsed() {
        val u = Affiliate.amazonSearchUrl("Powerbank", "MYTAG-99")
        assertEquals(true, u.endsWith("&tag=MYTAG-99"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "app.tda.AffiliateTest"`
Expected: FAIL — `Affiliate` nicht gefunden.

- [ ] **Step 3: Implement**

Create `app/src/main/java/app/tda/Affiliate.kt`:

```kotlin
package app.tda

import java.net.URLEncoder

/**
 * Reine Affiliate-/Such-URL-Logik (Port aus webapp/lib/prep.js). Nur statische Such-Links —
 * kein Tracker, keine Bezahl-API, kein Standortabfluss (spec TP-5 §D, Leitplanken).
 */
object Affiliate {
    const val TAG = "TDA-PLACEHOLDER-21"

    fun amazonSearchUrl(query: String, tag: String = TAG): String =
        "https://www.amazon.com.tr/s?k=" + enc(query) + "&tag=" + enc(tag)

    fun localSearchUrl(query: String): String =
        "https://www.google.com/search?q=" + enc(query)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "app.tda.AffiliateTest"`
Expected: PASS (4 Tests). (`URLEncoder` kodiert Leerzeichen als `+` und `-` bleibt erhalten → `TDA-PLACEHOLDER-21` unverändert.)

- [ ] **Step 5: Gate**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (Affiliate-Tests + die 21 aus TP-3 grün).

---

### Task 2: `ReadinessCatalog` + `ReadinessScore` (reine Logik)

**Files:**
- Create: `app/src/main/java/app/tda/ReadinessCatalog.kt`
- Create: `app/src/main/java/app/tda/ReadinessScore.kt`
- Test: `app/src/test/java/app/tda/ReadinessCatalogTest.kt`
- Test: `app/src/test/java/app/tda/ReadinessScoreTest.kt`

**Interfaces:**
- Produces:
  - `data class PrepItem(val key: String, val query: String, val group: Group)`
  - `enum class Group { BASE, QUAKE, PET, KIDS, CAR }`
  - `object ReadinessCatalog { val ITEMS: List<PrepItem>; fun applicable(hasPet: Boolean, hasKids: Boolean, hasCar: Boolean): List<PrepItem> }`
  - `data class Readiness(val score: Int, val missingCount: Int, val total: Int)`
  - `object ReadinessScore { fun compute(applicable: List<PrepItem>, ownedKeys: Set<String>): Readiness }`

- [ ] **Step 1: Failing tests**

Create `app/src/test/java/app/tda/ReadinessCatalogTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessCatalogTest {
    @Test fun baseAndQuakeAlwaysApplicable() {
        val out = ReadinessCatalog.applicable(hasPet = false, hasKids = false, hasCar = false)
        assertTrue(out.any { it.group == Group.BASE })
        assertTrue(out.any { it.group == Group.QUAKE })
        assertTrue(out.none { it.group == Group.PET })
        assertTrue(out.none { it.group == Group.KIDS })
        assertTrue(out.none { it.group == Group.CAR })
    }

    @Test fun togglesAddTheirGroups() {
        val out = ReadinessCatalog.applicable(hasPet = true, hasKids = true, hasCar = true)
        assertTrue(out.any { it.group == Group.PET })
        assertTrue(out.any { it.group == Group.KIDS })
        assertTrue(out.any { it.group == Group.CAR })
    }

    @Test fun keysAreUnique() {
        val keys = ReadinessCatalog.ITEMS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
```

Create `app/src/test/java/app/tda/ReadinessScoreTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessScoreTest {
    private val items = listOf(
        PrepItem("a", "qa", Group.BASE),
        PrepItem("b", "qb", Group.BASE),
        PrepItem("c", "qc", Group.QUAKE),
        PrepItem("d", "qd", Group.QUAKE),
    )

    @Test fun emptyOwnedIsZero() {
        val r = ReadinessScore.compute(items, emptySet())
        assertEquals(0, r.score); assertEquals(4, r.missingCount); assertEquals(4, r.total)
    }

    @Test fun allOwnedIsHundred() {
        val r = ReadinessScore.compute(items, setOf("a", "b", "c", "d"))
        assertEquals(100, r.score); assertEquals(0, r.missingCount)
    }

    @Test fun halfOwnedIsFifty() {
        val r = ReadinessScore.compute(items, setOf("a", "b"))
        assertEquals(50, r.score); assertEquals(2, r.missingCount)
    }

    @Test fun ownedKeysNotInListDoNotCount() {
        val r = ReadinessScore.compute(items, setOf("a", "zzz"))
        assertEquals(25, r.score); assertEquals(3, r.missingCount)
    }

    @Test fun emptyListIsZeroNotCrash() {
        val r = ReadinessScore.compute(emptyList(), setOf("a"))
        assertEquals(0, r.score); assertEquals(0, r.missingCount); assertEquals(0, r.total)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "app.tda.ReadinessCatalogTest" --tests "app.tda.ReadinessScoreTest"`
Expected: FAIL — Klassen nicht gefunden.

- [ ] **Step 3: Implement catalog**

Create `app/src/main/java/app/tda/ReadinessCatalog.kt`:

```kotlin
package app.tda

/** Eine Ausrüstungsposition (spec TP-5 §C). i18n: prep_item_<key> = Name, prep_why_<key> = Nutzen. */
data class PrepItem(val key: String, val query: String, val group: Group)

enum class Group { BASE, QUAKE, PET, KIDS, CAR }

/**
 * Katalog der Vorsorge-Positionen (Kotlin-Port + Erweiterung von webapp/lib/prep.js). Reine Daten.
 * BASE + QUAKE gelten immer; PET/KIDS/CAR nur bei gesetztem Profil-Schalter.
 */
object ReadinessCatalog {
    val ITEMS: List<PrepItem> = listOf(
        PrepItem("water", "Trinkwasser Notvorrat", Group.BASE),
        PrepItem("firstaid", "Erste-Hilfe-Set", Group.BASE),
        PrepItem("gobag", "Notfallrucksack Fluchtrucksack", Group.BASE),
        PrepItem("docs", "wasserdichte Dokumententasche", Group.BASE),
        PrepItem("radio", "Kurbelradio Notfallradio", Group.BASE),
        PrepItem("powerbank", "Powerbank", Group.BASE),
        PrepItem("whistle", "Trillerpfeife Notsignal", Group.QUAKE),
        PrepItem("helmet", "Schutzhelm", Group.QUAKE),
        PrepItem("mask", "FFP2 Staubmaske", Group.QUAKE),
        PrepItem("blanket", "Rettungsdecke", Group.QUAKE),
        PrepItem("petcarrier", "Transportbox Haustier", Group.PET),
        PrepItem("petfood", "Tierfutter Vorrat", Group.PET),
        PrepItem("kidsfirstaid", "Kinder Erste-Hilfe-Set", Group.KIDS),
        PrepItem("kidssupplies", "Windeln Babybedarf", Group.KIDS),
        PrepItem("jumpstarter", "Starthilfe Powerbank Auto", Group.CAR),
        PrepItem("warnvest", "Warnweste", Group.CAR),
        PrepItem("towrope", "Abschleppseil", Group.CAR),
    )

    fun applicable(hasPet: Boolean, hasKids: Boolean, hasCar: Boolean): List<PrepItem> =
        ITEMS.filter {
            when (it.group) {
                Group.BASE, Group.QUAKE -> true
                Group.PET -> hasPet
                Group.KIDS -> hasKids
                Group.CAR -> hasCar
            }
        }
}
```

- [ ] **Step 4: Implement score**

Create `app/src/main/java/app/tda/ReadinessScore.kt`:

```kotlin
package app.tda

/** Ergebnis der Readiness-Berechnung (spec TP-5 §C). */
data class Readiness(val score: Int, val missingCount: Int, val total: Int)

/**
 * Reine Score-Mathematik: Anteil der besessenen an den anwendbaren Positionen (0–100),
 * plus Anzahl fehlender. Leere Liste → 0/0/0, kein Absturz. JVM-testbar.
 */
object ReadinessScore {
    fun compute(applicable: List<PrepItem>, ownedKeys: Set<String>): Readiness {
        val total = applicable.size
        if (total == 0) return Readiness(0, 0, 0)
        val owned = applicable.count { ownedKeys.contains(it.key) }
        val score = Math.round(100.0 * owned / total).toInt()
        return Readiness(score, total - owned, total)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "app.tda.ReadinessCatalogTest" --tests "app.tda.ReadinessScoreTest"`
Expected: PASS (3 + 5 Tests).

- [ ] **Step 6: Gate**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

---

### Task 3: `Prefs` — Profil + „hab ich"-Zustand

**Files:**
- Modify: `app/src/main/java/app/tda/Prefs.kt`

**Interfaces:**
- Produces (neue `Prefs`-Properties): `hasPet: Boolean`, `hasKids: Boolean`, `hasCar: Boolean`, `householdSize: Int` (1–12), `ownedKeys: Set<String>`, plus `fun setOwned(key: String, owned: Boolean)`.

- [ ] **Step 1: Keys ergänzen**

In `app/src/main/java/app/tda/Prefs.kt` bei den `private const val KEY_...`-Zeilen ergänzen:

```kotlin
    private const val KEY_HAS_PET = "has_pet"
    private const val KEY_HAS_KIDS = "has_kids"
    private const val KEY_HAS_CAR = "has_car"
    private const val KEY_HOUSEHOLD = "household_size"
    private const val KEY_OWNED = "owned_keys"
```

- [ ] **Step 2: Properties + Helfer ergänzen**

In `Prefs` vor die schließende `}` einfügen:

```kotlin
    var hasPet: Boolean
        get() = prefs.getBoolean(KEY_HAS_PET, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_PET, v).apply() }

    var hasKids: Boolean
        get() = prefs.getBoolean(KEY_HAS_KIDS, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_KIDS, v).apply() }

    var hasCar: Boolean
        get() = prefs.getBoolean(KEY_HAS_CAR, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_CAR, v).apply() }

    var householdSize: Int
        get() = prefs.getInt(KEY_HOUSEHOLD, 1).coerceIn(1, 12)
        set(v) { prefs.edit().putInt(KEY_HOUSEHOLD, v.coerceIn(1, 12)).apply() }

    /** Set der Item-Keys, die der Nutzer als „hab ich" markiert hat. */
    var ownedKeys: Set<String>
        get() = prefs.getStringSet(KEY_OWNED, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_OWNED, HashSet(v)).apply() }

    /** Setzt/entfernt einen einzelnen Key (SharedPreferences-Set nie in-place mutieren). */
    fun setOwned(key: String, owned: Boolean) {
        val next = ownedKeys.toMutableSet()
        if (owned) next.add(key) else next.remove(key)
        ownedKeys = next
    }
```

- [ ] **Step 3: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Navigations-Schale (Fragment-Host + Home/Settings-Fragmente)

Wandelt `MainActivity` in einen Fragment-Host mit unterer Tab-Leiste um. **Der Alarmempfang bleibt in `MainActivity`.** Status/Seismogramm/Sequenz → `HomeFragment`; Stadt/Sprache/Theme/Farbenblind + Entwickler-Bereich (Test/Server) → `SettingsFragment`.

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml` (Host-Layout)
- Create: `app/src/main/res/menu/bottom_nav.xml`
- Create: `app/src/main/res/drawable/ic_nav_home.xml`, `ic_nav_readiness.xml`, `ic_nav_settings.xml`
- Create: `app/src/main/res/layout/fragment_home.xml`, `fragment_settings.xml`
- Create: `app/src/main/java/app/tda/HomeFragment.kt`, `SettingsFragment.kt`
- Create: `app/src/main/res/layout/fragment_readiness.xml` (Platzhalter-Layout, damit der Menü-Eintrag ein Ziel hat; Inhalt in Task 5)
- Modify: `app/src/main/java/app/tda/MainActivity.kt` (Host)
- Modify: `app/src/main/res/values/strings.xml` (Tab-/Dev-Strings)

**Interfaces:**
- Consumes: `EventBus`, `Prefs`, `TestScenarios`, `ServerLink`, `Eew`, `themeColor`, `NotificationChannels`, `AlarmService`.
- Produces: `MainActivity.EXTRA_OPEN_TAB: String` (Werte `"home"`/`"readiness"`/`"settings"`); `HomeFragment`, `SettingsFragment`, `ReadinessFragment` (Task 5 füllt es).

- [ ] **Step 1: Host-Layout**

Ersetze `app/src/main/res/layout/activity_main.xml` vollständig durch:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHost"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:menu="@menu/bottom_nav" />
</LinearLayout>
```

- [ ] **Step 2: Menü + Icons**

Create `app/src/main/res/menu/bottom_nav.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/nav_home" android:icon="@drawable/ic_nav_home" android:title="@string/tab_home" />
    <item android:id="@+id/nav_readiness" android:icon="@drawable/ic_nav_readiness" android:title="@string/tab_readiness" />
    <item android:id="@+id/nav_settings" android:icon="@drawable/ic_nav_settings" android:title="@string/tab_settings" />
</menu>
```

Create `app/src/main/res/drawable/ic_nav_home.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/textColorSecondary">
    <path android:fillColor="@android:color/white"
        android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z" />
</vector>
```

Create `app/src/main/res/drawable/ic_nav_readiness.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/textColorSecondary">
    <path android:fillColor="@android:color/white"
        android:pathData="M9,16.2l-3.5,-3.5 -1.4,1.4L9,19 20,8l-1.4,-1.4z" />
</vector>
```

Create `app/src/main/res/drawable/ic_nav_settings.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/textColorSecondary">
    <path android:fillColor="@android:color/white"
        android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z" />
</vector>
```

- [ ] **Step 3: Home-Fragment-Layout**

Create `app/src/main/res/layout/fragment_home.xml` (Status/Seismogramm/Sequenz — aus dem alten activity_main übernommen):

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:fillViewport="true">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="16dp">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">
            <TextView android:id="@+id/appNameText" android:layout_width="0dp" android:layout_height="wrap_content"
                android:text="@string/app_name" android:textSize="20sp" android:textStyle="bold"
                app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toStartOf="@id/statusChip" />
            <LinearLayout android:id="@+id/statusChip" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:orientation="horizontal" android:gravity="center_vertical" android:background="@drawable/bg_chip"
                android:contentDescription="@string/cd_status_chip"
                app:layout_constraintTop_toTopOf="parent" app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintEnd_toEndOf="parent">
                <ImageView android:id="@+id/statusIcon" android:layout_width="18dp" android:layout_height="18dp"
                    android:layout_marginEnd="6dp" android:importantForAccessibility="no" android:src="@drawable/ic_status_ready" />
                <TextView android:id="@+id/statusText" android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:textStyle="bold" android:text="@string/status_ready" />
            </LinearLayout>
        </androidx.constraintlayout.widget.ConstraintLayout>

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textStyle="bold" android:text="@string/seismogram_title" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="12sp" android:layout_marginBottom="8dp" android:text="@string/seismogram_subtitle" />
        <app.tda.SeismogramView android:id="@+id/seismogramView" android:layout_width="match_parent"
            android:layout_height="140dp" android:contentDescription="@string/cd_seismogram" android:layout_marginBottom="16dp" />

        <View android:layout_width="match_parent" android:layout_height="1dp" android:background="#33808080" android:layout_marginBottom="12dp" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textStyle="bold" android:text="@string/sequence_title" />
        <TextView android:id="@+id/sequenceEmptyText" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="12sp" android:layout_marginBottom="8dp" android:text="@string/sequence_empty" />
        <LinearLayout android:id="@+id/sequenceContainer" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 4: HomeFragment**

Create `app/src/main/java/app/tda/HomeFragment.kt` (beobachtet nur Anzeige-Ströme `status`/`sequence`; kein Alarm-Launch):

```kotlin
package app.tda

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Start-Tab: Status, Live-Seismogramm, Nachbeben-Sequenz. Reine Anzeige — der Alarm-Launch
 * lebt in [MainActivity] und ist tab-unabhängig. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<SeismogramView>(R.id.seismogramView).applyColorblindSafe(Prefs.colorblindSafe)
        viewLifecycleOwner.lifecycleScope.launch {
            EventBus.status.collect { renderStatusChip(view, it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            EventBus.sequence.collect { renderSequence(view, it) }
        }
    }

    private fun renderStatusChip(view: View, status: StatusState) {
        val icon = view.findViewById<ImageView>(R.id.statusIcon)
        val text = view.findViewById<TextView>(R.id.statusText)
        val chip = view.findViewById<View>(R.id.statusChip)
        val (drawableRes, stringRes, attrRes) = when (status) {
            StatusState.READY -> Triple(R.drawable.ic_status_ready, R.string.status_ready, R.attr.colorReady)
            StatusState.ATTENTION -> Triple(R.drawable.ic_status_attention, R.string.status_attention, R.attr.colorAttention)
            StatusState.ALARM_P0 -> Triple(R.drawable.ic_tier_p0, R.string.status_alarm_p0, R.attr.colorTierP0)
            StatusState.CONFIRMED_P2 -> Triple(R.drawable.ic_tier_p2, R.string.status_confirmed_p2, R.attr.colorTierP2)
            StatusState.DISTURBANCE_DISCARDED -> Triple(R.drawable.ic_status_block, R.string.status_disturbance_discarded, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes)
        text.setText(stringRes)
        chip.backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().themeColor(attrRes))
    }

    private fun renderSequence(view: View, entries: List<Eew.SequenceEntry>) {
        val container = view.findViewById<android.widget.LinearLayout>(R.id.sequenceContainer)
        val emptyText = view.findViewById<TextView>(R.id.sequenceEmptyText)
        container.removeAllViews()
        if (entries.isEmpty()) { emptyText.visibility = View.VISIBLE; return }
        emptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (entry in entries) {
            val row = inflater.inflate(R.layout.view_sequence_item, container, false)
            row.findViewById<ImageView>(R.id.itemIcon).setImageResource(if (entry.isMainshock) R.drawable.ic_tier_p0 else R.drawable.ic_tier_p1)
            val label = getString(if (entry.isMainshock) R.string.sequence_mainshock else R.string.sequence_aftershock)
            row.findViewById<TextView>(R.id.itemText).text =
                getString(R.string.sequence_item_format, label, entry.magnitude, Eew.mmiRoman(entry.mmiAtUser), formatTime(entry.timestamp))
            container.addView(row)
        }
    }

    private fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ts))
}
```

- [ ] **Step 5: Settings-Fragment-Layout**

Create `app/src/main/res/layout/fragment_settings.xml` (Stadt/Sprache/Theme/Farbenblind + ausklappbarer Entwickler-Bereich; Steuer-IDs identisch zu den alten, damit die Logik 1:1 umzieht):

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="16dp">

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/label_city" />
        <Spinner android:id="@+id/citySpinner" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="12dp" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/label_language" />
        <Spinner android:id="@+id/languageSpinner" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="12dp" />

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/label_theme" />
        <RadioGroup android:id="@+id/themeRadioGroup" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginBottom="8dp">
            <RadioButton android:id="@+id/radioThemeSystem" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/theme_system" android:checked="true" />
            <RadioButton android:id="@+id/radioThemeLight" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginStart="12dp" android:text="@string/theme_light" />
            <RadioButton android:id="@+id/radioThemeDark" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginStart="12dp" android:text="@string/theme_dark" />
        </RadioGroup>

        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"
            android:gravity="center_vertical" android:layout_marginBottom="16dp">
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="@string/label_colorblind_safe" />
            <androidx.appcompat.widget.SwitchCompat android:id="@+id/colorblindSwitch" android:layout_width="wrap_content" android:layout_height="wrap_content" />
        </LinearLayout>

        <View android:layout_width="match_parent" android:layout_height="1dp" android:background="#33808080" android:layout_marginBottom="12dp" />

        <Button android:id="@+id/btnToggleDev" style="@style/Widget.AppCompat.Button.Borderless"
            android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/dev_section_header" />

        <LinearLayout android:id="@+id/devSection" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:visibility="gone">

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:textStyle="bold"
                android:layout_marginBottom="8dp" android:text="@string/section_test_scenarios" />
            <Button android:id="@+id/btnTestQuake" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_test_quake" />
            <Button android:id="@+id/btnAftershockSequence" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_aftershock_sequence" />
            <Button android:id="@+id/btnDisturbanceFirework" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_disturbance_firework" />
            <Button android:id="@+id/btnReset" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_reset" />
            <Button android:id="@+id/btnTestBeaconChain" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_test_beacon_chain" />
            <Button android:id="@+id/btnViewReport" style="@style/Widget.AppCompat.Button.Borderless" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_view_report" />

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:textStyle="bold"
                android:layout_marginTop="12dp" android:layout_marginBottom="8dp" android:text="@string/section_server_connect" />
            <LinearLayout android:id="@+id/serverStatusChip" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:orientation="horizontal" android:gravity="center_vertical" android:background="@drawable/bg_chip"
                android:contentDescription="@string/cd_server_status_chip" android:layout_marginBottom="8dp">
                <ImageView android:id="@+id/serverStatusIcon" android:layout_width="18dp" android:layout_height="18dp" android:layout_marginEnd="6dp" android:importantForAccessibility="no" android:src="@drawable/ic_status_block" />
                <TextView android:id="@+id/serverStatusText" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textStyle="bold" android:text="@string/server_status_disconnected" />
            </LinearLayout>
            <EditText android:id="@+id/editServerUrl" android:layout_width="match_parent" android:layout_height="wrap_content"
                android:inputType="textUri" android:singleLine="true" android:hint="@string/hint_server_url" android:text="ws://10.0.2.2:8000" android:layout_marginBottom="8dp" />
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginBottom="8dp">
                <Button android:id="@+id/btnServerConnect" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginEnd="4dp" android:text="@string/btn_server_connect" />
                <Button android:id="@+id/btnServerDisconnect" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="4dp" android:text="@string/btn_server_disconnect" />
            </LinearLayout>
            <Button android:id="@+id/btnServerTestQuake" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_server_test_quake" />
            <Button android:id="@+id/btnServerTestFirework" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/btn_server_test_firework" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 6: SettingsFragment**

Create `app/src/main/java/app/tda/SettingsFragment.kt` (die Steuer-/Server-Logik zieht 1:1 aus dem alten `MainActivity` um):

```kotlin
package app.tda

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Einstellungen-Tab: Stadt/Sprache/Theme/Farbenblind + ausklappbarer Entwickler-Bereich
 * (Test-Szenarien + Server-Verbindung, vorher lose in MainActivity). */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private data class LanguageOption(val tag: String, val label: String)
    private val languages = listOf(
        LanguageOption("tr", "Türkçe"), LanguageOption("en", "English"),
        LanguageOption("ku", "Kurdî"), LanguageOption("ar", "العربية")
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupCitySpinner(view)
        setupLanguageSpinner(view)
        setupThemeControls(view)
        setupDevToggle(view)
        setupTestButtons(view)
        setupServerControls(view)
    }

    private fun setupCitySpinner(view: View) {
        val spinner = view.findViewById<android.widget.Spinner>(R.id.citySpinner)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, Eew.CITIES.map { it.displayName })
        spinner.setSelection(Eew.CITIES.indexOfFirst { it.id == Prefs.cityId }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { Prefs.cityId = Eew.CITIES[pos].id }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupLanguageSpinner(view: View) {
        val spinner = view.findViewById<android.widget.Spinner>(R.id.languageSpinner)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages.map { it.label })
        spinner.setSelection(languages.indexOfFirst { it.tag == Prefs.languageTag }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val tag = languages[pos].tag
                if (tag != Prefs.languageTag) { Prefs.languageTag = tag; Prefs.applyLocale() }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupThemeControls(view: View) {
        val group = view.findViewById<android.widget.RadioGroup>(R.id.themeRadioGroup)
        when (Prefs.themeMode) {
            Prefs.ThemeMode.SYSTEM -> group.check(R.id.radioThemeSystem)
            Prefs.ThemeMode.LIGHT -> group.check(R.id.radioThemeLight)
            Prefs.ThemeMode.DARK -> group.check(R.id.radioThemeDark)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioThemeLight -> Prefs.ThemeMode.LIGHT
                R.id.radioThemeDark -> Prefs.ThemeMode.DARK
                else -> Prefs.ThemeMode.SYSTEM
            }
            if (newMode != Prefs.themeMode) { Prefs.themeMode = newMode; Prefs.applyNightMode(); requireActivity().recreate() }
        }
        val cbSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.colorblindSwitch)
        cbSwitch.isChecked = Prefs.colorblindSafe
        cbSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != Prefs.colorblindSafe) { Prefs.colorblindSafe = isChecked; requireActivity().recreate() }
        }
    }

    private fun setupDevToggle(view: View) {
        val section = view.findViewById<View>(R.id.devSection)
        view.findViewById<View>(R.id.btnToggleDev).setOnClickListener {
            section.visibility = if (section.visibility == View.GONE) View.VISIBLE else View.GONE
        }
    }

    private fun setupTestButtons(view: View) {
        view.findViewById<View>(R.id.btnTestQuake).setOnClickListener { TestScenarios.runEarthquakeDrill(viewLifecycleOwner.lifecycleScope, Prefs.selectedCity()) }
        view.findViewById<View>(R.id.btnAftershockSequence).setOnClickListener { TestScenarios.runAftershockSequence(viewLifecycleOwner.lifecycleScope, Prefs.selectedCity()) }
        view.findViewById<View>(R.id.btnDisturbanceFirework).setOnClickListener { TestScenarios.runFireworksDisturbance(viewLifecycleOwner.lifecycleScope) }
        view.findViewById<View>(R.id.btnReset).setOnClickListener { TestScenarios.reset() }
        view.findViewById<View>(R.id.btnTestBeaconChain).setOnClickListener {
            AlarmService.arm(requireContext(), mmi = 9.0, tier = Eew.Tier.P2, isTest = true)
            Toast.makeText(requireContext(), R.string.test_beacon_started, Toast.LENGTH_LONG).show()
        }
        view.findViewById<View>(R.id.btnViewReport).setOnClickListener { startActivity(android.content.Intent(requireContext(), ReportActivity::class.java)) }
    }

    private fun setupServerControls(view: View) {
        val urlField = view.findViewById<android.widget.EditText>(R.id.editServerUrl)
        view.findViewById<View>(R.id.btnServerConnect).setOnClickListener {
            val url = urlField.text.toString().trim(); if (url.isNotEmpty()) ServerLink.connect(url)
        }
        view.findViewById<View>(R.id.btnServerDisconnect).setOnClickListener { ServerLink.disconnect() }
        view.findViewById<View>(R.id.btnServerTestQuake).setOnClickListener { ServerLink.sendSimulate("quake") }
        view.findViewById<View>(R.id.btnServerTestFirework).setOnClickListener { ServerLink.sendSimulate("firework") }
        viewLifecycleOwner.lifecycleScope.launch {
            ServerLink.connState.collect { renderServerStatusChip(view, it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ServerLink.invalidSignature.collect { Toast.makeText(requireContext(), R.string.ws_invalid_signature, Toast.LENGTH_LONG).show() }
        }
    }

    private fun renderServerStatusChip(view: View, state: ServerConnState) {
        val icon = view.findViewById<ImageView>(R.id.serverStatusIcon)
        val text = view.findViewById<TextView>(R.id.serverStatusText)
        val chip = view.findViewById<View>(R.id.serverStatusChip)
        val (drawableRes, stringRes, attrRes) = when (state) {
            ServerConnState.DISCONNECTED -> Triple(R.drawable.ic_status_block, R.string.server_status_disconnected, R.attr.colorDisturbance)
            ServerConnState.CONNECTING -> Triple(R.drawable.ic_status_attention, R.string.server_status_connecting, R.attr.colorAttention)
            ServerConnState.CONNECTED -> Triple(R.drawable.ic_status_ready, R.string.server_status_connected, R.attr.colorReady)
            ServerConnState.FAILED -> Triple(R.drawable.ic_status_block, R.string.server_status_failed, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes); text.setText(stringRes)
        chip.backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().themeColor(attrRes))
    }
}
```

- [ ] **Step 7: ReadinessFragment-Platzhalter + Layout (Inhalt in Task 5)**

Create `app/src/main/res/layout/fragment_readiness.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true">
    <LinearLayout android:id="@+id/readinessRoot" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="16dp" />
</ScrollView>
```

Create `app/src/main/java/app/tda/ReadinessFragment.kt` (Platzhalter; Task 5 füllt):

```kotlin
package app.tda

import androidx.fragment.app.Fragment

/** Bereitschaft-Tab. Inhalt folgt in Task 5. */
class ReadinessFragment : Fragment(R.layout.fragment_readiness)
```

- [ ] **Step 8: MainActivity als Host umbauen**

Ersetze `app/src/main/java/app/tda/MainActivity.kt` vollständig durch:

```kotlin
package app.tda

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fragment-Host mit unterer Tab-Leiste. **Der Alarmempfang lebt hier** (nicht in einem
 * Fragment), damit ein eingehender Alarm tab-unabhängig AlertActivity/FSI auslöst
 * (TP-3-Fluss). Home/Settings/Readiness sind reine Anzeige-Fragmente.
 */
class MainActivity : AppCompatActivity() {

    companion object { const val EXTRA_OPEN_TAB = "open_tab" }

    private lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        val nav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            showFragment(when (item.itemId) {
                R.id.nav_readiness -> ReadinessFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }); true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
                "readiness" -> R.id.nav_readiness
                "settings" -> R.id.nav_settings
                else -> R.id.nav_home
            }
        }

        observeAlarmPlumbing()

        NotificationChannels.ensure(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.navHost, f).commit()
    }

    /** Alarm-Launch + Disturbance/PushNotice: bewusst in der Host-Activity, tab-unabhängig. */
    private fun observeAlarmPlumbing() {
        scope.launch {
            EventBus.disturbance.collect { showDisturbanceDialog() }
        }
        scope.launch {
            EventBus.pushNotice.collect { entry ->
                Toast.makeText(this@MainActivity,
                    getString(R.string.sequence_item_format, getString(R.string.sequence_aftershock),
                        entry.magnitude, Eew.mmiRoman(entry.mmiAtUser), formatTime(entry.timestamp)),
                    Toast.LENGTH_LONG).show()
            }
        }
        scope.launch {
            EventBus.alarm.collect { payload ->
                if (payload.ver == 1 && EventBus.lastLaunchedAlarmId != payload.id) {
                    EventBus.lastLaunchedAlarmId = payload.id
                    postAlarmFullScreen(payload)
                    launchAlert(payload)
                }
            }
        }
    }

    private fun postAlarmFullScreen(payload: Eew.AlarmPayload) {
        NotificationChannels.ensure(this)
        val city = Prefs.selectedCity()
        val full = Intent(this, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_ID, payload.id)
            putExtra(AlertActivity.EXTRA_EPI_LAT, payload.lat)
            putExtra(AlertActivity.EXTRA_EPI_LON, payload.lon)
            putExtra(AlertActivity.EXTRA_DEPTH_KM, payload.depthKm)
            putExtra(AlertActivity.EXTRA_ORIGIN_TS, payload.originTs)
            putExtra(AlertActivity.EXTRA_USER_LAT, city.lat)
            putExtra(AlertActivity.EXTRA_USER_LON, city.lon)
            putExtra(AlertActivity.EXTRA_USER_CITY_NAME, city.displayName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fsi = android.app.PendingIntent.getActivity(this, 2, full,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = androidx.core.app.NotificationCompat.Builder(this, NotificationChannels.CRITICAL)
            .setSmallIcon(R.drawable.ic_tier_p0)
            .setContentTitle(getString(R.string.alert_title_p0))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(4200, n) }
    }

    private fun launchAlert(payload: Eew.AlarmPayload) {
        val city = Prefs.selectedCity()
        startActivity(Intent(this, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_ID, payload.id)
            putExtra(AlertActivity.EXTRA_EPI_LAT, payload.lat)
            putExtra(AlertActivity.EXTRA_EPI_LON, payload.lon)
            putExtra(AlertActivity.EXTRA_DEPTH_KM, payload.depthKm)
            putExtra(AlertActivity.EXTRA_ORIGIN_TS, payload.originTs)
            putExtra(AlertActivity.EXTRA_USER_LAT, city.lat)
            putExtra(AlertActivity.EXTRA_USER_LON, city.lon)
            putExtra(AlertActivity.EXTRA_USER_CITY_NAME, city.displayName)
        })
    }

    private fun showDisturbanceDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disturbance_title)
            .setMessage(R.string.disturbance_reason_firework)
            .setPositiveButton(R.string.btn_dismiss, null)
            .show()
    }

    private fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ts))

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
```

- [ ] **Step 9: Tab-/Dev-Strings (`values/strings.xml`)**

Vor `</resources>` einfügen:

```xml
    <!-- TP-5 navigation -->
    <string name="tab_home">Start</string>
    <string name="tab_readiness">Readiness</string>
    <string name="tab_settings">Settings</string>
    <string name="dev_section_header">Developer area</string>
```

- [ ] **Step 10: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Danach `./gradlew testDebugUnitTest` → weiterhin grün (Logik unberührt).

- [ ] **Step 11: Manuelle Prüfnotiz**

In `scratchpad/tp5/manual-checks.md`: „Untere Leiste mit 3 Tabs; Testbeben aus Einstellungen→Entwickler löst AlertActivity aus, egal welcher Tab offen ist; Theme-/Farbenblind-Wechsel rekonstruiert die App sauber."

---

### Task 5: `ReadinessFragment` — Score, Profil, Lücken-/Checkliste, Affiliate

**Files:**
- Create: `app/src/main/res/layout/view_prep_item.xml`
- Modify: `app/src/main/res/layout/fragment_readiness.xml` (voller Inhalt)
- Modify: `app/src/main/java/app/tda/ReadinessFragment.kt` (voller Inhalt)
- Modify: `app/src/main/res/values/strings.xml` (Readiness-Strings + prep_item_*/prep_why_*)

**Interfaces:**
- Consumes: `Prefs` (hasPet/hasKids/hasCar/householdSize/ownedKeys/setOwned), `ReadinessCatalog.applicable`, `ReadinessScore.compute`, `Affiliate.amazonSearchUrl`/`localSearchUrl`, `PrepItem`.
- Produces: nichts.

- [ ] **Step 1: Item-Zeilen-Layout**

Create `app/src/main/res/layout/view_prep_item.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:paddingTop="10dp" android:paddingBottom="10dp">

    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">
        <CheckBox android:id="@+id/itemOwned" android:layout_width="wrap_content" android:layout_height="wrap_content" />
        <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
            <TextView android:id="@+id/itemName" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textStyle="bold" />
            <TextView android:id="@+id/itemWhy" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="4dp">
        <Button android:id="@+id/itemAmazon" style="@style/Widget.AppCompat.Button.Borderless" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/btn_search_amazon" />
        <Button android:id="@+id/itemLocal" style="@style/Widget.AppCompat.Button.Borderless" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/btn_search_local" />
    </LinearLayout>
    <View android:layout_width="match_parent" android:layout_height="1dp" android:background="#22808080" android:layout_marginTop="6dp" />
</LinearLayout>
```

- [ ] **Step 2: Readiness-Layout (voll)**

Ersetze `app/src/main/res/layout/fragment_readiness.xml` durch:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:padding="16dp">

        <TextView android:id="@+id/scoreText" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="40sp" android:textStyle="bold" />
        <TextView android:id="@+id/missingText" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="14sp" android:layout_marginBottom="12dp" />

        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginBottom="4dp">
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="@string/profile_pet" />
            <androidx.appcompat.widget.SwitchCompat android:id="@+id/switchPet" android:layout_width="wrap_content" android:layout_height="wrap_content" />
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginBottom="4dp">
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="@string/profile_kids" />
            <androidx.appcompat.widget.SwitchCompat android:id="@+id/switchKids" android:layout_width="wrap_content" android:layout_height="wrap_content" />
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginBottom="12dp">
            <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="@string/profile_car" />
            <androidx.appcompat.widget.SwitchCompat android:id="@+id/switchCar" android:layout_width="wrap_content" android:layout_height="wrap_content" />
        </LinearLayout>

        <TextView android:layout_width="match_parent" android:layout_height="wrap_content"
            android:textSize="12sp" android:textStyle="italic" android:layout_marginBottom="12dp" android:text="@string/affiliate_notice" />

        <LinearLayout android:id="@+id/itemsContainer" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: ReadinessFragment (voll)**

Ersetze `app/src/main/java/app/tda/ReadinessFragment.kt` durch:

```kotlin
package app.tda

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

/**
 * Bereitschaft-Tab (spec TP-5 §D): Score + Profil-Schalter + Lücken-/Checkliste mit
 * gekennzeichneten Affiliate-Such-Links. Liest/schreibt Prefs direkt; rechnet via
 * ReadinessScore/ReadinessCatalog (getestet). Kein Affiliate außerhalb dieses Screens.
 */
class ReadinessFragment : Fragment(R.layout.fragment_readiness) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<SwitchCompat>(R.id.switchPet).apply {
            isChecked = Prefs.hasPet
            setOnCheckedChangeListener { _, c -> Prefs.hasPet = c; render(view) }
        }
        view.findViewById<SwitchCompat>(R.id.switchKids).apply {
            isChecked = Prefs.hasKids
            setOnCheckedChangeListener { _, c -> Prefs.hasKids = c; render(view) }
        }
        view.findViewById<SwitchCompat>(R.id.switchCar).apply {
            isChecked = Prefs.hasCar
            setOnCheckedChangeListener { _, c -> Prefs.hasCar = c; render(view) }
        }
        render(view)
    }

    private fun render(view: View) {
        val applicable = ReadinessCatalog.applicable(Prefs.hasPet, Prefs.hasKids, Prefs.hasCar)
        val owned = Prefs.ownedKeys
        val r = ReadinessScore.compute(applicable, owned)

        view.findViewById<TextView>(R.id.scoreText).text = getString(R.string.readiness_score_format, r.score)
        view.findViewById<TextView>(R.id.missingText).text =
            if (r.missingCount == 0) getString(R.string.readiness_ready)
            else getString(R.string.readiness_missing_format, r.missingCount)

        val container = view.findViewById<LinearLayout>(R.id.itemsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (item in applicable) {
            val row = inflater.inflate(R.layout.view_prep_item, container, false)
            val nameRes = resId("prep_item_${item.key}")
            val whyRes = resId("prep_why_${item.key}")
            row.findViewById<TextView>(R.id.itemName).text = if (nameRes != 0) getString(nameRes) else item.key
            row.findViewById<TextView>(R.id.itemWhy).text = if (whyRes != 0) getString(whyRes) else ""
            row.findViewById<CheckBox>(R.id.itemOwned).apply {
                setOnCheckedChangeListener(null)
                isChecked = owned.contains(item.key)
                setOnCheckedChangeListener { _, c -> Prefs.setOwned(item.key, c); render(view) }
            }
            row.findViewById<Button>(R.id.itemAmazon).setOnClickListener { open(Affiliate.amazonSearchUrl(item.query)) }
            row.findViewById<Button>(R.id.itemLocal).setOnClickListener { open(Affiliate.localSearchUrl(item.query)) }
            container.addView(row)
        }
    }

    private fun resId(name: String): Int =
        resources.getIdentifier(name, "string", requireContext().packageName)

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
```

- [ ] **Step 4: Readiness-Strings + Katalog-Strings (`values/strings.xml`)**

Vor `</resources>` einfügen (Score/Profil/Affiliate + Name+Nutzen je Katalog-Key):

```xml
    <!-- TP-5 readiness -->
    <string name="readiness_score_format">%1$d/100</string>
    <string name="readiness_missing_format">You are missing %1$d things for 72 hours</string>
    <string name="readiness_ready">You are well prepared.</string>
    <string name="profile_pet">Pet</string>
    <string name="profile_kids">Children</string>
    <string name="profile_car">Car</string>
    <string name="affiliate_notice">Ad/affiliate links: if you buy through these, we may earn a commission — the price stays the same for you.</string>
    <string name="btn_search_amazon">Search on Amazon</string>
    <string name="btn_search_local">Search locally</string>

    <string name="prep_item_water">Drinking water supply</string>
    <string name="prep_why_water">3 liters per person per day for at least 72 hours.</string>
    <string name="prep_item_firstaid">First-aid kit</string>
    <string name="prep_why_firstaid">Treat injuries when help is delayed.</string>
    <string name="prep_item_gobag">Go-bag</string>
    <string name="prep_why_gobag">Grab-and-go essentials for a fast evacuation.</string>
    <string name="prep_item_docs">Waterproof document pouch</string>
    <string name="prep_why_docs">Keep ID and papers safe and portable.</string>
    <string name="prep_item_radio">Hand-crank radio</string>
    <string name="prep_why_radio">Get official info when power and network are down.</string>
    <string name="prep_item_powerbank">Power bank</string>
    <string name="prep_why_powerbank">Keep your phone alive for alerts and calls.</string>
    <string name="prep_item_whistle">Whistle</string>
    <string name="prep_why_whistle">Signal rescuers if you are trapped.</string>
    <string name="prep_item_helmet">Helmet</string>
    <string name="prep_why_helmet">Protect your head from falling debris.</string>
    <string name="prep_item_mask">FFP2 dust mask</string>
    <string name="prep_why_mask">Breathe through dust after a collapse.</string>
    <string name="prep_item_blanket">Emergency blanket</string>
    <string name="prep_why_blanket">Stay warm while waiting for help.</string>
    <string name="prep_item_petcarrier">Pet carrier</string>
    <string name="prep_why_petcarrier">Evacuate your animal safely.</string>
    <string name="prep_item_petfood">Pet food supply</string>
    <string name="prep_why_petfood">Several days of food and water for your pet.</string>
    <string name="prep_item_kidsfirstaid">Children\'s first-aid kit</string>
    <string name="prep_why_kidsfirstaid">Child-appropriate medical supplies.</string>
    <string name="prep_item_kidssupplies">Baby/child supplies</string>
    <string name="prep_why_kidssupplies">Diapers, food and comfort items.</string>
    <string name="prep_item_jumpstarter">Jump starter</string>
    <string name="prep_why_jumpstarter">Start the car if the battery is dead.</string>
    <string name="prep_item_warnvest">Warning vest</string>
    <string name="prep_why_warnvest">Stay visible on the road.</string>
    <string name="prep_item_towrope">Tow rope</string>
    <string name="prep_why_towrope">Get the car moving after a breakdown.</string>
```

- [ ] **Step 5: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Danach `./gradlew testDebugUnitTest` → grün.

- [ ] **Step 6: Manuelle Prüfnotiz**

In `scratchpad/tp5/manual-checks.md`: „Bereitschaft-Tab zeigt Score + Liste; Profil-Schalter blenden Gruppen ein/aus und ändern Score; ‚hab ich' hebt Score; ‚Bei Amazon suchen' öffnet amazon.com.tr-Suche mit Tag."

---

### Task 6: Post-Event-Brücke (BeaconActivity → Bereitschaft-Tab)

**Files:**
- Modify: `app/src/main/res/layout/activity_beacon.xml` (Knopf)
- Modify: `app/src/main/java/app/tda/BeaconActivity.kt` (`onSafe` zeigt Knopf; Klick öffnet Readiness)
- Modify: `app/src/main/res/values/strings.xml` (Knopf-String)

**Interfaces:**
- Consumes: `MainActivity.EXTRA_OPEN_TAB` (aus Task 4).
- Produces: nichts.

- [ ] **Step 1: Knopf ins Beacon-Layout**

In `app/src/main/res/layout/activity_beacon.xml` nach dem `@+id/btnShareSafe`-Button (gleiche vertikale LinearLayout) einfügen:

```xml
        <Button android:id="@+id/btnPrepare"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="12dp" android:visibility="gone"
            android:text="@string/btn_prepare_aftershock" />
```

- [ ] **Step 2: BeaconActivity — Knopf verdrahten**

In `app/src/main/java/app/tda/BeaconActivity.kt`, in `onCreate` bei den anderen `setOnClickListener`-Zeilen ergänzen:

```kotlin
        findViewById<Button>(R.id.btnPrepare).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, "readiness")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
```

In der Methode `onSafe()` den `btnPrepare` sichtbar schalten (nach dem Sichtbarmachen von `btnShareSafe`):

```kotlin
        findViewById<Button>(R.id.btnPrepare).visibility = View.VISIBLE
```

- [ ] **Step 3: String (`values/strings.xml`)**

Vor `</resources>` einfügen:

```xml
    <string name="btn_prepare_aftershock">Prepare for the aftershock days</string>
```

- [ ] **Step 4: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manuelle Prüfnotiz**

In `scratchpad/tp5/manual-checks.md`: „Nach ‚Ich bin in Sicherheit' erscheint der Vorbereiten-Knopf; er öffnet MainActivity direkt im Bereitschaft-Tab; nie im aktiven Beacon/Alarm sichtbar."

---

### Task 7: i18n — alle neuen Strings in TR/KU/AR

Übersetzt alle in Tasks 4–6 in `values/strings.xml` (EN) neu angelegten Schlüssel nach TR/KU/AR. Fehlt ein Schlüssel nur in einer Übersetzung, fällt Android auf EN zurück — wir liefern trotzdem alle drei.

**Files:**
- Modify: `app/src/main/res/values-tr/strings.xml`, `values-ku/strings.xml`, `values-ar/strings.xml`

**Vollständige Schlüsselliste (muss in allen drei Dateien vorkommen):**
`tab_home`, `tab_readiness`, `tab_settings`, `dev_section_header`, `readiness_score_format`, `readiness_missing_format`, `readiness_ready`, `profile_pet`, `profile_kids`, `profile_car`, `affiliate_notice`, `btn_search_amazon`, `btn_search_local`, `btn_prepare_aftershock`, und je `prep_item_<key>` + `prep_why_<key>` für die 17 Keys (`water, firstaid, gobag, docs, radio, powerbank, whistle, helmet, mask, blanket, petcarrier, petfood, kidsfirstaid, kidssupplies, jumpstarter, warnvest, towrope`).

- [ ] **Step 1: Türkisch**

In `app/src/main/res/values-tr/strings.xml` vor `</resources>` einfügen:

```xml
    <!-- TP-5 -->
    <string name="tab_home">Başlangıç</string>
    <string name="tab_readiness">Hazırlık</string>
    <string name="tab_settings">Ayarlar</string>
    <string name="dev_section_header">Geliştirici alanı</string>
    <string name="readiness_score_format">%1$d/100</string>
    <string name="readiness_missing_format">72 saat için %1$d eşya eksik</string>
    <string name="readiness_ready">İyi hazırlanmışsın.</string>
    <string name="profile_pet">Evcil hayvan</string>
    <string name="profile_kids">Çocuk</string>
    <string name="profile_car">Araba</string>
    <string name="affiliate_notice">Reklam/ortaklık bağlantıları: buradan alışveriş yaparsan komisyon alabiliriz — fiyat senin için değişmez.</string>
    <string name="btn_search_amazon">Amazon\'da ara</string>
    <string name="btn_search_local">Yerel ara</string>
    <string name="btn_prepare_aftershock">Artçı günlerine hazırlan</string>
    <string name="prep_item_water">İçme suyu stoğu</string>
    <string name="prep_why_water">Kişi başı günde 3 litre, en az 72 saat.</string>
    <string name="prep_item_firstaid">İlk yardım seti</string>
    <string name="prep_why_firstaid">Yardım gecikince yaralanmalara müdahale.</string>
    <string name="prep_item_gobag">Acil çantası</string>
    <string name="prep_why_gobag">Hızlı tahliye için hazır temel eşyalar.</string>
    <string name="prep_item_docs">Su geçirmez belge çantası</string>
    <string name="prep_why_docs">Kimlik ve evrakları güvenli ve taşınabilir tut.</string>
    <string name="prep_item_radio">Kollu radyo</string>
    <string name="prep_why_radio">Elektrik ve şebeke yokken resmi bilgi al.</string>
    <string name="prep_item_powerbank">Taşınabilir şarj</string>
    <string name="prep_why_powerbank">Telefonu uyarılar ve aramalar için açık tut.</string>
    <string name="prep_item_whistle">Düdük</string>
    <string name="prep_why_whistle">Enkaz altında kalırsan kurtarıcılara işaret ver.</string>
    <string name="prep_item_helmet">Kask</string>
    <string name="prep_why_helmet">Düşen molozlardan başını koru.</string>
    <string name="prep_item_mask">FFP2 toz maskesi</string>
    <string name="prep_why_mask">Çökme sonrası tozda nefes al.</string>
    <string name="prep_item_blanket">Acil battaniye</string>
    <string name="prep_why_blanket">Yardım beklerken sıcak kal.</string>
    <string name="prep_item_petcarrier">Evcil hayvan taşıma kutusu</string>
    <string name="prep_why_petcarrier">Hayvanını güvenle tahliye et.</string>
    <string name="prep_item_petfood">Evcil hayvan mama stoğu</string>
    <string name="prep_why_petfood">Hayvanın için birkaç günlük mama ve su.</string>
    <string name="prep_item_kidsfirstaid">Çocuk ilk yardım seti</string>
    <string name="prep_why_kidsfirstaid">Çocuğa uygun tıbbi malzeme.</string>
    <string name="prep_item_kidssupplies">Bebek/çocuk malzemeleri</string>
    <string name="prep_why_kidssupplies">Bez, mama ve rahatlatıcı eşyalar.</string>
    <string name="prep_item_jumpstarter">Akü takviye cihazı</string>
    <string name="prep_why_jumpstarter">Akü bitince arabayı çalıştır.</string>
    <string name="prep_item_warnvest">Uyarı yeleği</string>
    <string name="prep_why_warnvest">Yolda görünür ol.</string>
    <string name="prep_item_towrope">Çekme halatı</string>
    <string name="prep_why_towrope">Arıza sonrası aracı hareket ettir.</string>
```

- [ ] **Step 2: Kurmancî (ku)**

In `app/src/main/res/values-ku/strings.xml` vor `</resources>` denselben Block auf Kurmancî einfügen; wo eine sichere Übersetzung fehlt, den englischen Wert übernehmen (Schlüssel muss vorhanden sein). Mindestens:

```xml
    <!-- TP-5 -->
    <string name="tab_home">Destpêk</string>
    <string name="tab_readiness">Amadekarî</string>
    <string name="tab_settings">Mîheng</string>
    <string name="dev_section_header">Qada pêşdebir</string>
    <string name="readiness_score_format">%1$d/100</string>
    <string name="readiness_missing_format">Ji bo 72 saetan %1$d tişt kêm in</string>
    <string name="readiness_ready">Tu baş amade yî.</string>
    <string name="profile_pet">Heywanê malê</string>
    <string name="profile_kids">Zarok</string>
    <string name="profile_car">Erebe</string>
    <string name="affiliate_notice">Girêdanên reklamê/hevkariyê: eger tu ji van bikirî, dibe ku em komîsyonê bistînin — biha ji bo te naguhere.</string>
    <string name="btn_search_amazon">Li Amazon bigere</string>
    <string name="btn_search_local">Herêmî bigere</string>
    <string name="btn_prepare_aftershock">Ji bo rojên paşerdhejan amade be</string>
    <string name="prep_item_water">Embara ava vexwarinê</string>
    <string name="prep_why_water">Rojê 3 lître ji bo her kesî, herî kêm 72 saet.</string>
    <string name="prep_item_firstaid">Kîta alîkariya pêşîn</string>
    <string name="prep_why_firstaid">Dema alîkarî dereng be birînan derman bike.</string>
    <string name="prep_item_gobag">Çenteyê acîl</string>
    <string name="prep_why_gobag">Tiştên bingehîn ji bo valakirina bilez.</string>
    <string name="prep_item_docs">Çenteyê belgeyan ê av-neguhêz</string>
    <string name="prep_why_docs">Nasname û kaxezan ewle û bar bike.</string>
    <string name="prep_item_radio">Radyoya destan</string>
    <string name="prep_why_radio">Dema kar û tor tune ne agahiya fermî bistîne.</string>
    <string name="prep_item_powerbank">Powerbank</string>
    <string name="prep_why_powerbank">Telefonê ji bo hişyarî û bangan vekirî bihêle.</string>
    <string name="prep_item_whistle">Fîk</string>
    <string name="prep_why_whistle">Eger di bin xîzê de bimînî, işaretê bide.</string>
    <string name="prep_item_helmet">Kum</string>
    <string name="prep_why_helmet">Serê xwe ji ketina keviran biparêze.</string>
    <string name="prep_item_mask">Maskeya toza FFP2</string>
    <string name="prep_why_mask">Piştî hilweşînê di tozê de bêhna xwe bikişîne.</string>
    <string name="prep_item_blanket">Betaniya acîl</string>
    <string name="prep_why_blanket">Dema li benda alîkariyê germ bimîne.</string>
    <string name="prep_item_petcarrier">Qutiya veguhastina heywan</string>
    <string name="prep_why_petcarrier">Heywanê xwe ewle valake.</string>
    <string name="prep_item_petfood">Embara xwarina heywan</string>
    <string name="prep_why_petfood">Çend rojan xwarin û av ji bo heywanê te.</string>
    <string name="prep_item_kidsfirstaid">Kîta alîkariya pêşîn a zarokan</string>
    <string name="prep_why_kidsfirstaid">Malzemeya tibbî ya li gorî zarokan.</string>
    <string name="prep_item_kidssupplies">Malzemeyên pitik/zarokan</string>
    <string name="prep_why_kidssupplies">Bêz, xwarin û tiştên aramkirinê.</string>
    <string name="prep_item_jumpstarter">Amûra destpêka bataryayê</string>
    <string name="prep_why_jumpstarter">Dema batarya vala be erebê bide destpêkirin.</string>
    <string name="prep_item_warnvest">Îzafeya hişyariyê</string>
    <string name="prep_why_warnvest">Li ser rê xuya bibe.</string>
    <string name="prep_item_towrope">Kabloya kişandinê</string>
    <string name="prep_why_towrope">Piştî xerabûnê erebê bilivîne.</string>
```

- [ ] **Step 3: Arabisch (ar)**

In `app/src/main/res/values-ar/strings.xml` vor `</resources>` denselben Block auf Arabisch einfügen:

```xml
    <!-- TP-5 -->
    <string name="tab_home">البداية</string>
    <string name="tab_readiness">الجاهزية</string>
    <string name="tab_settings">الإعدادات</string>
    <string name="dev_section_header">منطقة المطوّر</string>
    <string name="readiness_score_format">%1$d/100</string>
    <string name="readiness_missing_format">ينقصك %1$d أشياء لمدة 72 ساعة</string>
    <string name="readiness_ready">أنت مستعد جيدًا.</string>
    <string name="profile_pet">حيوان أليف</string>
    <string name="profile_kids">أطفال</string>
    <string name="profile_car">سيارة</string>
    <string name="affiliate_notice">روابط إعلانية/تسويق بالعمولة: إذا اشتريت عبرها فقد نحصل على عمولة — السعر لا يتغير بالنسبة لك.</string>
    <string name="btn_search_amazon">ابحث في أمازون</string>
    <string name="btn_search_local">ابحث محليًا</string>
    <string name="btn_prepare_aftershock">استعد لأيام الهزات الارتدادية</string>
    <string name="prep_item_water">مخزون مياه الشرب</string>
    <string name="prep_why_water">3 لترات للشخص يوميًا لمدة 72 ساعة على الأقل.</string>
    <string name="prep_item_firstaid">حقيبة إسعافات أولية</string>
    <string name="prep_why_firstaid">عالج الإصابات عند تأخر المساعدة.</string>
    <string name="prep_item_gobag">حقيبة طوارئ</string>
    <string name="prep_why_gobag">أساسيات جاهزة لإخلاء سريع.</string>
    <string name="prep_item_docs">حافظة مستندات مقاومة للماء</string>
    <string name="prep_why_docs">احفظ الهوية والأوراق بأمان وقابلة للحمل.</string>
    <string name="prep_item_radio">راديو يدوي</string>
    <string name="prep_why_radio">احصل على معلومات رسمية عند انقطاع الكهرباء والشبكة.</string>
    <string name="prep_item_powerbank">بطارية محمولة</string>
    <string name="prep_why_powerbank">أبقِ هاتفك يعمل للتنبيهات والمكالمات.</string>
    <string name="prep_item_whistle">صافرة</string>
    <string name="prep_why_whistle">أرسل إشارة للمنقذين إذا حوصرت.</string>
    <string name="prep_item_helmet">خوذة</string>
    <string name="prep_why_helmet">احمِ رأسك من الحطام المتساقط.</string>
    <string name="prep_item_mask">كمامة غبار FFP2</string>
    <string name="prep_why_mask">تنفّس عبر الغبار بعد الانهيار.</string>
    <string name="prep_item_blanket">بطانية طوارئ</string>
    <string name="prep_why_blanket">ابقَ دافئًا أثناء انتظار المساعدة.</string>
    <string name="prep_item_petcarrier">قفص نقل الحيوان</string>
    <string name="prep_why_petcarrier">أخلِ حيوانك بأمان.</string>
    <string name="prep_item_petfood">مخزون طعام الحيوان</string>
    <string name="prep_why_petfood">طعام وماء لعدة أيام لحيوانك.</string>
    <string name="prep_item_kidsfirstaid">حقيبة إسعاف للأطفال</string>
    <string name="prep_why_kidsfirstaid">مستلزمات طبية مناسبة للأطفال.</string>
    <string name="prep_item_kidssupplies">مستلزمات الرضّع/الأطفال</string>
    <string name="prep_why_kidssupplies">حفاضات وطعام وأغراض مريحة.</string>
    <string name="prep_item_jumpstarter">جهاز بدء تشغيل البطارية</string>
    <string name="prep_why_jumpstarter">شغّل السيارة عند نفاد البطارية.</string>
    <string name="prep_item_warnvest">سترة تحذيرية</string>
    <string name="prep_why_warnvest">كن مرئيًا على الطريق.</string>
    <string name="prep_item_towrope">حبل قطر</string>
    <string name="prep_why_towrope">حرّك السيارة بعد العطل.</string>
```

- [ ] **Step 4: Gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: beide BUILD SUCCESSFUL — reine-Logik-Tests (Tasks 1–2 + TP-3) grün UND App kompiliert mit allen Locales.

---

## Self-Review (gegen die Spec)

**1. Spec-Abdeckung:**
- Nav-Schale (§B) → Task 4 ✅
- Minimal-Profil (§C) → Task 3 (Prefs) + Task 5 (Schalter-UI) ✅
- Readiness-Score + Lückenliste (§C/D) → Task 2 + Task 5 ✅
- Vorsorge-Katalog + Affiliate (§D) → Task 1 + Task 2 + Task 5 ✅
- Post-Event-Brücke (§E) → Task 6 (+ EXTRA_OPEN_TAB aus Task 4) ✅
- Reine Logik + Tests (§F) → Tasks 1, 2 ✅
- i18n (§G) → Task 7 ✅
- Alarm-Fluss in Host bleibt → Task 4 (`observeAlarmPlumbing` in MainActivity) ✅
- Nicht drin: Erinnerungen, Wirkungs-Tab → nicht enthalten ✅

**2. Platzhalter:** keine „TBD/handle edge cases"; jeder Code-Schritt zeigt vollständigen Code. (`ReadinessFragment`-Stub in Task 4 wird in Task 5 durch die volle Fassung ersetzt — bewusst, damit der Menü-Eintrag schon ein Ziel hat.)

**3. Typkonsistenz:** `Affiliate.amazonSearchUrl/localSearchUrl` (T1) → T5; `PrepItem`/`Group`/`ReadinessCatalog.applicable` (T2) → T5; `ReadinessScore.compute`/`Readiness` (T2) → T5; `Prefs.hasPet/hasKids/hasCar/ownedKeys/setOwned` (T3) → T5; `MainActivity.EXTRA_OPEN_TAB` (T4) → T6; Fragmente `HomeFragment`/`SettingsFragment`/`ReadinessFragment` (T4) → im BottomNav-Listener (T4) genutzt; String-Keys `prep_item_*`/`prep_why_*` (T5) → in T7 übersetzt, in `ReadinessFragment.resId(...)` dynamisch aufgelöst. Konsistent.

**4. Risiko-Hinweis:** Task 4 ist der große Umbau; das Gate ist `assembleDebug` + die manuelle Prüfnotiz (der Alarm-Fluss ist nur auf Gerät voll verifizierbar — die reine-Logik-Tests bleiben grün, aber FSI/DND/Service sind Laufzeit).
