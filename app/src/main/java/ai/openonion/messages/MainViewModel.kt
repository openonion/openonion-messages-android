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
import ai.openonion.messages.data.PairingCredentials
import ai.openonion.messages.data.PendingPairingActivation
import ai.openonion.messages.network.PairingClaimRequest
import ai.openonion.messages.network.PairingLink
import ai.openonion.messages.protocol.SmsEncryptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MainUiState(
    /** Left over from 1.0.0, which asked to replace the phone's texting app. */
    val holdsSmsRole: Boolean = false,
    val hasSmsPermission: Boolean = false,
    /** Android refused without asking: a sideloaded app needs "Allow restricted settings" first. */
    val smsPermissionBlocked: Boolean = false,
    val pairedRecipient: String? = null,
    val pendingDeliveries: Int = 0,
    val pendingDeletions: Int = 0,
    val pairingConfirmationCode: String? = null,
    val justConnected: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MessagesApplication
    private val mutableState = MutableStateFlow(MainUiState())
    private var pairingJob: Job? = null
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val pendingPairing = app.container.pairingStore.loadPending()?.let { pending ->
                if (pending.expiresAt > now) {
                    pending
                } else {
                    app.container.pairingStore.clearPending()
                    null
                }
            }
            mutableState.update {
                it.copy(
                    holdsSmsRole = holdsSmsRole(),
                    hasSmsPermission = hasSmsPermission(),
                    pairedRecipient = app.container.pairingStore.load()?.recipient,
                    pairingConfirmationCode = pendingPairing?.confirmationCode,
                    pendingDeliveries = app.container.database.deliveryQueue().pendingCount(),
                    pendingDeletions = app.container.database.deliveryQueue().pendingDeletionCount(),
                )
            }
            if (pendingPairing != null && pairingJob?.isActive != true) {
                pairingJob = launch {
                    runCatching {
                        waitForSignedPairingActivation(
                            pendingPairing.claimToken,
                            pendingPairing.expiresAt,
                        )
                    }.onFailure { error -> failPairing(error) }
                }
            }
        }
    }

    fun pair(linkValue: String, onSuccess: () -> Unit) {
        if (pairingJob?.isActive == true) return
        pairingJob = viewModelScope.launch {
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
                    waitForSignedPairingActivation(pending.claimToken, link.expiresAt)
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
            }.onFailure { error -> failPairing(error) }
        }
    }

    /** Stops waiting for the Agent's approval; the server-side challenge simply expires. */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        app.container.pairingStore.clearPending()
        mutableState.update { it.copy(busy = false, pairingConfirmationCode = null) }
    }

    fun onSmsPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        mutableState.update { it.copy(hasSmsPermission = granted, smsPermissionBlocked = !granted && !canAskAgain) }
    }

    fun acknowledgeConnected() {
        mutableState.update { it.copy(justConnected = false) }
    }

    private fun failPairing(error: Throwable) {
        if (error is CancellationException) return
        mutableState.update {
            it.copy(
                busy = false,
                pairingConfirmationCode = null,
                error = error.message ?: "Could not connect this Agent",
            )
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
                justConnected = true,
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val credentials = app.container.pairingStore.load() ?: return@launch
            if (app.container.database.deliveryQueue().pendingDeletionCount() > 0) {
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

    private fun holdsSmsRole(): Boolean {
        val application = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            application.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(application) == application.packageName
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PAIRING_POLL_MILLIS = 2_000L
    }
}
