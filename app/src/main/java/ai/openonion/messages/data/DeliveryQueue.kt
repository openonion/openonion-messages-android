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
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "sms_delivery_queue")
data class QueuedDelivery(
    @PrimaryKey val messageId: String,
    val localSmsId: Long?,
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

@Entity(tableName = "sms_delivery_receipts")
data class DeliveryReceipt(
    @PrimaryKey val localSmsId: Long,
    val messageId: String,
    val serverMessageId: String,
    val recipient: String,
    val storedAtEpochMillis: Long,
)

@Entity(tableName = "sms_deletion_queue")
data class QueuedDeletion(
    @PrimaryKey val serverMessageId: String,
    val recipient: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
)

@Entity(tableName = "sms_local_deletion_intents")
data class LocalDeletionIntent(
    @PrimaryKey val localSmsId: Long,
    val createdAtEpochMillis: Long,
)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReceipt(receipt: DeliveryReceipt)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueDeletion(deletion: QueuedDeletion): Long

    @Query("SELECT * FROM sms_delivery_receipts WHERE localSmsId = :localSmsId LIMIT 1")
    suspend fun receiptForLocalSms(localSmsId: Long): DeliveryReceipt?

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

    @Query("SELECT * FROM sms_deletion_queue ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun pendingDeletions(limit: Int = 25): List<QueuedDeletion>

    @Query("DELETE FROM sms_deletion_queue WHERE serverMessageId = :serverMessageId")
    suspend fun deleteCompletedDeletion(serverMessageId: String)

    @Query(
        "UPDATE sms_deletion_queue SET attemptCount = attemptCount + 1, " +
            "lastAttemptAtEpochMillis = :now, lastErrorCode = :errorCode " +
            "WHERE serverMessageId = :serverMessageId",
    )
    suspend fun markDeletionFailed(serverMessageId: String, now: Long, errorCode: String)

    @Query("SELECT COUNT(*) FROM sms_deletion_queue")
    suspend fun pendingDeletionCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM sms_delivery_queue WHERE localSmsId = :localSmsId)")
    suspend fun hasPendingForLocalSms(localSmsId: Long): Boolean

    @Query("DELETE FROM sms_delivery_queue WHERE localSmsId = :localSmsId")
    suspend fun deletePendingForLocalSms(localSmsId: Long)

    @Query("DELETE FROM sms_delivery_receipts WHERE localSmsId = :localSmsId")
    suspend fun deleteReceipt(localSmsId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun beginLocalDeletion(intent: LocalDeletionIntent)

    @Query("SELECT * FROM sms_local_deletion_intents ORDER BY createdAtEpochMillis ASC")
    suspend fun pendingLocalDeletions(): List<LocalDeletionIntent>

    @Query("SELECT EXISTS(SELECT 1 FROM sms_local_deletion_intents WHERE localSmsId = :localSmsId)")
    suspend fun hasLocalDeletionIntent(localSmsId: Long): Boolean

    @Query("SELECT COUNT(*) FROM sms_local_deletion_intents")
    suspend fun pendingLocalDeletionCount(): Int

    @Query("DELETE FROM sms_local_deletion_intents WHERE localSmsId = :localSmsId")
    suspend fun cancelLocalDeletion(localSmsId: Long)

    @Query(
        """
        DELETE FROM sms_local_deletion_intents
        WHERE NOT EXISTS (
            SELECT 1 FROM sms_delivery_queue
            WHERE sms_delivery_queue.localSmsId = sms_local_deletion_intents.localSmsId
        ) AND NOT EXISTS (
            SELECT 1 FROM sms_delivery_receipts
            WHERE sms_delivery_receipts.localSmsId = sms_local_deletion_intents.localSmsId
        )
        """,
    )
    suspend fun cancelOrphanedLocalDeletions()

    @Transaction
    suspend fun finishLocalDeletion(localSmsId: Long, now: Long) {
        val hadPendingUpload = hasPendingForLocalSms(localSmsId)
        val receipt = receiptForLocalSms(localSmsId)
        deletePendingForLocalSms(localSmsId)
        if (receipt != null) {
            enqueueDeletion(
                QueuedDeletion(
                    serverMessageId = receipt.serverMessageId,
                    recipient = receipt.recipient,
                    createdAtEpochMillis = now,
                ),
            )
            deleteReceipt(localSmsId)
            cancelLocalDeletion(localSmsId)
        } else if (!hadPendingUpload) {
            cancelLocalDeletion(localSmsId)
        }
    }

    @Transaction
    suspend fun completeDelivery(delivery: QueuedDelivery, serverMessageId: String, now: Long) {
        delivery.localSmsId?.let { localSmsId ->
            if (hasLocalDeletionIntent(localSmsId)) {
                enqueueDeletion(
                    QueuedDeletion(
                        serverMessageId = serverMessageId,
                        recipient = delivery.recipient,
                        createdAtEpochMillis = now,
                    ),
                )
                cancelLocalDeletion(localSmsId)
            } else {
                saveReceipt(
                    DeliveryReceipt(
                        localSmsId = localSmsId,
                        messageId = delivery.messageId,
                        serverMessageId = serverMessageId,
                        recipient = delivery.recipient,
                        storedAtEpochMillis = now,
                    ),
                )
            }
        }
        deleteDelivered(delivery.messageId)
    }
}

@Database(
    entities = [
        QueuedDelivery::class,
        DeliveryReceipt::class,
        QueuedDeletion::class,
        LocalDeletionIntent::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryQueue(): DeliveryQueue

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "openonion-messages.db",
        ).addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_delivery_queue ADD COLUMN localSmsId INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_delivery_receipts (
                        localSmsId INTEGER NOT NULL PRIMARY KEY,
                        messageId TEXT NOT NULL,
                        serverMessageId TEXT NOT NULL,
                        recipient TEXT NOT NULL,
                        storedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_deletion_queue (
                        serverMessageId TEXT NOT NULL PRIMARY KEY,
                        recipient TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        lastAttemptAtEpochMillis INTEGER,
                        lastErrorCode TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_local_deletion_intents (
                        localSmsId INTEGER NOT NULL PRIMARY KEY,
                        createdAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
