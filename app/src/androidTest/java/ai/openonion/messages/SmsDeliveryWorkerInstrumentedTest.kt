package ai.openonion.messages

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ai.openonion.messages.data.LocalDeletionIntent
import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.network.SmsApiClient
import ai.openonion.messages.sync.SmsDeliveryWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SmsDeliveryWorkerInstrumentedTest {
    @Test
    fun queuedCiphertextIsDeliveredOnceWithTheDeviceCredential() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<MessagesApplication>()
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"id":"${UUID.randomUUID()}","duplicate":false,"stored_at":"2026-09-01T02:00:01Z"}""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}"),
            )
            app.container = AppContainer(app, api = SmsApiClient(server.url("/").toString()))
            app.container.database.clearAllTables()
            val recipient = "0x" + "ab".repeat(32)
            app.container.pairingStore.save(
                PairingCredentials(recipient, "device-1", "sms_dev_test_secret"),
            )
            val messageId = UUID.randomUUID().toString()
            app.container.database.deliveryQueue().enqueue(
                QueuedDelivery(
                    messageId = messageId,
                    localSmsId = 42,
                    recipient = recipient,
                    ciphertext = "A".repeat(64),
                    receivedAtEpochMillis = 1_788_229_800_000,
                ),
            )

            val worker = TestListenableWorkerBuilder<SmsDeliveryWorker>(app).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertEquals(0, app.container.database.deliveryQueue().pendingCount())

            val receipt = app.container.database.deliveryQueue().receiptForLocalSms(42)
            requireNotNull(receipt)
            app.container.database.deliveryQueue().finishLocalDeletion(42, 1_788_229_900_000)
            assertEquals(1, app.container.database.deliveryQueue().pendingDeletionCount())

            val deletionWorker = TestListenableWorkerBuilder<SmsDeliveryWorker>(app).build()
            assertEquals(ListenableWorker.Result.success(), deletionWorker.doWork())
            assertEquals(0, app.container.database.deliveryQueue().pendingDeletionCount())

            val request = server.takeRequest()
            assertEquals("Bearer sms_dev_test_secret", request.getHeader("Authorization"))
            assertEquals("/api/v1/sms/messages", request.path)
            assert(request.body.readUtf8().contains(messageId))

            val deletionRequest = server.takeRequest()
            assertEquals("DELETE", deletionRequest.method)
            assertEquals("/api/v1/sms/device/messages/${receipt.serverMessageId}", deletionRequest.path)
            assertEquals("Bearer sms_dev_test_secret", deletionRequest.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun localDeletionWinsWhenAnUploadWasAlreadyInFlight() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<MessagesApplication>()
        app.container = AppContainer(app)
        app.container.database.clearAllTables()
        val queue = app.container.database.deliveryQueue()
        val delivery = QueuedDelivery(
            messageId = UUID.randomUUID().toString(),
            localSmsId = 73,
            recipient = "0x" + "ab".repeat(32),
            ciphertext = "A".repeat(64),
            receivedAtEpochMillis = 1_788_229_800_000,
        )
        queue.enqueue(delivery)

        queue.beginLocalDeletion(LocalDeletionIntent(73, 1_788_229_810_000))
        queue.finishLocalDeletion(73, 1_788_229_820_000)
        assertEquals(1, queue.pendingLocalDeletionCount())

        val serverMessageId = UUID.randomUUID().toString()
        queue.completeDelivery(delivery, serverMessageId, 1_788_229_830_000)

        assertEquals(0, queue.pendingLocalDeletionCount())
        assertEquals(1, queue.pendingDeletionCount())
        assertEquals(null, queue.receiptForLocalSms(73))
        assertEquals(serverMessageId, queue.pendingDeletions().single().serverMessageId)
    }
}
