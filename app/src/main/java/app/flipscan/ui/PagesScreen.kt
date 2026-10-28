package app.flipscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.flipscan.scan.Sidecar
import java.io.File

/** Review captured pages: reorder with the arrows, delete with the bin, add more, or continue. */
@Composable
fun PagesScreen(pages: androidx.compose.runtime.snapshots.SnapshotStateList<File>, onAddMore: () -> Unit, onSave: () -> Unit) {
    BackHandler(onBack = onAddMore) // back = return to the camera, nothing is lost

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = "Review pages",
            subtitle = if (pages.isEmpty()) "No pages yet" else "${pages.size} page(s) · reorder or delete before saving",
            onBack = onAddMore
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(pages, key = { _, file -> file.name }) { i, file ->
                PageRow(
                    index = i,
                    file = file,
                    canMoveUp = i > 0,
                    canMoveDown = i < pages.lastIndex,
                    onMoveUp = { move(pages, i, i - 1) },
                    onMoveDown = { move(pages, i, i + 1) },
                    onDelete = { Sidecar.deleteAll(pages.removeAt(i)) }
                )
            }
        }

        BottomActionBar {
            OutlinedButton(onClick = onAddMore, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                Text("Add page")
            }
            Button(
                onClick = onSave,
                enabled = pages.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun PageRow(
    index: Int,
    file: File,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 54.dp, height = 70.dp).clip(RoundedCornerShape(10.dp))) {
                PageThumb(file, Modifier.fillMaxSize(), maxSide = 240)
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (canMoveUp || canMoveDown) "Use the arrows to reorder" else "Only page",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move page ${index + 1} up",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move page ${index + 1} down",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete page ${index + 1}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun move(list: MutableList<File>, from: Int, to: Int) {
    val f = list.removeAt(from)
    list.add(to, f)
}
