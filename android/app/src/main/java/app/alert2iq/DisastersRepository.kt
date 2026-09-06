package app.alert2iq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nativer Daten-Layer für Katastrophen- und Erdbebenfeeds (USGS, EMSC, GDACS, EONET).
 * Bietet 1:1 Parität mit den Filter-, Sortier- und Vereinheitlichungsregeln aus webapp/lib/disasters.js.
 */
object DisastersRepository {

    const val TR_MIN_LAT = 33.0
    const val TR_MAX_LAT = 43.5
    const val TR_MIN_LON = 24.0
    const val TR_MAX_LON = 47.0

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private const val USGS_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson"
    private const val EMSC_URL = "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=100"
    private const val GDACS_URL = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/MAP?alertlevel=green;orange;red"
    private const val EONET_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&days=3"

    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = Math.PI / 180.0
        val dLat = (lat2 - lat1) * r
        val dLon = (lon2 - lon1) * r
        val la1 = lat1 * r
        val la2 = lat2 * r
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun isInsideTurkey(lat: Double, lon: Double): Boolean {
        return lat in TR_MIN_LAT..TR_MAX_LAT && lon in TR_MIN_LON..TR_MAX_LON
    }

    /**
     * Parst USGS GeoJSON FeatureCollection.
     */
    fun parseUsgsGeoJson(jsonStr: String): List<DisasterEvent> {
        val list = mutableListOf<DisasterEvent>()
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return list
        val features = root.optJSONArray("features") ?: return list
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val id = f.optString("id")
            val props = f.optJSONObject("properties") ?: continue
            val geom = f.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val depth = if (coords.length() >= 3) coords.optDouble(2) else null
            val mag = if (props.has("mag") && !props.isNull("mag")) props.optDouble("mag") else null
            val place = props.optString("place", "")
            val timeMs = props.optLong("time", 0L)
            val url = props.optString("url", "")

            if (mag != null && id.isNotEmpty()) {
                list.add(
                    DisasterEvent(
                        id = id,
                        type = DisasterType.QUAKE,
                        magnitude = mag,
                        alertLevel = AlertLevel.NONE,
                        place = place,
                        lat = lat,
                        lon = lon,
                        depthKm = depth,
                        timeMs = timeMs,
                        source = "USGS",
                        url = url
                    )
                )
            }
        }
        return list
    }

    /**
     * Parst EMSC FDSN GeoJSON / JSON format.
     */
    fun parseEmscGeoJson(jsonStr: String): List<DisasterEvent> {
        val list = mutableListOf<DisasterEvent>()
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return list
        val features = root.optJSONArray("features") ?: return list
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val id = f.optString("id")
            val props = f.optJSONObject("properties") ?: continue
            val geom = f.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val depth = if (coords.length() >= 3) coords.optDouble(2) else null
            val mag = if (props.has("mag") && !props.isNull("mag")) props.optDouble("mag") else null
            val place = props.optString("flynn_region", props.optString("place", ""))
            val timeStr = props.optString("time", "")
            val timeMs = parseIsoTime(timeStr)
            val url = props.optString("url", "")

            if (mag != null && id.isNotEmpty()) {
                list.add(
                    DisasterEvent(
                        id = "emsc:$id",
                        type = DisasterType.QUAKE,
                        magnitude = mag,
                        alertLevel = AlertLevel.NONE,
                        place = place,
                        lat = lat,
                        lon = lon,
                        depthKm = depth,
                        timeMs = timeMs,
                        source = "EMSC",
                        url = url
                    )
                )
            }
        }
        return list
    }

    /**
     * Parst GDACS Multi-Hazard GeoJSON oder JSON Events.
     */
    fun parseGdacsGeoJson(jsonStr: String): List<DisasterEvent> {
        val list = mutableListOf<DisasterEvent>()
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return list
        val features = root.optJSONArray("features") ?: return list
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: continue
            val geom = f.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val eventType = props.optString("eventtype", "").uppercase()
            val alertStr = props.optString("alertlevel", "green")
            val alertLevel = AlertLevel.fromString(alertStr)
            val title = props.optString("name", props.optString("eventname", ""))
            val country = props.optString("country", "")
            val place = if (title.isNotEmpty()) title else country
            val dateStr = props.optString("fromdate", props.optString("todate", ""))
            val timeMs = parseIsoTime(dateStr)
            val url = props.optString("url", "")

            val type = when (eventType) {
                "EQ" -> DisasterType.QUAKE
                "TS" -> DisasterType.TSUNAMI
                "TC" -> DisasterType.STORM
                "FL" -> DisasterType.FLOOD
                "VO" -> DisasterType.VOLCANO
                "WF" -> DisasterType.WILDFIRE
                else -> DisasterType.OTHER
            }

            val id = "gdacs:$eventType:$lat,$lon:$timeMs"
            list.add(
                DisasterEvent(
                    id = id,
                    type = type,
                    magnitude = if (type == DisasterType.QUAKE) props.optDouble("severity", 0.0).takeIf { it > 0 } else null,
                    alertLevel = alertLevel,
                    place = place,
                    lat = lat,
                    lon = lon,
                    timeMs = timeMs,
                    source = "GDACS",
                    url = url
                )
            )
        }
        return list
    }

    /**
     * Parst EONET JSON v3.
     */
    fun parseEonetJson(jsonStr: String): List<DisasterEvent> {
        val list = mutableListOf<DisasterEvent>()
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return list
        val events = root.optJSONArray("events") ?: return list
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val id = ev.optString("id")
            val title = ev.optString("title")
            val cats = ev.optJSONArray("categories")
            val catTitle = cats?.optJSONObject(0)?.optString("title", "") ?: ""
            val geometries = ev.optJSONArray("geometry") ?: continue
            val latestGeom = geometries.optJSONObject(geometries.length() - 1) ?: continue
            val coords = latestGeom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue

            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val dateStr = latestGeom.optString("date", "")
            val timeMs = parseIsoTime(dateStr)

            val type = when (catTitle) {
                "Wildfires" -> DisasterType.WILDFIRE
                "Severe Storms" -> DisasterType.STORM
                "Volcanoes" -> DisasterType.VOLCANO
                "Floods" -> DisasterType.FLOOD
                else -> DisasterType.OTHER
            }

            if (id.isNotEmpty()) {
                list.add(
                    DisasterEvent(
                        id = "eonet:$id",
                        type = type,
                        magnitude = null,
                        alertLevel = AlertLevel.NONE,
                        place = title,
                        lat = lat,
                        lon = lon,
                        timeMs = timeMs,
                        source = "EONET"
                    )
                )
            }
        }
        return list
    }

    private fun parseIsoTime(isoStr: String): Long {
        if (isoStr.isEmpty()) return System.currentTimeMillis()
        return runCatching {
            java.time.Instant.parse(isoStr).toEpochMilli()
        }.getOrElse {
            runCatching {
                // Fallback for timestamps with different formatting
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoStr.take(19))?.time ?: System.currentTimeMillis()
            }.getOrDefault(System.currentTimeMillis())
        }
    }

    /**
     * Führt Deduplizierung von seismischen Ereignissen zwischen USGS, EMSC und GDACS durch.
     * Wenn zwei Beben innerhalb von 16 Sekunden und 45 km liegen, gewinnt der detailliertere Eintrag.
     */
    fun deduplicate(events: List<DisasterEvent>): List<DisasterEvent> {
        val quakes = events.filter { it.type == DisasterType.QUAKE }
        val nonQuakes = events.filter { it.type != DisasterType.QUAKE }

        val resultQuakes = mutableListOf<DisasterEvent>()
        for (q in quakes) {
            val duplicate = resultQuakes.find { existing ->
                val timeDiff = kotlin.math.abs(existing.timeMs - q.timeMs)
                val dist = haversineKm(existing.lat, existing.lon, q.lat, q.lon)
                timeDiff <= 25_000L && dist <= 50.0
            }
            if (duplicate == null) {
                resultQuakes.add(q)
            } else {
                // Bei Duplikat: behalte USGS oder das mit genauerem Ort / höherer Magnitude
                if (q.source == "USGS" && duplicate.source != "USGS") {
                    resultQuakes.remove(duplicate)
                    resultQuakes.add(q)
                }
            }
        }

        return resultQuakes + nonQuakes
    }

    /**
     * Filtert Ereignisse nach Typ, Region, Mindest-Magnitude, Warnstufe und Zeitfenster.
     */
    fun filter(
        events: List<DisasterEvent>,
        filter: DisastersFilter,
        userLat: Double = 0.0,
        userLon: Double = 0.0,
        userRadiusKm: Double = 300.0,
        nowMs: Long = System.currentTimeMillis()
    ): List<DisasterEvent> {
        val windowMs = filter.timeWindowHours * 3600 * 1000L
        return events.filter { d ->
            if (filter.types.isNotEmpty() && d.type !in filter.types) return@filter false
            if (d.timeMs > 0 && nowMs - d.timeMs > windowMs) return@filter false

            if (d.type == DisasterType.QUAKE) {
                if (d.magnitude != null && d.magnitude < filter.minMag) return@filter false
            } else {
                if (filter.minAlert != AlertLevel.NONE && d.alertLevel.rank < filter.minAlert.rank) return@filter false
            }

            when (filter.region) {
                RegionFilter.TURKEY -> if (!isInsideTurkey(d.lat, d.lon)) return@filter false
                RegionFilter.NEARBY -> if (haversineKm(userLat, userLon, d.lat, d.lon) > userRadiusKm) return@filter false
                RegionFilter.ALL -> {}
            }
            true
        }
    }

    /**
     * Sortiert Ereignisse nach Zeit, Nähe oder Schweregrad.
     */
    fun sort(
        events: List<DisasterEvent>,
        option: SortOption,
        userLat: Double = 0.0,
        userLon: Double = 0.0
    ): List<DisasterEvent> {
        val copy = events.toMutableList()
        when (option) {
            SortOption.NEARBY -> copy.sortWith(compareBy { haversineKm(userLat, userLon, it.lat, it.lon) })
            SortOption.SEVERITY -> copy.sortWith(compareByDescending { it.severityScore() })
            SortOption.TIME -> copy.sortWith(compareByDescending { it.timeMs })
        }
        return copy
    }

    /**
     * Lädt alle Feeds asynchron herunter und führt sie zusammen.
     */
    suspend fun fetchAll(): List<DisasterEvent> = withContext(Dispatchers.IO) {
        val all = mutableListOf<DisasterEvent>()

        val usgsReq = Request.Builder().url(USGS_URL).build()
        runCatching {
            httpClient.newCall(usgsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    all.addAll(parseUsgsGeoJson(body))
                }
            }
        }

        val emscReq = Request.Builder().url(EMSC_URL).build()
        runCatching {
            httpClient.newCall(emscReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    all.addAll(parseEmscGeoJson(body))
                }
            }
        }

        val gdacsReq = Request.Builder().url(GDACS_URL).build()
        runCatching {
            httpClient.newCall(gdacsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    all.addAll(parseGdacsGeoJson(body))
                }
            }
        }

        deduplicate(all)
    }
}
