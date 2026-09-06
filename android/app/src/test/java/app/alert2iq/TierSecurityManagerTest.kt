package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierSecurityManagerTest {

    @Test
    fun testHmacCalculationConsistency() {
        val data = "PRO:1799999999:device_salt_123"
        val hash1 = TierSecurityManager.calculateHmacSha256(data)
        val hash2 = TierSecurityManager.calculateHmacSha256(data)
        assertEquals(hash1, hash2)
        assertTrue(hash1.isNotBlank())
    }

    @Test
    fun testHmacDetectsTampering() {
        val originalData = "PRO:1799999999:device_salt_123"
        val tamperedData = "GUARDIAN:1799999999:device_salt_123"
        val originalHash = TierSecurityManager.calculateHmacSha256(originalData)
        val tamperedHash = TierSecurityManager.calculateHmacSha256(tamperedData)
        assertFalse(originalHash == tamperedHash)
    }

    @Test
    fun testTierQuotas() {
        assertEquals(1, AppTier.FREE.quotaLocations)
        assertEquals(5, AppTier.PRO.quotaLocations)
        assertEquals(10, AppTier.GUARDIAN.quotaLocations)

        assertFalse(AppTier.FREE.hasVault)
        assertTrue(AppTier.PRO.hasVault)
        assertTrue(AppTier.GUARDIAN.hasVault)

        assertFalse(AppTier.FREE.hasAutoSms)
        assertFalse(AppTier.PRO.hasAutoSms)
        assertTrue(AppTier.GUARDIAN.hasAutoSms)
    }
}
