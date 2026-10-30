package app.flipscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.flipscan.data.CardLayout
import app.flipscan.data.Downloads
import app.flipscan.data.ExportSettings
import app.flipscan.data.Filter
import app.flipscan.data.Format
import app.flipscan.data.Mode
import app.flipscan.data.Naming
import app.flipscan.data.Orientation
import app.flipscan.data.PageEditor
import app.flipscan.data.Paper
import app.flipscan.data.SavedExport
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.SettingsStore
import app.flipscan.export.Exporter
import app.flipscan.export.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExportFeedback(val ok: Boolean, val title: String, val body: String)

@Composable
fun ResultScreen(scanId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var scan by remember(scanId) { mutableStateOf<Scan?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(scanId) { scan = withContext(Dispatchers.IO) { ScanStore.get(ctx, scanId) } }
    val s = scan
    if (s == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // The scan is hoisted here so a rename survives recomposition of the whole screen.
        ResultContent(s, onBack, onScanChanged = { scan = it })
    }
}

@Composable
private fun ResultContent(scan: Scan, onBack: () -> Unit, onScanChanged: (Scan) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var previews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var cropIndex by remember { mutableStateOf<Int?>(null) }
    var rev by remember { mutableIntStateOf(0) } // bumps after an edit to rebuild the preview
    var feedback by remember { mutableStateOf<ExportFeedback?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Name of the file this screen writes. Starts as the scan name and is remembered per scan,
    // so switching format (PDF -> JPG) keeps whatever the user typed.
    var exportName by remember(scan.id) { mutableStateOf(scan.name) }

    fun update(n: ExportSettings) {
        settings = n
        SettingsStore.save(ctx, n)
    }

    /** After a successful save/share, show the confirmation for a moment and go back to Home. */
    fun leaveAfter(delayMs: Long) {
        leaving = true
        scope.launch {
            delay(delayMs)
            onBack()
        }
    }

    // Preview is rebuilt in the background whenever a setting changes (small bitmaps only).
    LaunchedEffect(scan.id, settings, rev) {
        delay(120) // debounce slider drags
        previews = withContext(Dispatchers.Default) { Exporter.previews(ctx, scan, settings) }
    }

    fun export() {
        scope.launch {
            busy = "Creating file…"
            feedback = null
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val res = Exporter.render(ctx, scan, settings, exportName)
                    // Downloads never overwrites; it comes back with the names actually written.
                    val saved = Downloads.save(ctx, res.files)
                    var updated = scan
                    val now = System.currentTimeMillis()
                    saved.forEach { s ->
                        updated = ScanStore.addExport(ctx, updated, SavedExport(s.name, s.uri, s.mime, now))
                    }
                    res to (saved to updated)
                }
            }
            busy = null
            feedback = r.fold(
                { (res, written) ->
                    val (saved, updated) = written
                    onScanChanged(updated) // "Open" on Home can now show this exact file
                    val kb = res.files.sumOf { f -> f.length() } / 1024
                    // Long JPG exports produce many files; list a few so the banner stays compact.
                    val names = saved.take(3).joinToString("\n") { it.name } +
                        if (saved.size > 3) "\n+${saved.size - 3} more" else ""
                    ExportFeedback(
                        ok = true,
                        title = "Saved to Downloads/FlipScan",
                        body = names + "\n${res.files.size} file(s) · $kb KB" + (res.note?.let { "\n$it" } ?: "")
                    )
                },
                { err -> ExportFeedback(false, "Export failed", err.message ?: "Unknown error") }
            )
            if (r.isSuccess) leaveAfter(1400)
        }
    }

    fun share() {
        scope.launch {
            busy = "Preparing…"
            feedback = null
            val r = withContext(Dispatchers.IO) { runCatching { Exporter.render(ctx, scan, settings, exportName).files } }
            busy = null
            r.onSuccess { files ->
                val sent = runCatching { Share.send(ctx, files) }
                feedback = if (sent.isSuccess) {
                    ExportFeedback(true, "Ready to share", "${files.size} file(s) generated on this device")
                } else {
                    ExportFeedback(false, "Could not share", sent.exceptionOrNull()?.message ?: "No app can receive this file")
                }
                if (sent.isSuccess) leaveAfter(900)
            }.onFailure {
                feedback = ExportFeedback(false, "Could not share", it.message ?: "Unknown error")
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            export()
        } else {
            feedback = ExportFeedback(false, "Storage permission needed", "Allow storage access to save into Downloads on this Android version.")
        }
    }

    fun onSave() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = scan.name,
                subtitle = (if (scan.mode == Mode.CARDS) "ID card" else "Document") + " · ${scan.pages.size} page(s)",
                onBack = onBack
            ) {
                IconButton(onClick = { renaming = true }, enabled = busy == null && !leaving) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename scan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PreviewStrip(scan, previews, Modifier.padding(horizontal = 16.dp))

            PageTools(
                scan = scan,
                enabled = busy == null && !leaving,
                onRotate = { i ->
                    scope.launch {
                        busy = "Rotating…"
                        withContext(Dispatchers.IO) {
                            PageEditor.rotate(ctx, scan, i)
                            runCatching { Exporter.writeThumb(ctx, scan) }
                        }
                        busy = null
                        rev++
                    }
                },
                onCrop = { cropIndex = it }
            )

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PaperPanel(scan, settings, ::update)
                OutputPanel(scan, settings, exportName, { exportName = it }, ::update)
            }

            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(tween(motionMs(160))) + expandVertically(tween(motionMs(200))),
                exit = fadeOut(tween(motionMs(120))) + shrinkVertically(tween(motionMs(160)))
            ) {
                feedback?.let { f ->
                    ExportBanner(
                        feedback = f,
                        onDismiss = { feedback = null },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            BottomActionBar {
                Button(
                    onClick = ::onSave,
                    modifier = Modifier.weight(1f),
                    enabled = busy == null && !leaving,
                    shape = MaterialTheme.shapes.small
                ) {
                    DownloadIcon(Modifier.size(17.dp), MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(7.dp))
                    Text("Save")
                }
                OutlinedButton(
                    onClick = ::share,
                    modifier = Modifier.weight(1f),
                    enabled = busy == null && !leaving,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Share")
                }
            }
        }

        AnimatedVisibility(
            visible = busy != null,
            enter = fadeIn(tween(motionMs(140))),
            modifier = Modifier.fillMaxSize()
        ) {
            BusyOverlay(busy ?: "", Modifier.fillMaxSize())
        }

        cropIndex?.let { i ->
            CropScreen(scan, i) { changed ->
                cropIndex = null
                if (changed) rev++
            }
        }

        if (renaming) {
            ScanRenameDialog(
                scan = scan,
                onDismiss = { renaming = false },
                onRenamed = { updated ->
                    renaming = false
                    // Keep the export-file field in step while the user has not typed their own name.
                    if (exportName == scan.name) exportName = updated.name
                    onScanChanged(updated)
                }
            )
        }
    }
}

@Composable
private fun PreviewStrip(scan: Scan, previews: List<Bitmap>, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
            if (previews.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Text(
                        "Building preview…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            } else {
                LazyRow(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(previews.size, key = { it }) { i ->
                        val bmp = previews[i]
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        Box(contentAlignment = Alignment.TopStart) {
                            Image(
                                image,
                                contentDescription = "Page ${i + 1} preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(bmp.width / bmp.height.toFloat())
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            )
                            Text(
                                if (scan.mode == Mode.CARDS) (if (i == 0) "FRONT" else "BACK") else "PAGE ${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTools(scan: Scan, enabled: Boolean, onRotate: (Int) -> Unit, onCrop: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scan.pages.indices.forEach { i ->
            val name = if (scan.mode == Mode.CARDS) (if (i == 0) "Front" else "Back") else "Page ${i + 1}"
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { onRotate(i) }, enabled = enabled, modifier = Modifier.size(38.dp)) {
                        RotateIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface, label = "Rotate $name")
                    }
                    IconButton(onClick = { onCrop(i) }, enabled = enabled, modifier = Modifier.size(38.dp)) {
                        CropIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface, label = "Crop $name")
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperPanel(scan: Scan, s: ExportSettings, update: (ExportSettings) -> Unit) {
    Panel {
        SectionLabel("Paper size")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Paper.entries.forEach { p ->
                ChoiceChip(selected = s.paper == p, label = p.label) { update(s.copy(paper = p)) }
            }
        }
        if (s.paper == Paper.CUSTOM) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmField("Width (mm)", s.customW, Modifier.weight(1f)) { update(s.copy(customW = it)) }
                MmField("Height (mm)", s.customH, Modifier.weight(1f)) { update(s.copy(customH = it)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Orientation")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Orientation.entries.forEach { o ->
                ChoiceChip(selected = s.orientation == o, label = o.label) { update(s.copy(orientation = o)) }
            }
        }
        if (scan.mode == Mode.CARDS) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("Card layout")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardLayout.entries.forEach { l ->
                    ChoiceChip(selected = s.layout == l, label = l.label) { update(s.copy(layout = l)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Card size")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(selected = s.realSize, label = "Real size (85.6 × 54 mm)") { update(s.copy(realSize = true)) }
                ChoiceChip(selected = !s.realSize, label = "Fit to page") { update(s.copy(realSize = false)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cut line", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Thin guide around each card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = s.cutLine, onCheckedChange = { update(s.copy(cutLine = it)) })
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Margin")
        MarginSlider(s, update)
    }
}

@Composable
private fun OutputPanel(
    scan: Scan,
    s: ExportSettings,
    exportName: String,
    onExportName: (String) -> Unit,
    update: (ExportSettings) -> Unit
) {
    Panel {
        SectionLabel("Filter")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Filter.entries.forEach { f ->
                ChoiceChip(selected = s.filter == f, label = f.label) { update(s.copy(filter = f)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("File format")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Format.entries.forEach { f ->
                ChoiceChip(selected = s.format == f, label = f.label) { update(s.copy(format = f)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Size limit")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 200, 500, 1000).forEach { kb ->
                ChoiceChip(selected = s.targetKb == kb, label = if (kb == 0) "No limit" else if (kb >= 1000) "${kb / 1000} MB" else "$kb KB") {
                    update(s.copy(targetKb = kb))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.targetKb == 0) "Full resolution." else "Resolution and JPEG quality are reduced only as much as needed to fit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("File name")
        OutlinedTextField(
            value = exportName,
            onValueChange = onExportName,
            singleLine = true,
            label = { Text("Export file name") },
            supportingText = {
                Text(
                    if (Naming.isBlank(exportName)) {
                        "Leave empty to use the scan name: ${Naming.preview("", s.format.ext, scan.name)}"
                    } else {
                        "Saves as ${Naming.preview(exportName, s.format.ext, scan.name)}"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Existing files are never overwritten — a number is added instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun MarginSlider(s: ExportSettings, update: (ExportSettings) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Margin", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            "${s.marginMm.toInt()} mm",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = s.marginMm,
        onValueChange = { update(s.copy(marginMm = it)) },
        valueRange = 0f..25f,
        steps = 24
    )
}

@Composable
private fun ExportBanner(feedback: ExportFeedback, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Green readable on both themes (the Material palette has no "success" role).
    val accent = if (feedback.ok) {
        if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (feedback.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(feedback.title, style = MaterialTheme.typography.titleSmall, color = accent)
                Text(
                    feedback.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
