package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePayloadTest {

    @Test
    fun testEncodeDecodeRoundtrip() {
        val original = BleSosMessage(
            status = BleSosStatus.TRAPPED,
            batteryPercent = 78,
            timestampSec = 1772000000L,
            coarseLat = 38.3541f,
            coarseLon = 38.3092f
        )

        val encoded = BleSosMessage.encode(original)
        assertEquals(BleSosMessage.PAYLOAD_SIZE, encoded.size)

        val decoded = BleSosMessage.decode(encoded)
        assertNotNull(decoded)
        assertEquals(BleSosStatus.TRAPPED, decoded!!.status)
        assertEquals(78, decoded.batteryPercent)
        assertEquals(1772000000L, decoded.timestampSec)
        assertEquals(38.3541f, decoded.coarseLat, 0.0001f)
        assertEquals(38.3092f, decoded.coarseLon, 0.0001f)
    }

    @Test
    fun testInvalidMagicReturnsNull() {
        val bytes = ByteArray(BleSosMessage.PAYLOAD_SIZE) { 0 }
        val decoded = BleSosMessage.decode(bytes)
        assertNull(decoded)
    }

    @Test
    fun testTooShortBytesReturnsNull() {
        val bytes = ByteArray(8) { 0 }
        val decoded = BleSosMessage.decode(bytes)
        assertNull(decoded)
    }

    @Test
    fun testDistanceEstimation() {
        // Starkes Signal (-50 dBm) -> Nahbereich (< 2m)
        val near = BleSosMessage.estimateDistanceMeters(-50, -59)
        assertTrue(near in 0.1..2.5)

        // Schwaches Signal (-85 dBm) -> Weiter entfernt
        val far = BleSosMessage.estimateDistanceMeters(-85, -59)
        assertTrue(far > near)
    }
}
