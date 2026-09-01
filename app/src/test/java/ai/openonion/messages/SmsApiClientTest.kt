package ai.openonion.messages

import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.network.PairingClaimRequest
import ai.openonion.messages.network.SmsApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SmsApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun pairingClaimUsesThePublicContract() = runBlocking {
        val address = "0x" + "ab".repeat(32)
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"device_id":"dev-1","device_token":"sms_dev_secret","recipient":"$address"}""",
                ),
        )
        val client = SmsApiClient(server.url("/").toString())
        val credentials = client.claimPairing(
            PairingClaimRequest(address, "sms_pair_secret", "Pixel", appVersion = "1.0.0"),
        )

        assertEquals("sms_dev_secret", credentials.deviceToken)
        val request = server.takeRequest()
        assertEquals("/api/v1/sms/pairings/claim", request.path)
        val body = request.body.readUtf8()
        assert(body.contains("sms_pair_secret"))
        assert(body.contains("\"platform\":\"android\""))
    }

    @Test
    fun disconnectRevokesTheCurrentDeviceCredential() = runBlocking {
        val address = "0x" + "ab".repeat(32)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = SmsApiClient(server.url("/").toString())

        client.revokeCurrentDevice(
            ai.openonion.messages.data.PairingCredentials(
                recipient = address,
                deviceId = "device-1",
                deviceToken = "sms_dev_secret",
            ),
        )

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/sms/devices/current", request.path)
        assertEquals("Bearer sms_dev_secret", request.getHeader("Authorization"))
    }

    @Test
    fun deleteMessageUsesTheDeviceScopedEndpoint() = runBlocking {
        val address = "0x" + "ab".repeat(32)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = SmsApiClient(server.url("/").toString())

        client.deleteDeliveredMessage(
            PairingCredentials(address, "device-1", "sms_dev_secret"),
            "43c8412d-4b88-4df6-bf2a-75ce3a5a64ac",
        )

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/api/v1/sms/device/messages/43c8412d-4b88-4df6-bf2a-75ce3a5a64ac",
            request.path,
        )
        assertEquals("Bearer sms_dev_secret", request.getHeader("Authorization"))
    }

    @Test
    fun deliveryIncludesTheFrozenProtocolDefaults() = runBlocking {
        val address = "0x" + "ab".repeat(32)
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":"43c8412d-4b88-4df6-bf2a-75ce3a5a64ac","duplicate":false,"stored_at":"2026-09-01T02:00:01Z"}""",
                ),
        )
        val client = SmsApiClient(server.url("/").toString())

        client.deliver(
            PairingCredentials(address, "device-1", "sms_dev_secret"),
            QueuedDelivery(
                messageId = "7bd3191e-7c2e-4c7f-9bf8-a256ba63c001",
                localSmsId = 7,
                recipient = address,
                ciphertext = "A".repeat(64),
                receivedAtEpochMillis = 1_788_229_800_000,
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assert(body.contains("\"version\":1"))
        assert(body.contains("\"algorithm\":\"x25519-xsalsa20-poly1305-sealed-box\""))
    }
}
