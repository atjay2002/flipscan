package app.flipscan.ui

import android.app.ActivityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Design tokens (light default; dark follows system)
// ---------------------------------------------------------------------------------------------

val DesignBackground = Color(0xFFF4F1FF)
val DesignInk = Color(0xFF17142B)
val DesignMuted = Color(0xFF4A4660)
val DesignBrand = Color(0xFF4B2FE0)
val DesignBrandLight = Color(0xFF6A4CFF)
val DesignAccent = Color(0xFFFFB020)
val DesignOfflineText = Color(0xFF0A5A3F)
val DesignChipText = Color(0xFF2E2194)
val DesignGlowPurple = Color(0xFF7C5CFF)
val DesignGlowAmber = Color(0xFFFFB020)
val DesignGlowMint = Color(0xFF3DDCB5)

val DesignShapeLarge = RoundedCornerShape(22.dp)
val DesignShapeMedium = RoundedCornerShape(16.dp)
val DesignShapeSmall = RoundedCornerShape(12.dp)
val DesignShapePill = RoundedCornerShape(50.dp)

val DesignPaddingHorizontal = 20.dp

// ---------------------------------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------------------------------

@Composable
fun rememberReduceMotion(): Boolean {
    val scale = rememberMotionScale()
    return scale == 0f
}

@Composable
fun rememberIsLowRam(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        ctx.applicationContext.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
}

// ---------------------------------------------------------------------------------------------
// GlowBackground — radial-gradient blobs behind content
// ---------------------------------------------------------------------------------------------

@Composable
fun GlowBackground(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val reduce = rememberReduceMotion()
    val lowRam = rememberIsLowRam()
    val shouldAnimate = enabled && !reduce && !lowRam

    // Animate a single drift progress (0..1) and derive blob positions from it.
    val drift = remember { Animatable(0f) }

    if (shouldAnimate) {
        LaunchedEffect(Unit) {
            while (true) {
                drift.animateTo(1f, animationSpec = tween(9000, easing = FastOutSlowInEasing))
                drift.animateTo(0f, animationSpec = tween(9000, easing = FastOutSlowInEasing))
            }
        }
    } else {
        LaunchedEffect(Unit) { drift.snapTo(0f) }
    }

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val t = drift.value

                // Purple blob, top-left.
                val cx1 = -70f.dp.toPx() + 22f.dp.toPx() * t
                val cy1 = 130f.dp.toPx() - 18f.dp.toPx() * t
                drawCircle(
                    color = DesignGlowPurple.copy(alpha = 0.5f),
                    center = Offset(cx1, cy1),
                    radius = 125f.dp.toPx() * (1f + 0.12f * t)
                )
                // Amber blob, right-middle.
                val cx2 = w + 60f.dp.toPx() - 22f.dp.toPx() * t
                val cy2 = 470f.dp.toPx() + 18f.dp.toPx() * t
                drawCircle(
                    color = DesignGlowAmber.copy(alpha = 0.5f),
                    center = Offset(cx2, cy2),
                    radius = 105f.dp.toPx() * (1f + 0.12f * t)
                )
                // Mint blob, bottom-left.
                val cx3 = 30f.dp.toPx() + 16f.dp.toPx() * t
                val cy3 = h - 340f.dp.toPx() - 14f.dp.toPx() * t
                drawCircle(
                    color = DesignGlowMint.copy(alpha = 0.4f),
                    center = Offset(cx3, cy3),
                    radius = 110f.dp.toPx() * (1f + 0.12f * t)
                )
            }
    )
}

// ---------------------------------------------------------------------------------------------
// GlassCard — white translucent card, no blur
// ---------------------------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DesignShapeLarge,
    strong: Boolean = false,
    content: @Composable () -> Unit
) {
    val tokens = LocalGlass.current
    val fill = if (strong) tokens.fillStrong else tokens.fill
    val baseColor = if (strong) Color.White.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.55f)
    val borderAlpha = if (strong) 0.90f else 0.75f

    Column(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(baseColor, baseColor.copy(alpha = baseColor.alpha * 0.82f)),
                        endY = size.height.coerceAtLeast(1f)
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to tokens.sheen.copy(alpha = 0.35f),
                        0.62f to Color.Transparent,
                        endY = size.height.coerceAtLeast(1f)
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = borderAlpha),
                            Color.White.copy(alpha = borderAlpha * 0.30f)
                        )
                    ),
                    style = Stroke(width = 1f)
                )
            }
            .shadow(
                8.dp,
                shape,
                clip = false,
                ambientColor = DesignBrand.copy(alpha = 0.12f),
                spotColor = DesignBrand.copy(alpha = 0.06f)
            )
            .padding(12.dp)
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// PrimaryButton — gradient brand-light → brand, shine sweep
// ---------------------------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val reduce = rememberReduceMotion()
    val shine = remember { Animatable(0f) }

    if (!reduce && enabled) {
        LaunchedEffect(Unit) {
            while (true) {
                shine.animateTo(1f, animationSpec = tween(1800, delayMillis = 1500, easing = FastOutSlowInEasing))
                shine.animateTo(0f, animationSpec = tween(1800, delayMillis = 0, easing = FastOutSlowInEasing))
            }
        }
    } else {
        LaunchedEffect(Unit) { shine.snapTo(0f) }
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(motionMs(120)),
        label = "primaryButtonPress"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DesignBrandLight, DesignBrand),
                    start = Offset.Zero,
                    end = Offset(1000f, 0f)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(22.dp)
            )
            .shadow(
                12.dp,
                RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = DesignBrand.copy(alpha = 0.30f),
                spotColor = DesignBrand.copy(alpha = 0.18f)
            )
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(70.dp)
                .graphicsLayer {
                    translationX = (-52f + (size.width + 52f) * shine.value)
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset.Zero,
                        end = Offset(52f, 0f)
                    )
                )
        )

        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SecondaryTile — glass tile
// ---------------------------------------------------------------------------------------------

@Composable
fun SecondaryTile(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(motionMs(120)),
        label = "secondaryTilePress"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.55f * 0.82f)
                        ),
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
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.30f))
                    ),
                    style = Stroke(width = 1f)
                )
            }
            .shadow(2.dp, RoundedCornerShape(20.dp), clip = false)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = DesignMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// SectionHeader — title + optional link
// ---------------------------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    link: String? = null,
    onLinkClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            ),
            color = DesignInk
        )
        Spacer(Modifier.weight(1f))
        link?.let {
            TextButton(
                onClick = { onLinkClick?.invoke() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    link,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = DesignBrand
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// PillBadge — small rounded badge
// ---------------------------------------------------------------------------------------------

@Composable
fun PillBadge(
    text: String,
    color: Color = DesignBrand,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = textColor
        )
    }
}

// ---------------------------------------------------------------------------------------------
// OfflinePill — glass pill with shield icon
// ---------------------------------------------------------------------------------------------

@Composable
fun OfflinePill(modifier: Modifier = Modifier, text: String = "Works offline") {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
            pulse.animateTo(0f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(50.dp))
            .drawBehind {
                val ringSize = size.minDimension + (12f.dp.toPx() * pulse.value)
                drawCircle(
                    color = DesignOfflineText.copy(alpha = 0.18f * (1f - pulse.value)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = ringSize / 2f,
                    style = Stroke(width = 2f.dp.toPx() * (1f - pulse.value))
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.55f * 0.82f)),
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
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.30f))
                    ),
                    style = Stroke(width = 1f)
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = DesignOfflineText,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = DesignOfflineText
            )
        }
    }
}
