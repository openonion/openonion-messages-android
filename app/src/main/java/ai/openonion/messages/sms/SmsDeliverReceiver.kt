package ai.openonion.messages.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ai.openonion.messages.MainActivity
import ai.openonion.messages.MessagesApplication
import ai.openonion.messages.R
import ai.openonion.messages.data.QueuedDelivery
import ai.openonion.messages.protocol.SmsEncryptor
import ai.openonion.messages.protocol.SmsPlaintext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val receivedAt = messages.minOf { it.timestampMillis }
        val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it >= 0 }

        val localSmsId = persistInSystemInbox(context, sender, body, receivedAt, subscriptionId)
        showPrivateNotification(context, sender)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                queueForAgent(context, sender, body, receivedAt, subscriptionId, localSmsId)
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
        localSmsId: Long?,
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
                localSmsId = localSmsId,
                recipient = pairing.recipient,
                ciphertext = encrypted.ciphertext,
                receivedAtEpochMillis = receivedAt,
            ),
        )
    }

    private fun persistInSystemInbox(
        context: Context,
        sender: String,
        body: String,
        receivedAt: Long,
        subscriptionId: Int?,
    ): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.DATE_SENT, receivedAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }
        return context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            ?.lastPathSegment
            ?.toLongOrNull()
    }

    private fun showPrivateNotification(context: Context, sender: String) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val openInbox = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.new_message))
            .setContentText(context.getString(R.string.unlock_to_read))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender.ifBlank { context.getString(R.string.unknown_sender) })
            .setContentText(context.getString(R.string.new_message))
            .setContentIntent(openInbox)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "incoming-messages"
    }
}
