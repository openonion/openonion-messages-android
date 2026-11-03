package ai.openonion.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openonion.messages.MainUiState
import ai.openonion.messages.MainViewModel
import ai.openonion.messages.R
import ai.openonion.messages.ui.theme.OpenOnionBlack
import ai.openonion.messages.ui.theme.OpenOnionGreen
import ai.openonion.messages.ui.theme.OpenOnionGreenBright
import ai.openonion.messages.ui.theme.OpenOnionGreenSoft

internal enum class SetupStage { PAIRING, PERMISSION, READY }

@Composable
fun MessagesScreen(
    viewModel: MainViewModel,
    requestSmsPermission: () -> Unit,
    openAppSettings: () -> Unit,
    openDefaultAppsSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showDisconnect by rememberSaveable { mutableStateOf(false) }
    val stage = state.setupStage()

    if (showScanner) {
        BackHandler { showScanner = false }
        PairingScannerScreen(
            onLink = { link ->
                showScanner = false
                viewModel.pair(link) {}
            },
            onPasteInstead = {
                showScanner = false
                showPairing = true
            },
            onClose = { showScanner = false },
        )
        return
    }
    state.pairingConfirmationCode?.let { code ->
        VerifyOnComputerScreen(code = code, onCancel = viewModel::cancelPairing)
        return
    }
    if (state.justConnected) {
        ConnectedScreen(
            agentAddress = state.pairedRecipient.orEmpty(),
            nextLabel = if (stage == SetupStage.PERMISSION) "Turn on SMS forwarding" else "Done",
            onNext = {
                viewModel.acknowledgeConnected()
                if (stage == SetupStage.PERMISSION) requestSmsPermission()
            },
        )
        return
    }

    HomeScreen(
        state = state,
        onScan = { showScanner = true },
        onPaste = { showPairing = true },
        onAllowSms = if (state.smsPermissionBlocked) openAppSettings else requestSmsPermission,
        onChooseTextingApp = openDefaultAppsSettings,
        onDisconnect = { showDisconnect = true },
    )

    if (showPairing) {
        PairingDialog(
            busy = state.busy,
            onDismiss = { showPairing = false },
            onPair = { link -> viewModel.pair(link) { showPairing = false } },
        )
    }
    if (showDisconnect) {
        DisconnectDialog(
            onDismiss = { showDisconnect = false },
            onConfirm = {
                showDisconnect = false
                viewModel.disconnect()
            },
        )
    }
}

/** Everything below the full-screen moments; stateless so it can be rendered in tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: MainUiState,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onAllowSms: () -> Unit,
    onChooseTextingApp: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val stage = state.setupStage()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrandTopBar() },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.holdsSmsRole) item { GiveBackCard(onChooseTextingApp) }
            item {
                when (stage) {
                    SetupStage.PAIRING -> PairingHero(busy = state.busy, onScan = onScan, onPaste = onPaste)
                    SetupStage.PERMISSION -> ForwardingCard(blocked = state.smsPermissionBlocked, onAllow = onAllowSms)
                    SetupStage.READY -> ActiveAgentCard(state = state, onDisconnect = onDisconnect)
                }
            }
            state.error?.let { error -> item { ErrorBanner(error) } }
            if (stage == SetupStage.READY) item { KeepTextingCard() }
        }
    }
}

/**
 * 1.0.0 asked to become the phone's texting app, which cost people their
 * contacts, photos and group chats. Hand that job back to a real texting app.
 */
@Composable
private fun GiveBackCard(onChoose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OpenOnionGreenSoft),
        border = BorderStroke(1.dp, OpenOnionGreen.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Switch back to your usual texting app",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "This app no longer needs to be your SMS app. Pick Messages again so sending, " +
                    "contacts, photos and group chats work normally. Forwarding keeps working.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text("Choose SMS app") }
        }
    }
}

/** One permission, explained in terms of what the person keeps, not what we take. */
@Composable
internal fun ForwardingCard(blocked: Boolean, onAllow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn on SMS forwarding", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Keep texting in your usual app. This app only gets a copy of each new text, " +
                        "encrypts it, and sends it to your Agent.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (blocked) RestrictedSettingsSteps()
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (blocked) "Open app settings" else "Allow SMS access")
            }
        }
    }
}

/** Android silently refuses SMS access to apps installed outside an app store until this is done. */
@Composable
private fun RestrictedSettingsSteps() {
    Surface(color = OpenOnionGreenSoft, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Android is blocking this until you allow it in Settings:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            NumberedLine(1, "Tap ⋮ at the top right, then Allow restricted settings")
            NumberedLine(2, "Go to Permissions → SMS → Allow")
            NumberedLine(3, "Come back here")
        }
    }
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(24.dp).clip(CircleShape).background(OpenOnionGreen),
        ) {
            Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Says plainly what changes and what doesn't, so nobody wonders where their texts went. */
@Composable
internal fun KeepTextingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Keep texting as usual", style = MaterialTheme.typography.titleLarge)
            CheckLine("Texts still arrive in your Messages app")
            CheckLine("Each new SMS also goes to your Agent, encrypted")
            CheckLine("Your Agent can't send texts or read old ones")
            Text(
                "Chat (RCS) messages and photos are not forwarded.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = OpenOnionGreen,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandTopBar() {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.openonion_mark_transparent),
                    contentDescription = "OpenOnion",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = "OpenOnion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "MESSAGES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun ActiveAgentCard(state: MainUiState, onDisconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OpenOnionBlack),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(OpenOnionGreen),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Forwarding to your Agent", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "Encrypted on this phone first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }
                SyncStatusPill()
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "CONNECTED AGENT",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    state.pairedRecipient.orEmpty().compactAddress(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                if (state.pendingDeliveries + state.pendingDeletions > 0) {
                    Text(
                        "${state.pendingDeliveries + state.pendingDeletions} encrypted change(s) waiting to sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpenOnionGreenBright,
                    )
                }
            }
            TextButton(
                onClick = onDisconnect,
                enabled = !state.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) { Text("Disconnect Agent") }
        }
    }
}

@Composable
private fun SyncStatusPill() {
    Surface(
        color = OpenOnionGreen.copy(alpha = 0.22f),
        shape = CircleShape,
        border = BorderStroke(1.dp, OpenOnionGreenBright.copy(alpha = 0.72f)),
    ) {
        Text(
            "ON",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = OpenOnionGreenBright,
        )
    }
}

@Composable
private fun ErrorBanner(error: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

@Composable
private fun PairingDialog(busy: Boolean, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect an Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste the one-time link that co sms pair printed under the QR code.")
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Private pairing link") },
                    supportingText = { Text("The link expires and can be claimed only once.") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(link) }, enabled = link.isNotBlank() && !busy) {
                Text(if (busy) "Connecting…" else "Connect")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DisconnectDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect this Agent?") },
        text = {
            Text(
                "New texts will stop going to your Agent. Texts it already has stay in its inbox until it deletes them.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


internal fun MainUiState.setupStage(): SetupStage = when {
    pairedRecipient == null -> SetupStage.PAIRING
    !hasSmsPermission -> SetupStage.PERMISSION
    else -> SetupStage.READY
}

private fun String.compactAddress(): String =
    if (length <= 24) this else "${take(12)}…${takeLast(8)}"
