package app.flipscan.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import app.flipscan.data.Filter
import java.io.File
import kotlin.math.roundToInt

/** Loads a stored page at the size it will actually be drawn (never the whole file if not needed). */
object PageLoader {
    fun size(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    fun load(file: File, targetW: Int, filter: Filter): Bitmap? {
        val (w, _) = size(file)
        if (w <= 0) return null
        var sample = 1
        while (w / (sample * 2) >= targetW) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        var bmp = decoded
        if (decoded.width > targetW) {
            val h = (decoded.height * targetW.toFloat() / decoded.width).roundToInt().coerceAtLeast(1)
            bmp = Bitmap.createScaledBitmap(decoded, targetW.coerceAtLeast(1), h, true)
            if (bmp !== decoded) decoded.recycle()
        }
        return Filters.apply(bmp, filter)
    }
}

object SheetRenderer {
    /**
     * Draws [plan] on a canvas whose unit is already millimetres (caller does canvas.scale()).
     * [dpi] only decides how sharp the decoded images are.
     * [recycle] must be false for PdfDocument canvases: they may still read the pixels later.
     */
    fun draw(canvas: Canvas, plan: SheetPlan, pages: List<File>, dpi: Int, filter: Filter, recycle: Boolean) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val pxPerMm = dpi / 25.4f
        for (p in plan.items) {
            val bmp = PageLoader.load(pages[p.page], (p.w * pxPerMm).roundToInt().coerceAtLeast(8), filter) ?: continue
            canvas.drawBitmap(bmp, null, RectF(p.x, p.y, p.x + p.w, p.y + p.h), paint)
            if (recycle) bmp.recycle()
        }
        if (plan.cutLine) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.DKGRAY
                strokeWidth = 0.3f
                pathEffect = DashPathEffect(floatArrayOf(2f, 1.5f), 0f)
            }
            for (p in plan.items) canvas.drawRect(p.x - 2f, p.y - 2f, p.x + p.w + 2f, p.y + p.h + 2f, line)
        }
    }
}
