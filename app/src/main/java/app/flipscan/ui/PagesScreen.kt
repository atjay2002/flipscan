package app.flipscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flipscan.scan.Sidecar
import java.io.File

/** Review captured pages: reorder with the arrows, delete with ✕, add more, or save. */
@Composable
fun PagesScreen(pages: SnapshotStateList<File>, onAddMore: () -> Unit, onSave: () -> Unit) {
    BackHandler(onBack = onAddMore) // back = return to the camera, nothing is lost

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onAddMore) { Text("← Camera") }
            Text("${pages.size} page(s)", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = pages.size, key = { pages[it].name }) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageThumb(pages[i], Modifier.height(96.dp).width(72.dp), maxSide = 240)
                    Text("Page ${i + 1}", Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(enabled = i > 0, onClick = { move(pages, i, i - 1) }) { Text("↑") }
                    TextButton(enabled = i < pages.size - 1, onClick = { move(pages, i, i + 1) }) { Text("↓") }
                    TextButton(onClick = { Sidecar.deleteAll(pages.removeAt(i)) }) { Text("✕") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddMore, modifier = Modifier.weight(1f)) { Text("Add page") }
            Button(onClick = onSave, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Continue") }
        }
    }
}

private fun move(list: MutableList<File>, from: Int, to: Int) {
    val f = list.removeAt(from)
    list.add(to, f)
}
