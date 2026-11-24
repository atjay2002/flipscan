package app.flipscan.data

import android.content.Context
import android.graphics.Bitmap
import app.flipscan.util.decodeSampled
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** A number found by OCR, and the page it was read from. */
data class DetectedPin(val value: String, val page: Int, val verified: Boolean)

/**
 * Reads the document number from an encrypted locker document.
 *
 * Recognition is ML Kit's bundled Latin model: it runs entirely on the phone, with no network and
 * no Play Services download. Pages are decrypted one at a time, decoded at a bounded size, handed
 * to the recogniser and recycled immediately, so at most one page bitmap is alive at once.
 *
 * The result is stored in the encrypted index by [LockerStore.setNumber]; nothing here writes a
 * plaintext file or logs a number.
 */
object LockerOcr {
    private const val OCR_MAX = 2200

    /**
     * Runs recognition over the document's pages and saves the first plausible number.
     * Also marks the document as checked when nothing is found, so opening it again is cheap.
     */
    suspend fun detectAndStore(ctx: Context, id: String): LockerDoc? = withContext(Dispatchers.Default) {
        val doc = LockerStore.get(ctx, id) ?: return@withContext null
        if (!doc.type.pins) return@withContext doc
        val found = runCatching { findNumber(ctx, doc) }.getOrNull()
        val saved = if (found != null) {
            LockerStore.setNumber(ctx, id, found.value, found.page, found.verified, checked = true)
        } else {
            LockerStore.markNumberChecked(ctx, id)
        }
        saved ?: doc
    }

    /** First page that yields a candidate wins: the number is usually on the front side. */
    suspend fun findNumber(ctx: Context, doc: LockerDoc): DetectedPin? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for (page in 0 until doc.pages) {
                val bytes = LockerStore.pageBytes(ctx, doc.id, page) ?: continue
                val bitmap = decodeSampled(bytes, OCR_MAX) ?: continue
                val text = try {
                    recognize(recognizer, bitmap)
                } finally {
                    bitmap.recycle()
                }
                val candidate = candidates(doc.type, text).firstOrNull() ?: continue
                return DetectedPin(
                    value = candidate,
                    page = page,
                    verified = PinFormat.isValid(doc.type, candidate)
                )
            }
        } finally {
            recognizer.close()
        }
        return null
    }

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }

    /**
     * Candidate numbers for one page, best first. Split out from recognition so the patterns can be
     * read (and reasoned about) without a camera or a model. Returns normalized values.
     */
    fun candidates(type: LockerDocType, text: String): List<String> {
        val upper = text.uppercase()
        val found = LinkedHashSet<String>()
        fun add(raw: String?) {
            val clean = raw?.let { PinFormat.normalize(type, it) } ?: return
            if (clean.isNotEmpty()) found += clean
        }
        when (type) {
            LockerDocType.AADHAAR -> {
                // Grouped on the card, but OCR sometimes drops or adds spaces.
                Regex("(?<!\\d)\\d{4}\\s?\\d{4}\\s?\\d{4}(?!\\d)").findAll(upper).forEach { add(it.value) }
                Regex("(?<!\\d)\\d{12}(?!\\d)").findAll(upper.replace(" ", "")).forEach { add(it.value) }
            }
            LockerDocType.PAN -> {
                Regex("(?<![A-Z0-9])[A-Z]{5}[0-9]{4}[A-Z](?![A-Z0-9])").findAll(upper).forEach { add(it.value) }
                // OCR often reads 0 as O (and 1 as I/L) inside the four digits.
                Regex("(?<![A-Z0-9])[A-Z]{5}[0-9OIL]{4}[A-Z](?![A-Z0-9])").findAll(upper).forEach {
                    add(fixDigits(it.value, 5, 4))
                }
            }
            LockerDocType.PASSPORT -> {
                // Second MRZ line starts with the 9-character passport field (8 + '<' filler).
                Regex("[A-Z0-9<]{9}[0-9][A-Z]{3}[0-9]{6}").findAll(upper).forEach { m ->
                    add(m.value.take(9).replace("<", ""))
                }
                Regex("(?<![A-Z0-9])[A-Z][0-9]{7}(?![A-Z0-9])").findAll(upper).forEach { add(it.value) }
            }
            LockerDocType.DRIVING_LICENCE -> {
                // State code, RTO, issue year and serial, with or without the printed spaces.
                Regex("[A-Z]{2}(?:\\s?[0-9]){11,13}").findAll(upper).forEach { add(it.value) }
            }
            LockerDocType.VOTER_ID -> {
                Regex("(?<![A-Z0-9])[A-Z]{3}[0-9]{7}(?![A-Z0-9])").findAll(upper).forEach { add(it.value) }
            }
            LockerDocType.CUSTOM -> Unit
        }
        // A candidate that passes the format check outranks one that only looks plausible.
        return found.sortedByDescending { PinFormat.isValid(type, it) }
    }

    /** Forces the digit block of an otherwise-certain match, turning O/I/L into 0/1/1. */
    private fun fixDigits(value: String, start: Int, length: Int): String {
        val chars = value.toCharArray()
        for (i in start until (start + length).coerceAtMost(chars.size)) {
            chars[i] = when (chars[i]) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> chars[i]
            }
        }
        return String(chars)
    }
}
