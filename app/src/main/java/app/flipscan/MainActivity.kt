package app.flipscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.flipscan.data.Mode
import app.flipscan.data.ScanStore
import app.flipscan.data.Work
import app.flipscan.ui.DoubleSideScreen
import app.flipscan.ui.HomeScreen
import app.flipscan.ui.PagesScreen
import app.flipscan.ui.RegularScreen
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import app.flipscan.ui.ResultScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object DoubleSide : Screen
    data object Regular : Screen
    data object Pages : Screen
    data class Result(val scanId: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App() }
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
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onDoubleSide = { Work.clear(ctx); screen = Screen.DoubleSide },
            onRegular = { Work.clear(ctx); regularPages.clear(); screen = Screen.Regular }
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
                        val scan = withContext(Dispatchers.IO) { ScanStore.create(ctx, Mode.DOCUMENT, files) }
                        regularPages.clear()
                        saving = false
                        screen = Screen.Result(scan.id)
                    }
                }
            }
        )
        Screen.DoubleSide -> DoubleSideScreen(
            onBack = { screen = Screen.Home },
            onDone = { front, back ->
                if (!saving) {
                    saving = true
                    scope.launch {
                        val scan = withContext(Dispatchers.IO) { ScanStore.create(ctx, Mode.CARDS, listOf(front, back)) }
                        saving = false
                        screen = Screen.Result(scan.id)
                    }
                }
            }
        )
        is Screen.Result -> ResultScreen(s.scanId, onBack = { screen = Screen.Home })
    }
}
