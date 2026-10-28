package app.flipscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small hand-drawn icon set. Material's core icon artifact has no flash, crop or rotate glyphs,
 * and drawing them here keeps the APK free of an extra dependency.
 */
private fun Modifier.iconLabel(label: String?): Modifier =
    if (label == null) this else semantics { contentDescription = label }

@Composable
fun BoltIcon(modifier: Modifier = Modifier, color: Color, outline: Boolean = false, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.62f, h * 0.02f)
            lineTo(w * 0.20f, h * 0.56f)
            lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.36f, h * 0.98f)
            lineTo(w * 0.80f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.42f)
            close()
        }
        if (outline) {
            drawPath(bolt, color, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
        } else {
            drawPath(bolt, color)
        }
    }
}

@Composable
fun CropIcon(modifier: Modifier = Modifier, color: Color, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (w * 0.09f).coerceAtLeast(1.5.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.02f)
            lineTo(w * 0.28f, h * 0.72f)
            lineTo(w * 0.98f, h * 0.72f)
            moveTo(w * 0.02f, h * 0.28f)
            lineTo(w * 0.72f, h * 0.28f)
            lineTo(w * 0.72f, h * 0.98f)
        }
        drawPath(path, color, style = stroke)
    }
}

/** Circular arrow; [clockwise] flips the direction so the two rotate buttons read correctly. */
@Composable
fun RotateIcon(modifier: Modifier = Modifier, color: Color, clockwise: Boolean = true, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val stroke = (w * 0.1f).coerceAtLeast(1.6.dp.toPx())
        val start = if (clockwise) 40f else 220f
        val sweep = if (clockwise) 280f else -280f
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(w - stroke, w - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val endRad = Math.toRadians((start + sweep).toDouble())
        val c = w / 2f
        val r = c - stroke / 2f
        val tip = Offset(c + (r * cos(endRad)).toFloat(), c + (r * sin(endRad)).toFloat())
        // Small arrow head oriented along the tangent.
        val tangent = endRad + Math.toRadians(if (clockwise) 90.0 else -90.0)
        val tx = cos(tangent).toFloat()
        val ty = sin(tangent).toFloat()
        val px = -ty
        val py = tx
        val len = w * 0.26f
        val half = w * 0.13f
        val head = Path().apply {
            moveTo(tip.x + tx * len, tip.y + ty * len)
            lineTo(tip.x + px * half, tip.y + py * half)
            lineTo(tip.x - px * half, tip.y - py * half)
            close()
        }
        drawPath(head, color)
    }
}

@Composable
fun CardStackIcon(modifier: Modifier = Modifier, color: Color, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.1f)
        drawRoundRect(
            color.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.06f, h * 0.02f),
            size = Size(w * 0.88f, h * 0.4f),
            cornerRadius = r
        )
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.06f, h * 0.3f),
            size = Size(w * 0.88f, h * 0.4f),
            cornerRadius = r
        )
        drawRoundRect(
            color.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.16f, h * 0.39f),
            size = Size(w * 0.34f, h * 0.055f),
            cornerRadius = CornerRadius(h * 0.03f)
        )
    }
}

@Composable
fun DocumentIcon(modifier: Modifier = Modifier, color: Color, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.075f).coerceAtLeast(1.4.dp.toPx())
        val page = Path().apply {
            moveTo(w * 0.22f, h * 0.03f)
            lineTo(w * 0.60f, h * 0.03f)
            lineTo(w * 0.80f, h * 0.23f)
            lineTo(w * 0.80f, h * 0.97f)
            lineTo(w * 0.22f, h * 0.97f)
            close()
            moveTo(w * 0.60f, h * 0.03f)
            lineTo(w * 0.60f, h * 0.23f)
            lineTo(w * 0.80f, h * 0.23f)
        }
        drawPath(page, color, style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round))
        for (i in 0..2) {
            val y = h * (0.45f + i * 0.16f)
            drawLine(
                color.copy(alpha = 0.8f),
                Offset(w * 0.33f, y), Offset(w * 0.69f, y),
                strokeWidth = stroke * 0.8f, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun DownloadIcon(modifier: Modifier = Modifier, color: Color, label: String? = null) {
    Canvas(modifier.iconLabel(label)) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.085f).coerceAtLeast(1.5.dp.toPx())
        drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.6f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.3f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.7f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
        val tray = Path().apply {
            moveTo(w * 0.16f, h * 0.72f)
            lineTo(w * 0.16f, h * 0.94f)
            lineTo(w * 0.84f, h * 0.94f)
            lineTo(w * 0.84f, h * 0.72f)
        }
        drawPath(tray, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Decorative mark used on the home screen: two cards, one behind the other. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.12f)
        drawRoundRect(
            tint.copy(alpha = 0.4f),
            topLeft = Offset(w * 0.12f, h * 0.1f),
            size = Size(w * 0.66f, h * 0.5f),
            cornerRadius = r
        )
        drawRoundRect(
            tint,
            topLeft = Offset(w * 0.2f, h * 0.38f),
            size = Size(w * 0.68f, h * 0.52f),
            cornerRadius = r
        )
        drawRoundRect(
            tint.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.3f, h * 0.52f),
            size = Size(w * 0.3f, h * 0.07f),
            cornerRadius = CornerRadius(h * 0.04f)
        )
    }
}
