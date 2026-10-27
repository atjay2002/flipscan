package app.flipscan.scan

import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.roundToInt

/** Turns a full-resolution capture into a cropped, straightened page image. Heavy: call off the main thread. */
object PageProcessor {
    // Standard card 85.6 x 54 mm at 300 dpi
    const val CARD_W = 1011
    const val CARD_H = 638
    private const val MAX_SIDE = 3508 // A4 long side at 300 dpi
    private const val CARD_RATIO = 85.6 / 54.0

    /**
     * Reads [raw], finds the document (falls back to [liveQuad], then to the whole image),
     * and writes the corrected page to [out] as JPEG. Only one full-res image is in memory.
     */
    fun process(raw: File, out: File, cardMode: Boolean, liveQuad: Quad?): Boolean {
        // imread applies the EXIF rotation, so the image is upright like the live preview.
        val src = Imgcodecs.imread(raw.absolutePath)
        var page: Mat? = null
        try {
            if (src.empty()) return false
            val quad = detectOnCapture(src, cardMode) ?: liveQuad
            page = if (quad != null) crop(src, quad, cardMode) else fallback(src, cardMode)
            return Imgcodecs.imwrite(out.absolutePath, page, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92))
        } finally {
            src.release()
            page?.release()
        }
    }

    /** Re-detect on a shrunken copy of the real photo: more accurate than the last live frame. */
    private fun detectOnCapture(src: Mat, cardMode: Boolean): Quad? {
        val scale = 480.0 / maxOf(src.cols(), src.rows())
        val small = Mat()
        val gray = Mat()
        try {
            Imgproc.resize(src, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
            return Detector.findQuad(gray, cardMode)
        } finally {
            small.release(); gray.release()
        }
    }

    private fun crop(src: Mat, quad: Quad, cardMode: Boolean): Mat {
        val sw = src.cols()
        val sh = src.rows()
        val px = quad.toList().map { Pt(it.x * sw, it.y * sh) }
        var w = (dist(px[0], px[1]) + dist(px[3], px[2])) / 2
        var h = (dist(px[0], px[3]) + dist(px[1], px[2])) / 2
        var q = quad
        if (cardMode && h > w) {
            // Card was shot upright: rotate the corner order so the result is landscape.
            q = Quad(quad.bl, quad.tl, quad.tr, quad.br)
            val t = w; w = h; h = t
        }
        val mw = w.roundToInt().coerceAtLeast(1)
        val mh = h.roundToInt().coerceAtLeast(1)
        val warped = Detector.warp(src, q, mw, mh)
        // Warp at measured size, then shrink with INTER_AREA (sharper than shrinking inside the warp).
        val (fw, fh) = if (cardMode) CARD_W to CARD_H else limit(mw, mh)
        return resized(warped, fw, fh)
    }

    /** No document found: keep the whole frame (cards: center crop to card shape). */
    private fun fallback(src: Mat, cardMode: Boolean): Mat {
        if (!cardMode) {
            val (fw, fh) = limit(src.cols(), src.rows())
            val copy = Mat()
            src.copyTo(copy)
            return resized(copy, fw, fh)
        }
        val sw = src.cols()
        val sh = src.rows()
        val roi = if (sw / sh.toDouble() >= CARD_RATIO) {
            val cw = (sh * CARD_RATIO).roundToInt()
            Rect((sw - cw) / 2, 0, cw, sh)
        } else {
            val ch = (sw / CARD_RATIO).roundToInt()
            Rect(0, (sh - ch) / 2, sw, ch)
        }
        val part = src.submat(roi)
        val copy = Mat()
        part.copyTo(copy)
        part.release()
        return resized(copy, CARD_W, CARD_H)
    }

    private fun limit(w: Int, h: Int): Pair<Int, Int> {
        val s = minOf(1.0, MAX_SIDE.toDouble() / maxOf(w, h))
        return (w * s).roundToInt().coerceAtLeast(1) to (h * s).roundToInt().coerceAtLeast(1)
    }

    /** Resizes [m] to w x h and releases [m]. */
    private fun resized(m: Mat, w: Int, h: Int): Mat {
        if (m.cols() == w && m.rows() == h) return m
        val out = Mat()
        val interp = if (w < m.cols()) Imgproc.INTER_AREA else Imgproc.INTER_CUBIC
        Imgproc.resize(m, out, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, interp)
        m.release()
        return out
    }
}
