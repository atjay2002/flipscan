package app.flipscan.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.flipscan.data.PageEditor
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.export.Exporter
import app.flipscan.scan.Pt
import app.flipscan.scan.Quad
import app.flipscan.scan.Sidecar
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class CropData(val bitmap: Bitmap, val quad: Quad)

private val FullImage = Quad(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 1.0))

/**
 * Drag the 4 corners to crop; perspective is corrected when applied.
 * The green corner becomes the top-left of the result, so rotating just moves the green corner.
 */
@Composable
fun CropScreen(scan: Scan, index: Int, onClose: (changed: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onClose(false) }

    val data by produceState<CropData?>(null, scan.id, index) {
        value = withContext(Dispatchers.IO) {
            val page = ScanStore.pageFiles(ctx, scan).getOrNull(index)
            val bmp = page?.let { decodeSampled(Sidecar.orig(it), 1600) }
            if (bmp == null) null else CropData(bmp, PageEditor.loadQuad(ctx, scan, index) ?: FullImage)
        }
    }
    var busy by remember { mutableStateOf(false) }
    val loaded = data

    Column(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onClose(false) }) { Text("Cancel", color = Color.White) }
            Text("Crop", color = Color.White, modifier = Modifier.weight(1f))
        }
        if (loaded == null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "The original photo is not stored for this page (it was scanned with an older version). " +
                        "You can still rotate it.",
                    color = Color.White
                )
            }
        } else {
            var quad by remember(loaded) { mutableStateOf(loaded.quad) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val bw = loaded.bitmap.width.toFloat()
                val bh = loaded.bitmap.height.toFloat()
                val scale = minOf(boxW / bw, boxH / bh)
                val iw = bw * scale
                val ih = bh * scale
                val left = (boxW - iw) / 2f
                val top = (boxH - ih) / 2f
                val grab = with(density) { 56.dp.toPx() }

                fun handle(p: Pt) = Offset(left + p.x.toFloat() * iw, top + p.y.toFloat() * ih)

                val image = remember(loaded) { loaded.bitmap.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier.fillMaxSize().pointerInput(loaded, left, top, iw, ih, grab) {
                        var active = -1
                        detectDragGestures(
                            onDragStart = { pos ->
                                val d = quad.toList().map { (handle(it) - pos).getDistance() }
                                val best = d.indices.minByOrNull { d[it] } ?: -1
                                active = if (best >= 0 && d[best] <= grab * 1.8f) best else -1
                            },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 },
                            onDrag = { change, _ ->
                                if (active >= 0) {
                                    change.consume()
                                    val nx = ((change.position.x - left) / iw).toDouble().coerceIn(0.0, 1.0)
                                    val ny = ((change.position.y - top) / ih).toDouble().coerceIn(0.0, 1.0)
                                    val l = quad.toList().toMutableList()
                                    l[active] = Pt(nx, ny)
                                    quad = Quad(l[0], l[1], l[2], l[3])
                                }
                            }
                        )
                    }
                ) {
                    val pts = quad.toList().map { handle(it) }
                    val poly = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1..3) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                    // Darken everything outside the crop.
                    val shade = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(left, top, left + iw, top + ih))
                        addPath(poly)
                    }
                    drawPath(shade, Color(0x99000000))
                    drawPath(poly, Color(0xFF00E676), style = Stroke(width = 2.5.dp.toPx()))
                    pts.forEachIndexed { i, p ->
                        drawCircle(Color.White, radius = 14.dp.toPx(), center = p)
                        drawCircle(if (i == 0) Color(0xFF00E676) else Color(0xFF4F46E5), radius = 10.dp.toPx(), center = p)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { quad = Quad(quad.tr, quad.br, quad.bl, quad.tl) }) {
                    Text("Rotate left", color = Color.White)
                }
                OutlinedButton(onClick = { quad = Quad(quad.bl, quad.tl, quad.tr, quad.br) }) {
                    Text("Rotate right", color = Color.White)
                }
                OutlinedButton(onClick = { quad = FullImage }) { Text("Reset", color = Color.White) }
            }
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.Default) {
                            PageEditor.applyCrop(ctx, scan, index, quad)
                            runCatching { Exporter.writeThumb(ctx, scan) }
                        }
                        busy = false
                        onClose(true)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Apply") }
        }
    }
    if (busy) BusyOverlay("Cropping…", Modifier.fillMaxSize())
}
