package ai.openonion.messages

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ai.openonion.messages.data.LocalDeletionIntent
import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.PendingPairingActivation
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.network.PairingLink
import ai.openonion.messages.network.SmsApiClient
import ai.openonion.messages.protocol.SmsEncryptor
import ai.openonion.messages.protocol.SmsPlaintext
import ai.openonion.messages.sync.SmsDeliveryWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealBackendSyncInstrumentedTest {
    @Test
    fun claimSignedPairingWithRealBackend() = runBlocking {
        val baseUrl = argumentOrSkip("baseUrl")
        val link = PairingLink.parse(argumentOrSkip("pairingLink"))
        val app = ApplicationProvider.getApplicationContext<MessagesApplication>()
        app.container = AppContainer(app, api = SmsApiClient(baseUrl))
        app.container.database.clearAllTables()
        app.container.pairingStore.clear()

        val pending = app.container.api.claimSignedPairing(
            link = link,
            deviceIdentity = app.container.deviceIdentity,
            deviceName = "OpenOnion E2E emulator",
            appVersion = BuildConfig.VERSION_NAME,
        )
        app.container.pairingStore.savePending(
            PendingPairingActivation(
                claimToken = pending.claimToken,
                expiresAt = link.expiresAt,
                confirmationCode = pending.confirmationCode,
            ),
        )
        assertEquals(
            link.confirmationCode(app.container.deviceIdentity.publicKeyBase64),
            pending.confirmationCode,
        )
    }

    @Test
    fun activateAndUploadCiphertextToRealBackend() = runBlocking {
        val baseUrl = argumentOrSkip("baseUrl")
        val app = ApplicationProvider.getApplicationContext<MessagesApplication>()
        app.container = AppContainer(app, api = SmsApiClient(baseUrl))
        val pending = requireNotNull(app.container.pairingStore.loadPending())
        val activation = app.container.api.activateSignedPairing(pending.claimToken)
        assertEquals("active", activation.status)
        val credentials = PairingCredentials(
            recipient = requireNotNull(activation.recipient),
            deviceId = requireNotNull(activation.deviceId),
            deviceToken = requireNotNull(activation.deviceToken),
        )
        app.container.pairingStore.save(credentials)
        app.container.pairingStore.clearPending()
        val encrypted = SmsEncryptor().encrypt(
            credentials.recipient,
            SmsPlaintext(
                sender = TEST_SENDER,
                body = TEST_BODY,
                receivedAt = TEST_RECEIVED_AT,
                subscriptionId = 1,
            ),
        )
        app.container.database.deliveryQueue().enqueue(
            QueuedDelivery(
                messageId = TEST_MESSAGE_ID,
                localSmsId = TEST_LOCAL_SMS_ID,
                recipient = credentials.recipient,
                ciphertext = encrypted.ciphertext,
                receivedAtEpochMillis = 1_788_229_800_000,
            ),
        )

        val worker = TestListenableWorkerBuilder<SmsDeliveryWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, app.container.database.deliveryQueue().pendingCount())
        assertNotNull(app.container.database.deliveryQueue().receiptForLocalSms(TEST_LOCAL_SMS_ID))
    }

    @Test
    fun deleteCiphertextFromRealBackend() = runBlocking {
        val baseUrl = argumentOrSkip("baseUrl")
        val app = ApplicationProvider.getApplicationContext<MessagesApplication>()
        app.container = AppContainer(app, api = SmsApiClient(baseUrl))
        val queue = app.container.database.deliveryQueue()
        assertNotNull(queue.receiptForLocalSms(TEST_LOCAL_SMS_ID))

        queue.beginLocalDeletion(LocalDeletionIntent(TEST_LOCAL_SMS_ID, System.currentTimeMillis()))
        queue.finishLocalDeletion(TEST_LOCAL_SMS_ID, System.currentTimeMillis())
        val worker = TestListenableWorkerBuilder<SmsDeliveryWorker>(app).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, queue.pendingDeletionCount())
        assertNull(queue.receiptForLocalSms(TEST_LOCAL_SMS_ID))
    }

    private fun argumentOrSkip(name: String): String {
        val value = InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
        assumeTrue("Real backend E2E argument not supplied: $name", value != null)
        return requireNotNull(value)
    }

    private companion object {
        const val TEST_LOCAL_SMS_ID = 70_001L
        const val TEST_MESSAGE_ID = "7bd3191e-7c2e-4c7f-9bf8-a256ba63c001"
        const val TEST_SENDER = "+61412345678"
        const val TEST_BODY = "Android to PostgreSQL E2E code 482193"
        const val TEST_RECEIVED_AT = "2026-09-01T02:00:00Z"
    }
}
