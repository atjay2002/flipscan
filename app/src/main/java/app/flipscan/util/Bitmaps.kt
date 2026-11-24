package app.flipscan.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decodes a downsampled bitmap so the longest side is at least [maxSide] px (never the full image). */
fun decodeSampled(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    return BitmapFactory.decodeFile(file.path, options(bounds, maxSide))
}

/**
 * Same as the [File] overload, for decrypted bytes that are never written to disk (locker pages
 * and thumbnails). The byte array is only read by the decoder; no plaintext file is produced.
 */
fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options(bounds, maxSide))
}

private fun options(bounds: BitmapFactory.Options, maxSide: Int): BitmapFactory.Options {
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
}
