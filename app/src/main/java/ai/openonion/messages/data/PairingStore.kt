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

data class PendingPairingActivation(
    val claimToken: String,
    val expiresAt: Long,
    val confirmationCode: String,
)

/** Keeps the revocable device bearer token encrypted by an Android Keystore key. */
class PairingStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): PairingCredentials? {
        val encoded = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val json = decrypt(encoded)
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
        check(
            preferences.edit()
                .putString(KEY_CIPHERTEXT, encrypt(plaintext))
                .commit(),
        ) { "Could not persist pairing credentials" }
    }

    fun loadPending(): PendingPairingActivation? {
        val encoded = preferences.getString(KEY_PENDING_CIPHERTEXT, null) ?: return null
        return try {
            val json = decrypt(encoded)
            PendingPairingActivation(
                claimToken = json.getString("claim_token"),
                expiresAt = json.getLong("expires_at"),
                confirmationCode = json.getString("confirmation_code"),
            )
        } catch (_: GeneralSecurityException) {
            clearUnreadablePendingCredential()
            null
        } catch (_: RuntimeException) {
            clearUnreadablePendingCredential()
            null
        }
    }

    fun savePending(pending: PendingPairingActivation) {
        val plaintext = JSONObject()
            .put("claim_token", pending.claimToken)
            .put("expires_at", pending.expiresAt)
            .put("confirmation_code", pending.confirmationCode)
            .toString()
            .encodeToByteArray()
        check(
            preferences.edit()
                .putString(KEY_PENDING_CIPHERTEXT, encrypt(plaintext))
                .commit(),
        ) { "Could not persist pending pairing" }
    }

    fun clearPending() {
        check(preferences.edit().remove(KEY_PENDING_CIPHERTEXT).commit()) {
            "Could not clear pending pairing"
        }
    }

    fun clear() {
        check(
            preferences.edit()
                .remove(KEY_CIPHERTEXT)
                .remove(KEY_PENDING_CIPHERTEXT)
                .commit(),
        ) {
            "Could not clear pairing credentials"
        }
    }

    private fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val blob = cipher.iv + cipher.doFinal(plaintext)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): JSONObject {
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        require(blob.size > IV_BYTES) { "Encrypted pairing data is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
        )
        return JSONObject(cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)).decodeToString())
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

    private fun clearUnreadablePendingCredential() {
        preferences.edit().remove(KEY_PENDING_CIPHERTEXT).commit()
    }

    private companion object {
        const val FILE_NAME = "pairing-credentials"
        const val KEY_CIPHERTEXT = "encrypted-pairing"
        const val KEY_PENDING_CIPHERTEXT = "encrypted-pending-pairing"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "openonion-messages-pairing-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
