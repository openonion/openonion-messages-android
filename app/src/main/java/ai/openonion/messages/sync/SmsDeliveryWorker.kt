package ai.openonion.messages.sync

import android.content.Context
import android.util.Log
import ai.openonion.messages.MessagesApplication
import ai.openonion.messages.network.SmsApiException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SmsDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MessagesApplication).container
        val credentials = container.pairingStore.load() ?: return Result.failure()
        val pending = container.database.deliveryQueue().pending()
        if (pending.isEmpty()) return Result.success()

        for (delivery in pending) {
            if (delivery.recipient != credentials.recipient) continue
            try {
                container.api.deliver(credentials, delivery)
                container.database.deliveryQueue().deleteDelivered(delivery.messageId)
            } catch (error: SmsApiException) {
                container.database.deliveryQueue().markAttemptFailed(
                    delivery.messageId,
                    System.currentTimeMillis(),
                    error.code,
                )
                return if (error.code in RETRYABLE_CODES) Result.retry() else Result.failure()
            } catch (error: Exception) {
                Log.w(TAG, "Encrypted SMS delivery failed: ${error.javaClass.simpleName}")
                container.database.deliveryQueue().markAttemptFailed(
                    delivery.messageId,
                    System.currentTimeMillis(),
                    "network_error",
                )
                return Result.retry()
            }
        }
        return if (container.database.deliveryQueue().pendingCount() > 0) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "SmsDeliveryWorker"
        val RETRYABLE_CODES = setOf("rate_limited", "server_unavailable")
    }
}
