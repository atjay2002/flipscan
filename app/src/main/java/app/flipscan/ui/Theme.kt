package app.flipscan.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

// ---------------------------------------------------------------------------------------------
// Brand palette. Screens use MaterialTheme.colorScheme for anything that must follow light/dark;
// these raw colors are for the fixed dark camera chrome and for accent artwork.
// ---------------------------------------------------------------------------------------------
val Indigo = Color(0xFF4F46E5)
val IndigoDeep = Color(0xFF3730A3)
val Violet = Color(0xFF7C3AED)
val Teal = Color(0xFF0F9D8F)
val Amber = Color(0xFFF5A524)
val Success = Color(0xFF16A34A)
val Danger = Color(0xFFDC2626)

/** Immersive camera surfaces (always dark, independent of the system theme). */
val CameraTop = Color(0xFF0B1220)
val CameraBottom = Color(0xFF141C2F)
val CameraCard = Color(0xFF18223A)
val CameraLine = Color(0xFF2C3A57)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAFE),
    onPrimaryContainer = Color(0xFF23205F),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F3EF),
    onSecondaryContainer = Color(0xFF064E47),
    tertiary = Violet,
    onTertiary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF5A6474),
    outline = Color(0xFFD3D9E4),
    outlineVariant = Color(0xFFE5E9F1),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E56),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1065),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1D2740),
    onSurfaceVariant = Color(0xFFA8B2C4),
    outline = Color(0xFF38455F),
    outlineVariant = Color(0xFF232D45),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Slightly tighter, more editorial type scale than the Material defaults. */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 1 = full motion, 0 = reduce/disable. Follows the system "Remove animations" setting
 * (animator duration scale), so the app respects it without adding a preference.
 */
val LocalMotionScale = staticCompositionLocalOf { 1f }

@Composable
fun rememberMotionScale(): Float {
    val ctx = LocalContext.current
    fun read() = runCatching {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f).coerceIn(0f, 1f)

    var scale by remember(ctx) { mutableFloatStateOf(read()) }
    // Pick up a change made in system settings while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scale = read() }
    return scale
}

/** Scales a base duration by the system animation preference (0 ms when animations are off). */
@Composable
fun motionMs(base: Int): Int = (base * LocalMotionScale.current).toInt().coerceIn(0, base)

@Composable
fun FlipScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scale = rememberMotionScale()
    CompositionLocalProvider(LocalMotionScale provides scale) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
