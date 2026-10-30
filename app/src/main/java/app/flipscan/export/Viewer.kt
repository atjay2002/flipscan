package app.flipscan.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.flipscan.data.Downloads
import app.flipscan.data.SavedExport

/** Opens a previously saved export in whatever viewer the user has, via a safe content URI. */
object Viewer {
    /** Returns null on success, or a short message that can be shown to the user. */
    fun open(ctx: Context, saved: SavedExport): String? {
        if (!Downloads.exists(ctx, saved)) return "This file is no longer in Downloads/FlipScan."
        return try {
            val uri = Uri.parse(saved.uri)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, saved.mime.ifEmpty { "application/pdf" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "No app on this phone can open ${if (saved.mime.startsWith("image/")) "images" else "PDF files"}."
        } catch (e: SecurityException) {
            "This file cannot be opened any more. Save the scan again."
        }
    }
}
