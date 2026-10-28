package app.flipscan.data

import android.content.Context
import app.flipscan.scan.PageProcessor
import app.flipscan.scan.Quad
import app.flipscan.scan.Sidecar

/** Per-page editing of a saved scan: crop and rotate. Heavy: call off the main thread. */
object PageEditor {
    private fun page(ctx: Context, scan: Scan, index: Int) = ScanStore.pageFiles(ctx, scan).getOrNull(index)

    fun hasOriginal(ctx: Context, scan: Scan, index: Int): Boolean =
        page(ctx, scan, index)?.let { Sidecar.orig(it).exists() } ?: false

    fun loadQuad(ctx: Context, scan: Scan, index: Int): Quad? = page(ctx, scan, index)?.let { Sidecar.readQuad(it) }

    /** Re-crops page [index] from the stored original using [quad]. */
    fun applyCrop(ctx: Context, scan: Scan, index: Int, quad: Quad): Boolean {
        val page = page(ctx, scan, index) ?: return false
        val orig = Sidecar.orig(page)
        if (!orig.exists()) return false
        val ok = PageProcessor.recrop(orig, page, quad, scan.mode == Mode.CARDS)
        if (ok) Sidecar.writeQuad(page, quad)
        return ok
    }

    /** Rotates one page 90 degrees clockwise. */
    fun rotate(ctx: Context, scan: Scan, index: Int) {
        val page = page(ctx, scan, index) ?: return
        val q = Sidecar.readQuad(page)
        if (q != null && Sidecar.orig(page).exists()) {
            // Cycling the corners rotates the result and keeps full quality (re-warped from the original).
            applyCrop(ctx, scan, index, Quad(q.bl, q.tl, q.tr, q.br))
        } else {
            ScanStore.rotatePage(ctx, scan, index) // older scans without a stored original
        }
    }
}
