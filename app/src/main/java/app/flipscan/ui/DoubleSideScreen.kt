package app.flipscan.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import app.flipscan.scan.Sidecar
import app.flipscan.scan.rememberScanCameraState
import app.flipscan.scan.statusText
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CARD_RATIO = 85.6f / 54f
private val Green = Color(0xFF34D399)

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
    val flashMs = motionMs(380)
    val slideMs = motionMs(380)

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
            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(flashMs)) }
            scope.launch {
                val page = Work.newFile(ctx, if (slot == 0) "front" else "back")
                val good = withContext(Dispatchers.Default) { PageProcessor.process(raw, page, true, quad) }
                raw.delete()
                if (good) {
                    val thumb = withContext(Dispatchers.IO) { decodeSampled(page, 800) }
                    if (slot == 0) {
                        frontFile?.let { Sidecar.deleteAll(it) }; frontFile = page; frontThumb = thumb
                    } else {
                        backFile?.let { Sidecar.deleteAll(it) }; backFile = page; backThumb = thumb
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

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(CameraTop, Color(0xFF101A2E), CameraBottom)))
    ) {
        CameraHeader(
            title = "ID card",
            subtitle = "Both sides on one page",
            onBack = { if (hasAny) confirmExit = true else onBack() }
        ) {
            // Only meaningful while a camera is bound (the preview composes only for an active slot).
            if (active >= 0) FlashToggle(cam, Modifier.padding(end = 4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepPill(1, "Front", done = frontFile != null, active = active == 0)
            Box(Modifier.size(width = 14.dp, height = 1.dp).background(Color.White.copy(alpha = 0.2f)))
            StepPill(2, "Back", done = backFile != null, active = active == 1)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            val gap = 12.dp
            val slotW = minOf(maxWidth, (maxHeight - gap) / 2 * CARD_RATIO)
            val slotH = slotW / CARD_RATIO
            val stepPx = with(LocalDensity.current) { (slotH + gap).toPx() }
            val offsetY = remember { Animatable(0f) }
            // Only the transform animates; the camera view itself keeps its size.
            LaunchedEffect(active) { if (active >= 0) offsetY.animateTo(active * stepPx, tween(slideMs)) }

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
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        ScanCameraView(cam, Modifier.fillMaxSize(), compatible = true)
                        FlashOverlay({ flash.value }, Modifier.fillMaxSize())
                        if (busy) BusyOverlay("Processing…", Modifier.fillMaxSize())
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (active >= 0) {
                LiveStatus(cam, if (active == 0) "Front" else "Back")
            } else {
                StatusPill("Both sides captured", accent = Green)
            }
            Text(
                when {
                    active == 0 -> "Fill the frame with the front of the card"
                    active == 1 && frontFile == null -> "Fill the frame with the back of the card"
                    active == 1 -> "Now flip the card for the back side"
                    else -> "Tap a card above to retake it"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoCaptureToggle(auto, Modifier.width(90.dp)) { auto = it }
                ShutterButton(enabled = !busy && active >= 0, progress = { cam.steady }, onClick = ::shoot)
                Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                    val f = frontFile
                    val b = backFile
                    if (f != null && b != null && !busy) {
                        Button(
                            onClick = { onDone(f, b) },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = CameraTop
                            )
                        ) { Text("Review") }
                    }
                }
            }
        }
    }
}

/** Own composable so only this small piece recomposes when the detection result changes. */
@Composable
private fun LiveStatus(cam: ScanCameraState, side: String) {
    val text by remember(cam) { derivedStateOf { statusText(cam.live) } }
    val accent = when {
        cam.live?.quad == null -> Color(0xFF94A3B8)
        cam.live?.blurry == true || cam.live?.glare == true -> Amber
        else -> Green
    }
    StatusPill("$side: $text", accent = accent)
}

@Composable
private fun Slot(label: String, thumb: Bitmap?, isActive: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor by animateColorAsState(
        if (isActive) Green else Color.White.copy(alpha = 0.12f),
        tween(motionMs(260)),
        label = "slotBorder"
    )
    Box(
        modifier
            .clip(shape)
            .background(CameraCard)
            .border(if (isActive) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    CardStackIcon(Modifier.size(22.dp), Color.White.copy(alpha = 0.4f))
                }
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "Tap to capture",
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            // Captured photo pops in with a short, motion-preference-aware fade/scale.
            val pop = remember(thumb) { Animatable(0f) }
            val popMs = motionMs(220)
            LaunchedEffect(thumb) { pop.animateTo(1f, tween(popMs, easing = FastOutSlowInEasing)) }
            val image = remember(thumb) { thumb.asImageBitmap() }
            Image(
                image,
                contentDescription = label,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val sc = 0.94f + 0.06f * pop.value
                    scaleX = sc
                    scaleY = sc
                    alpha = pop.value.coerceIn(0f, 1f)
                }
            )
            if (!isActive) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color(0xCC0B1220)
                        )
                    )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Green))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$label captured · tap to retake",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
