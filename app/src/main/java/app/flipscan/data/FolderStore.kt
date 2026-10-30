package app.flipscan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A user-created folder. Scans point at it by id; nothing is moved on disk. */
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long
)

/**
 * Folder definitions live in one small JSON file next to the scans folder
 * (`filesDir/folders.json`). Membership is stored on each scan (`meta.json`,
 * `folderId`), so deleting a scan can never leave a stale membership behind and
 * no page image or sidecar ever has to move.
 *
 * Folders are an extra grouping on top of the chronological list: Home's "Recent scans"
 * always shows every scan (with its folder name), and a folder screen filters by id.
 */
object FolderStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "folders.json")

    fun list(ctx: Context): List<Folder> = read(ctx).sortedBy { it.createdAt }

    fun get(ctx: Context, id: String): Folder? = read(ctx).firstOrNull { it.id == id }

    /** Creates a folder with a cleaned, unique name. Returns null when nothing usable was typed. */
    fun create(ctx: Context, rawName: String): Folder? {
        val name = Naming.sanitize(rawName, "")
        if (name.isEmpty()) return null
        val folders = read(ctx)
        val folder = Folder(UUID.randomUUID().toString(), freeName(folders, name, null), System.currentTimeMillis())
        write(ctx, folders + folder)
        return folder
    }

    /** Renames an existing folder. Returns the updated folder, or null for bad input. */
    fun rename(ctx: Context, id: String, rawName: String): Folder? {
        val name = Naming.sanitize(rawName, "")
        if (name.isEmpty()) return null
        val folders = read(ctx)
        val i = folders.indexOfFirst { it.id == id }
        if (i < 0) return null
        val updated = folders[i].copy(name = freeName(folders, name, id))
        write(ctx, folders.toMutableList().also { it[i] = updated })
        return updated
    }

    /** Removes the folder definition only; its scans stay and fall back to the main collection. */
    fun delete(ctx: Context, id: String) {
        write(ctx, read(ctx).filterNot { it.id == id })
    }

    private fun freeName(folders: List<Folder>, name: String, exceptId: String?): String {
        val taken = folders.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var i = 2
        while ("${name.lowercase()} ($i)" in taken && i < 1000) i++
        return "$name ($i)"
    }

    private fun read(ctx: Context): List<Folder> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("folders")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Folder(id, name, o.optLong("createdAt", 0L))
            }
        } catch (e: Exception) {
            emptyList() // a damaged file must not hide the rest of the app
        }
    }

    private fun write(ctx: Context, folders: List<Folder>) {
        val json = JSONObject().put("folders", JSONArray().apply {
            folders.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt))
            }
        })
        val target = file(ctx)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(json.toString())
            tmp.delete()
        }
    }
}
