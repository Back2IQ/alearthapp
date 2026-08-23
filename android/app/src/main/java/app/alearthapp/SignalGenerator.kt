package app.alearthapp

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
