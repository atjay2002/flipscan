package app.flipscan

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class FlipScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loads the native OpenCV library bundled in the APK (no network involved).
        if (!OpenCVLoader.initLocal()) Log.e("FlipScan", "OpenCV failed to load")
    }
}
