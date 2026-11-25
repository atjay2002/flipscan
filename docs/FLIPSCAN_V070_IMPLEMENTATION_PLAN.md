# FlipScan v0.7.0 — Implementation Plan

**Project:** FlipScan
**Application ID:** `app.flipscan`
**Revision audited:** Branch `main`, commit `7ac29e3`
**Plan revision:** 2 (technical review corrections — see §14)
**Plan date:** 25 November 2026
**Status:** Draft for approval — Stage 5 (implementation) NOT started

**Sources:** `docs/FLIPSCAN_V070_AUDIT.md`, `docs/multi-agent/01_SECURITY_AUDIT.md` … `06_INDEPENDENT_REVIEW.md`, plus direct re-reading of application source during Stage 4 (evidence cited per item).

---

## 1. Purpose, scope and constraints

This plan specifies the work needed before FlipScan can be published to Google Play and before the Digital Locker security items are addressed. Every item below cites the code that was actually read during this planning stage — audit claims that could not be reproduced from source are marked as such and are not scheduled as work.

**Hard constraints for Stage 4 (this document):**
- No changes to application source code.
- No changes to Gradle configuration.
- No schema changes; no changes to encryption formats or existing keys.
- No creation of signing credentials.
- No deletion or modification of user documents.
- No commits or pushes.

**Hard constraints carried into Stage 5 (implementation), from `AGENTS.md` / `SPEC.md`:**
- `INTERNET` permission is an invariant. Any new dependency must be checked for merged network permissions.
- No new dependency without explicit approval (`SPEC.md`).
- Offline-only: no cloud, no analytics, no tracking.
- Preserve existing scanning, crop, folder, export and locker behaviour.
- `AGENTS.md` records that the project has **no unit tests, no instrumentation tests and no CI**. Any test infrastructure proposed here is *new work*, not existing coverage (§10).

---

## 2. What changed in revision 2

The technical review identified eight classes of error. Summary of corrections (full detail in §14):

| # | Original plan | Correction in revision 2 |
|---|---|---|
| 1 | "Stream the PDF to a temp file page-by-page" as an `Exporter`-only fix | Technically impossible with `android.graphics.pdf.PdfDocument`. The real cause is different and larger than the claimed one (§6.3) |
| 2 | `setUserAuthenticationRequired(true)` described loosely as "binds key to biometric; re-enrolment invalidates it" | Corrected semantics, and SEC-001 is now **deferred**, not scheduled (§6.7) |
| 3 | `noBackupFilesDir` implied to be a hardened location for plaintext | It is not encrypted, and it is *more* persistent than `cacheDir`. Benefit is narrower and the location move is optional (§6.5) |
| 4 | Per-render export directories, with dismissal treated as end-of-life | Explicit retention policy that never deletes active or recent exports (§6.4) |
| 5 | "Add a `signingConfig`" | Verification-first, never-regenerate rule, and first-publication vs update distinction (§6.2) |
| 6 | "Clearing app data destroys the Keystore key" asserted as fact | Three distinct scenarios, no unverified destruction claims, user-facing warning retained (§6.6) |
| 7 | Manual device checklist treated as the test strategy | Automated infrastructure that must be added, manual checklist separately, disposable documents for destructive tests (§10) |
| 8 | Parallelism by topic only | Shared-file ownership map; one owner per file; worktree requirement for `Exporter.kt` and `app/build.gradle.kts` (§9) |
| 9 | Rollback by reverting to previous behaviour | Reverting is explicitly disallowed where the previous behaviour is the defect; mitigations and integrity checks per risky change (§11) |

Two audit conclusions are also **downgraded by re-reading the source** rather than accepted: the `ByteArrayOutputStream` is not the dominant memory cost of PDF export (IMPL-001), and moving locker export temps to `noBackupFilesDir` is not automatically an improvement (IMPL-003). Both are newly recorded findings, labelled as Stage-4 additions.

---

## 3. Priority scale and status vocabulary

Revision 1 used "P0/P1/release blocker" inconsistently. This scale is authoritative and used for every item in §6 and §7.

| Priority | Definition | Consequence of not fixing |
|---|---|---|
| **P0** | Blocks Google Play submission. No upload can succeed. | Release cannot ship at all |
| **P1** | Must fix before the next public release. Security, data-loss, or a user-visible functional defect. | Shipping a known defect or exposure |
| **P2** | Hardening or quality work, scheduled for the release after next. | Acceptable short-term risk |
| **P3** | Optional, cosmetic, or speculative. | No user impact |
| **DEFERRED** | Deliberately unscheduled pending a prerequisite design or decision. | Risk accepted in writing |

| Status | Meaning |
|---|---|
| **Confirmed** | The described code path exists and the described behaviour follows deterministically. Verified by reading the cited lines during Stage 4. |
| **Confirmed-path / conditional-impact** | The code path exists exactly as described; whether the impact materialises depends on device behaviour, document size or platform version not provable by reading code. |
| **Theoretical** | Risk exists only under a future code change or a condition the current code cannot reach. Not scheduled. |

---

## 4. Finding reconciliation

### 4.1 P0 — Play submission blockers

| ID | Status | Evidence verified in Stage 4 | Summary |
|---|---|---|---|
| REL-001 | Confirmed | `app/build.gradle.kts:14` — `targetSdk = 34` | Below the API level Google Play requires for new apps and updates; uploads are rejected |
| REL-002 | Confirmed | `app/build.gradle.kts:9` — `compileSdk = 34` | Must be ≥ `targetSdk`; cannot raise `targetSdk` without raising this |
| REL-003 | Confirmed | `build.gradle.kts:2` — AGP `8.5.2` | AGP must be upgraded to support `compileSdk` at the required level; exact minimum version is an unresolved decision (§12-Q1) |
| REL-006 / REL-015 | Confirmed | `app/build.gradle.kts:24–34` — `release { }` block contains `isMinifyEnabled`, `isShrinkResources`, `proguardFiles`, `ndk`; **no `signingConfig`** | Release artefacts are produced unsigned and must be signed out-of-band today |

> Note on REL-001/002/003: the *policy claim* (that API 34 no longer satisfies Play requirements) originates from Agent 5 and was reiterated by Agent 6 with external references. It is a policy statement, not a code property, and cannot be proven by reading this repository. It is scheduled as P0 because acting on a stale target SDK is cheap relative to a rejected submission. Re-confirm the current requirement in Play Console before executing WP-01 (§12-Q1).

### 4.2 P1 — reliability, privacy, disclosure

| ID | Status | Evidence verified in Stage 4 | Summary |
|---|---|---|---|
| EXP-004 | Confirmed-path / conditional-impact | `Exporter.kt:57–74`, `137–145`, `163–166`; `SheetRenderer.kt:43,45–53` | Multi-page PDF export holds every page bitmap in memory simultaneously and serialises through an extra in-memory copy. Severity depends on page count, DPI and device RAM (§6.3) |
| EXP-002 | Confirmed | `Exporter.kt:76–77`; callers `Exporter.kt:153`, `207` | Every render recursively deletes the shared cache export directory, including files an in-flight share/open may still be reading |
| SEC-002 / EXP-001 / SEC-005 / SEC-011 | Confirmed (location), Conditional (exposure) | `LockerViewerScreen.kt:173–183`; `MainActivity.kt:141–147`; `LockerStore.kt:78–79, 300–303` | Locker pages are decrypted to `cacheDir/locker_export/<docId>/`; the startup sweep cleans `filesDir/work`, `noBackupFilesDir/locker_tmp` and locker orphans, but never `cacheDir/locker_export` |
| SEC-008 | Confirmed | `LockerCrypto.kt:29` (fixed alias); `LockerStore.kt:88` (`filesDir/locker`); `AndroidManifest.xml` `allowBackup="false"` | Locker contents are unrecoverable if app storage is lost, and this is not disclosed to the user (§6.6) |

### 4.3 P2 — hardening and quality

| ID | Status | Evidence verified in Stage 4 | Summary |
|---|---|---|---|
| OCR-001 / OCR-005 / OCR-013 | Confirmed | `DigitalPins.kt:169–175` (agent-cited); `PinFormat.isValid` as source of `numberVerified` | A local format check is surfaced as "Verified" |
| OCR-002 | Confirmed | `LockerOcr.kt:96–101, 124–134` (agent-cited) | PAN `fixDigits()` rewrites `O→0`, `I/L→1` unconditionally |
| PERF-002 | Confirmed-path / conditional-impact | `PageEditor.kt:44–61` (agent-cited) | Legacy in-place rotate decodes full resolution without sampling |
| PERF-003 | Conditional | `PageProcessor.kt:27–41, 95–111` (agent-cited) | Source and warped `Mat` can coexist |
| REL-007 | Potential (downgraded) | `proguard-rules.pro:1–18` covers OpenCV, CameraX, annotations, coroutines only | Keep rules for `org.json`, `androidx.biometric`, ML Kit and Keystore are absent; R8 is enabled. Fix is verification-led, not speculative rule-writing |
| SEC-004 | Confirmed | `LockerStore.kt:326–333` — `readEncrypted` returns `null` on any decryption failure | Corrupt page ciphertext is indistinguishable from a missing page; no user-visible signal |
| SEC-007 | Confirmed | `LockerAuth.kt` (agent-cited) | `setConfirmationRequired(false)`; biometric strength policy unspecified |
| IMPL-004 (new) | Confirmed-path / conditional-impact | `LockerViewerScreen.kt:185–187, 236–238` | `decryptPagesToTemp()` is invoked on the UI event path before `scope.launch`, so decryption and file writes happen on the calling thread |

### 4.4 Downgraded or theoretical — not scheduled

| ID | Agent 6 status | Stage-4 position |
|---|---|---|
| SEC-006 | Low | Agreed; `Work.clear()` at `MainActivity.kt:143` is a reliable backstop |
| SEC-010 | Low | Agreed; sub-frame `FLAG_SECURE` window, not practically exploitable |
| EXP-003 | Low | Agreed; partial cache files are not user data |
| PERF-001 | Theoretical | Agreed; draw-scope reads today |
| PERF-004 | Theoretical | Agreed; all callers use `Dispatchers.Default`/`IO` (`ResultScreen.kt:152,161`; `ScanActions.kt:115,170`; `LockerViewerScreen.kt:197,248`) |
| PERF-005, PERF-006, PERF-007, PERF-008 | Low / Informational | Not scheduled; no user-visible defect |
| SEC-009, SEC-012, SEC-013 | Informational (positive) | Recorded as existing strengths; must be preserved, not "fixed" |

### 4.5 Duplicates (single owner in this plan)

| Primary ID | Absorbs | Note |
|---|---|---|
| EXP-004 | — | Distinct from EXP-002; both live in `Exporter.kt` but touch different functions |
| EXP-002 | — | Directory lifecycle, not PDF memory |
| SEC-002 | EXP-001, SEC-005, SEC-011 | Same plaintext location, same missing sweep |
| REL-006 | REL-015 | Same missing `signingConfig` |
| OCR-001 | OCR-005, OCR-013 | Same "Verified" labelling defect |

### 4.6 New findings from Stage-4 source verification

These were **not** in any agent report and are recorded here for the first time. They are labelled `IMPL-` to keep them distinguishable from audit IDs.

| ID | Severity | Status | File / lines | Finding |
|---|---|---|---|---|
| IMPL-001 | High | Confirmed | `SheetRenderer.kt:43, 50–53`; `Exporter.kt:65` | In PDF mode the renderer is called with `recycle = false` because "PdfDocument canvases … may still read the pixels later". Every page bitmap therefore stays alive until `doc.writeTo()`. Memory grows linearly with page count and is the **dominant** cost of the export — larger than the `ByteArrayOutputStream` that Agent 3 identified |
| IMPL-002 | Medium | Confirmed | `Exporter.kt:137–145` | `pdfToTarget()` re-renders the entire document up to seven times (base DPI plus `220, 160, 120, 90, 70, 50`) to reach a size target, holding the previous attempt's `ByteArray` while building the next. Time and peak memory are both multiplied |
| IMPL-003 | Medium | Confirmed | `LockerStore.kt:78–79` vs `LockerViewerScreen.kt:173–183` | The "move temps to `noBackupFilesDir`" recommendation contradicts a documented invariant, **but** `noBackupFilesDir` is not encrypted and is retained across "Clear cache", so the move trades one exposure for another unless a sweep is added |
| IMPL-004 | Medium | Confirmed-path / conditional-impact | `LockerViewerScreen.kt:185–187, 236–238` | Decryption and disk writes run on the UI event path; on a multi-page locker document this is main-thread I/O |
| IMPL-005 | Low | Confirmed | `LockerViewerScreen.kt:228–232, 269–272` | Cleanup is `pages.firstOrNull()?.parentFile?.deleteRecursively()`. If *no* page decrypted successfully, `pages` is empty and the created directory is never removed |

---

## 5. Dependencies, conflicts and shared-file ownership

### 5.1 Dependency graph

```
WP-01a AGP upgrade ──► WP-01b compileSdk ──► WP-01c targetSdk ──► WP-01d behaviour verification
WP-01a ──► (may require) Kotlin plugin bump ──► re-verify Compose compiler plugin alignment

WP-02 signing ── independent, but must land before any artefact is offered for upload

WP-03 EXP-004 ── decision required (§12-Q2) ──► implementation
             └─ immediate partial mitigation (§6.3 stage 1+2) is independent and can ship alone

WP-04 EXP-002 ── shares Exporter.kt with WP-03  (conflict — see 5.2)
WP-05 SEC-002 ── shares LockerViewerScreen.kt with IMPL-004/IMPL-005; shares LockerStore.kt with SEC-008 copy
WP-06 SEC-008 ── independent (UI text + optional docs); no code coupling to WP-05

WP-07 SEC-001 DEFERRED ── blocked on an independently reviewed key-management and migration design
```

### 5.2 Shared-file conflicts

These four files are touched by more than one work package. **No two work packages may edit the same file concurrently in the same working tree.**

| File | Work packages that touch it | Conflict nature |
|---|---|---|
| `app/src/main/java/app/flipscan/export/Exporter.kt` | WP-03 (EXP-004), WP-04 (EXP-002) | Both rewrite the export directory + write path. Size-target retry loop and directory lifecycle interact: a streaming implementation changes how many times a file is written |
| `app/build.gradle.kts` | WP-01 (compileSdk, targetSdk, possibly AGP-driven `kotlinOptions`), WP-02 (`signingConfig` + `buildTypes.release`) | Adjacent blocks in a small file; merge conflicts guaranteed |
| `app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt` | WP-05 (SEC-002), P2 IMPL-004/IMPL-005 | Same `decryptPagesToTemp` / `export` / `share` region |
| `app/src/main/java/app/flipscan/data/LockerStore.kt` | WP-05 (sweep extension), WP-06 (optional copy wording) | `sweep()` and the class KDoc, which is the documented security contract |

### 5.3 Ownership assignment

One owner per shared file. Parallel work is permitted only across different rows.

| Work package | Sole owner of | May run concurrently with |
|---|---|---|
| WP-01 toolchain | `app/build.gradle.kts` (SDK levels only), `build.gradle.kts` | WP-03 (different files), WP-05, WP-06, WP-07-design |
| WP-02 signing | `app/build.gradle.kts` (signing block only) | Nothing simultaneous on `app/build.gradle.kts` — sequence after WP-01 |
| WP-03 PDF | `Exporter.kt` (PDF path only) | WP-01, WP-05, WP-06 |
| WP-04 export dirs | `Exporter.kt` (directory lifecycle only) | Must follow WP-03 **unless** run in a separate worktree and rebased |
| WP-05 temp plaintext | `LockerViewerScreen.kt`, `LockerStore.kt` | WP-01, WP-03, WP-06 (text-only) |
| WP-06 disclosure | `LockerScreen.kt`, locker strings, `docs/TESTING.md` | Anything except another edit to `LockerScreen.kt` |

If two work packages must proceed in parallel on `Exporter.kt` or `app/build.gradle.kts`, each requires its own git worktree and the second must be rebased onto the first before merge. Uncoordinated simultaneous edits to those two files are prohibited.

---

## 6. Work packages — P0 and P1

### 6.1 WP-01 — Toolchain: AGP, `compileSdk`, `targetSdk` (REL-001/002/003, REL-004)

| Field | Detail |
|---|---|
| Priority | **P0** |
| Status | Confirmed — versions verified locally; requirement verified against Google's published policy (Q1) |
| Complexity | **High** |
| Owner | Single agent — build files + wrapper |
| Files | root `build.gradle.kts:2–4`; `app/build.gradle.kts:9, 14`, possibly `:36–41`; `gradle/wrapper/gradle-wrapper.properties` |
| Machine prerequisite | Android SDK Platform 36 and Build-Tools 36 must be installed (`cmdline-tools/latest/bin/sdkmanager.bat` is present) |

**Verified prerequisites (checked 25 November 2026).**

| Fact | Evidence |
|---|---|
| Gradle wrapper is **8.9** | `gradle/wrapper/gradle-wrapper.properties` — `distributionUrl=…gradle-8.9-bin.zip` |
| Only SDK platform **34** is installed | `%ANDROID_HOME%\platforms` contains `android-34` only |
| Only Build-Tools **34.0.0** is installed | `%ANDROID_HOME%\build-tools` contains `34.0.0` only |
| `sdkmanager` is available | `%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat` exists |
| Minimum AGP for API 36 is **8.9.1**, which requires **Gradle 8.11.1+** | Android's minimum-tools-per-API table and the AGP/Gradle compatibility table |

So WP-01 is a **four-file plus machine-prerequisite change**, not a two-number edit. The chain is forced: `targetSdk 36` → `compileSdk 36` → `AGP ≥ 8.9.1` → `Gradle ≥ 8.11.1` → SDK Platform 36 and Build-Tools 36 installed. Choosing a newer AGP line moves the Gradle and JDK floors again (AGP 8.13 needs Gradle 8.13; AGP 9.x needs Gradle 9.1+), which is decision Q10.

**Root cause.** `app/build.gradle.kts` pins `compileSdk = 34` (line 9) and `targetSdk = 34` (line 14). Root `build.gradle.kts` pins AGP `8.5.2` (line 2) and Kotlin `2.0.20` (lines 3–4). AGP determines the highest `compileSdk` it understands; raising `compileSdk` beyond what the current AGP supports fails the build or emits an unsupported-compileSdk error. `targetSdk` cannot exceed `compileSdk`.

**Proposed sequence.**
1. Install **SDK Platform 36** and **Build-Tools 36** through `sdkmanager` on the build machine. This downloads tooling to the development machine only; it has no effect on the app's manifest or permissions.
2. Bump the **Gradle wrapper** from 8.9 to at least 8.11.1 (higher if decision Q10 selects a newer AGP line). The wrapper downloads its distribution on first run.
3. Raise **AGP** to the version chosen in Q10 — minimum 8.9.1 for API 36. **Do not guess**; record the AGP release notes for the chosen version as the citation.
4. Raise `compileSdk` to 36, build.
5. Raise `targetSdk` to 36, build.
6. Only if the AGP bump requires it, align Kotlin and `org.jetbrains.kotlin.plugin.compose` (REL-004 is *possible*, not confirmed). The Compose compiler plugin version is tied to the Kotlin version, so they move together or not at all.
7. Keep `JavaVersion.VERSION_17` / `jvmTarget = "17"` (lines 36–41) unless the AGP bump requires otherwise; changing the Java level is a separate decision.

**What this can break (must be verified, not assumed).**
- A higher `targetSdk` changes runtime behaviour. Concrete items to test on a device running the newest supported Android: predictive back with the hand-rolled `Screen` state machine in `MainActivity.kt`; `BackHandler` behaviour; `AnimatedContent` transitions; `FLAG_SECURE` toggling (`MainActivity.kt:166–169`).
- Portrait-only orientation is declared via the manifest. Newer platform versions ignore orientation restrictions in some large-screen configurations. Verify the locker and camera screens on a tablet or unfolded device; do not assume the existing `LockedOrientationActivity` lint suppression still applies.
- Edge-to-edge enforcement and window insets on higher target levels can shift layout in screens that assume system bars.
- A newer AGP may change resource shrinking or R8 defaults; re-check the release APK size and re-run the R8 smoke test (§10.4 item 14).

**Automated checks.** `assembleDebug`, `lintDebug`, `assembleRelease`, `bundleRelease` must all succeed; `lint` must not exceed the 12-warning baseline recorded in the audit.
**Manual checks.** §10.3 items 1–6 on a physical device.
**Backward compatibility.** No user data impact. Artefacts signed with a different key would break update continuity — WP-01 must not touch signing.
**Rollback.** Reverting to API 34 is permitted only as a temporary state because it re-blocks submission; it must be recorded as a known blocker, not a resolution (§11).

---

### 6.2 WP-02 — Release signing configuration (REL-006 / REL-015)

| Field | Detail |
|---|---|
| Priority | **P0** |
| Status | Confirmed — no `signingConfig` exists |
| Complexity | **Low** (code) / **Medium** (process, because it is irreversible if done wrong) |
| Owner | Single agent — `app/build.gradle.kts` signing block only |
| Files | `app/build.gradle.kts:19–35`; a gitignored local properties file |

**Root cause.** The `release` build type at lines 24–34 sets minification, shrinking, ProGuard files and ABI filters, but nothing else. `assembleRelease` and `bundleRelease` therefore emit unsigned artefacts; signing is currently performed out-of-band.

**Verification before configuration — mandatory, in this order.**
1. Confirm whether `app.flipscan` is already published on Google Play. **First publication and an update are different situations:**
   - *First publication:* the key chosen now becomes the long-term signing identity. If Play App Signing is used, the uploaded key becomes the upload key and can be reset by support if lost.
   - *Existing listing:* the upload key must match the key already registered, or the upload is rejected. Replacing it requires an upload-key reset request through Play Console. There is no code-side workaround.
2. Verify the existing keystore before touching Gradle: list its entries and certificate fingerprints, and compare against a known-good artefact produced today. The certificate SHA-256 recorded for this project (`061280f6…926b6c`) must remain identical.
3. Confirm the keystore file is outside the repository and remains gitignored.

**Rules.**
- **Never generate a replacement signing key.** Not in this plan, not as a fallback, not "temporarily". A new key means existing installs cannot be updated and, once published, the listing is stranded.
- **No credentials in the repository.** Path, alias and passwords come from a gitignored file or environment variables. If the values cannot be supplied, the build must fail loudly rather than fall back to an unsigned artefact silently.
- The signing configuration must apply to release only; debug signing must remain untouched so emulator builds keep working.
- Verify the new configuration by building and then checking the signature of the produced artefact, not by trusting the Gradle log.

**Automated checks.** `bundleRelease` then `assembleRelease`; verify signature scheme and certificate fingerprint on both; confirm debug builds still install.
**Backward compatibility.** If the configuration matches the existing keystore, installed users update normally. If it does not, every existing install is stranded — this is the single highest-consequence item in the plan.
**Rollback.** Reverting the Gradle block restores manual signing; no artefact is invalidated.

---

### 6.3 WP-03 — Memory-efficient multi-page PDF export (EXP-004, IMPL-001, IMPL-002)

| Field | Detail |
|---|---|
| Priority | **P1** (reclassified from "release blocker" — see note) |
| Status | Confirmed-path / conditional-impact |
| Complexity | **High** |
| Owner | Single agent — `Exporter.kt` PDF path |
| Files | `app/src/main/java/app/flipscan/export/Exporter.kt` (57–74, 137–145, 163–166, 217–219); `app/src/main/java/app/flipscan/export/SheetRenderer.kt` (43–63, read-only reference) |

> **Priority note.** Agent 3 rated this High and the consolidated audit called it a release blocker. Reading the code shows the risk is real but conditional on page count and device RAM, and it does not block a Play upload. It is therefore **P1**: must be fixed before release, not before submission.

**Root cause — corrected.** Revision 1 of this plan said `pdfBytes()` "builds the entire PDF in a `ByteArrayOutputStream`" and proposed "stream the PDF to a temp file page-by-page". Two corrections:

1. **The `ByteArrayOutputStream` is real but secondary.** `Exporter.kt:68–70` writes the finished document into a `ByteArrayOutputStream` and then copies it with `toByteArray()`. That doubles the serialised PDF transiently, but the serialised PDF is not where the memory goes.
2. **The dominant cost is retained page bitmaps.** `SheetRenderer.draw` loads a bitmap per page (line 50) and only recycles it when `recycle == true` (line 52). `pdfBytes` calls it with `recycle = false` (line 65), and the comment at `SheetRenderer.kt:43` explains why: *"[recycle] must be false for PdfDocument canvases: they may still read the pixels later."* Android's `PdfDocument` defers reading those pixels until `writeTo()`. Consequently **every page bitmap of the document is alive simultaneously**. At 200 DPI, `Exporter.baseDpi` for documents (line 35), an A4 sheet is roughly 1654 × 2339 px ≈ 15.5 MB as `ARGB_8888`; ten pages is ≈ 155 MB before the PDF itself exists. This directly violates the `AGENTS.md` performance invariant that only one full-resolution bitmap should be alive at a time.
3. **The retry loop multiplies both.** `pdfToTarget` (137–145) re-renders the whole document for each DPI in a seven-step ladder until the size target is met, retaining the previous attempt's `ByteArray`. Each attempt re-decodes every page image.

**Why "stream page-by-page" is not implementable as written.** `android.graphics.pdf.PdfDocument` exposes `startPage` / `finishPage` / `writeTo(OutputStream)` and has no API to flush a completed page to the output stream. Serialisation happens in one pass at `writeTo`. Writing that call's output directly to a file removes the duplicate copy but cannot reduce the retained-bitmap cost, because the pictures still hold their bitmaps until that single pass. A page-by-page incremental writer therefore requires **leaving `PdfDocument`**.

**Proposed solution — staged.**

*Stage 1 (safe, no dependency, no behaviour change).* Replace the `ByteArrayOutputStream` with a `FileOutputStream` on the destination file and obtain the size from `File.length()`. Removes two serialised copies. Peak memory becomes "retained bitmaps + one serialised stream". This is strictly an improvement and independently shippable.

*Stage 2 (safe, no dependency).* Bound the retry ladder:
- Write each attempt to its own temporary file; measure by length; delete the losing file immediately instead of holding a `ByteArray`.
- Replace the seven-step unconditional ladder with a size estimate derived from the first attempt (image payload scales roughly with the square of the DPI) plus at most one or two confirmation renders.
- Add a hard guard: above a page-count threshold, reduce the starting DPI rather than attempting the full ladder. The threshold must come from measurement, not a guess.
- Keep the user-facing "could not reach N KB" note (`Exporter.kt:159–161`) behaviourally identical.

*Stage 3 (the actual fix — decision required).* Render one sheet at a time and never hold more than one bitmap:
1. For each sheet, produce the composite image with the existing `renderBitmap(plan, files, dpi, filter)` (`Exporter.kt:44–55`). That path already calls `SheetRenderer.draw(..., recycle = true)`, so it produces the *same pixels* as the current PDF canvas — including the white background and the dashed cut line (`SheetRenderer.kt:46, 54–62`) — and recycles each page bitmap.
2. Compress that single composite to JPEG or PNG in memory, write it to a scratch file, recycle the composite.
3. Assemble the PDF from the scratch files, one page at a time, deleting each scratch file after it is embedded.

Two candidate assemblers for step 3:
- **Option A — minimal in-repo PDF writer.** A sheet is a white page with one image and (optionally) a dashed cut line already baked into the image. A small writer can emit per-page image XObjects: JPEG can be embedded directly with `DCTDecode`, i.e. **byte-for-byte reuse of the encoded JPEG with no re-encode and no quality loss**; PNG requires `FlateDecode` of the already-deflated data. Page order = `sheets` order; page box = `plan.widthMm/heightMm × 72 / 25.4` points, exactly as at `Exporter.kt:61–62`. No new dependency, no network permission, fully offline, peak memory ≈ one composite bitmap plus one compressed page. Cost: hand-written PDF structure (xref table, content streams) must be correct — this is the highest-risk part and needs real files to be opened in several viewers.
- **Option B — PDF library with scratch-file support.** A maintained Android-compatible PDF library (Apache-2.0 licensed) that supports temporary-file-only memory usage and image insertion from disk. This requires **explicit approval under `SPEC.md`** and a check that it does not merge `INTERNET`/`ACCESS_NETWORK_STATE` into the manifest (the same check that already applies to ML Kit).

**Fidelity requirements for any option.** Page order, page dimensions in points, applied filter, white background, `cutLine` rendering, the `_1 … _n` multi-file naming for JPG/PNG (unchanged), and the size-target note must all be preserved. Note the existing trade-off documented at `Exporter.kt:133–136`: `PdfDocument` stores pictures losslessly and steers size with DPI; a JPEG-based writer steers size with quality instead. That changes the size/quality curve, so Stage 3 must compare visual output against a Stage-1 build at the same DPI before being accepted.

**Regression tests.** §10.2 A-4 and §10.3 items 7–9.
**Backward compatibility.** Output is a PDF at the same path with the same name; no stored data is touched.
**Rollback.** Reverting to `ByteArrayOutputStream` is **not** an acceptable fallback (§11); the fallback is Stage 2 with a page-count/DPI guard.

---

### 6.4 WP-04 — Export directory lifecycle and share retention (EXP-002)

| Field | Detail |
|---|---|
| Priority | **P1** |
| Status | Confirmed |
| Complexity | **Medium** |
| Owner | Single agent — `Exporter.kt` directory lifecycle (sequences after WP-03 unless a worktree is used) |
| Files | `Exporter.kt:76–77, 151–182, 190–235`; `export/Share.kt:11–22`; callers `ui/ResultScreen.kt:161,197`, `ui/ScanActions.kt:115,170`, `ui/LockerViewerScreen.kt:197–207, 248–259` |

**Root cause.** `exportDir()` is `File(ctx.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }` (line 77), called at the start of every `render` (line 153) and `renderPages` (line 207). Every export deletes every previously rendered cache export.

**Why it matters.** `Share.send` (`Share.kt:11–22`) passes `FileProvider` content URIs to a chooser with `FLAG_GRANT_READ_URI_PERMISSION`. The grant is read-only and transient, but **transient does not mean finished**. A receiving app may still hold an open file descriptor after the chooser closes, copy or upload asynchronously after the sheet is dismissed, or be resumed from Recents with the same URI. There is no callback that tells FlipScan a consumer has finished reading.

**Proposed solution — explicit retention policy.**
1. Each render writes into a fresh directory: `cacheDir/exports/<epochMillis>-<shortUuid>/`. Never reuse or wipe the parent.
2. **Never delete the directory belonging to the current session's export**, and never delete one younger than the retention window.
3. Sweep at app start and opportunistically before creating a new directory; delete only directories older than `EXPORT_RETENTION_MS` (proposed default **24 hours**, a named constant). Best-effort; must never fail a render.
4. Do not delete anything while an export is in progress (in-progress guard in the object).
5. Preserve the existing `Downloads.save()` copy to public `Downloads/FlipScan` — that is the durable artefact. Sharing still reads from cache, so the retention window is what protects the consumer.
6. Keep `Exporter.Result`'s contract unchanged so `ResultScreen`, `ScanActions` and `LockerViewerScreen` need no signature changes.

**Explicitly rejected.** (a) "Delete the directory when the chooser is dismissed" — no reliable dismissal signal exists and dismissal does not imply the consumer finished. (b) Unbounded growth — the age-based sweep caps disk use and `cacheDir` remains system-evictable.

**Residual risk, recorded.** A consumer reading a shared file more than 24 hours later may find it gone. Accepted; far better than invalidating shares within seconds, and invisible when the user saved to `Downloads/FlipScan` from the same dialog.

**Regression tests.** §10.2 A-5 and §10.3 item 10.
**Backward compatibility.** Temporary files only; `Downloads/FlipScan` untouched.
**Rollback.** If per-render directories misbehave, widen the retention window or stop sweeping — **do not** restore `deleteRecursively()` on every render (§11).

---

### 6.5 WP-05 — Temporary plaintext for locker export (SEC-002 / EXP-001 / SEC-005 / SEC-011, IMPL-004, IMPL-005)

| Field | Detail |
|---|---|
| Priority | **P1** |
| Status | Confirmed (location); Conditional (exposure window) |
| Complexity | **Medium** |
| Owner | Single agent — locker UI + store |
| Files | `ui/LockerViewerScreen.kt:173–183, 185–234, 236–274`; `MainActivity.kt:141–147`; optionally `data/LockerStore.kt:300–322` |

**Root cause.** `decryptPagesToTemp()` writes plaintext JPEGs to `cacheDir/locker_export/<docId>/` (lines 174–177). The startup maintenance block (`MainActivity.kt:141–147`) covers `filesDir/work` (`Work.clear`), `noBackupFilesDir/locker_tmp` (`LockerStore.clearTemps`, `LockerStore.kt:300–303`) and locker orphans (`LockerStore.sweep`, 305–322) — but **never** `cacheDir/locker_export`. If the process dies between decryption and the `finally` cleanup (lines 228–232, 269–272), plaintext remains until the next successful export of that same document, which deletes the directory first (line 176).

**Correcting the recommendation.** Revision 1, following Agents 1 and 3, proposed moving these files to `noBackupFilesDir/locker_export`. Three corrections:

1. **`noBackupFilesDir` does not encrypt anything.** It is ordinary app-private storage (`/data/user/<n>/<pkg>/no_backup`) containing plaintext bytes. Moving a file there does not make it cryptographically protected; it changes which system lifecycle applies.
2. **The genuine benefits are narrow:** excluded from Auto Backup and device-to-device transfer; not cleared by "Clear cache" or by low-storage eviction.
3. **`cacheDir` already has two properties `noBackupFilesDir` lacks:** it is also excluded from backup (cache is never backed up), and it *is* subject to system eviction and "Clear cache". For **plaintext**, system eviction is a feature. Moving plaintext to `noBackupFilesDir` makes it survive "Clear cache" — strictly worse unless a reliable in-app sweep exists.

**Proposed solution.** Both options are acceptable; Option A is recommended because it retains the system's cleanup as a backstop.

- **Option A (recommended) — keep `cacheDir`, add cleanup.**
  - Per-export directory (`cacheDir/locker_export/<docId>/<exportId>/`) so a new export never deletes another export's in-flight files (the current `deleteRecursively()` at line 176 can break a concurrent share of the same document).
  - Extend the startup maintenance block (`MainActivity.kt:141–147`) to delete the whole `cacheDir/locker_export` tree before any locker UI is reachable. This is the missing SEC-011 fix and closes the process-death window.
  - Fix IMPL-005: clean the directory itself rather than `pages.firstOrNull()?.parentFile`, so an all-pages-failed export leaves nothing behind.
  - Fix IMPL-004: move `decryptPagesToTemp` inside the existing `scope.launch` on `Dispatchers.IO` so decryption and writes never run on the UI thread. No visible flow change.
- **Option B — move to `noBackupFilesDir`.** Acceptable **only with** an unconditional startup sweep, because the cache-eviction safety net is lost. If chosen, update the `LockerStore` KDoc contract at lines 77–79 so documentation and behaviour agree.

**Cleanup definition (required for both options).**

| Event | Behaviour |
|---|---|
| Export/share completes | `finally` deletes decrypted pages and the per-export directory (existing behaviour, corrected) |
| Export/share throws | Same `finally` path |
| Process killed mid-export | Startup sweep deletes the entire locker export tree; nothing survives the next launch |
| App restarted normally | Startup sweep runs before the locker gate is shown (`MainActivity.kt:141`) |
| App backgrounded / session locked | No decrypted files are expected then; the next-start sweep is the guarantee, not a lock hook |
| Storage pressure | Under Option A the system may evict `cacheDir` at any time; acceptable because artefacts are reproducible |

**Explicit non-goal.** This does not keep plaintext off the filesystem during export. Export is an intentional decrypt → render → copy flow; the control is bounded lifetime, not absence.

**Regression tests.** §10.2 A-6 and §10.3 items 11–13.
**Backward compatibility.** Temporary files only; encrypted layout and formats untouched.
**Rollback.** Restore the previous temp location — but the startup sweep must remain, since it is the actual defect fix (§11).

---

### 6.6 WP-06 — Locker data-loss disclosure (SEC-008)

| Field | Detail |
|---|---|
| Priority | **P1** (disclosure only — no cryptographic change) |
| Status | Confirmed (no recovery mechanism exists); scenarios distinguished below |
| Complexity | **Low** |
| Owner | Single agent — locker UI strings + docs |
| Files | `ui/LockerScreen.kt` (gate / home / empty state); `docs/TESTING.md`; optionally `README.md` |

**Root cause.** The locker key exists only in the Android Keystore under a fixed alias (`LockerCrypto.kt:29`), and the encrypted records live only in app-private storage (`LockerStore.kt:88`). `allowBackup="false"` prevents cloud backup. There is no escrow, recovery passphrase or key export. The user is never told that losing app storage means losing every locker document.

**Correcting the claim.** Revision 1 asserted that "clearing app data destroys the Keystore key". That is **not verified** and must not be asserted. The three events differ:

| Event | App-private files | Keystore entries | Certainty |
|---|---|---|---|
| **Clear storage / clear data** (Settings or `pm clear`) | Deleted | Whether entries for this UID are also removed is platform- and OEM-dependent and is not a documented developer guarantee. Must be measured on the target device | Mechanism uncertain; **user outcome certain** — documents are gone either way, because the ciphertext is deleted and a key alone is useless |
| **Uninstall** | Deleted | Android documents key removal on uninstall; reinstall generates a fresh key | Outcome certain: documents gone; reinstall starts empty |
| **Factory reset / new device** | Deleted | Device-wide wipe | Certain: documents gone |

The user-facing message must be about **outcome**, which is provable, not key internals, which are not. All three lead to the same result: **Locker documents cannot be recovered.**

**Proposed solution.**
1. First-entry disclosure in the locker gate or home, shown before or at the first import: locker documents exist only on this device, are protected by the device's secure hardware, and **cannot be recovered** if app storage is cleared, the app is uninstalled, or the device is reset or replaced. Add a one-line recommendation to keep a copy outside the locker for documents that matter.
2. A persistent, low-emphasis note in the locker home empty state — not a dismissible modal on every visit.
3. Update `docs/TESTING.md` with the destructive tests in §10.4.
4. Optional and **not scheduled**: a user-supplied recovery passphrase that wraps the Keystore key. That is a cryptographic design change belonging with the deferred SEC-001 work and must not be improvised.

**Non-goals.** No change to key, alias, envelope or storage layout. No promise of recovery. No claim of backup.

**Regression tests.** §10.4 (destructive, disposable documents only).
**Backward compatibility.** UI text only.
**Rollback.** Removing the text restores an undisclosed data-loss risk and must be recorded as accepted.

---

### 6.7 WP-07 — Biometric binding of the locker key (SEC-001) — **DEFERRED**

| Field | Detail |
|---|---|
| Priority | **DEFERRED** — not scheduled, not estimated, not to be implemented in Stage 5 |
| Status | Confirmed gap; remediation blocked on prerequisite design |
| Complexity | **High** (currently unbounded — the migration design does not exist) |
| Files (if ever approved) | `data/LockerCrypto.kt`, `data/LockerStore.kt`, plus a new migration component |

**Current state, verified.** `LockerCrypto.secretKey()` builds the key with `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, GCM, no padding, 256-bit, `setRandomizedEncryptionRequired(true)` (`LockerCrypto.kt:48–58`). It does **not** call `setUserAuthenticationRequired`, `setUserAuthenticationParameters`, `setUserAuthenticationValidityDurationSeconds`, `setInvalidatedByBiometricEnrollment` or `setUnlockedDeviceRequired`. The class comment (lines 23–26) documents this as deliberate. The biometric prompt (`ui/LockerAuth.kt`) is an app-level UI gate; the key is usable by any code in the process that can reach `LockerCrypto`.

**Corrected semantics.** Revision 1 described the platform behaviour imprecisely. The accurate position — which determines whether the fix is safe — is:

- `setUserAuthenticationRequired(true)` makes each key use depend on prior user authentication. **How much** prior authentication, and **which** authenticators count, are separate settings, not consequences of this flag.
- *Per-use* authentication (validity duration 0) requires an auth event for every operation, normally via `BiometricPrompt` with a `CryptoObject`. *Time-bound* authentication (validity duration > 0) permits operations within a window after one successful authentication, without a prompt per operation.
- On modern API levels the authenticator set is explicit (biometric and/or device credential). **When device credential is permitted, a device credential satisfies the key** — and keys permitting device credential are not invalidated merely because a new biometric is enrolled, because the credential still guards them. The claim "every biometric enrolment change destroys the documents" is therefore **not generally true**; it depends on the chosen authenticator configuration.
- `setInvalidatedByBiometricEnrollment` controls invalidation on new biometric enrolment. It is not a device-credential-change control, and its applicability depends on the authenticator configuration. Where invalidation applies, failure surfaces at **use** time as a permanent-invalidation exception, not at generation time.
- Device credential fallback therefore has a direct cryptographic consequence: permitting it keeps the locker usable after a biometric change, at the cost of weaker per-operation assurance. The current UI already offers credential fallback, so any future design must reconcile the two.

**Why deferred rather than scheduled.**
1. Binding constrains *when* the key can be used. `LockerStore` encrypts during import, edits and OCR writes; `LockerViewerScreen` decrypts during viewing and export. Several of those paths are not in front of a prompt today, so a naive binding would break editing, OCR and export — not just unlocking.
2. Changing the key's parameters on the **existing alias** does not retroactively protect existing records.
3. Replacing the key without re-encryption makes every existing record undecryptable.
4. A safe migration must re-encrypt every record — index, pages, sidecars, thumbnails — resumably, with no window where data is unreadable.

**Prerequisites before any implementation.** A separate, independently reviewed design covering at least:

| # | Requirement |
|---|---|
| P-1 | **Interrupted migration.** Resumable; must never leave a record readable by neither key. Candidate: write new-key ciphertext to side files, verify each by decrypting, commit by rename, keep the old key until the index is fully rewritten and verified |
| P-2 | **Old-key availability.** The old key must remain accessible for the whole migration. Since keys are addressed by alias, this needs a second alias for the new generation plus explicit generation tracking in the encrypted index — an envelope/metadata change that touches the encryption format |
| P-3 | **Device credential fallback.** Decide whether the bound key permits device credential. "Biometric only" makes the locker unusable without biometrics and after a biometric change; "credential permitted" keeps it usable. Record the decision |
| P-4 | **Recovery scenarios.** Behaviour when: the key is permanently invalidated mid-migration; no biometric is enrolled; the user changes their credential; the app is killed during migration; migration completed but the new key is later invalidated |
| P-5 | **Background work.** Classify every current key use as prompt-capable or not. Non-promptable uses need either a time-bound validity window or a flow redesign |
| P-6 | **Explicit user consent.** Re-keying is a one-way operation on user data and must be surfaced, not silent |
| P-7 | **Point of no return.** Define exactly when rollback stops being possible and what the user is told |

Until P-1 … P-7 exist and are reviewed, **no code change to `LockerCrypto` or the key alias may be made**. The current design is documented, internally consistent and does not lose data; replacing it with a partially specified one is a worse outcome than the current gap.

---

## 7. Work packages — P2 (not required for the next release)

These are recorded so they are not lost, but they are not part of the release gate. Each is independent and low-coupling unless stated.

| ID | Item | Files | Approach | Complexity |
|---|---|---|---|---|
| WP-08 | OCR-001 — stop calling a format check "Verified" | `ui/DigitalPins.kt:169–175`; `data/PinFormat.kt` usage in `data/LockerStore.kt` | Rename the state to something factual ("Format OK" / "Check digits"), and make clear the value is read off the document, not confirmed by any authority. No change to `numberVerified`'s semantics or storage | Low |
| WP-09 | OCR-002 — PAN `fixDigits` conservatism | `data/LockerOcr.kt:96–101, 124–134` | Do not silently rewrite ambiguous characters. Prefer marking the candidate as low-confidence and asking the user to confirm, or normalise only when the result is the single valid candidate. Must not alter the canonical `normalize()` used elsewhere | Medium |
| WP-10 | PERF-002 — legacy rotate sampling | `data/PageEditor.kt:44–61` | Reuse the existing `decodeSampled` path for the legacy fallback so the destructive rotate never decodes full resolution. Only affects scans without an `.orig` sidecar | Medium |
| WP-11 | PERF-003 — capture peak memory | `scan/PageProcessor.kt:27–41, 95–111` | Release the source `Mat` as soon as the warped output exists; measure on a low-RAM device before and after | Medium |
| WP-12 | SEC-004 — surface decryption failures | `data/LockerStore.kt:326–333` | Distinguish "missing page" from "page failed to decrypt" and show a per-document warning. Do not change the storage format or silently delete anything | Low |
| WP-13 | SEC-007 — biometric policy | `ui/LockerAuth.kt` | Confirm the authenticator strength used by the system prompt and whether confirmation is required; align with whatever WP-07 eventually decides | Low |
| WP-14 | REL-007 — release keep rules | `app/proguard-rules.pro` | Verification-led: run the release smoke test first, add a keep rule only for an observed failure. No speculative rules for libraries that already ship consumer rules | Low |
| WP-15 | IMPL-005 — empty-directory cleanup | `ui/LockerViewerScreen.kt:228–232, 269–272` | Folded into WP-05; listed here only for traceability | Low |
| WP-16 | IMPL-002 — retry-ladder amplification | `Exporter.kt:137–145` | Folded into WP-03 Stage 2 | Medium |
| WP-17 | 16 KB page-size support (native code) | build config, OpenCV native libs | Tracked, not scheduled. Play is reported to require 16 KB page-size support for updates from 1 Feb 2027, which affects any app shipping `.so` files. Verify the requirement, then measure the current APK's page alignment before planning work | Unknown until verified |

---

## 8. Backward compatibility and encrypted-document migration

### 8.1 Impact matrix

| Work package | User data | Encrypted records | Encryption format | Migration needed |
|---|---|---|---|---|
| WP-01 toolchain | None | None | None | No |
| WP-02 signing | None | None | None | No — but the signing identity must match existing artefacts exactly |
| WP-03 PDF | None (output only) | None | None | No |
| WP-04 export dirs | Cache only | None | None | No |
| WP-05 temp plaintext | Cache only (under Option A) or `noBackupFilesDir` (Option B) | None | None | No |
| WP-06 disclosure | UI text only | None | None | No |
| WP-07 (deferred) | **All locker documents** | All records re-encrypted | **Yes** | **Yes — mandatory, resumable, verified** |

### 8.2 Statement of what must not change

- `LockerCrypto`'s envelope (`FSLK1 | ivLen | iv | ciphertext+tag`), the alias `flipscan_locker_v1`, and the key size/mode are **frozen** for every item in §6 except the deferred WP-07.
- `LockerStore`'s on-disk layout (`filesDir/locker/index.bin`, `docs/<uuid>/p_i.jpg.enc`, `p_i.orig.jpg.enc`, `p_i.quad.enc`, `thumb.jpg.enc`) is unchanged by this plan.
- Quads stay normalised and ordered TL, TR, BR, BL; rotation behaviour is untouched.
- The release certificate must remain the same identity across every build offered for update.

### 8.3 Migration risks that exist today (unchanged by this plan)

| Risk | Detail | Handling |
|---|---|---|
| Locker contents are device-bound | No escrow, no backup, no recovery | WP-06 discloses it; no technical mitigation scheduled |
| Corrupt page is indistinguishable from missing page | `LockerStore.kt:326–333` returns `null` | WP-12 (P2) improves the message; no data is deleted |
| Unreadable index is not overwritten | `readAll` returns a failure and `import` refuses to overwrite (`LockerStore.kt:102–103, 119`) — existing, correct behaviour | Preserve; must not be "simplified" during any refactor |
| App data cleared / uninstall / reset | Documents unrecoverable, by design | Disclosed in WP-06 |

---

## 9. Parallel vs sequential execution

### 9.1 Execution waves

**Wave 1 — parallel, no shared files**

| Package | Files | Owner rule |
|---|---|---|
| WP-01 toolchain | root `build.gradle.kts`, `app/build.gradle.kts` (SDK levels) | Sole owner of both build files for this wave |
| WP-03 stage 1+2 | `Exporter.kt` (PDF write path) | Sole owner of `Exporter.kt` for this wave |
| WP-05 temp cleanup | `LockerViewerScreen.kt`, `MainActivity.kt` | Sole owner of both |
| WP-06 disclosure | `LockerScreen.kt`, docs | Sole owner |

**Wave 2 — after Wave 1 merges**

| Package | Reason for sequencing |
|---|---|
| WP-02 signing | Needs the final `app/build.gradle.kts` from WP-01; no concurrent build-file edits |
| WP-04 export dirs | Needs the final `Exporter.kt` from WP-03; no concurrent `Exporter.kt` edits |
| WP-03 stage 3 (if approved) | Depends on the assembler decision (§12-Q2) and on WP-04's directory layout |

**Wave 3 — P2 only, after the release gate closes**

WP-08 … WP-14, grouped by file: WP-08 with WP-13 (both locker UI/auth, different files), WP-09 with WP-12 (both data layer, different files), WP-10 with WP-11 (both page processing, different files).

### 9.2 Coordination rules

1. Exactly **one agent owns a file** at a time. Ownership is stated in the package table, not inferred.
2. `Exporter.kt` and `app/build.gradle.kts` may never be edited concurrently in the same working tree. If genuine parallelism is required, each agent works in its own git worktree and the later branch is rebased before merge.
3. Two build files, two agents, one wave → same rule: `app/build.gradle.kts` is owned by one agent, and `build.gradle.kts` by that same agent, because the AGP version and the `compileSdk` it supports must be changed together.
4. File a short note in the work log for any file that had to change hands, stating what was already done and verified.
5. No agent rebases or resolves conflicts in another agent's package.

---

## 10. Test strategy

### 10.1 Current state — no automated coverage exists

`AGENTS.md` states there are no unit tests, no instrumentation tests, no test source sets and no CI, and explicitly warns against inventing a `./gradlew test` target. Any automated testing in this plan is **new infrastructure**, not existing coverage, and adding it requires approval (§12-Q5) because it adds dependencies.

### 10.2 Infrastructure that must be added before automated tests are possible

| Item | Where | Note |
|---|---|---|
| `app/src/test/java/…` source set | new | JVM unit tests for pure logic |
| `app/src/androidTest/java/…` source set | new | Needs a device or emulator |
| `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` | `app/build.gradle.kts` `defaultConfig` | Touches a file owned by WP-01/WP-02 — must be sequenced, not concurrent |
| JVM test dependency (JUnit 4) | `app/build.gradle.kts` | Approval required |
| Instrumentation dependencies (`androidx.test.ext:junit`, `androidx.test:runner`) | `app/build.gradle.kts` | Approval required |
| A non-debuggable-unsafe path for Keystore tests | instrumentation | Keystore and `LockerStore` are only testable on-device |
| `AGENTS.md` correction | root | The "no tests" statement becomes false once this lands; it must be updated in the same change |

### 10.3 Automated tests (to be written; none exist today)

| ID | Target | Type | Assertion |
|---|---|---|---|
| A-1 | `PinFormat` (masking, normalisation, Aadhaar checksum) | JVM unit | Masking hides the first eight Aadhaar digits and all but the last four elsewhere; checksum accepts known-good and rejects mutated numbers; the canonical `normalize()` is idempotent |
| A-2 | `Naming.baseName` / `Layouts` geometry | JVM unit | Sanitisation of user names; page box dimensions in points match `mm × 72 / 25.4` |
| A-3 | `LockerCrypto` round-trip | Instrumentation | Seal/open round-trip; a flipped byte fails; a truncated record fails; two seals of identical plaintext produce different ciphertext (fresh IV) |
| A-4 | PDF export page count and order (WP-03) | Instrumentation | Exported file opens, page count equals sheet count, page sizes match the plan, first page of a two-page document is page 1 (order preserved). Memory assertion is a manual measurement, not a unit test |
| A-5 | Export directory retention (WP-04) | Instrumentation | A render does not delete a directory younger than the retention window; an older directory is swept; the current session's directory survives repeated renders |
| A-6 | Locker temp cleanup (WP-05) | Instrumentation | After a completed export and after a forced failure, the locker export tree is empty; a simulated "leftover" tree is removed by the startup routine |
| A-7 | `LockerStore` import/sweep invariants | Instrumentation | Import is atomic (no half-written doc visible); an unreadable index is never overwritten; `sweep` removes orphans and `.tmp` only |

Tests A-3, A-6 and A-7 use a **dedicated test document directory** and must not read or mutate any real locker content on the device.

### 10.4 Manual device verification (cannot be automated)

Run per `docs/TESTING.md` on a physical device. Automated tests above do not replace these.

| # | Scenario | Expected |
|---|---|---|
| 1 | Cold start, camera permission, capture a card both ways | Capture, detection and auto-capture unchanged |
| 2 | Crop with corner drag, rotate, page reorder | Loupe, quads and rotation unchanged; no visible jank |
| 3 | Save PDF/JPG/PNG with a filter and a size target | Output identical to the pre-change build for the same settings (Stage-3 comparison, WP-03) |
| 4 | Share from the result screen, then export a different scan while the first share is still open | First share still resolves; WP-04 regression |
| 5 | Locker unlock: biometric success, biometric cancel, device credential fallback | Gate behaves exactly as before; session starts only on success |
| 6 | Background the app while the locker is open; wait past the session cap | Locks on `ON_STOP`; 5-minute cap still enforced |
| 7 | 10+ page document → PDF with the size target cleared | Completes without OOM on a low-RAM device; record peak memory |
| 8 | 10+ page document → PDF with a tight size target | Completes; note text appears if the target is unreachable |
| 9 | Card-mode locker document → PDF | Two sides on one sheet, unchanged from before |
| 10 | Share a locker export, keep the sheet open, start another export | First share still opens; no cache wipe |
| 11 | Force-stop during a locker export, relaunch | No plaintext remains under `cacheDir/locker_export` (checked with `run-as` on a **debug** build) |
| 12 | Locker export → Downloads, then Open from the exported-copies list | Opens the saved copy; no plaintext temp left behind |
| 13 | Locker edit / OCR read-again after WP-05 changes | Editing and OCR still work; nothing prompts unexpectedly |
| 14 | Release (R8) build smoke test | No release-only crash in Keystore, ML Kit, OpenCV, CameraX |
| 15 | Highest supported Android version: predictive back, insets, orientation on a large screen | No regression from WP-01 |

### 10.5 Destructive tests — rules

1. Destructive tests (clear data, uninstall, factory reset) run **only** with documents created for the test, never with documents the user cares about.
2. Create the test set from synthetic images, name them recognisably (e.g. `TEST-DISPOSABLE 1`), and record how many exist before the test.
3. Run destructive tests on a device or profile that holds no real locker content, or export and set aside anything needed first.
4. After the test, record the observed outcome and delete any leftovers from `Downloads/FlipScan`.
5. Never run a destructive test on a device holding production data without explicit user confirmation.

| Destructive scenario | Question to answer | Expected user outcome |
|---|---|---|
| Clear app storage with test documents present | Are the documents gone? Are Keystore entries also removed? (Record the observation — do not assume) | Documents unrecoverable |
| Uninstall then reinstall | Does the locker open empty rather than showing stale entries? | Empty locker, no crash |
| Kill the process mid-export, then relaunch | Is any plaintext left behind? | None |

---

## 11. Rollback strategy

**Principle:** reverting is only valid when the pre-change behaviour was acceptable. For EXP-002 and EXP-004 the pre-change behaviour **is the defect**, so "revert" is not a rollback — the correct fallback is a narrower mitigation.

| Change | Previous behaviour acceptable? | Rollback / fallback | Integrity check after rollback |
|---|---|---|---|
| WP-01 toolchain | Yes (the app builds and runs; it merely cannot be submitted) | Revert the SDK levels; keep AGP and SDK changes together so the pair is consistent | `assembleDebug`, `assembleRelease`, `bundleRelease` all succeed; lint at baseline; app launches |
| WP-02 signing | Yes (manual signing still works) | Revert the `signingConfig`; continue manual signing | Only if an artefact was produced: verify its certificate fingerprint is unchanged |
| WP-03 PDF | **No** — retained bitmaps can OOM | **Not a revert.** Fall back to Stage 2 (direct file output + bounded ladder) plus a page-count/DPI guard, and keep the user-visible note | Open the produced PDFs in at least two viewers; page count, order, size and orientation match the pre-change output; compare a sample page visually |
| WP-04 export dirs | **No** — every render invalidates active shares | **Not a revert.** Widen the retention window or disable the sweep (`EXPORT_RETENTION_MS` to a very large value). Never restore per-render deletion | Share from an older session still resolves; no directory is deleted while in use |
| WP-05 temp plaintext | **No** — plaintext survives process death with no sweep | **Not a revert of the sweep.** The startup sweep stays. If the location change causes problems, revert only the location and keep the sweep and the per-export subdirectory | Force-stop mid-export, relaunch, confirm no plaintext under either candidate location |
| WP-06 disclosure | **No** — an undisclosed data-loss risk | Text can be removed, but the residual risk must be written down and accepted by the owner | Not a data operation; no integrity check |
| WP-07 (deferred) | n/a | **No rollback exists once re-encryption begins.** This is why the design must define the point of no return (P-7) before any code is written | Full decrypt-verify pass over every record before and after, with a count of records compared |

**Pre-change baseline to capture (once, before Wave 1):**

1. Record the build outputs: `assembleDebug`, `assembleRelease`, `bundleRelease` succeed; lint warning count.
2. Record current APK/AAB sizes for comparison (the README figures are from an older version).
3. Export one PDF, one JPG and one PNG from a fixed multi-page scan and keep them as the visual baseline for WP-03.
4. Record the current `Downloads/FlipScan` contents and the locker document count (counts only — no content inspection).

Without this baseline, "the output is unchanged" cannot be demonstrated, and a rollback decision becomes guesswork.

---

## 12. Unresolved decisions (approval required before Stage 5)

| # | Question | Blocks | Options |
|---|---|---|---|
| Q1 | ~~What target API level does Play currently require, and which AGP supports it?~~ **RESOLVED 25 Nov 2026** | — | Answer: API **36** is mandatory for new apps and updates since 31 Aug 2026; the 1 Nov 2026 extension window has closed. Minimum stable **AGP 8.9.1**, which requires **Gradle 8.11.1+**. API 34 also fails the separate *availability* bar (API 35) for existing apps. Sources: Play Console Help "Target API level requirements", Android developers "Meet Google Play's target API level requirement" (updated 2026-10-28), and the AGP minimum-tools-per-API table |
| Q2 | Which PDF assembler for WP-03 Stage 3? | WP-03 stage 3 | (a) minimal in-repo writer using `DCTDecode`; (b) approved Apache-2.0 library with scratch files; (c) ship Stage 1+2 only, with a page-count guard, and revisit |
| Q3 | Keep locker export temps in `cacheDir` (Option A) or move to `noBackupFilesDir` (Option B)? | WP-05 | A recommended: system eviction remains a backstop, sweep added. B only with an unconditional sweep |
| Q4 | Value of the export retention window | WP-04 | Proposed 24 h; a shorter window risks long-running consumers, a longer one uses more cache |
| Q5 | Approve new test dependencies and the `androidTest` source set? | §10.2 | Required for any automated coverage; blocked by `SPEC.md` until approved |
| Q6 | ProGuard keep rules now or only after an observed release failure? | WP-14 | Verification-led recommended; preemptive rules risk masking a real shrinker problem |
| Q7 | Is `app.flipscan` already published on Google Play? | WP-02 | Determines whether the existing key is an upload key or a first identity; must be answered before any signing configuration |
| Q8 | Page-count/DPI guard threshold for WP-03 | WP-03 stage 2 | Must come from measurement (manual test 7), not a guess |
| Q9 | Should the locker disclose a "keep a copy outside the locker" recommendation, or only the loss warning? | WP-06 | Product wording decision |
| Q10 | Which AGP line: the minimum 8.9.1 (Gradle 8.11.1, smallest jump) or a newer line such as 8.13 / 9.x (higher Gradle and JDK floors)? | WP-01 | Minimum-risk recommendation: 8.9.1 first, shipping; upgrade further only when a dependency or policy requires it. Any choice must be recorded with its Gradle/JDK pairing |
| Q11 | Is 16 KB page-size support required for this app before 1 Feb 2027? | WP-17 (future) | FlipScan ships native code (OpenCV), and Play is reported to require 16 KB page-size support for updates from Feb 2027. Verify in Play Console; if required, it constrains the AGP/NDK line chosen in Q10 |

**Q1 is closed.** Two external facts are now established rather than assumed: API 36 is the submission gate and API 34 also fails the availability bar, so REL-001 is no longer "policy claim only" — it is a documented, dated requirement. What remains unverified is only whether *this* listing is already published (Q7), which decides whether WP-02 is a first key or an upload key.

---

## 13. Complexity and priority summary

| ID | Work package | Priority | Complexity | Wave | Owner conflict |
|---|---|---|---|---|---|
| REL-001/002/003/004 | WP-01 toolchain | P0 | High | 1 | `app/build.gradle.kts` |
| REL-006/015 | WP-02 signing | P0 | Low (code) / Medium (process) | 2 | `app/build.gradle.kts` |
| EXP-004/IMPL-001/IMPL-002 | WP-03 PDF | P1 | High | 1 (stages 1–2), 2 (stage 3) | `Exporter.kt` |
| EXP-002 | WP-04 export dirs | P1 | Medium | 2 | `Exporter.kt` |
| SEC-002/EXP-001/SEC-005/SEC-011/IMPL-004/IMPL-005 | WP-05 temp plaintext | P1 | Medium | 1 | `LockerViewerScreen.kt`, `MainActivity.kt` |
| SEC-008 | WP-06 disclosure | P1 | Low | 1 | `LockerScreen.kt` |
| SEC-001 | WP-07 | **DEFERRED** | High (unbounded) | — | `LockerCrypto.kt`, `LockerStore.kt` |
| OCR-001/005/013 | WP-08 | P2 | Low | 3 | `DigitalPins.kt` |
| OCR-002 | WP-09 | P2 | Medium | 3 | `LockerOcr.kt` |
| PERF-002 | WP-10 | P2 | Medium | 3 | `PageEditor.kt` |
| PERF-003 | WP-11 | P2 | Medium | 3 | `PageProcessor.kt` |
| SEC-004 | WP-12 | P2 | Low | 3 | `LockerStore.kt` |
| SEC-007 | WP-13 | P2 | Low | 3 | `LockerAuth.kt` |
| REL-007 | WP-14 | P2 | Low | 3 | `proguard-rules.pro` |

---

## 14. Revision history

### Revision 4 — 25 November 2026 — Stage 5, Wave 1 implemented

Approved scope: **WP-01, WP-03 stages 1–2, WP-05 (Option A), WP-06.** Decisions taken: Q2 = stages 1+2 then measure (no new dependency); Q7 = `app.flipscan` not yet published (so WP-02 will create a first-publication identity); Q10 = AGP 8.9.1 minimum.

**Files changed**

| File | Change |
|---|---|
| `app/build.gradle.kts` | `compileSdk` and `targetSdk` 34 → **36** |
| `build.gradle.kts` | AGP 8.5.2 → **8.9.1** |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.9 → **8.11.1** (AGP 8.9 floor) |
| `export/Exporter.kt` | `pdfBytes` → `writePdf` writing through `FileOutputStream`; `pdfToTarget` rewritten to use scratch/best files with a square-root dpi estimate (`MAX_PDF_ATTEMPTS = 4`, `MIN_PDF_DPI = 50`); added `replace()`; `render`/`renderPages` PDF branches now move a file instead of writing a `ByteArray` |
| `ui/LockerViewerScreen.kt` | Per-export temp directory `locker_export/<docId>/<timestamp>-<uuid>/`; decryption moved into `Dispatchers.IO` inside the existing `scope.launch`; cleanup runs under `NonCancellable` and removes the directory itself (IMPL-004, IMPL-005) |
| `data/LockerStore.kt` | Added `clearExportTemps()` for the `locker_export` tree |
| `MainActivity.kt` | Startup maintenance now also calls `clearExportTemps` (SEC-011) |
| `ui/LockerScreen.kt` | Gate gains a fourth bullet and the empty state gains a paragraph: locker documents are only on this phone, never backed up, and cannot be recovered |
| `docs/TESTING.md` | New "Locker export, sharing and retention" and "Destructive locker tests" sections; disclosure check added to the locker list |

**Verification actually executed**

| Check | Result |
|---|---|
| `sdkmanager --install "platforms;android-36" "build-tools;36.0.0"` | Installed; `platforms` now lists `android-34, android-36`, `build-tools` `34.0.0, 36.0.0` |
| `assembleDebug` | SUCCESSFUL |
| `lintDebug` | SUCCESSFUL — 20 findings, up from the 12 baseline; all 8 additions are `UseKtx` style suggestions from the newer lint, no errors, no new categories |
| `assembleRelease` | SUCCESSFUL |
| `bundleRelease` | SUCCESSFUL |
| Merged release manifest | `targetSdkVersion="36"`, `minSdkVersion="24"`, **no `INTERNET`, no `ACCESS_NETWORK_STATE`** — the invariant survived the toolchain upgrade |
| `jarsigner -verify` on the AAB | `jar is unsigned` — confirms WP-02 is still outstanding and no accidental signing occurred |
| Sizes | debug 135,219,665 B; release APK 58,692,222 B; AAB 31,116,929 B (pre-Wave-1: 135,174,166 / 58,724,986 / 29,606,821) |

**Observation to investigate before upload:** the AAB grew by ~1.5 MB (29.6 → 31.1 MB) across the toolchain change while the release APK shrank slightly. The cause is not established and is not attributed to any single change. It matters only for download size, not correctness, but should be checked with the Play upload's size report.

**Not done, by design:** WP-02 (signing), WP-04 (export retention), WP-03 stage 3, all P2 items. **Not verified:** every device item in §10.4, including the memory measurement that decides Q8 and the PDF visual-parity comparison against the captured baseline. No device testing has been performed or claimed.

### Revision 3 — 25 November 2026 — verification addendum

Stage 4 was closed at revision 2. This addendum records only facts established afterwards; **no new work packages and no reclassification** were introduced.

1. **Q1 resolved from primary sources.** API 36 is the Google Play submission gate for new apps *and* updates since 31 Aug 2026, the 1 Nov 2026 extension window has closed, and API 34 also fails the separate availability bar (API 35) for existing listings. Minimum stable AGP for API 36 is 8.9.1, requiring Gradle 8.11.1+. REL-001/002/003 are therefore promoted from "policy claim, re-confirm before executing" to documented requirements.
2. **WP-01 scope corrected upward.** The local toolchain was inspected: Gradle wrapper is **8.9**, and only **SDK Platform 34 / Build-Tools 34.0.0** are installed. WP-01 is a four-file change (`app/build.gradle.kts`, root `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, plus the SDK installation) rather than a two-number edit. `sdkmanager` is present, so the platform install is a machine prerequisite, not a blocker.
3. **Q10 added** — which AGP line to adopt, since newer lines raise the Gradle and JDK floors further.
4. **Q11 / WP-17 added** — 16 KB page-size support, reported to be required for updates from 1 Feb 2027 and relevant because FlipScan ships OpenCV native libraries. Tracked, not scheduled, and not assumed.

### Revision 2 — 25 November 2026 — technical review corrections

**Corrections applied**

1. **EXP-004 / PDF (WP-03).** Withdrew the claim that `PdfDocument` supports page-by-page incremental output; it does not. Re-read `Exporter.kt` and `SheetRenderer.kt` and identified the dominant memory cost as *retained page bitmaps* (`SheetRenderer.kt:43, 52` with `recycle = false` at `Exporter.kt:65`), not the `ByteArrayOutputStream`, which is secondary. Added IMPL-002 (the seven-step re-render ladder amplifies the cost). Proposed a three-stage solution: direct file output, bounded retry ladder, then a genuine streaming assembler with two concrete options (in-repo `DCTDecode` writer or an approved library), including explicit fidelity requirements and the quality/size trade-off this changes.

2. **SEC-001 (WP-07).** Corrected the description of `setUserAuthenticationRequired`, per-use vs time-bound authentication, authenticator selection, device-credential consequences, and the conditional applicability of `setInvalidatedByBiometricEnrollment`. Removed the assertion that any biometric enrolment change always destroys the documents. **Deferred** the item: no implementation, no estimate, blocked on an independently reviewed key-management and migration design, with prerequisites P-1 … P-7 covering interrupted migration, old-key availability, credential fallback and recovery scenarios.

3. **SEC-002 / EXP-001 (WP-05).** Clarified that `noBackupFilesDir` is private app storage that does **not** encrypt anything, and that it is *more* persistent than `cacheDir` (it survives "Clear cache" and low-storage eviction). Documented the real benefit (backup and device-transfer exclusion) and the real cost. Recommended keeping `cacheDir` with a mandatory startup sweep as Option A, with the move as a documented Option B. Added an explicit cleanup table covering completion, failure, process death and restart.

4. **EXP-002 (WP-04).** Replaced "per-render directories" with an explicit retention policy: unique directory per render, never delete the current session's export, never delete anything younger than a 24-hour retention window, never delete during an export. Removed any implication that share-sheet dismissal means the consumer has finished reading.

5. **REL-006 (WP-02).** Added mandatory verification of the existing keystore and certificate before configuration; prohibited generating a replacement key under any circumstance; distinguished first Play publication from an update to an existing listing, including the upload-key reset path.

6. **SEC-008 (WP-06).** Replaced the unverified claim that clearing app data destroys the Keystore key with three distinct scenarios (clear data, uninstall, factory reset), stating explicitly what is documented and what must be measured. Reframed the user-facing warning around the provable outcome: locker documents cannot be recovered.

7. **Testing (§10).** Separated automated tests from manual device verification; listed the infrastructure that must be added (test source sets, runner, dependencies, an `AGENTS.md` correction) and flagged it as approval-gated; added the rule that destructive tests use disposable, clearly named test documents.

8. **Parallel execution (§9).** Added a shared-file conflict table and an ownership rule: one owner per file, `Exporter.kt` and `app/build.gradle.kts` never edited concurrently, worktrees required if genuine parallelism is needed.

9. **Rollback (§11).** Removed the recommendation to revert EXP-002 and EXP-004 to their previous behaviour, because that behaviour is the defect being fixed. Each row now states a narrower mitigation and an integrity check, and requires a pre-change baseline capture.

10. **Priority consistency (§3).** Introduced a single scale and applied it everywhere. EXP-004 moved from "release blocker" to **P1** with a stated rationale; EXP-002 is **P1**; SEC-001 is **DEFERRED**.

**Added during the same revision**

- §4.6 — new findings IMPL-001 … IMPL-005, identified by re-reading source rather than by accepting the audit.
- §8.2 — explicit "must not change" list for the crypto envelope, alias and storage layout.
- §12 — nine unresolved decisions, each tied to the work package it blocks.
- §11 — baseline capture requirement, without which change-versus-regression cannot be demonstrated.

**Unchanged from revision 1:** all specialist finding IDs, severities where not reclassified, file paths and line references, the P0 set (REL-001/002/003, REL-006), the statement that no source has been modified, and the requirement for explicit approval before Stage 5.

### Revision 1 — 25 November 2026 — initial plan

Consolidated `docs/FLIPSCAN_V070_AUDIT.md` and the six agent reports into a first implementation plan.

---

**END OF PLAN** — Stage 4 only. No application source, Gradle configuration, database schema, encryption format, key, signing credential or user document has been created, modified or deleted. Nothing has been committed or pushed. Stage 5 requires explicit approval.

