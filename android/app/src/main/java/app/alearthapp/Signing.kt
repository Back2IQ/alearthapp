package app.alearthapp

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Ed25519 payload verification for the "Server-Verbindungsmodus" (spec §Server-
 * Verbindungsmodus). Mirrors the backend EXACTLY -- see
 * tda/server/src/tda_server/alert/payload.py `canonical_bytes` / `verify_payload`:
 * take all payload fields except "sig", sort the keys alphabetically, build
 * "key=value" lines, join with "\n" (no trailing newline), UTF-8 encode, then
 * verify the base64-decoded "sig" (Ed25519) against those bytes.
 *
 * SECURITY: the public key used here is embedded as a constant and must NEVER be
 * replaced by the "pub_key" the server announces in its "hello" message -- that
 * value is untrusted and for display/comparison only (a compromised or
 * man-in-the-middle server could announce any key it likes).
 */
object Signing {

    /** Embedded Ed25519 public key, 32 raw bytes, base64 -- matches the server's signing key. */
    const val SERVER_PUBLIC_KEY_B64 = "6kcriusWuSdd8wJ6IUXfYhasu7oN2LAIhW1HZGnaJhc="

    private val publicKeyParams: Ed25519PublicKeyParameters by lazy {
        Ed25519PublicKeyParameters(Base64.decode(SERVER_PUBLIC_KEY_B64, Base64.NO_WRAP), 0)
    }

    /**
     * Builds the exact canonical byte string the server signs: all keys of [payload]
     * except "sig", sorted alphabetically, as "key=value" lines joined with "\n"
     * (no trailing newline), UTF-8 encoded.
     */
    fun canonicalBytes(payload: Map<String, String>): ByteArray {
        val lines = payload.keys
            .filter { it != "sig" }
            .sorted()
            .map { key -> "$key=${payload.getValue(key)}" }
        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    /**
     * True iff `payload["sig"]` (base64) is a valid Ed25519 signature over
     * [canonicalBytes] of [payload], under the embedded [SERVER_PUBLIC_KEY_B64].
     * Never throws -- any malformed input (missing/invalid base64, wrong length,
     * bad signature) is treated as "not verified".
     */
    fun verify(payload: Map<String, String>): Boolean {
        val sigB64 = payload["sig"] ?: return false
        return try {
            val sig = Base64.decode(sigB64, Base64.NO_WRAP)
            val message = canonicalBytes(payload)
            val verifier = Ed25519Signer()
            verifier.init(false, publicKeyParams)
            verifier.update(message, 0, message.size)
            verifier.verifySignature(sig)
        } catch (_: Exception) {
            false
        }
    }
}
