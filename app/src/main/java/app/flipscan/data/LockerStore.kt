package app.flipscan.data

import android.content.Context
import android.graphics.Bitmap
import app.flipscan.scan.Quad
import app.flipscan.scan.Sidecar
import app.flipscan.util.decodeSampled
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** The kinds of sensitive document the locker offers. Cards use the front/back camera flow. */
enum class LockerDocType(
    val label: String,
    val hint: String,
    val cards: Boolean,
    /** Digital Pins can read a document number from these; a custom document has no known shape. */
    val pins: Boolean = true
) {
    AADHAAR("Aadhaar", "Identity card · front and back", true),
    PAN("PAN", "Tax card · front and back", true),
    PASSPORT("Passport", "Booklet · photo and address pages", false),
    DRIVING_LICENCE("Driving Licence", "Card · front and back", true),
    VOTER_ID("Voter ID", "Card · front and back", true),
    CUSTOM("Custom document", "Any document you want encrypted", false, pins = false);

    companion object {
        fun from(raw: String): LockerDocType = entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

/**
 * One protected document. Names, types, dates and the optional Digital Pin live in the encrypted
 * index; the pages live encrypted next to it. [numberChecked] records that on-device OCR has already
 * looked at this document, so opening it again does not re-run recognition. [exports] records
 * plaintext copies that have been written to Downloads/FlipScan (export breaks the locker's
 * encryption boundary — the exported file is no longer protected).
 */
data class LockerDoc(
    val id: String,
    val type: LockerDocType,
    val name: String,
    val createdAt: Long,
    val pages: Int,
    val number: String? = null,
    val numberPage: Int? = null,
    val numberVerified: Boolean = false,
    val numberChecked: Boolean = false,
    val exports: List<SavedExport> = emptyList()
)

/**
 * Plaintext working copies of one locker page, kept only for the duration of a crop or rotate.
 * Always hand this back to [LockerStore.commitEdit] or [LockerStore.discardEdit].
 */
class LockerEdit internal constructor(
    internal val dir: File,
    internal val index: Int,
    internal val cardMode: Boolean,
    val page: File
)

/**
 * Encrypted storage for the Digital Locker.
 *
 * Everything lives under `filesDir/locker`: one encrypted index (`index.bin`) and one directory per
 * document holding `p_i.jpg.enc`, `p_i.orig.jpg.enc`, `p_i.quad.enc` and `thumb.jpg.enc`. Filenames
 * reveal only a random UUID and the page count; names, types and dates are inside the index.
 *
 * Writes go to a `.tmp` name and are renamed into place, so a crash cannot leave a half-written
 * record that looks complete. Decrypted copies are only ever written under
 * `noBackupFilesDir/locker_tmp`, and callers remove them in a `finally`; [sweep] and [clearTemps]
 * clean up after a process death. Nothing here is reachable through FileProvider.
 */
object LockerStore {
    private const val INDEX = "index.bin"
    private const val INDEX_TMP = "index.bin.tmp"
    private const val THUMB_MAX = 720

    private val lock = Any()

    private fun root(ctx: Context) = File(ctx.filesDir, "locker").apply { mkdirs() }
    private fun docsDir(ctx: Context) = File(root(ctx), "docs").apply { mkdirs() }
    private fun docDir(ctx: Context, id: String) = File(docsDir(ctx), id)
    private fun tmpRoot(ctx: Context) = File(ctx.noBackupFilesDir, "locker_tmp")

    private fun indexFile(ctx: Context) = File(root(ctx), INDEX)
    private fun pageFile(ctx: Context, id: String, index: Int) = File(docDir(ctx, id), "p_$index.jpg.enc")
    private fun origFile(ctx: Context, id: String, index: Int) = File(docDir(ctx, id), "p_$index.orig.jpg.enc")
    private fun quadFile(ctx: Context, id: String, index: Int) = File(docDir(ctx, id), "p_$index.quad.enc")
    private fun thumbFile(ctx: Context, id: String) = File(docDir(ctx, id), "thumb.jpg.enc")

    /** Newest first. An unreadable index is reported through [readAll] instead of pretending it is empty. */
    fun list(ctx: Context): List<LockerDoc> = readAll(ctx).getOrDefault(emptyList())

    /** [Result.failure] means the index exists but could not be decrypted; callers must not overwrite it. */
    fun readAll(ctx: Context): Result<List<LockerDoc>> = runCatching { readIndexOrThrow(ctx) }

    fun get(ctx: Context, id: String): LockerDoc? = list(ctx).firstOrNull { it.id == id }

    fun defaultName(type: LockerDocType, at: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("dd MMM yyyy HH.mm", Locale.getDefault()).format(Date(at))
        return "${type.label} $stamp"
    }

    /**
     * Moves captured pages into the locker. The plaintext inputs (and their sidecars) are deleted
     * only after the encrypted copies and the index are safely on disk.
     */
    fun import(ctx: Context, type: LockerDocType, files: List<File>): LockerDoc {
        require(files.isNotEmpty()) { "No pages to save" }
        synchronized(lock) {
            val current = readIndexOrThrow(ctx) // refuses to overwrite an index we cannot read
            val id = UUID.randomUUID().toString()
            val staging = File(docsDir(ctx), "$id.tmp")
            val target = docDir(ctx, id)
            if (!staging.mkdirs()) throw IOException("Could not create locker storage")
            var indexed = false
            try {
                files.forEachIndexed { i, page ->
                    writeEncrypted(File(staging, "p_$i.jpg.enc"), page.readBytes())
                    val orig = Sidecar.orig(page)
                    if (orig.exists()) writeEncrypted(File(staging, "p_$i.orig.jpg.enc"), orig.readBytes())
                    val quad = Sidecar.quad(page)
                    if (quad.exists()) writeEncrypted(File(staging, "p_$i.quad.enc"), quad.readText().toByteArray(Charsets.UTF_8))
                }
                makeThumbBytes(files.first())?.let { writeEncrypted(File(staging, "thumb.jpg.enc"), it) }

                if (!staging.renameTo(target)) throw IOException("Could not finish locker storage")
                val now = System.currentTimeMillis()
                val doc = LockerDoc(id, type, defaultName(type, now), now, files.size)
                writeIndex(ctx, current + doc)
                indexed = true
                // The encrypted copy is durable. A failure to delete the scratch files must not undo
                // the import; whatever is left in the work folder is cleared on the next start.
                files.forEach { runCatching { Sidecar.deleteAll(it) } }
                return doc
            } catch (e: Exception) {
                staging.deleteRecursively()
                // After the index is written the document is valid; only pre-index failures roll back.
                if (!indexed) target.deleteRecursively()
                throw e
            }
        }
    }

    fun rename(ctx: Context, id: String, raw: String): LockerDoc? = synchronized(lock) {
        val name = Naming.sanitize(raw, "")
        if (name.isEmpty()) return null
        val docs = readIndexOrThrow(ctx)
        val at = docs.indexOfFirst { it.id == id }
        if (at < 0) return null
        val updated = docs[at].copy(name = name)
        val next = docs.toMutableList().also { it[at] = updated }
        writeIndex(ctx, next)
        return updated
    }

    /**
     * Stores (or clears, when [value] is null) the Digital Pin for one document. Reads the index
     * again so a concurrent rename or OCR result is not lost. [checked] marks that recognition has
     * already run, which stops the viewer from trying again on every open.
     */
    fun setNumber(
        ctx: Context,
        id: String,
        value: String?,
        page: Int? = null,
        verified: Boolean = false,
        checked: Boolean = true
    ): LockerDoc? = synchronized(lock) {
        val docs = readIndexOrThrow(ctx)
        val at = docs.indexOfFirst { it.id == id }
        if (at < 0) return null
        val current = docs[at]
        val clean = value?.let { PinFormat.normalize(current.type, it).ifEmpty { null } }
        val updated = current.copy(
            number = clean,
            numberPage = if (clean == null) null else page,
            numberVerified = clean != null && verified,
            numberChecked = checked
        )
        writeIndex(ctx, docs.toMutableList().also { it[at] = updated })
        updated
    }

    /** Records that recognition ran for this document without changing what it found. */
    fun markNumberChecked(ctx: Context, id: String): LockerDoc? = synchronized(lock) {
        val docs = readIndexOrThrow(ctx)
        val at = docs.indexOfFirst { it.id == id }
        if (at < 0) return null
        val updated = docs[at].copy(numberChecked = true)
        writeIndex(ctx, docs.toMutableList().also { it[at] = updated })
        updated
    }

    /** Records that a plaintext export of this document now exists in Downloads/FlipScan. */
    fun addExport(ctx: Context, id: String, saved: SavedExport): LockerDoc? = synchronized(lock) {
        val docs = readIndexOrThrow(ctx)
        val at = docs.indexOfFirst { it.id == id }
        if (at < 0) return null
        val updated = docs[at].copy(
            exports = (listOf(saved) + docs[at].exports).distinctBy { it.uri }.take(8)
        )
        writeIndex(ctx, docs.toMutableList().also { it[at] = updated })
        return updated
    }

    fun delete(ctx: Context, id: String): Boolean = synchronized(lock) {
        val docs = readIndexOrThrow(ctx)
        if (docs.none { it.id == id }) return false
        // Index first: a crash then leaves only an orphan directory, which [sweep] removes.
        writeIndex(ctx, docs.filterNot { it.id == id })
        docDir(ctx, id).deleteRecursively()
        return true
    }

    /** Decrypted JPEG bytes for the viewer. Null when the page is missing or cannot be opened. */
    fun pageBytes(ctx: Context, id: String, index: Int): ByteArray? = readEncrypted(pageFile(ctx, id, index))

    /** Decrypted thumbnail bytes, decoded by the UI. Never written back to disk. */
    fun thumbBytes(ctx: Context, id: String): ByteArray? = readEncrypted(thumbFile(ctx, id))

    fun hasOriginal(ctx: Context, id: String, index: Int): Boolean = origFile(ctx, id, index).exists()

    /**
     * Decrypts one page (plus its editable original and crop corners) into a private temp folder.
     * The caller must finish with [commitEdit] or [discardEdit].
     */
    fun stageForEdit(ctx: Context, id: String, index: Int): LockerEdit? {
        val doc = get(ctx, id) ?: return null
        if (index !in 0 until doc.pages) return null
        val dir = File(tmpRoot(ctx), "edit-${UUID.randomUUID()}")
        if (!dir.mkdirs()) return null
        return try {
            val page = File(dir, "page_$index.jpg")
            val pageBytes = readEncrypted(pageFile(ctx, id, index)) ?: throw IOException("Page missing")
            page.writeBytes(pageBytes)
            readEncrypted(origFile(ctx, id, index))?.let { Sidecar.orig(page).writeBytes(it) }
            readEncrypted(quadFile(ctx, id, index))?.let { Sidecar.quad(page).writeBytes(it) }
            LockerEdit(dir, index, doc.type.cards, page)
        } catch (e: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    /**
     * Re-encrypts the edited page and its corners, refreshes the list thumbnail for page 0 and
     * always removes the plaintext temp folder. Returns false when the write did not complete.
     */
    fun commitEdit(ctx: Context, id: String, edit: LockerEdit): Boolean = synchronized(lock) {
        try {
            val dir = docDir(ctx, id)
            if (!dir.exists()) return@synchronized false
            writeEncrypted(File(dir, "p_${edit.index}.jpg.enc"), edit.page.readBytes())
            val quad = Sidecar.readQuad(edit.page)
            val qTarget = File(dir, "p_${edit.index}.quad.enc")
            if (quad != null) {
                writeEncrypted(qTarget, serializeQuad(quad).toByteArray(Charsets.UTF_8))
            } else {
                qTarget.delete()
            }
            if (edit.index == 0) {
                makeThumbBytes(edit.page)?.let { writeEncrypted(File(dir, "thumb.jpg.enc"), it) }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            edit.dir.deleteRecursively()
        }
    }

    fun discardEdit(edit: LockerEdit) {
        edit.dir.deleteRecursively()
    }

    /** Rotates one page 90° clockwise from its stored original when possible. */
    fun rotate(ctx: Context, id: String, index: Int): Boolean = synchronized(lock) {
        val edit = stageForEdit(ctx, id, index) ?: return@synchronized false
        val keep = try {
            PageEditor.rotate(edit.page, edit.cardMode)
        } catch (e: Exception) {
            false
        }
        if (!keep) {
            edit.dir.deleteRecursively()
            return@synchronized false
        }
        commitEdit(ctx, id, edit)
    }

    /** Deletes decrypted temp folders. Safe to call whenever no editor is open. */
    fun clearTemps(ctx: Context) {
        tmpRoot(ctx).deleteRecursively()
    }

    /**
     * Removes stale `.tmp` directories, orphaned document folders and a half-written index.
     * When the index itself is unreadable nothing is deleted, so a key problem never destroys data.
     */
    fun sweep(ctx: Context) = synchronized(lock) {
        File(root(ctx), INDEX_TMP).delete()
        val index = indexFile(ctx)
        val docs = docsDir(ctx)
        if (!index.exists()) {
            // Interrupted first save: no index can point at these folders.
            docs.listFiles()?.forEach { it.deleteRecursively() }
            return@synchronized
        }
        val known = readAll(ctx).getOrElse { return@synchronized }.map { it.id }.toHashSet()
        docs.listFiles()?.forEach { dir ->
            if (dir.name.endsWith(".tmp") || dir.name !in known) dir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun readEncrypted(file: File): ByteArray? {
        if (!file.exists()) return null
        return try {
            LockerCrypto.decrypt(file.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    private fun writeEncrypted(target: File, plain: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(LockerCrypto.encrypt(plain))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not store locker file")
            }
        }
    }

    private fun makeThumbBytes(page: File): ByteArray? {
        val bmp = decodeSampled(page, THUMB_MAX) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun serializeQuad(q: Quad): String =
        q.toList().joinToString(",") { "${it.x},${it.y}" }

    private fun writeIndex(ctx: Context, docs: List<LockerDoc>) {
        val arr = JSONArray()
        docs.forEach { d ->
            val o = JSONObject()
                .put("id", d.id)
                .put("type", d.type.name)
                .put("name", d.name)
                .put("createdAt", d.createdAt)
                .put("pages", d.pages)
            // Absent keys mean "no pin / not checked", so an older index still reads correctly.
            d.number?.let { o.put("number", it) }
            d.numberPage?.let { o.put("numberPage", it) }
            if (d.numberVerified) o.put("numberVerified", true)
            if (d.numberChecked) o.put("numberChecked", true)
            if (d.exports.isNotEmpty()) {
                o.put("exports", JSONArray(d.exports.map {
                    JSONObject().put("name", it.name).put("uri", it.uri).put("mime", it.mime)
                }))
            }
            arr.put(o)
        }
        writeEncrypted(indexFile(ctx), arr.toString().toByteArray(Charsets.UTF_8))
    }

    private fun readIndexOrThrow(ctx: Context): List<LockerDoc> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        val plain = LockerCrypto.decrypt(f.readBytes())
        val arr = JSONArray(String(plain, Charsets.UTF_8))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val exports = if (o.has("exports")) {
                val e = o.getJSONArray("exports")
                (0 until e.length()).map { j ->
                    val eo = e.getJSONObject(j)
                    SavedExport(
                        name = eo.getString("name"),
                        uri = eo.getString("uri"),
                        mime = eo.getString("mime"),
                        at = eo.optLong("at", 0L)
                    )
                }
            } else emptyList()
            LockerDoc(
                id = o.getString("id"),
                type = LockerDocType.from(o.optString("type")),
                name = o.getString("name"),
                createdAt = o.getLong("createdAt"),
                pages = o.getInt("pages"),
                number = o.optString("number", "").ifEmpty { null },
                numberPage = if (o.has("numberPage")) o.optInt("numberPage") else null,
                numberVerified = o.optBoolean("numberVerified", false),
                numberChecked = o.optBoolean("numberChecked", false),
                exports = exports
            )
        }.sortedByDescending { it.createdAt }
    }
}
