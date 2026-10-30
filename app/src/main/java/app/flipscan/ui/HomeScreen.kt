package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.flipscan.data.Folder
import app.flipscan.data.FolderStore
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onDoubleSide: () -> Unit,
    onRegular: () -> Unit,
    onEdit: (Scan) -> Unit,
    onOpenFolder: (Folder) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scans by remember { mutableStateOf<List<Scan>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    var actionsFor by remember { mutableStateOf<Scan?>(null) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<Folder?>(null) }
    var deletingFolder by remember { mutableStateOf<Folder?>(null) }

    // Reads small JSON files on a background thread; runs again whenever Home is shown or data changed.
    LaunchedEffect(reload) {
        val data = withContext(Dispatchers.IO) { ScanStore.list(ctx) to FolderStore.list(ctx) }
        scans = data.first
        folders = data.second
        loaded = true
    }

    fun countIn(folderId: String) = scans.count { it.folderId == folderId }

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

            item(key = "foldersHeader") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (folders.isNotEmpty()) CountBadge("${folders.size}")
                    TextButton(onClick = { creatingFolder = true }) { Text("New folder") }
                }
            }

            if (folders.isEmpty()) {
                item(key = "foldersEmpty") {
                    Text(
                        "Group scans into folders — a project, a person, a month. Move any scan into one " +
                            "from its ⋮ menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item(key = "folders") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FolderCard(
                                folder = folder,
                                scanCount = countIn(folder.id),
                                onOpen = { onOpenFolder(folder) },
                                onActions = { folderMenu = folder }
                            )
                        }
                    }
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
                        "Scans you save stay on this phone and appear here, ready to open, edit or export again."
                    )
                }
            } else {
                items(scans, key = { it.id }) { scan ->
                    ScanRow(
                        scan = scan,
                        folderName = folders.firstOrNull { it.id == scan.folderId }?.name,
                        onActions = { actionsFor = scan }
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

        ScanActionsHost(
            scan = actionsFor,
            folders = folders,
            onDismiss = { actionsFor = null },
            onEdit = { s -> actionsFor = null; onEdit(s) },
            onChanged = { reload++ }
        )
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                creatingFolder = false
                scope.launch {
                    val folder = withContext(Dispatchers.IO) { FolderStore.create(ctx, name) }
                    reload++
                    if (folder != null) Toast.makeText(ctx, "Folder \"${folder.name}\" created", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = "Rename folder",
            initial = folder.name,
            confirmLabel = "Save",
            onDismiss = { renamingFolder = null },
            onConfirm = { name ->
                renamingFolder = null
                scope.launch {
                    withContext(Dispatchers.IO) { FolderStore.rename(ctx, folder.id, name) }
                    reload++
                }
            }
        )
    }

    deletingFolder?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            scanCount = countIn(folder.id),
            onDismiss = { deletingFolder = null },
            onConfirm = {
                deletingFolder = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        // Scans move back to the main collection; nothing on disk is deleted.
                        ScanStore.clearFolder(ctx, folder.id)
                        FolderStore.delete(ctx, folder.id)
                    }
                    reload++
                }
            }
        )
    }

    folderMenu?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            scanCount = countIn(folder.id),
            onDismiss = { folderMenu = null },
            onRename = { folderMenu = null; renamingFolder = folder },
            onDelete = { folderMenu = null; deletingFolder = folder }
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
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (filled) fg.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
