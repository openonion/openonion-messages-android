package ai.openonion.messages.protocol

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val SEALED_BOX_ALGORITHM = "x25519-xsalsa20-poly1305-sealed-box"

@Serializable
data class SmsPlaintext(
    val schema: Int = PROTOCOL_VERSION,
    val sender: String,
    val body: String,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("subscription_id") val subscriptionId: Int? = null,
)

data class EncryptedSms(
    val algorithm: String = SEALED_BOX_ALGORITHM,
    val ciphertext: String,
)

class SmsEncryptor(
    private val sodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid()),
    private val json: Json = Json { encodeDefaults = true; explicitNulls = true },
) {
    fun encrypt(recipient: String, plaintext: SmsPlaintext): EncryptedSms {
        val ed25519Key = parseAddress(recipient)
        val curve25519Key = ByteArray(Box.PUBLICKEYBYTES)
        require(sodium.convertPublicKeyEd25519ToCurve25519(curve25519Key, ed25519Key)) {
            "Recipient address is not a valid Ed25519 public key"
        }

        val message = json.encodeToString(plaintext).encodeToByteArray()
        val ciphertext = ByteArray(message.size + Box.SEALBYTES)
        check(sodium.cryptoBoxSeal(ciphertext, message, message.size.toLong(), curve25519Key)) {
            "Could not encrypt SMS"
        }
        return EncryptedSms(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        )
    }

    companion object {
        fun parseAddress(address: String): ByteArray {
            require(address.startsWith("0x")) { "Agent address must start with 0x" }
            val hex = address.substring(2)
            require(hex.length == 64 && hex.all { it.isHexDigit() }) {
                "Agent address must contain exactly 32 bytes of hexadecimal public key data"
            }
            return ByteArray(32) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
