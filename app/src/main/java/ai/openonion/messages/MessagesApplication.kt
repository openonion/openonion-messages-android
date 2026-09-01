package ai.openonion.messages

import android.app.Application
import ai.openonion.messages.data.AppDatabase
import ai.openonion.messages.data.DeviceIdentity
import ai.openonion.messages.data.PairingStore
import ai.openonion.messages.network.SmsApiClient
import ai.openonion.messages.sync.DeliveryCoordinator

class MessagesApplication : Application() {
    lateinit var container: AppContainer
        internal set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(
    application: Application,
    val database: AppDatabase = AppDatabase.create(application),
    val pairingStore: PairingStore = PairingStore(application),
    val deviceIdentity: DeviceIdentity = DeviceIdentity(),
    val api: SmsApiClient = SmsApiClient(BuildConfig.OO_API_BASE_URL),
) {
    val deliveryCoordinator = DeliveryCoordinator(
        context = application,
        queue = database.deliveryQueue(),
        pairingStore = pairingStore,
    )
}
