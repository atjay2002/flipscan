package app.flipscan.ui

import androidx.compose.foundation.background
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

@Composable
fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    )
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
