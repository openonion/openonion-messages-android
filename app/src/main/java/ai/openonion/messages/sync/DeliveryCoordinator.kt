package ai.openonion.messages.sync

import android.content.Context
import ai.openonion.messages.data.DeliveryQueue
import ai.openonion.messages.data.PairingStore
import ai.openonion.messages.data.QueuedDelivery
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class DeliveryCoordinator(
    private val context: Context,
    private val queue: DeliveryQueue,
    private val pairingStore: PairingStore,
) {
    suspend fun enqueue(delivery: QueuedDelivery): Boolean {
        if (pairingStore.load()?.recipient != delivery.recipient) return false
        queue.enqueue(delivery)
        schedule()
        return true
    }

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SmsDeliveryWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "deliver-encrypted-sms"
    }
}
