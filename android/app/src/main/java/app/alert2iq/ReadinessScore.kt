package app.alert2iq

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
