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

    /** Both card sides on one sheet. */
    fun cards(s: ExportSettings): SheetPlan {
        val pw = s.paperW
        val ph = s.paperH
        val m = s.marginMm
        val availW = (pw - 2 * m).coerceAtLeast(10f)
        val availH = (ph - 2 * m).coerceAtLeast(10f)
        val stacked = s.layout == CardLayout.STACKED

        val blockW = if (stacked) CARD_W_MM else 2 * CARD_W_MM + GAP_MM
        val blockH = if (stacked) 2 * CARD_H_MM + GAP_MM else CARD_H_MM
        val fit = minOf(availW / blockW, availH / blockH)
        // Real size = 1:1 (85.6 x 54 mm), but never bigger than fits on the page.
        val scale = if (s.realSize) minOf(1f, fit) else fit

        val cw = CARD_W_MM * scale
        val ch = CARD_H_MM * scale
        val gap = GAP_MM * scale
        val x0 = (pw - (if (stacked) cw else 2 * cw + gap)) / 2f
        val y0 = m
        val items = if (stacked) {
            listOf(Placed(0, x0, y0, cw, ch), Placed(1, x0, y0 + ch + gap, cw, ch))
        } else {
            listOf(Placed(0, x0, y0, cw, ch), Placed(1, x0 + cw + gap, y0, cw, ch))
        }
        return SheetPlan(pw, ph, items, s.cutLine)
    }

    /** One document page: fit inside the margins, centered. Wide pages get a landscape sheet. */
    fun document(imgW: Int, imgH: Int, s: ExportSettings): SheetPlan {
        var pw = s.paperW
        var ph = s.paperH
        if (imgW > imgH && pw < ph) { val t = pw; pw = ph; ph = t }
        val m = s.marginMm
        val availW = (pw - 2 * m).coerceAtLeast(10f)
        val availH = (ph - 2 * m).coerceAtLeast(10f)
        val scale = minOf(availW / imgW, availH / imgH)
        val w = imgW * scale
        val h = imgH * scale
        return SheetPlan(pw, ph, listOf(Placed(0, (pw - w) / 2f, (ph - h) / 2f, w, h)), false)
    }
}
