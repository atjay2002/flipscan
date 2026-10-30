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

/** A file this scan produced in Downloads/FlipScan, kept so "Open" can show the real export. */
data class SavedExport(
    val name: String,
    val uri: String,
    val mime: String,
    val at: Long
)

/**
 * A saved scan. Its pages live in app-private storage: filesDir/scans/<id>/.
 * [folderId] is null for the main collection; membership is metadata only, nothing moves on disk.
 */
data class Scan(
    val id: String,
    val name: String,
    val mode: Mode,
    val createdAt: Long,
    val pages: List<String>,
    val folderId: String? = null,
    val exports: List<SavedExport> = emptyList()
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

    /** Renames the scan itself (what the app shows). Returns the saved scan. */
    fun rename(ctx: Context, scan: Scan, newName: String): Scan? {
        val name = Naming.sanitize(newName, "")
        if (name.isEmpty()) return null
        val updated = scan.copy(name = name)
        writeMeta(ctx, updated)
        return updated
    }

    /** Puts the scan in [folderId], or back into the main collection when null. */
    fun setFolder(ctx: Context, scan: Scan, folderId: String?): Scan {
        val updated = scan.copy(folderId = folderId)
        writeMeta(ctx, updated)
        return updated
    }

    /** Empties a folder without touching the scans or their files. Returns how many scans moved out. */
    fun clearFolder(ctx: Context, folderId: String): Int {
        val members = list(ctx).filter { it.folderId == folderId }
        members.forEach { writeMeta(ctx, it.copy(folderId = null)) }
        return members.size
    }

    /** Remembers a file the scan wrote to Downloads. Newest first, at most 8 entries. */
    fun addExport(ctx: Context, scan: Scan, saved: SavedExport): Scan {
        val list = (listOf(saved) + scan.exports).distinctBy { it.uri }.take(8)
        val updated = scan.copy(exports = list)
        writeMeta(ctx, updated)
        return updated
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
        s.folderId?.let { json.put("folderId", it) }
        if (s.exports.isNotEmpty()) {
            json.put("exports", JSONArray().apply {
                s.exports.forEach {
                    put(
                        JSONObject()
                            .put("name", it.name)
                            .put("uri", it.uri)
                            .put("mime", it.mime)
                            .put("at", it.at)
                    )
                }
            })
        }
        // Write then rename, so a crash mid-write cannot leave a truncated meta.json.
        val target = metaFile(ctx, s.id)
        val tmp = File(target.parentFile, "meta.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(json.toString())
            tmp.delete()
        }
    }

    private fun readMeta(f: File): Scan? {
        if (!f.exists()) return null
        return try {
            val j = JSONObject(f.readText())
            val arr = j.getJSONArray("pages")
            Scan(
                id = j.getString("id"),
                name = j.getString("name"),
                mode = Mode.valueOf(j.getString("mode")),
                createdAt = j.getLong("createdAt"),
                pages = (0 until arr.length()).map { arr.getString(it) },
                folderId = j.optString("folderId", "").takeIf { it.isNotEmpty() },
                exports = parseExports(j.optJSONArray("exports"))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseExports(arr: JSONArray?): List<SavedExport> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SavedExport(
                name = o.optString("name"),
                uri = uri,
                mime = o.optString("mime", "application/pdf"),
                at = o.optLong("at", 0L)
            )
        }
    }
}
