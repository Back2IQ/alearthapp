package app.alearthapp

import java.util.LinkedHashMap

/**
 * Verhindert redundante Alarme bei wiederholten FCM-Zustellungen und verwaltet
 * Event-Updates anhand von Event-ID und Versionsnummer (ver).
 */
object AlertDeduplicator {

    enum class Action {
        PROCESS,      // Neuer Alarm oder signifikantes Upgrade -> voll ausführen
        UPDATE_ONLY,  // Nur Parameter-Update (bereits alarmiert)
        IGNORE        // Duplikat oder veraltete Version -> ignorieren
    }

    private data class Entry(
        val ver: Int,
        val level: PushEval.Level,
        val timestamp: Long
    )

    private const val MAX_ENTRIES = 100
    private const val TTL_MILLIS = 15 * 60 * 1000L // 15 Minuten

    private val cache = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun evaluate(
        id: String,
        ver: Int,
        level: PushEval.Level,
        now: Long = System.currentTimeMillis()
    ): Action {
        if (id.isBlank()) return Action.PROCESS

        // Abgelaufene Einträge bereinigen
        cache.entries.removeIf { now - it.value.timestamp > TTL_MILLIS }

        val existing = cache[id]
        if (existing == null) {
            cache[id] = Entry(ver, level, now)
            return Action.PROCESS
        }

        // Veraltete Version eingetroffen?
        if (ver < existing.ver) {
            return Action.IGNORE
        }

        // Gleiche Version -> reines Duplikat
        if (ver == existing.ver) {
            return Action.IGNORE
        }

        // Höhere Version (ver > existing.ver):
        val isUpgrade = existing.level != PushEval.Level.ALARM && level == PushEval.Level.ALARM
        cache[id] = Entry(ver, level, now)

        return if (isUpgrade) Action.PROCESS else Action.UPDATE_ONLY
    }

    @Synchronized
    fun reset() {
        cache.clear()
    }
}