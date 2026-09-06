package app.alert2iq

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Fachlogik des Frühwarn-Clients, gespiegelt aus dem verifizierten Server-Backend
 * (siehe tda/docs/client-mvp-spec.md, Abschnitt "Fachlogik").
 *
 * WICHTIG: Dies ist eine reine Client-Anzeigelogik. Die Payload-Signaturprüfung
 * (Ed25519) ist im MVP NICHT verdrahtet -- siehe Kommentar in [AlarmPayload].
 */
object Eew {

    /** Erdradius in km, identisch zum Backend. */
    const val EARTH_RADIUS_KM = 6371.0

    /** S-Wellen-Geschwindigkeit in km/s. */
    const val V_S_KM_S = 3.5

    /** P-Wellen-Geschwindigkeit in km/s (nur Info). */
    const val V_P_KM_S = 6.0

    /** Haversine-Distanz in km zwischen zwei Punkten (identisch zum Backend, R=6371). */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val la1 = Math.toRadians(lat1)
        val la2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Verbleibende Sekunden bis die S-Welle den Nutzerort erreicht (nie negativ). */
    fun sWaveEtaSeconds(distKm: Double, elapsedSec: Double): Double =
        max(0.0, distKm / V_S_KM_S - elapsedSec)

    /** Verbleibende Sekunden bis die P-Welle den Nutzerort erreicht (Info, nie negativ). */
    fun pWaveEtaSeconds(distKm: Double, elapsedSec: Double): Double =
        max(0.0, distKm / V_P_KM_S - elapsedSec)

    /**
     * Grobe Intensitätsschätzung am Ort (MMI, 1..12), NICHT wissenschaftlich exakt --
     * einfache Abnahmeformel zur Anzeige, klar als Schätzung zu kennzeichnen.
     * MMI ≈ 1.5·mag − 3.0·log10(max(dist_km,1)) + 3.0
     */
    fun mmi(magnitude: Double, distKm: Double): Double {
        val raw = 1.5 * magnitude - 3.0 * log10(max(distKm, 1.0)) + 3.0
        return raw.coerceIn(1.0, 12.0)
    }

    private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

    /** Rundet MMI auf ganze Stufe und liefert die römische Ziffer (I..XII). */
    fun mmiRoman(mmiValue: Double): String {
        val idx = (Math.round(mmiValue).toInt() - 1).coerceIn(0, ROMAN.size - 1)
        return ROMAN[idx]
    }

    /** Ganzzahlige MMI-Stufe 1..12, für Kurztext-Lookup in strings.xml. */
    fun mmiLevel(mmiValue: Double): Int =
        Math.round(mmiValue).toInt().coerceIn(1, 12)

    enum class ProtectionLevel { STRONG, MODERATE, WEAK }

    /** Schutzstatus aus MMI: >=5 Ducken/Schützen/Halten, 3-4 Erschütterung möglich, <3 schwach. */
    fun protectionLevel(mmiValue: Double): ProtectionLevel = when {
        mmiValue >= 5.0 -> ProtectionLevel.STRONG
        mmiValue >= 3.0 -> ProtectionLevel.MODERATE
        else -> ProtectionLevel.WEAK
    }

    /** Alarm-Tier, wie vom Backend gemeldet. */
    enum class Tier { P0, P1, P2 }

    /**
     * Alarm-Payload -- Feldnamen 1:1 wie Backend `build_payload` (String-Map).
     * v=Protokollversion, id=Event-ID, ver=Revisionszähler, state=Lebenszyklus-Status,
     * tier=P0/P1/P2, test=Testflag, origin_ts=Bruchbeginn (epoch s),
     * lat/lon=Epizentrum, depth_km=Tiefe, mag=Magnitude, mag_hi=obere Schätzung,
     * src=Quelle, issued_ts=Ausgabezeit (epoch s).
     *
     * TODO(Stufe 2): Ed25519/BouncyCastle-Signaturprüfung der Payload ist hier NICHT
     * verdrahtet (Public-Key-Verteilung ungeklärt). Im MVP kommen alle Payloads aus dem
     * lokalen [TestScenarios]-Injektor, nie aus dem Netz.
     */
    data class AlarmPayload(
        val v: String = "1",
        val id: String,
        val ver: Int = 1,
        val state: String,
        val tier: Tier,
        val test: Boolean = true,
        val originTs: Long,
        val lat: Double,
        val lon: Double,
        val depthKm: Double,
        val mag: Double,
        val magHi: Double? = null,
        val src: String,
        val issuedTs: Long
    ) {
        fun toStringMap(): Map<String, String> = mapOf(
            "v" to v,
            "id" to id,
            "ver" to ver.toString(),
            "state" to state,
            "tier" to tier.name,
            "test" to test.toString(),
            "origin_ts" to originTs.toString(),
            "lat" to lat.toString(),
            "lon" to lon.toString(),
            "depth_km" to depthKm.toString(),
            "mag" to mag.toString(),
            "mag_hi" to (magHi?.toString() ?: ""),
            "src" to src,
            "issued_ts" to issuedTs.toString()
        )
    }

    data class City(val id: String, val displayName: String, val lat: Double, val lon: Double)

    val CITIES = listOf(
        City("adana", "Adana", 37.00, 35.32),
        City("gaziantep", "Gaziantep", 37.06, 37.38),
        City("kahramanmaras", "Kahramanmaraş", 37.58, 36.93),
        City("istanbul", "İstanbul", 41.01, 28.98),
        City("izmir", "İzmir", 38.42, 27.14),
        City("ankara", "Ankara", 39.93, 32.86)
    )

    /** Sequenz-Eintrag für die Nachbeben-Sequenzliste. */
    data class SequenceEntry(
        val label: String,
        val magnitude: Double,
        val mmiAtUser: Double,
        val timestamp: Long,
        val isMainshock: Boolean
    )
}
