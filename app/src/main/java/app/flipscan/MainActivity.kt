package app.flipscan

import android.os.Bundle
import android.widget.Toast
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
import app.flipscan.ui.ResultScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object DoubleSide : Screen
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
    var saving by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onDoubleSide = { Work.clear(ctx); screen = Screen.DoubleSide },
            onRegular = { Toast.makeText(ctx, "Regular mode arrives in milestone 5", Toast.LENGTH_SHORT).show() }
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
