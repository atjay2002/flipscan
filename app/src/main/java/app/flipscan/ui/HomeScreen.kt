package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flipscan.data.Folder
import app.flipscan.data.FolderStore
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onDoubleSide: () -> Unit,
    onRegular: () -> Unit,
    onEdit: (Scan) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onLocker: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scans by remember { mutableStateOf<List<Scan>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    var actionsFor by remember { mutableStateOf<Scan?>(null) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<Folder?>(null) }
    var deletingFolder by remember { mutableStateOf<Folder?>(null) }

    LaunchedEffect(reload) {
        val data = withContext(Dispatchers.IO) { ScanStore.list(ctx) to FolderStore.list(ctx) }
        scans = data.first
        folders = data.second
        loaded = true
    }

    fun countIn(folderId: String) = scans.count { it.folderId == folderId }

    Box(
        Modifier
            .fillMaxSize()
            .background(DesignBackground)
            .systemBarsPadding()
            .then(GlowBackground(enabled = true))
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Entrance-animated sections ──
            val reduce = rememberReduceMotion()
            val enterMs = if (reduce) 0 else motionMs(420)

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal, top = 14.dp)
                    .graphicsLayer {
                        if (!reduce) {
                            alpha = 1f
                            translationY = 0f
                        }
                    }
            ) {
                // Header row: logo + title + offline pill
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 50)))
                        .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 50), initialOffsetY = { 16.dp }))
                        , verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(DesignBrand),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandMark(Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "FlipScan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = DesignInk
                    )
                    Spacer(Modifier.weight(1f))
                    OfflinePill(text = "Works offline")
                }

                Spacer(Modifier.height(8.dp))

                // Heading: "Both sides." / "One page."
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 150)))
                        .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 150), initialOffsetY = { 16.dp }))
                ) {
                    Text(
                        "Both sides.",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = DesignInk,
                        lineHeight = 36.sp
                    )
                    Text(
                        "One page.",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = DesignBrand,
                        lineHeight = 36.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Promo row: two equal glass cards ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal)
                    .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 450)))
                    .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 450), initialOffsetY = { 16.dp })),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Card 1: Both sides, one page
                PromoCardBothSides(
                    onClick = onDoubleSide,
                    modifier = Modifier.weight(1f).height(188.dp)
                )
                // Card 2: Digital Locker
                PromoCardLocker(
                    onClick = onLocker,
                    modifier = Modifier.weight(1f).height(188.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Primary button: Scan ID card ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal)
                    .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 600)))
                    .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 600), initialOffsetY = { 16.dp }))
            ) {
                PrimaryButton(
                    onClick = onDoubleSide,
                    icon = {
                        CameraIcon(Modifier.size(26.dp), Color.White)
                    },
                    title = "Scan ID card",
                    subtitle = "Aadhaar, PAN, DL, voter ID"
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Tiles row: Document + Digital Locker ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal)
                    .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 750)))
                    .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 750), initialOffsetY = { 16.dp })),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                SecondaryTile(
                    onClick = onRegular,
                    icon = { DocumentIcon(Modifier.size(26.dp), DesignBrand) },
                    title = "Document",
                    subtitle = "Passbooks, notes",
                    modifier = Modifier.weight(1f)
                )
                SecondaryTile(
                    onClick = onLocker,
                    icon = { LockIcon(Modifier.size(26.dp), DesignBrand) },
                    title = "Digital Locker",
                    subtitle = "Open your vault",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Section: Recent scans ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = "Recent scans",
                    link = "New folder",
                    onLinkClick = { creatingFolder = true }
                )
            }

            if (!loaded) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            } else if (scans.isEmpty()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignPaddingHorizontal)
                        .then(if (reduce) Modifier else Modifier.fadeIn(animationSpec = tween(enterMs, delayMillis = 750)))
                        .then(if (reduce) Modifier else Modifier.slideInVertically(animationSpec = tween(enterMs, delayMillis = 750), initialOffsetY = { 16.dp })),
                    strong = true
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            FlipIcon(Modifier.size(20.dp), DesignBrand)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Scan your first ID",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            ),
                            color = DesignInk,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Saved scans stay on this phone.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = DesignMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp, horizontal = 12.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = DesignPaddingHorizontal, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scans, key = { it.id }) { scan ->
                        ScanRow(
                            scan = scan,
                            folderName = folders.firstOrNull { it.id == scan.folderId }?.name,
                            onActions = { actionsFor = scan }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Footer ──
            Text(
                "Private by default. Shared only by you.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = DesignMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignPaddingHorizontal, bottom = 28.dp)
            )
        }

        // ── Scan actions sheet (unchanged behavior) ──
        ScanActionsHost(
            scan = actionsFor,
            folders = folders,
            onDismiss = { actionsFor = null },
            onEdit = { s -> actionsFor = null; onEdit(s) },
            onChanged = { reload++ }
        )
    }

    // ── Folder dialogs (unchanged behavior) ──
    if (creatingFolder) {
        FolderNameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                creatingFolder = false
                scope.launch {
                    val folder = withContext(Dispatchers.IO) { FolderStore.create(ctx, name) }
                    reload++
                    if (folder != null) Toast.makeText(ctx, "Folder \"${folder.name}\" created", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = "Rename folder",
            initial = folder.name,
            confirmLabel = "Save",
            onDismiss = { renamingFolder = null },
            onConfirm = { name ->
                renamingFolder = null
                scope.launch {
                    withContext(Dispatchers.IO) { FolderStore.rename(ctx, folder.id, name) }
                    reload++
                }
            }
        )
    }

    deletingFolder?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            scanCount = countIn(folder.id),
            onDismiss = { deletingFolder = null },
            onConfirm = {
                deletingFolder = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ScanStore.clearFolder(ctx, folder.id)
                        FolderStore.delete(ctx, folder.id)
                    }
                    reload++
                }
            }
        )
    }

    folderMenu?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            scanCount = countIn(folder.id),
            onDismiss = { folderMenu = null },
            onRename = { folderMenu = null; renamingFolder = folder },
            onDelete = { folderMenu = null; deletingFolder = folder }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Promo card: Both sides, one page (ID card illustration)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PromoCardBothSides(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduce = rememberReduceMotion()
    val lowRam = rememberIsLowRam()

    // Entrance animation for the front card (drop in) and back card (flip in)
    var frontOffset by remember { Animatable(0f) }
    var frontRotation by remember { Animatable(0f) }
    var backRotationX by remember { Animatable(0f) }

    if (!reduce && !lowRam) {
        LaunchedEffect(Unit) {
            frontOffset.animateTo(
                0f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
            frontRotation.animateTo(
                -3f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
            delay(700)
            backRotationX.animateTo(
                0f,
                animationSpec = tween(900, easing = FastOutSlowInEasing)
            )
        }
        // Continuous floating animation
        LaunchedEffect(Unit) {
            while (true) {
                frontOffset.animateTo(
                    -5f.dp.toPx(),
                    animationSpec = tween(2250, easing = FastOutSlowInEasing)
                )
                frontOffset.animateTo(
                    0f,
                    animationSpec = tween(2250, easing = FastOutSlowInEasing)
                )
                delay(4500)
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(900)
                backRotationX.animateTo(
                    5f.dp.toPx(),
                    animationSpec = tween(2250, easing = FastOutSlowInEasing)
                )
                backRotationX.animateTo(
                    -5f.dp.toPx(),
                    animationSpec = tween(2250, easing = FastOutSlowInEasing)
                )
                delay(3600)
            }
        }
    }

    GlassCard(
        modifier = modifier
            .semantics { contentDescription = "Scan ID card, both sides on one page" }
            .role(Role.Button)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        strong = true
    ) {
        Box(Modifier.fillMaxSize()) {
            // Mini white sheet behind the cards
            Box(
                Modifier
                    .size(100.dp, 116.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .shadow(10.dp, RoundedCornerShape(6.dp), clip = false, ambientColor = DesignBrand.copy(alpha = 0.26f), spotColor = DesignBrand.copy(alpha = 0.13f))
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // FRONT card
                Box(
                    Modifier
                        .size(78.dp, 46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DesignBrand)
                        .shadow(5.dp, RoundedCornerShape(6.dp), clip = false, ambientColor = DesignBrand.copy(alpha = 0.35f), spotColor = DesignBrand.copy(alpha = 0.17f))
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .offset { IntOffset(0, frontOffset.value.toInt()) }
                        .graphicsLayer { rotationZ = frontRotation.value },
                    contentAlignment = Alignment.Center
                ) {
                    CardDetailsFront()
                }

                // BACK card
                Box(
                    Modifier
                        .size(78.dp, 46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF17142B))
                        .shadow(5.dp, RoundedCornerShape(6.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.17f))
                        .align(Alignment.TopCenter)
                        .padding(top = 62.dp)
                        .graphicsLayer { rotationX = backRotationX.value }
                        .cameraDistance(8f.dp.toPx()),
                    contentAlignment = Alignment.Center
                ) {
                    CardDetailsBack()
                }

                // Amber "1 page" sticker
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 60.dp, end = 8.dp)
                        .background(DesignAccent)
                        .clip(RoundedCornerShape(50.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .graphicsLayer { rotationZ = 6f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "1 page",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = DesignInk
                    )
                }
            }

            // Caption
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Both sides, one page",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = DesignInk,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Print-ready, real card size",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DesignMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CardDetailsFront(modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier.size(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) { /* chip */ }
        Spacer(Modifier.width(6.dp).height(14.dp))
        Box(
            Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        )
        Spacer(Modifier.width(6.dp).height(4.dp))
        Box(
            Modifier.width(24.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "FRONT",
            style = MaterialTheme.typography.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CardDetailsBack(modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) { /* chip */ }
        Spacer(Modifier.width(6.dp).height(8.dp))
        Box(
            Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        )
        Spacer(Modifier.width(6.dp).height(8.dp))
        Box(
            Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "BACK",
            style = MaterialTheme.typography.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Promo card: Digital Locker (lock badge + copy chip)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PromoCardLocker(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduce = rememberReduceMotion()
    var pulse by remember { Animatable(0f) }

    if (!reduce) {
        LaunchedEffect(Unit) {
            while (true) {
                pulse.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
                delay(200)
            }
        }
    }

    GlassCard(
        modifier = modifier
            .semantics { contentDescription = "Open Digital Locker" }
            .role(Role.Button)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        strong = true
    ) {
        Box(Modifier.fillMaxSize()) {
            // Lock badge
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(DesignBrandLight, DesignBrand),
                            start = Alignment.Companion.TopLeft,
                            end = Alignment.Companion.BottomRight
                        )
                    )
                    .shadow(8.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = DesignBrand.copy(alpha = 0.30f), spotColor = DesignBrand.copy(alpha = 0.15f))
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .graphicsLayer {
                        val ring = 12f.dp.toPx() * pulse.value
                        scaleX = 1f + 0.05f * pulse.value
                        scaleY = 1f + 0.05f * pulse.value
                    },
                contentAlignment = Alignment.Center
            ) {
                LockIcon(Modifier.size(28.dp), Color.White)
            }

            // Copy chip (illustration only — masked number, never real data)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp)
                    .background(Color.White.copy(alpha = 0.92f))
                    .clip(RoundedCornerShape(50.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, spacing = 6.dp) {
                    Text(
                        "Aadhaar •••• •••• 1234",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = DesignChipText
                    )
                    CopyChipIcon(Modifier.size(12.dp), DesignChipText)
                }
            }

            // Caption
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Locked on your phone",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = DesignInk,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Copy ID numbers in one tap",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = DesignMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small decorative icons
// ---------------------------------------------------------------------------------------------

@Composable
private fun CameraIcon(modifier: Modifier = Modifier, color: Color) {
    Box(modifier) {
        // Simplified camera glyph
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(color.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        )
    }
}

@Composable
private fun CopyChipIcon(modifier: Modifier = Modifier, color: Color) {
    Box(modifier) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.width(8.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(color.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        )
    }
}

@Composable
private fun FlipIcon(modifier: Modifier = Modifier, color: Color) {
    Box(modifier) {
        // Simple flip/document icon
        Box(
            Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// FlipScreen placeholder: home screen needs BackHandler for consistency
// ---------------------------------------------------------------------------------------------

/**
 * The home screen is the root; BackHandler is not needed here since there's nothing to pop to.
 * Kept for structural consistency with other screens.
 */
