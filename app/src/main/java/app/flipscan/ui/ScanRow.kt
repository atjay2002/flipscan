package app.flipscan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flipscan.data.Mode
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import java.text.DateFormat
import java.util.Date

/**
 * One saved scan in a list. Tapping it (or the overflow button) opens the action sheet,
 * which is where Open / Edit / Rename / Share / Move live.
 */
@Composable
fun ScanRow(
    scan: Scan,
    folderName: String? = null,
    onActions: () -> Unit
) {
    val ctx = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().pressable(onActions),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            PageThumb(
                ScanStore.thumbFile(ctx, scan.id),
                Modifier.size(width = 52.dp, height = 66.dp).clip(RoundedCornerShape(10.dp)),
                maxSide = 240
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(scan.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.createdAt))
                Text(
                    "$date · ${scan.pages.size} page(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (scan.mode == Mode.CARDS) "ID CARD" else "DOCUMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scan.mode == Mode.CARDS) {
                            MaterialTheme.colorScheme.primary
                        } else if (isSystemInDarkTheme()) {
                            Color(0xFF5EEAD4)
                        } else {
                            Color(0xFF0F766E)
                        }
                    )
                    if (folderName != null) {
                        Text(
                            "· $folderName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onActions) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Actions for ${scan.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
