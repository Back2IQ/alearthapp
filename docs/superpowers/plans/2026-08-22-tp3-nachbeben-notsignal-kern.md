# TP-3 Nachbeben-/Notsignal-Kern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nach einem starken Beben führt der native Android-Client eine Überlebens-Kette aus — kritischer Alarm (Stumm/DND-fähig), „Bist du sicher?"-Rückfrage mit Totmann-Automatik, lokales Notsignal (Pfeife + Bildschirm-/Taschenlampen-Strobo) und „Ich bin in Sicherheit"-Teilen.

**Architecture:** Die Politik liegt in reinen, JVM-testbaren Klassen (`SafetyState`, `CriticalAlarmPolicy`, `StrobePattern`, `SignalGenerator`) — gebaut per TDD wie das bestehende `Eew`. Darüber ein dünner Android-Rand: ein Vordergrund-Dienst (`AlarmService`) trägt Alarmton, Schonfrist-/Totmann-Timer und Notsignal; `BeaconActivity` zeigt „Bist du sicher?" + Beacon; Alarme kommen künftig per Full-Screen-Intent-Notification. Persistenz in `Prefs`, keine neue Netz-/Bezahl-Abhängigkeit.

**Tech Stack:** Kotlin, Gradle 8.11 / **JDK 17**, AndroidX (core-ktx, appcompat, material), kotlinx-coroutines, JUnit4 (neu für Tests). compileSdk 36, minSdk 26, targetSdk 36.

## Global Constraints

- **Nullkosten:** kein bezahlter Dienst, **keine gebundelte Audiodatei** (Notsignal-Ton wird im Code synthetisiert), keine neue Netz-Abhängigkeit.
- **Ehrlichkeit sichtbar:** TEST-Alarm unverwechselbar von Ernst (Cry-Wolf); DND-Opt-in ehrlich beschriftet („ohne diese Freigabe kann volles ‚Nicht stören' den Alarm schlucken"); Rechte-/Zustell-Grenzen sichtbar.
- **Parameter-Defaults (persistiert in `Prefs`, alle änderbar):** `beaconEnabled=true`, `beaconMmiThreshold=7` (Bereich 5–9), `graceMinutes=5` (0–30), `deadmanCountdownSec=60` (15–300), `signalWhistle=true`, `signalScreenStrobe=true`, `signalTorchStrobe=true`, `dndBypassOptIn=false`.
- **Schwelle = lokale Intensität** `Eew.mmi(mag, distKm)`, nicht Herd-Magnitude.
- **Warnungen nie unterdrücken:** neue P0/P2 (auch starke Nachbeben) erreichen den Nutzer in jedem Modus.
- **i18n:** alle neuen Strings in **TR/EN/KU/AR** (`values/`, `values-tr/`, `values-ku/`, `values-ar/`).
- **Nicht anfassen:** `Signing.kt`, der Verifikationskern von `ServerLink.kt`, die Formeln in `Eew.kt` (nur lesende Nutzung).
- **Nicht in TP-3:** zwei native Onboardings + ausgebaute Einstellungsseite (→ TP-3b); BLE-Mesh-Funkruf + echte FCM-Hintergrundzustellung (→ TP-4). TP-3 persistiert Parameter mit Defaults und exponiert nur minimale Auslöser + einen TEST-Knopf.
- **Verifizierung:** jede Task endet mit `./gradlew testDebugUnitTest` (reine Logik) bzw. `./gradlew assembleDebug` (Kompilat). **Ausführung erst, wenn JDK 17 installiert ist** (aktuell nur Java-8-JRE auf der Maschine).
- **Kein „commit" ohne Nutzer-Freigabe:** dieses Projekt committet nur auf ausdrückliche Bitte. Die „Commit"-Schritte unten bedeuten in der subagent-getriebenen Ausführung: **Gate grün laufen lassen** (`testDebugUnitTest`/`assembleDebug`), nicht `git commit`.

**Arbeitsverzeichnis aller Befehle:** `tda/android/`. Test-Sourceset (neu): `app/src/test/java/app/tda/`.

---

### Task 1: Test-Sourceset + `SafetyState` (reine Zustandslogik)

Führt den JVM-Test-Sourceset und JUnit erstmals im Client ein und baut den Kern-Zustandsautomaten per TDD.

**Files:**
- Modify: `app/build.gradle` (JUnit-Testabhängigkeit)
- Create: `app/src/main/java/app/tda/SafetyState.kt`
- Test: `app/src/test/java/app/tda/SafetyStateTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `enum class SafetyPhase { IDLE, ARMED, ASKING, WATCH, BEACON }`
  - `data class SafetyConfig(val beaconEnabled: Boolean, val mmiThreshold: Int, val graceMillis: Long, val deadmanMillis: Long)`
  - `class SafetyState(config: SafetyConfig)` mit `val phase: SafetyPhase` und `onConfirmedQuake(mmi: Double): SafetyPhase`, `onGraceElapsed(): SafetyPhase`, `onCountdownElapsed(): SafetyPhase`, `onUserSafe(): SafetyPhase`, `onUserHelp(): SafetyPhase`, `reset()`.

- [ ] **Step 1: JUnit-Abhängigkeit hinzufügen**

In `app/build.gradle` den `dependencies{ ... }`-Block um die Testzeile ergänzen (letzte Zeile vor der schließenden Klammer):

```groovy
  testImplementation 'junit:junit:4.13.2'
```

- [ ] **Step 2: Failing test schreiben**

Create `app/src/test/java/app/tda/SafetyStateTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyStateTest {
    private fun cfg(enabled: Boolean = true, threshold: Int = 7) =
        SafetyConfig(beaconEnabled = enabled, mmiThreshold = threshold, graceMillis = 300_000, deadmanMillis = 60_000)

    @Test fun belowThreshold_staysIdle() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.IDLE, s.onConfirmedQuake(6.9))
    }

    @Test fun disabled_staysIdle() {
        val s = SafetyState(cfg(enabled = false))
        assertEquals(SafetyPhase.IDLE, s.onConfirmedQuake(9.0))
    }

    @Test fun qualifyingQuake_arms() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.ARMED, s.onConfirmedQuake(7.0))
    }

    @Test fun graceThenCountdown_leadsToBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5)
        assertEquals(SafetyPhase.ASKING, s.onGraceElapsed())
        assertEquals(SafetyPhase.BEACON, s.onCountdownElapsed())
    }

    @Test fun userSafeDuringAsking_goesToWatch() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed()
        assertEquals(SafetyPhase.WATCH, s.onUserSafe())
    }

    @Test fun userHelpDuringAsking_goesToBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed()
        assertEquals(SafetyPhase.BEACON, s.onUserHelp())
    }

    @Test fun userSafeStopsActiveBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed(); s.onCountdownElapsed()
        assertEquals(SafetyPhase.WATCH, s.onUserSafe())
    }

    @Test fun graceIgnoredWhenNotArmed() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.IDLE, s.onGraceElapsed())
    }

    @Test fun resetReturnsToIdle() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5)
        s.reset()
        assertEquals(SafetyPhase.IDLE, s.phase)
    }
}
```

- [ ] **Step 3: Test rot laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.SafetyStateTest"`
Expected: FAIL — `SafetyState`/`SafetyPhase`/`SafetyConfig` nicht gefunden (Kompilierfehler).

- [ ] **Step 4: `SafetyState` implementieren**

Create `app/src/main/java/app/tda/SafetyState.kt`:

```kotlin
package app.tda

/** Phasen der Post-Beben-Sicherheits-Kette (spec TP-3 §"Ablauf"). Reine Logik, kein Android. */
enum class SafetyPhase { IDLE, ARMED, ASKING, WATCH, BEACON }

data class SafetyConfig(
    val beaconEnabled: Boolean,
    val mmiThreshold: Int,
    val graceMillis: Long,
    val deadmanMillis: Long
)

/**
 * Zustandsautomat der „Bist du sicher?"-Kette. Der Service/die Activity rufen nur die
 * Eingänge und rendern [phase]; die ganze Politik (Schwelle, Schonfrist, Totmann,
 * Wächter/Beacon-Gabelung) steckt hier und ist JVM-testbar. Unterdrückt NIE Alarme —
 * Warnungen laufen an [SafetyState] vorbei.
 */
class SafetyState(private val config: SafetyConfig) {

    var phase: SafetyPhase = SafetyPhase.IDLE
        private set

    /** Bestätigtes Beben mit lokaler Intensität [mmi]; scharf nur bei aktivierter Kette und Schwelle. */
    fun onConfirmedQuake(mmi: Double): SafetyPhase {
        if (config.beaconEnabled && mmi >= config.mmiThreshold && phase == SafetyPhase.IDLE) {
            phase = SafetyPhase.ARMED
        }
        return phase
    }

    /** Schonfrist abgelaufen → Rückfrage anzeigen. */
    fun onGraceElapsed(): SafetyPhase {
        if (phase == SafetyPhase.ARMED) phase = SafetyPhase.ASKING
        return phase
    }

    /** Totmann-Countdown abgelaufen ohne Antwort → Notsignal. */
    fun onCountdownElapsed(): SafetyPhase {
        if (phase == SafetyPhase.ASKING) phase = SafetyPhase.BEACON
        return phase
    }

    /** Nutzer meldet „Mir geht's gut" — aus jeder aktiven Phase in den Wächter-Modus (stoppt Beacon). */
    fun onUserSafe(): SafetyPhase {
        if (phase == SafetyPhase.ARMED || phase == SafetyPhase.ASKING || phase == SafetyPhase.BEACON) {
            phase = SafetyPhase.WATCH
        }
        return phase
    }

    /** Nutzer meldet „Ich brauche Hilfe" — sofort ins Notsignal. */
    fun onUserHelp(): SafetyPhase {
        if (phase == SafetyPhase.ARMED || phase == SafetyPhase.ASKING) phase = SafetyPhase.BEACON
        return phase
    }

    fun reset() { phase = SafetyPhase.IDLE }
}
```

- [ ] **Step 5: Test grün laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.SafetyStateTest"`
Expected: PASS (9 Tests).

- [ ] **Step 6: Gate (statt commit)**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — alle Unit-Tests grün.

---

### Task 2: `CriticalAlarmPolicy` (reine Alarm-Politik)

Entscheidet aus Tier/Test/Ton-Pref/DND-Zustand, wie der Alarm klingt und ob er DND durchbricht — der Kern des „Alarm trotz Stumm/DND" und des Cry-Wolf-Schutzes.

**Files:**
- Create: `app/src/main/java/app/tda/CriticalAlarmPolicy.kt`
- Test: `app/src/test/java/app/tda/CriticalAlarmPolicyTest.kt`

**Interfaces:**
- Consumes: `Eew.Tier` (bestehend: `P0`, `P1`, `P2`).
- Produces:
  - `enum class AlarmChannelKind { CRITICAL, TEST }`
  - `data class AlarmPlan(val channel: AlarmChannelKind, val playAlarmSound: Boolean, val vibrate: Boolean, val bypassDnd: Boolean, val fullScreen: Boolean)`
  - `object CriticalAlarmPolicy { fun plan(tier: Eew.Tier, isTest: Boolean, soundEnabled: Boolean, dndOptIn: Boolean, dndAccessGranted: Boolean): AlarmPlan }`

- [ ] **Step 1: Failing test schreiben**

Create `app/src/test/java/app/tda/CriticalAlarmPolicyTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalAlarmPolicyTest {

    @Test fun test_isUnmistakablyDifferent_neverBypassesOrLoops() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = true, soundEnabled = true, dndOptIn = true, dndAccessGranted = true)
        assertEquals(AlarmChannelKind.TEST, p.channel)
        assertFalse(p.playAlarmSound)
        assertFalse(p.bypassDnd)
    }

    @Test fun p2_confirmed_playsAlarmWhenSoundOn() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = true, dndOptIn = false, dndAccessGranted = false)
        assertEquals(AlarmChannelKind.CRITICAL, p.channel)
        assertTrue(p.playAlarmSound)
        assertTrue(p.vibrate)
        assertTrue(p.fullScreen)
    }

    @Test fun p2_soundOff_vibratesOnly() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = false, dndOptIn = true, dndAccessGranted = true)
        assertFalse(p.playAlarmSound)
        assertTrue(p.vibrate)
    }

    @Test fun p0_isMutedVibrationOnly() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P0, isTest = false, soundEnabled = true, dndOptIn = true, dndAccessGranted = true)
        assertFalse(p.playAlarmSound) // P0 noch unbestätigt → gedämpft
        assertTrue(p.vibrate)
        assertFalse(p.bypassDnd)
    }

    @Test fun bypassDnd_onlyWithOptInAndAccess() {
        val base = { optIn: Boolean, access: Boolean ->
            CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = true, dndOptIn = optIn, dndAccessGranted = access).bypassDnd
        }
        assertTrue(base(true, true))
        assertFalse(base(true, false))
        assertFalse(base(false, true))
        assertFalse(base(false, false))
    }
}
```

- [ ] **Step 2: Test rot laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.CriticalAlarmPolicyTest"`
Expected: FAIL — `CriticalAlarmPolicy` nicht gefunden.

- [ ] **Step 3: `CriticalAlarmPolicy` implementieren**

Create `app/src/main/java/app/tda/CriticalAlarmPolicy.kt`:

```kotlin
package app.tda

/** Welcher Notification-Kanal/Ton für einen Alarm gilt (spec TP-3 §"Teil 1"). Reine Logik. */
enum class AlarmChannelKind { CRITICAL, TEST }

data class AlarmPlan(
    val channel: AlarmChannelKind,
    val playAlarmSound: Boolean,
    val vibrate: Boolean,
    val bypassDnd: Boolean,
    val fullScreen: Boolean
)

/**
 * Entscheidet, wie ein Alarm zugestellt wird. P2 (bestätigt, lebensrettend) darf laut sein
 * und — mit Opt-in + gewährtem Policy-Zugriff — volles DND durchbrechen; P0 bleibt gedämpft
 * (noch unbestätigt); TEST ist unverwechselbar (kein Alarmschleifen-Ton, nie DND-Durchbruch).
 */
object CriticalAlarmPolicy {
    fun plan(
        tier: Eew.Tier,
        isTest: Boolean,
        soundEnabled: Boolean,
        dndOptIn: Boolean,
        dndAccessGranted: Boolean
    ): AlarmPlan {
        if (isTest) {
            return AlarmPlan(AlarmChannelKind.TEST, playAlarmSound = false, vibrate = true, bypassDnd = false, fullScreen = true)
        }
        val confirmed = tier == Eew.Tier.P2
        val sound = confirmed && soundEnabled
        return AlarmPlan(
            channel = AlarmChannelKind.CRITICAL,
            playAlarmSound = sound,
            vibrate = true,
            bypassDnd = confirmed && dndOptIn && dndAccessGranted,
            fullScreen = true
        )
    }
}
```

- [ ] **Step 4: Test grün laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.CriticalAlarmPolicyTest"`
Expected: PASS (5 Tests).

- [ ] **Step 5: Gate**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

---

### Task 3: `StrobePattern` (reine Puls-Timings)

**Files:**
- Create: `app/src/main/java/app/tda/StrobePattern.kt`
- Test: `app/src/test/java/app/tda/StrobePatternTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `data class Pulse(val onMillis: Long, val offMillis: Long)`
  - `object StrobePattern { val screen: Pulse; val torch: Pulse; fun onFraction(p: Pulse): Double }`

- [ ] **Step 1: Failing test schreiben**

Create `app/src/test/java/app/tda/StrobePatternTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertTrue
import org.junit.Test

class StrobePatternTest {
    @Test fun torchIsSparserThanScreen() {
        assertTrue(StrobePattern.onFraction(StrobePattern.torch) < StrobePattern.onFraction(StrobePattern.screen))
    }

    @Test fun fractionsAreInUnitRange() {
        for (p in listOf(StrobePattern.screen, StrobePattern.torch)) {
            val f = StrobePattern.onFraction(p)
            assertTrue(f > 0.0 && f < 1.0)
        }
    }

    @Test fun pulsesArePositive() {
        for (p in listOf(StrobePattern.screen, StrobePattern.torch)) {
            assertTrue(p.onMillis > 0 && p.offMillis > 0)
        }
    }
}
```

- [ ] **Step 2: Test rot laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.StrobePatternTest"`
Expected: FAIL — `StrobePattern` nicht gefunden.

- [ ] **Step 3: `StrobePattern` implementieren**

Create `app/src/main/java/app/tda/StrobePattern.kt`:

```kotlin
package app.tda

/** Ein An/Aus-Puls in Millisekunden. */
data class Pulse(val onMillis: Long, val offMillis: Long)

/**
 * Puls-Timings für das Notsignal (spec TP-3 §"Teil 3"). Bildschirm blinkt dicht (gut sichtbar),
 * die Taschenlampe deutlich sparsamer (Akku/Hitze). Reine Werte → testbar ohne Hardware.
 */
object StrobePattern {
    val screen = Pulse(onMillis = 250, offMillis = 250)
    val torch = Pulse(onMillis = 200, offMillis = 1800)

    /** Anteil der Zeit, in dem der Kanal „an" ist — für Akku-Abschätzung und Tests. */
    fun onFraction(p: Pulse): Double = p.onMillis.toDouble() / (p.onMillis + p.offMillis)
}
```

- [ ] **Step 4: Test grün laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.StrobePatternTest"`
Expected: PASS (3 Tests).

- [ ] **Step 5: Gate**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

---

### Task 4: `SignalGenerator` (synthetisierter Pfeif-Ton, reine Puffer-Logik)

**Files:**
- Create: `app/src/main/java/app/tda/SignalGenerator.kt`
- Test: `app/src/test/java/app/tda/SignalGeneratorTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `object SignalGenerator { const val SAMPLE_RATE = 44100; fun sweepPcm(fStartHz: Double, fEndHz: Double, durationMs: Int, amplitude: Double = 0.9): ShortArray }`

- [ ] **Step 1: Failing test schreiben**

Create `app/src/test/java/app/tda/SignalGeneratorTest.kt`:

```kotlin
package app.tda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalGeneratorTest {
    @Test fun bufferLengthMatchesDuration() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 1000)
        assertEquals(SignalGenerator.SAMPLE_RATE, buf.size)
    }

    @Test fun bufferIsNotSilent() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 200)
        assertTrue(buf.any { it.toInt() != 0 })
    }

    @Test fun amplitudeStaysWithinRange() {
        val buf = SignalGenerator.sweepPcm(600.0, 2400.0, 200, amplitude = 1.0)
        val max = buf.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(max <= Short.MAX_VALUE.toInt())
        assertTrue(max > Short.MAX_VALUE.toInt() / 2) // wirklich laut
    }

    @Test fun shortDurationYieldsShortBuffer() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 100)
        assertEquals(SignalGenerator.SAMPLE_RATE / 10, buf.size)
    }
}
```

- [ ] **Step 2: Test rot laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.SignalGeneratorTest"`
Expected: FAIL — `SignalGenerator` nicht gefunden.

- [ ] **Step 3: `SignalGenerator` implementieren**

Create `app/src/main/java/app/tda/SignalGenerator.kt`:

```kotlin
package app.tda

import kotlin.math.PI
import kotlin.math.sin

/**
 * Erzeugt den Pfeif-/Sirenenton als 16-bit-Mono-PCM im Code — keine Audiodatei (Nullkosten,
 * keine APK-Größe). Die Puffer-Erzeugung ist reine Mathematik und JVM-testbar; die Wiedergabe
 * über AudioTrack ist der dünne Android-Rand in [AlarmService]/[BeaconActivity].
 */
object SignalGenerator {
    const val SAMPLE_RATE = 44100

    /** Sinus-Frequenz-Sweep von [fStartHz] nach [fEndHz] über [durationMs] ms. */
    fun sweepPcm(fStartHz: Double, fEndHz: Double, durationMs: Int, amplitude: Double = 0.9): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = if (n > 1) i.toDouble() / (n - 1) else 0.0
            val f = fStartHz + (fEndHz - fStartHz) * t
            phase += 2.0 * PI * f / SAMPLE_RATE
            out[i] = (sin(phase) * amplitude * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
```

- [ ] **Step 4: Test grün laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "app.tda.SignalGeneratorTest"`
Expected: PASS (4 Tests).

- [ ] **Step 5: Gate**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — alle vier reine-Logik-Module (Tasks 1–4) grün.

---

### Task 5: `Prefs` — die acht TP-3-Parameter

**Files:**
- Modify: `app/src/main/java/app/tda/Prefs.kt`

**Interfaces:**
- Consumes: bestehendes `Prefs`-Muster (`prefs.getX`/`edit().putX().apply()`).
- Produces (neue `Prefs`-Properties): `beaconEnabled: Boolean`, `beaconMmiThreshold: Int`, `graceMinutes: Int`, `deadmanCountdownSec: Int`, `signalWhistle: Boolean`, `signalScreenStrobe: Boolean`, `signalTorchStrobe: Boolean`, `dndBypassOptIn: Boolean`; Helfer `safetyConfig(): SafetyConfig`.

- [ ] **Step 1: Keys + Properties ergänzen**

In `app/src/main/java/app/tda/Prefs.kt` nach der Zeile `private const val KEY_CITY = "city_id"` einfügen:

```kotlin
    private const val KEY_BEACON_ENABLED = "beacon_enabled"
    private const val KEY_BEACON_MMI = "beacon_mmi_threshold"
    private const val KEY_GRACE_MIN = "grace_minutes"
    private const val KEY_DEADMAN_SEC = "deadman_countdown_sec"
    private const val KEY_SIG_WHISTLE = "signal_whistle"
    private const val KEY_SIG_SCREEN = "signal_screen_strobe"
    private const val KEY_SIG_TORCH = "signal_torch_strobe"
    private const val KEY_DND_OPTIN = "dnd_bypass_optin"
```

- [ ] **Step 2: Properties + Helfer ergänzen**

In `Prefs` vor die schließende `}` des Objekts einfügen (nutzt `coerceIn` für die Bereiche aus den Global Constraints):

```kotlin
    var beaconEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEACON_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_BEACON_ENABLED, v).apply() }

    var beaconMmiThreshold: Int
        get() = prefs.getInt(KEY_BEACON_MMI, 7).coerceIn(5, 9)
        set(v) { prefs.edit().putInt(KEY_BEACON_MMI, v.coerceIn(5, 9)).apply() }

    var graceMinutes: Int
        get() = prefs.getInt(KEY_GRACE_MIN, 5).coerceIn(0, 30)
        set(v) { prefs.edit().putInt(KEY_GRACE_MIN, v.coerceIn(0, 30)).apply() }

    var deadmanCountdownSec: Int
        get() = prefs.getInt(KEY_DEADMAN_SEC, 60).coerceIn(15, 300)
        set(v) { prefs.edit().putInt(KEY_DEADMAN_SEC, v.coerceIn(15, 300)).apply() }

    var signalWhistle: Boolean
        get() = prefs.getBoolean(KEY_SIG_WHISTLE, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_WHISTLE, v).apply() }

    var signalScreenStrobe: Boolean
        get() = prefs.getBoolean(KEY_SIG_SCREEN, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_SCREEN, v).apply() }

    var signalTorchStrobe: Boolean
        get() = prefs.getBoolean(KEY_SIG_TORCH, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_TORCH, v).apply() }

    var dndBypassOptIn: Boolean
        get() = prefs.getBoolean(KEY_DND_OPTIN, false)
        set(v) { prefs.edit().putBoolean(KEY_DND_OPTIN, v).apply() }

    /** Baut die reine [SafetyConfig] aus den persistierten Parametern. */
    fun safetyConfig(): SafetyConfig = SafetyConfig(
        beaconEnabled = beaconEnabled,
        mmiThreshold = beaconMmiThreshold,
        graceMillis = graceMinutes * 60_000L,
        deadmanMillis = deadmanCountdownSec * 1000L
    )
```

- [ ] **Step 3: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (kompiliert; `Prefs` referenziert `SafetyConfig` aus Task 1).

---

### Task 6: Manifest, Rechte, `NotificationChannels`

Deklariert alle neuen Rechte, den Vordergrund-Dienst und die Notification-Kanäle. Die Kanäle setzen `bypassDnd` nur, wenn der Nutzer opt-in **und** den Policy-Zugriff gewährt hat.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/tda/NotificationChannels.kt`

**Interfaces:**
- Consumes: `CriticalAlarmPolicy` (nicht direkt), `Prefs.dndBypassOptIn`.
- Produces:
  - `object NotificationChannels { const val CRITICAL = "alarm_critical"; const val TEST = "alarm_test"; const val BEACON_READY = "beacon_ready"; const val SERVICE = "svc_watch"; fun ensure(context: Context) }`

- [ ] **Step 1: Rechte + Service ins Manifest**

In `app/src/main/AndroidManifest.xml` nach den bestehenden `<uses-permission>`-Zeilen einfügen:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Innerhalb von `<application>` nach der `ReportActivity`-Zeile einfügen:

```xml
        <activity
            android:name=".BeaconActivity"
            android:exported="false"
            android:launchMode="singleTop"
            android:theme="@style/Theme.Tda.Alert" />

        <service
            android:name=".AlarmService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="earthquake aftershock watch and local survival beacon" />
        </service>
```

- [ ] **Step 2: `NotificationChannels` implementieren**

Create `app/src/main/java/app/tda/NotificationChannels.kt`:

```kotlin
package app.tda

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build

/**
 * Legt die Notification-Kanäle an (spec TP-3 §"Teil 1"). Der kritische Kanal durchbricht
 * „Nicht stören" nur, wenn der Nutzer opt-in ist UND der App der Policy-Zugriff gewährt wurde
 * — sonst best-effort (hoher Kanal + Alarm-Audio). TEST ist ein eigener, dezenter Kanal.
 */
object NotificationChannels {
    const val CRITICAL = "alarm_critical"
    const val TEST = "alarm_test"
    const val BEACON_READY = "beacon_ready"
    const val SERVICE = "svc_watch"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val critical = NotificationChannel(CRITICAL, context.getString(R.string.chan_critical), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.chan_critical_desc)
            setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAudio)
            enableVibration(true)
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                nm.isNotificationPolicyAccessGranted
            setBypassDnd(Prefs.dndBypassOptIn && granted)
        }
        val test = NotificationChannel(TEST, context.getString(R.string.chan_test), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.chan_test_desc)
        }
        val ready = NotificationChannel(BEACON_READY, context.getString(R.string.chan_ready), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.chan_ready_desc)
        }
        val service = NotificationChannel(SERVICE, context.getString(R.string.chan_service), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.chan_service_desc)
        }
        nm.createNotificationChannels(listOf(critical, test, ready, service))
    }
}
```

- [ ] **Step 3: Kanal-Strings (nur `values/strings.xml` — Übersetzungen folgen in Task 11)**

In `app/src/main/res/values/strings.xml` vor `</resources>` einfügen:

```xml
    <!-- TP-3 notification channels -->
    <string name="chan_critical">Critical earthquake alerts</string>
    <string name="chan_critical_desc">Life-saving confirmed alerts. Can sound even when muted.</string>
    <string name="chan_test">Test alerts</string>
    <string name="chan_test_desc">Practice alerts — never the real alarm sound.</string>
    <string name="chan_ready">Beacon standby</string>
    <string name="chan_ready_desc">Quiet notice after a strong quake, so you can mark yourself safe.</string>
    <string name="chan_service">Aftershock watch</string>
    <string name="chan_service_desc">Keeps watching for aftershocks in the background.</string>
```

- [ ] **Step 4: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (Manifest merged, `NotificationChannels` kompiliert). Der `<service>` referenziert `.AlarmService`, die erst Task 7 anlegt — daher **hier nur bis inkl. NotificationChannels bauen**; falls der Manifest-Merger die fehlende Klasse bemängelt, `AlarmService`-Stub aus Task 7 Step 1 vorziehen.

---

### Task 7: `AlarmService` (Vordergrund-Dienst: Alarmton + Schonfrist/Totmann + Beacon-Träger)

Der Dienst hält den Alarm am Leben (DND-fähig), fährt die Zeitgeber der `SafetyState`-Kette und startet die `BeaconActivity` per Full-Screen-Intent. Nicht JVM-testbar → Gate ist Kompilat + manuelle Prüfliste; die Politik ist bereits in Task 1/2 getestet.

**Files:**
- Create: `app/src/main/java/app/tda/AlarmService.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SafetyState`, `SafetyConfig`, `Prefs.safetyConfig()`, `CriticalAlarmPolicy`, `SignalGenerator`, `NotificationChannels`, `Eew`, `EventBus`.
- Produces:
  - `class AlarmService : Service()` mit Actions: `ACTION_ARM` (Extras: `EXTRA_MMI: Double`, `EXTRA_TIER: String`, `EXTRA_TEST: Boolean`), `ACTION_USER_SAFE`, `ACTION_USER_HELP`, `ACTION_STOP`.
  - `companion object { fun arm(ctx: Context, mmi: Double, tier: Eew.Tier, isTest: Boolean); fun userSafe(ctx: Context); fun userHelp(ctx: Context); fun stop(ctx: Context) }`

- [ ] **Step 1: `AlarmService` implementieren**

Create `app/src/main/java/app/tda/AlarmService.kt`:

```kotlin
package app.tda

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vordergrund-Dienst (spec TP-3 §"Teil 2"). Trägt den DND-fähigen Alarmton, fährt die
 * Schonfrist-/Totmann-Zeitgeber über [SafetyState] und startet die [BeaconActivity] per
 * Full-Screen-Intent. Die reine Politik steckt in [SafetyState]/[CriticalAlarmPolicy]
 * (dort getestet); hier nur der Android-Rand.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_ARM = "app.tda.ARM"
        const val ACTION_USER_SAFE = "app.tda.USER_SAFE"
        const val ACTION_USER_HELP = "app.tda.USER_HELP"
        const val ACTION_STOP = "app.tda.STOP"
        const val EXTRA_MMI = "mmi"
        const val EXTRA_TIER = "tier"
        const val EXTRA_TEST = "test"
        private const val NOTIF_ID = 4201

        fun arm(ctx: Context, mmi: Double, tier: Eew.Tier, isTest: Boolean) =
            start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_ARM)
                .putExtra(EXTRA_MMI, mmi).putExtra(EXTRA_TIER, tier.name).putExtra(EXTRA_TEST, isTest))

        fun userSafe(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_USER_SAFE))
        fun userHelp(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_USER_HELP))
        fun stop(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))

        private fun start(ctx: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var safety: SafetyState
    private var config: SafetyConfig = SafetyConfig(true, 7, 300_000, 60_000)
    private var timerJob: Job? = null
    private var whistleTrack: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NotificationChannels.ensure(this)
        config = Prefs.safetyConfig()
        safety = SafetyState(config)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        startForeground(NOTIF_ID, buildServiceNotification(getString(R.string.svc_watching)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> onArm(
                intent.getDoubleExtra(EXTRA_MMI, 0.0),
                runCatching { Eew.Tier.valueOf(intent.getStringExtra(EXTRA_TIER) ?: "P2") }.getOrDefault(Eew.Tier.P2),
                intent.getBooleanExtra(EXTRA_TEST, false)
            )
            ACTION_USER_SAFE -> { safety.onUserSafe(); enterWatch() }
            ACTION_USER_HELP -> { safety.onUserHelp(); enterBeacon() }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun onArm(mmi: Double, tier: Eew.Tier, isTest: Boolean) {
        config = Prefs.safetyConfig()
        safety = SafetyState(config)
        if (safety.onConfirmedQuake(mmi) != SafetyPhase.ARMED) { // unterhalb Schwelle / deaktiviert
            if (!isTest) return else { /* TEST: Kette trotzdem zeigen */ }
        }
        // Schonfrist läuft leise; danach Rückfrage; unbeantwortet → Beacon (Totmann).
        timerJob?.cancel()
        timerJob = scope.launch {
            updateNotification(getString(R.string.svc_grace))
            delay(if (isTest) 3_000 else config.graceMillis)
            if (!isActive) return@launch
            safety.onGraceElapsed()
            launchBeaconAsk()
            delay(config.deadmanMillis)
            if (!isActive) return@launch
            if (safety.phase == SafetyPhase.ASKING) { safety.onCountdownElapsed(); enterBeacon() }
        }
    }

    private fun launchBeaconAsk() {
        val fsi = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeaconActivity::class.java)
                .putExtra(BeaconActivity.EXTRA_MODE, BeaconActivity.MODE_ASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, NotificationChannels.CRITICAL)
            .setSmallIcon(R.drawable.ic_status_attention)
            .setContentTitle(getString(R.string.beacon_ask_title))
            .setContentText(getString(R.string.beacon_ask_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID + 1, n)
    }

    private fun enterWatch() {
        timerJob?.cancel()
        stopWhistle()
        updateNotification(getString(R.string.svc_watching))
    }

    private fun enterBeacon() {
        updateNotification(getString(R.string.svc_beacon))
        if (Prefs.signalWhistle) startWhistle()
        val i = Intent(this, BeaconActivity::class.java)
            .putExtra(BeaconActivity.EXTRA_MODE, BeaconActivity.MODE_BEACON)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    /** Looped Sinus-Sweep über AudioTrack, USAGE_ALARM. Puffer aus [SignalGenerator] (getestet). */
    private fun startWhistle() {
        stopWhistle()
        val pcm = SignalGenerator.sweepPcm(900.0, 2200.0, 1200)
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { bytes[i * 2] = (pcm[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SignalGenerator.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(bytes, 0, bytes.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.play()
        whistleTrack = track
    }

    private fun stopWhistle() {
        whistleTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
        whistleTrack = null
    }

    private fun stopEverything() {
        timerJob?.cancel(); stopWhistle(); safety.reset()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun buildServiceNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_status_ready)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(text: String) {
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, buildServiceNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWhistle(); scope.cancel(); super.onDestroy()
    }
}
```

- [ ] **Step 2: Service-Strings ergänzen (`values/strings.xml`)**

Vor `</resources>` einfügen:

```xml
    <!-- TP-3 service + beacon -->
    <string name="svc_watching">Watching for aftershocks</string>
    <string name="svc_grace">Strong quake — you have time to act. Tap when safe.</string>
    <string name="svc_beacon">Emergency beacon active</string>
    <string name="beacon_ask_title">Are you safe?</string>
    <string name="beacon_ask_body">Tap to answer. No answer starts the emergency beacon.</string>
```

- [ ] **Step 3: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (`BeaconActivity` wird referenziert und in Task 9 gebaut — falls der Compiler sie hier vermisst, den Stub aus Task 9 Step 1 vorziehen.)

- [ ] **Step 4: Manuelle Prüfnotiz (für spätere Geräte-/Emulator-Prüfung, nicht Teil des Gates)**

Notieren in `scratchpad/tp3/manual-checks.md`: „ACTION_ARM (TEST) → nach 3 s FSI-Rückfrage; unbeantwortet → Beacon-Ton; ACTION_USER_SAFE stoppt Ton + Wächter-Notification."

---

### Task 8: Alarm als Full-Screen-Intent + Kette anstoßen

Bisher startet `MainActivity` `AlertActivity` nur bei offener App. Diese Task postet echte Alarme als Full-Screen-Intent-Notification (durchbricht Sperrbildschirm) und stößt bei bestätigtem starken Beben die `AlarmService`-Kette an.

**Files:**
- Modify: `app/src/main/java/app/tda/MainActivity.kt` (Alarm-Beobachter)
- Modify: `app/src/main/java/app/tda/AlertActivity.kt` (Ton-Politik via `CriticalAlarmPolicy`; Kette anstoßen bei P2)

**Interfaces:**
- Consumes: `CriticalAlarmPolicy`, `AlarmService.arm`, `NotificationChannels`, `Eew.mmi`, `Eew.Tier`, `Prefs`.
- Produces: keine neuen öffentlichen Typen.

- [ ] **Step 1: `AlertActivity` — Ton-Politik zentralisieren + Kette anstoßen**

In `app/src/main/java/app/tda/AlertActivity.kt` die Methode `triggerAlertFeedback` ersetzen durch eine Version, die `CriticalAlarmPolicy` nutzt, und in `observeEscalation` beim Wechsel auf P2 die Kette anstoßen.

Ersetze den kompletten Body von `triggerAlertFeedback(tier)` durch:

```kotlin
    private fun triggerAlertFeedback(tier: Eew.Tier) {
        val plan = CriticalAlarmPolicy.plan(
            tier = tier,
            isTest = false,
            soundEnabled = true,
            dndOptIn = Prefs.dndBypassOptIn,
            dndAccessGranted = dndAccessGranted()
        )
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (plan.playAlarmSound) {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(this, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone?.play()
            } catch (_: Exception) { }
        }
        if (plan.vibrate) {
            val pattern = if (tier == Eew.Tier.P2) longArrayOf(0, 400, 200, 400, 200, 400) else longArrayOf(0, 250)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun dndAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = getSystemService(android.app.NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }
```

In `observeEscalation()` direkt nach `triggerAlertFeedback(currentTier)` (im P2-Zweig) einfügen:

```kotlin
                    val mmiNow = Eew.mmi(mag, distKm)
                    AlarmService.arm(this@AlertActivity, mmiNow, Eew.Tier.P2, isTest = false)
```

- [ ] **Step 2: `MainActivity` — Alarm zusätzlich als Full-Screen-Intent posten**

In `app/src/main/java/app/tda/MainActivity.kt`, im `observeEventBus()`-Block, den `EventBus.alarm.collect { ... }`-Abschnitt so erweitern, dass zusätzlich zum bestehenden `launchAlert(payload)` eine FSI-Notification gepostet wird (damit der Alarm auch bei nicht-offener App zieht). Ersetze den bestehenden Alarm-Collector durch:

```kotlin
        scope.launch {
            EventBus.alarm.collect { payload ->
                if (payload.ver == 1 && EventBus.lastLaunchedAlarmId != payload.id) {
                    EventBus.lastLaunchedAlarmId = payload.id
                    postAlarmFullScreen(payload)
                    launchAlert(payload)
                }
            }
        }
```

Und eine Methode ergänzen (nutzt `NotificationChannels`, gleiche Extras wie `launchAlert`):

```kotlin
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
```

- [ ] **Step 3: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manuelle Prüfnotiz**

In `scratchpad/tp3/manual-checks.md`: „Testbeben → AlertActivity erscheint; bei P2 startet AlarmService (Wächter-Notification sichtbar); FSI-Notification erscheint auch bei Home-Screen."

---

### Task 9: `BeaconActivity` + Layout (Rückfrage, Notsignal, „Ich bin in Sicherheit"-Teilen)

Vollbild-Screen für „Bist du sicher?" (Countdown) und den Beacon-Modus (Bildschirm-/Taschenlampen-Strobo + Ton-Steuerung + Teilen).

**Files:**
- Create: `app/src/main/java/app/tda/BeaconActivity.kt`
- Create: `app/src/main/java/app/tda/Strobe.kt`
- Create: `app/src/main/res/layout/activity_beacon.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `StrobePattern`, `Pulse`, `Prefs`, `AlarmService.userSafe/userHelp/stop`.
- Produces:
  - `class BeaconActivity : AppCompatActivity()` mit `companion object { const val EXTRA_MODE="mode"; const val MODE_ASK="ask"; const val MODE_BEACON="beacon" }`
  - `class Strobe(activity: Activity) { fun startScreen(pulse: Pulse); fun startTorch(pulse: Pulse); fun stopAll() }`

- [ ] **Step 1: `Strobe` implementieren**

Create `app/src/main/java/app/tda/Strobe.kt`:

```kotlin
package app.tda

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Bildschirm- und Taschenlampen-Strobo (spec TP-3 §"Teil 3"). Timings kommen aus dem
 * getesteten [StrobePattern]; hier nur der Hardware-Rand. setTorchMode braucht kein Kamera-Recht.
 */
class Strobe(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchId: String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private var screenOn = false
    private var torchOn = false

    fun startScreen(pulse: Pulse) {
        val root = activity.findViewById<android.view.View>(R.id.beaconStrobe)
        val step = object : Runnable {
            var on = false
            override fun run() {
                on = !on
                root.setBackgroundColor(if (on) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setBrightness(if (on) 1f else 0.02f)
                handler.postDelayed(this, if (on) pulse.onMillis else pulse.offMillis)
            }
        }
        screenOn = true
        handler.post(step)
    }

    fun startTorch(pulse: Pulse) {
        val id = torchId ?: return
        val step = object : Runnable {
            var on = false
            override fun run() {
                on = !on
                runCatching { cameraManager?.setTorchMode(id, on) }
                handler.postDelayed(this, if (on) pulse.onMillis else pulse.offMillis)
            }
        }
        torchOn = true
        handler.post(step)
    }

    fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        if (screenOn) setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        if (torchOn) torchId?.let { runCatching { cameraManager?.setTorchMode(it, false) } }
        screenOn = false; torchOn = false
    }

    private fun setBrightness(value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value
        activity.window.attributes = lp
    }
}
```

- [ ] **Step 2: Layout anlegen**

Create `app/src/main/res/layout/activity_beacon.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/beaconStrobe"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="24dp">

        <TextView android:id="@+id/beaconTitle"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="28sp" android:textStyle="bold"
            android:textColor="@android:color/white"
            android:text="@string/beacon_ask_title" />

        <TextView android:id="@+id/beaconCountdown"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="48sp" android:textColor="@android:color/white"
            android:layout_marginTop="12dp" />

        <Button android:id="@+id/btnSafe"
            android:layout_width="match_parent" android:layout_height="72dp"
            android:layout_marginTop="24dp" android:text="@string/beacon_im_safe" />

        <Button android:id="@+id/btnHelp"
            android:layout_width="match_parent" android:layout_height="72dp"
            android:layout_marginTop="12dp" android:text="@string/beacon_need_help" />

        <Button android:id="@+id/btnStop"
            android:layout_width="match_parent" android:layout_height="72dp"
            android:layout_marginTop="12dp" android:visibility="gone"
            android:text="@string/beacon_stop" />

        <Button android:id="@+id/btnShareSafe"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="12dp" android:visibility="gone"
            android:text="@string/beacon_share_safe" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 3: `BeaconActivity` implementieren**

Create `app/src/main/java/app/tda/BeaconActivity.kt`:

```kotlin
package app.tda

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vollbild „Bist du sicher?" (MODE_ASK) bzw. aktives Notsignal (MODE_BEACON) — spec TP-3
 * §"Teil 2/3/4". Politik/Timings kommen aus [SafetyState]/[StrobePattern] (getestet); der
 * Dienst [AlarmService] ist die Wahrheit über den Zustand, diese Activity ist nur die Fläche.
 */
class BeaconActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ASK = "ask"
        const val MODE_BEACON = "beacon"
    }

    private lateinit var scope: CoroutineScope
    private var strobe: Strobe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = true))
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        setContentView(R.layout.activity_beacon)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        findViewById<Button>(R.id.btnSafe).setOnClickListener { onSafe() }
        findViewById<Button>(R.id.btnHelp).setOnClickListener { onHelp() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { onSafe() }
        findViewById<Button>(R.id.btnShareSafe).setOnClickListener { shareSafe() }

        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_BEACON -> renderBeacon()
            else -> renderAsk()
        }
    }

    private fun renderAsk() {
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_ask_title)
        findViewById<Button>(R.id.btnSafe).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnHelp).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnStop).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.GONE
        val total = Prefs.deadmanCountdownSec
        val cd = findViewById<TextView>(R.id.beaconCountdown)
        scope.launch {
            for (s in total downTo 1) {
                cd.text = s.toString()
                delay(1000)
                if (!isActive) return@launch
            }
            // Kein Tippen → Beacon (Totmann). AlarmService ist die Wahrheit; er startet uns neu in MODE_BEACON.
            renderBeacon()
        }
    }

    private fun renderBeacon() {
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_active_title)
        findViewById<TextView>(R.id.beaconCountdown).text = ""
        findViewById<Button>(R.id.btnSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnHelp).visibility = View.GONE
        findViewById<Button>(R.id.btnStop).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.GONE
        strobe = Strobe(this).also {
            if (Prefs.signalScreenStrobe) it.startScreen(StrobePattern.screen)
            if (Prefs.signalTorchStrobe) it.startTorch(StrobePattern.torch)
        }
        // Ton läuft im AlarmService (überlebt Screen-aus); hier nur Optik + Stopp.
    }

    private fun onSafe() {
        strobe?.stopAll()
        AlarmService.userSafe(this)
        AlarmService.stop(this)
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_safe_title)
        findViewById<TextView>(R.id.beaconCountdown).text = ""
        findViewById<Button>(R.id.btnSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnHelp).visibility = View.GONE
        findViewById<Button>(R.id.btnStop).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.VISIBLE
    }

    private fun onHelp() {
        AlarmService.userHelp(this)
        renderBeacon()
    }

    private fun shareSafe() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.beacon_share_text))
        }
        startActivity(Intent.createChooser(send, getString(R.string.beacon_share_safe)))
    }

    override fun onDestroy() {
        strobe?.stopAll(); scope.cancel(); super.onDestroy()
    }
}
```

- [ ] **Step 4: Beacon-Strings ergänzen (`values/strings.xml`)**

Vor `</resources>` einfügen:

```xml
    <string name="beacon_active_title">Emergency beacon active</string>
    <string name="beacon_safe_title">Marked safe</string>
    <string name="beacon_im_safe">I\'m safe</string>
    <string name="beacon_need_help">I need help</string>
    <string name="beacon_stop">Stop beacon</string>
    <string name="beacon_share_safe">Share “I\'m safe”</string>
    <string name="beacon_share_text">I\'m safe. — via Alert2IQ</string>
```

- [ ] **Step 5: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manuelle Prüfnotiz**

In `scratchpad/tp3/manual-checks.md`: „MODE_ASK zeigt Countdown + Sicher/Hilfe; ‚Ich brauche Hilfe' → Strobo (Bildschirm blinkt, Taschenlampe pulst sparsamer); ‚Ich bin in Sicherheit' → Strobo/Ton aus + Teilen-Sheet."

---

### Task 10: DND-Opt-in + minimale Auslöser + TEST-Kette in MainActivity

Verdrahtet den best-effort/Opt-in-DND-Fluss (ehrlich beschriftet), die Laufzeit-Rechte-Anfrage für Benachrichtigungen und einen TEST-Knopf, der die ganze Kette (verkürzt) durchspielt.

**Files:**
- Create: `app/src/main/java/app/tda/DndAccess.kt`
- Modify: `app/src/main/java/app/tda/MainActivity.kt` (Rechte-Anfrage + TEST-Knopf)
- Modify: `app/src/main/res/layout/activity_main.xml` (ein Knopf)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AlarmService.arm`, `NotificationChannels`, `Prefs.dndBypassOptIn`.
- Produces: `object DndAccess { fun isGranted(ctx: Context): Boolean; fun requestIntent(): Intent }`.

- [ ] **Step 1: `DndAccess` implementieren**

Create `app/src/main/java/app/tda/DndAccess.kt`:

```kotlin
package app.tda

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Kapselt den „Nicht stören"-Policy-Zugriff (spec TP-3 §"Teil 1", best-effort + Opt-in). */
object DndAccess {
    fun isGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun requestIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}
```

- [ ] **Step 2: TEST-Knopf ins Layout**

In `app/src/main/res/layout/activity_main.xml` im Test-Szenarien-Bereich (nach dem Reset-Button `@id/btnReset`) einfügen:

```xml
        <Button
            android:id="@+id/btnTestBeaconChain"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_test_beacon_chain" />
```

- [ ] **Step 3: MainActivity — Rechte-Anfrage + TEST-Kette**

In `app/src/main/java/app/tda/MainActivity.kt` am Ende von `onCreate` (nach `observeEventBus()`) ergänzen:

```kotlin
        NotificationChannels.ensure(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
```

In `setupTestButtons()` ergänzen:

```kotlin
        findViewById<android.widget.Button>(R.id.btnTestBeaconChain).setOnClickListener {
            val city = Prefs.selectedCity()
            // TEST: unverwechselbar über AlarmService (verkürzte Schonfrist), MMI klar über Schwelle.
            AlarmService.arm(this, mmi = 9.0, tier = Eew.Tier.P2, isTest = true)
            Toast.makeText(this, R.string.test_beacon_started, Toast.LENGTH_LONG).show()
        }
```

Sicherstellen, dass `import android.os.Build` oben vorhanden ist (sonst ergänzen).

- [ ] **Step 4: Strings ergänzen (`values/strings.xml`)**

Vor `</resources>` einfügen:

```xml
    <string name="btn_test_beacon_chain">Test beacon chain (TEST)</string>
    <string name="test_beacon_started">TEST beacon chain armed — this is only a drill.</string>
    <string name="dnd_optin_explain">Allow the app to sound through “Do Not Disturb”. Without this, full DND can silence even a life-saving alert.</string>
```

- [ ] **Step 5: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manuelle Prüfnotiz**

In `scratchpad/tp3/manual-checks.md`: „TEST-Knopf → nach ~3 s Rückfrage; unbeantwortet → Beacon; POST_NOTIFICATIONS-Dialog erscheint bei Erststart auf Android 13+."

---

### Task 11: i18n — alle neuen Strings in TR/KU/AR

Alle in Tasks 6–10 in `values/strings.xml` (EN) neu angelegten Schlüssel in `values-tr/`, `values-ku/`, `values-ar/` übersetzen. Kein `t()`-Fallback im Android-Ressourcensystem — fehlt ein Schlüssel in der Default-`values/`, bricht der Build; fehlt er nur in einer Übersetzung, fällt Android auf EN zurück (akzeptabel, aber wir liefern alle vier).

**Files:**
- Modify: `app/src/main/res/values-tr/strings.xml`
- Modify: `app/src/main/res/values-ku/strings.xml`
- Modify: `app/src/main/res/values-ar/strings.xml`

**Interfaces:**
- Consumes: die neuen Schlüssel aus `values/strings.xml`.
- Produces: nichts.

**Neue Schlüssel (vollständige Liste, muss in allen drei Dateien vorkommen):**
`chan_critical`, `chan_critical_desc`, `chan_test`, `chan_test_desc`, `chan_ready`, `chan_ready_desc`, `chan_service`, `chan_service_desc`, `svc_watching`, `svc_grace`, `svc_beacon`, `beacon_ask_title`, `beacon_ask_body`, `beacon_active_title`, `beacon_safe_title`, `beacon_im_safe`, `beacon_need_help`, `beacon_stop`, `beacon_share_safe`, `beacon_share_text`, `btn_test_beacon_chain`, `test_beacon_started`, `dnd_optin_explain`.

- [ ] **Step 1: Türkisch ergänzen**

In `app/src/main/res/values-tr/strings.xml` vor `</resources>` einfügen:

```xml
    <!-- TP-3 -->
    <string name="chan_critical">Kritik deprem uyarıları</string>
    <string name="chan_critical_desc">Hayat kurtaran onaylı uyarılar. Sessizde bile çalabilir.</string>
    <string name="chan_test">Test uyarıları</string>
    <string name="chan_test_desc">Deneme uyarıları — asla gerçek alarm sesi değil.</string>
    <string name="chan_ready">Bekleme modu</string>
    <string name="chan_ready_desc">Güçlü depremden sonra sessiz bildirim; güvende olduğunu bildirebilirsin.</string>
    <string name="chan_service">Artçı takibi</string>
    <string name="chan_service_desc">Arka planda artçı sarsıntıları izler.</string>
    <string name="svc_watching">Artçılar izleniyor</string>
    <string name="svc_grace">Güçlü deprem — harekete zamanın var. Güvendeysen dokun.</string>
    <string name="svc_beacon">Acil sinyal etkin</string>
    <string name="beacon_ask_title">Güvende misin?</string>
    <string name="beacon_ask_body">Yanıtlamak için dokun. Yanıt yoksa acil sinyal başlar.</string>
    <string name="beacon_active_title">Acil sinyal etkin</string>
    <string name="beacon_safe_title">Güvende olarak işaretlendi</string>
    <string name="beacon_im_safe">Güvendeyim</string>
    <string name="beacon_need_help">Yardıma ihtiyacım var</string>
    <string name="beacon_stop">Sinyali durdur</string>
    <string name="beacon_share_safe">“Güvendeyim”i paylaş</string>
    <string name="beacon_share_text">Güvendeyim. — Alert2IQ ile</string>
    <string name="btn_test_beacon_chain">Sinyal zincirini test et (TEST)</string>
    <string name="test_beacon_started">TEST sinyal zinciri kuruldu — bu yalnızca bir tatbikat.</string>
    <string name="dnd_optin_explain">Uygulamanın “Rahatsız Etmeyin”de çalmasına izin ver. Bu olmadan tam DND, hayat kurtaran bir uyarıyı bile susturabilir.</string>
```

- [ ] **Step 2: Kurmancî (ku) ergänzen**

In `app/src/main/res/values-ku/strings.xml` vor `</resources>` denselben Block einfügen, übersetzt nach Kurmancî. Wo eine sichere Übersetzung fehlt, den englischen Wert übernehmen (Android-Fallback ist ohnehin EN) — aber Schlüssel müssen vorhanden sein. Mindesttext:

```xml
    <!-- TP-3 -->
    <string name="chan_critical">Hişyariyên krîtîk ên erdhejê</string>
    <string name="chan_critical_desc">Hişyariyên pejirandî yên jiyanparêz. Di dema bêdengiyê de jî dikare bibe deng.</string>
    <string name="chan_test">Hişyariyên ceribandinê</string>
    <string name="chan_test_desc">Hişyariyên ceribandinê — tu carî dengê alarma rastîn nine.</string>
    <string name="chan_ready">Moda amadebûnê</string>
    <string name="chan_ready_desc">Piştî erdhejeke bihêz agahdariyeke bêdeng; tu dikarî ewlehiya xwe ragihînî.</string>
    <string name="chan_service">Şopandina paşerdhejan</string>
    <string name="chan_service_desc">Di paşperdeyê de paşerdhejan dişopîne.</string>
    <string name="svc_watching">Paşerdhej têne şopandin</string>
    <string name="svc_grace">Erdheja bihêz — wextê te heye. Gava ewle bî bitikîne.</string>
    <string name="svc_beacon">Sînyala awarte çalak e</string>
    <string name="beacon_ask_title">Tu ewle yî?</string>
    <string name="beacon_ask_body">Bo bersivê bitikîne. Bê bersiv, sînyala awarte dest pê dike.</string>
    <string name="beacon_active_title">Sînyala awarte çalak e</string>
    <string name="beacon_safe_title">Wek ewle hate nîşankirin</string>
    <string name="beacon_im_safe">Ez ewle me</string>
    <string name="beacon_need_help">Alîkariya min divê</string>
    <string name="beacon_stop">Sînyalê rawestîne</string>
    <string name="beacon_share_safe">“Ez ewle me” parve bike</string>
    <string name="beacon_share_text">Ez ewle me. — bi Alert2IQ</string>
    <string name="btn_test_beacon_chain">Zincîra sînyalê biceribîne (TEST)</string>
    <string name="test_beacon_started">Zincîra sînyalê ya TEST hate amadekirin — ev tenê şêwirandin e.</string>
    <string name="dnd_optin_explain">Destûrê bide ku app di “Aciz neke” de bibe deng. Bêyî vê, DND-a temam dikare hişyariyeke jiyanparêz jî bêdeng bike.</string>
```

- [ ] **Step 3: Arabisch (ar) ergänzen**

In `app/src/main/res/values-ar/strings.xml` vor `</resources>` denselben Block auf Arabisch einfügen:

```xml
    <!-- TP-3 -->
    <string name="chan_critical">تنبيهات الزلازل الحرجة</string>
    <string name="chan_critical_desc">تنبيهات مؤكدة منقذة للحياة. قد تصدر صوتًا حتى في الوضع الصامت.</string>
    <string name="chan_test">تنبيهات اختبارية</string>
    <string name="chan_test_desc">تنبيهات تجريبية — ليست صوت الإنذار الحقيقي أبدًا.</string>
    <string name="chan_ready">وضع الاستعداد</string>
    <string name="chan_ready_desc">إشعار صامت بعد زلزال قوي حتى تتمكن من الإبلاغ أنك بأمان.</string>
    <string name="chan_service">مراقبة الهزات الارتدادية</string>
    <string name="chan_service_desc">يراقب الهزات الارتدادية في الخلفية.</string>
    <string name="svc_watching">مراقبة الهزات الارتدادية</string>
    <string name="svc_grace">زلزال قوي — لديك وقت للتصرف. انقر عندما تكون بأمان.</string>
    <string name="svc_beacon">إشارة الطوارئ نشطة</string>
    <string name="beacon_ask_title">هل أنت بأمان؟</string>
    <string name="beacon_ask_body">انقر للرد. عدم الرد يبدأ إشارة الطوارئ.</string>
    <string name="beacon_active_title">إشارة الطوارئ نشطة</string>
    <string name="beacon_safe_title">تم وضع علامة بأمان</string>
    <string name="beacon_im_safe">أنا بأمان</string>
    <string name="beacon_need_help">أحتاج المساعدة</string>
    <string name="beacon_stop">إيقاف الإشارة</string>
    <string name="beacon_share_safe">مشاركة ”أنا بأمان“</string>
    <string name="beacon_share_text">أنا بأمان. — عبر Alert2IQ</string>
    <string name="btn_test_beacon_chain">اختبار سلسلة الإشارة (اختبار)</string>
    <string name="test_beacon_started">تم تجهيز سلسلة إشارة الاختبار — هذا تدريب فقط.</string>
    <string name="dnd_optin_explain">اسمح للتطبيق بإصدار صوت أثناء ”عدم الإزعاج“. بدون ذلك قد يُسكت وضع عدم الإزعاج الكامل حتى تنبيهًا منقذًا للحياة.</string>
```

- [ ] **Step 4: Gate**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — alle Locales kompilieren, keine fehlenden Schlüssel in `values/`.

- [ ] **Step 5: Vollständigkeits-Check**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL — reine-Logik-Tests (Tasks 1–4) grün **und** App kompiliert mit allen TP-3-Teilen.

---

## Self-Review (gegen die Spec)

**1. Spec-Abdeckung:**
- Teil 1 (kritischer Alarm/DND) → Tasks 2, 6, 8, 10 ✅
- Teil 2 („Bist du sicher?" + FGS) → Tasks 1, 7, 9 ✅
- Teil 3 (Pfeife/Strobo) → Tasks 3, 4, 7 (Ton), 9 (Strobo) ✅
- Teil 4 (in Sicherheit teilen) → Task 9 ✅
- Parameter/Prefs → Task 5 ✅
- i18n TR/KU/AR → Task 11 ✅
- Reine-Logik-Tests → Tasks 1–4 ✅
- Nicht in TP-3 (Onboarding/Einstellungsseite, BLE, FCM) → nicht enthalten ✅

**2. Platzhalter:** keine „TBD/TODO/handle edge cases"; jeder Code-Schritt zeigt vollständigen Code.

**3. Typkonsistenz:** `SafetyPhase`/`SafetyConfig`/`SafetyState` (Task 1) → genutzt in `Prefs.safetyConfig()` (5) und `AlarmService` (7); `AlarmPlan`/`CriticalAlarmPolicy.plan(...)` Signatur (2) → genutzt in `AlertActivity` (8); `Pulse`/`StrobePattern.screen/torch` (3) → genutzt in `Strobe`/`BeaconActivity` (9); `SignalGenerator.sweepPcm`/`SAMPLE_RATE` (4) → genutzt in `AlarmService` (7); `NotificationChannels.CRITICAL/...` (6) → genutzt in 7/8/10; `AlarmService.arm/userSafe/userHelp/stop` (7) → genutzt in 8/9/10; `BeaconActivity.EXTRA_MODE/MODE_ASK/MODE_BEACON` (9) → genutzt in 7. Konsistent.

**Bekannte Reihenfolge-Abhängigkeit:** Task 6 deklariert den `<service .AlarmService>`, Task 7 legt die Klasse an; Task 7/8 referenzieren `BeaconActivity` aus Task 9. Bei strikt sequentieller Ausführung ggf. Stub vorziehen (in den betroffenen Steps vermerkt). Empfohlene Ausführungsreihenfolge daher: 1 → 2 → 3 → 4 → 5 → 6 → 9 → 7 → 8 → 10 → 11, oder Stubs nutzen.
