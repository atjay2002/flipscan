package app.flipscan.scan

import java.io.File

/**
 * Files stored next to each page image so the page can be re-cropped later:
 *  page_0.jpg        the cropped page (what gets exported)
 *  page_0.orig.jpg   the upright original photo, downscaled
 *  page_0.quad       the 4 crop corners (normalized to the original)
 */
object Sidecar {
    fun orig(page: File) = File(page.parentFile, page.nameWithoutExtension + ".orig.jpg")
    fun quad(page: File) = File(page.parentFile, page.nameWithoutExtension + ".quad")

    fun writeQuad(page: File, q: Quad) {
        quad(page).writeText(q.toList().joinToString(",") { "${it.x},${it.y}" })
    }

    fun readQuad(page: File): Quad? = try {
        val v = quad(page).readText().split(",").map { it.toDouble() }
        if (v.size == 8) Quad(Pt(v[0], v[1]), Pt(v[2], v[3]), Pt(v[4], v[5]), Pt(v[6], v[7])) else null
    } catch (e: Exception) {
        null
    }

    fun deleteAll(page: File) {
        page.delete(); orig(page).delete(); quad(page).delete()
    }
}
