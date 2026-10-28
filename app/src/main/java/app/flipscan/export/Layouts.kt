package app.flipscan.export

import app.flipscan.data.CardLayout
import app.flipscan.data.ExportSettings

/** An image placed on the sheet. All numbers are millimetres. */
data class Placed(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float)

data class SheetPlan(val widthMm: Float, val heightMm: Float, val items: List<Placed>, val cutLine: Boolean)

/** Pure layout maths: no bitmaps involved, so it is cheap to run on every settings change. */
object Layouts {
    const val CARD_W_MM = 85.6f
    const val CARD_H_MM = 54f
    private const val GAP_MM = 10f

    /**
     * Both card sides on one sheet. [sizes] = pixel size of each stored side: a card is landscape
     * (85.6 x 54 mm) unless the user rotated it, then it is placed portrait (54 x 85.6 mm).
     */
    fun cards(s: ExportSettings, sizes: List<Pair<Int, Int>>): SheetPlan {
        val (pw, ph) = s.sheetSize(autoLandscape = s.layout == CardLayout.SIDE_BY_SIDE)
        val m = s.marginMm
        val availW = (pw - 2 * m).coerceAtLeast(10f)
        val availH = (ph - 2 * m).coerceAtLeast(10f)
        val stacked = s.layout == CardLayout.STACKED

        val dims = sizes.take(2).map { (w, h) -> if (h > w) CARD_H_MM to CARD_W_MM else CARD_W_MM to CARD_H_MM }
        val (w0, h0) = dims[0]
        val (w1, h1) = dims[1]
        val blockW = if (stacked) maxOf(w0, w1) else w0 + w1 + GAP_MM
        val blockH = if (stacked) h0 + h1 + GAP_MM else maxOf(h0, h1)
        val fit = minOf(availW / blockW, availH / blockH)
        // Real size = 1:1, but never bigger than fits on the page.
        val scale = if (s.realSize) minOf(1f, fit) else fit

        val bw = blockW * scale
        val bh = blockH * scale
        val gap = GAP_MM * scale
        val x0 = (pw - bw) / 2f
        val y0 = m
        val a: Placed
        val b: Placed
        if (stacked) {
            a = Placed(0, x0 + (bw - w0 * scale) / 2f, y0, w0 * scale, h0 * scale)
            b = Placed(1, x0 + (bw - w1 * scale) / 2f, y0 + h0 * scale + gap, w1 * scale, h1 * scale)
        } else {
            a = Placed(0, x0, y0 + (bh - h0 * scale) / 2f, w0 * scale, h0 * scale)
            b = Placed(1, x0 + w0 * scale + gap, y0 + (bh - h1 * scale) / 2f, w1 * scale, h1 * scale)
        }
        return SheetPlan(pw, ph, listOf(a, b), s.cutLine)
    }

    /** One document page: fit inside the margins, centered. Wide pages get a landscape sheet. */
    fun document(imgW: Int, imgH: Int, s: ExportSettings): SheetPlan {
        val (pw, ph) = s.sheetSize(autoLandscape = imgW > imgH)
        val m = s.marginMm
        val availW = (pw - 2 * m).coerceAtLeast(10f)
        val availH = (ph - 2 * m).coerceAtLeast(10f)
        val scale = minOf(availW / imgW, availH / imgH)
        val w = imgW * scale
        val h = imgH * scale
        return SheetPlan(pw, ph, listOf(Placed(0, (pw - w) / 2f, (ph - h) / 2f, w, h)), false)
    }
}
