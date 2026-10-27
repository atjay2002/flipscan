package app.flipscan.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import app.flipscan.data.ExportSettings
import app.flipscan.data.Filter
import app.flipscan.data.Format
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

    private fun compress(bmp: Bitmap, format: Format, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val cf = if (format == Format.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bmp.compress(cf, quality, out)
        return out.toByteArray()
    }

    /**
     * JPG/PNG with an optional size limit. JPG lowers quality first, then shrinks the picture;
     * PNG is lossless so it can only shrink the picture. Returns (bytes, limit reached).
     */
    private fun encode(bmp: Bitmap, format: Format, targetBytes: Int): Pair<ByteArray, Boolean> {
        if (targetBytes <= 0) return compress(bmp, format, 92) to true
        val qualities = if (format == Format.JPG) intArrayOf(90, 75, 60, 45, 30) else intArrayOf(100)
        var scale = 1f
        var best = ByteArray(0)
        var scaled: Bitmap? = null
        try {
            for (step in 0..10) {
                val current = scaled ?: bmp
                for (q in qualities) {
                    best = compress(current, format, q)
                    if (best.size <= targetBytes) return best to true
                }
                scale *= 0.8f
                if (scale < 0.15f) break
                scaled?.recycle()
                scaled = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1),
                    (bmp.height * scale).roundToInt().coerceAtLeast(1), true
                )
            }
            return best to false
        } finally {
            scaled?.recycle()
        }
    }

    /**
     * PdfDocument stores pictures losslessly, so the PDF size is steered with the resolution:
     * try the normal dpi first, then step down until the file fits.
     */
    private fun pdfToTarget(sheets: List<Sheet>, filter: Filter, dpi: Int, targetBytes: Int): Pair<ByteArray, Boolean> {
        if (targetBytes <= 0) return pdfBytes(sheets, filter, dpi) to true
        var last = ByteArray(0)
        for (d in listOf(dpi) + listOf(220, 160, 120, 90, 70, 50).filter { it < dpi }) {
            last = pdfBytes(sheets, filter, d)
            if (last.size <= targetBytes) return last to true
        }
        return last to false
    }

    /** Writes the export into the cache folder and returns the files (ready to share or copy to Downloads). */
    fun render(ctx: Context, scan: Scan, s: ExportSettings): Result {
        val sheets = sheets(ctx, scan, s)
        val dir = exportDir(ctx)
        val base = safeName(scan.name)
        val dpi = baseDpi(scan)
        val target = s.targetKb * 1024
        val files = mutableListOf<File>()
        val notes = mutableListOf<String>()
        fun limitNote(bytes: ByteArray) {
            notes += "Could not reach ${s.targetKb} KB; smallest result is ${bytes.size / 1024} KB"
        }

        if (s.format == Format.PDF) {
            val (bytes, reached) = pdfToTarget(sheets, s.filter, dpi, target)
            files += File(dir, "$base.pdf").also { it.writeBytes(bytes) }
            if (!reached) limitNote(bytes)
        } else {
            sheets.forEachIndexed { i, (plan, pageFiles) ->
                // One sheet at a time: only one big bitmap is alive.
                val bmp = renderBitmap(plan, pageFiles, dpi, s.filter)
                try {
                    val (bytes, reached) = encode(bmp, s.format, target)
                    val name = if (sheets.size == 1) base else "${base}_${i + 1}"
                    files += File(dir, "$name.${s.format.ext}").also { it.writeBytes(bytes) }
                    if (!reached) limitNote(bytes)
                } finally {
                    bmp.recycle()
                }
            }
        }
        return Result(files, notes.firstOrNull())
    }
}
