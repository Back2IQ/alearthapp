package app.alert2iq

/** Geteilte, feinjustierbare Startwerte für Ruhe- und Rütteln-Erkennung. */
data class SensorConfig(
    val stillVar: Double = 0.05,
    val motionVar: Double = 0.5,
    val settleMs: Long = 60_000,
    val shakeDelta: Double = 1.2,
    val shakeMinSamples: Int = 3,
    val refractoryMs: Long = 30_000,
)

enum class Stillness { SETTLED, UNSETTLED }

/**
 * Erkennt, ob das Handy ruhig liegt: gleitende Varianz der Beschleunigungs-
 * Magnitude über die letzten [windowSize] Samples. SETTLED, sobald die Varianz
 * für ≥ settleMs unter stillVar blieb; ein Ausschlag über motionVar setzt sofort
 * zurück (Handy angefasst). Reine Logik — Sensor-Anbindung im Service (Task 11).
 */
class StillnessDetector(private val cfg: SensorConfig, private val windowSize: Int = 50) {
    private val mags = ArrayDeque<Double>()
    private var quietSinceMs: Long? = null

    fun onSample(tMs: Long, magnitude: Double): Stillness {
        mags.addLast(magnitude)
        while (mags.size > windowSize) mags.removeFirst()
        if (mags.size < windowSize) return Stillness.UNSETTLED
        val variance = variance(mags)
        if (variance > cfg.motionVar) {
            quietSinceMs = null
            return Stillness.UNSETTLED
        }
        if (variance <= cfg.stillVar) {
            val since = quietSinceMs ?: tMs.also { quietSinceMs = it }
            if (tMs - since >= cfg.settleMs) return Stillness.SETTLED
        }
        return Stillness.UNSETTLED
    }

    private fun variance(xs: Collection<Double>): Double {
        val mean = xs.sum() / xs.size
        return xs.sumOf { (it - mean) * (it - mean) } / xs.size
    }
}
