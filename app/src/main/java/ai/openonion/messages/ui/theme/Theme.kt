package ai.openonion.messages.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = OpenOnionGreen,
    onPrimary = OpenOnionWhite,
    primaryContainer = OpenOnionGreenSoft,
    onPrimaryContainer = OpenOnionBlack,
    secondary = OpenOnionGreen,
    onSecondary = OpenOnionWhite,
    tertiary = OpenOnionGreen,
    background = Paper,
    surface = OpenOnionWhite,
    surfaceVariant = OpenOnionGreenSoft,
    outline = Hairline,
    outlineVariant = Hairline,
    onBackground = OpenOnionBlack,
    onSurface = OpenOnionBlack,
    onSurfaceVariant = InkMuted,
    error = OpenOnionBlack,
    onError = OpenOnionWhite,
    errorContainer = OpenOnionBlack,
    onErrorContainer = OpenOnionWhite,
)

private val ColorDarkOutline = androidx.compose.ui.graphics.Color(0xFF354039)

private val DarkColors = darkColorScheme(
    primary = OpenOnionGreenBright,
    onPrimary = OpenOnionBlack,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF103B27),
    onPrimaryContainer = NightInk,
    secondary = OpenOnionGreenBright,
    tertiary = OpenOnionGreenBright,
    background = Night,
    surface = NightSurface,
    surfaceVariant = NightSurfaceRaised,
    outline = ColorDarkOutline,
    outlineVariant = ColorDarkOutline,
    onBackground = NightInk,
    onSurface = NightInk,
    onSurfaceVariant = NightMuted,
    error = NightInk,
    onError = Night,
    errorContainer = NightSurfaceRaised,
    onErrorContainer = NightInk,
)

private val OpenOnionTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.35).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
)

private val OpenOnionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun OpenOnionMessagesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OpenOnionTypography,
        shapes = OpenOnionShapes,
        content = content,
    )
}
