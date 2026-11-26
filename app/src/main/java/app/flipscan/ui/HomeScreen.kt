package app.flipscan.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flipscan.data.Folder
import app.flipscan.data.FolderStore
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    // The redesigned dashboard is a fixed light surface. Pinning the light colour scheme here keeps
    // every Material-based child (scan rows, sheets, dialogs) light instead of mixing a dark card
    // into the light page when the system theme is dark. Dark-mode polish is deferred.
    FlipScanTheme(darkTheme = false) {
        HomeScreenContent(onDoubleSide, onRegular, onEdit, onOpenFolder, onLocker)
    }
}

@Composable
private fun HomeScreenContent(
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
    ) {
        LightSurfaceSystemBars()
        GlowBackground(Modifier.fillMaxSize(), enabled = true)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item { HomeHeader() }

            item { Spacer(Modifier.height(8.dp)) }

            item { HomeHeading() }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignPaddingHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PromoCardBothSides(
                        onClick = onDoubleSide,
                        modifier = Modifier.weight(1f)
                    )
                    PromoCardLocker(
                        onClick = onLocker,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(Modifier.height(14.dp)) }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignPaddingHorizontal)
                ) {
                    PrimaryButton(
                        onClick = onDoubleSide,
                        icon = { CameraIcon(Modifier.size(26.dp), Color.White) },
                        title = "Scan ID card",
                        subtitle = "Aadhaar, PAN, DL, voter ID"
                    )
                }
            }

            item { Spacer(Modifier.height(14.dp)) }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignPaddingHorizontal),
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
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignPaddingHorizontal)
                        .padding(top = 16.dp, bottom = 6.dp)
                ) {
                    SectionHeader(
                        title = "Recent scans",
                        link = "New folder",
                        onLinkClick = { creatingFolder = true }
                    )
                }
            }

            if (!loaded) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else if (scans.isEmpty()) {
                item { EmptyRecentState() }
            } else {
                items(scans, key = { it.id }) { scan ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignPaddingHorizontal)
                            .padding(bottom = 10.dp)
                    ) {
                        ScanRow(
                            scan = scan,
                            folderName = folders.firstOrNull { it.id == scan.folderId }?.name,
                            onActions = { actionsFor = scan }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            item { HomeFooter() }
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
// Home sections
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignPaddingHorizontal)
            .padding(top = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
                color = DesignInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OfflinePill(text = "Works offline")
        }
    }
}

@Composable
private fun HomeHeading() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignPaddingHorizontal)
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

@Composable
private fun EmptyRecentState() {
    val reduce = rememberReduceMotion()
    val flipRotation = remember { Animatable(0f) }

    LaunchedEffect(reduce) {
        if (reduce) {
            flipRotation.snapTo(0f)
        } else {
            while (true) {
                flipRotation.animateTo(180f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
                flipRotation.animateTo(360f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
                delay(2000)
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignPaddingHorizontal)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            strong = true
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { rotationY = flipRotation.value },
                    contentAlignment = Alignment.Center
                ) {
                    FlipArrowIcon(
                        modifier = Modifier.size(28.dp),
                        color = DesignBrand,
                        label = null
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Scan your first ID",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = DesignInk
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Saved scans stay on this phone.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = DesignMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeFooter() {
    Text(
        "Private by default. Shared only by you.",
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        ),
        color = DesignMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignPaddingHorizontal)
    )
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

    val frontAlpha = remember { Animatable(0f) }
    val frontOffset = remember { Animatable(24f) }
    val backAlpha = remember { Animatable(0f) }
    val backRotationX = remember { Animatable(-80f) }
    val floatOffset = remember { Animatable(0f) }

    LaunchedEffect(reduce, lowRam) {
        if (reduce || lowRam) {
            frontAlpha.snapTo(1f)
            frontOffset.snapTo(0f)
            backAlpha.snapTo(1f)
            backRotationX.snapTo(0f)
        } else {
            launch {
                frontAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                frontOffset.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
            }
            delay(120)
            launch {
                backAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                backRotationX.animateTo(0f, animationSpec = tween(800, easing = FastOutSlowInEasing))
            }
            launch {
                while (true) {
                    floatOffset.animateTo(-5f, animationSpec = tween(2200, easing = FastOutSlowInEasing))
                    floatOffset.animateTo(0f, animationSpec = tween(2200, easing = FastOutSlowInEasing))
                    delay(2000)
                }
            }
        }
    }

    GlassCard(
        modifier = modifier
            .heightIn(min = 188.dp)
            .semantics {
                contentDescription = "Scan ID card, both sides on one page"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        strong = true
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Illustration area (fixed height; the caption below grows with font scale) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                contentAlignment = Alignment.Center
            ) {
                // Mini white sheet holding the FRONT card above the BACK card.
                Box(
                    Modifier
                        .size(96.dp, 108.dp)
                        .graphicsLayer { translationY = floatOffset.value }
                        .shadow(
                            6.dp,
                            RoundedCornerShape(6.dp),
                            clip = false,
                            ambientColor = DesignBrand.copy(alpha = 0.22f),
                            spotColor = DesignBrand.copy(alpha = 0.10f)
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // FRONT (brand purple) on top.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = frontAlpha.value
                                    translationY = frontOffset.value
                                }
                                .clip(RoundedCornerShape(4.dp))
                                .background(DesignBrand),
                            contentAlignment = Alignment.Center
                        ) {
                            CardDetailsFront()
                        }
                        // BACK (#17142B) below.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = backAlpha.value
                                    rotationX = backRotationX.value
                                    cameraDistance = 8f.dp.toPx()
                                }
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF17142B)),
                            contentAlignment = Alignment.Center
                        ) {
                            CardDetailsBack()
                        }
                    }
                }

                // "1 page" sticker at the top-end of the illustration area, never over the caption.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(DesignAccent)
                        .clip(RoundedCornerShape(50.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
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

            // ── Caption block at the bottom ──
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Both sides, one page",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = DesignInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Print-ready, real card size",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DesignMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CardDetailsFront() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(9.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.40f))
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Box(
                    Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.55f))
                )
            }
        }
        Text(
            "FRONT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 8.sp
            ),
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CardDetailsBack() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Box(
                Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.85f))
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }
        Text(
            "BACK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 8.sp
            ),
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
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(reduce) {
        if (reduce) {
            pulse.snapTo(0f)
        } else {
            while (true) {
                pulse.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
            }
        }
    }

    GlassCard(
        modifier = modifier
            .heightIn(min = 188.dp)
            .semantics {
                contentDescription = "Open Digital Locker"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        strong = true
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Illustration area: lock badge, then the masked number chip ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(DesignBrandLight, DesignBrand),
                                    start = androidx.compose.ui.geometry.Offset.Zero,
                                    end = androidx.compose.ui.geometry.Offset(50f, 50f)
                                )
                            )
                            .graphicsLayer {
                                scaleX = 1f + 0.05f * pulse.value
                                scaleY = 1f + 0.05f * pulse.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LockIcon(Modifier.size(26.dp), Color.White)
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(
                        Modifier
                            .background(Color.White.copy(alpha = 0.92f))
                            .clip(RoundedCornerShape(50.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Aadhaar •••• 1234",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.1.sp
                                ),
                                color = DesignChipText,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(Modifier.width(4.dp))
                            CopyIcon(Modifier.size(11.dp), DesignChipText)
                        }
                    }
                }
            }

            // ── Caption block at the bottom ──
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Locked on your phone",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = DesignInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Copy ID numbers in one tap",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = DesignMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White)) { }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(color.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) { }
    }
}
