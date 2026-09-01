package ai.openonion.messages

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.openonion.messages.data.LocalDeletionIntent
import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.PendingPairingActivation
import ai.openonion.messages.network.PairingClaimRequest
import ai.openonion.messages.network.PairingLink
import ai.openonion.messages.protocol.SmsEncryptor
import ai.openonion.messages.sms.LocalSms
import ai.openonion.messages.sms.SmsRepository
import ai.openonion.messages.sms.SmsSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MainUiState(
    val isDefaultSmsApp: Boolean = false,
    val hasSmsPermissions: Boolean = false,
    val pairedRecipient: String? = null,
    val pendingDeliveries: Int = 0,
    val pendingDeletions: Int = 0,
    val pairingConfirmationCode: String? = null,
    val messages: List<LocalSms> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MessagesApplication
    private val repository = SmsRepository(application.contentResolver)
    private val sender = SmsSender(application)
    private val mutableState = MutableStateFlow(MainUiState())
    private var pendingPairingPollStarted = false
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            recoverConfirmedLocalDeletions()
            val now = System.currentTimeMillis() / 1000
            val pendingPairing = app.container.pairingStore.loadPending()?.let { pending ->
                if (pending.expiresAt > now) {
                    pending
                } else {
                    app.container.pairingStore.clearPending()
                    null
                }
            }
            val isDefault = isDefaultSmsApp()
            val messages = if (isDefault) runCatching { repository.latest() }.getOrDefault(emptyList()) else emptyList()
            mutableState.update {
                it.copy(
                    isDefaultSmsApp = isDefault,
                    hasSmsPermissions = hasSmsPermissions(),
                    pairedRecipient = app.container.pairingStore.load()?.recipient,
                    pairingConfirmationCode = pendingPairing?.confirmationCode,
                    pendingDeliveries = app.container.database.deliveryQueue().pendingCount(),
                    pendingDeletions =
                        app.container.database.deliveryQueue().pendingDeletionCount() +
                        app.container.database.deliveryQueue().pendingLocalDeletionCount(),
                    messages = messages,
                )
            }
            if (pendingPairing != null && !pendingPairingPollStarted) {
                pendingPairingPollStarted = true
                launch {
                    runCatching {
                        waitForSignedPairingActivation(
                            pendingPairing.claimToken,
                            pendingPairing.expiresAt,
                        )
                    }.onFailure { error ->
                        mutableState.update {
                            it.copy(error = error.message ?: "Could not finish connecting this agent")
                        }
                    }
                    pendingPairingPollStarted = false
                }
            }
        }
    }

    fun pair(linkValue: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching {
                val link = PairingLink.parse(linkValue)
                SmsEncryptor.parseAddress(link.recipient)
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                if (link.isSignedChallenge) {
                    val pending = app.container.api.claimSignedPairing(
                        link = link,
                        deviceIdentity = app.container.deviceIdentity,
                        deviceName = deviceName,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                    app.container.pairingStore.savePending(
                        PendingPairingActivation(
                            claimToken = pending.claimToken,
                            expiresAt = link.expiresAt,
                            confirmationCode = pending.confirmationCode,
                        ),
                    )
                    mutableState.update {
                        it.copy(
                            busy = false,
                            pairingConfirmationCode = pending.confirmationCode,
                        )
                    }
                    onSuccess()
                    pendingPairingPollStarted = true
                    try {
                        waitForSignedPairingActivation(pending.claimToken, link.expiresAt)
                    } finally {
                        pendingPairingPollStarted = false
                    }
                } else {
                    val credentials = app.container.api.claimPairing(
                        PairingClaimRequest(
                            recipient = link.recipient,
                            token = link.token,
                            deviceName = deviceName,
                            appVersion = BuildConfig.VERSION_NAME,
                        ),
                    )
                    activate(credentials)
                    onSuccess()
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        busy = false,
                        pairingConfirmationCode = null,
                        error = error.message ?: "Could not connect this agent",
                    )
                }
            }
        }
    }

    private suspend fun waitForSignedPairingActivation(claimToken: String, expiresAt: Long) {
        while (System.currentTimeMillis() / 1000 < expiresAt) {
            val response = app.container.api.activateSignedPairing(claimToken)
            if (response.status == "active") {
                val deviceId = requireNotNull(response.deviceId)
                val deviceToken = requireNotNull(response.deviceToken)
                val recipient = requireNotNull(response.recipient)
                activate(PairingCredentials(recipient, deviceId, deviceToken))
                return
            }
            delay(PAIRING_POLL_MILLIS)
        }
        app.container.pairingStore.clearPending()
        error("Pairing challenge expired before the Agent approved this phone")
    }

    private fun activate(credentials: PairingCredentials) {
        app.container.pairingStore.save(credentials)
        app.container.pairingStore.clearPending()
        app.container.deliveryCoordinator.schedule()
        mutableState.update {
            it.copy(
                busy = false,
                pairedRecipient = credentials.recipient,
                pairingConfirmationCode = null,
            )
        }
    }

    fun send(recipient: String, body: String) {
        viewModelScope.launch {
            runCatching { sender.send(recipient.trim(), body) }
                .onFailure { error ->
                    mutableState.update { it.copy(error = error.message ?: "Could not send message") }
                }
            refresh()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val credentials = app.container.pairingStore.load() ?: return@launch
            if (
                app.container.database.deliveryQueue().pendingDeletionCount() > 0 ||
                app.container.database.deliveryQueue().pendingLocalDeletionCount() > 0
            ) {
                app.container.deliveryCoordinator.schedule()
                mutableState.update {
                    it.copy(error = "Finish pending server deletions before disconnecting this agent")
                }
                return@launch
            }
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching { app.container.api.revokeCurrentDevice(credentials) }
                .onSuccess {
                    app.container.database.deliveryQueue()
                        .deletePendingForRecipient(credentials.recipient)
                    app.container.pairingStore.clear()
                    mutableState.update {
                        it.copy(
                            busy = false,
                            pairedRecipient = null,
                            pendingDeliveries = app.container.database.deliveryQueue().pendingCount(),
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(busy = false, error = error.message ?: "Could not disconnect this agent")
                    }
                }
        }
    }

    fun delete(message: LocalSms, onSuccess: () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching {
                val queue = app.container.database.deliveryQueue()
                queue.beginLocalDeletion(
                    LocalDeletionIntent(message.id, System.currentTimeMillis()),
                )
                if (repository.exists(message.id) && !repository.delete(message.id)) {
                    queue.cancelLocalDeletion(message.id)
                    error("The local SMS could not be deleted")
                }
                queue.finishLocalDeletion(message.id, System.currentTimeMillis())
                app.container.deliveryCoordinator.schedule()
            }.onSuccess {
                mutableState.update { it.copy(busy = false) }
                refresh()
                onSuccess()
            }.onFailure { error ->
                mutableState.update {
                    it.copy(busy = false, error = error.message ?: "Could not delete message")
                }
            }
        }
    }

    private suspend fun recoverConfirmedLocalDeletions() {
        val queue = app.container.database.deliveryQueue()
        queue.pendingLocalDeletions().forEach { intent ->
            if (!repository.exists(intent.localSmsId) || repository.delete(intent.localSmsId)) {
                queue.finishLocalDeletion(intent.localSmsId, System.currentTimeMillis())
                app.container.deliveryCoordinator.schedule()
            }
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        val application = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            application.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(application) == application.packageName
        }
    }

    private fun hasSmsPermissions(): Boolean {
        val application = getApplication<Application>()
        return REQUIRED_SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private companion object {
        const val PAIRING_POLL_MILLIS = 2_000L
        val REQUIRED_SMS_PERMISSIONS = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
        )
    }
}
