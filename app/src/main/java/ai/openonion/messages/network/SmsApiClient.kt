package ai.openonion.messages.network

import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.DeviceIdentity
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.protocol.PROTOCOL_VERSION
import ai.openonion.messages.protocol.SEALED_BOX_ALGORITHM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class PairingClaimRequest(
    val recipient: String,
    val token: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class PairingClaimResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
    val recipient: String,
)

@Serializable
private data class DeviceClaimProof(
    @SerialName("device_public_key") val devicePublicKey: String,
    val grant: String,
    val purpose: String = "openonion-sms-device-claim",
    val version: Int = 2,
)

@Serializable
data class SignedPairingClaimRequest(
    @SerialName("pairing_id") val pairingId: String,
    val recipient: String,
    val nonce: String,
    @SerialName("expires_at") val expiresAt: Long,
    val signature: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("device_signature") val deviceSignature: String,
)

@Serializable
data class PendingPairingClaim(
    @SerialName("pairing_id") val pairingId: String,
    @SerialName("device_id") val deviceId: String,
    val recipient: String,
    @SerialName("claim_token") val claimToken: String,
    @SerialName("confirmation_code") val confirmationCode: String,
    val status: String,
)

@Serializable
private data class ActivationRequest(@SerialName("claim_token") val claimToken: String)

@Serializable
data class ActivationResponse(
    val status: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_token") val deviceToken: String? = null,
    val recipient: String? = null,
)

@Serializable
data class DeliveryRequest(
    val version: Int = PROTOCOL_VERSION,
    val recipient: String,
    @SerialName("message_id") val messageId: String,
    val algorithm: String = SEALED_BOX_ALGORITHM,
    val ciphertext: String,
)

@Serializable
data class DeliveryResponse(
    val id: String,
    val duplicate: Boolean,
    @SerialName("stored_at") val storedAt: String,
)

class SmsApiException(val code: String, message: String) : IOException(message)

class SmsApiClient(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val baseUrl = baseUrl.trimEnd('/')

    suspend fun claimPairing(request: PairingClaimRequest): PairingCredentials = withContext(Dispatchers.IO) {
        val response = post("/api/v1/sms/pairings/claim", json.encodeToString(request))
        response.use {
            val body = it.body?.string().orEmpty()
            requireSuccess(it.code)
            val claim = json.decodeFromString<PairingClaimResponse>(body)
            PairingCredentials(claim.recipient, claim.deviceId, claim.deviceToken)
        }
    }

    suspend fun claimSignedPairing(
        link: PairingLink,
        deviceIdentity: DeviceIdentity,
        deviceName: String,
        appVersion: String,
    ): PendingPairingClaim = withContext(Dispatchers.IO) {
        link.verifyAgentSignature()
        val publicKey = deviceIdentity.publicKeyBase64
        val proof = json.encodeToString(
            DeviceClaimProof(devicePublicKey = publicKey, grant = link.canonicalGrant()),
        )
        val request = SignedPairingClaimRequest(
            pairingId = link.pairingId,
            recipient = link.recipient,
            nonce = link.nonce,
            expiresAt = link.expiresAt,
            signature = link.signature,
            deviceName = deviceName,
            appVersion = appVersion,
            devicePublicKey = publicKey,
            deviceSignature = deviceIdentity.sign(proof),
        )
        val response = post("/api/v1/sms/pairings/v2/claim", json.encodeToString(request))
        response.use {
            val body = it.body?.string().orEmpty()
            requireSuccess(it.code)
            json.decodeFromString(body)
        }
    }

    suspend fun activateSignedPairing(claimToken: String): ActivationResponse =
        withContext(Dispatchers.IO) {
            val response = post(
                "/api/v1/sms/pairings/v2/activate",
                json.encodeToString(ActivationRequest(claimToken)),
            )
            response.use {
                val body = it.body?.string().orEmpty()
                requireSuccess(it.code)
                json.decodeFromString(body)
            }
        }

    suspend fun deliver(
        credentials: PairingCredentials,
        delivery: QueuedDelivery,
    ): DeliveryResponse = withContext(Dispatchers.IO) {
        val body = DeliveryRequest(
            recipient = delivery.recipient,
            messageId = delivery.messageId,
            ciphertext = delivery.ciphertext,
        )
        val response = post(
            path = "/api/v1/sms/messages",
            jsonBody = json.encodeToString(body),
            bearerToken = credentials.deviceToken,
        )
        response.use {
            val responseBody = it.body?.string().orEmpty()
            requireSuccess(it.code)
            json.decodeFromString(responseBody)
        }
    }

    suspend fun revokeCurrentDevice(credentials: PairingCredentials) = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url(baseUrl + "/api/v1/sms/devices/current")
                .delete()
                .header("Authorization", "Bearer ${credentials.deviceToken}")
                .build(),
        ).execute()
        response.use { requireSuccess(it.code) }
    }

    suspend fun deleteDeliveredMessage(
        credentials: PairingCredentials,
        serverMessageId: String,
    ) = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url(baseUrl + "/api/v1/sms/device/messages/$serverMessageId")
                .delete()
                .header("Authorization", "Bearer ${credentials.deviceToken}")
                .build(),
        ).execute()
        response.use { requireSuccess(it.code) }
    }

    private fun post(path: String, jsonBody: String, bearerToken: String? = null) =
        client.newCall(
            Request.Builder()
                .url(baseUrl + path)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    if (bearerToken != null) header("Authorization", "Bearer $bearerToken")
                }
                .build(),
        ).execute()

    private fun requireSuccess(status: Int) {
        if (status in 200..299) return
        val code = when (status) {
            401 -> "device_unauthorized"
            404 -> "not_found"
            409 -> "conflict"
            429 -> "rate_limited"
            in 500..599 -> "server_unavailable"
            else -> "request_rejected"
        }
        throw SmsApiException(code, "oo-api returned HTTP $status")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
