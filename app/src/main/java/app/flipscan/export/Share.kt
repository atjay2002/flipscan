package app.flipscan.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.flipscan.data.Downloads
import java.io.File

object Share {
    /** Opens the system share sheet for exported files (one or several). */
    fun send(ctx: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = Downloads.mimeOf(files[0])
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Share scan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
