package app.flipscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.flipscan.data.Folder
import app.flipscan.data.FolderStore
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One folder: its scans, plus rename/delete for the folder itself. */
@Composable
fun FolderScreen(folderId: String, onBack: () -> Unit, onEdit: (Scan) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf<Folder?>(null) }
    var scans by remember { mutableStateOf<List<Scan>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var actionsFor by remember { mutableStateOf<Scan?>(null) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(folderId, reload) {
        val data = withContext(Dispatchers.IO) {
            Triple(FolderStore.get(ctx, folderId), ScanStore.list(ctx), FolderStore.list(ctx))
        }
        folder = data.first
        scans = data.second.filter { it.folderId == folderId }
        folders = data.third
        loaded = true
    }

    // The folder disappeared (deleted from another screen): leave the now-empty screen.
    LaunchedEffect(loaded, folder) {
        if (loaded && folder == null) onBack()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = folder?.name ?: "Folder",
                subtitle = if (loaded) {
                    if (scans.size == 1) "1 scan" else "${scans.size} scans"
                } else null,
                onBack = onBack
            ) {
                IconButton(onClick = { menu = true }, enabled = folder != null) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Folder options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                !loaded -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
                scans.isEmpty() -> EmptyState(
                    "This folder is empty",
                    "Open a scan from Recent scans and choose Move to folder to put it here."
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scans, key = { it.id }) { scan ->
                        ScanRow(scan = scan, onActions = { actionsFor = scan })
                    }
                }
            }
        }

        ScanActionsHost(
            scan = actionsFor,
            folders = folders,
            onDismiss = { actionsFor = null },
            onEdit = { s -> actionsFor = null; onEdit(s) },
            onChanged = { reload++ }
        )
    }

    val current = folder
    if (menu && current != null) {
        FolderActionsSheet(
            folder = current,
            scanCount = scans.size,
            onDismiss = { menu = false },
            onRename = { menu = false; renaming = true },
            onDelete = { menu = false; deleting = true }
        )
    }

    if (renaming && current != null) {
        FolderNameDialog(
            title = "Rename folder",
            initial = current.name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                scope.launch {
                    withContext(Dispatchers.IO) { FolderStore.rename(ctx, current.id, name) }
                    reload++
                }
            }
        )
    }

    if (deleting && current != null) {
        DeleteFolderDialog(
            folder = current,
            scanCount = scans.size,
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ScanStore.clearFolder(ctx, current.id)
                        FolderStore.delete(ctx, current.id)
                    }
                    onBack()
                }
            }
        )
    }
}
