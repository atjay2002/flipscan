package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.SettingsStore
import app.flipscan.export.Exporter
import app.flipscan.export.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(onDoubleSide: () -> Unit, onRegular: () -> Unit, onOpen: (Scan) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scans by remember { mutableStateOf<List<Scan>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<Scan?>(null) }
    var deleting by remember { mutableStateOf<Scan?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    // Reads small JSON files on a background thread; runs again whenever Home is shown.
    LaunchedEffect(reload) { scans = withContext(Dispatchers.IO) { ScanStore.list(ctx) } }

    fun share(scan: Scan) {
        scope.launch {
            busy = "Preparing…"
            val r = withContext(Dispatchers.IO) {
                runCatching { Exporter.render(ctx, scan, SettingsStore.load(ctx)).files }
            }
            busy = null
            r.onSuccess { Share.send(ctx, it) }
                .onFailure { Toast.makeText(ctx, "Could not share: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // Soft entrance: content fades and slides up once.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(Indigo, Violet, Teal)))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardArt(Modifier.size(76.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("FlipScan", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text("Both sides on one page.\nOffline, no ads.", color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            Column(
                Modifier.weight(1f).padding(horizontal = 20.dp)
                    .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f }
            ) {
                Spacer(Modifier.height(16.dp))
                BigButton("Double Side", "Aadhaar, PAN, DL, voter ID, college ID", listOf(Indigo, Violet), true, onDoubleSide)
                Spacer(Modifier.height(12.dp))
                BigButton("Regular", "Passbooks, certificates, any document", listOf(Teal, Color(0xFF0EA5E9)), false, onRegular)

                Text("Recent scans", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                if (scans.isEmpty()) {
                    Text("Nothing yet. Your scans appear here.")
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(count = scans.size, key = { scans[it].id }) { i ->
                            val scan = scans[i]
                            ScanRow(scan, onOpen = { onOpen(scan) }, onShare = { share(scan) },
                                onRename = { renaming = scan }, onDelete = { deleting = scan })
                        }
                    }
                }
            }
        }
        busy?.let { BusyOverlay(it, Modifier.fillMaxSize()) }
    }

    renaming?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ScanStore.rename(ctx, s, text) }
                        renaming = null
                        reload++
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${s.name}\"?") },
            text = { Text("This removes the scan from the app. Files you already saved to Downloads are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ScanStore.delete(ctx, s) }
                        deleting = null
                        reload++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScanRow(scan: Scan, onOpen: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .pressable(onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageThumb(ScanStore.thumbFile(ctx, scan.id), Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)), maxSide = 240)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(scan.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.createdAt))
            Text("$date · ${scan.pages.size} page(s)", style = MaterialTheme.typography.bodySmall)
        }
        Box {
            TextButton(onClick = { menu = true }) { Text("⋮") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun BigButton(title: String, subtitle: String, colors: List<Color>, cards: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(colors))
            .pressable(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniIcon(cards, Modifier.size(44.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

/** Two stacked cards (Double Side) or a sheet of paper (Regular). */
@Composable
private fun MiniIcon(cards: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(5.dp.toPx())
        if (cards) {
            drawRoundRect(Color.White, Offset(0f, 2.dp.toPx()), Size(size.width, size.height * 0.4f), r)
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(0f, size.height * 0.56f), Size(size.width, size.height * 0.4f), r)
        } else {
            drawRoundRect(Color.White, Offset(size.width * 0.15f, 0f), Size(size.width * 0.7f, size.height), r)
            for (i in 1..4) {
                drawRoundRect(
                    Color(0xFF0EA5E9).copy(alpha = 0.5f),
                    Offset(size.width * 0.25f, size.height * (0.15f + i * 0.16f)), Size(size.width * 0.5f, 3.dp.toPx()), CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/** Header illustration: two cards gently floating in opposite directions. */
@Composable
private fun CardArt(modifier: Modifier) {
    val float = rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatValue"
    )
    Canvas(modifier) {
        val dy = float.value * 6.dp.toPx()
        val r = CornerRadius(8.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(0f, size.height * 0.5f + 4.dp.toPx() - dy), Size(size.width, size.height * 0.4f), r)
        drawRoundRect(Color.White, Offset(0f, size.height * 0.08f + dy), Size(size.width, size.height * 0.4f), r)
        drawRoundRect(Indigo.copy(alpha = 0.35f), Offset(8.dp.toPx(), size.height * 0.08f + dy + 8.dp.toPx()), Size(size.width * 0.35f, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
    }
}
