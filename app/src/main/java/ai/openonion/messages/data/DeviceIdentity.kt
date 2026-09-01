package ai.openonion.messages.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Non-exportable Android Keystore identity used only to prove which phone was confirmed. */
class DeviceIdentity {
    val publicKeyBase64: String
        get() = Base64.encodeToString(loadOrCreate().public.encoded, Base64.NO_WRAP)

    fun sign(message: String): String {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(loadOrCreate().private)
        signer.update(message.encodeToByteArray())
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    @Synchronized
    private fun loadOrCreate(): KeyPair {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return KeyPair(existing.certificate.publicKey, existing.privateKey)

        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER).run {
            initialize(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "openonion-messages-device-pairing-v2"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
