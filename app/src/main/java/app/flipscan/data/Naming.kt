package app.flipscan.data

import java.io.File

/**
 * User-visible names: sanitising, previewing and picking a non-colliding file name.
 * Kept in one place so the scan name (inside FlipScan) and the export file name
 * (in Downloads) follow the same rules.
 */
object Naming {
    const val MAX_LEN = 64

    /** Characters that are invalid in the file systems we can land on (incl. FAT via SD cards). */
    private val INVALID = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]")
    private val SPACES = Regex("\\s+")
    private val KNOWN_EXT = Regex("\\.(pdf|jpe?g|png)$", RegexOption.IGNORE_CASE)

    /**
     * Cleans [raw] into something safe to write as a file name, without the extension.
     * Returns [fallback] when nothing usable is left.
     */
    fun sanitize(raw: String, fallback: String): String {
        val cleaned = raw
            .replace(INVALID, " ")
            .replace(SPACES, " ")
            .trim()
            .trim('.', ' ')
        val limited = if (cleaned.length > MAX_LEN) cleaned.take(MAX_LEN).trim().trim('.', ' ') else cleaned
        return limited.ifEmpty { fallback }
    }

    /** True when [sanitize] would fall back, i.e. the user typed nothing usable. */
    fun isBlank(raw: String): Boolean = sanitize(raw, "").isEmpty()

    /**
     * Base name for an exported file: cleaned, and a trailing .pdf/.jpg/.jpeg/.png removed so
     * typing "report.pdf" with the PDF format does not produce "report.pdf.pdf".
     */
    fun baseName(raw: String, fallback: String): String {
        val clean = sanitize(raw, fallback)
        return clean.replace(KNOWN_EXT, "").trim().trim('.', ' ').ifEmpty { clean }
    }

    /** "Card 3" + 2 -> "Card 3 (2)"; 1 returns the plain name. */
    fun suffixed(base: String, n: Int): String = if (n <= 1) base else "$base ($n)"

    /** Splits "a.b.pdf" into ("a.b", ".pdf"); extension is always kept for exports. */
    private fun split(fileName: String): Pair<String, String> {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) fileName to "" else fileName.substring(0, dot) to fileName.substring(dot)
    }

    /** First free `base.ext`, `base (2).ext`, ... inside [dir]. Never returns an existing file. */
    fun uniqueFile(dir: File, fileName: String): File {
        val (base, ext) = split(fileName)
        var candidate = File(dir, base + ext)
        var i = 1
        while (candidate.exists() && i < 1000) {
            i++
            candidate = File(dir, suffixed(base, i) + ext)
        }
        return candidate
    }

    /** "Card 3" + "pdf" -> "Card 3.pdf", for the preview line in the export panel. */
    fun preview(name: String, ext: String, fallback: String): String =
        baseName(name, fallback) + "." + ext
}
