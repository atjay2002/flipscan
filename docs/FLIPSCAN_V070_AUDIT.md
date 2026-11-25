# FlipScan v0.7.0 — Consolidated Audit

**Project:** FlipScan  
**Application ID:** `app.flipscan`  
**Revision audited:** Branch `main`, commit `7ac29e3` ("Locker export: PDF/JPG/PNG, Open, Share (0.7.0)")  
**Audit date:** November 25, 2026  
**Auditor:** Multi-agent orchestration (Agents 1–6 per `docs/MULTI_AGENT_ORCHESTRATION.md`)

---

## 1. Executive Summary

FlipScan v0.7.0 is **not ready for Google Play publication** as of November 25, 2026.

The core architecture is sound: AES-256-GCM per-record encryption with fresh IVs, GCM tag verification, on-device bundled ML Kit OCR (no INTERNET permission), strong storage isolation between normal scans and the Digital Locker, `allowBackup=false`, non-exported FileProvider, bitmap recycling, and off-main-thread heavy work.

However, there are **four genuine release blockers** that prevent Play Store upload:

| Blocker | Finding IDs | Evidence |
|---------|-------------|----------|
| `targetSdk = 34` below Play minimum API 36 | REL-001 | `app/build.gradle.kts:14`; Play requires API 36 for new apps/updates since Aug 31, 2026; Nov 1, 2026 extension passed |
| `compileSdk = 34` insufficient for API 36; AGP 8.5.2 max compileSdk is 34 | REL-002, REL-003 | `app/build.gradle.kts:9`; root `build.gradle.kts:2` |
| No release signing configuration | REL-006 / REL-015 | `app/build.gradle.kts:24–34`; no `signingConfig` |
| OOM risk on large multi-page PDF export | EXP-004 | `Exporter.kt:57–74`; entire PDF built in `ByteArrayOutputStream` |

Additional high-priority security hardening items (SEC-001, SEC-002/EXP-001, SEC-008) should be addressed before or shortly after release. Critically, **SEC-001 (bind locker key to biometric) must not be implemented without a key-rewrap migration path**, or it will cause mass data loss on first biometric re-enrollment (SEC-008-style).

No source code was modified during this audit. All work was read-only inspection.

---

## 2. Repository and Architecture Overview

### 2.1 Source layout

```
app/src/main/java/app/flipscan/
  data/          Downloads, FolderStore, LockerCrypto, LockerOcr, LockerStore,
                 Naming, PageEditor, PinFormat, ScanStore, Settings, Work
  export/        Exporter, Filters, Layouts, Share, SheetRenderer, Viewer
  scan/          Detector, FrameAnalyzer, Geometry, PageProcessor, ScanCamera, Sidecar
  ui/            Components, CropScreen, DigitalPins, DoubleSideScreen, FolderScreen,
                 FoldersUi, HomeScreen, Icons, LockerAuth, LockerScreen,
                 LockerViewerScreen, PagesScreen, PageThumb, PermissionGate,
                 RegularScreen, ResultScreen, ScanActions, ScanRow, Theme
  util/          Bitmaps
FlipScanApp.kt
MainActivity.kt
```

### 2.2 Architecture

- Single module `:app`; minSdk 24, compileSdk/targetSdk 34
- Kotlin + Compose Material3, portrait-only
- No DI, ViewModel, Navigation, or Room: screen state is a hand-rolled `sealed interface Screen` in `MainActivity`, with Compose state + coroutines
- OpenCV initialized once in `FlipScanApp` via `OpenCVLoader.initLocal()`
- CameraX: separate Preview / ImageAnalysis / ImageCapture use cases with `KEEP_ONLY_LATEST` and every-3rd-frame analysis
- Export: `Exporter.render()` writes to `cacheDir/exports` (deleted on every render), then `Downloads.save()` copies to public `Downloads/FlipScan`; Share uses FileProvider authority `app.flipscan.fileprovider`
- No `INTERNET` permission is a design invariant

### 2.3 Digital Locker (v0.5.0/v0.6.0/v0.7.0)

- Encrypted index `filesDir/locker/index.bin` + per-doc `docs/<uuid>/` holding `p_i.jpg.enc`, `p_i.orig.jpg.enc`, `p_i.quad.enc`, `thumb.jpg.enc`
- AES-256-GCM key in Android Keystore (alias `flipscan_locker_v1`), fresh IV per record, envelope `FSLK1 | ivLen | iv | ct+tag`
- Unlock via system BiometricPrompt with device-credential fallback; session in memory, locks on `ON_STOP` and after 5 minutes; `FLAG_SECURE` on locker screens
- v0.6.0 added Digital Pins (OCR document numbers, masked by default, reveal/copy/edit)
- v0.7.0 added locker export (PDF/JPG/PNG) and Open/Share of exported copies

---

## 3. Agent Completion Status

| Agent | Scope | Status | Findings | Output File |
|-------|-------|--------|----------|-------------|
| Agent 1 | Digital Locker Security | Complete | 13 (SEC-001–013) | `docs/multi-agent/01_SECURITY_AUDIT.md` |
| Agent 2 | OCR & Digital Pins | Complete | 13 (OCR-001–013) | `docs/multi-agent/02_OCR_DIGITAL_PINS_AUDIT.md` |
| Agent 3 | Export, Sharing & Storage | Complete | 12 (EXP-001–012) | `docs/multi-agent/03_EXPORT_STORAGE_AUDIT.md` |
| Agent 4 | Scanning Performance & QA | Complete | 8 (PERF-001–008) | `docs/multi-agent/04_PERFORMANCE_QA_AUDIT.md` |
| Agent 5 | Android Config & Play Store | Complete | 18 (REL-001–018) | `docs/multi-agent/05_ANDROID_RELEASE_AUDIT.md` |
| Agent 6 | Independent Security & Quality Reviewer | Complete | Duplicates, refutations, gaps | `docs/multi-agent/06_INDEPENDENT_REVIEW.md` |

All six agents operated in read-only mode. No production source code, Gradle configuration, dependencies, encryption formats, or user files were modified.

---

## 4. Confirmed Findings

Finding IDs use the specialist prefixes: SEC- (security), OCR- (OCR/Digital Pins), EXP- (export/storage), PERF- (performance), REL- (release). Agent 6 verified each high-impact finding against source code and recorded its assessment.

### 4.1 Release blockers (confirmed)

| ID | Severity | File | Lines | Evidence | Impact | Recommendation |
|----|----------|------|-------|----------|--------|----------------|
| REL-001 | Critical | `app/build.gradle.kts` | 14 | `targetSdk = 34`; Play requires API 36 since Aug 31, 2026; extension passed Nov 1, 2026; today is Nov 25, 2026 | Play Console blocks AAB/APK uploads targeting API 34 | Raise `targetSdk` to 36 |
| REL-002 | High | `app/build.gradle.kts` | 9 | `compileSdk = 34`; must be ≥ targetSdk | Cannot compile targetSdk 36 with compileSdk 34 | Raise `compileSdk` to ≥36 |
| REL-003 | High | root `build.gradle.kts` | 2 | AGP 8.5.2 max compileSdk is 34; API 36 requires AGP 8.9.1+ | targetSdk bump cannot compile without AGP upgrade | Upgrade AGP to 8.9.1+ |
| REL-006 / REL-015 | High | `app/build.gradle.kts` | 24–34 | No `signingConfig` in `release` buildType | Unsigned AAB/APK cannot be uploaded to Play Console | Add local (uncommitted) `signingConfig` |
| EXP-004 | High | `app/src/main/java/app/flipscan/export/Exporter.kt` | 57–74 | `pdfBytes()` builds entire multi-page PDF in `ByteArrayOutputStream`; no streaming | OOM risk on 10+ page PDF exports on low-RAM devices | Stream PDF to temp file page-by-page |

### 4.2 High-priority security hardening

| ID | Severity | File | Lines | Evidence | Impact | Recommendation |
|----|----------|------|-------|----------|--------|----------------|
| SEC-001 | High | `app/src/main/java/app/flipscan/data/LockerCrypto.kt` | 23–26, 48–58, 66–68 | Keystore key generated without `setUserAuthenticationRequired(true)`; class comment documents intentional non-binding | BiometricPrompt is only a UI gate; any code in app process can decrypt all locker records | Bind key to biometric with `.setUserAuthenticationRequired(true)` — but **must pair with key-rewrap migration** (see 4.5) |
| SEC-002 / EXP-001 / SEC-005 / SEC-011 | Medium | `app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt` | 174–183, 228–232 | `decryptPagesToTemp()` writes plaintext JPEGs to `cacheDir/locker_export/<docId>/`; startup sweep never touches `cacheDir/locker_export`; `LockerStore` comment (lines 77–79) says decrypted copies should only be under `noBackupFilesDir/locker_tmp` | Plaintext locker pages can persist in cache after a killed export; not covered by startup cleanup | Write export temps to `noBackupFilesDir/locker_export`; add startup sweep of `locker_export` |
| SEC-008 | High | `app/src/main/java/app/flipscan/data/LockerCrypto.kt` | 28–62 | No key backup, escrow, or recovery; key lives only in Keystore alias `flipscan_locker_v1`; `allowBackup="false"` | Clearing app data or factory reset permanently destroys all locker documents; no recovery path | Add first-run data-loss warning; optionally offer user-provided recovery passphrase key-wrapping the Keystore key |

### 4.3 Medium / lower-priority confirmed findings

| ID | Severity | File | Lines | Summary |
|----|----------|------|-------|---------|
| EXP-002 | High | `app/src/main/java/app/flipscan/export/Exporter.kt` | 76–77 | `exportDir()` deletes entire `cacheDir/exports` on every render, invalidating in-progress shares |
| OCR-001 / OCR-005 | Medium | `app/src/main/java/app/flipscan/ui/DigitalPins.kt` | 169–175 | "Verified" pill labels `PinFormat.isValid()` format pass as authoritative verification |
| OCR-002 | Medium | `app/src/main/java/app/flipscan/data/LockerOcr.kt` | 96–101, 124–134 | PAN `fixDigits()` unconditionally maps O→0, I/L→1, can silently mutate a real character |
| PERF-002 | Medium | `app/src/main/java/app/flipscan/data/PageEditor.kt` | 44–61 | Legacy `rotateInPlace()` decodes full-res bitmap without sampling; OOM risk on low-end devices for scans without `.orig` sidecar |
| PERF-003 | Medium | `app/src/main/java/app/flipscan/scan/PageProcessor.kt` | 27–41, 95–111 | Full-res `src` + warped `Mat` can coexist natively during capture (~40–50 MB) |
| REL-007 | Medium (Potential) | `app/proguard-rules.pro` | 1–18 | Missing keep rules for `org.json`, `androidx.biometric`, ML Kit, Keystore; R8 minification enabled |

See the full finding tables in each agent report for additional Low/Informational findings (SEC-004, SEC-007, SEC-010, EXP-003, EXP-009, PERF-001, PERF-004–008, REL-005, REL-008–018, OCR-003–013).

---

## 5. Duplicate Findings (collapsed by Agent 6)

| Finding IDs | Agents | Issue | Recommended single ID |
|-------------|--------|-------|----------------------|
| SEC-002, EXP-001, SEC-005, SEC-011 | 1, 3 | Locker plaintext export temps in `cacheDir/locker_export`, not `noBackupFilesDir/`; survive process death | SEC-002 / EXP-001 (keep SEC-002 primary) |
| REL-006, REL-015 | 5 | Release builds unsigned; no `signingConfig` | REL-006 (primary) |
| OCR-001, OCR-005, OCR-013 | 2 | "Verified" pill overstates assurance — format check, not user confirmation | OCR-001 (primary) |

Not duplicates (distinct issues): SEC-001 vs SEC-003; EXP-002 vs EXP-003.

---

## 6. Findings Refuted or Downgraded by Agent 6

| ID | Original | Agent 6 Status | Reason |
|----|----------|----------------|--------|
| SEC-006 | Medium | Low | Plaintext scratch remnant has reliable next-start `Work.clear()` backstop; impact overstated |
| SEC-010 | Medium | Low | Sub-frame `FLAG_SECURE` window (<16 ms) is negligible, not practically exploitable |
| EXP-003 | Medium | Low | Partial files cleaned on next render; no user data loss |
| PERF-001 | Informational | Theoretical/future-only | Draw-scope/derivedState pattern is correct today; risk is future maintenance only |
| PERF-004 | High | Theoretical/future-only | All `Exporter.render` callers already dispatch off-main thread; no current defect |
| PERF-008 | Low/Medium | Potential (not current defect) | Conditional on camera returning oversized captures |
| REL-007 | Medium | Potential | Missing ProGuard rules are a release-smoke-test item, not a confirmed crash |

---

## 7. Missing Audit Areas (identified by Agent 6)

1. **Crypto agility / key rotation** — no versioning or key-rotation path for the fixed Keystore alias `flipscan_locker_v1`
2. **Biometric re-enrollment invalidation** — `setInvalidatedByBiometricEnrollment` defaults to false; no user warning or recovery path
3. **Lifecycle of `lockerUntil` session vs process death** — entirely in-memory; no persistent "last unlocked at" binding
4. **ML Kit model size / APK size impact** — no current APK/AAB size measurement (README data is from v0.3.0)
5. **`Work.clear()` / `filesDir/work` hygiene for locker captures** — not deeply audited
6. **Error surface for corrupted locker index** — locker appears empty with no actionable user error
7. **Target SDK 36 behavior changes** — Android 16 runtime changes (predictive back, etc.) not audited for `AnimatedContent`/`BackHandler`/portrait-only orientation

---

## 8. Data Loss / Migration Risks

| Risk | Source | Details | Mitigation |
|------|--------|---------|------------|
| Fixing SEC-001 invalidates existing locker documents | `LockerCrypto.kt:23–26, 48–58` | Binding key to biometric invalidates it on re-enrollment; all `*.enc` + `index.bin` become undecryptable; no key-rewrap path | Before implementing SEC-001, design key-rewrap/re-encryption flow; add user-facing data-loss warning |
| SEC-008: clearing app data destroys locker contents | `LockerCrypto.kt:29`; `AndroidManifest.xml:25` | Key exists only in Keystore; no backup; app-data clear or factory reset destroys key permanently | Add first-run data-loss warning; optionally offer recovery passphrase |

---

## 9. Security Assessment

**Overall: sound core, but with a critical crypto-layer gap and a data-loss risk.**

Confirmed strengths:
- AES-256-GCM per-record encryption, fresh IVs, GCM tag verification (rejects tampered/truncated files)
- Key held in Android Keystore, never leaves it, no hardcoded keys
- `allowBackup=false`; locker files never exposed via FileProvider
- BiometricPrompt with device-credential fallback; no custom PIN storage
- Session in memory; locks on `ON_STOP`, 5-minute cap, process death
- `FLAG_SECURE` on locker screens; no sensitive data in accessibility/logs
- No INTERNET permission (ML Kit's merged INTERNET/ACCESS_NETWORK_STATE stripped via `tools:node="remove"`)
- OCR runs on-device, bundled model, pages decrypted one at a time and recycled, nothing logged

Critical gap:
- **SEC-001:** Keystore key is not authentication-bound. Biometric is purely a UI gate. Fixing this requires a key-rewrap migration to avoid SEC-008-style mass data loss.

---

## 10. Performance Assessment

**Overall: well architected; no confirmed Critical/High defects.**

Strengths confirmed by Agent 4:
- CameraX separate Preview/ImageAnalysis/ImageCapture with `KEEP_ONLY_LATEST` and 1-in-3 frame throttle on dedicated background executor
- All heavy OpenCV/bitmap/PDF work off main thread via `Dispatchers.Default`/`IO`
- Bitmap sampling (`inSampleSize`) and recycling consistently applied; one full-resolution bitmap at a time is the design intent
- Camera lifecycle and image-buffer closing correct — no resource leaks
- Crop loupe reuses single downsampled bitmap; no per-drag decode

Conditional risks (verify on 3 GB RAM device per `docs/TESTING.md`):
- PERF-002: legacy full-res rotate fallback OOM risk
- PERF-003: PageProcessor peak native memory (~40–50 MB during warp)
- EXP-004: PDF OOM on 10+ page exports (release blocker)

---

## 11. Build and Test Results

| Check | Result |
|-------|--------|
| `assembleDebug` | BUILD SUCCESSFUL (103,778,252 B debug APK) |
| `assembleRelease` | BUILD SUCCESSFUL (58,724,986 B unsigned release APK) |
| `bundleRelease` | BUILD SUCCESSFUL (29,606,821 B AAB; signed to 29,648,318 B) |
| `lintDebug` | 12 baseline warnings only (8 GradleDependency, ChromeOsAbiSupport, DataExtractionRules, LockedOrientationActivity, DiscouragedApi) |
| Signed AAB | Verified with `jarsigner`; signed by `CN=FlipScan, O=FlipScan, C=IN` (same cert as prior builds) |
| Installed on device | `app.flipscan` versionCode 8 / versionName 0.7.0 on Nothing Phone (2a), Android 16 |
| `apksigner verify` | v2 + v3 schemes valid; certificate SHA-256 unchanged from prior builds (value withheld; the app is not yet published) |
| Unit/instrumentation tests | None exist in project (per AGENTS.md) |
| CI | None configured |

---

## 12. Remaining Manual Verification

Per `docs/TESTING.md`, the following must be performed on a physical device (Nothing Phone (2a), 8 GB RAM, Android 16):

1. Digital Locker: auth success/failure, credential fallback, no-biometric device behavior, backgrounding, 5-minute session cap, process death (`am force-stop`), interrupted saves, encryption at rest (`run-as` inspection of `*.enc` files), temp cleanup
2. Digital Pins: OCR read accuracy, masked-by-default reveal, 20-second re-mask, copy with sensitive clipboard flag, edit/remove, Verhoeff checksum behavior
3. Locker export: PDF/JPG/PNG save to Downloads/FlipScan, Open previously exported files, Share, plaintext warning
4. Normal scan regression: capture, crop, rotate, export, share, folders, rename
5. Release build (R8) smoke test: Keystore/org.json/ML Kit keep rules; no release-only crashes
6. TargetSdk 36 behavior: predictive back, `AnimatedContent` transitions, `BackHandler`, portrait-only on Android 16

No device testing has been claimed or performed by this audit beyond the install/launch smoke test.

---

## 13. Release-Readiness Status

**NOT READY** for Google Play publication as of November 25, 2026.

Release blockers that must be resolved first:
1. REL-001/REL-002/REL-003: raise `targetSdk`/`compileSdk` to 36 and upgrade AGP to 8.9.1+ (and likely Kotlin plugin)
2. REL-006: configure a local (uncommitted) `signingConfig` for release builds
3. EXP-004: stream PDF export to disk page-by-page to avoid OOM
4. EXP-002: use per-render export subdirectories instead of `deleteRecursively()` on every render

High-priority security fixes (before or shortly after release):
5. SEC-002/EXP-001: move locker export plaintext temps to `noBackupFilesDir/locker_export`; add startup sweep
6. SEC-008: add first-run data-loss warning; optionally add recovery passphrase
7. SEC-001: bind locker key to biometric — **only after** designing key-rewrap migration (to avoid data loss)

Once these are addressed and physical-device testing per `docs/TESTING.md` is completed, FlipScan v0.7.0 can be reassessed for release readiness.

---

## 14. Source Documents

This consolidated audit synthesizes the following agent reports (all read-only, at commit `7ac29e3`):

- `docs/multi-agent/01_SECURITY_AUDIT.md` — Agent 1: Digital Locker Security
- `docs/multi-agent/02_OCR_DIGITAL_PINS_AUDIT.md` — Agent 2: OCR & Digital Pins
- `docs/multi-agent/03_EXPORT_STORAGE_AUDIT.md` — Agent 3: Export, Sharing & File Storage
- `docs/multi-agent/04_PERFORMANCE_QA_AUDIT.md` — Agent 4: Scanning Performance & QA
- `docs/multi-agent/05_ANDROID_RELEASE_AUDIT.md` — Agent 5: Android Configuration & Play Store
- `docs/multi-agent/06_INDEPENDENT_REVIEW.md` — Agent 6: Independent Security & Quality Reviewer
