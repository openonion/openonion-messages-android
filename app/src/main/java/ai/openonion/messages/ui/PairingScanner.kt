package ai.openonion.messages.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.openonion.messages.R
import ai.openonion.messages.network.PairingLink
import ai.openonion.messages.ui.theme.OpenOnionGreenBright
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Reads a QR code from one camera frame's luminance (Y) plane.
 *
 * Terminals print the code light-on-dark or dark-on-light depending on their
 * theme, so inverted codes are accepted too.
 */
fun decodeQr(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
    return try {
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), QR_HINTS).text
    } catch (_: ReaderException) {
        null // ZXing signals "no readable code in this frame" by throwing.
    }
}

private val QR_HINTS = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
    DecodeHintType.ALSO_INVERTED to true,
)

/** What a scanned code means to the person holding the phone. */
sealed interface ScanVerdict {
    data class Pair(val link: String) : ScanVerdict
    data object NotOurs : ScanVerdict
    data object Expired : ScanVerdict
}

fun judgeScan(text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): ScanVerdict {
    val link = runCatching { PairingLink.parse(text) }.getOrNull() ?: return ScanVerdict.NotOurs
    if (link.isSignedChallenge && link.expiresAt <= nowEpochSeconds) return ScanVerdict.Expired
    return ScanVerdict.Pair(text.trim())
}

@Composable
fun PairingScannerScreen(onLink: (String) -> Unit, onPasteInstead: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            granted -> LiveScanner(onLink = onLink)
            asked -> CameraOff(
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }
        ScannerChrome(onPasteInstead = onPasteInstead, onClose = onClose)
    }
}

@Composable
private fun LiveScanner(onLink: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    CameraPreview(
        onText = { text ->
            if (done) return@CameraPreview
            when (val verdict = judgeScan(text)) {
                is ScanVerdict.Pair -> {
                    done = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onLink(verdict.link)
                }
                ScanVerdict.Expired -> hint = "This code has expired. Run co sms pair again on your computer."
                ScanVerdict.NotOurs -> hint = "That isn't an OpenOnion pairing code."
            }
        },
    )
    Viewfinder(locked = done)
    hint?.let { message ->
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 132.dp), Alignment.BottomCenter) {
            Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(50)) {
                Text(
                    message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(onText: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { image ->
                val plane = image.planes[0]
                val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                val text = decodeQr(bytes, plane.rowStride, image.width, image.height)
                image.close()
                if (text != null) mainExecutor.execute { onText(text) }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, mainExecutor)
        onDispose {
            if (providerFuture.isDone) providerFuture.get().unbindAll()
            analyzerExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Dims everything except a rounded window, and draws green corner brackets around it. */
@Composable
internal fun Viewfinder(locked: Boolean) {
    val pulse by rememberInfiniteTransition(label = "viewfinder").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "corners",
    )
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val side = size.minDimension * 0.72f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        val radius = 28.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.58f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = topLeft,
            size = Size(side, side),
            cornerRadius = CornerRadius(radius),
            blendMode = BlendMode.Clear,
        )
        val arm = side * 0.16f
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val color = OpenOnionGreenBright.copy(alpha = if (locked) 1f else pulse)
        val right = topLeft.x + side
        val bottom = topLeft.y + side
        listOf(
            Triple(Offset(topLeft.x, topLeft.y + arm), Offset(topLeft.x, topLeft.y), Offset(topLeft.x + arm, topLeft.y)),
            Triple(Offset(right - arm, topLeft.y), Offset(right, topLeft.y), Offset(right, topLeft.y + arm)),
            Triple(Offset(right, bottom - arm), Offset(right, bottom), Offset(right - arm, bottom)),
            Triple(Offset(topLeft.x + arm, bottom), Offset(topLeft.x, bottom), Offset(topLeft.x, bottom - arm)),
        ).forEach { (start, corner, end) ->
            val path = Path().apply {
                moveTo(start.x, start.y)
                quadraticTo(corner.x, corner.y, end.x, end.y)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

@Composable
internal fun ScannerChrome(onPasteInstead: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close scanner", tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the code on your computer",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "It appears when you run co sms pair",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onPasteInstead,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) { Text("Paste a link instead") }
    }
}

@Composable
private fun CameraOff(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access is off", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            "The camera is only used to read the pairing code. Nothing is recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.height(48.dp)) { Text("Allow in Settings") }
        Spacer(Modifier.size(1.dp))
    }
}
