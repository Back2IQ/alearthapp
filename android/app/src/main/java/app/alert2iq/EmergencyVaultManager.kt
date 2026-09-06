package app.alert2iq

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

data class EmergencyProfile(
    val fullName: String = "",
    val age: Int = 0,
    val gender: String = "",
    val bloodType: String = "",
    val chronicDiseases: String = "",
    val emergencyMedications: String = "",
    val allergies: String = "",
    val emergencyContactsSummary: String = "",
    val passportOrIdNumber: String = "",
    val broadcastMedicalData: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fullName", fullName)
        put("age", age)
        put("gender", gender)
        put("bloodType", bloodType)
        put("chronicDiseases", chronicDiseases)
        put("emergencyMedications", emergencyMedications)
        put("allergies", allergies)
        put("emergencyContactsSummary", emergencyContactsSummary)
        put("passportOrIdNumber", passportOrIdNumber)
        put("broadcastMedicalData", broadcastMedicalData)
    }

    companion object {
        fun fromJson(json: JSONObject): EmergencyProfile = EmergencyProfile(
            fullName = json.optString("fullName", ""),
            age = json.optInt("age", 0),
            gender = json.optString("gender", ""),
            bloodType = json.optString("bloodType", ""),
            chronicDiseases = json.optString("chronicDiseases", ""),
            emergencyMedications = json.optString("emergencyMedications", ""),
            allergies = json.optString("allergies", ""),
            emergencyContactsSummary = json.optString("emergencyContactsSummary", ""),
            passportOrIdNumber = json.optString("passportOrIdNumber", ""),
            broadcastMedicalData = json.optBoolean("broadcastMedicalData", true)
        )
    }
}

/**
 * Verschlüsselter Offline-Notfalltresor (AES-GCM-256).
 * Schützt sensible Notfalldaten und stellt sie im Katastrophenfall 100% offline bereit.
 */
object EmergencyVaultManager {

    private const val PREFS_FILE = "alert2iq_vault"
    private const val KEY_ENCRYPTED_DATA = "vault_payload"
    private const val KEY_IV = "vault_iv"
    private const val KEY_VAULT_KEY = "vault_master_key"

    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        var keyBase64 = prefs.getString(KEY_VAULT_KEY, null)
        if (keyBase64 == null) {
            val keyBytes = ByteArray(32) // 256 Bit
            SecureRandom().nextBytes(keyBytes)
            keyBase64 = Base64.getEncoder().encodeToString(keyBytes)
            prefs.edit().putString(KEY_VAULT_KEY, keyBase64).apply()
        }
        val raw = Base64.getDecoder().decode(keyBase64)
        return SecretKeySpec(raw, "AES")
    }

    fun saveProfile(context: Context, profile: EmergencyProfile): Boolean {
        return try {
            val key = getOrCreateKey(context)
            val jsonBytes = profile.toJson().toString().toByteArray(StandardCharsets.UTF_8)

            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val cipherText = cipher.doFinal(jsonBytes)

            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ENCRYPTED_DATA, Base64.getEncoder().encodeToString(cipherText))
                .putString(KEY_IV, Base64.getEncoder().encodeToString(iv))
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadProfile(context: Context): EmergencyProfile {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_DATA, null) ?: return EmergencyProfile()
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return EmergencyProfile()

        return try {
            val key = getOrCreateKey(context)
            val cipherText = Base64.getDecoder().decode(encryptedBase64)
            val iv = Base64.getDecoder().decode(ivBase64)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plainBytes = cipher.doFinal(cipherText)
            val json = JSONObject(String(plainBytes, StandardCharsets.UTF_8))
            EmergencyProfile.fromJson(json)
        } catch (_: Exception) {
            EmergencyProfile()
        }
    }
}
