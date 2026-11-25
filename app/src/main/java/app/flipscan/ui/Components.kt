package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.flipscan.scan.ScanCameraState

/**
 * Standard light-theme top bar used by every non-camera screen.
 * Kept as a Row (not TopAppBar) so the height and typography match the rest of the app exactly.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

/** Same bar on the immersive (always dark) camera screens. */
@Composable
fun CameraHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        // Camera screens run edge to edge, so this bar clears the status bar itself.
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Box(Modifier.size(38.dp).cameraGlass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
            }
        }
        actions()
    }
}

/**
 * Keeps the status and navigation bar icons light while a camera screen is on top.
 *
 * The app is edge to edge (Android 15+ forces it for apps targeting API 35+), so the system bars
 * are transparent and their icons are drawn over our own UI. Camera screens are dark in both
 * themes, so the theme-driven dark icons would vanish on them. The previous appearance is
 * restored when the screen leaves composition.
 */
@Composable
fun LightSystemBars() {
    val ctx = LocalContext.current
    DisposableEffect(ctx) {
        val window = ctx.findFragmentActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
}

/** Clickable with a ripple and a subtle press-in scale. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = tween(motionMs(120)),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

/**
 * The app's glass surface: a translucent fill, a short sheen over the top third and a fine
 * two-tone border (bright on top, quiet at the bottom).
 *
 * Plain draw calls only — no RenderEffect/backdrop blur — so a list can use it on every row
 * without costing an extra layer or a per-frame blur. [accent] adds a soft tint at the top-left
 * for surfaces that should echo a colour without changing their text contrast.
 */
@Composable
fun Modifier.glass(
    shape: Shape = MaterialTheme.shapes.large,
    strong: Boolean = false,
    accent: Color? = null,
    borderAccent: Color? = null,
    elevation: Dp = 0.dp
): Modifier {
    val tokens = LocalGlass.current
    val fill = if (strong) tokens.fillStrong else tokens.fill
    var base = this
    if (elevation > 0.dp) {
        base = base.shadow(elevation, shape, clip = false, ambientColor = tokens.shadow, spotColor = tokens.shadow)
    }
    return base
        .clip(shape)
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(fill, fill.copy(alpha = fill.alpha * 0.82f)),
                    endY = size.height.coerceAtLeast(1f)
                )
            )
            if (accent != null) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.maxDimension * 0.8f + 1f
                    )
                )
            }
            // Sheen: strongest along the top edge, gone before the middle of the surface.
            drawRect(
                Brush.verticalGradient(
                    0f to tokens.sheen,
                    0.62f to Color.Transparent,
                    endY = size.height.coerceAtLeast(1f)
                )
            )
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                if (borderAccent != null) {
                    listOf(borderAccent.copy(alpha = 0.55f), borderAccent.copy(alpha = 0.22f))
                } else {
                    listOf(tokens.borderTop, tokens.borderBottom)
                }
            ),
            shape
        )
}

/**
 * The same treatment in fixed camera colours. The camera chrome is always dark, so it cannot use
 * the theme-dependent [LocalGlass] tokens.
 */
@Composable
fun Modifier.cameraGlass(shape: Shape = CircleShape): Modifier = this
    .clip(shape)
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xB30B1220), Color(0x8C0B1220)),
                endY = size.height.coerceAtLeast(1f)
            )
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.62f to Color.Transparent,
                endY = size.height.coerceAtLeast(1f)
            )
        )
    }
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.08f))),
        shape
    )

/**
 * One very soft accent wash in the top-end corner. Used on dashboard-style roots, never inside
 * scrolling items, and drawn behind the content so text contrast is unaffected.
 */
@Composable
fun Modifier.ambientGlow(color: Color = LocalGlass.current.glow): Modifier = drawBehind {
    val w = size.width.coerceAtLeast(1f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(w * 0.92f, -size.height * 0.04f),
            radius = w * 1.15f
        )
    )
}

/** A flat, bordered surface used for grouped controls and list rows. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().glass(MaterialTheme.shapes.large).padding(padding.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp)
    )
}

@Composable
fun ChoiceChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = null
    )
}

/** Label + switch used on both camera screens. */
@Composable
fun AutoCaptureToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Auto", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CameraTop,
                checkedTrackColor = Color(0xFF34D399),
                uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, accent: Color = Color.Transparent) {
    Row(
        modifier
            .cameraGlass(RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (accent != Color.Transparent) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Flash / torch toggle. Reflects the live CameraX torch state, not just the last tap. */
@Composable
fun FlashToggle(cam: ScanCameraState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val available = cam.flashAvailable
    val on = cam.torchOn
    val bg by animateColorAsState(
        targetValue = when {
            available == true && on -> Amber
            available == true -> Color(0x730B1220)
            else -> Color(0x400B1220)
        },
        animationSpec = tween(motionMs(180)),
        label = "flashBg"
    )
    val description = when {
        available == false -> "Flash unavailable on this camera"
        on -> "Flash on"
        else -> "Flash off"
    }
    val tint = when {
        available == false -> Color.White.copy(alpha = 0.35f)
        on -> Color(0xFF201600)
        else -> Color.White.copy(alpha = 0.95f)
    }
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(bg)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.7f to Color.Transparent,
                        endY = size.height.coerceAtLeast(1f)
                    )
                )
            }
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.12f))
                ),
                CircleShape
            )
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) {
                when (available) {
                    true -> cam.setTorch(!on)
                    false -> Toast.makeText(ctx, "This camera has no flash unit.", Toast.LENGTH_SHORT).show()
                    null -> Unit // camera still starting; the control is inert for a moment
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BoltIcon(Modifier.size(21.dp), tint)
    }
}

/** Shutter with a press bounce and a ring that fills while the document is held steady. */
@Composable
fun ShutterButton(enabled: Boolean, progress: () -> Float = { 0f }, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotionScale.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = if (motion == 0f) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shutter"
    )
    Box(
        Modifier
            .size(84.dp)
            .semantics { contentDescription = "Take photo" }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val w = 4.dp.toPx()
                val inset = w / 2
                drawCircle(Color.White.copy(alpha = 0.28f), style = Stroke(w), radius = size.minDimension / 2 - inset)
                val p = progress()
                if (p > 0f) {
                    drawArc(
                        Color(0xFF34D399), -90f, 360f * p, false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
            }
            .padding(9.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize(0.78f).clip(CircleShape)
                .background(if (enabled) Color(0xFFF8FAFC) else Color.White.copy(alpha = 0.3f))
        )
    }
}

/** White flash drawn over the preview right after a capture. [alpha] is read while drawing. */
@Composable
fun FlashOverlay(alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.graphicsLayer { this.alpha = alpha() }.background(Color.White))
}

/** Dims what is behind and shows a spinner; swallows touches while heavy work runs. */
@Composable
fun BusyOverlay(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(0xB30A1020))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/** Asks before throwing away unsaved scans. */
@Composable
fun DiscardDialog(show: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Discard this scan?") },
        text = { Text("The photos you took so far will be deleted. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep scanning") } }
    )
}

/** One of the two numbered steps shown above the Double Side preview. */
@Composable
fun StepPill(number: Int, label: String, done: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = when {
            done -> MaterialTheme.colorScheme.secondaryContainer
            active -> MaterialTheme.colorScheme.primary
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(motionMs(220)),
        label = "stepBg"
    )
    val fg = when {
        done -> MaterialTheme.colorScheme.onSecondaryContainer
        active -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White.copy(alpha = 0.65f)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                1.dp,
                Color.White.copy(alpha = if (done || active) 0.28f else 0.12f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (done) "✓" else "$number",
            style = MaterialTheme.typography.labelLarge,
            color = fg
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Rounded count chip ("3 pages"). */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            DocumentIcon(Modifier.size(26.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        if (action != null) {
            Box(Modifier.padding(top = 12.dp)) { action() }
        }
    }
}

/** Primary/secondary action bar pinned to the bottom of the review screens. */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.large, strong = true, elevation = 8.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
