package ai.openonion.messages.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairingCredentials(
    val recipient: String,
    val deviceId: String,
    val deviceToken: String,
)

/** Keeps the revocable device bearer token encrypted by an Android Keystore key. */
class PairingStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): PairingCredentials? {
        val encoded = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            require(blob.size > IV_BYTES) { "Encrypted pairing data is truncated" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
            )
            val json = JSONObject(
                cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)).decodeToString(),
            )
            PairingCredentials(
                recipient = json.getString("recipient"),
                deviceId = json.getString("device_id"),
                deviceToken = json.getString("device_token"),
            )
        } catch (_: GeneralSecurityException) {
            clearUnreadableCredential()
            null
        } catch (_: RuntimeException) {
            clearUnreadableCredential()
            null
        }
    }

    fun save(credentials: PairingCredentials) {
        val plaintext = JSONObject()
            .put("recipient", credentials.recipient)
            .put("device_id", credentials.deviceId)
            .put("device_token", credentials.deviceToken)
            .toString()
            .encodeToByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val blob = cipher.iv + cipher.doFinal(plaintext)
        check(
            preferences.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(blob, Base64.NO_WRAP))
                .commit(),
        ) { "Could not persist pairing credentials" }
    }

    fun clear() {
        check(preferences.edit().remove(KEY_CIPHERTEXT).commit()) {
            "Could not clear pairing credentials"
        }
    }

    @Synchronized
    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun clearUnreadableCredential() {
        preferences.edit().remove(KEY_CIPHERTEXT).commit()
    }

    private companion object {
        const val FILE_NAME = "pairing-credentials"
        const val KEY_CIPHERTEXT = "encrypted-pairing"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "openonion-messages-pairing-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
