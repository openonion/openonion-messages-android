package ai.openonion.messages

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.openonion.messages.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun versionOneQueueMigratesWithoutLosingPendingCiphertext() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO sms_delivery_queue
                    (messageId, recipient, ciphertext, receivedAtEpochMillis, state,
                     attemptCount, lastAttemptAtEpochMillis, lastErrorCode)
                VALUES ('message-1', '0xagent', 'ciphertext', 1788229800000,
                        'pending', 0, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query(
                "SELECT messageId, localSmsId FROM sms_delivery_queue WHERE messageId = 'message-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("message-1", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            assertEquals(0, database.rowCount("sms_delivery_receipts"))
            assertEquals(0, database.rowCount("sms_deletion_queue"))
            assertEquals(0, database.rowCount("sms_local_deletion_intents"))
        }
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "openonion-migration-test"
    }
}
