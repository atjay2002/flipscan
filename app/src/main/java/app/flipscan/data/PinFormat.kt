package app.flipscan.data

/**
 * Formatting, masking and light validation for the numbers Digital Pins shows.
 *
 * Pure Kotlin, no Android types: the rules are easy to read and the UI never has to guess how a
 * number should look. These checks are about *shape*, not about the authority having confirmed a
 * document: an Aadhaar checksum passing mainly means the digits are internally consistent, which is
 * also a good sign that OCR read them correctly.
 */
object PinFormat {
    private const val MASK = '•'

    /** Canonical storage form: no spaces or dashes for typed documents; CUSTOM keeps readable text. */
    fun normalize(type: LockerDocType, raw: String): String {
        val upper = raw.uppercase()
        return when (type) {
            LockerDocType.AADHAAR -> upper.filter { it.isDigit() }.take(12)
            LockerDocType.PAN -> upper.filter { it.isLetterOrDigit() }.take(10)
            LockerDocType.PASSPORT -> upper.filter { it.isLetterOrDigit() }.take(9)
            LockerDocType.DRIVING_LICENCE -> upper.filter { it.isLetterOrDigit() }.take(16)
            LockerDocType.VOTER_ID -> upper.filter { it.isLetterOrDigit() }.take(10)
            LockerDocType.CUSTOM -> raw.trim().replace(Regex("\\s+"), " ").take(40)
        }
    }

    /** How the number is written out when the user asks to see it. */
    fun display(type: LockerDocType, value: String): String {
        val clean = normalize(type, value)
        return if (type == LockerDocType.AADHAAR && clean.length == 12) {
            "${clean.substring(0, 4)} ${clean.substring(4, 8)} ${clean.substring(8)}"
        } else {
            clean
        }
    }

    /**
     * Safe to show by default. Aadhaar hides the first eight digits (as the printed card does in
     * many places); everything else keeps only the last four characters visible.
     */
    fun masked(type: LockerDocType, value: String): String {
        val clean = normalize(type, value)
        if (clean.isEmpty()) return ""
        return if (type == LockerDocType.AADHAAR && clean.length == 12) {
            "$MASK$MASK$MASK$MASK $MASK$MASK$MASK$MASK ${clean.substring(8)}"
        } else {
            val hidden = (clean.length - 4).coerceAtLeast(0)
            if (hidden == 0) clean else MASK.toString().repeat(hidden) + clean.takeLast(4)
        }
    }

    fun isValid(type: LockerDocType, value: String): Boolean {
        val clean = normalize(type, value)
        return when (type) {
            LockerDocType.AADHAAR -> verhoeffValid(clean)
            LockerDocType.PAN -> Regex("^[A-Z]{5}[0-9]{4}[A-Z]$").matches(clean)
            LockerDocType.PASSPORT -> Regex("^[A-Z][0-9]{7}$").matches(clean)
            LockerDocType.DRIVING_LICENCE -> Regex("^[A-Z]{2}[0-9]{11,13}$").matches(clean)
            LockerDocType.VOTER_ID -> Regex("^[A-Z]{3}[0-9]{7}$").matches(clean)
            LockerDocType.CUSTOM -> clean.length >= 3
        }
    }

    /** One line under the field while editing, phrased for a person, not a validator. */
    fun hint(type: LockerDocType, value: String): String {
        val clean = normalize(type, value)
        if (clean.isEmpty()) {
            return when (type) {
                LockerDocType.AADHAAR -> "12 digits, as printed on the card"
                LockerDocType.PAN -> "10 characters, e.g. ABCDE1234F"
                LockerDocType.PASSPORT -> "8 characters, e.g. A1234567"
                LockerDocType.DRIVING_LICENCE -> "State code and digits, e.g. MH0220110012345"
                LockerDocType.VOTER_ID -> "10 characters, e.g. ABC1234567"
                LockerDocType.CUSTOM -> "Any reference number"
            }
        }
        return when {
            isValid(type, clean) && type == LockerDocType.AADHAAR -> "Checksum matches — looks like a valid Aadhaar number."
            isValid(type, clean) -> "Format looks right."
            type == LockerDocType.AADHAAR -> "Does not pass the Aadhaar checksum. Check each digit."
            else -> "That does not match the usual ${type.label} format yet."
        }
    }

    /**
     * Aadhaar's twelfth digit is a Verhoeff checksum, which catches most OCR misreads.
     * Tables are the standard Verhoeff multiplication and permutation tables.
     */
    fun verhoeffValid(digits: String): Boolean {
        if (digits.length != 12 || digits.any { !it.isDigit() }) return false
        var c = 0
        digits.reversed().forEachIndexed { i, ch ->
            c = D[c][P[i % 8][ch - '0']]
        }
        return c == 0
    }

    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )

    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )
}
