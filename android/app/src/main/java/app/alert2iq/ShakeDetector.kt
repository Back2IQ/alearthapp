package app.alert2iq

import kotlin.math.abs

/**
 * Erkennt ein anhaltendes Rütteln (nicht einen einzelnen Klaps): ≥ shakeMinSamples
 * aufeinanderfolgende Samples, die um mehr als shakeDelta von der Ruhelage
 * (Schwerkraft) abweichen → gibt den Zeitstempel des ERSTEN auffälligen Samples
 * als trigger_ms zurück. Danach refractoryMs Sperrzeit gegen Dubletten desselben
 * Ereignisses. Bewusst großzügig — der Server sortiert Fehlalarme aus.
 */
class ShakeDetector(private val cfg: SensorConfig, private val baseline: Double = 9.81) {
    private var run = 0
    private var runStartMs = 0L
    private var suppressUntilMs = 0L

    fun onSample(tMs: Long, magnitude: Double): Long? {
        if (tMs < suppressUntilMs) {
            // still shaking during refractory: keep suppressing, don't re-arm mid-burst
            if (abs(magnitude - baseline) <= cfg.shakeDelta) run = 0
            return null
        }
        if (abs(magnitude - baseline) > cfg.shakeDelta) {
            if (run == 0) runStartMs = tMs
            run++
            if (run >= cfg.shakeMinSamples) {
                val triggerMs = runStartMs
                run = 0
                suppressUntilMs = tMs + cfg.refractoryMs
                return triggerMs
            }
        } else {
            run = 0
        }
        return null
    }
}
