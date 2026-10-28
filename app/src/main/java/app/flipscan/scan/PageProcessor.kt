package app.flipscan.scan

import org.opencv.core.Mat
import org.opencv.core.MatOfInt
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
    private const val ORIG_SIDE = 2400 // stored original: long side
    private const val CARD_RATIO = 85.6 / 54.0

    /**
     * Reads [raw], finds the document (falls back to [liveQuad], then to a sensible default),
     * writes the corrected page to [out] and keeps a downscaled original + the crop corners
     * next to it (see [Sidecar]) so the crop can be edited later.
     */
    fun process(raw: File, out: File, cardMode: Boolean, liveQuad: Quad?): Boolean {
        // imread applies the EXIF rotation, so the image is upright like the live preview.
        val src = Imgcodecs.imread(raw.absolutePath)
        var page: Mat? = null
        try {
            if (src.empty()) return false
            val found = detectOnCapture(src, cardMode) ?: liveQuad ?: defaultQuad(src, cardMode)
            val quad = orient(found, src.cols(), src.rows(), cardMode)
            page = render(src, quad, cardMode)
            if (!Imgcodecs.imwrite(out.absolutePath, page, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92))) return false
            writeOriginal(src, Sidecar.orig(out))
            Sidecar.writeQuad(out, quad)
            return true
        } finally {
            src.release()
            page?.release()
        }
    }

    /** Re-crops the stored original with a user-adjusted [quad]. */
    fun recrop(orig: File, out: File, quad: Quad, cardMode: Boolean): Boolean {
        val src = Imgcodecs.imread(orig.absolutePath)
        var page: Mat? = null
        try {
            if (src.empty()) return false
            page = render(src, quad, cardMode)
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

    /** Nothing detected: cards get a centered card-shaped box, documents the whole frame. */
    private fun defaultQuad(src: Mat, cardMode: Boolean): Quad {
        var wn = 1.0
        var hn = 1.0
        if (cardMode) {
            val a = src.cols().toDouble() / src.rows()
            if (a >= CARD_RATIO) wn = CARD_RATIO / a else hn = a / CARD_RATIO
        }
        val l = (1 - wn) / 2
        val t = (1 - hn) / 2
        return Quad(Pt(l, t), Pt(l + wn, t), Pt(l + wn, t + hn), Pt(l, t + hn))
    }

    /** Cards: if the shape is upright, cycle the corners so the result is landscape. */
    private fun orient(q: Quad, sw: Int, sh: Int, cardMode: Boolean): Quad {
        if (!cardMode) return q
        val px = q.toList().map { Pt(it.x * sw, it.y * sh) }
        val w = (dist(px[0], px[1]) + dist(px[3], px[2])) / 2
        val h = (dist(px[0], px[3]) + dist(px[1], px[2])) / 2
        return if (h > w) Quad(q.bl, q.tl, q.tr, q.br) else q
    }

    /** Warps the quad (as given: corner order decides the rotation) into the page image. */
    private fun render(src: Mat, quad: Quad, cardMode: Boolean): Mat {
        val sw = src.cols()
        val sh = src.rows()
        val px = quad.toList().map { Pt(it.x * sw, it.y * sh) }
        val w = (dist(px[0], px[1]) + dist(px[3], px[2])) / 2
        val h = (dist(px[0], px[3]) + dist(px[1], px[2])) / 2
        val mw = w.roundToInt().coerceAtLeast(1)
        val mh = h.roundToInt().coerceAtLeast(1)
        val warped = Detector.warp(src, quad, mw, mh)
        // Warp at measured size, then shrink with INTER_AREA (sharper than shrinking inside the warp).
        val (fw, fh) = when {
            !cardMode -> limit(mw, mh)
            mw >= mh -> CARD_W to CARD_H
            else -> CARD_H to CARD_W // a card the user rotated upright
        }
        return resized(warped, fw, fh)
    }

    private fun writeOriginal(src: Mat, out: File) {
        val s = minOf(1.0, ORIG_SIDE.toDouble() / maxOf(src.cols(), src.rows()))
        val small = Mat()
        try {
            if (s < 1.0) Imgproc.resize(src, small, Size(), s, s, Imgproc.INTER_AREA) else src.copyTo(small)
            Imgcodecs.imwrite(out.absolutePath, small, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 90))
        } finally {
            small.release()
        }
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
