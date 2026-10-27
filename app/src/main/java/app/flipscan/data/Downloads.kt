package app.flipscan.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Copies exported files into the public Downloads/FlipScan folder via MediaStore. */
object Downloads {
    fun mimeOf(f: File): String = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        else -> "image/jpeg"
    }

    fun save(ctx: Context, files: List<File>) {
        for (f in files) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(f))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlipScan")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create file in Downloads")
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("Could not write to Downloads")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            } else {
                // Android 7-9: needs WRITE_EXTERNAL_STORAGE (requested only on these versions).
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FlipScan")
                dir.mkdirs()
                val dest = File(dir, f.name)
                f.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(ctx, arrayOf(dest.path), null, null)
            }
        }
    }
}
