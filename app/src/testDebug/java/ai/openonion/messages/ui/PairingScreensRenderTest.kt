package ai.openonion.messages.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ai.openonion.messages.MainUiState
import ai.openonion.messages.ui.theme.OpenOnionMessagesTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each pairing screen and saves it to build/pairing-screens/ so the
 * flow can be reviewed as pictures, and asserts the words a person acts on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PairingScreensRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hero() = shot("1-hero") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            PairingHero(busy = false, onScan = {}, onPaste = {})
        }
    }.also {
        compose.onNodeWithText("Scan pairing code").assertIsDisplayed()
        compose.onNodeWithText("co sms pair").assertIsDisplayed()
    }

    @Test
    fun scanner() = shot("2-scanner") {
        Box(Modifier.fillMaxSize().background(Color(0xFF1E1F22)), contentAlignment = Alignment.Center) {
            Image(
                bitmap = terminalQr(),
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(240.dp),
            )
        }
        Viewfinder(locked = false)
        ScannerChrome(onPasteInstead = {}, onClose = {})
    }.also {
        compose.onNodeWithText("Scan the code on your computer").assertIsDisplayed()
    }

    @Test
    fun verify() = shot("3-verify") {
        VerifyOnComputerScreen(code = "482913", onCancel = {})
    }.also {
        compose.onNodeWithText("If your terminal shows the same code, press Enter there.").assertIsDisplayed()
    }

    @Test
    fun connected() = shot("4-connected") {
        ConnectedScreen(
            agentAddress = "0x10e68f6dff39ab1c50cc48ea1c74e7fd6ce7269aa6e8123829b344e57d005508",
            nextLabel = "Turn on SMS forwarding",
            onNext = {},
        )
    }.also {
        compose.onNodeWithText("0x10e6…5508").assertIsDisplayed()
    }

    @Test
    fun askForSms() = home("5-allow-sms", MainUiState(pairedRecipient = agent)).also {
        compose.onNodeWithText("Allow SMS access").assertIsDisplayed()
    }

    @Test
    fun smsBlockedBySideloadRestriction() =
        home("6-sms-blocked", MainUiState(pairedRecipient = agent, smsPermissionBlocked = true)).also {
            compose.onNodeWithText("Open app settings").assertIsDisplayed()
            compose.onNodeWithText("Go to Permissions → SMS → Allow").assertIsDisplayed()
        }

    @Test
    fun forwarding() = home("7-forwarding", MainUiState(pairedRecipient = agent, hasSmsPermission = true)).also {
        compose.onNodeWithText("Keep texting as usual").assertIsDisplayed()
        compose.onNodeWithText("Texts still arrive in your Messages app").assertIsDisplayed()
    }

    @Test
    fun upgradedFromDefaultSmsApp() = home(
        "8-give-back",
        MainUiState(pairedRecipient = agent, hasSmsPermission = true, holdsSmsRole = true),
    ).also {
        compose.onNodeWithText("Choose SMS app").assertIsDisplayed()
    }

    private val agent = "0x10e68f6dff39ab1c50cc48ea1c74e7fd6ce7269aa6e8123829b344e57d005508"

    private fun home(name: String, state: MainUiState) = shot(name) {
        HomeScreen(state, onScan = {}, onPaste = {}, onAllowSms = {}, onChooseTextingApp = {}, onDisconnect = {})
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { OpenOnionMessagesTheme(darkTheme = false) { content() } }
        val dir = File("build/pairing-screens").apply { mkdirs() }
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A light-on-dark QR, the way a dark-theme terminal prints it. */
    private fun terminalQr() = QRCodeWriter()
        .encode("openonion://sms/pair?v=2&id=preview", BarcodeFormat.QR_CODE, 33, 33)
        .let { matrix ->
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                    setPixel(x, y, if (matrix[x, y]) 0xFF1E1F22.toInt() else 0xFFE8E8E8.toInt())
                }
            }.asImageBitmap()
        }
}
