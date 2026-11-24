# FlipScan

Scan **both sides** of a card (Aadhaar, PAN, driving licence, voter ID, college ID) onto **one page**, at real size if you like. Also scans regular multi-page documents.

- 100% offline: no INTERNET permission, no ads, no analytics, no cloud
- Scans stay in app-private storage until you export
- Sensitive documents stay encrypted, behind your phone's own screen lock
- MIT licensed

## Features
- **Digital Locker**: a separate, authenticated space for sensitive documents (Aadhaar, PAN, passport, driving licence, voter ID, or a custom document). It reuses the same camera and crop flow, but every page, crop corner and thumbnail is encrypted with AES-256-GCM under a key held in Android Keystore before it reaches storage. Unlocking uses the system BiometricPrompt with the phone's PIN/pattern/password as the fallback, so FlipScan never sees or stores a credential. The locker re-locks when the app goes to the background, after 5 minutes, and on process death; its screens block screenshots (`FLAG_SECURE`), and locker documents never appear in Recent scans, folders or Downloads.
- **Double Side mode**: FRONT and BACK slots laid out like the final page; the live preview slides from slot to slot; tap a slot to retake; standard card size 85.6 x 54 mm.
- **Regular mode**: many pages, free-form edge detection and crop, reorder and delete.
- **Detection (OpenCV)**: downscaled frames, every 3rd frame, background thread, latest-frame-only; grayscale, blur, Canny, largest quadrilateral, perspective warp on the full-resolution photo. Corner overlay, auto-capture after ~1 s steady, blur and glare warnings.
- **Flash / torch**: continuous torch toggle on both camera screens, driven by CameraX `cameraControl.enableTorch`. The button mirrors the camera's real `TorchState`, and is disabled with an explanation when the camera has no flash unit.
- **Output**: A4 / A5 / Letter / custom; stacked or side by side; margins, cut line, real size or fit; filters (original, grayscale, B&W, magic color); PDF / JPG / PNG; compress to 200 KB / 500 KB / 1 MB.
- **Orientation**: page orientation Auto / Portrait / Landscape, and a Rotate button per page (cards stay correctly shaped when rotated).
- **Per-page edit**: Rotate and manual Crop (drag 4 corners, perspective corrected) for every page. The original photo is kept, downscaled, in app-private storage so a crop can be redone. While you drag a corner, a loupe (2.4x, crosshair, drawn from the same in-memory bitmap) shows exactly where it will land.
- **Design**: custom Material 3 theme (indigo/teal, light and dark), reusable components, hand-drawn icons, short purposeful animations that follow the system "remove animations" setting. A restrained glass layer (translucent fills, a top sheen, fine two-tone borders, selected floating surfaces) is drawn with plain gradients — no backdrop blur, no extra dependency — so scrolling lists stay cheap. Camera screens stay dark for preview contrast.
- **Saved scans**: tap a scan for Open / Edit / Rename / Share / Move / Delete. Open shows the exact PDF or image already saved to Downloads (no re-export); if there is none yet it offers "Save & open". Names are editable, and the export file name is chosen separately (with a live preview) and never overwrites an existing file.
- **Folders**: create, rename and delete folders, move scans in and out from a scan's menu, with per-folder counts. Folder membership is metadata only — deleting a folder never deletes a scan or its files.
- **Save and share**: Downloads/FlipScan via MediaStore, or the system share sheet.

## Screenshots
Add yours to `docs/screenshots/` (none are committed yet).

## Build
Requires JDK 17+ and the Android SDK (or open in Android Studio).

macOS / Linux / Git Bash:
```
./gradlew assembleDebug
```

Windows (the repo ships only the POSIX `gradlew`, there is no `gradlew.bat`):
```
bash gradlew assembleDebug
```

Outputs:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (ARM + `x86_64` for emulators)
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (ARM only)
- App Bundle: `app/build/outputs/bundle/release/app-release.aab`

Release builds enable R8 minification and resource shrinking (`app/proguard-rules.pro` keeps
`org.opencv.**` for JNI and `androidx.camera.**` for runtime-resolved classes). The bundle splits
native libraries per ABI, so Play delivers only what each device needs. Release APKs are unsigned;
configure your own signing key locally and never commit it.

`adb install` rejects the unsigned APK with `INSTALL_PARSE_FAILED_NO_CERTIFICATES`. To test a release
build on a device, sign it locally first (any keystore works for local testing):
```
apksigner sign --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out app-release-test.apk app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r app-release-test.apk
```

### Measured sizes (version 0.3.0, `assembleDebug`, `assembleRelease`, `bundleRelease`)
| Artifact | Before | After |
| --- | --- | --- |
| Debug APK | 102,806,416 B (98.0 MiB) | 103,153,947 B (98.4 MiB) |
| Release APK | 100,095,429 B (95.5 MiB, unminified) | 38,738,513 B (36.9 MiB) |
| Release AAB | 46,335,564 B (44.2 MiB, unminified) | 19,474,865 B (18.6 MiB) |

The native OpenCV libraries are the dominant cost and are already trimmed to the ABIs the app
supports (`arm64-v8a`, `armeabi-v7a`; `x86_64` only in debug).

Version 0.5.0 (Digital Locker) measures 103,778,252 B debug (+771 KB) and 39,161,602 B signed
release (+287 KB), including the `androidx.biometric` library.

## Project layout
- `scan/`: CameraX state (incl. torch), frame analyzer, OpenCV detector, page processor
- `export/`: layout maths, sheet renderer, filters, exporter (PDF/JPG/PNG), share
- `data/`: settings, scan storage, Downloads writer, `LockerCrypto` (Keystore AES-GCM) and `LockerStore` (encrypted locker index and documents)
- `ui/`: Compose screens, theme tokens, shared components, custom icons, locker gate/home/viewer

## Digital Locker (Phase 1)
- Storage: `filesDir/locker/index.bin` (encrypted JSON index) and `filesDir/locker/docs/<uuid>/`
  holding `p_i.jpg.enc`, `p_i.orig.jpg.enc`, `p_i.quad.enc` and `thumb.jpg.enc`. Filenames expose only
  a random UUID and the page count; names, types and dates live inside the encrypted index.
- Encryption: one AES-256-GCM key generated in Android Keystore (alias `flipscan_locker_v1`), a fresh
  random IV per record, envelope `FSLK1 | ivLen | iv | ciphertext+tag`. The GCM tag rejects tampered
  or truncated files. The key is not bound to biometrics, so enrolling a new fingerprint cannot
  destroy stored documents; the locker is gated by the system prompt at the app level instead.
- Writes: every record goes to a `.tmp` name and is renamed into place; an import creates its
  directory first, then the index, and only then deletes the plaintext capture files. A failed save
  rolls back; an interrupted one is cleaned up by `LockerStore.sweep()` on the next start (it deletes
  nothing when the index itself cannot be read).
- Plaintext copies exist only while a crop or rotate is in progress, and only under
  `noBackupFilesDir/locker_tmp`; they are removed in a `finally`, on lock, and at start-up.
- No export or share of locker documents in this version: both would create plaintext copies.
- Backups: `android:allowBackup="false"`, and nothing under `filesDir/locker` is exposed by FileProvider.

## Known limitations
- Portrait only. The 0.3.0 UI redesign, the 0.4.0 crop loupe, folders, rename and Open/Edit flows, the 0.4.1 glass visual layer, and the 0.5.0 Digital Locker compile and pass lint, but have not been exercised on a physical device yet: see `docs/TESTING.md` for the test and profiling checklist.
- PdfDocument stores images losslessly, so PDF size limits are met by lowering resolution.
- Blur/glare thresholds are first guesses and need tuning on real phones.
- Digital Locker is Phase 1: it stores and edits encrypted scans. It does not read document numbers yet, so there is nothing to mask or verify; Digital Pins (document-number reveal/copy) and OCR are Phase 2. Locker documents also cannot be exported or shared yet, by design.
- Device-credential fallback, lockout and enrolment-change behaviour depend on the phone's biometric stack and can only be confirmed on hardware.
- Not built yet: Aadhaar masking, watermark, whole-app lock, Tamil/Hindi.
- R8 full mode is left at the AGP default; if a release-only crash appears on a device, check the keep rules in `app/proguard-rules.pro` first.

See [SPEC.md](SPEC.md) for the full plan. License: [MIT](LICENSE).
