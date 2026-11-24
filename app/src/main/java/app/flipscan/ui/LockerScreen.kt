package app.flipscan.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.flipscan.data.LockerDoc
import app.flipscan.data.LockerDocType
import app.flipscan.data.LockerStore
import app.flipscan.data.Naming
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The locker's front door. It only ever shows a lock: no document names, thumbnails or counts
 * are read until [onUnlocked] fires after the system prompt succeeds.
 */
@Composable
fun LockerGateScreen(onUnlocked: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(LockerAuth.status(ctx)) }
    var unlocking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    // Coming back from the phone's security settings can change what is available.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = LockerAuth.status(ctx) }

    fun startAuthentication() {
        if (unlocking) return
        val activity = ctx.findFragmentActivity()
        if (activity == null) {
            message = "Authentication is unavailable on this device."
            return
        }
        unlocking = true
        message = null
        LockerAuth.unlock(
            activity = activity,
            onSuccess = {
                unlocking = false
                onUnlocked()
            },
            onCancelled = { unlocking = false },
            onError = { text ->
                unlocking = false
                message = text
                status = LockerAuth.status(ctx)
            }
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).ambientGlow()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Digital Locker",
                subtitle = "Encrypted on this phone",
                onBack = onBack
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(30.dp))
                Box(
                    Modifier.size(96.dp).glass(CircleShape, strong = true, accent = MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    LockIcon(Modifier.size(44.dp), MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 20.dp)
                )
                Text(
                    "Aadhaar, PAN, passport and other sensitive documents live here, encrypted and " +
                        "protected by your phone's own screen lock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Panel(Modifier.padding(top = 24.dp)) {
                    LockBullet("Fingerprint, face or your phone's PIN, pattern or password")
                    Spacer(Modifier.height(10.dp))
                    LockBullet("AES-256-GCM encryption with a key held in Android Keystore")
                    Spacer(Modifier.height(10.dp))
                    LockBullet("Separate from Recent scans, folders and Downloads")
                }

                if (status == LockerAuthStatus.NO_LOCK) {
                    Text(
                        "Set a screen lock first. FlipScan uses it to protect the locker and never sees the PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    OutlinedButton(
                        onClick = { openSecuritySettings(ctx) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = MaterialTheme.shapes.small
                    ) { Text("Open security settings") }
                } else {
                    Button(
                        onClick = ::startAuthentication,
                        enabled = !unlocking,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (unlocking) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Waiting for the phone…")
                        } else {
                            LockIcon(Modifier.size(17.dp), MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock Digital Locker")
                        }
                    }
                    Text(
                        when (status) {
                            LockerAuthStatus.BIOMETRIC -> "Uses your fingerprint, face or screen lock."
                            LockerAuthStatus.CREDENTIAL -> "No biometric is set up, so your screen lock is used."
                            LockerAuthStatus.NO_LOCK -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Text(
                    "No internet permission. Nothing in the locker leaves this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, bottom = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun LockBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            LockIcon(Modifier.size(15.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 11.dp)
        )
    }
}

/** The unlocked locker dashboard. Reads the encrypted index only after authentication. */
@Composable
fun LockerHomeScreen(
    onBack: () -> Unit,
    onScan: (LockerDocType) -> Unit,
    onOpen: (String) -> Unit,
    onLock: () -> Unit
) {
    val ctx = LocalContext.current
    var docs by remember { mutableStateOf<List<LockerDoc>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var indexError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LaunchedEffect(reload) {
        val result = withContext(Dispatchers.IO) {
            LockerStore.sweep(ctx) // interrupted saves and orphan folders never survive a visit
            LockerStore.readAll(ctx)
        }
        result.onSuccess {
            docs = it
            indexError = false
        }.onFailure {
            docs = emptyList()
            indexError = true
        }
        loaded = true
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).ambientGlow()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Digital Locker",
                subtitle = "Encrypted on this phone",
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

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "scan") { LockerScanPanel(onScan = { picking = true }) }

                item(key = "docsHeader") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "My secure documents",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (docs.isNotEmpty()) CountBadge("${docs.size}")
                    }
                }

                when {
                    !loaded -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    indexError -> item(key = "indexError") {
                        Text(
                            "The encrypted index could not be opened, so nothing is shown and nothing " +
                                "was changed. Lock the locker and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    docs.isEmpty() -> item(key = "empty") { LockerEmptyState() }
                    else -> items(docs, key = { it.id }) { doc ->
                        LockerDocRow(doc = doc, onClick = { onOpen(doc.id) })
                    }
                }

                item(key = "pins") {
                    DigitalPinsSection(docs = docs, onOpen = onOpen)
                }
                item(key = "privacy") {
                    Text(
                        "Encrypted with a key inside this phone's Keystore. Locker documents are never " +
                            "listed in Recent scans, folders or Downloads, and are not exported or shared in this version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (picking) {
        LockerTypeSheet(onDismiss = { picking = false }, onPick = { type ->
            picking = false
            onScan(type)
        })
    }
}

@Composable
private fun LockerScanPanel(onScan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .glass(MaterialTheme.shapes.large, strong = true, accent = MaterialTheme.colorScheme.primary)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                LockIcon(Modifier.size(26.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Scan sensitive document", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aadhaar · PAN · Passport · DL · Voter ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Scanning here saves straight into the locker, encrypted. Ordinary scans on Home stay " +
                "separate and need no authentication.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            shape = MaterialTheme.shapes.small
        ) { Text("Scan sensitive document") }
    }
}

@Composable
private fun LockerEmptyState() {
    Column(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.large).padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            LockIcon(Modifier.size(26.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "No secure documents yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            "Scan an Aadhaar, PAN, passport or any other sensitive document inside the locker. " +
                "It is encrypted before it reaches storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun LockerDocRow(doc: LockerDoc, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium).pressable(onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 54.dp, height = 70.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            LockerThumb(doc.id, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                doc.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${doc.type.label} · ${if (doc.pages == 1) "1 page" else "${doc.pages} pages"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                formatLockerDate(doc.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Decrypts the small thumbnail in memory. The description is deliberately null: a thumbnail is decoration. */
@Composable
fun LockerThumb(id: String, modifier: Modifier = Modifier, maxSide: Int = 220) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, id, maxSide) {
        value = withContext(Dispatchers.IO) {
            LockerStore.thumbBytes(ctx, id)?.let { decodeSampled(it, maxSide) }
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
            LockIcon(
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockerTypeSheet(onDismiss: () -> Unit, onPick: (LockerDocType) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("Scan sensitive document", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Saved encrypted inside the locker only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LockerDocType.entries.forEach { type ->
                Row(
                    Modifier.fillMaxWidth().pressable(onClick = { onPick(type) })
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (type.cards) {
                            CardStackIcon(Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            DocumentIcon(Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(type.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            type.hint,
                            style = MaterialTheme.typography.bodySmall,
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

@Composable
fun LockerRenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    val clean = Naming.sanitize(text, "")
    val valid = clean.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Rename document") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                label = { Text("Document name") },
                supportingText = {
                    Text(if (valid) "Shown only inside the locker." else "Enter a name.")
                }
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(clean) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatLockerDate(at: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(at))

private fun openSecuritySettings(ctx: android.content.Context) {
    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(intent) }.isFailure) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
