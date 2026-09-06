package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalP2PMeshTest {

    @Test
    fun `pack and unpack 64-byte binary alert roundtrip`() {
        val geohash = "sq93jk"
        val mag = 6.4
        val ts = System.currentTimeMillis()
        val devId = "dev-salt-99812"

        val payload = LocalP2PMesh.packAlertPayload(geohash, mag, ts, devId)
        assertEquals(64, payload.size)

        val unpacked = LocalP2PMesh.unpackAlertPayload(payload)
        assertNotNull(unpacked)
        assertEquals(geohash, unpacked!!.geohash)
        assertEquals(mag, unpacked.magnitude, 0.001)
        assertEquals(ts, unpacked.timestampMs)
        assertEquals(devId, unpacked.deviceIdHash)
    }

    @Test
    fun `invalid magic bytes return null`() {
        val corrupted = ByteArray(64) { 0x00 }
        val unpacked = LocalP2PMesh.unpackAlertPayload(corrupted)
        assertEquals(null, unpacked)
    }
}
