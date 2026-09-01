package ai.openonion.messages.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openonion.messages.MainUiState
import ai.openonion.messages.MainViewModel
import ai.openonion.messages.R
import ai.openonion.messages.sms.LocalSms
import ai.openonion.messages.ui.theme.OpenOnionBlack
import ai.openonion.messages.ui.theme.OpenOnionGreen
import ai.openonion.messages.ui.theme.OpenOnionGreenBright
import ai.openonion.messages.ui.theme.OpenOnionGreenSoft
import java.text.DateFormat
import java.util.Date

private enum class SetupStage {
    DEFAULT_APP,
    PERMISSIONS,
    PAIRING,
    READY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MainViewModel,
    requestDefaultRole: () -> Unit,
    requestSmsPermissions: () -> Unit,
    initialRecipient: String,
) {
    val state by viewModel.state.collectAsState()
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var showComposer by rememberSaveable { mutableStateOf(initialRecipient.isNotBlank()) }
    var showDisconnect by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<LocalSms?>(null) }
    val stage = state.setupStage()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrandTopBar() },
        floatingActionButton = {
            if (state.isDefaultSmsApp && state.hasSmsPermissions) {
                FloatingActionButton(
                    onClick = { showComposer = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New message")
                }
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                if (stage == SetupStage.READY) {
                    ActiveAgentCard(state = state, onDisconnect = { showDisconnect = true })
                } else {
                    SetupCard(
                        stage = stage,
                        busy = state.busy,
                        onPrimaryAction = {
                            when (stage) {
                                SetupStage.DEFAULT_APP -> requestDefaultRole()
                                SetupStage.PERMISSIONS -> requestSmsPermissions()
                                SetupStage.PAIRING -> showPairing = true
                                SetupStage.READY -> Unit
                            }
                        },
                    )
                }
            }

            state.error?.let { error -> item { ErrorBanner(error) } }

            item {
                InboxHeader(
                    messageCount = state.messages.size,
                    pendingDeliveries = state.pendingDeliveries,
                    pendingDeletions = state.pendingDeletions,
                )
            }

            if (state.messages.isEmpty()) {
                item { EmptyInbox(stage) }
            } else {
                items(state.messages, key = { it.id }) { message ->
                    MessageCard(message = message, onDelete = { deleteCandidate = message })
                }
            }
        }
    }

    if (showPairing) {
        PairingDialog(
            busy = state.busy,
            onDismiss = { showPairing = false },
            onPair = { link -> viewModel.pair(link) { showPairing = false } },
        )
    }
    state.pairingConfirmationCode?.let { code -> PairingConfirmationDialog(code) }
    if (showComposer) {
        ComposerDialog(
            initialRecipient = initialRecipient,
            onDismiss = { showComposer = false },
            onSend = { recipient, body ->
                viewModel.send(recipient, body)
                showComposer = false
            },
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
    deleteCandidate?.let { message ->
        DeleteDialog(
            message = message,
            busy = state.busy,
            onDismiss = { deleteCandidate = null },
            onConfirm = { viewModel.delete(message) { deleteCandidate = null } },
        )
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
private fun SetupCard(stage: SetupStage, busy: Boolean, onPrimaryAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set up your private inbox", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your messages stay readable on this phone. Only encrypted copies sync to the Agent you choose.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupStep(1, "Make Messages the default SMS app", stage.ordinal > SetupStage.DEFAULT_APP.ordinal)
                SetupStep(2, "Allow SMS access on this phone", stage.ordinal > SetupStage.PERMISSIONS.ordinal)
                SetupStep(3, "Connect one Agent with a private link", stage.ordinal > SetupStage.PAIRING.ordinal)
            }
            Button(
                onClick = onPrimaryAction,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        when (stage) {
                            SetupStage.DEFAULT_APP -> "Use as default SMS app"
                            SetupStage.PERMISSIONS -> "Allow SMS access"
                            SetupStage.PAIRING -> "Connect an Agent"
                            SetupStage.READY -> "Ready"
                        },
                    )
                }
            }
            Text(
                "SMS only in v1 · No MMS or RCS · Agent cannot send",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupStep(number: Int, label: String, complete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (complete) OpenOnionGreen else OpenOnionGreenSoft),
        ) {
            if (complete) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OpenOnionGreen,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
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
                    Text("Agent inbox connected", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "Encrypted sync is active",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }
                SyncStatusPill()
            }
            Text(
                "New SMS is encrypted on this phone before it is uploaded.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f),
            )
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
private fun InboxHeader(messageCount: Int, pendingDeliveries: Int, pendingDeletions: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Inbox", style = MaterialTheme.typography.titleLarge)
            Text(
                if (messageCount == 1) "1 message" else "$messageCount messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pendingDeliveries + pendingDeletions > 0) {
            Text(
                "Syncing ${pendingDeliveries + pendingDeletions}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MessageCard(message: LocalSms, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (message.unread) OpenOnionGreen else Color.Transparent),
            )
            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                message.address.ifBlank { "Unknown sender" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (message.unread) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (message.unread) {
                                Spacer(Modifier.size(8.dp))
                                Box(Modifier.size(7.dp).clip(CircleShape).background(OpenOnionGreen))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Delete message from phone and Agent inbox",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    message.body,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 18.dp),
                )
                if (!message.incoming) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "SENT FROM THIS PHONE",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox(stage: SetupStage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_inbox),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (stage == SetupStage.READY) "Your inbox is ready" else "Your messages will appear here",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (stage == SetupStage.READY) {
                    "New SMS will remain readable here and sync as encrypted data to your connected Agent."
                } else {
                    "Finish the private setup above to read and sync new SMS."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                Text("Paste the one-time private link created by your ConnectOnion Agent.")
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
private fun PairingConfirmationDialog(code: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Verify this phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Confirm that the same six digits appear in the ConnectOnion terminal.")
                Text(
                    text = "${code.take(3)} ${code.drop(3)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "The Agent must approve this exact device key before encrypted SMS can sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Text("Waiting for Agent approval…", color = MaterialTheme.colorScheme.primary)
        },
    )
}

@Composable
private fun ComposerDialog(
    initialRecipient: String,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var recipient by rememberSaveable { mutableStateOf(initialRecipient) }
    var body by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    recipient,
                    { recipient = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    body,
                    { body = it },
                    label = { Text("Message") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(recipient, body) },
                enabled = recipient.isNotBlank() && body.isNotBlank(),
            ) { Text("Send") }
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
                "This phone will stop syncing new SMS and revoke its device credential. Existing encrypted messages remain in the Agent inbox until deleted.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    message: LocalSms,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete this message everywhere?") },
        text = {
            Text(
                "The SMS from ${message.address.ifBlank { "this sender" }} will be permanently removed from this phone and its encrypted copy in the Agent inbox. This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (busy) "Deleting…" else "Delete everywhere") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

private fun MainUiState.setupStage(): SetupStage = when {
    !isDefaultSmsApp -> SetupStage.DEFAULT_APP
    !hasSmsPermissions -> SetupStage.PERMISSIONS
    pairedRecipient == null -> SetupStage.PAIRING
    else -> SetupStage.READY
}

private fun String.compactAddress(): String =
    if (length <= 24) this else "${take(12)}…${takeLast(8)}"
