package ai.openonion.messages.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openonion.messages.R
import ai.openonion.messages.ui.theme.OpenOnionBlack
import ai.openonion.messages.ui.theme.OpenOnionGreen
import ai.openonion.messages.ui.theme.OpenOnionGreenBright
import ai.openonion.messages.ui.theme.OpenOnionGreenSoft

/** First thing a new user sees: one command on the computer, one button on the phone. */
@Composable
fun PairingHero(busy: Boolean, onScan: () -> Unit, onPaste: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(OpenOnionGreenSoft),
            ) {
                Icon(
                    painterResource(R.drawable.ic_qr_scan),
                    contentDescription = null,
                    tint = OpenOnionGreen,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect your Agent", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "On your computer, run this command. Then scan the code it shows.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CommandChip("co sms pair")
            Button(
                onClick = onScan,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Connecting…")
                } else {
                    Icon(painterResource(R.drawable.ic_qr_scan), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("Scan pairing code")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Encrypted on this phone. Only your Agent can read it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPaste, enabled = !busy) { Text("Paste link") }
            }
        }
    }
}

@Composable
private fun CommandChip(command: String) {
    Surface(color = OpenOnionBlack, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("$ ", fontFamily = FontFamily.Monospace, color = OpenOnionGreenBright, fontSize = 16.sp)
            Text(command, fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 16.sp)
        }
    }
}

/** The phone and the terminal show the same six digits; the person approves on the computer. */
@Composable
fun VerifyOnComputerScreen(code: String, onCancel: () -> Unit) {
    FullScreen {
        Spacer(Modifier.weight(1f))
        Text(
            "CHECK YOUR COMPUTER",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            code.forEachIndexed { index, digit ->
                if (index == 3) Spacer(Modifier.size(10.dp))
                DigitTile(digit)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "If your terminal shows the same code, press Enter there.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "This makes sure the phone you're holding is the one your Agent trusts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(
                "Waiting for approval",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun DigitTile(digit: Char) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OpenOnionGreenSoft),
    ) {
        Text(
            digit.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = OpenOnionGreen,
        )
    }
}

/** The moment it works. The next step is offered right here, so nobody has to hunt for it. */
@Composable
fun ConnectedScreen(agentAddress: String, nextLabel: String, onNext: () -> Unit) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    FullScreen {
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp).scale(pop.value).clip(CircleShape).background(OpenOnionGreen),
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("Connected", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            agentAddress.compactAgentAddress(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Your Agent will receive new texts from now on, encrypted.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { Text(nextLabel) }
    }
}

@Composable
private fun FullScreen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun String.compactAgentAddress(): String =
    if (length <= 16) this else "${take(6)}…${takeLast(4)}"
