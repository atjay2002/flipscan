package app.flipscan.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import app.flipscan.data.ExportSettings
import app.flipscan.data.Filter
import app.flipscan.data.Format
import app.flipscan.data.Mode
import app.flipscan.data.Naming
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.SettingsStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

private typealias Sheet = Pair<SheetPlan, List<File>>

/** Builds sheets from a saved scan. Everything here is heavy: call from a background dispatcher. */
object Exporter {
    class Result(val files: List<File>, val note: String?)

    /** Cards: one sheet with both sides. Documents: one sheet per page. */
    private fun sheets(ctx: Context, scan: Scan, s: ExportSettings): List<Sheet> {
        val files = ScanStore.pageFiles(ctx, scan)
        if (scan.mode == Mode.CARDS && files.size >= 2) return listOf(Layouts.cards(s, files.take(2).map { PageLoader.size(it) }) to files.take(2))
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

    /**
     * Draws the document straight into [target] and returns its size in bytes.
     *
     * The pages cannot be flushed one at a time: [PdfDocument] serialises everything in
     * [PdfDocument.writeTo], and until then it keeps reading the page bitmaps — which is why
     * [SheetRenderer.draw] is called with recycle = false. Writing to a file instead of a
     * ByteArrayOutputStream still helps, because it avoids holding a second copy of the whole
     * PDF (the stream buffer plus the array copy) while that serialisation runs.
     */
    private fun writePdf(sheets: List<Sheet>, filter: Filter, dpi: Int, target: File): Long {
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
            FileOutputStream(target).use { out -> doc.writeTo(out) }
            return target.length()
        } finally {
            doc.close()
        }
    }

    fun exportDir(ctx: Context): File =
        File(ctx.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }

    /** Sanitises a user-visible base name; the extension is added by [render]. */
    fun safeName(name: String): String = Naming.baseName(name, "scan")

    /** Small JPEG of the first sheet, shown in the recent scans list. */
    fun writeThumb(ctx: Context, scan: Scan) {
        val s = SettingsStore.load(ctx)
        val (plan, files) = sheets(ctx, scan, s).firstOrNull() ?: return
        val dpi = (360f / (plan.widthMm / 25.4f)).roundToInt().coerceAtLeast(10)
        val bmp = renderBitmap(plan, files, dpi, s.filter)
        try {
            ScanStore.thumbFile(ctx, scan.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        } finally {
            bmp.recycle()
        }
    }

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

    /** How many times the PDF may be rebuilt while hunting for the size target. */
    private const val MAX_PDF_ATTEMPTS = 4

    /** Below this dpi there is nothing left to gain; the smallest result is returned instead. */
    private const val MIN_PDF_DPI = 50

    /**
     * PdfDocument stores pictures losslessly, so the PDF size is steered with the resolution.
     * The first attempt uses the normal dpi; if the file is still too big, the next dpi is
     * estimated from the square root of the overshoot (image bytes grow with dpi squared).
     * Every attempt is written straight to a scratch file, so no attempt is ever held in
     * memory and the previous ladder of whole-document byte arrays is gone. Only the smallest
     * attempt is kept and moved into place. Returns the size and whether the target was reached.
     */
    private fun pdfToTarget(
        sheets: List<Sheet>,
        filter: Filter,
        dpi: Int,
        target: File,
        targetBytes: Int
    ): Pair<Long, Boolean> {
        val scratch = File(target.parentFile, "${target.name}.tmp")
        val best = File(target.parentFile, "${target.name}.best")
        var bestSize = -1L
        var current = dpi
        var attempts = 0
        try {
            while (true) {
                attempts++
                val size = writePdf(sheets, filter, current, scratch)
                if (bestSize < 0 || size < bestSize) {
                    replace(scratch, best)
                    bestSize = size
                } else {
                    scratch.delete()
                }
                if (targetBytes <= 0 || size <= targetBytes) break
                if (attempts >= MAX_PDF_ATTEMPTS) break
                val scaled = floor(current * sqrt(targetBytes.toDouble() / size)).toInt()
                // Always step down, so a rounding artefact cannot stall the loop.
                val next = scaled.coerceAtMost(current - 10)
                if (next < MIN_PDF_DPI) break
                current = next
            }
            if (bestSize < 0) throw IOException("Could not write the PDF")
            replace(best, target)
            return bestSize to (targetBytes <= 0 || bestSize <= targetBytes)
        } finally {
            scratch.delete()
            best.delete()
        }
    }

    /** Renames [from] onto [to], replacing it: renameTo can fail when the target already exists. */
    private fun replace(from: File, to: File) {
        to.delete()
        if (!from.renameTo(to)) throw IOException("Could not finish the export")
    }

    /**
     * Writes the export into the cache folder and returns the files (ready to share or copy to Downloads).
     * [baseName] lets the user name this export; when null the scan name is used.
     */
    fun render(ctx: Context, scan: Scan, s: ExportSettings, baseName: String? = null): Result {
        val sheets = sheets(ctx, scan, s)
        val dir = exportDir(ctx)
        val base = safeName(baseName ?: scan.name)
        val dpi = baseDpi(scan)
        val target = s.targetKb * 1024
        val files = mutableListOf<File>()
        val notes = mutableListOf<String>()
        fun limitNote(size: Long) {
            notes += "Could not reach ${s.targetKb} KB; smallest result is ${size / 1024} KB"
        }

        if (s.format == Format.PDF) {
            val file = File(dir, "$base.pdf")
            val (size, reached) = pdfToTarget(sheets, s.filter, dpi, file, target)
            files += file
            if (!reached) limitNote(size)
        } else {
            sheets.forEachIndexed { i, (plan, pageFiles) ->
                // One sheet at a time: only one big bitmap is alive.
                val bmp = renderBitmap(plan, pageFiles, dpi, s.filter)
                try {
                    val (bytes, reached) = encode(bmp, s.format, target)
                    val name = if (sheets.size == 1) base else "${base}_${i + 1}"
                    files += File(dir, "$name.${s.format.ext}").also { it.writeBytes(bytes) }
                    if (!reached) limitNote(bytes.size.toLong())
                } finally {
                    bmp.recycle()
                }
            }
        }
        return Result(files, notes.firstOrNull())
    }

    /**
     * Same as [render], but works on a plain list of page files instead of a [Scan].
     * Used for locker document export: pages are decrypted to temp files first, then rendered,
     * then the temp files are deleted by the caller. [cardMode] lays out two pages side-by-side
     * like an ID card; otherwise one sheet per page.
     */
    fun renderPages(
        ctx: Context,
        pages: List<File>,
        name: String,
        s: ExportSettings,
        baseName: String? = null,
        cardMode: Boolean = false
    ): Result {
        val sheets: List<Sheet> = if (cardMode && pages.size >= 2) {
            val sizes = pages.take(2).map { PageLoader.size(it) }
            listOf(Layouts.cards(s, sizes) to pages.take(2))
        } else {
            pages.map { f ->
                val (w, h) = PageLoader.size(f)
                Layouts.document(w.coerceAtLeast(1), h.coerceAtLeast(1), s) to listOf(f)
            }
        }
        val dir = exportDir(ctx)
        val base = safeName(baseName ?: name)
        val dpi = if (cardMode) 300 else 200
        val target = s.targetKb * 1024
        val files = mutableListOf<File>()
        val notes = mutableListOf<String>()
        fun limitNote(size: Long) {
            notes += "Could not reach ${s.targetKb} KB; smallest result is ${size / 1024} KB"
        }

        if (s.format == Format.PDF) {
            val file = File(dir, "$base.pdf")
            val (size, reached) = pdfToTarget(sheets, s.filter, dpi, file, target)
            files += file
            if (!reached) limitNote(size)
        } else {
            sheets.forEachIndexed { i, (plan, pageFiles) ->
                val bmp = renderBitmap(plan, pageFiles, dpi, s.filter)
                try {
                    val (bytes, reached) = encode(bmp, s.format, target)
                    val name = if (sheets.size == 1) base else "${base}_${i + 1}"
                    files += File(dir, "$name.${s.format.ext}").also { it.writeBytes(bytes) }
                    if (!reached) limitNote(bytes.size.toLong())
                } finally {
                    bmp.recycle()
                }
            }
        }
        return Result(files, notes.firstOrNull())
    }
}
