package app.flipscan.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flipscan.data.Work
import app.flipscan.scan.AutoCaptureEffect
import app.flipscan.scan.PageProcessor
import app.flipscan.scan.ScanCameraState
import app.flipscan.scan.ScanCameraView
import app.flipscan.scan.rememberScanCameraState
import app.flipscan.scan.statusText
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CARD_RATIO = 85.6f / 54f
private val Green = Color(0xFF00E676)

@Composable
fun DoubleSideScreen(onBack: () -> Unit, onDone: (front: File, back: File) -> Unit) {
    CameraPermissionGate { DoubleSideContent(onBack, onDone) }
}

@Composable
private fun DoubleSideContent(onBack: () -> Unit, onDone: (File, File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cam = rememberScanCameraState(cardMode = true)
    val flash = remember { Animatable(0f) }

    var frontFile by remember { mutableStateOf<File?>(null) }
    var backFile by remember { mutableStateOf<File?>(null) }
    var frontThumb by remember { mutableStateOf<Bitmap?>(null) }
    var backThumb by remember { mutableStateOf<Bitmap?>(null) }
    var active by remember { mutableIntStateOf(0) } // 0 = front, 1 = back, -1 = both done
    var busy by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(true) }
    var confirmExit by remember { mutableStateOf(false) }
    var shots by remember { mutableIntStateOf(0) }

    val hasAny = frontFile != null || backFile != null
    BackHandler { if (hasAny) confirmExit = true else onBack() }
    DiscardDialog(confirmExit, onConfirm = { confirmExit = false; onBack() }, onDismiss = { confirmExit = false })

    fun shoot() {
        if (busy || active < 0) return
        val slot = active
        busy = true
        val raw = Work.newFile(ctx, "raw") // saved to app storage right away
        cam.takePicture(raw) { ok, quad ->
            if (!ok) {
                busy = false
                return@takePicture
            }
            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(380)) }
            scope.launch {
                val page = Work.newFile(ctx, if (slot == 0) "front" else "back")
                val good = withContext(Dispatchers.Default) { PageProcessor.process(raw, page, true, quad) }
                raw.delete()
                if (good) {
                    val thumb = withContext(Dispatchers.IO) { decodeSampled(page, 800) }
                    if (slot == 0) {
                        frontFile?.delete(); frontFile = page; frontThumb = thumb
                    } else {
                        backFile?.delete(); backFile = page; backThumb = thumb
                    }
                    shots++
                    // Move on to the other side if it is still empty, otherwise we are done.
                    active = when {
                        slot == 0 && backFile == null -> 1
                        slot == 1 && frontFile == null -> 0
                        else -> -1
                    }
                } else {
                    page.delete()
                }
                busy = false
            }
        }
    }

    AutoCaptureEffect(cam, enabled = auto && !busy && active >= 0, shots = shots) { shoot() }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (hasAny) confirmExit = true else onBack() }) { Text("← Back", color = Color.White) }
            Text("Double Side", color = Color.White, fontSize = 18.sp)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val gap = 14.dp
            val slotW = minOf(maxWidth, (maxHeight - gap) / 2 * CARD_RATIO)
            val slotH = slotW / CARD_RATIO
            val stepPx = with(LocalDensity.current) { (slotH + gap).toPx() }
            val offsetY = remember { Animatable(0f) }
            // Only the transform animates; the camera view itself keeps its size.
            LaunchedEffect(active) { if (active >= 0) offsetY.animateTo(active * stepPx, tween(380)) }

            Box(Modifier.align(Alignment.Center).size(slotW, slotH * 2 + gap)) {
                Slot("FRONT", frontThumb, active == 0, Modifier.align(Alignment.TopStart).size(slotW, slotH)) {
                    if (!busy) active = 0
                }
                Slot("BACK", backThumb, active == 1, Modifier.align(Alignment.BottomStart).size(slotW, slotH)) {
                    if (!busy) active = 1
                }
                if (active >= 0) {
                    Box(
                        Modifier
                            .size(slotW, slotH)
                            .graphicsLayer { translationY = offsetY.value } // read in draw phase: no recomposition
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        ScanCameraView(cam, Modifier.fillMaxSize(), compatible = true)
                        FlashOverlay({ flash.value }, Modifier.fillMaxSize())
                        if (busy) BusyOverlay("Processing…", Modifier.fillMaxSize())
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (active >= 0) {
                LiveStatus(cam, if (active == 0) "Front side" else "Back side")
            } else {
                StatusPill("Both sides captured. Tap a card to retake.")
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto", color = Color.White, fontSize = 12.sp)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                ShutterButton(enabled = !busy && active >= 0, progress = { cam.steady }, onClick = ::shoot)
                Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                    val f = frontFile
                    val b = backFile
                    if (f != null && b != null && !busy) Button(onClick = { onDone(f, b) }) { Text("Done") }
                }
            }
        }
    }
}

/** Own composable so only this small piece recomposes when the detection result changes. */
@Composable
private fun LiveStatus(cam: ScanCameraState, side: String) {
    val text by remember(cam) { derivedStateOf { statusText(cam.live) } }
    StatusPill("$side: $text")
}

@Composable
private fun Slot(label: String, thumb: Bitmap?, isActive: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(if (isActive) Green else Color(0xFF475569), tween(300), label = "border")
    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF1E293B))
            .border(2.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb == null) {
            Text(label, color = Color(0xFF64748B), fontSize = 22.sp)
        } else {
            // Captured photo pops in with a soft spring.
            val pop = remember(thumb) { Animatable(0f) }
            LaunchedEffect(thumb) { pop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
            val image = remember(thumb) { thumb.asImageBitmap() }
            Image(
                image, contentDescription = label, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val sc = 0.9f + 0.1f * pop.value
                    scaleX = sc
                    scaleY = sc
                    alpha = pop.value.coerceIn(0f, 1f)
                }
            )
            if (!isActive) {
                Text(
                    "$label · tap to retake", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
