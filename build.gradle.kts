plugins {
    // AGP 8.10 bundles R8 8.10.21, the first R8 that understands Kotlin 2.2 metadata.
    id("com.android.application") version "8.10.1" apply false
    // CameraX 1.7 ships Kotlin 2.2 metadata, so the compiler must be at least that new.
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
