package app.flipscan.scan

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Owns the CameraX use cases. Preview, ImageAnalysis and ImageCapture are separate use cases:
 * the preview stays smooth, analysis is throttled, capture is full resolution.
 */
@Stable
class ScanCameraState(context: Context, val cardMode: Boolean) {
    /** Latest detection result. Read it in a draw scope to avoid recomposition. */
    var live by mutableStateOf<LiveResult?>(null)
        private set
    var capturing by mutableStateOf(false)
        private set

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        // ~8 MP is plenty for A4 at 300 dpi and keeps memory sane on mid-range phones.
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(3264, 2448), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build()
        )
        .build()

    val analysis: ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build().also { it.setAnalyzer(analysisExecutor, FrameAnalyzer(cardMode) { r -> live = r }) }

    /** Saves a full-res JPEG to [file] (app-private). [onDone] runs on the main thread. */
    fun takePicture(file: File, onDone: (ok: Boolean, quad: Quad?) -> Unit) {
        if (capturing) return
        capturing = true
        val quad = live?.quad
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onDone(true, quad)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    onDone(false, null)
                }
            }
        )
    }

    fun clearLive() { live = null }

    fun close() {
        analysis.clearAnalyzer()
        analysisExecutor.shutdown()
    }
}

@Composable
fun rememberScanCameraState(cardMode: Boolean): ScanCameraState {
    val ctx = LocalContext.current
    val state = remember(cardMode) { ScanCameraState(ctx, cardMode) }
    DisposableEffect(state) { onDispose { state.close() } }
    return state
}

/**
 * Camera preview with the corner overlay on top.
 * [compatible] = TextureView (needed when the view is moved/animated); otherwise SurfaceView.
 * [active] = false unbinds the camera (saves battery while the preview is hidden).
 */
@Composable
fun ScanCameraView(
    state: ScanCameraState,
    modifier: Modifier = Modifier,
    compatible: Boolean = false,
    active: Boolean = true
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode =
                if (compatible) PreviewView.ImplementationMode.COMPATIBLE
                else PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(owner, active) {
        val future = ProcessCameraProvider.getInstance(context)
        if (active) {
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, state.imageCapture, state.analysis
                )
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            if (active && future.isDone) future.get().unbindAll()
            state.clearLive()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        CornerOverlay(state, Modifier.matchParentSize())
    }
}

/** Draws the detected corners. Reading state inside the draw lambda = redraw only, no recomposition. */
@Composable
private fun CornerOverlay(state: ScanCameraState, modifier: Modifier) {
    Canvas(modifier) {
        val r = state.live ?: return@Canvas
        val q = r.quad ?: return@Canvas
        // Same math as PreviewView FILL_CENTER: scale to cover, centered.
        val scale = max(size.width / r.imgW, size.height / r.imgH)
        val dx = (size.width - r.imgW * scale) / 2f
        val dy = (size.height - r.imgH * scale) / 2f
        val pts = q.toList().map { Offset((it.x * r.imgW * scale + dx).toFloat(), (it.y * r.imgH * scale + dy).toFloat()) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..3) lineTo(pts[i].x, pts[i].y)
            close()
        }
        val green = Color(0xFF00E676)
        drawPath(path, green.copy(alpha = 0.18f))
        drawPath(path, green, style = Stroke(width = 3.dp.toPx()))
        pts.forEach { drawCircle(green, radius = 7.dp.toPx(), center = it) }
    }
}

/** Fires [onTrigger] once the document has been detected and held still for about 1 second. */
@Composable
fun AutoCaptureEffect(state: ScanCameraState, enabled: Boolean, onTrigger: () -> Unit) {
    val trigger by rememberUpdatedState(onTrigger)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        var last: Quad? = null
        var since = 0L
        snapshotFlow { state.live }.collect { r ->
            val q = r?.quad
            if (q == null) {
                last = null
                return@collect
            }
            val now = SystemClock.elapsedRealtime()
            val prev = last
            if (prev == null || q.maxShift(prev) > 0.02) { // moved more than 2% of the frame: restart
                last = q
                since = now
            } else if (now - since >= 1000) {
                last = null
                trigger()
            }
        }
    }
}

/** Short hint shown under the preview. */
fun statusText(r: LiveResult?): String = when {
    r == null -> "Starting camera…"
    r.quad == null -> "Place the document inside the frame"
    r.blurry -> "Blurry: hold the phone steady"
    r.glare -> "Glare: tilt to avoid reflections"
    else -> "Hold steady…"
}
