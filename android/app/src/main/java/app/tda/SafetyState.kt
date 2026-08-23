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
