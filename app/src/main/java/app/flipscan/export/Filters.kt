package app.flipscan.export

import android.graphics.Bitmap
import app.flipscan.data.Filter

object Filters {
    /** Milestone 6 adds the real filters. For now every filter returns the image unchanged. */
    fun apply(src: Bitmap, filter: Filter): Bitmap = src
}
