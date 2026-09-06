package app.alert2iq

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class AppTier(val quotaLocations: Int, val hasVault: Boolean, val hasAutoSms: Boolean, val hasMultiHazardPush: Boolean) {
    FREE(quotaLocations = 1, hasVault = false, hasAutoSms = false, hasMultiHazardPush = false),
    PRO(quotaLocations = 5, hasVault = true, hasAutoSms = false, hasMultiHazardPush = true),
    GUARDIAN(quotaLocations = 10, hasVault = true, hasAutoSms = true, hasMultiHazardPush = true)
}

/**
 * Back2IQ Global Developer Rule 4:
 * Local storage tier & quota states protected via HMAC SHA-256 checksums.
 * Any manual manipulation or invalid signature safely resets the state to FREE tier.
 */
object TierSecurityManager {

    private const val PREFS_FILE = "alert2iq_tier_store"
    private const val KEY_TIER = "active_tier"
    private const val KEY_EXPIRES_TS = "tier_expires_ts"
    private const val KEY_DEVICE_SALT = "device_salt"
    private const val KEY_CHECKSUM = "tier_hmac_sha256"

    private val HMAC_SECRET_KEY = "Back2IQ-Alert2IQ-AntiTamper-SigningSecret-2026".toByteArray(StandardCharsets.UTF_8)

    fun calculateHmacSha256(data: String, key: ByteArray = HMAC_SECRET_KEY): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        hmac.init(secretKeySpec)
        val hash = hmac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    private fun getOrCreateDeviceSalt(prefs: SharedPreferences): String {
        var salt = prefs.getString(KEY_DEVICE_SALT, null)
        if (salt == null) {
            salt = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_SALT, salt).apply()
        }
        return salt
    }

    fun getActiveTier(context: Context): AppTier {
        val prefs = getPrefs(context)
        val tierStr = prefs.getString(KEY_TIER, AppTier.FREE.name) ?: AppTier.FREE.name
        val expiresTs = prefs.getLong(KEY_EXPIRES_TS, 0L)
        val checksum = prefs.getString(KEY_CHECKSUM, "") ?: ""
        val salt = getOrCreateDeviceSalt(prefs)

        if (tierStr == AppTier.FREE.name) {
            return AppTier.FREE
        }

        val now = System.currentTimeMillis()
        if (expiresTs in 1 until now) {
            resetToFree(context)
            return AppTier.FREE
        }

        val expectedData = "$tierStr:$expiresTs:$salt"
        val expectedChecksum = calculateHmacSha256(expectedData)

        if (checksum != expectedChecksum) {
            resetToFree(context)
            return AppTier.FREE
        }

        return try {
            AppTier.valueOf(tierStr)
        } catch (_: Exception) {
            resetToFree(context)
            AppTier.FREE
        }
    }

    fun setTier(context: Context, tier: AppTier, expiresTs: Long = 0L) {
        val prefs = getPrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)

        if (tier == AppTier.FREE) {
            resetToFree(context)
            return
        }

        val dataToSign = "${tier.name}:$expiresTs:$salt"
        val checksum = calculateHmacSha256(dataToSign)

        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putLong(KEY_EXPIRES_TS, expiresTs)
            .putString(KEY_CHECKSUM, checksum)
            .apply()
    }

    fun resetToFree(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_TIER, AppTier.FREE.name)
            .putLong(KEY_EXPIRES_TS, 0L)
            .remove(KEY_CHECKSUM)
            .apply()
    }
}
