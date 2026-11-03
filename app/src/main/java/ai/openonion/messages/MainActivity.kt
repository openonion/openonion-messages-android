package ai.openonion.messages

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.openonion.messages.ui.MessagesScreen
import ai.openonion.messages.ui.theme.OpenOnionMessagesTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // No dialog and no rationale means Android decided for the user: either
        // "don't ask again", or restricted settings on a sideloaded install.
        val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)
        viewModel.onSmsPermissionResult(granted, canAskAgain)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenOnionMessagesTheme {
                MessagesScreen(
                    viewModel = viewModel,
                    requestSmsPermission = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                    openAppSettings = ::openAppSettings,
                    openDefaultAppsSettings = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                )
            }
        }
        handlePairingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
    }

    private fun handlePairingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "openonion" && uri.host == "sms" && uri.path == "/pair") {
            viewModel.pair(uri.toString()) {}
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
