# FlipScan release rules.
# The app has no reflection of its own; the rules below protect the two libraries that do.

# OpenCV is entered from native code (JNI looks classes and methods up by name) and is loaded
# with System.loadLibrary("opencv_java4"). Keep the whole bridge, not only the public API.
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }
-dontwarn org.opencv.**

# CameraX resolves several camera2 / extensions classes by name at runtime.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Annotations are read at runtime by CameraX and Compose tooling.
-keepattributes *Annotation*

# Coroutines ships an optional debug agent that is not present on Android.
-dontwarn kotlinx.coroutines.**
