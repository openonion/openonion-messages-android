package ai.openonion.messages.sms

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.telephony.SmsManager
import android.provider.Telephony

class SmsSender(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun send(recipient: String, body: String) {
        require(recipient.isNotBlank()) { "Recipient is required" }
        require(body.isNotBlank()) { "Message is required" }
        val manager = context.getSystemService(SmsManager::class.java)
        val parts = manager.divideMessage(body)
        if (parts.size == 1) {
            manager.sendTextMessage(recipient, null, body, null, null)
        } else {
            manager.sendMultipartTextMessage(recipient, null, parts, null, null)
        }
        resolver.insert(
            Telephony.Sms.Sent.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            },
        )
    }
}
