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
