package app.alearthapp

/**
 * Typen von Naturereignissen / Katastrophen.
 */
enum class DisasterType(val code: String) {
    QUAKE("quake"),
    TSUNAMI("tsunami"),
    STORM("storm"),
    FLOOD("flood"),
    WILDFIRE("wildfire"),
    VOLCANO("volcano"),
    OTHER("other");

    companion object {
        fun fromCode(code: String): DisasterType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OTHER
    }
}

/**
 * Warnstufen für GDACS-Ereignisse (Green, Orange, Red).
 */
enum class AlertLevel(val rank: Double) {
    NONE(0.0),
    GREEN(1.0),
    ORANGE(1.5),
    RED(2.0);

    companion object {
        fun fromString(s: String?): AlertLevel = when (s?.lowercase()) {
            "red" -> RED
            "orange" -> ORANGE
            "green" -> GREEN
            else -> NONE
        }
    }
}

/**
 * Einheitliches Ereignis-Modell für Erdbeben und Naturgefahren.
 */
data class DisasterEvent(
    val id: String,
    val type: DisasterType,
    val magnitude: Double? = null,
    val alertLevel: AlertLevel = AlertLevel.NONE,
    val place: String = "",
    val lat: Double,
    val lon: Double,
    val depthKm: Double? = null,
    val timeMs: Long = 0L,
    val source: String = "",
    val url: String = ""
) {
    /**
     * Kombinierter Schweregrad-Score für sinnvolle Sortierung über Gefahrentypen hinweg.
     */
    fun severityScore(): Double {
        if (type == DisasterType.QUAKE && magnitude != null) return magnitude
        if (alertLevel != AlertLevel.NONE) return 3.5 + alertLevel.rank
        return 0.0
    }
}

enum class RegionFilter {
    ALL,
    TURKEY,
    NEARBY
}

enum class SortOption {
    TIME,
    NEARBY,
    SEVERITY
}

data class DisastersFilter(
    val types: Set<DisasterType> = emptySet(),
    val region: RegionFilter = RegionFilter.ALL,
    val minMag: Double = 0.0,
    val minAlert: AlertLevel = AlertLevel.NONE,
    val timeWindowHours: Int = 48
)
