package ai.openonion.messages

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.openonion.messages.protocol.SmsEncryptor
import ai.openonion.messages.protocol.SmsPlaintext
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsProtocolInstrumentedTest {
    @Test
    fun pythonVectorDecryptsAndKotlinCiphertextRoundTrips() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val vector = JSONObject(
            context.assets.open("sms_protocol_v1.json").bufferedReader().use { it.readText() },
        )
        val sodium = LazySodiumAndroid(SodiumAndroid())
        val seed = vector.getString("seed_hex").hexToBytes()
        val ed25519 = sodium.cryptoSignSeedKeypair(seed)
        val curve25519 = sodium.convertKeyPairEd25519ToCurve25519(ed25519)

        assertEquals(vector.getString("curve25519_public_hex"), curve25519.publicKey.asBytes.toHex())

        val fixedCiphertext = Base64.decode(vector.getString("ciphertext_base64"), Base64.NO_WRAP)
        val fixedPlaintext = ByteArray(fixedCiphertext.size - Box.SEALBYTES)
        check(
            sodium.cryptoBoxSealOpen(
                fixedPlaintext,
                fixedCiphertext,
                fixedCiphertext.size.toLong(),
                curve25519.publicKey.asBytes,
                curve25519.secretKey.asBytes,
            ),
        )
        assertEquals(vector.getString("plaintext_utf8"), fixedPlaintext.decodeToString())

        val encrypted = SmsEncryptor(sodium).encrypt(
            vector.getString("address"),
            SmsPlaintext(
                sender = "+61412345678",
                body = "Your code is 123456",
                receivedAt = "2026-09-01T02:00:00Z",
                subscriptionId = 1,
            ),
        )
        val ciphertext = Base64.decode(encrypted.ciphertext, Base64.NO_WRAP)
        val plaintext = ByteArray(ciphertext.size - Box.SEALBYTES)
        check(
            sodium.cryptoBoxSealOpen(
                plaintext,
                ciphertext,
                ciphertext.size.toLong(),
                curve25519.publicKey.asBytes,
                curve25519.secretKey.asBytes,
            ),
        )
        assertArrayEquals(vector.getString("plaintext_utf8").encodeToByteArray(), plaintext)
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
