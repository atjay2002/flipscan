package app.flipscan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flipscan.data.Folder
import app.flipscan.data.Naming

/** A folder tile in the home dashboard: name, how many scans it holds, and its actions. */
@Composable
fun FolderCard(
    folder: Folder,
    scanCount: Int,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(150.dp).pressable(onOpen),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    FolderIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f))
                IconButton(onClick = onActions, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Actions for folder ${folder.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp)
            )
            Text(
                if (scanCount == 1) "1 scan" else "$scanCount scans",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Create or rename a folder. [onConfirm] gets the cleaned name. */
@Composable
fun FolderNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val valid = !Naming.isBlank(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                label = { Text("Folder name") },
                supportingText = {
                    Text(if (valid) "Group scans by project, person or month." else "Enter a folder name.")
                }
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(Naming.sanitize(text, "Folder")) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Deleting a folder never deletes scans; a non-empty folder says so explicitly. */
@Composable
fun DeleteFolderDialog(
    folder: Folder,
    scanCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Delete folder \"${folder.name}\"?") },
        text = {
            Text(
                if (scanCount > 0) {
                    "The folder is removed. Its $scanCount scan(s) stay in FlipScan and move back to " +
                        "the main collection — no photos, crops or exported files are deleted."
                } else {
                    "The empty folder is removed. Scans are not affected."
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(
    folder: Folder,
    scanCount: Int,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (scanCount == 1) "1 scan" else "$scanCount scans",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().pressable(onRename).padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rename folder", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp))
            }
            Row(
                Modifier.fillMaxWidth().pressable(onDelete).padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Delete folder", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        "Scans stay in FlipScan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
