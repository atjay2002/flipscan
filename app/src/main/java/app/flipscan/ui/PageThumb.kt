package app.flipscan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small image loaded in the background (downsampled), so lists stay smooth. */
@Composable
fun PageThumb(file: File, modifier: Modifier = Modifier, maxSide: Int = 200) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSide) }
    }
    Box(modifier.background(Color(0xFF2A3036)), contentAlignment = Alignment.Center) {
        bmp?.let { b ->
            val image = remember(b) { b.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
