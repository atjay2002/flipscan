package app.flipscan.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import app.flipscan.data.ExportSettings
import app.flipscan.data.Filter
import app.flipscan.data.Mode
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

private typealias Sheet = Pair<SheetPlan, List<File>>

/** Builds sheets from a saved scan. Everything here is heavy: call from a background dispatcher. */
object Exporter {
    class Result(val files: List<File>, val note: String?)

    /** Cards: one sheet with both sides. Documents: one sheet per page. */
    private fun sheets(ctx: Context, scan: Scan, s: ExportSettings): List<Sheet> {
        val files = ScanStore.pageFiles(ctx, scan)
        if (scan.mode == Mode.CARDS && files.size >= 2) return listOf(Layouts.cards(s) to files.take(2))
        return files.map { f ->
            val (w, h) = PageLoader.size(f)
            Layouts.document(w.coerceAtLeast(1), h.coerceAtLeast(1), s) to listOf(f)
        }
    }

    private fun baseDpi(scan: Scan) = if (scan.mode == Mode.CARDS) 300 else 200

    /** Small on-screen previews (filters are applied here too, but on tiny images). */
    fun previews(ctx: Context, scan: Scan, s: ExportSettings): List<Bitmap> =
        sheets(ctx, scan, s).map { (plan, files) ->
            val dpi = (560f / (plan.widthMm / 25.4f)).roundToInt().coerceAtLeast(10)
            renderBitmap(plan, files, dpi, s.filter)
        }

    private fun renderBitmap(plan: SheetPlan, files: List<File>, dpi: Int, filter: Filter): Bitmap {
        val ppm = dpi / 25.4f
        val bmp = Bitmap.createBitmap(
            (plan.widthMm * ppm).roundToInt().coerceAtLeast(1),
            (plan.heightMm * ppm).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.scale(ppm, ppm)
        SheetRenderer.draw(canvas, plan, files, dpi, filter, recycle = true)
        return bmp
    }

    private fun pdfBytes(sheets: List<Sheet>, filter: Filter, dpi: Int): ByteArray {
        val doc = PdfDocument()
        try {
            sheets.forEachIndexed { i, (plan, files) ->
                val wPt = (plan.widthMm / 25.4f * 72f).roundToInt()
                val hPt = (plan.heightMm / 25.4f * 72f).roundToInt()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create())
                page.canvas.scale(wPt / plan.widthMm, hPt / plan.heightMm) // draw in millimetres
                SheetRenderer.draw(page.canvas, plan, files, dpi, filter, recycle = false)
                doc.finishPage(page)
            }
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    fun exportDir(ctx: Context): File =
        File(ctx.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }

    fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim().ifEmpty { "scan" }

    /** Writes the export into the cache folder and returns the files (ready to share or copy to Downloads). */
    fun render(ctx: Context, scan: Scan, s: ExportSettings): Result {
        val sheets = sheets(ctx, scan, s)
        val out = File(exportDir(ctx), safeName(scan.name) + ".pdf")
        out.writeBytes(pdfBytes(sheets, s.filter, baseDpi(scan)))
        return Result(listOf(out), null)
    }
}
