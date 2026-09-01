package ai.openonion.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.openonion.messages.sms.LocalSms
import ai.openonion.messages.ui.theme.OpenOnionMessagesTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (isDefaultSmsApp()) requestSmsPermissions()
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenOnionMessagesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MessagesScreen(
                        viewModel = viewModel,
                        requestDefaultRole = ::requestDefaultRole,
                        requestSmsPermissions = ::requestSmsPermissions,
                        initialRecipient = intent?.data?.schemeSpecificPart.orEmpty(),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun requestDefaultRole() {
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(
                Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                packageName,
            )
        }
        roleLauncher.launch(request)
    }

    private fun isDefaultSmsApp(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }

    private fun requestSmsPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesScreen(
    viewModel: MainViewModel,
    requestDefaultRole: () -> Unit,
    requestSmsPermissions: () -> Unit,
    initialRecipient: String,
) {
    val state by viewModel.state.collectAsState()
    var showPairing by remember { mutableStateOf(false) }
    var showComposer by remember { mutableStateOf(initialRecipient.isNotBlank()) }
    var showDisconnect by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenOnion Messages") },
                navigationIcon = {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.openonion_mark_transparent),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(start = 12.dp).size(38.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            if (state.isDefaultSmsApp) {
                FloatingActionButton(onClick = { showComposer = true }) { Text("+") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                StatusCard(
                    state,
                    requestDefaultRole,
                    requestSmsPermissions,
                    onPair = { showPairing = true },
                    onDisconnect = { showDisconnect = true },
                )
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        when {
                            !state.isDefaultSmsApp -> "Make this your default SMS app to show messages"
                            !state.hasSmsPermissions -> "Grant SMS permissions to show messages"
                            else -> "No messages yet"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 28.dp),
                    )
                }
            } else {
                items(state.messages, key = { it.id }) { message -> MessageRow(message) }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showPairing) {
        PairingDialog(
            busy = state.busy,
            onDismiss = { showPairing = false },
            onPair = { link -> viewModel.pair(link) { showPairing = false } },
        )
    }
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
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Disconnect this agent?") },
            text = {
                Text("This phone will stop uploading new SMS and its device credential will be revoked.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnect = false
                        viewModel.disconnect()
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnect = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: MainUiState,
    requestDefaultRole: () -> Unit,
    requestSmsPermissions: () -> Unit,
    onPair: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Private agent inbox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Messages stay readable on this phone and are encrypted here before oo-api receives them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "SMS only in v1 · MMS and RCS are not supported",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.isDefaultSmsApp) {
                    Button(onClick = requestDefaultRole) { Text("Use as default SMS") }
                } else if (!state.hasSmsPermissions) {
                    Button(onClick = requestSmsPermissions) { Text("Grant SMS permissions") }
                }
                if (state.pairedRecipient == null) {
                    OutlinedButton(onClick = onPair) { Text("Connect agent") }
                }
            }
            state.pairedRecipient?.let {
                Text("Agent ${it.take(10)}…${it.takeLast(6)} · ${state.pendingDeliveries} pending")
                OutlinedButton(onClick = onDisconnect, enabled = !state.busy) {
                    Text("Disconnect agent")
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: LocalSms) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                message.address.ifBlank { "Unknown sender" },
                fontWeight = if (message.unread) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            message.body,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PairingDialog(busy: Boolean, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect an agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Create a one-time pairing link from your ConnectOnion agent, then paste it here.")
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Pairing link") },
                    enabled = !busy,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(link) }, enabled = link.isNotBlank() && !busy) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ComposerDialog(
    initialRecipient: String,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var recipient by remember { mutableStateOf(initialRecipient) }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(recipient, { recipient = it }, label = { Text("Phone number") })
                OutlinedTextField(body, { body = it }, label = { Text("Message") }, minLines = 3)
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
