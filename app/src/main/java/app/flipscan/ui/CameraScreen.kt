package app.flipscan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flipscan.data.Work
import app.flipscan.scan.AutoCaptureEffect
import app.flipscan.scan.PageProcessor
import app.flipscan.scan.ScanCameraView
import app.flipscan.scan.rememberScanCameraState
import app.flipscan.scan.statusText
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Milestone 2 test screen: camera + corner overlay + auto/manual capture + cropped result. */
@Composable
fun CameraScreen() {
    CameraPermissionGate { CameraContent() }
}

@Composable
private fun CameraContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cam = rememberScanCameraState(cardMode = false)
    var busy by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<Bitmap?>(null) }

    fun shoot() {
        if (busy) return
        busy = true
        val raw = Work.newFile(ctx, "raw")
        cam.takePicture(raw) { ok, quad ->
            if (!ok) {
                busy = false
                return@takePicture
            }
            scope.launch {
                val page = Work.newFile(ctx, "page")
                val good = withContext(Dispatchers.Default) { PageProcessor.process(raw, page, false, quad) }
                raw.delete()
                result = if (good) withContext(Dispatchers.IO) { decodeSampled(page, 1200) } else null
                busy = false
            }
        }
    }

    AutoCaptureEffect(cam, enabled = auto && !busy && result == null) { shoot() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ScanCameraView(cam, Modifier.fillMaxSize())
        StatusPill(statusText(cam.live), Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Text("Auto", color = Color.White)
            }
            Switch(checked = auto, onCheckedChange = { auto = it })
            ShutterButton(enabled = !busy, onClick = ::shoot)
        }
        val r = result
        if (r != null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Image(r.asImageBitmap(), contentDescription = "Result", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Button(onClick = { result = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                    Text("Scan again")
                }
            }
        }
        if (busy) BusyOverlay("Processing…", Modifier.fillMaxSize())
    }
}
