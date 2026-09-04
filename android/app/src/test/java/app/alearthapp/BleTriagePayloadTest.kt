package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleTriagePayloadTest {

    @Test
    fun testTriageEncodingAndDecoding() {
        val triage = BleTriageProfile(
            bloodType = BleBloodType.A_POS,
            gender = BleGender.MALE,
            age = 34,
            isUserResponsive = true,
            hasInsulinDiabetes = true,
            hasHeartCondition = false,
            hasRespiratoryRisk = true,
            nameInitials = "D.K."
        )

        val msg = BleSosMessage(
            status = BleSosStatus.TRAPPED,
            batteryPercent = 88,
            timestampSec = 1700000000L,
            coarseLat = 37.05f,
            coarseLon = 37.35f,
            triage = triage
        )

        val encoded = BleSosMessage.encode(msg)
        assertEquals(BleSosMessage.EXTENDED_PAYLOAD_SIZE, encoded.size)

        val decoded = BleSosMessage.decode(encoded)
        assertNotNull(decoded)
        assertEquals(BleSosStatus.TRAPPED, decoded?.status)
        assertEquals(88, decoded?.batteryPercent)
        assertEquals(37.05f, decoded?.coarseLat ?: 0f, 0.001f)

        val t = decoded?.triage
        assertNotNull(t)
        assertEquals(BleBloodType.A_POS, t?.bloodType)
        assertEquals(BleGender.MALE, t?.gender)
        assertEquals(34, t?.age)
        assertTrue(t?.isUserResponsive == true)
        assertTrue(t?.hasInsulinDiabetes == true)
        assertFalse(t?.hasHeartCondition == true)
        assertTrue(t?.hasRespiratoryRisk == true)
        assertEquals("D.K.", t?.nameInitials)
    }

    @Test
    fun testLegacyPayloadCompatibility() {
        // Altes 16-Byte Paket ohne Triage
        val legacy = ByteArray(16)
        legacy[0] = 0x01
        legacy[1] = 0xAE.toByte() // Magic
        legacy[2] = 1 // TRAPPED
        legacy[3] = 50 // Batt

        val decoded = BleSosMessage.decode(legacy)
        assertNotNull(decoded)
        assertEquals(BleBloodType.UNKNOWN, decoded?.triage?.bloodType)
        assertEquals(0, decoded?.triage?.age)
        assertFalse(decoded?.triage?.isUserResponsive == true)
    }
}
