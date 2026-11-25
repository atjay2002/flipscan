# Agent 5 — Android Configuration and Play Store Audit

## Repository Revision Inspected
- Branch: main (could not be verified — `git` is not installed in this environment)
- Commit: 7ac29e3 (could not be verified — `git` is not installed in this environment)
- Files reviewed:
  - app/src/main/AndroidManifest.xml
  - app/build.gradle.kts
  - build.gradle.kts (root)
  - gradle.properties
  - settings.gradle.kts
  - app/proguard-rules.pro
  - app/src/main/res/xml/file_paths.xml
  - local.properties
  - SPEC.md, README.md, AGENTS.md
  - docs/TESTING.md
  - docs/MULTI_AGENT_ORCHESTRATION.md

## Findings

### REL-001 — targetSdk 34 does not meet current Google Play API 36 requirement

| Field | Description |
|---|---|
| Finding ID | REL-001 |
| Category | Release |
| Severity | Critical |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 14 |
| Evidence | `targetSdk = 34` is declared in `defaultConfig`. |
| Impact | As of August 31, 2026, Google Play requires all new apps and app updates for phones/tablets to target Android 16 (API level 36) or higher. The November 1, 2026 extension window has closed. Today is November 25, 2026. Play Console will block any AAB/APK upload targeting API 34. The app cannot be published or updated on Play Store without raising targetSdk. |
| Recommendation | Raise `targetSdk` to 36 (and `compileSdk` to at least 36). Test on Android 16 behavior changes before release. |
| Verification | Build a release AAB with targetSdk=36 and attempt upload to Play Console internal testing track; confirm no target-API rejection. |

### REL-002 — compileSdk 34 is insufficient for API 36 targeting

| Field | Description |
|---|---|
| Finding ID | REL-002 |
| Category | Release |
| Severity | High |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 9 |
| Evidence | `compileSdk = 34`. The compile SDK must be greater than or equal to the target SDK. |
| Impact | If targetSdk is raised to 36 (required by REL-001), compileSdk must also be raised to at least 36. Currently compileSdk=34 would block compilation with targetSdk=36. |
| Recommendation | Raise `compileSdk` to 36 alongside the targetSdk bump. Verify AGP version supports compileSdk 36 (see REL-003). |
| Verification | `./gradlew assembleRelease` succeeds with compileSdk=36 and targetSdk=36. |

### REL-003 — AGP 8.5.2 may not support compileSdk 36

| Field | Description |
|---|---|
| Finding ID | REL-003 |
| Category | Release |
| Severity | High |
| Status | Potential |
| File | build.gradle.kts (root) |
| Lines | 2 |
| Evidence | `id("com.android.application") version "8.5.2" apply false`. AGP 8.5.x was released in late 2024. AGP 8.6+ added full support for compileSdk 35/36 in some cases; compileSdk 36 may require AGP 8.6 or later. |
| Impact | Raising compileSdk to 36 may fail with AGP 8.5.2, requiring an AGP upgrade before the targetSdk bump can proceed. |
| Recommendation | Verify AGP compatibility with compileSdk 36. If AGP 8.5.2 rejects it, upgrade AGP to 8.6+ (and corresponding Kotlin plugin) in root build.gradle.kts. |
| Verification | `./gradlew assembleRelease` with compileSdk=36 completes without AGP errors. |

### REL-004 — Kotlin plugin 2.0.20 may need upgrade with AGP change

| Field | Description |
|---|---|
| Finding ID | REL-004 |
| Category | Release |
| Severity | Medium |
| Status | Potential |
| File | build.gradle.kts (root) |
| Lines | 3 |
| Evidence | `id("org.jetbrains.kotlin.android") version "2.0.20" apply false`. Kotlin 2.0.20 pairs with AGP 8.2–8.4 era. A newer AGP may require a newer Kotlin plugin. |
| Impact | AGP/Kotlin version mismatch can cause compilation failures or subtle R8/ProGuard issues in release builds. |
| Recommendation | If AGP is upgraded (REL-003), upgrade the Kotlin plugin to a compatible version per Android Gradle plugin release notes. |
| Verification | `./gradlew assembleRelease` succeeds after any AGP/Kotlin version changes. |

### REL-005 — WRITE_EXTERNAL_STORAGE declared with maxSdkVersion=28

| Field | Description |
|---|---|
| Finding ID | REL-005 |
| Category | Release |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml |
| Lines | 18–20 |
| Evidence | `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />` is declared. |
| Impact | This permission is only effective on Android 7–9 (API 24–28). On Android 10+, scoped storage / MediaStore is used instead. The manifest comment acknowledges this. It is not strictly "unnecessary" because the app supports minSdk=24 and saves to Downloads/FlipScan on older devices, but it is a legacy permission that generates a runtime permission dialog on Android 7–9. No Play Store policy violation. |
| Recommendation | Keep for minSdk 24 compatibility on Android 7–9. No change required unless minSdk is raised above 28. Document the rationale (already done in manifest comment). |
| Verification | Confirm Downloads/FlipScan saving works on an Android 7–9 device or emulator; confirm no storage permission is requested on Android 10+. |

### REL-006 — No signing configuration in build.gradle.kts; release APKs are unsigned

| Field | Description |
|---|---|
| Finding ID | REL-006 |
| Category | Release |
| Severity | High |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 24–34 (release buildType) |
| Evidence | The `release` buildType has no `signingConfig` block. README.md confirms: "Release APKs are unsigned; configure your own signing key locally and never commit it." |
| Impact | An unsigned release APK/AAB cannot be uploaded to Google Play Console. Signing must be configured locally (keystore not committed). This is not a code defect but a release-blocking configuration gap. |
| Recommendation | Add a `signingConfig` referencing a local keystore (e.g., in `~/.android/` or `local.properties`-referenced path), never committed to version control. Apply it to the `release` buildType. |
| Verification | `./gradlew bundleRelease` produces a signed AAB that uploads to Play Console without certificate errors. |

### REL-007 — ProGuard rules missing for org.json, androidx.biometric, ML Kit, and Keystore

| Field | Description |
|---|---|
| Finding ID | REL-007 |
| Category | Release |
| Severity | Medium |
| Status | Potential |
| File | app/proguard-rules.pro |
| Lines | 1–18 |
| Evidence | `proguard-rules.pro` contains keep rules for `org.opencv.**`, `androidx.camera.**`, and `-dontwarn kotlinx.coroutines.**`. docs/TESTING.md (line 137) states: "R8 must not break Keystore (encryption), org.json (index) or androidx.biometric (prompt)." No keep rules exist for `org.json.**`, `androidx.biometric.**`, `com.google.mlkit.**`, or Android Keystore classes. |
| Impact | R8 minification (`isMinifyEnabled = true`) could strip classes/members used reflectively by ML Kit OCR, BiometricPrompt, or org.json, causing release-only crashes in the Digital Locker, Digital Pins, or camera flows. TESTING.md explicitly lists this as the first suspect for release-only failures. |
| Recommendation | Add keep rules for `org.json.**`, `androidx.biometric.**`, ML Kit classes (`com.google.mlkit.**`), and any Keystore-related classes used by LockerCrypto. Test release build on device (TESTING.md "Release build (R8) smoke test"). |
| Verification | Install release APK on a physical device; exercise camera capture, locker unlock, OCR read, and export. Confirm no ClassNotFoundException / NoSuchMethodError. |

### REL-008 — INTERNET permission correctly absent from merged manifest

| Field | Description |
|---|---|
| Finding ID | REL-008 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml |
| Lines | 8–13 |
| Evidence | `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are declared with `tools:node="remove"`. Manifest comments confirm ML Kit's merged permissions are stripped at build time because the bundled OCR model never needs network. |
| Impact | The offline-first invariant is preserved. No INTERNET permission will appear in the merged manifest, satisfying Google Play's expectation that permissions match declared functionality. |
| Recommendation | None. Keep the `tools:node="remove"` declarations. Verify via APK Analyzer / merged manifest that INTERNET is absent in both debug and release builds. |
| Verification | Inspect `app/build/outputs/logs/manifest-merger__releaseReport.html` or use APK Analyzer to confirm no INTERNET/ACCESS_NETWORK_STATE in final APK. |

### REL-009 — allowBackup=false correctly configured for sensitive documents

| Field | Description |
|---|---|
| Finding ID | REL-009 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml |
| Lines | 23–28 |
| Evidence | `android:allowBackup="false"` on the `<application>` tag. No `android:fullBackupContent` is declared (not needed when allowBackup is false). |
| Impact | Android Auto Backup is disabled, so app-private data (scans, encrypted locker index, sidecars) will not be backed up to Google Drive. This is appropriate for an app handling sensitive identity documents. Locker plaintext copies already live under `noBackupFilesDir/locker_tmp` (per AGENTS.md/README). |
| Recommendation | None. Confirm no `android:allowBackup="true"` override exists in any manifest fragment or library merge. |
| Verification | Check merged manifest for `allowBackup=false`. Confirm FileProvider does not expose `filesDir/locker`. |

### REL-010 — FileProvider authority and paths correctly configured

| Field | Description |
|---|---|
| Finding ID | REL-010 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml; app/src/main/res/xml/file_paths.xml |
| Lines | Manifest 29–37; file_paths.xml 1–7 |
| Evidence | Authority `app.flipscan.fileprovider`, `android:exported="false"`, `android:grantUriPermissions="true"`. file_paths.xml exposes only `cache-path` for `exports/` and `external-path` for `Download/FlipScan/`. No `files-path` exposing app-private storage or locker directory. |
| Impact | Locker encrypted documents are never exposed through FileProvider (README confirms: "nothing under filesDir/locker is exposed by FileProvider"). Share uses content URIs, not `file://` URIs (TESTING.md line 125). |
| Recommendation | None. Keep the current scope. If locker export/share is ever added, add a dedicated encrypted file path (not plaintext). |
| Verification | Confirm no `file://` URI is ever passed to an external viewer (TESTING.md item 125). |

### REL-011 — R8 minification and resource shrinking correctly enabled for release

| Field | Description |
|---|---|
| Finding ID | REL-011 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 24–34 |
| Evidence | `release` buildType: `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard files `proguard-android-optimize.txt` + `proguard-rules.pro`. `dependenciesInfo { includeInApk = false; includeInBundle = false }` removes the dependency metadata blob. `packaging` excludes `DebugProbesKt.bin` and `META-INF/*.version`. |
| Impact | Release builds are minimized and shrunk, reducing APK size. README reports measured release APK ~36.9 MiB (minified) vs ~95.5 MiB unminified at v0.3.0. |
| Recommendation | None for configuration. Address missing keep rules (REL-007). |
| Verification | `./gradlew assembleRelease` succeeds; release APK installs and runs on device (TESTING.md release smoke test). |

### REL-012 — ABI configuration compliant with Play 64-bit requirement

| Field | Description |
|---|---|
| Finding ID | REL-012 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 22, 33 |
| Evidence | Debug: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Release: `arm64-v8a`, `armeabi-v7a` (no x86_64). `bundle { abi { enableSplit = true } }` enables per-ABI splitting in the AAB. |
| Impact | Google Play requires 64-bit support (arm64-v8a) — present. armeabi-v7a (32-bit) is allowed alongside arm64-v8a. No x86 (32-bit) is packaged, which is fine. The AAB delivers only the native libraries each device needs. |
| Recommendation | None. x86_64 is correctly debug-only (emulator use). |
| Verification | Inspect AAB native library splits: each split contains only its ABI's `.so` files. |

### REL-013 — No x86 (32-bit) native libraries packaged

| Field | Description |
|---|---|
| Finding ID | REL-013 |
| Category | Release |
| Severity | Informational |
| Status | Confirmed |
| File | app/build.gradle.kts |
| Lines | 33 |
| Evidence | Release ndk filters are `arm64-v8a`, `armeabi-v7a` only. No `x86`. |
| Impact | Compliant with Play 64-bit requirement. Smaller release artifacts. |
| Recommendation | None. |
| Verification | Confirm `lib/x86/` is absent from the release APK/AAB. |

### REL-014 — APK size analysis data is from v0.3.0; current size unknown

| Field | Description |
|---|---|
| Finding ID | REL-014 |
| Category | Release |
| Severity | Informational |
| Status | Potential |
| File | README.md |
| Lines | 60–73 |
| Evidence | README size table is labeled "Version 0.3.0". Current version is 0.7.0 (build.gradle.kts line 16). v0.6.0 added ML Kit OCR (~19.6 MB increase over 0.5.0). No current debug/release/AAB size measurements exist in the repo. |
| Impact | Cannot confirm whether current APK/AAB size is within reasonable limits for Play Store delivery (150 MB APK limit, 200 MB AAB limit, but ~100+ MB debug APK due to OpenCV native libs may be a download concern). |
| Recommendation | Run `./gradlew assembleDebug assembleRelease bundleRelease` and measure artifact sizes. Consider whether debug APK (~100+ MB with x86_64 + OpenCV) is acceptable for internal testers. |
| Verification | Build all three artifacts and record byte sizes. Compare against Play limits (APK 150 MB, AAB 200 MB uncompressed). |

### REL-015 — Release APKs unsigned; no signing config committed

| Field | Description |
|---|---|
| Finding ID | REL-015 |
| Category | Release |
| Severity | High |
| Status | Confirmed |
| File | app/build.gradle.kts; README.md |
| Lines | build.gradle.kts 24–34; README 49–58 |
| Evidence | No `signingConfig` in build.gradle.kts. README documents manual `apksigner` signing for local testing. |
| Impact | Cannot upload unsigned APK/AAB to Play Console. Signing keystore must exist locally and never be committed. |
| Recommendation | Configure `signingConfig` for `release` using a keystore path from `local.properties` or an environment variable. Never commit the keystore or passwords. |
| Verification | `./gradlew bundleRelease` produces a signed AAB; upload to Play Console internal track succeeds. |

### REL-016 — No lint configuration found

| Field | Description |
|---|---|
| Finding ID | REL-016 |
| Category | Release |
| Severity | Low |
| Status | Confirmed |
| File | (none found) |
| Lines | N/A |
| Evidence | Glob for `**/lint*` returned no files. No `lint.xml` in app or root. |
| Impact | Lint runs with default settings only. Release-blocking lint errors (e.g., missing permissions, manifest issues) would not be customized. Not a Play Store blocker but a quality gap. |
| Recommendation | Add `app/lint.xml` if specific lint rules need suppression or severity changes. Run `./gradlew lintRelease` before release. |
| Verification | `./gradlew lintRelease` completes; review any warnings related to manifest or permissions. |

### REL-017 — Dependency versions appear current but unverified for vulnerabilities

| Field | Description |
|---|---|
| Finding ID | REL-017 |
| Category | Release |
| Severity | Medium |
| Status | Potential |
| File | app/build.gradle.kts |
| Lines | 62–87 |
| Evidence | Declared dependencies: androidx.core:core-ktx:1.13.1, androidx.activity:activity-compose:1.9.2, androidx.lifecycle:lifecycle-runtime-compose:2.8.6, compose-bom:2024.09.00, CameraX 1.3.4, androidx.biometric:biometric:1.1.0, com.google.mlkit:text-recognition:16.0.1, org.opencv:opencv:4.9.0. AGP 8.5.2, Kotlin 2.0.20. |
| Impact | No known critical vulnerabilities identified from inspection alone, but dependency versions are from the 2024 timeframe. No `dependencyCheck` or `org.owasp:dependency-check` plugin is configured, so no automated vulnerability scanning occurs. |
| Recommendation | Run `./gradlew dependencies` to inspect the full dependency tree. Consider adding OWASP Dependency-Check or Google Play's SDK index review. No code change required for this audit. |
| Verification | `./gradlew dependencies` output reviewed; no known-vulnerable versions flagged. |

### REL-018 — Reproducible builds not explicitly configured

| Field | Description |
|---|---|
| Finding ID | REL-018 |
| Category | Release |
| Severity | Low |
| Status | Potential |
| File | app/build.gradle.kts; gradle.properties |
| Lines | 50–54; 1–4 |
| Evidence | `dependenciesInfo.includeInApk/bundle = false` removes dependency metadata (helps reproducibility). `gradle.properties` sets JVM args and encoding. No `reproducibleBuilds` or `android.enableR8.fullMode` explicit settings; R8 full mode is at AGP default (per README known limitations line 123). |
| Impact | Builds may not be byte-for-byte reproducible across machines due to timestamps, build paths, and default R8 behavior. Not a Play Store requirement, but a release-engineering gap. |
| Recommendation | Enable `reproducibleBuilds` if byte-identical rebuilds are desired. Document R8 full mode caveat. |
| Verification | Run `./gradlew assembleRelease` twice on the same machine; compare APK hashes (will differ due to timestamps unless reproducibleBuilds enabled). |

## Areas Reviewed (checklist)

- [x] AndroidManifest.xml permissions (no INTERNET) — REL-008 confirmed absent via `tools:node="remove"`
- [x] minSdk / targetSdk / compileSdk values — minSdk=24, targetSdk=34, compileSdk=34 (REL-001/REL-002: targetSdk non-compliant)
- [x] AGP and Kotlin plugin versions — AGP 8.5.2, Kotlin 2.0.20 (REL-003/REL-004)
- [x] Dependency versions and necessity — REL-017; all appear necessary and current-ish
- [x] Backup configuration (allowBackup=false) — REL-009 confirmed
- [x] FileProvider authority and paths — REL-010 confirmed
- [x] R8 minification and resource shrinking — REL-011 confirmed enabled
- [x] ABI configuration (arm64-v8a, armeabi-v7a release; x86_64 debug only) — REL-012/REL-013 confirmed compliant
- [x] APK size analysis (debug vs release) — REL-014: data stale (v0.3.0), current size unknown
- [x] AAB build and per-ABI splitting — `bundle { abi { enableSplit = true } }` present
- [x] Signing configuration — REL-006/REL-015: none configured, release APKs unsigned
- [x] ProGuard rules for OpenCV, CameraX, ML Kit — REL-007: OpenCV/CameraX present; ML Kit/biometric/org.json missing
- [x] Target SDK compliance with Google Play policies — REL-001: API 34 non-compliant; API 36 required since Aug 31, 2026

## Conclusion

FlipScan's Android configuration is well-intentioned and mostly well-executed, with strong privacy defaults (no INTERNET, allowBackup=false, scoped FileProvider, offline-only ML Kit). However, it is **not currently ready for Google Play publication** due to a single critical blocker:

**targetSdk=34 is below the Google Play minimum of API 36 (Android 16), required for all new apps and updates since August 31, 2026.** The extension window closed November 1, 2026, and today is November 25, 2026. Play Console will reject any AAB/APK targeting API 34.

### Release-blocking items (P0/P1)

1. **Raise targetSdk to 36 and compileSdk to at least 36** (REL-001, REL-002). This is mandatory for Play Store submission as of today.
2. **Verify AGP 8.5.2 supports compileSdk 36**; upgrade AGP/Kotlin if needed (REL-003, REL-004).
3. **Configure a signing config for release builds** (REL-006, REL-015). Unsigned artifacts cannot be uploaded.
4. **Add ProGuard keep rules for org.json, androidx.biometric, ML Kit, and Keystore** (REL-007) to prevent R8-related release-only crashes, especially given R8 minification is enabled.

### Secondary items (P2/P3)

- Measure current APK/AAB size (README data is from v0.3.0; current version is 0.7.0) (REL-014).
- Consider adding lint.xml and running lintRelease (REL-016).
- Verify dependency versions for known vulnerabilities (REL-017).
- Consider reproducibleBuilds if byte-identical rebuilds are desired (REL-018).

### Confirmed compliant items (no action needed)

- INTERNET permission absent from merged manifest (REL-008).
- allowBackup=false (REL-009).
- FileProvider exported=false with scoped paths; no locker exposure (REL-010).
- R8 minification + resource shrinking enabled for release (REL-011).
- ABI splits compliant with Play 64-bit requirement; arm64-v8a present, no x86 (REL-012, REL-013).
- WRITE_EXTERNAL_STORAGE scoped to maxSdkVersion=28 for Android 7–9 compatibility (REL-005).

### Recommended next steps

1. Bump `compileSdk` and `targetSdk` to 36 in `app/build.gradle.kts`.
2. Verify AGP/Kotlin compatibility; upgrade `build.gradle.kts` (root) if needed.
3. Add missing ProGuard keep rules to `app/proguard-rules.pro`.
4. Add release `signingConfig` referencing a local (uncommitted) keystore.
5. Run `./gradlew assembleRelease bundleRelease lintRelease` and test the signed release APK on a physical device per docs/TESTING.md "Release build (R8) smoke test".
6. Record current APK/AAB sizes for the release record.
