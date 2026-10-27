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
import app.flipscan.data.Work
import app.flipscan.ui.DoubleSideScreen
import app.flipscan.ui.HomeScreen

sealed interface Screen {
    data object Home : Screen
    data object DoubleSide : Screen
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
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (screen) {
        Screen.Home -> HomeScreen(
            onDoubleSide = { Work.clear(ctx); screen = Screen.DoubleSide },
            onRegular = { Toast.makeText(ctx, "Regular mode arrives in milestone 5", Toast.LENGTH_SHORT).show() }
        )
        Screen.DoubleSide -> DoubleSideScreen(
            onBack = { screen = Screen.Home },
            onDone = { _, _ ->
                Toast.makeText(ctx, "Both sides captured (merging comes in milestone 4)", Toast.LENGTH_LONG).show()
                screen = Screen.Home
            }
        )
    }
}
