package ai.openonion.messages.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "sms_delivery_queue")
data class QueuedDelivery(
    @PrimaryKey val messageId: String,
    val recipient: String,
    val ciphertext: String,
    val receivedAtEpochMillis: Long,
    val state: String = STATE_PENDING,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
) {
    companion object {
        const val STATE_PENDING = "pending"
    }
}

@Dao
interface DeliveryQueue {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(delivery: QueuedDelivery): Long

    @Query(
        "SELECT * FROM sms_delivery_queue " +
            "WHERE state = 'pending' ORDER BY receivedAtEpochMillis ASC LIMIT :limit",
    )
    suspend fun pending(limit: Int = 25): List<QueuedDelivery>

    @Query("DELETE FROM sms_delivery_queue WHERE messageId = :messageId")
    suspend fun deleteDelivered(messageId: String)

    @Query(
        "UPDATE sms_delivery_queue SET attemptCount = attemptCount + 1, " +
            "lastAttemptAtEpochMillis = :now, lastErrorCode = :errorCode " +
            "WHERE messageId = :messageId",
    )
    suspend fun markAttemptFailed(messageId: String, now: Long, errorCode: String)

    @Query("SELECT COUNT(*) FROM sms_delivery_queue WHERE state = 'pending'")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM sms_delivery_queue WHERE recipient = :recipient AND state = 'pending'")
    suspend fun deletePendingForRecipient(recipient: String)
}

@Database(entities = [QueuedDelivery::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryQueue(): DeliveryQueue

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "openonion-messages.db",
        ).build()
    }
}
