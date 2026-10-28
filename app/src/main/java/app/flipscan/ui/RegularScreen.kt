package app.flipscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(380)) }
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
        // No animation on this screen, so the cheaper SurfaceView preview is used.
        ScanCameraView(cam, Modifier.fillMaxSize())
        FlashOverlay({ flash.value }, Modifier.fillMaxSize())

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (pages.isEmpty()) onBack() else confirmExit = true }) {
                Text("← Back", color = Color.White)
            }
        }
        LiveStatus(cam, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))

        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pages.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().height(72.dp).padding(bottom = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages.size) { i -> PageThumb(pages[i], Modifier.height(64.dp).width(48.dp), maxSide = 120) }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto", color = Color.White, fontSize = 12.sp)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                ShutterButton(enabled = !busy, progress = { cam.steady }, onClick = ::shoot)
                Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                    if (pages.isNotEmpty() && !busy) Button(onClick = onDone) { Text("Done (${pages.size})") }
                }
            }
        }
        if (busy) BusyOverlay("Processing…", Modifier.fillMaxSize())
    }
}

/** Own composable so only this small piece recomposes when the detection result changes. */
@Composable
private fun LiveStatus(cam: ScanCameraState, modifier: Modifier) {
    val text by remember(cam) { derivedStateOf { statusText(cam.live) } }
    StatusPill(text, modifier)
}
