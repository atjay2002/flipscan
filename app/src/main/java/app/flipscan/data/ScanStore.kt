package app.flipscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import app.flipscan.scan.Sidecar
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Mode { CARDS, DOCUMENT }

/** A saved scan. Its pages live in app-private storage: filesDir/scans/<id>/. */
data class Scan(
    val id: String,
    val name: String,
    val mode: Mode,
    val createdAt: Long,
    val pages: List<String>
)

object ScanStore {
    private fun root(ctx: Context) = File(ctx.filesDir, "scans").apply { mkdirs() }
    private fun dir(ctx: Context, id: String) = File(root(ctx), id)
    private fun metaFile(ctx: Context, id: String) = File(dir(ctx, id), "meta.json")

    fun pageFiles(ctx: Context, scan: Scan): List<File> = scan.pages.map { File(dir(ctx, scan.id), it) }
    fun thumbFile(ctx: Context, id: String) = File(dir(ctx, id), "thumb.jpg")

    /** Moves the working page files into a new scan folder. */
    fun create(ctx: Context, mode: Mode, files: List<File>): Scan {
        val id = UUID.randomUUID().toString()
        val d = dir(ctx, id).apply { mkdirs() }
        val names = files.mapIndexed { i, f ->
            val target = File(d, "page_$i.jpg")
            moveFile(f, target)
            // Keep the original photo and crop corners so the page can be edited later.
            if (Sidecar.orig(f).exists()) moveFile(Sidecar.orig(f), Sidecar.orig(target))
            if (Sidecar.quad(f).exists()) moveFile(Sidecar.quad(f), Sidecar.quad(target))
            target.name
        }
        val now = System.currentTimeMillis()
        val scan = Scan(id, defaultName(now, mode), mode, now, names)
        writeMeta(ctx, scan)
        return scan
    }

    private fun moveFile(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    fun get(ctx: Context, id: String): Scan? = readMeta(metaFile(ctx, id))

    /** Newest first. */
    fun list(ctx: Context): List<Scan> =
        (root(ctx).listFiles() ?: emptyArray())
            .mapNotNull { readMeta(File(it, "meta.json")) }
            .sortedByDescending { it.createdAt }

    fun rename(ctx: Context, scan: Scan, newName: String) {
        val name = newName.trim()
        if (name.isNotEmpty()) writeMeta(ctx, scan.copy(name = name))
    }

    /** Rotates one stored page 90 degrees clockwise (re-encodes the JPEG in place). */
    fun rotatePage(ctx: Context, scan: Scan, index: Int) {
        val f = pageFiles(ctx, scan).getOrNull(index) ?: return
        val src = BitmapFactory.decodeFile(f.path) ?: return
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
        if (rotated !== src) src.recycle()
        f.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        rotated.recycle()
    }

    fun delete(ctx: Context, scan: Scan) {
        dir(ctx, scan.id).deleteRecursively()
    }

    private fun defaultName(ts: Long, mode: Mode): String {
        val stamp = SimpleDateFormat("dd MMM yyyy HH.mm", Locale.getDefault()).format(Date(ts))
        return (if (mode == Mode.CARDS) "Card " else "Scan ") + stamp
    }

    private fun writeMeta(ctx: Context, s: Scan) {
        val json = JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("mode", s.mode.name)
            .put("createdAt", s.createdAt)
            .put("pages", JSONArray(s.pages))
        metaFile(ctx, s.id).writeText(json.toString())
    }

    private fun readMeta(f: File): Scan? {
        if (!f.exists()) return null
        return try {
            val j = JSONObject(f.readText())
            val arr = j.getJSONArray("pages")
            Scan(
                j.getString("id"), j.getString("name"), Mode.valueOf(j.getString("mode")),
                j.getLong("createdAt"), (0 until arr.length()).map { arr.getString(it) }
            )
        } catch (e: Exception) {
            null
        }
    }
}
