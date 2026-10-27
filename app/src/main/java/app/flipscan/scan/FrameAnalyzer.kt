package app.flipscan.scan

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** What the live analysis saw in one frame. Coordinates are normalized to the upright frame. */
data class LiveResult(
    val quad: Quad?,
    val blurry: Boolean,
    val glare: Boolean,
    val imgW: Int,
    val imgH: Int
)

/**
 * Runs on a background executor. With STRATEGY_KEEP_ONLY_LATEST frames never queue up,
 * and we only analyze every 3rd frame.
 */
class FrameAnalyzer(
    private val cardMode: Boolean,
    private val onResult: (LiveResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount = 0
    private var buffer = ByteArray(0) // reused to avoid per-frame garbage

    override fun analyze(image: ImageProxy) {
        try {
            if (frameCount++ % 3 != 0) return
            val gray = toSmallUprightGray(image)
            try {
                val quad = Detector.findQuad(gray, cardMode)
                val sharp = Detector.sharpness(gray)
                val glare = if (quad != null) Detector.glareFraction(gray, quad) else 0.0
                onResult(
                    LiveResult(
                        quad, sharp < Thresholds.BLUR_MIN, glare > Thresholds.GLARE_MAX,
                        gray.cols(), gray.rows()
                    )
                )
            } finally {
                gray.release()
            }
        } catch (t: Throwable) {
            Log.w("FlipScan", "analysis failed", t)
        } finally {
            image.close()
        }
    }

    /** Takes the Y (brightness) plane = free grayscale, shrinks it, then rotates it upright. */
    private fun toSmallUprightGray(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val needed = rowStride * h
        if (buffer.size < needed) buffer = ByteArray(needed)
        val buf = plane.buffer
        buf.rewind()
        buf.get(buffer, 0, minOf(buf.remaining(), needed))

        val full = Mat(h, rowStride, CvType.CV_8UC1)
        full.put(0, 0, buffer, 0, needed)
        val cropped = full.submat(0, h, 0, w) // drop row padding
        val scale = Detector.ANALYSIS_SIZE / maxOf(w, h)
        val small = Mat()
        Imgproc.resize(cropped, small, Size(), scale, scale, Imgproc.INTER_AREA)

        val out = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(small, out, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(small, out, Core.ROTATE_180)
            270 -> Core.rotate(small, out, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> small.copyTo(out)
        }
        full.release(); cropped.release(); small.release()
        return out
    }
}
