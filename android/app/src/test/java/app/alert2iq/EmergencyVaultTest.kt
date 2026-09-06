package app.alert2iq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyVaultTest {

    @Test
    fun testDefaultProfileStrictOptIn() {
        val defaultProfile = EmergencyProfile()
        // Datenschutz-Garantie: Standardmäßig KEIN Broadcast von medizinischen Daten (Opt-In)
        assertFalse(defaultProfile.broadcastMedicalData)
        assertEquals("", defaultProfile.fullName)
        assertEquals(0, defaultProfile.age)
        assertEquals("", defaultProfile.bloodType)
        assertEquals("", defaultProfile.chronicDiseases)
    }

    @Test
    fun testProfileJsonSerializationRoundtrip() {
        val profile = EmergencyProfile(
            fullName = "Deniz Kiran",
            age = 34,
            gender = "M",
            bloodType = "A+",
            chronicDiseases = "Diabetes / Insulin, Asthma",
            emergencyMedications = "Ventolin, Lantus",
            allergies = "Penicillin",
            emergencyContactsSummary = "Family: +49170123456",
            passportOrIdNumber = "TR12345678",
            broadcastMedicalData = true
        )

        val json = profile.toJson()
        val loaded = EmergencyProfile.fromJson(json)

        assertEquals(profile.fullName, loaded.fullName)
        assertEquals(profile.age, loaded.age)
        assertEquals(profile.gender, loaded.gender)
        assertEquals(profile.bloodType, loaded.bloodType)
        assertEquals(profile.chronicDiseases, loaded.chronicDiseases)
        assertEquals(profile.emergencyMedications, loaded.emergencyMedications)
        assertEquals(profile.allergies, loaded.allergies)
        assertTrue(loaded.broadcastMedicalData)
    }

    @Test
    fun testBleTriageMaskingWhenOptedOut() {
        val profile = EmergencyProfile(
            fullName = "Max Mustermann",
            age = 45,
            gender = "M",
            bloodType = "0-",
            chronicDiseases = "Herzinsuffizienz, Asthma",
            broadcastMedicalData = false // Nutzer hat Broadcast NICHT aktiviert
        )

        // Wenn broadcastMedicalData = false, dürfen im BLE-Payload KEINE Daten landen
        val triage = if (profile.broadcastMedicalData) {
            BleTriageProfile(
                bloodType = BleBloodType.fromString(profile.bloodType),
                gender = BleGender.fromString(profile.gender),
                age = profile.age,
                isUserResponsive = true,
                hasInsulinDiabetes = profile.chronicDiseases.contains("insulin", ignoreCase = true),
                hasHeartCondition = profile.chronicDiseases.contains("herz", ignoreCase = true),
                hasRespiratoryRisk = profile.chronicDiseases.contains("asthma", ignoreCase = true),
                nameInitials = "M.M."
            )
        } else {
            BleTriageProfile(isUserResponsive = true)
        }

        assertEquals(BleBloodType.UNKNOWN, triage.bloodType)
        assertEquals(BleGender.UNKNOWN, triage.gender)
        assertEquals(0, triage.age)
        assertFalse(triage.hasInsulinDiabetes)
        assertFalse(triage.hasHeartCondition)
        assertFalse(triage.hasRespiratoryRisk)
        assertEquals("", triage.nameInitials)
    }
}