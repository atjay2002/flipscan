package app.flipscan

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.flipscan.data.LockerDocType
import app.flipscan.data.LockerStore
import app.flipscan.data.Mode
import app.flipscan.data.Scan
import app.flipscan.data.ScanStore
import app.flipscan.data.Work
import app.flipscan.export.Exporter
import app.flipscan.ui.BusyOverlay
import app.flipscan.ui.DoubleSideScreen
import app.flipscan.ui.FlipScanTheme
import app.flipscan.ui.FolderScreen
import app.flipscan.ui.HomeScreen
import app.flipscan.ui.LockerGateScreen
import app.flipscan.ui.LockerHomeScreen
import app.flipscan.ui.LockerViewerScreen
import app.flipscan.ui.PagesScreen
import app.flipscan.ui.RegularScreen
import app.flipscan.ui.ResultScreen
import app.flipscan.ui.motionMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How long one successful unlock stays valid while the app is in the foreground. */
private const val LOCKER_SESSION_MS = 5 * 60 * 1000L

sealed interface Screen {
    data object Home : Screen
    data object DoubleSide : Screen
    data object Regular : Screen
    data object Pages : Screen
    data class Folder(val folderId: String) : Screen
    /** [fromFolder] is where Back returns to, so editing inside a folder does not drop to Home. */
    data class Result(val scanId: String, val fromFolder: String? = null) : Screen

    // Digital Locker. Every one of these is covered by FLAG_SECURE.
    data object LockerGate : Screen
    data object LockerHome : Screen
    data object LockerCard : Screen
    data object LockerDoc : Screen
    data object LockerPages : Screen
    data class LockerViewer(val id: String) : Screen
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlipScanTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val regularPages = remember { mutableStateListOf<File>() }
    val lockerPages = remember { mutableStateListOf<File>() }
    var saving by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val fadeInMs = motionMs(220)
    val fadeOutMs = motionMs(150)
    val slideMs = motionMs(280)

    // Session state is in memory only: a process death or activity recreation locks the locker.
    var lockerUnlocked by remember { mutableStateOf(false) }
    var lockerUntil by remember { mutableLongStateOf(0L) }
    var lockerType by remember { mutableStateOf(LockerDocType.AADHAAR) }

    fun lockLocker() {
        val wasInLocker = screen.inLocker()
        lockerUnlocked = false
        lockerUntil = 0L
        lockerPages.clear()
        if (wasInLocker) {
            screen = Screen.Home
            // Plaintext capture files and decrypted crop copies must not outlive the session.
            // If an import is still running, it removes its own inputs when it finishes.
            val clearWork = !saving
            scope.launch(Dispatchers.IO) {
                LockerStore.clearTemps(ctx)
                if (clearWork) Work.clear(ctx)
            }
        }
    }

    fun lockerActive(): Boolean =
        lockerUnlocked && SystemClock.elapsedRealtime() < lockerUntil

    /**
     * Leaving a locker capture before saving must not leave plaintext pages in the work folder:
     * ordinary scans may leave scratch files behind, locker captures may not.
     */
    fun discardLockerCapture() {
        lockerPages.clear()
        scope.launch(Dispatchers.IO) { Work.clear(ctx) }
    }

    // A process that died during a locker scan leaves plaintext scratch and encrypted orphans.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Work.clear(ctx)
            LockerStore.clearTemps(ctx)
            LockerStore.clearExportTemps(ctx)
            LockerStore.sweep(ctx)
        }
    }

    // Backgrounding the app ends the session immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (lockerUnlocked) lockLocker()
    }

    // Absolute session cap, even while the user keeps the locker open.
    LaunchedEffect(lockerUnlocked, lockerUntil) {
        if (!lockerUnlocked) return@LaunchedEffect
        while (true) {
            delay(1_000)
            if (SystemClock.elapsedRealtime() >= lockerUntil) {
                lockLocker()
                break
            }
        }
    }

    // Screenshots and screen recording are blocked only while a locker screen is on top.
    val secureScreen = screen.inLocker()
    LaunchedEffect(secureScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        if (secureScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            // The outgoing locker screen is still fading; keep it protected until it is gone.
            delay(400)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Screens cross-fade and nudge horizontally in the direction of travel.
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
                (fadeIn(tween(fadeInMs)) + slideInHorizontally(tween(slideMs)) { it / 16 * dir }) togetherWith
                    (fadeOut(tween(fadeOutMs)) + slideOutHorizontally(tween(slideMs)) { -it / 16 * dir })
            },
            label = "screens"
        ) { current ->
            when (current) {
                Screen.Home -> HomeScreen(
                    onDoubleSide = { Work.clear(ctx); screen = Screen.DoubleSide },
                    onRegular = { Work.clear(ctx); regularPages.clear(); screen = Screen.Regular },
                    onEdit = { screen = Screen.Result(it.id) },
                    onOpenFolder = { screen = Screen.Folder(it.id) },
                    onLocker = {
                        if (lockerActive()) {
                            screen = Screen.LockerHome
                        } else {
                            lockerUnlocked = false
                            screen = Screen.LockerGate
                        }
                    }
                )
                is Screen.Folder -> FolderScreen(
                    folderId = current.folderId,
                    onBack = { screen = Screen.Home },
                    onEdit = { screen = Screen.Result(it.id, fromFolder = current.folderId) }
                )
                Screen.DoubleSide -> DoubleSideScreen(
                    onBack = { screen = Screen.Home },
                    onDone = { files ->
                        if (!saving && files.size == 2) {
                            saving = true
                            scope.launch {
                                val scan = finishScan(ctx, Mode.CARDS, files)
                                saving = false
                                screen = Screen.Result(scan.id)
                            }
                        }
                    }
                )
                Screen.Regular -> RegularScreen(
                    regularPages,
                    onDone = { screen = Screen.Pages },
                    onBack = { screen = Screen.Home }
                )
                Screen.Pages -> PagesScreen(
                    regularPages,
                    onAddMore = { screen = Screen.Regular },
                    onSave = {
                        if (!saving && regularPages.isNotEmpty()) {
                            saving = true
                            val files = regularPages.toList()
                            scope.launch {
                                val scan = finishScan(ctx, Mode.DOCUMENT, files)
                                regularPages.clear()
                                saving = false
                                screen = Screen.Result(scan.id)
                            }
                        }
                    }
                )
                is Screen.Result -> ResultScreen(
                    current.scanId,
                    onBack = {
                        screen = current.fromFolder?.let { Screen.Folder(it) } ?: Screen.Home
                    }
                )

                Screen.LockerGate -> LockerGateScreen(
                    onUnlocked = {
                        lockerUnlocked = true
                        lockerUntil = SystemClock.elapsedRealtime() + LOCKER_SESSION_MS
                        screen = Screen.LockerHome
                    },
                    onBack = { screen = Screen.Home }
                )
                Screen.LockerHome -> LockerHomeScreen(
                    onBack = {
                        discardLockerCapture()
                        screen = Screen.Home
                    },
                    onScan = { type ->
                        if (lockerActive()) {
                            lockerType = type
                            Work.clear(ctx)
                            lockerPages.clear()
                            screen = if (type.cards) Screen.LockerCard else Screen.LockerDoc
                        } else {
                            lockerUnlocked = false
                            screen = Screen.LockerGate
                        }
                    },
                    onOpen = {
                        if (lockerActive()) {
                            screen = Screen.LockerViewer(it)
                        } else {
                            lockerUnlocked = false
                            screen = Screen.LockerGate
                        }
                    },
                    onLock = { lockLocker() }
                )
                Screen.LockerCard -> DoubleSideScreen(
                    onBack = {
                        discardLockerCapture()
                        screen = Screen.LockerHome
                    },
                    allowSingleSide = true,
                    title = lockerType.label,
                    subtitle = "Saved encrypted in the locker",
                    onDone = { files ->
                        if (!saving && files.isNotEmpty()) {
                            saving = true
                            scope.launch {
                                val doc = runCatching {
                                    withContext(Dispatchers.IO) { LockerStore.import(ctx, lockerType, files) }
                                }.getOrNull()
                                saving = false
                                val stillActive = lockerActive()
                                when {
                                    doc != null && stillActive -> screen = Screen.LockerViewer(doc.id)
                                    doc != null || !stillActive -> withContext(Dispatchers.IO) {
                                        // Locked mid-save, or nothing was written: no plaintext may survive.
                                        Work.clear(ctx)
                                        LockerStore.clearTemps(ctx)
                                    }
                                    else -> Toast.makeText(
                                        ctx,
                                        "Could not save to the locker. Nothing was changed.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
                Screen.LockerDoc -> RegularScreen(
                    lockerPages,
                    title = lockerType.label,
                    onDone = { screen = Screen.LockerPages },
                    onBack = {
                        discardLockerCapture()
                        screen = Screen.LockerHome
                    }
                )
                Screen.LockerPages -> PagesScreen(
                    lockerPages,
                    onAddMore = { screen = Screen.LockerDoc },
                    onSave = {
                        if (!saving && lockerPages.isNotEmpty()) {
                            saving = true
                            val files = lockerPages.toList()
                            scope.launch {
                                val doc = runCatching {
                                    withContext(Dispatchers.IO) { LockerStore.import(ctx, lockerType, files) }
                                }.getOrNull()
                                saving = false
                                val stillActive = lockerActive()
                                when {
                                    doc != null && stillActive -> {
                                        lockerPages.clear()
                                        screen = Screen.LockerViewer(doc.id)
                                    }
                                    doc != null || !stillActive -> {
                                        withContext(Dispatchers.IO) {
                                            Work.clear(ctx)
                                            LockerStore.clearTemps(ctx)
                                        }
                                        lockerPages.clear()
                                    }
                                    else -> Toast.makeText(
                                        ctx,
                                        "Could not save to the locker. Nothing was changed.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
                is Screen.LockerViewer -> LockerViewerScreen(
                    docId = current.id,
                    onBack = { screen = Screen.LockerHome },
                    onDeleted = { screen = Screen.LockerHome },
                    onLock = { lockLocker() }
                )
            }
        }

        // Moving pages into a saved scan (or encrypting a locker document) takes a moment.
        AnimatedVisibility(
            visible = saving,
            enter = fadeIn(tween(fadeInMs)),
            exit = fadeOut(tween(fadeOutMs))
        ) {
            BusyOverlay(
                if (screen.inLocker()) "Encrypting document…" else "Saving scan…",
                Modifier.fillMaxSize()
            )
        }
    }
}

/** Moves the working files into a new scan and makes its list thumbnail, off the main thread. */
private suspend fun finishScan(ctx: Context, mode: Mode, files: List<File>): Scan =
    withContext(Dispatchers.IO) {
        val scan = ScanStore.create(ctx, mode, files)
        runCatching { Exporter.writeThumb(ctx, scan) } // a missing thumbnail is not fatal
        scan
    }

private fun Screen.inLocker(): Boolean = when (this) {
    Screen.LockerGate, Screen.LockerHome, Screen.LockerCard,
    Screen.LockerDoc, Screen.LockerPages -> true
    is Screen.LockerViewer -> true
    else -> false
}

/** Navigation depth, used to decide the direction of the screen transition. */
private fun depth(s: Screen): Int = when (s) {
    Screen.Home -> 0
    Screen.DoubleSide, Screen.Regular, is Screen.Folder, Screen.LockerGate -> 1
    Screen.Pages, Screen.LockerHome -> 2
    is Screen.Result, Screen.LockerCard, Screen.LockerDoc -> 3
    Screen.LockerPages -> 4
    is Screen.LockerViewer -> 5
}
