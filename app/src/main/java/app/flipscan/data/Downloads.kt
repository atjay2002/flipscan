package app.flipscan.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Copies exported files into the public Downloads/FlipScan folder via MediaStore.
 * Never overwrites an existing file: a free "name (2).ext" is picked instead.
 */
object Downloads {
    const val FOLDER = "FlipScan"

    /** A file that now exists in Downloads, with the content URI other apps can open. */
    data class Saved(val name: String, val uri: String, val mime: String)

    fun mimeOf(f: File): String = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        else -> "image/jpeg"
    }

    private fun relativePath() = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER

    fun save(ctx: Context, files: List<File>): List<Saved> = files.map { saveOne(ctx, it) }

    private fun saveOne(ctx: Context, f: File): Saved =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveModern(ctx, f) else saveLegacy(ctx, f)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveModern(ctx: Context, f: File): Saved {
        val rel = relativePath()
        val name = freeMediaStoreName(ctx, rel, f.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(f))
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create file in Downloads")
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Could not write to Downloads")
        } catch (e: Exception) {
            runCatching { ctx.contentResolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        return Saved(name, uri.toString(), mimeOf(f))
    }

    /** Android 7-9: needs WRITE_EXTERNAL_STORAGE (requested only on these versions). */
    private fun saveLegacy(ctx: Context, f: File): Saved {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            .apply { mkdirs() }
        val dest = Naming.uniqueFile(dir, f.name)
        f.copyTo(dest, overwrite = false)
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.path), arrayOf(mimeOf(dest)), null)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
        return Saved(dest.name, uri.toString(), mimeOf(dest))
    }

    /** First DISPLAY_NAME in Downloads/FlipScan that is not taken yet. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun freeMediaStoreName(ctx: Context, rel: String, fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot <= 0) fileName else fileName.substring(0, dot)
        val ext = if (dot <= 0) "" else fileName.substring(dot)
        var candidate = fileName
        var i = 1
        while (mediaNameExists(ctx, rel, candidate) && i < 1000) {
            i++
            candidate = base + " ($i)" + ext
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaNameExists(ctx: Context, rel: String, name: String): Boolean =
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "$rel%"),
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)

    /** True when the saved file is still readable (the user may have deleted it from Downloads). */
    fun exists(ctx: Context, saved: SavedExport): Boolean =
        runCatching {
            ctx.contentResolver.openAssetFileDescriptor(Uri.parse(saved.uri), "r")?.use { true } ?: false
        }.getOrDefault(false)
}
