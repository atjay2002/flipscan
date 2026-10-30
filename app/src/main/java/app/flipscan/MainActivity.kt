package app.flipscan

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.flipscan.ui.PagesScreen
import app.flipscan.ui.RegularScreen
import app.flipscan.ui.ResultScreen
import app.flipscan.ui.motionMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object DoubleSide : Screen
    data object Regular : Screen
    data object Pages : Screen
    data class Folder(val folderId: String) : Screen
    /** [fromFolder] is where Back returns to, so editing inside a folder does not drop to Home. */
    data class Result(val scanId: String, val fromFolder: String? = null) : Screen
}

class MainActivity : ComponentActivity() {
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
    val scope = rememberCoroutineScope()
    val regularPages = remember { mutableStateListOf<File>() }
    var saving by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val fadeInMs = motionMs(220)
    val fadeOutMs = motionMs(150)
    val slideMs = motionMs(280)

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
                    onOpenFolder = { screen = Screen.Folder(it.id) }
                )
                is Screen.Folder -> FolderScreen(
                    folderId = current.folderId,
                    onBack = { screen = Screen.Home },
                    onEdit = { screen = Screen.Result(it.id, fromFolder = current.folderId) }
                )
                Screen.DoubleSide -> DoubleSideScreen(
                    onBack = { screen = Screen.Home },
                    onDone = { front, back ->
                        if (!saving) {
                            saving = true
                            scope.launch {
                                val scan = finishScan(ctx, Mode.CARDS, listOf(front, back))
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
            }
        }

        // Moving pages into a saved scan takes a moment; keep taps out while it happens.
        AnimatedVisibility(
            visible = saving,
            enter = fadeIn(tween(fadeInMs)),
            exit = fadeOut(tween(fadeOutMs))
        ) {
            BusyOverlay("Saving scan…", Modifier.fillMaxSize())
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

/** Navigation depth, used to decide the direction of the screen transition. */
private fun depth(s: Screen): Int = when (s) {
    Screen.Home -> 0
    Screen.DoubleSide, Screen.Regular, is Screen.Folder -> 1
    Screen.Pages -> 2
    is Screen.Result -> 3
}
