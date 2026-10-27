package app.flipscan.scan

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Tunable detection thresholds. Adjust after testing on real phones. */
object Thresholds {
    /** Variance of the Laplacian below this on the ~400 px frame means "blurry". */
    const val BLUR_MIN = 35.0
    /** Fraction of the document area that is blown-out white above this means "glare". */
    const val GLARE_MAX = 0.08
}

object Detector {
    /** Longest side (px) of the frame used for detection. */
    const val ANALYSIS_SIZE = 400.0
    private const val CARD_RATIO = 85.6 / 54.0

    /**
     * Finds the largest 4-corner shape in an upright 8-bit gray image.
     * Pipeline: blur -> Canny -> dilate -> contours -> polygon approximation.
     * Returns corners normalized to 0..1, or null.
     */
    fun findQuad(gray: Mat, cardMode: Boolean): Quad? {
        val w = gray.cols().toDouble()
        val h = gray.rows().toDouble()
        val frameArea = w * h
        val minArea = frameArea * (if (cardMode) 0.04 else 0.10)

        val blurred = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            // Canny thresholds follow the scene brightness so dim rooms still give edges.
            val mean = Core.mean(blurred).`val`[0]
            val lo = (0.66 * mean).coerceIn(20.0, 100.0)
            Imgproc.Canny(blurred, edges, lo, (lo * 2.5).coerceAtMost(255.0))
            Imgproc.dilate(edges, edges, kernel) // close small gaps in the outline
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            var best: Quad? = null
            var bestArea = 0.0
            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < minArea || area > frameArea * 0.97 || area <= bestArea) continue
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                for (eps in doubleArrayOf(0.02, 0.03, 0.04)) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                    val pts = approx.toArray()
                    approx.release()
                    if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                        val q = order(pts)
                        if (!cardMode || looksLikeCard(q)) {
                            best = Quad(
                                Pt(q[0].x / w, q[0].y / h), Pt(q[1].x / w, q[1].y / h),
                                Pt(q[2].x / w, q[2].y / h), Pt(q[3].x / w, q[3].y / h)
                            )
                            bestArea = area
                        }
                        break
                    }
                }
                c2f.release()
            }
            return best
        } finally {
            blurred.release(); edges.release(); kernel.release(); hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /** Orders 4 points as TL, TR, BR, BL. */
    private fun order(p: Array<Point>): Array<Point> {
        val tl = p.minByOrNull { it.x + it.y }!!
        val br = p.maxByOrNull { it.x + it.y }!!
        val tr = p.minByOrNull { it.y - it.x }!!
        val bl = p.maxByOrNull { it.y - it.x }!!
        return arrayOf(tl, tr, br, bl)
    }

    /** Long/short side ratio roughly matches an ID card (allows for perspective). */
    private fun looksLikeCard(q: Array<Point>): Boolean {
        val pts = q.map { Pt(it.x, it.y) }
        val w = (dist(pts[0], pts[1]) + dist(pts[3], pts[2])) / 2
        val h = (dist(pts[0], pts[3]) + dist(pts[1], pts[2])) / 2
        if (w < 1 || h < 1) return false
        val ratio = maxOf(w, h) / minOf(w, h)
        return ratio in (CARD_RATIO * 0.78)..(CARD_RATIO * 1.25)
    }

    /** Variance of the Laplacian: low = blurry. */
    fun sharpness(gray: Mat): Double {
        val lap = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        try {
            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            Core.meanStdDev(lap, mean, std)
            val s = std.get(0, 0)[0]
            return s * s
        } finally {
            lap.release(); mean.release(); std.release()
        }
    }

    /** Fraction of the quad's area that is almost pure white. */
    fun glareFraction(gray: Mat, quad: Quad): Double {
        val w = gray.cols()
        val h = gray.rows()
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        val bright = Mat()
        val poly = MatOfPoint(*quad.toList().map { Point(it.x * w, it.y * h) }.toTypedArray())
        try {
            Imgproc.fillConvexPoly(mask, poly, Scalar(255.0))
            Imgproc.threshold(gray, bright, 250.0, 255.0, Imgproc.THRESH_BINARY)
            Core.bitwise_and(bright, mask, bright)
            val area = Core.countNonZero(mask)
            return if (area == 0) 0.0 else Core.countNonZero(bright).toDouble() / area
        } finally {
            mask.release(); bright.release(); poly.release()
        }
    }

    /** Perspective-corrects the quad (normalized) of [src] into an outW x outH image. */
    fun warp(src: Mat, quad: Quad, outW: Int, outH: Int): Mat {
        val sw = src.cols().toDouble()
        val sh = src.rows().toDouble()
        val from = MatOfPoint2f(*quad.toList().map { Point(it.x * sw, it.y * sh) }.toTypedArray())
        val to = MatOfPoint2f(
            Point(0.0, 0.0), Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0), Point(0.0, outH - 1.0)
        )
        val m = Imgproc.getPerspectiveTransform(from, to)
        val out = Mat()
        Imgproc.warpPerspective(src, out, m, Size(outW.toDouble(), outH.toDouble()), Imgproc.INTER_CUBIC)
        from.release(); to.release(); m.release()
        return out
    }
}
