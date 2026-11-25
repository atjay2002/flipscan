package app.flipscan.ui

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flipscan.data.Downloads
import app.flipscan.data.ExportSettings
import app.flipscan.data.Format
import app.flipscan.data.LockerDoc
import app.flipscan.data.LockerDocType
import app.flipscan.data.LockerEdit
import app.flipscan.data.LockerOcr
import app.flipscan.data.LockerStore
import app.flipscan.data.SavedExport
import app.flipscan.data.SettingsStore
import app.flipscan.export.Exporter
import app.flipscan.export.Share
import java.io.File
import java.util.UUID
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A decrypted view of one locker document.
 *
 * Only the current page is decoded at full preview size; the strip uses small bitmaps. Every
 * operation that needs a plaintext file (crop, rotate) works on a temp copy that is re-encrypted
 * and deleted in the same operation. Nothing is written to Downloads and nothing is shared.
 */
@Composable
fun LockerViewerScreen(
    docId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onLock: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by remember(docId) { mutableStateOf<LockerDoc?>(null) }
    var missing by remember(docId) { mutableStateOf(false) }
    var page by remember(docId) { mutableIntStateOf(0) }
    var rev by remember(docId) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var cropEdit by remember { mutableStateOf<LockerEdit?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var canCrop by remember { mutableStateOf(false) }
    var reading by remember(docId) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(Format.PDF) }
    var exportName by remember(docId) { mutableStateOf(doc?.name ?: "") }
    var showingExportDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    // Reading a number is on-device OCR and happens once per document; the flag lives in the
    // encrypted index, so reopening a document does not run recognition again.
    LaunchedEffect(docId) {
        val loaded = withContext(Dispatchers.IO) { LockerStore.get(ctx, docId) }
        doc = loaded
        missing = loaded == null
        if (loaded != null && loaded.type.pins && !loaded.numberChecked) {
            reading = true
            val updated = runCatching { LockerOcr.detectAndStore(ctx, docId) }.getOrNull()
            reading = false
            updated?.let { doc = it }
        }
    }

    val pageCount = doc?.pages ?: 0
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && page >= pageCount) page = pageCount - 1
    }
    LaunchedEffect(docId, page) {
        canCrop = withContext(Dispatchers.IO) { LockerStore.hasOriginal(ctx, docId, page) }
    }

    val bitmap by produceState<Bitmap?>(null, docId, page, rev, pageCount) {
        value = null
        if (pageCount <= 0) return@produceState
        val bytes = withContext(Dispatchers.IO) { LockerStore.pageBytes(ctx, docId, page) }
        value = bytes?.let { withContext(Dispatchers.Default) { decodeSampled(it, 1800) } }
    }

    fun rotate() {
        if (busy != null) return
        message = null
        scope.launch {
            busy = "Rotating…"
            val ok = withContext(Dispatchers.Default) { LockerStore.rotate(ctx, docId, page) }
            busy = null
            if (ok) rev++ else message = "Could not rotate this page."
        }
    }

    fun startCrop() {
        if (busy != null) return
        message = null
        scope.launch {
            busy = "Preparing crop…"
            val edit = withContext(Dispatchers.IO) { LockerStore.stageForEdit(ctx, docId, page) }
            busy = null
            if (edit == null) message = "This page could not be opened for cropping." else cropEdit = edit
        }
    }

    fun readNumber() {
        if (reading) return
        message = null
        scope.launch {
            reading = true
            val updated = runCatching { LockerOcr.detectAndStore(ctx, docId) }.getOrNull()
            reading = false
            if (updated != null) doc = updated else message = "Could not read the number from this page."
        }
    }

    /**
     * Decrypts all pages into a fresh directory: cacheDir/locker_export/<docId>/<exportId>/.
     *
     * A new directory per export means a second export never deletes the files an in-flight
     * share of the same document is still reading. The caller owns [Pair.first] and must delete
     * it; the startup sweep removes the whole locker_export tree after a process death.
     */
    fun decryptPagesToTemp(doc: LockerDoc): Pair<File, List<File>> {
        val exportDir = File(ctx.cacheDir, "locker_export/${doc.id}/${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
        if (!exportDir.mkdirs()) return exportDir to emptyList()
        val pages = (0 until doc.pages).map { i ->
            val out = File(exportDir, "page_$i.jpg")
            LockerStore.pageBytes(ctx, doc.id, i)?.let { out.writeBytes(it) }
            out
        }.filter { it.exists() }
        return exportDir to pages
    }

    fun export() {
        val current = doc ?: return
        exporting = true
        message = null
        scope.launch {
            var tempDir: File? = null
            try {
                // Decryption and disk writes stay off the UI thread.
                val (dir, pages) = withContext(Dispatchers.IO) { decryptPagesToTemp(current) }
                tempDir = dir
                if (pages.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        exporting = false
                        message = "Could not decrypt pages for export."
                    }
                    return@launch
                }
                val settings = SettingsStore.load(ctx)
                val res = withContext(Dispatchers.Default) {
                    Exporter.renderPages(
                        ctx = ctx,
                        pages = pages,
                        name = current.name,
                        s = settings.copy(format = exportFormat),
                        baseName = exportName.ifEmpty { current.name },
                        cardMode = current.type.cards
                    )
                }
                val saved = withContext(Dispatchers.IO) { Downloads.save(ctx, res.files) }
                withContext(Dispatchers.Main) {
                    exporting = false
                    showingExportDialog = false
                    saved.forEach { s ->
                        val updated = LockerStore.addExport(
                            ctx,
                            current.id,
                            SavedExport(name = s.name, uri = s.uri, mime = s.mime, at = System.currentTimeMillis())
                        )
                        if (updated != null) doc = updated
                    }
                    val names = saved.take(3).joinToString("\n") { it.name } +
                        if (saved.size > 3) "\n+${saved.size - 3} more" else ""
                    message = "Saved to Downloads/FlipScan\n$names"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exporting = false
                    message = "Export failed: ${e.message ?: "Unknown error"}"
                }
            } finally {
                // Plaintext must not outlive the export, even if the work was cancelled.
                tempDir?.let { dir ->
                    withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
                }
            }
        }
    }

    fun share() {
        val current = doc ?: return
        exporting = true
        message = null
        scope.launch {
            var tempDir: File? = null
            try {
                val (dir, pages) = withContext(Dispatchers.IO) { decryptPagesToTemp(current) }
                tempDir = dir
                if (pages.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        exporting = false
                        message = "Could not decrypt pages for sharing."
                    }
                    return@launch
                }
                val settings = SettingsStore.load(ctx)
                val files = withContext(Dispatchers.Default) {
                    Exporter.renderPages(
                        ctx = ctx,
                        pages = pages,
                        name = current.name,
                        s = settings.copy(format = exportFormat),
                        baseName = exportName.ifEmpty { current.name },
                        cardMode = current.type.cards
                    ).files
                }
                withContext(Dispatchers.Main) {
                    Share.send(ctx, files)
                    exporting = false
                    showingExportDialog = false
                    message = "${files.size} file(s) ready to share."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exporting = false
                    message = "Could not share: ${e.message ?: "Unknown error"}"
                }
            } finally {
                tempDir?.let { dir ->
                    withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
                }
            }
        }
    }

    fun openExport(saved: SavedExport) {
        val uri = Uri.parse(saved.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, saved.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (runCatching { ctx.startActivity(intent) }.isFailure) {
            message = "No app can open this file type."
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).ambientGlow()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = doc?.name ?: "Secure document",
                subtitle = doc?.let {
                    "${it.type.label} · ${if (it.pages == 1) "1 page" else "${it.pages} pages"} · encrypted"
                },
                onBack = onBack
            ) {
                IconButton(onClick = onLock) {
                    LockIcon(
                        Modifier.size(20.dp),
                        MaterialTheme.colorScheme.onSurface,
                        label = "Lock Digital Locker"
                    )
                }
            }

            when {
                missing -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "This document is no longer in the locker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                doc == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    PageStage(
                        bitmap = bitmap,
                        page = page,
                        pages = pageCount,
                        onPrevious = { if (page > 0) page-- },
                        onNext = { if (page < pageCount - 1) page++ },
                        modifier = Modifier.weight(1f)
                    )

                    if (pageCount > 1) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageCount, key = { it }) { i ->
                                val shape = RoundedCornerShape(8.dp)
                                Box(
                                    Modifier.size(width = 44.dp, height = 56.dp)
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            if (i == page) 2.dp else 1.dp,
                                            if (i == page) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape
                                        )
                                        .pressable({ page = i })
                                ) {
                                    LockerPageThumb(docId, i, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolButton("Rotate", enabled = busy == null && !exporting, onClick = ::rotate) {
                            RotateIcon(Modifier.size(17.dp), MaterialTheme.colorScheme.onSurface)
                        }
                        ToolButton("Crop", enabled = busy == null && !exporting && canCrop, onClick = ::startCrop) {
                            CropIcon(Modifier.size(17.dp), MaterialTheme.colorScheme.onSurface)
                        }
                        ToolButton("Rename", enabled = busy == null && !exporting, onClick = { renaming = true }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        ToolButton("Export", enabled = busy == null && !exporting, onClick = { showingExportDialog = true }) {
                            Box(Modifier.size(17.dp))
                        }
                        ToolButton("Share", enabled = busy == null && !exporting, onClick = { showingExportDialog = true }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        ToolButton("Delete", enabled = busy == null && !exporting, onClick = { deleting = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        )
                    }

                    val current = doc
                    if (current != null) {
                        DigitalPinCard(
                            doc = current,
                            reading = reading,
                            onDetect = ::readNumber,
                            onChanged = { doc = it },
                            onError = { message = it }
                        )

                        // Previously exported files that can be opened again.
                        if (current.exports.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 8.dp)
                            ) {
                                Text(
                                    "Exported copies",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Column(Modifier.fillMaxWidth()) {
                                    current.exports.forEach { saved ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .glass(MaterialTheme.shapes.medium)
                                                .pressable(onClick = { openExport(saved) })
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.size(17.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    saved.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    saved.mime,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Encrypted at rest. Exporting creates a plaintext copy in Downloads/FlipScan; " +
                            "the copy is not protected by the locker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }

        busy?.let { BusyOverlay(it, Modifier.fillMaxSize()) }

        if (showingExportDialog) {
            val current = doc
            AlertDialog(
                onDismissRequest = { showingExportDialog = false },
                title = { Text("Export document") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = exportName,
                            onValueChange = { exportName = it },
                            singleLine = true,
                            label = { Text("File name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Format", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                            FormatButton("PDF", selected = exportFormat == Format.PDF) {
                                exportFormat = Format.PDF
                            }
                            FormatButton("JPG", selected = exportFormat == Format.JPG) {
                                exportFormat = Format.JPG
                            }
                            FormatButton("PNG", selected = exportFormat == Format.PNG) {
                                exportFormat = Format.PNG
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showingExportDialog = false
                            export()
                        }
                    ) { Text("Export & Save") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showingExportDialog = false
                                share()
                            }
                        ) { Text("Share") }
                        TextButton(onClick = { showingExportDialog = false }) { Text("Cancel") }
                    }
                }
            )
        }

        cropEdit?.let { edit ->
            CropScreen(
                page = edit.page,
                cardMode = edit.cardMode,
                pageName = lockerPageName(edit.cardMode, edit.index, doc?.pages ?: 1)
            ) { changed ->
                cropEdit = null
                if (!changed) {
                    scope.launch(Dispatchers.IO) { LockerStore.discardEdit(edit) }
                } else {
                    scope.launch {
                        busy = "Encrypting…"
                        val ok = withContext(Dispatchers.IO) { LockerStore.commitEdit(ctx, docId, edit) }
                        busy = null
                        if (ok) rev++ else message = "Could not save the crop."
                    }
                }
            }
        }
    }

    if (renaming) {
        val current = doc
        LockerRenameDialog(
            initial = current?.name ?: "",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                scope.launch {
                    val updated = withContext(Dispatchers.IO) { LockerStore.rename(ctx, docId, name) }
                    if (updated != null) doc = updated else message = "Could not rename this document."
                }
            }
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Delete this document?") },
            text = {
                Text(
                    "The encrypted pages, crop data and thumbnail for \"${doc?.name ?: "this document"}\" " +
                        "are removed from this phone. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        busy = "Deleting…"
                        val ok = withContext(Dispatchers.IO) { LockerStore.delete(ctx, docId) }
                        busy = null
                        if (ok) onDeleted() else message = "Could not delete this document."
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PageStage(
    bitmap: Bitmap?,
    page: Int,
    pages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .glass(MaterialTheme.shapes.large, strong = true).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                Text(
                    "Decrypting page…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        } else {
            val picture = remember(image) { image.asImageBitmap() }
            Image(
                picture,
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (pages > 1) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PageArrow(enabled = page > 0, previous = true, onClick = onPrevious)
                Text(
                    "${page + 1} / $pages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.glass(RoundedCornerShape(50), strong = true)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                PageArrow(enabled = page < pages - 1, previous = false, onClick = onNext)
            }
        }
    }
}

@Composable
private fun PageArrow(enabled: Boolean, previous: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).glass(RoundedCornerShape(50), strong = true)
            .alpha(if (enabled) 1f else 0.35f)
            .pressable(onClick, enabled = enabled),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (previous) Icons.AutoMirrored.Filled.KeyboardArrowLeft
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (previous) "Previous page" else "Next page",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LockerPageThumb(docId: String, index: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, docId, index) {
        value = withContext(Dispatchers.IO) {
            LockerStore.pageBytes(ctx, docId, index)?.let { decodeSampled(it, 120) }
        }
    }
    val image = bmp
    if (image != null) {
        val bitmap = remember(image) { image.asImageBitmap() }
        Image(
            bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(18.dp).glass(RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier.glass(RoundedCornerShape(50))
            .alpha(if (enabled) 1f else 0.42f)
            .pressable(onClick, enabled = enabled)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FormatButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(32.dp)
            .pressable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = color
        )
    }
}

private fun lockerPageName(cardMode: Boolean, index: Int, pages: Int): String =
    if (cardMode && pages >= 2) {
        when (index) {
            0 -> "Front"
            1 -> "Back"
            else -> "Page ${index + 1}"
        }
    } else {
        "Page ${index + 1} of $pages"
    }
