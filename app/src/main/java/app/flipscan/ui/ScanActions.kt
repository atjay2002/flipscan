package app.flipscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.flipscan.data.Downloads
import app.flipscan.data.Folder
import app.flipscan.data.FolderStore
import app.flipscan.data.Mode
import app.flipscan.data.Naming
import app.flipscan.data.SavedExport
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.SettingsStore
import app.flipscan.export.Exporter
import app.flipscan.export.Share
import app.flipscan.export.Viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything you can do with one saved scan. A short bottom sheet holds the actions
 * (Open / Edit / Rename / Share / Move / Delete); the dialogs are owned here so they
 * survive the sheet closing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanActionsHost(
    scan: Scan?,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onEdit: (Scan) -> Unit,
    onChanged: () -> Unit
) {
    if (scan == null) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var current by remember(scan.id) { mutableStateOf(scan) }
    var sheetOpen by remember(scan.id) { mutableStateOf(true) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var noExport by remember { mutableStateOf(false) }
    var pickExport by remember { mutableStateOf<List<SavedExport>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val folderName = folders.firstOrNull { it.id == current.folderId }?.name

    fun closeSheet() {
        // No suspend hide() here: every caller immediately opens a dialog, so the sheet
        // is removed from composition and the dialog appears without waiting for an animation.
        sheetOpen = false
    }

    fun openSaved(saved: SavedExport) {
        val error = Viewer.open(ctx, saved)
        if (error != null) message = error else onDismiss()
    }

    fun saveAndOpen() {
        scope.launch {
            busy = "Creating file…"
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val res = Exporter.render(ctx, current, SettingsStore.load(ctx), current.name)
                    val saved = Downloads.save(ctx, res.files)
                    var updated = current
                    saved.forEach {
                        updated = ScanStore.addExport(
                            ctx, updated, SavedExport(it.name, it.uri, it.mime, System.currentTimeMillis())
                        )
                    }
                    updated
                }
            }
            busy = null
            r.onSuccess { updated ->
                current = updated
                onChanged()
                val first = updated.exports.firstOrNull()
                if (first == null) {
                    message = "The file was created but cannot be opened."
                } else {
                    val error = Viewer.open(ctx, first)
                    if (error != null) message = error else onDismiss()
                }
            }.onFailure { message = "Could not create the file: ${it.message ?: "unknown error"}" }
        }
    }

    // Android 7-9 needs WRITE_EXTERNAL_STORAGE before the copy into Downloads. Asking here
    // means "Save & open" works even if the user never saved from the Result screen.
    var pendingSave by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingSave = true
        } else {
            message = "Storage permission is needed to save into Downloads on this Android version."
        }
    }

    fun saveAndOpenChecked() {
        val needs = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needs) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveAndOpen()
    }

    LaunchedEffect(pendingSave) {
        if (pendingSave) {
            pendingSave = false
            saveAndOpen()
        }
    }

    fun share() {
        scope.launch {
            busy = "Preparing…"
            val r = withContext(Dispatchers.IO) {
                runCatching { Exporter.render(ctx, current, SettingsStore.load(ctx), current.name).files }
            }
            busy = null
            r.onSuccess { files ->
                val sent = runCatching { Share.send(ctx, files) }
                if (sent.isSuccess) onDismiss() else message = "Could not share: ${sent.exceptionOrNull()?.message}"
            }.onFailure { message = "Could not share: ${it.message ?: "unknown error"}" }
        }
    }

    fun openAction() {
        val existing = current.exports.filter { Downloads.exists(ctx, it) }
        when {
            existing.size == 1 -> openSaved(existing.first())
            existing.isNotEmpty() -> pickExport = existing
            else -> noExport = true
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false; onDismiss() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(if (current.mode == Mode.CARDS) "ID card" else "Document")
                            append(" · ${current.pages.size} page(s)")
                            append(" · ")
                            append(folderName ?: "Main collection")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                SheetAction(
                    title = "Open",
                    subtitle = current.exports.firstOrNull()?.name ?: "View the saved PDF or image",
                    icon = { tint -> OpenIcon(Modifier.size(21.dp), tint) }
                ) { closeSheet(); openAction() }
                SheetAction(
                    title = "Edit",
                    subtitle = "Crop, rotate, change paper and export settings",
                    icon = { tint -> CropIcon(Modifier.size(21.dp), tint) }
                ) { closeSheet(); onEdit(current) }
                SheetAction(
                    title = "Rename",
                    subtitle = "Name shown inside FlipScan",
                    icon = { tint -> Icon(Icons.Filled.Edit, null, tint = tint, modifier = Modifier.size(21.dp)) }
                ) { closeSheet(); renaming = true }
                SheetAction(
                    title = "Share",
                    subtitle = "Send the exported file to another app",
                    icon = { tint -> Icon(Icons.Filled.Share, null, tint = tint, modifier = Modifier.size(21.dp)) }
                ) { closeSheet(); share() }
                SheetAction(
                    title = "Move to folder",
                    subtitle = folderName ?: "Main collection",
                    icon = { tint -> FolderIcon(Modifier.size(21.dp), tint) }
                ) { closeSheet(); moving = true }
                SheetAction(
                    title = "Delete",
                    subtitle = "Removes the scan from FlipScan",
                    danger = true,
                    icon = { tint -> Icon(Icons.Filled.Delete, null, tint = tint, modifier = Modifier.size(21.dp)) }
                ) { closeSheet(); deleting = true }
            }
        }
    }

    if (renaming) {
        ScanRenameDialog(
            scan = current,
            onDismiss = { renaming = false; onDismiss() },
            onRenamed = { updated -> current = updated; onChanged(); onDismiss() }
        )
    }

    if (moving) {
        FolderPickerDialog(
            folders = folders,
            currentFolderId = current.folderId,
            onDismiss = { moving = false; onDismiss() },
            onPick = { folderId ->
                scope.launch {
                    val updated = withContext(Dispatchers.IO) { ScanStore.setFolder(ctx, current, folderId) }
                    current = updated
                    moving = false
                    onChanged()
                    onDismiss()
                }
            }
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false; onDismiss() },
            shape = MaterialTheme.shapes.large,
            title = { Text("Delete \"${current.name}\"?") },
            text = { Text("This removes the scan from FlipScan. Files you already saved to Downloads are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ScanStore.delete(ctx, current) }
                        deleting = false
                        onChanged()
                        onDismiss()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false; onDismiss() }) { Text("Cancel") } }
        )
    }

    if (noExport) {
        val stale = current.exports.isNotEmpty()
        AlertDialog(
            onDismissRequest = { noExport = false; onDismiss() },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (stale) "Saved file is missing" else "No saved file yet") },
            text = {
                Text(
                    if (stale) {
                        "The exported file is no longer in Downloads/FlipScan (it may have been deleted or moved). " +
                            "Save this scan again to open it."
                    } else {
                        "Open shows the PDF or image this scan produced. This scan has not been saved to " +
                            "Downloads yet — save it now and it will open in your viewer."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { noExport = false; saveAndOpenChecked() }) {
                    Text(if (stale) "Save again" else "Save & open")
                }
            },
            dismissButton = { TextButton(onClick = { noExport = false; onDismiss() }) { Text("Cancel") } }
        )
    }

    pickExport?.let { options ->
        AlertDialog(
            onDismissRequest = { pickExport = null; onDismiss() },
            shape = MaterialTheme.shapes.large,
            title = { Text("Open which file?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "This scan has more than one saved file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().pressable(onClick = {
                                pickExport = null
                                openSaved(option)
                            }).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OpenIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                            Text(
                                option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickExport = null; onDismiss() }) { Text("Cancel") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null; onDismiss() },
            shape = MaterialTheme.shapes.large,
            title = { Text("Cannot open") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null; onDismiss() }) { Text("OK") } }
        )
    }

    if (busy != null) BusyOverlay(busy ?: "", Modifier.fillMaxSize())
}

@Composable
private fun SheetAction(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(
                    if (danger) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) { icon(if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Renames the scan itself (not the exported file, which has its own name field). */
@Composable
fun ScanRenameDialog(scan: Scan, onDismiss: () -> Unit, onRenamed: (Scan) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(scan.id) { mutableStateOf(scan.name) }
    var saving by remember { mutableStateOf(false) }
    val valid = !Naming.isBlank(text)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = MaterialTheme.shapes.large,
        title = { Text("Rename scan") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = !valid,
                    label = { Text("Name") },
                    supportingText = {
                        Text(
                            if (valid) "Shown in FlipScan. Exported files are named separately."
                            else "Enter a name that is not blank."
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) { ScanStore.rename(ctx, scan, text) }
                        saving = false
                        if (updated != null) {
                            Toast.makeText(ctx, "Renamed to \"${updated.name}\"", Toast.LENGTH_SHORT).show()
                            onRenamed(updated)
                        } else {
                            onDismiss()
                        }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

/** Picks a folder (or the main collection) for one scan, with inline folder creation. */
@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var localFolders by remember(folders) { mutableStateOf(folders) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (creating) "New folder" else "Move to folder") },
        text = {
            if (creating) {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; error = null },
                        singleLine = true,
                        isError = error != null,
                        label = { Text("Folder name") },
                        supportingText = { Text(error ?: "The scan will be moved into the new folder.") }
                    )
                }
            } else {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    FolderChoice(
                        label = "Main collection",
                        detail = "No folder",
                        selected = currentFolderId == null
                    ) { onPick(null) }
                    localFolders.forEach { folder ->
                        FolderChoice(
                            label = folder.name,
                            detail = null,
                            selected = folder.id == currentFolderId
                        ) { onPick(folder.id) }
                    }
                    Row(
                        Modifier.fillMaxWidth().pressable(onClick = { creating = true }).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Text(
                            "New folder…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = !Naming.isBlank(newName),
                    onClick = {
                        scope.launch {
                            val folder = withContext(Dispatchers.IO) { FolderStore.create(ctx, newName) }
                            if (folder == null) {
                                error = "Enter a folder name."
                            } else {
                                localFolders = localFolders + folder
                                onPick(folder.id)
                            }
                        }
                    }
                ) { Text("Create & move") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) creating = false else onDismiss() }) {
                Text(if (creating) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun FolderChoice(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderIcon(
            Modifier.size(18.dp),
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}
