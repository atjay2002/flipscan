package app.flipscan.scan

import kotlin.math.hypot

data class Pt(val x: Double, val y: Double)

/** Four corners in order top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    fun toList() = listOf(tl, tr, br, bl)

    /** Largest corner movement between two quads (same units as the coordinates). */
    fun maxShift(o: Quad): Double =
        toList().zip(o.toList()).maxOf { (a, b) -> dist(a, b) }
}

fun dist(a: Pt, b: Pt): Double = hypot(a.x - b.x, a.y - b.y)
