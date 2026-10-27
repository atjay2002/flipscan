# FlipScan

Scan **both sides** of a card (Aadhaar, PAN, driving licence, voter ID, college ID) onto **one page**, at real size if you like. Also scans regular multi-page documents.

- 100% offline: no internet permission, no ads, no analytics, no cloud
- Scans stay in app-private storage until you export
- Open source (MIT, added in milestone 8)

## Status
Milestone 2 of 8: OpenCV edge detection overlay, auto/manual capture, perspective crop.
See [SPEC.md](SPEC.md) for the full plan.

## Build
Requires JDK 17+ and the Android SDK (or just open in Android Studio).

```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
