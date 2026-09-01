package ai.openonion.messages

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
        assert(request.body.readUtf8().contains("sms_pair_secret"))
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
}
