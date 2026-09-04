package app.alearthapp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

enum class BleSosStatus(val code: Byte) {
    TRAPPED(1),
    INJURED(2),
    OK(3);

    companion object {
        fun fromCode(code: Byte): BleSosStatus = values().find { it.code == code } ?: TRAPPED
    }
}

data class BleSosMessage(
    val status: BleSosStatus,
    val batteryPercent: Int,
    val timestampSec: Long,
    val coarseLat: Float = 0.0f,
    val coarseLon: Float = 0.0f
) {
    companion object {
        const val MAGIC: Short = 0xAE01.toShort()
        const val PAYLOAD_SIZE: Int = 16 // 2 magic + 1 status + 1 battery + 4 ts + 4 lat + 4 lon

        fun encode(msg: BleSosMessage): ByteArray {
            val buf = ByteBuffer.allocate(PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(MAGIC)
            buf.put(msg.status.code)
            buf.put(msg.batteryPercent.coerceIn(0, 100).toByte())
            buf.putInt((msg.timestampSec and 0xFFFFFFFFL).toInt())
            buf.putFloat(msg.coarseLat)
            buf.putFloat(msg.coarseLon)
            return buf.array()
        }

        fun decode(bytes: ByteArray?): BleSosMessage? {
            if (bytes == null || bytes.size < PAYLOAD_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.short
            if (magic != MAGIC) return null

            val status = BleSosStatus.fromCode(buf.get())
            val battery = buf.get().toInt() and 0xFF
            val ts = buf.int.toLong() and 0xFFFFFFFFL
            val lat = buf.float
            val lon = buf.float

            return BleSosMessage(
                status = status,
                batteryPercent = battery,
                timestampSec = ts,
                coarseLat = lat,
                coarseLon = lon
            )
        }

        /**
         * Schätzt die Distanz in Metern basierend auf dem BLE RSSI-Wert (Log-Distance Path Loss Model).
         */
        fun estimateDistanceMeters(rssi: Int, txPower: Int = -59): Double {
            if (rssi == 0) return -1.0
            val ratio = rssi * 1.0 / txPower
            return if (ratio < 1.0) {
                ratio.pow(10.0)
            } else {
                (0.89976) * ratio.pow(7.7095) + 0.111
            }
        }
    }

    /**
     * Anti-Spoofing & Replay-Schutz: Prüft, ob der Zeitstempel innerhalb des Toleranzfensters liegt.
     * Pakete älter als maxDeltaSec oder aus der Zukunft werden verworfen.
     */
    fun isValidTimestamp(nowSec: Long = System.currentTimeMillis() / 1000L, maxDeltaSec: Long = 120L): Boolean {
        val diff = kotlin.math.abs(nowSec - timestampSec)
        return diff <= maxDeltaSec
    }
}
