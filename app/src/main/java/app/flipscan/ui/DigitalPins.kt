package app.flipscan.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flipscan.data.LockerDoc
import app.flipscan.data.LockerDocType
import app.flipscan.data.LockerStore
import app.flipscan.data.PinFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long a revealed number stays on screen before it masks itself again. */
private const val REVEAL_MS = 20_000L

/**
 * The Digital Pins list on the locker home: one row per document that has a number. The number is
 * always shown masked until the user asks for it, and it hides itself again after a short while.
 */
@Composable
fun DigitalPinsSection(docs: List<LockerDoc>) {
    val pins = docs.filter { it.number != null }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Digital Pins",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (pins.isNotEmpty()) CountBadge("${pins.size}")
        }
        Text(
            "Document numbers read on this phone and sealed with the same key as the pages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )

        if (pins.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().glass(MaterialTheme.shapes.large)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text("No numbers yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Open a card in the locker and FlipScan reads its number with on-device text " +
                        "recognition. Masked by default, revealed only when you ask.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pins.forEach { doc -> DigitalPinRow(doc) }
            }
        }
    }
}

@Composable
private fun DigitalPinRow(doc: LockerDoc) {
    val number = doc.number ?: return
    val ctx = LocalContext.current
    var revealed by remember(doc.id, number) { mutableStateOf(false) }
    RevealTimeout(revealed) { revealed = false }

    Row(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium)
            .pressable(onClick = { revealed = !revealed })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PinTypeBadge(doc.type)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                doc.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (revealed) PinFormat.display(doc.type, number) else PinFormat.masked(doc.type, number),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        TextButton(onClick = { revealed = !revealed }) {
            Text(if (revealed) "Hide" else "Show")
        }
        TextButton(
            onClick = {
                copyPin(ctx, number)
                revealed = false
            }
        ) {
            Text("Copy")
        }
    }
}

/**
 * The Digital Pin inside the viewer: reading state, reveal/copy/edit, and the one-tap way to run
 * recognition again. Sensitive values only ever reach the clipboard, never a log or a file.
 */
@Composable
fun DigitalPinCard(
    doc: LockerDoc,
    reading: Boolean,
    onDetect: () -> Unit,
    onChanged: (LockerDoc) -> Unit,
    onError: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val number = doc.number
    var revealed by remember(doc.id, number) { mutableStateOf(false) }
    var editing by remember(doc.id) { mutableStateOf(false) }
    RevealTimeout(revealed) { revealed = false }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .glass(MaterialTheme.shapes.large).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Digital Pin", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            when {
                number == null -> Unit
                doc.numberVerified -> Pill("Verified", MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer)
                else -> Pill("Check digits", MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        when {
            reading -> Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Reading the number on this phone…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            number == null -> {
                Text(
                    if (doc.type.pins) {
                        "No number found yet. Read it from the page, or type it in if the print is worn."
                    } else {
                        "Custom documents have no number to read. Add one if you want it here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (doc.type.pins) {
                        TextButton(onClick = onDetect) { Text("Read number") }
                    }
                    TextButton(onClick = { editing = true }) { Text("Add manually") }
                }
            }

            else -> {
                Text(
                    if (revealed) PinFormat.display(doc.type, number) else PinFormat.masked(doc.type, number),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(if (revealed) "Hide" else "Show")
                    }
                    TextButton(onClick = { copyPin(ctx, number) }) { Text("Copy") }
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    if (doc.type.pins) {
                        TextButton(onClick = onDetect, enabled = !reading) { Text("Read again") }
                    }
                }
            }
        }
    }

    if (editing) {
        LockerPinDialog(
            doc = doc,
            onDismiss = { editing = false },
            onSave = { value ->
                editing = false
                scope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        LockerStore.setNumber(
                            ctx,
                            doc.id,
                            value,
                            page = null,
                            verified = value != null && PinFormat.isValid(doc.type, value),
                            checked = true
                        )
                    }
                    if (updated != null) onChanged(updated) else onError("Could not save the number.")
                }
            }
        )
    }
}

/** Shared edit dialog: normalises as you type and explains the expected shape. */
@Composable
fun LockerPinDialog(
    doc: LockerDoc,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var text by remember(doc.id) { mutableStateOf(doc.number.orEmpty()) }
    val clean = PinFormat.normalize(doc.type, text)
    val hint = PinFormat.hint(doc.type, text)
    val warns = clean.isNotEmpty() && !PinFormat.isValid(doc.type, clean)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (doc.number == null) "Add document number" else "Edit document number") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("${doc.type.label} number") },
                    supportingText = {
                        Text(
                            hint,
                            color = if (warns) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Text(
                    "Stored encrypted inside the locker. Shown masked by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotEmpty(),
                onClick = { onSave(clean.ifEmpty { null }) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (doc.number != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Copies with the Android 13+ "sensitive" flag so the value is not previewed by the system UI. */
private fun copyPin(ctx: Context, value: String) {
    val manager = ctx.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("Document number", value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    manager?.setPrimaryClip(clip)
    Toast.makeText(ctx, "Number copied", Toast.LENGTH_SHORT).show()
}

/** Masks the value again after a while, so a forgotten reveal does not stay on screen. */
@Composable
private fun RevealTimeout(revealed: Boolean, onTimeout: () -> Unit) {
    LaunchedEffect(revealed) {
        if (!revealed) return@LaunchedEffect
        delay(REVEAL_MS)
        onTimeout()
    }
}

@Composable
private fun PinTypeBadge(type: LockerDocType) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (type.cards) {
            CardStackIcon(Modifier.size(19.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            DocumentIcon(Modifier.size(19.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, foreground: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
