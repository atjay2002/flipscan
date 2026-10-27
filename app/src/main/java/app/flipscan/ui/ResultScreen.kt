package app.flipscan.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.flipscan.data.CardLayout
import app.flipscan.data.Downloads
import app.flipscan.data.ExportSettings
import app.flipscan.data.Filter
import app.flipscan.data.Format
import app.flipscan.data.Mode
import app.flipscan.data.Paper
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.SettingsStore
import app.flipscan.export.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(scanId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scan by produceState<Scan?>(null, scanId) { value = withContext(Dispatchers.IO) { ScanStore.get(ctx, scanId) } }
    BackHandler(onBack = onBack)
    val s = scan
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        ResultContent(s, onBack)
    }
}

@Composable
private fun ResultContent(scan: Scan, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var previews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun update(n: ExportSettings) {
        settings = n
        SettingsStore.save(ctx, n)
    }

    // Preview is rebuilt in the background whenever a setting changes (small bitmaps only).
    LaunchedEffect(scan.id, settings) {
        delay(120) // debounce slider drags
        previews = withContext(Dispatchers.Default) { Exporter.previews(ctx, scan, settings) }
    }

    fun export() {
        scope.launch {
            busy = "Creating file…"
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val res = Exporter.render(ctx, scan, settings)
                    Downloads.save(ctx, res.files)
                    res
                }
            }
            busy = null
            val msg = r.fold(
                {
                    val kb = it.files.sumOf { f -> f.length() } / 1024
                    "Saved ${it.files.size} file(s), $kb KB, to Downloads/FlipScan" + (it.note?.let { n -> "\n$n" } ?: "")
                },
                { "Export failed: ${it.message}" }
            )
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) export() else Toast.makeText(ctx, "Storage permission is needed to save to Downloads on this Android version", Toast.LENGTH_LONG).show()
    }

    fun onSave() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(scan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }

            Box(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFFE9ECEF)), contentAlignment = Alignment.Center) {
                if (previews.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyRow(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(previews.size) { i ->
                            val bmp = previews[i]
                            val image = remember(bmp) { bmp.asImageBitmap() }
                            Image(
                                image, contentDescription = "Page ${i + 1}", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxHeight().aspectRatio(bmp.width / bmp.height.toFloat())
                                    .border(1.dp, Color(0xFFB0B7BE))
                            )
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsControls(scan, settings, ::update)
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::onSave, modifier = Modifier.weight(1f), enabled = busy == null) { Text("Save ${settings.format.label} to Downloads") }
            }
        }
        busy?.let { BusyOverlay(it, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun SettingsControls(scan: Scan, s: ExportSettings, update: (ExportSettings) -> Unit) {
    Choice("Paper size", Paper.entries, s.paper, { it.label }) { update(s.copy(paper = it)) }
    if (s.paper == Paper.CUSTOM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmField("Width (mm)", s.customW, Modifier.weight(1f)) { update(s.copy(customW = it)) }
            MmField("Height (mm)", s.customH, Modifier.weight(1f)) { update(s.copy(customH = it)) }
        }
    }
    if (scan.mode == Mode.CARDS) {
        Choice("Layout", CardLayout.entries, s.layout, { it.label }) { update(s.copy(layout = it)) }
        Choice("Card size", listOf(true, false), s.realSize, { if (it) "Real size (85.6 × 54 mm)" else "Fit to page" }) {
            update(s.copy(realSize = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cut line", Modifier.weight(1f))
            Switch(checked = s.cutLine, onCheckedChange = { update(s.copy(cutLine = it)) })
        }
    }
    Text("Margin: ${s.marginMm.toInt()} mm")
    Slider(value = s.marginMm, onValueChange = { update(s.copy(marginMm = it)) }, valueRange = 0f..25f)

    Choice("Filter", Filter.entries, s.filter, { it.label }) { update(s.copy(filter = it)) }
    Choice("Format", Format.entries, s.format, { it.label }) { update(s.copy(format = it)) }
    Choice("Size limit", listOf(0, 200, 500, 1000), s.targetKb, { if (it == 0) "No limit" else if (it >= 1000) "${it / 1000} MB" else "$it KB" }) {
        update(s.copy(targetKb = it))
    }
}

@Composable
fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

@Composable
private fun MmField(label: String, value: Float, modifier: Modifier, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            val v = t.toFloatOrNull()
            if (v != null && v in 30f..1000f) onValue(v)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}
