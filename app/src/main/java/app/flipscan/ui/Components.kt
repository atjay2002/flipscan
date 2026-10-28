package app.flipscan.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shutter with a press bounce and a green ring that fills while the document is held steady. */
@Composable
fun ShutterButton(enabled: Boolean, progress: () -> Float = { 0f }, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "shutter"
    )
    Box(
        Modifier
            .size(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val w = 5.dp.toPx()
                val arcTopLeft = Offset(w / 2, w / 2)
                val arcSize = Size(size.width - w, size.height - w)
                drawCircle(Color.White.copy(alpha = 0.35f), style = Stroke(w))
                val p = progress()
                if (p > 0f) {
                    drawArc(Color(0xFF00E676), -90f, 360f * p, false, arcTopLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            .padding(10.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
    )
}

/** White flash drawn over the preview right after a capture. [alpha] is read while drawing. */
@Composable
fun FlashOverlay(alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.graphicsLayer { this.alpha = alpha() }.background(Color.White))
}

/** Clickable with a small bounce when pressed. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, color = Color.White)
    }
}

/** Dims what is behind and shows a spinner; swallows touches while heavy work runs. */
@Composable
fun BusyOverlay(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Color(0x99000000)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text(text, color = Color.White, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Asks before throwing away unsaved scans. */
@Composable
fun DiscardDialog(show: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!show) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard this scan?") },
        text = { Text("The photos you took so far will be deleted.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text("Discard") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Keep scanning") } }
    )
}
