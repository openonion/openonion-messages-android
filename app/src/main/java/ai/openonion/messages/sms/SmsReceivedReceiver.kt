package ai.openonion.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ai.openonion.messages.MessagesApplication
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.protocol.SmsEncryptor
import ai.openonion.messages.protocol.SmsPlaintext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Listens alongside the phone's own texting app. Android hands every incoming
 * SMS to the default app (which stores it and notifies) and also broadcasts it
 * here, so this app never has to be the default and never touches the inbox.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val receivedAt = messages.minOf { it.timestampMillis }
        val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it >= 0 }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                queueForAgent(context, sender, body, receivedAt, subscriptionId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun queueForAgent(
        context: Context,
        sender: String,
        body: String,
        receivedAt: Long,
        subscriptionId: Int?,
    ) {
        val container = (context.applicationContext as MessagesApplication).container
        val pairing = container.pairingStore.load() ?: return
        val encrypted = SmsEncryptor().encrypt(
            pairing.recipient,
            SmsPlaintext(
                sender = sender,
                body = body,
                receivedAt = Instant.ofEpochMilli(receivedAt).toString(),
                subscriptionId = subscriptionId,
            ),
        )
        container.deliveryCoordinator.enqueue(
            QueuedDelivery(
                messageId = UUID.randomUUID().toString(),
                localSmsId = null,
                recipient = pairing.recipient,
                ciphertext = encrypted.ciphertext,
                receivedAtEpochMillis = receivedAt,
            ),
        )
    }
}
