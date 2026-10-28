# FlipScan

Scan **both sides** of a card (Aadhaar, PAN, driving licence, voter ID, college ID) onto **one page**, at real size if you like. Also scans regular multi-page documents.

- 100% offline: no INTERNET permission, no ads, no analytics, no cloud
- Scans stay in app-private storage until you export
- MIT licensed

## Features
- **Double Side mode**: FRONT and BACK slots laid out like the final page; the live preview slides from slot to slot; tap a slot to retake; standard card size 85.6 x 54 mm.
- **Regular mode**: many pages, free-form edge detection and crop, reorder and delete.
- **Detection (OpenCV)**: downscaled frames, every 3rd frame, background thread, latest-frame-only; grayscale, blur, Canny, largest quadrilateral, perspective warp on the full-resolution photo. Corner overlay, auto-capture after ~1 s steady, blur and glare warnings.
- **Output**: A4 / A5 / Letter / custom; stacked or side by side; margins, cut line, real size or fit; filters (original, grayscale, B&W, magic color); PDF / JPG / PNG; compress to 200 KB / 500 KB / 1 MB.
- **Save and share**: Downloads/FlipScan via MediaStore, or the system share sheet. Recent scans with rename, delete, share.

## Screenshots
Add yours to `docs/screenshots/` (none are committed yet).

## Build
Requires JDK 17+ and the Android SDK (or open in Android Studio).
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Project layout
- `scan/`: CameraX state, frame analyzer, OpenCV detector, page processor
- `export/`: layout maths, sheet renderer, filters, exporter (PDF/JPG/PNG), share
- `data/`: settings, scan storage, Downloads writer
- `ui/`: Compose screens

## Known limitations
- Portrait only. Not yet compiled or run on a device by the author of this commit: see `docs/TESTING.md` for the test and profiling checklist.
- PdfDocument stores images losslessly, so PDF size limits are met by lowering resolution.
- Blur/glare thresholds are first guesses and need tuning on real phones.
- Not built yet: Aadhaar masking, watermark, app lock, Tamil/Hindi, dark mode polish.

See [SPEC.md](SPEC.md) for the full plan. License: [MIT](LICENSE).
