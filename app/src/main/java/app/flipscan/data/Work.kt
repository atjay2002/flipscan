package app.flipscan.data

import android.content.Context
import java.io.File

/** Scratch space (app-private) for the scan in progress. */
object Work {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "work").apply { mkdirs() }

    fun newFile(ctx: Context, prefix: String, ext: String = "jpg"): File =
        File(dir(ctx), "${prefix}_${System.nanoTime()}.$ext")

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
