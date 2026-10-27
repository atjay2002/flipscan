# FlipScan — Specification

## Goal
A document scanner that puts BOTH SIDES of small cards (Aadhaar, PAN, DL, voter ID, college ID) on ONE page. Existing apps make two pages; this fixes that. Offline, no ads, open source.

## Tech
- Kotlin, Jetpack Compose, min SDK 24, single module
- CameraX (Preview + ImageAnalysis + ImageCapture as separate use cases)
- OpenCV for Android for detection and perspective correction (no ML model for now)
- Android PdfDocument for PDF export, MediaStore for saving
- NO INTERNET permission, no analytics, no ads, no cloud

## Two modes (home screen: two big buttons + recent scans list)
1. Double Side mode (cards)
   - One screen with two slots: FRONT (top) and BACK (bottom), laid out like the final page
   - Live camera preview shows in the active slot; after front is captured it locks in and the preview moves to the back slot, with a smooth animation
   - Tap a slot to retake it; "Done" appears when both are filled
   - Assume standard card size 85.6 x 54 mm, so output can print at real size
2. Regular mode (passbooks, certificates, any document)
   - Capture one or many pages, free-form edge detection and crop
   - Each capture becomes its own page in the output; allow reorder and delete

## Detection (OpenCV)
- Analyze a downscaled frame (about 320-480 px), every 2nd or 3rd frame, on a background executor
- Use STRATEGY_KEEP_ONLY_LATEST so frames never queue up
- Grayscale, blur, Canny, find the largest quadrilateral contour, order the 4 corners, warpPerspective on the full-res capture
- Draw a corner overlay on the preview
- Auto-capture when the card is detected and steady for about 1 second; manual capture button always available
- Warn on blur (Laplacian variance) and glare

## Output and settings (shared by both modes)
- Paper size: A4, A5, Letter, custom
- Layout for Double Side: stacked vertically (default) or side by side; margins; optional cut line; real size or fit to page
- Filters: original, grayscale, black and white, enhanced/magic color
- Export: PDF, JPG, PNG, plus "compress to target size" (e.g. under 200 KB or 500 KB)
- Share sheet and save to Downloads via MediaStore

## Performance (must run smoothly on mid-range phones)
- Never do heavy work on the main thread; heavy steps (warp, filters, PDF) run in the background after capture with a progress indicator
- Show downscaled previews, apply filters on full-res only at export
- Never hold several full-res bitmaps in memory; downsample and recycle
- Animate only transforms (translate, scale, alpha); keep the camera surface stable and avoid recomposition while it animates
- Save captures to app storage early

## Privacy
- Scans stored in app-private storage, not the public gallery, until the user exports
- All processing on-device

## Milestones
1. Project setup, CameraX preview, capture button, permission handling
2. OpenCV integration, edge detection overlay, perspective crop
3. Double Side mode UI: two slots, flow, animations
4. Merge to a single page with paper size and layout options, PDF export
5. Regular mode (multi-page)
6. Filters, JPG/PNG export, compress to target size
7. Recent scans list, share, rename, delete
8. Polish, profiling on a mid-range device, README with screenshots and an MIT license

## Later (do NOT build yet)
Aadhaar masking (hide first 8 digits), watermark option, app lock, Tamil/Hindi, dark mode polish.

## Working rules
- Ask before adding any dependency not listed above
- Keep code simple and readable; short comments where logic is non-obvious
- After each milestone, list what to test on a real phone
