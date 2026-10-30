package app.flipscan.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.flipscan.data.Mode
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
private val Accent = Color(0xFF34D399)

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
    val pageName = if (scan.mode == Mode.CARDS) (if (index == 0) "Front" else "Back") else "Page ${index + 1}"

    Column(Modifier.fillMaxSize().background(CameraTop)) {
        CameraHeader(
            title = "Adjust crop",
            subtitle = pageName,
            onBack = { onClose(false) }
        ) {
            TextButton(onClick = { onClose(false) }) {
                Text("Cancel", color = Color.White.copy(alpha = 0.85f))
            }
        }

        if (loaded == null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(58.dp).background(Color.White.copy(alpha = 0.06f), MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center
                    ) { CropIcon(Modifier.size(26.dp), Color.White.copy(alpha = 0.5f)) }
                    Text(
                        "This page has no stored original",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "It was scanned with an older version of FlipScan, so it cannot be re-cropped. " +
                            "You can still rotate it from the previous screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = { onClose(false) },
                        modifier = Modifier.padding(top = 18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) { Text("Close") }
                }
            }
        } else {
            var quad by remember(loaded) { mutableStateOf(loaded.quad) }
            var activeHandle by remember { mutableIntStateOf(-1) }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
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
                val grab = with(density) { 48.dp.toPx() }

                fun handle(p: Pt) = Offset(left + p.x.toFloat() * iw, top + p.y.toFloat() * ih)

                val image = remember(loaded) { loaded.bitmap.asImageBitmap() }
                Image(image, contentDescription = "Crop preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier.fillMaxSize().pointerInput(loaded, left, top, iw, ih, grab) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val d = quad.toList().map { (handle(it) - pos).getDistance() }
                                val best = d.indices.minByOrNull { d[it] } ?: -1
                                activeHandle = if (best >= 0 && d[best] <= grab * 2.2f) best else -1
                            },
                            onDragEnd = { activeHandle = -1 },
                            onDragCancel = { activeHandle = -1 },
                            onDrag = { change, _ ->
                                if (activeHandle >= 0) {
                                    change.consume()
                                    val nx = ((change.position.x - left) / iw).toDouble().coerceIn(0.0, 1.0)
                                    val ny = ((change.position.y - top) / ih).toDouble().coerceIn(0.0, 1.0)
                                    val l = quad.toList().toMutableList()
                                    l[activeHandle] = Pt(nx, ny)
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
                    // Everything outside the crop is dimmed, so the selection reads at a glance.
                    val shade = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(left, top, left + iw, top + ih))
                        addPath(poly)
                    }
                    drawPath(shade, Color(0xAA000000))

                    // Thirds grid inside the selection, drawn only with the crop area.
                    clipPath(poly) {
                        val g = Color.White.copy(alpha = 0.16f)
                        for (i in 1..2) {
                            val x = left + iw * i / 3f
                            val y = top + ih * i / 3f
                            drawLine(g, Offset(x, top), Offset(x, top + ih), strokeWidth = 1.dp.toPx())
                            drawLine(g, Offset(left, y), Offset(left + iw, y), strokeWidth = 1.dp.toPx())
                        }
                    }

                    drawPath(poly, Accent, style = Stroke(width = 2.dp.toPx()))
                    pts.forEachIndexed { i, p ->
                        val active = i == activeHandle
                        drawCircle(Color.Black.copy(alpha = 0.25f), radius = (if (active) 20.dp else 17.dp).toPx(), center = p)
                        drawCircle(Color.White, radius = (if (active) 14.dp else 12.dp).toPx(), center = p)
                        drawCircle(if (i == 0) Accent else Color(0xFF6366F1), radius = (if (active) 9.dp else 7.dp).toPx(), center = p)
                    }

                    // Precision loupe: magnifies the real pixels around the corner being dragged.
                    // It reads the already-loaded bitmap (no decode per event) and is drawn as a
                    // clipped image, so the magnified content is the photo itself, not the overlay.
                    val corner = pts.getOrNull(activeHandle)
                    if (corner != null) {
                        val r = 68.dp.toPx() // ~136 dp diameter
                        val gapPx = 28.dp.toPx()
                        val edge = 6.dp.toPx()
                        var cx = corner.x
                        var cy = corner.y - r - gapPx
                        if (cy - r < edge) {
                            // Not enough room above (top corners): sit beside the finger instead.
                            cy = corner.y
                            cx = if (corner.x < boxW / 2f) corner.x + r + gapPx else corner.x - r - gapPx
                        }
                        cx = cx.coerceIn(r + edge, (boxW - r - edge).coerceAtLeast(r + edge))
                        cy = cy.coerceIn(r + edge, (boxH - r - edge).coerceAtLeast(r + edge))

                        val zoom = 2.4f
                        val half = (r / (scale * zoom)).coerceAtMost(minOf(bw, bh) / 2f)
                        val ix = (corner.x - left) / scale
                        val iy = (corner.y - top) / scale
                        val sx = if (bw <= half * 2f) bw / 2f else ix.coerceIn(half, bw - half)
                        val sy = if (bh <= half * 2f) bh / 2f else iy.coerceIn(half, bh - half)

                        val circle = Path().apply {
                            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                        }
                        clipPath(circle) {
                            drawImage(
                                image = image,
                                srcOffset = IntOffset((sx - half).toInt(), (sy - half).toInt()),
                                srcSize = IntSize(
                                    (half * 2f).toInt().coerceAtLeast(1),
                                    (half * 2f).toInt().coerceAtLeast(1)
                                ),
                                dstOffset = IntOffset((cx - r).toInt(), (cy - r).toInt()),
                                dstSize = IntSize((r * 2f).toInt(), (r * 2f).toInt())
                            )
                            // Crosshair marks the corner inside the loupe, even when the sampled
                            // window had to be clamped at the edge of the photo.
                            val perPixel = r / half
                            val crossX = cx + (ix - sx) * perPixel
                            val crossY = cy + (iy - sy) * perPixel
                            val line = 1.dp.toPx()
                            drawLine(Color.Black.copy(alpha = 0.5f), Offset(crossX - r, crossY), Offset(crossX + r, crossY), strokeWidth = line * 2.4f)
                            drawLine(Color.Black.copy(alpha = 0.5f), Offset(crossX, crossY - r), Offset(crossX, crossY + r), strokeWidth = line * 2.4f)
                            drawLine(Color.White, Offset(crossX - r, crossY), Offset(crossX + r, crossY), strokeWidth = line)
                            drawLine(Color.White, Offset(crossX, crossY - r), Offset(crossX, crossY + r), strokeWidth = line)
                            drawCircle(Accent, radius = 3.5.dp.toPx(), center = Offset(crossX, crossY))
                        }
                        drawCircle(
                            Color.Black.copy(alpha = 0.45f),
                            radius = r + 2.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(4.dp.toPx())
                        )
                        drawCircle(Color.White, radius = r, center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
            }

            Text(
                "Drag the corners to frame the page. The green corner becomes the top-left.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { quad = Quad(quad.tr, quad.br, quad.bl, quad.tl); activeHandle = -1 },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                    shape = MaterialTheme.shapes.small
                ) {
                    RotateIcon(Modifier.size(16.dp), Color.White, clockwise = false)
                    Spacer(Modifier.width(7.dp))
                    Text("Rotate left")
                }
                OutlinedButton(
                    onClick = { quad = Quad(quad.bl, quad.tl, quad.tr, quad.br); activeHandle = -1 },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                    shape = MaterialTheme.shapes.small
                ) {
                    RotateIcon(Modifier.size(16.dp), Color.White, clockwise = true)
                    Spacer(Modifier.width(7.dp))
                    Text("Rotate right")
                }
                OutlinedButton(
                    onClick = { quad = FullImage; activeHandle = -1 },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                    shape = MaterialTheme.shapes.small
                ) { Text("Reset") }
            }

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.Default) {
                            val applied = PageEditor.applyCrop(ctx, scan, index, quad)
                            if (applied) runCatching { Exporter.writeThumb(ctx, scan) }
                            applied
                        }
                        busy = false
                        onClose(ok)
                    }
                },
                enabled = !busy,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 16.dp, top = 2.dp)
            ) { Text("Apply crop") }
        }
    }

    AnimatedVisibility(visible = busy, enter = fadeIn(tween(motionMs(140)))) {
        BusyOverlay("Cropping…", Modifier.fillMaxSize())
    }
}
