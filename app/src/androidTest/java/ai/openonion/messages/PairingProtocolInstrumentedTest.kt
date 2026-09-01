package ai.openonion.messages

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import ai.openonion.messages.network.PairingLink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class PairingProtocolInstrumentedTest {
    @Test
    fun sharedVectorVerifiesAgentGrantDeviceClaimAndConfirmationCode() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val vector = JSONObject(
            context.assets.open("sms_pairing_v2.json").bufferedReader().use { it.readText() },
        )
        val link = PairingLink.parse(
            "openonion://sms/pair" +
                "?v=${vector.getInt("version")}" +
                "&id=${vector.getString("pairing_id")}" +
                "&recipient=${vector.getString("recipient")}" +
                "&nonce=${vector.getString("nonce")}" +
                "&expires=${vector.getLong("expires_at")}" +
                "&signature=${vector.getString("agent_signature_hex")}",
        )

        link.verifyAgentSignature(nowEpochSeconds = vector.getLong("expires_at") - 600)
        assertEquals(vector.getString("grant_utf8"), link.canonicalGrant())
        assertEquals(
            vector.getString("confirmation_code"),
            link.confirmationCode(vector.getString("device_public_key_base64")),
        )

        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(
                Base64.decode(vector.getString("device_public_key_base64"), Base64.DEFAULT),
            ),
        )
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(vector.getString("device_claim_utf8").encodeToByteArray())
        }
        assertTrue(
            verifier.verify(Base64.decode(vector.getString("device_signature_base64"), Base64.DEFAULT)),
        )
    }
}
