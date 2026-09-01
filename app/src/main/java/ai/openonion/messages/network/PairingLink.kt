package ai.openonion.messages.network

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class PairingLink(
    val recipient: String,
    val token: String = "",
    val version: Int = 1,
    val pairingId: String = "",
    val nonce: String = "",
    val expiresAt: Long = 0,
    val signature: String = "",
) {
    val isSignedChallenge: Boolean get() = version == SIGNED_PAIRING_VERSION

    fun canonicalGrant(): String {
        require(isSignedChallenge) { "This pairing link does not contain a signed challenge" }
        return "{" +
            "\"expires_at\":$expiresAt," +
            "\"nonce\":\"$nonce\"," +
            "\"pairing_id\":\"$pairingId\"," +
            "\"purpose\":\"openonion-sms-pair\"," +
            "\"recipient\":\"${recipient.lowercase()}\"," +
            "\"version\":$SIGNED_PAIRING_VERSION}"
    }

    fun verifyAgentSignature(
        sodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid()),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ) {
        require(isSignedChallenge) { "Unsupported SMS pairing protocol" }
        require(expiresAt > nowEpochSeconds) { "Pairing challenge has expired" }
        require(expiresAt - nowEpochSeconds <= MAX_PAIRING_LIFETIME_SECONDS) {
            "Pairing challenge expiry is outside the allowed window"
        }
        val publicKey = recipient.removePrefix("0x").hexToBytes()
        val detachedSignature = signature.hexToBytes()
        val message = canonicalGrant().encodeToByteArray()
        require(publicKey.size == 32 && detachedSignature.size == 64) {
            "Pairing challenge has invalid key material"
        }
        require(
            sodium.cryptoSignVerifyDetached(
                detachedSignature,
                message,
                message.size,
                publicKey,
            ),
        ) { "Pairing challenge signature is invalid" }
    }

    fun confirmationCode(devicePublicKeyBase64: String): String {
        val devicePublicKey = Base64.decode(devicePublicKeyBase64, Base64.DEFAULT)
        val digest = MessageDigest.getInstance("SHA-256").digest(
            canonicalGrant().encodeToByteArray() + byteArrayOf(0) + devicePublicKey,
        )
        val number = ((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)
        return (number % 1_000_000).toString().padStart(6, '0')
    }

    companion object {
        fun parse(value: String): PairingLink {
            val uri = URI(value.trim())
            require(uri.scheme == "openonion" && uri.host == "sms" && uri.path == "/pair") {
                "Expected an openonion://sms/pair link"
            }
            val query = uri.rawQuery.orEmpty()
                .split('&')
                .filter { it.isNotBlank() }
                .associate { item ->
                    val parts = item.split('=', limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }
            val recipient = query["recipient"].orEmpty()
            require(recipient.isNotBlank()) { "Pairing link is incomplete" }
            val version = query["v"]?.toIntOrNull() ?: 1
            if (version == 1) {
                val token = query["token"].orEmpty()
                require(token.isNotBlank()) { "Pairing link is incomplete" }
                return PairingLink(recipient = recipient, token = token)
            }
            require(version == SIGNED_PAIRING_VERSION) { "Unsupported SMS pairing protocol" }
            val pairingId = query["id"].orEmpty()
            val nonce = query["nonce"].orEmpty()
            val expiresAt = query["expires"]?.toLongOrNull() ?: 0
            val signature = query["signature"].orEmpty()
            require(
                pairingId.isNotBlank() && nonce.isNotBlank() &&
                    expiresAt > 0 && signature.isNotBlank()
            ) { "Pairing link is incomplete" }
            return PairingLink(
                recipient = recipient,
                version = version,
                pairingId = pairingId,
                nonce = nonce,
                expiresAt = expiresAt,
                signature = signature,
            )
        }

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        "Expected hexadecimal key material"
    }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private const val SIGNED_PAIRING_VERSION = 2
private const val MAX_PAIRING_LIFETIME_SECONDS = 1800
