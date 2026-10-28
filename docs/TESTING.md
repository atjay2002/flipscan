# Testing and profiling on a real phone

## Functional checklist
1. First launch: camera permission prompt; deny, then use "Open app settings".
2. Double Side: front captured, preview slides to BACK slot, back captured, "Done" appears.
3. Tap a slot to retake it. Check the old photo is replaced.
4. Auto-capture: hold a card still ~1 s. It must NOT capture the same side twice; flip the card first.
5. Blur / glare hints appear (shiny laminated card under a lamp).
6. Result: switch A4/A5/Letter/Custom, stacked/side by side, real size vs fit, margin, cut line. Print at 100% and measure the card: 85.6 x 54 mm.
7. Filters, formats (PDF/JPG/PNG), size limits (200 KB / 500 KB). Check the toast when a limit cannot be reached.
8. Save to Downloads (Downloads/FlipScan), Share to WhatsApp/Gmail.
9. Regular mode: capture 3+ pages, reorder, delete one, add another, Continue, export.
10. Home: rename, delete, share from the recent list.
11. Android 7-9 only: saving asks for storage permission.

## Tunable values (detection)
- `Thresholds.BLUR_MIN`, `Thresholds.GLARE_MAX` in `scan/Detector.kt`
- Steady window (1 s) and movement tolerance (2%) in `AutoCaptureEffect` (`scan/ScanCamera.kt`)
- Card aspect tolerance in `Detector.looksLikeCard`

## Profiling (Android Studio)
- Profiler > CPU: analysis thread should stay well below the camera frame rate; main thread idle while capturing.
- Profiler > Memory: after a capture and after export, native + Java heap should return to baseline (only one full-res image alive at a time).
- Developer options > Profile GPU rendering: the slot animation should stay under the 16 ms line.
- Layout Inspector / Compose recomposition counts: `DoubleSideContent` must not recompose on every camera frame (only `LiveStatus` should).
- Try an A4 export with filters on a 3 GB RAM phone; PDF export of 10+ regular pages is the heaviest case.
