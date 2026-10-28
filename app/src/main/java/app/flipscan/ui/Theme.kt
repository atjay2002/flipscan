package app.flipscan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Teal = Color(0xFF14B8A6)

private val Colors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFFF5F6FB),
    surface = Color.White
)

@Composable
fun FlipScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
