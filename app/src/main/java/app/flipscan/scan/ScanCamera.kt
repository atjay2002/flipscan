package app.flipscan.scan

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
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

    /** 0..1: how long the document has been held steady (1 = auto-capture fires). */
    var steady by mutableFloatStateOf(0f)
        private set

    /**
     * Torch support of the bound camera: null = camera not bound yet, true/false = known.
     * The UI reads this to disable or hide the flash control instead of guessing.
     */
    var flashAvailable by mutableStateOf<Boolean?>(null)
        private set

    /** Mirrors the camera's real torch state (updated by CameraX, not by the last tap). */
    var torchOn by mutableStateOf(false)
        private set

    private var boundCamera: Camera? = null
    private val torchObserver = Observer<Int> { state -> torchOn = state == TorchState.ON }
    // If the camera is closed (another app grabbed it, screen turned off, ...) the torch is off too;
    // mirror that so the button never shows a stale "on" state.
    private val cameraStateObserver = Observer<CameraState> { s ->
        if (s.type == CameraState.Type.CLOSED || s.type == CameraState.Type.PENDING_OPEN) torchOn = false
    }

    fun updateSteady(v: Float) { steady = v }

    /** Called right after CameraX binds the use cases (main thread). */
    fun attachCamera(camera: Camera) {
        detachCamera()
        boundCamera = camera
        flashAvailable = camera.cameraInfo.hasFlashUnit()
        camera.cameraInfo.torchState.observeForever(torchObserver)
        camera.cameraInfo.cameraState.observeForever(cameraStateObserver)
        torchOn = camera.cameraInfo.torchState.value == TorchState.ON
    }

    fun detachCamera() {
        boundCamera?.cameraInfo?.torchState?.removeObserver(torchObserver)
        boundCamera?.cameraInfo?.cameraState?.removeObserver(cameraStateObserver)
        boundCamera = null
        torchOn = false
        flashAvailable = null
    }

    /** Continuous torch (not still-photo flash), matching how CameraX preview illumination works. */
    fun setTorch(on: Boolean) {
        val camera = boundCamera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) return
        camera.cameraControl.enableTorch(on)
    }

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
        detachCamera()
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
        var disposed = false
        if (active) {
            future.addListener({
                // The screen may have been left before the provider finished starting.
                if (disposed) return@addListener
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, state.imageCapture, state.analysis
                )
                state.attachCamera(camera)
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            state.detachCamera()
            if (active && future.isDone) future.get().unbindAll()
            state.clearLive()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        CornerOverlay(state, Modifier.matchParentSize())
    }
}

private val Green = Color(0xFF00E676)
private val Amber = Color(0xFFFFB300)

/**
 * Draws the detected corners (smoothed frame by frame, green = good, amber = blur/glare)
 * and a sweeping scan line while nothing is found.
 * Everything is read inside the draw lambda, so there is no recomposition.
 */
@Composable
private fun CornerOverlay(state: ScanCameraState, modifier: Modifier) {
    val smooth = remember { FloatArray(8) }
    var tick by remember { mutableIntStateOf(0) }
    val sweep = rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweepValue"
    )

    // Each frame, glide the shown corners toward the latest detection (no more jumping boxes).
    LaunchedEffect(state) {
        var has = false
        while (true) {
            withFrameNanos { }
            val q = state.live?.quad
            if (q == null) {
                if (has) { has = false; tick++ }
            } else {
                val t = floatArrayOf(
                    q.tl.x.toFloat(), q.tl.y.toFloat(), q.tr.x.toFloat(), q.tr.y.toFloat(),
                    q.br.x.toFloat(), q.br.y.toFloat(), q.bl.x.toFloat(), q.bl.y.toFloat()
                )
                if (!has) {
                    t.copyInto(smooth)
                    has = true
                } else {
                    for (i in 0 until 8) smooth[i] += (t[i] - smooth[i]) * 0.3f
                }
                tick++
            }
        }
    }

    Canvas(modifier) {
        if (tick < 0) return@Canvas // reading it here makes the canvas redraw on each smoothing step
        val r = state.live ?: return@Canvas
        if (r.quad == null) {
            val y = size.height * sweep.value
            val band = 70.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x5500E5FF), Color.Transparent),
                    startY = y - band, endY = y + band
                ),
                topLeft = Offset(0f, y - band), size = GSize(size.width, band * 2)
            )
            drawLine(Color(0xAA00E5FF), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            return@Canvas
        }
        // Same math as PreviewView FILL_CENTER: scale to cover, centered.
        val scale = max(size.width / r.imgW, size.height / r.imgH)
        val dx = (size.width - r.imgW * scale) / 2f
        val dy = (size.height - r.imgH * scale) / 2f
        val pts = (0..3).map { i ->
            Offset(smooth[2 * i] * r.imgW * scale + dx, smooth[2 * i + 1] * r.imgH * scale + dy)
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..3) lineTo(pts[i].x, pts[i].y)
            close()
        }
        val col = if (r.blurry || r.glare) Amber else Green
        drawPath(path, col.copy(alpha = 0.10f + 0.22f * state.steady)) // fills up while holding steady
        drawPath(path, col, style = Stroke(width = 3.dp.toPx()))
        pts.forEach {
            drawCircle(Color.White, radius = 9.dp.toPx(), center = it)
            drawCircle(col, radius = 6.dp.toPx(), center = it)
        }
    }
}

/**
 * Fires [onTrigger] once the document has been detected and held still for about 1 second.
 * [shots] = number of captures so far. After a capture it waits for the page to be moved or
 * taken away (at most 2.5 s), so the same page is not captured twice.
 * It polls every ~80 ms, so the timer advances even when two results look identical.
 */
@Composable
fun AutoCaptureEffect(state: ScanCameraState, enabled: Boolean, shots: Int, onTrigger: () -> Unit) {
    val trigger by rememberUpdatedState(onTrigger)
    LaunchedEffect(state, enabled, shots) {
        state.updateSteady(0f)
        if (!enabled) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        var waiting = shots > 0
        var anchor: Quad? = null
        var last: Quad? = null
        var since = 0L
        while (true) {
            delay(80)
            val q = state.live?.quad
            val now = SystemClock.elapsedRealtime()
            if (waiting) {
                if (q == null || now - startedAt > 2500) {
                    waiting = false
                } else {
                    val a = anchor
                    if (a == null) anchor = q else if (q.maxShift(a) > 0.06) waiting = false
                }
                if (waiting) continue
            }
            if (q == null) {
                last = null
                state.updateSteady(0f)
                continue
            }
            val prev = last
            if (prev == null || q.maxShift(prev) > 0.02) { // moved more than 2% of the frame: restart
                last = q
                since = now
                state.updateSteady(0f)
            } else {
                val p = ((now - since) / 1000f).coerceIn(0f, 1f)
                state.updateSteady(p)
                if (p >= 1f) {
                    last = null
                    state.updateSteady(0f)
                    trigger()
                }
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
