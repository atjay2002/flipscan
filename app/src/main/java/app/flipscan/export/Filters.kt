package app.flipscan.export

import android.graphics.Bitmap
import app.flipscan.data.Filter
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Image filters. They run on the size that is actually drawn: tiny for previews, full only at export. */
object Filters {
    /** Returns the filtered bitmap. [src] is recycled when a new bitmap is returned. */
    fun apply(src: Bitmap, filter: Filter): Bitmap {
        if (filter == Filter.ORIGINAL) return src
        val rgba = Mat()
        val out = Mat()
        try {
            Utils.bitmapToMat(src, rgba) // RGBA
            when (filter) {
                Filter.GRAY -> Imgproc.cvtColor(rgba, out, Imgproc.COLOR_RGBA2GRAY)
                Filter.BW -> blackAndWhite(rgba, out)
                Filter.MAGIC -> magicColor(rgba, out)
                Filter.ORIGINAL -> return src
            }
            val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, result) // accepts 1-channel (gray/B&W) and 3-channel (RGB) mats
            src.recycle()
            return result
        } finally {
            rgba.release()
            out.release()
        }
    }

    /** Adaptive threshold: handles uneven lighting better than one global cut-off. */
    private fun blackAndWhite(rgba: Mat, out: Mat) {
        val gray = Mat()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            val block = (maxOf(gray.cols(), gray.rows()) / 30).coerceIn(15, 101) or 1 // must be odd
            Imgproc.adaptiveThreshold(
                gray, out, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 12.0
            )
        } finally {
            gray.release()
        }
    }

    /**
     * Keeps colour but removes shadows and whitens the paper:
     * estimate the lighting with a big blur (on a quarter-size copy, which is fast),
     * divide it out, then add a little contrast.
     */
    private fun magicColor(rgba: Mat, out: Mat) {
        val rgb = Mat()
        val small = Mat()
        val background = Mat()
        val flat = Mat()
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.resize(rgb, small, Size(), 0.25, 0.25, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(small, small, Size(), maxOf(small.cols(), small.rows()) / 12.0)
            Imgproc.resize(small, background, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.divide(rgb, background, flat, 255.0)
            flat.convertTo(out, -1, 1.15, -20.0)
        } finally {
            rgb.release(); small.release(); background.release(); flat.release()
        }
    }
}
