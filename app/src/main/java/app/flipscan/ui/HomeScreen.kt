package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flipscan.data.Mode
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
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<Scan?>(null) }
    var deleting by remember { mutableStateOf<Scan?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    // Reads small JSON files on a background thread; runs again whenever Home is shown.
    LaunchedEffect(reload) {
        scans = withContext(Dispatchers.IO) { ScanStore.list(ctx) }
        loaded = true
    }

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

    // Soft entrance: the whole page fades and rises once.
    val enter = remember { Animatable(0f) }
    val enterMs = motionMs(420)
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 36f
            },
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "brand") { BrandHeader() }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(
                        title = "ID card",
                        subtitle = "Aadhaar, PAN, DL, voter ID",
                        filled = true,
                        icon = { CardStackIcon(Modifier.size(26.dp), Color.White) },
                        onClick = onDoubleSide
                    )
                    ActionCard(
                        title = "Document",
                        subtitle = "Passbooks, certificates, notes",
                        filled = false,
                        icon = { DocumentIcon(Modifier.size(26.dp), MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = onRegular
                    )
                }
            }

            item(key = "recentHeader") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent scans", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (scans.isNotEmpty()) CountBadge("${scans.size}")
                }
            }

            if (!loaded) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else if (scans.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "Nothing here yet",
                        "Scans you save stay on this phone and appear here, ready to edit or export again."
                    )
                }
            } else {
                items(scans, key = { it.id }) { scan ->
                    ScanRow(
                        scan = scan,
                        onOpen = { onOpen(scan) },
                        onShare = { share(scan) },
                        onRename = { renaming = scan },
                        onDelete = { deleting = scan }
                    )
                }
            }

            item(key = "privacy") {
                Text(
                    "No internet permission. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = busy != null,
            enter = fadeIn(tween(motionMs(140))),
            modifier = Modifier.fillMaxSize()
        ) {
            BusyOverlay(busy ?: "", Modifier.fillMaxSize())
        }
    }

    renaming?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("Rename scan") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
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
            shape = MaterialTheme.shapes.large,
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
private fun BrandHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Indigo, Violet))),
                contentAlignment = Alignment.Center
            ) {
                BrandMark(Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text("FlipScan", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Offline document scanner",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OfflineChip()
        }
        Text(
            "Both sides.\nOne page.",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Scan a card once and print both sides together, or capture a full document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun OfflineChip() {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Success))
        Spacer(Modifier.width(6.dp))
        Text(
            "Offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    filled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val sub = if (filled) fg.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth().pressable(onClick),
        shape = MaterialTheme.shapes.large,
        color = bg,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (filled) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { icon() }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (filled) fg.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanRow(
    scan: Scan,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().pressable(onOpen),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            PageThumb(
                ScanStore.thumbFile(ctx, scan.id),
                Modifier.size(width = 52.dp, height = 66.dp).clip(RoundedCornerShape(10.dp)),
                maxSide = 240
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(scan.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.createdAt))
                Text(
                    "$date · ${scan.pages.size} page(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    if (scan.mode == Mode.CARDS) "ID CARD" else "DOCUMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (scan.mode == Mode.CARDS) {
                        MaterialTheme.colorScheme.primary
                    } else if (isSystemInDarkTheme()) {
                        Color(0xFF5EEAD4)
                    } else {
                        Color(0xFF0F766E)
                    },
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Scan options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { menu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
