# Agent 4 — Scanning Performance and QA Audit

## Repository Revision Inspected
- Branch: main
- Commit: 7ac29e3
- Files reviewed:
  - app/src/main/java/app/flipscan/scan/ScanCamera.kt
  - app/src/main/java/app/flipscan/scan/PageProcessor.kt
  - app/src/main/java/app/flipscan/scan/Detector.kt
  - app/src/main/java/app/flipscan/scan/FrameAnalyzer.kt
  - app/src/main/java/app/flipscan/scan/Sidecar.kt
  - app/src/main/java/app/flipscan/scan/Geometry.kt
  - app/src/main/java/app/flipscan/util/Bitmaps.kt
  - app/src/main/java/app/flipscan/data/PageEditor.kt
  - app/src/main/java/app/flipscan/ui/CropScreen.kt
  - app/src/main/java/app/flipscan/ui/DoubleSideScreen.kt
  - app/src/main/java/app/flipscan/ui/RegularScreen.kt
  - app/src/main/java/app/flipscan/ui/PageThumb.kt
  - app/src/main/java/app/flipscan/export/Exporter.kt
  - app/src/main/java/app/flipscan/export/SheetRenderer.kt (PageLoader + SheetRenderer)
  - app/src/main/java/app/flipscan/export/Filters.kt
  - app/src/main/java/app/flipscan/data/ScanStore.kt
  - app/src/main/java/app/flipscan/data/Work.kt

## Findings

### PERF-001 — Compose state written from a background analysis thread

| Field | Description |
|---|---|
| Finding ID | PERF-001 |
| Category | Performance |
| Severity | Informational |
| Status | Confirmed (works by design, but fragile) |
| File | app/src/main/java/app/flipscan/scan/FrameAnalyzer.kt (line 43) and app/src/main/java/app/flipscan/scan/ScanCamera.kt (lines 66, 122, 143) |
| Evidence | `FrameAnalyzer.analyze()` runs on `analysisExecutor` (`Executors.newSingleThreadExecutor()`, ScanCamera.kt line 122) and invokes `onResult(LiveResult(...))` at FrameAnalyzer.kt line 43. That callback writes `live = r` into a Compose `mutableStateOf` (ScanCamera.kt line 66) from the background thread. Recomposition is avoided because consumers read `state.live` inside the Canvas draw lambda (`CornerOverlay`, ScanCamera.kt lines 261-315) or via `derivedStateOf` (`LiveStatus`, DoubleSideScreen.kt line 259 / RegularScreen.kt line 232), matching the AGENTS.md guidance ("Read cam.live in draw/lambda scope"). |
| Impact | No observable jank today. However, writing Compose mutable state off the main thread is a fragile pattern: any future consumer that reads `live` directly in a Compose `@Composable` (rather than draw/derivedState scope) would recompose on every camera frame (~10 analyses/sec after the 1-in-3 throttle), causing main-thread jank. |
| Recommendation | Keep the draw-scope/derivedState pattern. Add a comment at FrameAnalyzer.kt line 43 and ScanCamera.kt line 66 documenting that `onResult` / `live` may be invoked from the analysis thread and must only be read in draw-scope or derivedStateOf. Consider wrapping the state write in `withContext(Dispatchers.Main)` if that contract cannot be guaranteed by future callers. |
| Verification | Compose recomposition count (Layout Inspector / Compose compiler metrics): `CornerOverlay` and `LiveStatus`/`RegularStatus` must not recompose once per analysis frame; only the steady-state animation should drive redraws. Profile on a mid-range phone per docs/TESTING.md §37. |

### PERF-002 — Legacy rotate fallback decodes full-res bitmap without sampling (OOM risk)

| Field | Description |
|---|---|
| Finding ID | PERF-002 |
| Category | Performance |
| Severity | Medium |
| Status | Potential (triggers only on legacy scans without a stored `.orig` sidecar) |
| File | app/src/main/java/app/flipscan/data/PageEditor.kt lines 44-61 |
| Evidence | `PageEditor.rotateInPlace()` (legacy fallback for scans without a stored original) calls `BitmapFactory.decodeFile(page.path)` at line 45 with no `inSampleSize`, then `Bitmap.createBitmap(...)` at line 48 to produce a rotated copy, then compresses back to JPEG at line 54. For a full-res page (up to ~8 MP, ~35 MB ARGB_8888) the source bitmap plus the rotated copy can simultaneously occupy ~70 MB of Java heap. Bitmaps are recycled in the `finally` block (lines 59-60), but the peak allocation happens before recycling. |
| Impact | On low-end devices (e.g. 3 GB RAM), rotating a legacy full-resolution page could trigger `OutOfMemoryError`. Newer scans are unaffected because `PageEditor.rotate()` (lines 33-40) re-warps from the stored downscaled original (lossless, low memory). |
| Recommendation | Add `BitmapFactory.Options` with an `inSampleSize` (or `inTargetDensity`/`inScaled`) to `rotateInPlace` so the source decode is capped (e.g. longest side ≤ 1600 px, matching `CropScreen`'s `decodeSampled(Sidecar.orig(page), 1600)` at CropScreen.kt line 96), rotate the sampled bitmap, then re-encode at full quality. This reduces peak heap from ~70 MB to a few MB with negligible quality loss for a 90° rotation. |
| Verification | On a 3 GB RAM device, open a legacy scan (no `.orig.jpg` sidecar) and tap Rotate; confirm no OOM/crash and memory returns to baseline after. Compare heap before/after via Android Studio Memory Profiler. |

### PERF-003 — Peak native memory during PageProcessor capture pipeline

| Field | Description |
|---|---|
| Finding ID | PERF-003 |
| Category | Performance |
| Severity | Medium |
| Status | Potential |
| File | app/src/main/java/app/flipscan/scan/PageProcessor.kt lines 27-41, 95-111 |
| Evidence | `PageProcessor.process()` reads the full-resolution capture with `Imgcodecs.imread(raw)` (line 27) into an OpenCV `Mat` (BGR, 3 bytes/px → ~24 MB for 8 MP). `render()` → `Detector.warp()` (line 103) produces a second full-res warped `Mat` before `resized()` (line 130) downsizes to `MAX_SIDE` 3508 and releases the warped Mat. For a moment `src` (full-res) and `warped` (full-res measured size) can coexist natively (~40-50 MB native). Mats are native allocations, not Java heap, so they do not trigger Java `OutOfMemoryError` directly but can exhaust native memory on low-end devices. `ImageCapture` is capped at ~8 MP target (3264×2448, ScanCamera.kt lines 127-132) but that is a resolution *hint*, not a hard cap (see PERF-008). |
| Impact | On memory-constrained devices, concurrent native allocations during warp could cause an OpenCV/native allocation failure, failing the capture. Output is correctly capped by `limit()` (3508 max side, PageProcessor.kt line 124), but the intermediate warp is full-measurement size. |
| Recommendation | Downsize the source to `MAX_SIDE` before warping when the source exceeds `MAX_SIDE`, or warp directly into the target `fw × fh` size rather than warping at measured size then resizing. This keeps only one full-res (or near-full-res) Mat alive at a time, consistent with the AGENTS.md constraint ("Only one full-resolution bitmap should be alive at a time"). |
| Verification | Android Studio Memory Profiler (native heap) during a single capture: confirm only one large native allocation is live at peak, and that it returns to baseline after `process()` completes. Test on a 3 GB RAM phone capturing the largest supported resolution. |

### PERF-004 — Exporter.render has no enforcement that it runs off the main thread

| Field | Description |
|---|---|
| Finding ID | PERF-004 |
| Category | Performance |
| Severity | High |
| Status | Potential (depends on caller, not enforced) |
| File | app/src/main/java/app/flipscan/export/Exporter.kt lines 21-23, 44-55, 151-182 |
| Evidence | `Exporter.render()` is documented ("Everything here is heavy: call from a background dispatcher", line 21) but performs no thread enforcement. `renderBitmap()` (line 44) allocates a full-resolution `Bitmap.createBitmap(...)` (line 46) — for A4 at 300 dpi this is ~2480×3508 ≈ 35 MB ARGB_8888 — on whichever thread invokes `render()`. If a caller invokes it on the main thread, this single allocation plus `Canvas` work would cause jank/ANR and a large main-thread heap spike. The AGENTS.md states previews rerender after every settings change with a 120 ms debounce; the caller must dispatch off-main. |
| Impact | A future or existing caller invoking `Exporter.render()`/`previews()` on the main thread would block the UI during filter + PDF/bitmap rendering, causing dropped frames or ANR, especially with filters (Filters.kt runs OpenCV on the bitmap). |
| Recommendation | Add `check(CoroutineDispatcher.isMainThread) == false` is not available directly; instead document the contract and, ideally, have `render()`/`previews()` internally `withContext(Dispatchers.Default)` or require the caller to do so. Confirm every caller (Settings-driven preview debounce, ResultScreen export, Locker export) launches off the main thread. |
| Verification | Confirm all callers of `Exporter.render`/`previews`/`writeThumb` wrap the call in `withContext(Dispatchers.Default)` or `Dispatchers.IO`. Thread-check via Studio CPU profiler: main thread must stay idle during export/preview render. |

### PERF-005 — Detector.findQuad allocates working Mats on every analysis frame without pooling

| Field | Description |
|---|---|
| Finding ID | PERF-005 |
| Category | Performance |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/scan/Detector.kt lines 38-42, 79-82 |
| Evidence | `findQuad()` allocates `blurred`, `edges`, `kernel`, `hierarchy` Mats (lines 38-42) on every invocation, plus one `MatOfPoint2f` per contour (line 57) and one `MatOfPoint2f`/`approx` per epsilon attempt (lines 60-63). All are released in the `finally` block (lines 79-82), but allocation/release happens on every analyzed frame (every 3rd camera frame, ~10×/second). The analysis size is constant (~400 px max side, `Detector.ANALYSIS_SIZE`), so these Mats could be pooled/reused across frames. |
| Impact | Minor native-allocation and GC pressure each analysis frame. Not enough to cause jank by itself, but adds CPU work that could be eliminated, contributing to overall detection CPU load. |
| Recommendation | Pool the fixed-size working Mats (`blurred`, `edges`, `kernel`) as `ThreadLocal` or instance fields reused across `findQuad` calls, since the analysis dimensions are stable. Release them when the analyzer is cleared. Keep per-contour temporaries as-is (they vary in count/size). |
| Verification | CPU profiler (Android Studio) during camera scanning: `Detector.findQuad` should show reduced allocation frequency after pooling. Confirm no change in detection quality (quad found rate unchanged). |

### PERF-006 — FrameAnalyzer.toSmallUprightGray creates multiple intermediate Mats per frame

| Field | Description |
|---|---|
| Finding ID | PERF-006 |
| Category | Performance |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/scan/FrameAnalyzer.kt lines 54-82 |
| Evidence | `toSmallUprightGray()` allocates `full` (line 66), `cropped` (submat, line 68 — shares data, cheap), `small` (resize, line 70), and `out` (rotate/copyTo, lines 73-79) on every analyzed frame. The `buffer` ByteArray is correctly reused as a field (line 31), avoiding per-frame garbage. `full` is sized `(h, rowStride)` which can exceed `(h, w)` due to row-padding, then `submat` drops the padding — minor inefficiency. All Mats are released (line 80). |
| Impact | Several native allocations per analyzed frame (~10×/second). Lightweight but avoidable; adds up on low-end CPUs. |
| Recommendation | Combine the resize and rotate into fewer steps where possible (e.g. resize directly to the analysis size, then rotate). The current pipeline is acceptable; optimize only if profiling shows `toSmallUprightGray` as a hotspot. |
| Verification | CPU profiler during scanning: confirm `toSmallUprightGray` is not a top consumer. No functional change required unless profiling indicates otherwise. |

### PERF-007 — AutoCaptureEffect runs a polling loop on the main thread (lightweight)

| Field | Description |
|---|---|
| Finding ID | PERF-007 |
| Category | Performance |
| Severity | Informational |
| Status | Confirmed (by design) |
| File | app/src/main/java/app/flipscan/scan/ScanCamera.kt lines 327-368 |
| Evidence | `AutoCaptureEffect` uses `LaunchedEffect` (default Main dispatcher) with a `while(true) { delay(80); ... }` loop (lines 335-367). It only reads `state.live` (Compose state) and calls `SystemClock.elapsedRealtime()` and `state.updateSteady()` — no bitmap or OpenCV work. It triggers `trigger()` → `shoot()` → `cam.takePicture()` (CameraX, main thread). All work per poll is O(1) state reads. |
| Impact | Negligible main-thread cost (a few state reads every 80 ms). Intended design; no recomposition because `steady`/`live` are read in draw/derivedState scopes elsewhere. |
| Recommendation | No change needed. Keep the polling loop free of any image processing. |
| Verification | CPU profiler: the AutoCaptureEffect loop must not appear as a main-thread hotspot. |

### PERF-008 — ImageCapture resolution is a hint, not a hard cap

| Field | Description |
|---|---|
| Finding ID | PERF-008 |
| Category | Performance |
| Severity | Low/Medium |
| Status | Potential |
| File | app/src/main/java/app/flipscan/scan/ScanCamera.kt lines 124-133 |
| Evidence | `ImageCapture.Builder().setResolutionSelector(... ResolutionStrategy(Size(3264, 2448), FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))` targets ~8 MP (line 130) per the comment "~8 MP is plenty for A4 at 300 dpi and keeps memory sane on mid-range phones." CameraX resolution strategies are hints; on some devices the selected size may exceed 3264×2448 if no closer-lower size is supported. `PageProcessor.imread` would then load a larger-than-expected Mat (see PERF-003). |
| Impact | If a device returns a much larger capture (e.g. 12+ MP), `imread` + `warp` native memory could exceed expectations on low-end devices, raising OOM/allocation-failure risk. |
| Recommendation | After `imread`, check `src.cols()/src.rows()` and downscale to `MAX_SIDE` (3508) before detection/warp if exceeded. This bounds worst-case memory regardless of the camera's selected resolution. |
| Verification | On devices with high-resolution cameras, confirm `PageProcessor.process` peak native memory stays bounded and capture succeeds. |

---

## Areas Reviewed (checklist)

- [x] CameraX use cases (Preview, ImageAnalysis, ImageCapture) — separate use cases confirmed (ScanCamera.kt lines 114-143); `unbindAll` then `bindToLifecycle` (line 216-219).
- [x] Frame processing rate control (every 3rd frame, KEEP_ONLY_LATEST) — Confirmed: `frameCount++ % 3 != 0` (FrameAnalyzer.kt line 35); `STRATEGY_KEEP_ONLY_LATEST` (ScanCamera.kt line 142).
- [x] OpenCV detection on background thread (Dispatchers.Default/IO) — Confirmed: `analysisExecutor` single-thread (ScanCamera.kt line 122); `PageProcessor.process`/`recrop` invoked via `withContext(Dispatchers.Default)` (RegularScreen.kt line 103, DoubleSideScreen.kt line 125, CropScreen.kt line 336).
- [x] Bitmap sampling and recycling (decodeSampled) — Confirmed: `decodeSampled` uses `inSampleSize` (Bitmaps.kt lines 26-32); `PageLoader.load` uses `inSampleSize` + `createScaledBitmap` + `recycle` (SheetRenderer.kt lines 22-36); `Filters.apply` recycles `src` (Filters.kt line 28); `Exporter` recycles per-sheet bitmap (Exporter.kt lines 176-178).
- [x] Single full-resolution bitmap alive at a time — Confirmed by design in Exporter (`render` processes one sheet at a time, line 169 comment) and `limit()` cap (PageProcessor.kt line 124). Partial risk in legacy `rotateInPlace` (PERF-002) and PageProcessor intermediate Mats (PERF-003).
- [x] Camera lifecycle management (binding/unbinding) — Confirmed: `DisposableEffect` onDispose unbinds + `clearLive` (ScanCamera.kt lines 223-228); `close()` detaches observers, clears analyzer, shuts down executor (lines 169-173); `observeForever` observers removed in `detachCamera` (lines 106-112). No leak detected.
- [x] Image buffer closing (ImageAnalysis, ImageCapture) — Confirmed: `image.close()` in `finally` (FrameAnalyzer.kt line 50); `gray.release()` (line 45); all `Detector` Mats released in `finally` blocks (Detector.kt lines 79-82, 114-116, 132-134, 149); `PageProcessor` `src.release()` / `page?.release()` in `finally` (lines 38-41, 52-55).
- [x] Crop and perspective correction performance — Confirmed: `Detector.warp` uses `warpPerspective` with `INTER_CUBIC` (Detector.kt line 148); `render` warps at measured size then shrinks with `INTER_AREA` (PageProcessor.kt lines 95-111); `PageEditor.rotate` re-warps from stored original (lossless) rather than re-encoding (PageEditor.kt lines 33-40).
- [x] Rotation (lossless re-warp vs lossy re-encode) — Confirmed: `PageEditor.rotate()` uses lossless re-warp when `.orig` exists (lines 35-40); `rotateInPlace()` is the lossy fallback (lines 44-61) — see PERF-002.
- [x] Loupe rendering during corner drag — Confirmed: CropScreen loupe reuses the already-loaded `image` (`asImageBitmap`), no per-drag decode (CropScreen.kt lines 164, 222-280); decode happens once off-main via `decodeSampled(..., 1600)` (line 96).
- [x] Memory pressure on large documents — Partial: `ImageCapture` ~8 MP target, `decodeSampled`/`PageLoader` downsample, `limit()` caps output to 3508 side. Risks remain in legacy rotate (PERF-002), PageProcessor peak native Mats (PERF-003), and non-hard resolution cap (PERF-008).
- [x] Main thread usage during capture/processing — Confirmed: detection/warp/filters/PDF/export all dispatched off-main (`Dispatchers.Default`/`IO`). CameraX callbacks (`onImageSaved`, `takePicture`) run on `mainExecutor` but are lightweight. `FrameAnalyzer` writes Compose state from background thread (PERF-001, handled by draw-scope reads).

## Conclusion

Scanning performance is well architected and largely consistent with the intentional performance constraints in AGENTS.md:

- CameraX uses separate Preview / ImageAnalysis / ImageCapture use cases with `KEEP_ONLY_LATEST` and a 1-in-3 frame analysis throttle on a dedicated background executor.
- All heavy OpenCV work (detection, warp, filters) and all bitmap/PDF work run off the main thread via `Dispatchers.Default`/`IO`.
- Bitmap sampling (`inSampleSize`) and recycling are consistently applied; only one full-resolution bitmap is intended to be alive at a time.
- Camera lifecycle and image-buffer closing are correctly managed — no resource leaks detected.
- The loupe and crop editor reuse a single downsampled bitmap with no per-drag decode.

There are **no confirmed Critical or High-severity defects**. The two Medium-severity findings are conditional: the legacy full-res rotate fallback (PERF-002) and PageProcessor peak native memory (PERF-003). The High-severity finding (PERF-004) is a potential risk dependent on callers dispatching `Exporter.render` off the main thread — this must be verified but is not a defect in `Exporter` itself.

**Release readiness: Ready, with caveats.** Before release, verify on a mid-range/3 GB RAM device per docs/TESTING.md §34-38: (1) memory returns to baseline after capture and export; (2) main thread stays idle during detection/export; (3) `Exporter.render` callers dispatch off the main thread; (4) legacy rotate on a memory-constrained device does not OOM. Address PERF-002 and PERF-003 if testing shows memory pressure.
