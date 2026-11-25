# Agent 3 — Export, Sharing and File Storage Audit

## Repository Revision Inspected
- Branch: main (working tree, no git available in this environment)
- Commit: not determinable (no git binary); revision inspected: working tree as of 2026-11-25
- Files reviewed:
  - app/src/main/java/app/flipscan/export/Exporter.kt
  - app/src/main/java/app/flipscan/export/Share.kt
  - app/src/main/java/app/flipscan/export/Viewer.kt
  - app/src/main/java/app/flipscan/export/SheetRenderer.kt
  - app/src/main/java/app/flipscan/export/Filters.kt
  - app/src/main/java/app/flipscan/export/Layouts.kt
  - app/src/main/java/app/flipscan/data/Downloads.kt
  - app/src/main/java/app/flipscan/data/ScanStore.kt
  - app/src/main/java/app/flipscan/data/LockerStore.kt
  - app/src/main/java/app/flipscan/data/LockerCrypto.kt
  - app/src/main/java/app/flipscan/data/LockerOcr.kt
  - app/src/main/java/app/flipscan/data/PinFormat.kt
  - app/src/main/java/app/flipscan/data/Naming.kt
  - app/src/main/java/app/flipscan/data/Settings.kt
  - app/src/main/java/app/flipscan/data/Work.kt
  - app/src/main/java/app/flipscan/util/Bitmaps.kt
  - app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt
  - app/src/main/java/app/flipscan/ui/ResultScreen.kt
  - app/src/main/java/app/flipscan/ui/ScanActions.kt
  - app/src/main/java/app/flipscan/MainActivity.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/xml/file_paths.xml

## Findings

| Field | Description |
|---|---|
| Finding ID | EXP-001 |
| Category | Storage |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 174–183 |
| Evidence | `decryptPagesToTemp()` writes decrypted plaintext JPEG pages to `ctx.cacheDir/locker_export/<docId>/page_i.jpg`. `cacheDir` is app-private but is **not** the no-backup directory; on some devices/backup configurations app cache can be included in auto-backup or inspected by root/adb shell run-as. The comment on LockerStore (lines 77–80) explicitly states decrypted copies should be "only ever written under `noBackupFilesDir/locker_tmp`", but `decryptPagesToTemp` bypasses that and uses `cacheDir`. |
| Impact | Sensitive locker page plaintext may persist outside the encrypted index and outside the designated no-backup temp area, increasing exposure if the device is backed up, rooted, or if the app process is killed before cleanup runs. |
| Recommendation | Redirect `decryptPagesToTemp` to write under `ctx.noBackupFilesDir/locker_export/<docId>/` (same location used by `stageForEdit`/`clearTemps`), or reuse `LockerStore.stageForEdit`/`commitEdit` patterns. Ensure app `android:allowBackup="false"` is set (it is, manifest line 25) but do not rely on it alone. |
| Verification | Run `adb shell run-as app.flipscan ls cache/locker_export/` after a locker export; the directory should not exist or should be empty immediately after export completes. Also test process kill mid-export and confirm no plaintext remains on restart. |

| Field | Description |
|---|---|
| Finding ID | EXP-002 |
| Category | Export / File cleanup |
| Severity | High |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/export/Exporter.kt |
| Lines | 76–77 |
| Evidence | `exportDir()` calls `deleteRecursively(); mkdirs()` on `cacheDir/exports` at the start of every render. Any export files from a previous render that have **not yet been copied to Downloads or shared** are silently deleted. The normal flows (ResultScreen.export, ResultScreen.share, ScanActions.saveAndOpen, ScanActions.share, LockerViewerScreen.export, LockerViewerScreen.share) call `render`/`renderPages` then immediately save/share, so the window is small, but it is not atomic: if the user triggers a second export while the first share sheet is still open, the first export's files vanish from under the active `FileProvider` URI grant. |
| Impact | An in-progress share can fail or deliver missing/incomplete files if a second export reuses the same export directory. Interrupted or aborted second renders leave the first export's files deleted with no recovery. |
| Recommendation | Use a per-render subdirectory (e.g. `cacheDir/exports/<uuid>/`) or write to a temp file then rename, so previous exports remain valid until the new render is complete and the previous URI grants have expired. Clean old export directories with a bounded age sweep rather than `deleteRecursively` on every render. |
| Verification | Trigger an export/share, keep the share sheet open, then start a second export; confirm the first share URI still resolves and the first file is intact. |

| Field | Description |
|---|---|
| Finding ID | EXP-003 |
| Category | Export failure / interrupted export |
| Severity | Medium |
| Status | Potential |
| File | app/src/main/java/app/flipscan/export/Exporter.kt |
| Lines | 165, 174, 219, 227 |
| Evidence | Exported bytes are written directly to the final file name via `File(...).also { it.writeBytes(bytes) }`. There is no temp-file-then-rename pattern. If the process is killed or the disk fills between `writeBytes` starting and completing, a truncated/partial export file remains in `cacheDir/exports/`. On the next render, `exportDir()` deletes the whole directory (EXP-002), so the partial file is removed — but only at the next render, and only if a new render occurs. A stale partial file can sit in cache until then. |
| Impact | Incomplete exports left in cache; a user who browses `cache/exports` (root/adb) or a backup that includes cache could see partial plaintext exports. No data loss for the user (source scans are untouched), but a failed export leaves artifacts. |
| Recommendation | Write each export to `name.ext.tmp` and `renameTo` the final name only after `writeBytes` succeeds; delete `.tmp` leftovers at render start. |
| Verification | Simulate a low-disk or kill-during-export scenario; confirm no `.pdf`/`.jpg`/`.png` partial files remain in `cache/exports` after restart, only `.tmp` remnants that are cleaned. |

| Field | Description |
|---|---|
| Finding ID | EXP-004 |
| Category | Export failure / memory |
| Severity | High |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/export/Exporter.kt |
| Lines | 57–74 |
| Evidence | `pdfBytes()` builds the **entire** multi-page PDF into a single `ByteArrayOutputStream` in memory before returning the byte array. `PdfDocument` stores pictures losslessly, so a large multi-page document (e.g. 10+ A4 pages at 200 DPI with filters) can require a large heap allocation. `TESTING.md` line 38 identifies "PDF export of 10+ regular pages" as the heaviest case and suggests testing on a 3 GB RAM phone. There is no streaming to disk. |
| Impact | Risk of `OutOfMemoryError` or process kill on large multi-page PDF exports, particularly on low-RAM devices. This can leave an incomplete export (EXP-003) and fail the export entirely. |
| Recommendation | Stream the PDF to a temp file page-by-page (write each `PdfDocument` page to a `FileOutputStream` incrementally) instead of accumulating all pages in a `ByteArrayOutputStream`. Keep one page's bitmap in memory at a time, consistent with the "only one full-resolution bitmap alive" performance constraint. |
| Verification | Export a 10+ page A4 document with a filter and `targetKb = 0` on a low-RAM device/emulator; confirm no OOM and memory returns to baseline after export. |

| Field | Description |
|---|---|
| Finding ID | EXP-005 |
| Category | Storage |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 228–232, 269–272 |
| Evidence | Both `export()` and `share()` clean up plaintext temp files in a `finally` block: `pages.forEach { it.delete() }` then `pages.firstOrNull()?.parentFile?.deleteRecursively()`. The temp dir is `cacheDir/locker_export/<docId>/`. Cleanup runs even when `Exporter.renderPages` or `Downloads.save`/`Share.send` throws. However (see EXP-001), the temp dir is under `cacheDir`, not `noBackupFilesDir`. |
| Impact | Plaintext temp files are reliably removed on normal completion and on exceptions, but only if the process stays alive. A process kill between decryption and the `finally` leaves plaintext in cache until the next export (which calls `exportDir.deleteRecursively()` first) or forever if no further export occurs. |
| Recommendation | Move the temp dir to `noBackupFilesDir` (EXP-001) and add a startup sweep of `locker_export` alongside `LockerStore.clearTemps()`/`sweep()` in `MainActivity` (line 141–147). |
| Verification | Kill the process mid-export; on restart confirm `cache/locker_export/` (or its relocated path) is empty. |

| Field | Description |
|---|---|
| Finding ID | EXP-006 |
| Category | Storage isolation |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml; app/src/main/res/xml/file_paths.xml |
| Lines | Manifest 29–37; file_paths 1–7 |
| Evidence | FileProvider authority is `app.flipscan.fileprovider`, `android:exported="false"`, `android:grantUriPermissions="true"`. `file_paths.xml` exposes only two paths: `cache-path name="exports" path="exports/"` and `external-path name="downloads" path="Download/FlipScan/"`. The locker directory (`filesDir/locker`, `noBackupFilesDir/locker_tmp`, `cacheDir/locker_export`) is **not** exposed through FileProvider, consistent with LockerStore's comment (line 79: "Nothing here is reachable through FileProvider"). |
| Impact | Locker encrypted files and decrypted temp pages cannot be served to other apps via content URI. Only exports in `cache/exports` and copies in `Download/FlipScan` are shareable, which is correct isolation. |
| Recommendation | None required; keep locker paths out of `file_paths.xml`. |
| Verification | Attempt to construct a `FileProvider.getUriForFile` for a locker `.enc` file; confirm it throws `IllegalArgumentException`. |

| Field | Description |
|---|---|
| Finding ID | EXP-007 |
| Category | Storage / Content URI permissions |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/export/Share.kt; app/src/main/java/app/flipscan/export/Viewer.kt; app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | Share.kt 13–20; Viewer.kt 16–20; LockerViewerScreen.kt 276–284 |
| Evidence | `Share.send()` grants `FLAG_GRANT_READ_URI_PERMISSION` on each `FileProvider` URI (read-only, transient). `Viewer.open()` grants `FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK` on a parsed `saved.uri`. `LockerViewerScreen.openExport()` (line 278–281) grants `FLAG_GRANT_READ_URI_PERMISSION` but does **not** add `FLAG_ACTIVITY_NEW_TASK`; it relies on `ctx.startActivity(intent)` from an activity context, which is fine in practice. All URIs are content:// URIs; no `file://` URIs are ever constructed (grep confirmed). |
| Impact | URI permissions are scoped correctly: read-only, granted to a specific receiving app via the share/viewer intent, and revoked when the task ends. No broad `file://` exposure. The missing `FLAG_ACTIVITY_NEW_TASK` in `openExport()` is a minor robustness issue only if called from a non-activity context (it is not). |
| Recommendation | Add `FLAG_ACTIVITY_NEW_TASK` to `openExport()` for symmetry with `Viewer.open()` (defensive). |
| Verification | Open a saved export from `ScanActions` and `LockerViewerScreen`; confirm the receiving app can read the file and that no other app can guess the URI. |

| Field | Description |
|---|---|
| Finding ID | EXP-008 |
| Category | Export |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/export/Share.kt |
| Lines | 13, 19 |
| Evidence | `Share.send()` derives the intent MIME type from `files[0]` only (`Downloads.mimeOf(files[0])`). All files in a single export share have the same format (one `Exporter.render`/`renderPages` call produces one format), so this is consistent. For `ACTION_SEND_MULTIPLE` with mixed types it would be inaccurate, but that case cannot occur from current callers. |
| Impact | None in practice. |
| Recommendation | None required. |
| Verification | Share a multi-page JPG export (multiple files) and confirm the share sheet treats them as images. |

| Field | Description |
|---|---|
| Finding ID | EXP-009 |
| Category | Storage isolation |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 207, 259, 458–464 |
| Evidence | Locker export (`export()`) calls `Downloads.save(ctx, res.files)` writing plaintext copies to `Downloads/FlipScan`. `share()` sends those plaintext files to other apps. The UI warns at line 458–464: "Encrypted at rest. Exporting creates a plaintext copy in Downloads/FlipScan; the copy is not protected by the locker." `LockerStore.addExport()` records the plaintext export's name/uri/mime in the encrypted index so the viewer can list "Exported copies" and re-open them. |
| Impact | Users are informed that export breaks the encryption boundary. Exported copies are clearly distinguished: they live in the public Downloads folder, are listed separately under "Exported copies" with MIME type, and the locker UI carries an explicit warning. Normal scan workflows are unaffected because locker export uses a separate code path (`renderPages`, not `render`). |
| Recommendation | Consider reiterating the plaintext warning inside the export confirmation dialog (currently only in the static body text), so the user acknowledges it at the moment of export. |
| Verification | Export a locker document; confirm the file appears in `Downloads/FlipScan` and is readable outside the app, while `files/locker/docs` still contains only `.enc` files. |

| Field | Description |
|---|---|
| Finding ID | EXP-010 |
| Category | Export / Bitmap recycling |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/export/Exporter.kt; app/src/main/java/app/flipscan/export/SheetRenderer.kt; app/src/main/java/app/flipscan/export/Filters.kt |
| Lines | Exporter 44–55, 170–178; SheetRenderer 45–53; Filters 14–34 |
| Evidence | In `render()` (non-PDF), each sheet renders one bitmap, encodes it, and recycles it in a `finally` block (line 176–178). `SheetRenderer.draw()` recycles per-page bitmaps when `recycle=true` (used for bitmaps/previews) and does **not** recycle when `recycle=false` (used for `PdfDocument` canvases that may still read pixels — correct). `Filters.apply()` recycles the source bitmap when a new filtered bitmap is produced. `PageLoader.load()` recycles intermediate scaled bitmaps. |
| Impact | Memory is well managed: only one full-resolution bitmap alive at a time during JPG/PNG multi-page export, and PDF uses `recycle=false` appropriately. No obvious bitmap leaks in the export pipeline. |
| Recommendation | None required. |
| Verification | Profile memory during a 10-page JPG export; confirm heap returns to baseline and only one large bitmap is resident at any instant. |

| Field | Description |
|---|---|
| Finding ID | EXP-011 |
| Category | Export settings persistence |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/Settings.kt; app/src/main/java/app/flipscan/ui/ResultScreen.kt |
| Lines | Settings 58–94; ResultScreen 123–138, 150–153 |
| Evidence | `ExportSettings` (format, filter, targetKb, paper, layout, etc.) is persisted globally in `SharedPreferences` ("flipscan_settings", MODE_PRIVATE) via `SettingsStore.save/load`. `ResultScreen` reloads settings on every change (`update()` saves + `LaunchedEffect` rebuilds previews with 120 ms debounce). Previews are small bitmaps built off-main. |
| Impact | Settings survive process death and apply consistently across normal scans and locker exports (locker viewer reads `SettingsStore.load(ctx)` at export time). No cross-contamination: settings are export-format preferences, not scan data. |
| Recommendation | None required. |
| Verification | Change format/targetKb, kill the app, reopen; confirm the export panel reflects the persisted settings. |

| Field | Description |
|---|---|
| Finding ID | EXP-012 |
| Category | Storage / normal scan workflows |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/ScanStore.kt; app/src/main/java/app/flipscan/data/Downloads.kt |
| Lines | ScanStore 38–42, 101–111; Downloads 33–73 |
| Evidence | Normal scans live in app-private `filesDir/scans/<id>/` (`page_i.jpg`, `meta.json`, `thumb.jpg`) and are never exposed through FileProvider. Exports are copied to `Downloads/FlipScan` via MediaStore (Android 10+) or legacy public directory (Android 7–9), then recorded as `SavedExport` (content URI) in `ScanStore.addExport`. `Downloads.saveLegacy` uses `FileProvider.getUriForFile` for the `external-path` "downloads" entry. `Viewer.open` resolves the saved content URI and checks `Downloads.exists()` before opening. Deleting a scan (`ScanStore.delete`, line 109) removes only app-private files and explicitly does not touch Downloads copies (ScanActions.kt line 280). |
| Impact | Strong isolation: normal scan originals stay app-private; exported copies live in public Downloads and are independently deletable. Delete-scan warning correctly tells users Downloads files are not touched. |
| Recommendation | None required. |
| Verification | Delete a scan that has exports; confirm the export files remain in `Downloads/FlipScan` and `Viewer.open` reports "no longer in Downloads" only if the user deleted them externally. |

## Areas Reviewed (checklist)

- [x] PDF export implementation — Exporter.kt `pdfBytes`/`pdfToTarget`; builds full PDF in `ByteArrayOutputStream` (see EXP-004)
- [x] JPG/PNG export implementation — Exporter.kt `render()` non-PDF branch; per-sheet bitmap with recycle (EXP-010)
- [x] Digital Locker export (decrypt → render → save → cleanup) — LockerViewerScreen.kt `decryptPagesToTemp`/`export`/`share`; temp under `cacheDir` not `noBackupFilesDir` (EXP-001, EXP-005)
- [x] FileProvider authority and exported paths — AndroidManifest.xml `app.flipscan.fileprovider`, exported=false; file_paths.xml exposes only `exports/` and `Download/FlipScan/` (EXP-006)
- [x] Content URI permissions (FLAG_GRANT_READ_URI_PERMISSION) — Share.kt line 20, Viewer.kt line 19, LockerViewerScreen.kt line 280 (EXP-007)
- [x] Share sheet integration — Share.kt `send()`; ResultScreen.share; ScanActions.share (EXP-007, EXP-008)
- [x] Temporary plaintext file cleanup (finally blocks) — LockerViewerScreen export/share finally blocks (EXP-005); EXP-001 concerns location, not cleanup logic
- [x] Export failure and interrupted export handling — `runCatching` in callers; direct `writeBytes` with no tmp-then-rename (EXP-003); exportDir deleteRecursively on every render (EXP-002)
- [x] Multi-page document export (one sheet per page, cards side-by-side) — Exporter.kt `sheets()`; Layouts.kt `cards()`/`document()`; render/sheets handle one-sheet-per-page and two-side cards
- [x] Storage isolation (locker files vs normal scan files) — LockerStore under `filesDir/locker`; ScanStore under `filesDir/scans`; neither exposed via FileProvider (EXP-006, EXP-012)
- [x] Downloads/FlipScan folder usage — Downloads.kt MediaStore + legacy; `Downloads.save`; ScanStore.addExport records URIs (EXP-009, EXP-012)
- [x] Bitmap recycling in export pipeline — SheetRenderer/Filters/Exporter recycle paths (EXP-010)
- [x] Export settings persistence — SettingsStore SharedPreferences; debounced previews (EXP-011)

## Conclusion

The export, sharing, and file storage implementation is **well-designed and largely sound** for release, with strong storage isolation between normal scans (app-private `filesDir/scans`), the Digital Locker (AES-256-GCM encrypted under `filesDir/locker`, never exposed via FileProvider), and exported plaintext copies (public `Downloads/FlipScan` + transient `cacheDir/exports` served through a properly scoped FileProvider). Content URIs are used exclusively (no `file://`), URI grants are read-only and transient, bitmap recycling keeps memory bounded, and temp plaintext files are cleaned in `finally` blocks.

The release-blocking concerns are:

1. **EXP-004 (High)** — `pdfBytes()` accumulates the entire multi-page PDF in a `ByteArrayOutputStream` before writing, risking OOM on large (10+ page) PDF exports, the heaviest case identified in `TESTING.md`. Streaming to disk page-by-page is recommended before release.
2. **EXP-002 (High)** — `exportDir()` deletes the entire `cacheDir/exports` directory on every render, which can invalidate an in-progress share from a previous render. Per-render subdirectories or tmp-then-rename is recommended.
3. **EXP-001 (Medium)** — Locker plaintext export temp files are written to `cacheDir/locker_export/` instead of `noBackupFilesDir/`, contrary to LockerStore's own design contract ("decrypted copies are only ever written under `noBackupFilesDir/locker_tmp`"). Relocating to `noBackupFilesDir` and sweeping at startup closes this gap.
4. **EXP-003 (Medium)** — Exports are written directly to their final name with no tmp-then-rename, so a kill/disk-full mid-write leaves a partial file in cache until the next render cleans the directory.

Normal scan workflows are unaffected by all of these; they are confined to export and locker-export paths. With the above fixes addressed, the export/storage subsystem is ready for release.

Prepared by Agent 3 — Export, Sharing and File Storage Auditor.