package app.flipscan.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("FlipScan", style = MaterialTheme.typography.headlineLarge)
            Text("Both sides of a card on one page. Offline, no ads.", modifier = Modifier.padding(bottom = 16.dp))
            BigButton("Double Side", "Aadhaar, PAN, DL, voter ID, college ID", onDoubleSide)
            Spacer(Modifier.height(12.dp))
            BigButton("Regular", "Passbooks, certificates, any document", onRegular)

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
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PageThumb(ScanStore.thumbFile(ctx, scan.id), Modifier.size(width = 56.dp, height = 72.dp), maxSide = 240)
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
private fun BigButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(20.dp)) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
