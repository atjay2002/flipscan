package app.flipscan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import app.flipscan.scan.PageProcessor
import app.flipscan.scan.Quad
import app.flipscan.scan.Sidecar
import java.io.File

/**
 * Per-page editing: crop and rotate.
 *
 * Works on page files rather than a [Scan], so the same editor serves normal scans and the
 * encrypted locker (which decrypts a page to a temp file, edits it, then re-encrypts it).
 * Heavy: call off the main thread.
 */
object PageEditor {
    fun hasOriginal(page: File): Boolean = Sidecar.orig(page).exists()

    fun loadQuad(page: File): Quad? = Sidecar.readQuad(page)

    /** Re-crops [page] from the stored original using [quad]. */
    fun applyCrop(page: File, quad: Quad, cardMode: Boolean): Boolean {
        val orig = Sidecar.orig(page)
        if (!orig.exists()) return false
        val ok = PageProcessor.recrop(orig, page, quad, cardMode)
        if (ok) Sidecar.writeQuad(page, quad)
        return ok
    }

    /** Rotates one page 90° clockwise. */
    fun rotate(page: File, cardMode: Boolean): Boolean {
        val q = Sidecar.readQuad(page)
        return if (q != null && Sidecar.orig(page).exists()) {
            // Cycling the corners rotates the result and keeps full quality (re-warped from the original).
            applyCrop(page, Quad(q.bl, q.tl, q.tr, q.br), cardMode)
        } else {
            rotateInPlace(page) // older scans without a stored original
        }
    }

    /** Lossy fallback for pages saved before originals were kept. */
    private fun rotateInPlace(page: File): Boolean {
        val src = BitmapFactory.decodeFile(page.path) ?: return false
        var result: Bitmap? = null
        return try {
            val rotated = Bitmap.createBitmap(
                src, 0, 0, src.width, src.height,
                Matrix().apply { postRotate(90f) }, true
            )
            result = rotated
            if (rotated !== src) src.recycle()
            page.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (e: Exception) {
            false
        } finally {
            if (!src.isRecycled) src.recycle()
            result?.takeIf { it !== src && !it.isRecycled }?.recycle()
        }
    }
}
