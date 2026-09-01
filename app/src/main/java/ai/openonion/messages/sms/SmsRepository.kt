package ai.openonion.messages.sms

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalSms(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
    val unread: Boolean,
)

class SmsRepository(private val resolver: ContentResolver) {
    suspend fun latest(limit: Int = 200): List<LocalSms> = withContext(Dispatchers.IO) {
        val uri = Telephony.Sms.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .build()
        resolver.query(
            uri,
            PROJECTION,
            null,
            null,
            "${Telephony.Sms.DEFAULT_SORT_ORDER}",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val read = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LocalSms(
                            id = cursor.getLong(id),
                            address = cursor.getString(address).orEmpty(),
                            body = cursor.getString(body).orEmpty(),
                            timestamp = cursor.getLong(date),
                            incoming = cursor.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            unread = cursor.getInt(read) == 0,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    suspend fun delete(localSmsId: Long): Boolean = withContext(Dispatchers.IO) {
        resolver.delete(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, localSmsId),
            null,
            null,
        ) == 1
    }

    suspend fun exists(localSmsId: Long): Boolean = withContext(Dispatchers.IO) {
        resolver.query(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, localSmsId),
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
    }

    private companion object {
        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
    }
}
