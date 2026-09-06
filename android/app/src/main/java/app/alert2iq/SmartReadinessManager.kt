package app.alert2iq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReadinessSupplyItem(
    val id: String,
    val name: String,
    val category: String, // z.B. "WATER", "FOOD", "MEDICINE", "BATTERY"
    val expiryTimestampMs: Long,
    val quantity: Int = 1
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = expiryTimestampMs in 1 until nowMs
    fun isExpiringSoon(daysThreshold: Int = 30, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (expiryTimestampMs <= 0) return false
        val diffDays = (expiryTimestampMs - nowMs) / (1000L * 60 * 60 * 24)
        return diffDays in 0..daysThreshold
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("expiryTimestampMs", expiryTimestampMs)
        put("quantity", quantity)
    }

    companion object {
        fun fromJson(json: JSONObject): ReadinessSupplyItem = ReadinessSupplyItem(
            id = json.getString("id"),
            name = json.getString("name"),
            category = json.optString("category", "OTHER"),
            expiryTimestampMs = json.optLong("expiryTimestampMs", 0L),
            quantity = json.optInt("quantity", 1)
        )
    }
}

object SmartReadinessManager {

    private const val PREFS_FILE = "alert2iq_supplies"
    private const val KEY_ITEMS = "supply_items"

    fun getItems(context: Context): List<ReadinessSupplyItem> {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ReadinessSupplyItem>()
            for (i in 0 until array.length()) {
                list.add(ReadinessSupplyItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addItem(context: Context, item: ReadinessSupplyItem) {
        val current = getItems(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        saveItems(context, current)
    }

    fun removeItem(context: Context, itemId: String) {
        val current = getItems(context).toMutableList()
        if (current.removeAll { it.id == itemId }) {
            saveItems(context, current)
        }
    }

    fun saveItems(context: Context, items: List<ReadinessSupplyItem>) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
