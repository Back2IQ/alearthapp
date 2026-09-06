package app.alert2iq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MonitoredLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isPrimary: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
        put("isPrimary", isPrimary)
    }

    companion object {
        fun fromJson(json: JSONObject): MonitoredLocation = MonitoredLocation(
            id = json.getString("id"),
            name = json.getString("name"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            isPrimary = json.optBoolean("isPrimary", false)
        )
    }
}

object MultiLocationManager {

    private const val PREFS_FILE = "alert2iq_locations"
    private const val KEY_LOCATIONS = "saved_locations"

    fun getLocations(context: Context): List<MonitoredLocation> {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LOCATIONS, null) ?: return listOf(defaultLocation(context))
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<MonitoredLocation>()
            for (i in 0 until array.length()) {
                list.add(MonitoredLocation.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) listOf(defaultLocation(context)) else list
        } catch (_: Exception) {
            listOf(defaultLocation(context))
        }
    }

    fun addLocation(context: Context, location: MonitoredLocation): Boolean {
        val current = getLocations(context).toMutableList()
        val tier = TierSecurityManager.getActiveTier(context)
        if (current.size >= tier.quotaLocations) {
            return false // Quota erreicht
        }
        current.removeAll { it.id == location.id }
        current.add(location)
        saveLocations(context, current)
        return true
    }

    fun removeLocation(context: Context, locationId: String): Boolean {
        val current = getLocations(context).toMutableList()
        val removed = current.removeAll { it.id == locationId && !it.isPrimary }
        if (removed) {
            saveLocations(context, current)
        }
        return removed
    }

    fun saveLocations(context: Context, locations: List<MonitoredLocation>) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val array = JSONArray()
        locations.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_LOCATIONS, array.toString()).apply()
    }

    private fun defaultLocation(context: Context): MonitoredLocation {
        val city = Prefs.selectedCity()
        return MonitoredLocation(
            id = "default_primary",
            name = city.displayName,
            latitude = city.lat,
            longitude = city.lon,
            isPrimary = true
        )
    }
}
