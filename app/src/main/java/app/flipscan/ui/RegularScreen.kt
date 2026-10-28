package app.flipscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flipscan.data.Work
import app.flipscan.scan.AutoCaptureEffect
import app.flipscan.scan.PageProcessor
import app.flipscan.scan.ScanCameraState
import app.flipscan.scan.ScanCameraView
import app.flipscan.scan.rememberScanCameraState
import app.flipscan.scan.statusText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Regular mode camera: capture one or many pages; every capture becomes its own page. */
@Composable
fun RegularScreen(pages: SnapshotStateList<File>, onDone: () -> Unit, onBack: () -> Unit) {
    CameraPermissionGate { RegularContent(pages, onDone, onBack) }
}

@Composable
private fun RegularContent(pages: SnapshotStateList<File>, onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cam = rememberScanCameraState(cardMode = false)
    val flash = remember { Animatable(0f) }
    val flashMs = motionMs(380)
    var busy by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(true) }
    var shots by remember { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler { if (pages.isEmpty()) onBack() else confirmExit = true }
    DiscardDialog(confirmExit, onConfirm = { confirmExit = false; onBack() }, onDismiss = { confirmExit = false })

    fun shoot() {
        if (busy) return
        busy = true
        val raw = Work.newFile(ctx, "raw")
        cam.takePicture(raw) { ok, quad ->
            if (!ok) {
                busy = false
                return@takePicture
            }
            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(flashMs)) }
            scope.launch {
                val page = Work.newFile(ctx, "page")
                val good = withContext(Dispatchers.Default) { PageProcessor.process(raw, page, false, quad) }
                raw.delete()
                if (good) {
                    pages.add(page)
                    shots++
                } else {
                    page.delete()
                }
                busy = false
            }
        }
    }

    AutoCaptureEffect(cam, enabled = auto && !busy, shots = shots) { shoot() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // TextureView so the preview survives the animated screen transition.
        ScanCameraView(cam, Modifier.fillMaxSize(), compatible = true)
        FlashOverlay({ flash.value }, Modifier.fillMaxSize())

        // Scrim only behind the top bar, so the preview keeps its full contrast.
        Box(
            Modifier.fillMaxWidth().height(132.dp)
                .background(Brush.verticalGradient(listOf(Color(0xB30A1020), Color.Transparent)))
        )
        CameraHeader(
            title = "Document",
            subtitle = if (pages.isEmpty()) "Capture the first page" else "${pages.size} page(s) captured",
            onBack = { if (pages.isEmpty()) onBack() else confirmExit = true }
        ) {
            FlashToggle(cam, Modifier.padding(end = 4.dp))
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xF20E1728),
                border = null
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)) {
                    Box(
                        Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .align(Alignment.CenterHorizontally)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RegularStatus(cam, Modifier.weight(1f))
                        Text(
                            if (pages.isEmpty()) "0 pages" else "${pages.size} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (pages.isNotEmpty()) {
                        LazyRow(
                            Modifier.fillMaxWidth().height(74.dp).padding(top = 10.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pages, key = { _, file -> file.name }) { i, file ->
                                Box(
                                    Modifier.size(width = 48.dp, height = 62.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                ) {
                                    PageThumb(file, Modifier.fillMaxSize(), maxSide = 120, placeholder = CameraCard)
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xB30B1220))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoCaptureToggle(auto, Modifier.width(90.dp)) { auto = it }
                        ShutterButton(enabled = !busy, progress = { cam.steady }, onClick = ::shoot)
                        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                            if (pages.isNotEmpty() && !busy) {
                                Button(
                                    onClick = onDone,
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = CameraTop
                                    )
                                ) { Text("Done (${pages.size})") }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(tween(motionMs(140))),
            modifier = Modifier.fillMaxSize()
        ) {
            BusyOverlay("Processing…", Modifier.fillMaxSize())
        }
    }
}

/** Own composable so only this small piece recomposes when the detection result changes. */
@Composable
private fun RegularStatus(cam: ScanCameraState, modifier: Modifier = Modifier) {
    val text by remember(cam) { derivedStateOf { statusText(cam.live) } }
    val accent = when {
        cam.live?.quad == null -> Color(0xFF94A3B8)
        cam.live?.blurry == true || cam.live?.glare == true -> Amber
        else -> Color(0xFF34D399)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
