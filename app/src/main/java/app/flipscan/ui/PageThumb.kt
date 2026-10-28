package app.flipscan.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.flipscan.util.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small image loaded in the background (downsampled), fading in when ready, so lists stay smooth. */
@Composable
fun PageThumb(
    file: File,
    modifier: Modifier = Modifier,
    maxSide: Int = 200,
    placeholder: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSide) }
    }
    val alpha by animateFloatAsState(
        targetValue = if (bmp != null) 1f else 0f,
        animationSpec = tween(motionMs(180)),
        label = "thumbAlpha"
    )
    Box(modifier.background(placeholder)) {
        bmp?.let { b ->
            val image = remember(b) { b.asImageBitmap() }
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Fit
            )
        }
    }
}
