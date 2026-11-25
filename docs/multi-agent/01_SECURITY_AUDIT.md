# Agent 1 — Digital Locker Security Audit

## Repository Revision Inspected
- Branch: main
- Commit: 7ac29e3
- Files reviewed:
  - app/src/main/java/app/flipscan/data/LockerCrypto.kt
  - app/src/main/java/app/flipscan/data/LockerStore.kt
  - app/src/main/java/app/flipscan/ui/LockerAuth.kt
  - app/src/main/java/app/flipscan/ui/LockerScreen.kt
  - app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt
  - app/src/main/java/app/flipscan/MainActivity.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/app/flipscan/data/LockerOcr.kt (supporting)
  - app/src/main/java/app/flipscan/data/Downloads.kt (supporting)
  - app/src/main/java/app/flipscan/data/Settings.kt (supporting)
  - app/src/main/java/app/flipscan/FlipScanApp.kt (supporting)

## Findings

| Field | Description |
|---|---|
| Finding ID | SEC-001 |
| Category | Security |
| Severity | High |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerCrypto.kt |
| Lines | 48-58, 66-68 |
| Evidence | The Android Keystore key is generated with `KeyGenParameterSpec.Builder` specifying only `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, GCM block mode, no padding, 256-bit size, and `setRandomizedEncryptionRequired(true)`. Crucially, `setUserAuthenticationRequired(true)` is NOT called, and `setInvalidatedByBiometricEnrollment(false)` is the default (not explicitly set to true). The key requires no biometric or credential to use — any code in the app process can call `Cipher.init(DECRYPT_MODE, key, ...)` and decrypt. The class comment (lines 23-26) explicitly documents this as an intentional design choice so the key stays usable for background work. |
| Impact | Biometric authentication (BiometricPrompt) is purely a UI gate. The encryption key itself offers no additional authorization. If an attacker can invoke `LockerStore.decrypt`/`pageBytes`/`thumbBytes` from within the app process (e.g., via a vulnerability that allows arbitrary code execution, or a malicious component sharing the UID), all locker records are decryptable without ever passing the biometric prompt. The cryptographic boundary does not enforce authentication. |
| Recommendation | Bind the key to biometric authentication by calling `.setUserAuthenticationRequired(true)` on the `KeyGenParameterSpec.Builder`, and consider `.setInvalidatedByBiometricEnrollment(true)` if re-enrollment should invalidate existing documents. This forces BiometricPrompt confirmation at the crypto layer for every decrypt operation. Accept that biometric re-enrollment will invalidate existing ciphertext, or implement a key-rewrap flow. |
| Verification | Instrumented test: attempt `LockerCrypto.decrypt()` on a sealed blob without showing BiometricPrompt; confirm it throws `UserNotAuthenticatedException` (or similar) when the key is authentication-bound. |

<|close|>argument<|sep|><|open|>argument key="newString" type="string"<|sep|>
| Finding ID | SEC-001 |
| Category | Security |
| Severity | High |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerCrypto.kt |
| Lines | 48-58, 66-68 |
| Evidence | The Android Keystore key is generated with `KeyGenParameterSpec.Builder` specifying only `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, GCM block mode, no padding, 256-bit size, and `setRandomizedEncryptionRequired(true)`. Crucially, `setUserAuthenticationRequired(true)` is NOT called, and `setInvalidatedByBiometricEnrollment` is left at its default (false). The key requires no biometric or credential to use — any code in the app process can call `Cipher.init(DECRYPT_MODE, key, ...)` and decrypt. The class comment (lines 23-26) explicitly documents this as an intentional design choice so the key stays usable for background work. |
| Impact | Biometric authentication (BiometricPrompt) is purely a UI gate. The encryption key itself offers no additional authorization. If an attacker can invoke `LockerStore.pageBytes`/`thumbBytes`/`readEncrypted` from within the app process, all locker records are decryptable without ever passing the biometric prompt. The cryptographic boundary does not enforce authentication; confidentiality rests entirely on the Compose navigation state (`lockerUnlocked`/`lockerActive` in MainActivity). |
| Recommendation | Bind the key to biometric authentication by calling `.setUserAuthenticationRequired(true)` on the `KeyGenParameterSpec.Builder`. Consider `.setInvalidatedByBiometricEnrollment(true)` if re-enrollment should invalidate existing documents. This forces BiometricPrompt confirmation at the crypto layer for every decrypt/encrypt operation. Accept that biometric re-enrollment will invalidate existing ciphertext, or implement a key-rewrap/re-encryption flow on enrollment change. |
| Verification | Instrumented test: attempt `LockerCrypto.decrypt()` on a sealed blob from within the app process without showing BiometricPrompt; confirm it throws `UserNotAuthenticatedException` when the key is authentication-bound. |

| Field | Description |
|---|---|
| Finding ID | SEC-002 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 174-183, 228-231 |
| Evidence | `decryptPagesToTemp()` writes decrypted plaintext page images to `cacheDir/locker_export/<docId>/` (line 175). The `cacheDir` is not `noBackupFilesDir`, but more importantly there is no cleanup path for a process death that occurs while export is in progress. The `finally` block (lines 228-232) deletes the temp page files only when the coroutine reaches it. If the process is force-stopped or crashes between line 178 (writing plaintext) and the finally block, plaintext JPEG files remain in `cacheDir/locker_export/<docId>/` indefinitely. `MainActivity` startup (lines 141-147) calls `LockerStore.clearTemps(ctx)` which only removes `noBackupFilesDir/locker_tmp`, and `LockerStore.clearTemps` (LockerStore.kt line 301-303) only removes `tmpRoot = noBackupFilesDir/locker_tmp`. Neither touches `cacheDir/locker_export`. |
| Impact | Decrypted plaintext pages can persist on disk in the app's cache directory after a failed/export-interrupted session. A later app update, root access, or physical extraction of the app's cache could recover plaintext locker pages that were never meant to leave encryption. Cache is not backed up by Android, but it survives app process restarts and is only cleared by the OS under storage pressure. |
| Recommendation | Write export temp files to `noBackupFilesDir/locker_export` (same protected location as `locker_tmp`) instead of `cacheDir`. Add a startup cleanup in `MainActivity` (or `LockerStore.sweep`) that recursively deletes any `locker_export` directories under both `cacheDir` and `noBackupFilesDir` on app start, before any UI is shown. |
| Verification | Instrumented test: start an export, force-stop the app mid-export via `adb shell am force-stop app.flipscan`, reopen, then `adb shell run-as app.flipscan ls cache/locker_export/`; confirm no plaintext `page_*.jpg` remains. |

| Field | Description |
|---|---|
| Finding ID | SEC-003 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/MainActivity.kt |
| Lines | 107-126, 149-152, 155-164 |
| Evidence | The locker session is tracked entirely by in-memory Compose state: `lockerUnlocked` (boolean) and `lockerUntil` (long, milliseconds). `lockLocker()` (line 111-126) resets these. `lockerActive()` (line 128-129) returns `lockerUnlocked && SystemClock.elapsedRealtime() < lockerUntil`. A 5-minute absolute cap is enforced by a coroutine (lines 155-164) that checks every 1 second and calls `lockLocker()`. `ON_STOP` (lines 150-152) calls `lockLocker()`. However, all of this is process-local and in-memory. There is no persistent binding of the session to the actual Keystore key or to a server-side/device-bound token. |
| Impact | The "session" is a UI convenience only, not a cryptographic enforcement. Because the key is not authentication-bound (SEC-001), an attacker who can execute code in the app process does not need a valid session at all — they can call the decryption APIs directly. For a local attacker without code execution, the session model is adequate (ON_STOP + 5-minute cap + in-memory state reset on process death), but it provides no defense beyond the Compose screen state. |
| Recommendation | Addressed in part by SEC-001 (bind key to biometric). Additionally, consider invalidating the in-memory session on any `setNumber`/edit operation if re-authentication is desired for writes. No change needed for the 5-minute cap itself; it is correctly implemented. |
| Verification | Instrumented test: unlock locker, background app for >5 minutes (or advance device time), restore app; confirm `lockerActive()` returns false and the user is routed to LockerGate. |

| Field | Description |
|---|---|
| Finding ID | SEC-004 |
| Category | Security |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerStore.kt |
| Lines | 326-333 |
| Evidence | `readEncrypted()` (private, lines 326-333) catches all exceptions from `LockerCrypto.decrypt()` and returns `null`. A corrupted ciphertext, wrong key, truncated file, or GCM tag mismatch all silently yield `null` rather than surfacing an error. Callers (`pageBytes` line 225, `thumbBytes` line 228) then treat the page/thumbnail as simply missing. |
| Impact | Corrupted or tampered encrypted data is silently ignored — pages and thumbnails fail to load with no user-visible indication of tampering or corruption. From a confidentiality standpoint this is safe (garbage is never revealed), but from an integrity/availability standpoint the user loses data without warning and cannot distinguish "page never existed" from "page was corrupted". GCM tag verification already prevents acceptance of modified ciphertext; the issue is purely the silent swallowing of the failure. |
| Recommendation | Distinguish "file missing" (returns null) from "decryption failed" (throws or logs). At minimum, log the exception (with no plaintext) so corruption is observable in logcat/crash reports, and optionally surface an index-level error similar to `indexError` in LockerHomeScreen (line 313-320). |
| Verification | Unit test: corrupt a single byte in `p_0.jpg.enc`, call `pageBytes`, confirm the failure is logged rather than silently returning null. |

| Field | Description |
|---|---|
| Finding ID | SEC-005 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 173-183, 236-273 |
| Evidence | Both `export()` (line 185) and `share()` (line 236) call `decryptPagesToTemp()` which writes ALL pages of a document as plaintext JPEG files to `cacheDir/locker_export/<docId>/` (line 174-183) before rendering. The comment on LockerStore.kt line 42 notes that export "breaks the locker's encryption boundary — the exported file is no longer protected." The plaintext files exist on disk for the entire duration of `Exporter.renderPages` + `Downloads.save`/`Share.send`. |
| Impact | During export/share, every page of the locker document exists as unencrypted plaintext on disk. If the device is compromised or the process is dumped during this window, all pages are exposed. The window is unavoidable for export (plaintext must exist to render PDF/JPG), but it should be minimized and located in the most protected directory possible. |
| Recommendation | Move `decryptPagesToTemp` output to `noBackupFilesDir/locker_export` instead of `cacheDir`. Keep the existing `finally` cleanup (already correct). Consider decrypting pages one-at-a-time and streaming into the exporter rather than holding all pages as plaintext simultaneously, to reduce the plaintext footprint. |
| Verification | Instrumented test: trigger a locker export, inspect `cacheDir/locker_export` mid-operation; confirm plaintext `page_*.jpg` files exist there (expected), then after export completes confirm they are deleted. Confirm path location under `noBackupFilesDir` after fix. |

| Field | Description |
|---|---|
| Finding ID | SEC-006 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/MainActivity.kt |
| Lines | 111-126, 294-316, 331-361 |
| Evidence | `lockerImport` (LockerCard/LockerDoc `onDone`, lines 294-316 and 331-361) calls `LockerStore.import()` which moves plaintext input pages into encrypted storage and then deletes the plaintext scratch files via `runCatching { Sidecar.deleteAll(it) }` (LockerStore.kt line 142). If `import()` succeeds (index written, `indexed = true`) but the subsequent plaintext-scratch deletion fails (caught by `runCatching`, so import still returns success), the original plaintext page files remain in `filesDir/work` until the next app start, where `Work.clear(ctx)` removes them. There is a small window between successful encryption and plaintext deletion. |
| Impact | Plaintext captured pages can briefly persist in `filesDir/work` after a successful locker import if the sidecar deletion fails. They are cleared on next app start (`MainActivity` lines 141-147 and 121-124 call `Work.clear`), but an attacker with access to the app's files directory in that window could recover plaintext locker pages. |
| Recommendation | Make plaintext scratch deletion part of the import atomicity: if deletion fails after a successful import, schedule a guaranteed cleanup rather than silently ignoring via `runCatching`. At minimum, the existing next-start `Work.clear()` is an acceptable backstop. |
| Verification | Instrumented test: make `Sidecar.deleteAll` fail (e.g., read-only files), complete a locker import, confirm plaintext files are gone by next app start. |

| Field | Description |
|---|---|
| Finding ID | SEC-007 |
| Category | Security |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerAuth.kt |
| Lines | 80-89 |
| Evidence | `BiometricPrompt.PromptInfo.Builder()` calls `setConfirmationRequired(false)` (line 80), so a single biometric match unlocks without requiring the user to tap a confirmation button. It also accepts `BIOMETRIC_WEAK` authenticators (line 83), which on some devices includes lower-assurance face unlock (2D) rather than only strong biometrics. |
| Impact | Marginally weaker authentication assurance: accidental or spoofable biometric matches can unlock the locker without deliberate user confirmation. Weak biometrics (e.g., 2D face) are accepted as sufficient for unlock. |
| Recommendation | Enable `setConfirmationRequired(true)` for high-assurance unlock, and restrict to `BIOMETRIC_STRONG` where device policy allows. Balance against usability; document the choice. |
| Verification | Manual test: confirm the prompt unlocks on a single finger touch without a confirmation tap (current behavior). |

| Field | Description |
|---|---|
| Finding ID | SEC-008 |
| Category | Security |
| Severity | High |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerCrypto.kt; app/src/main/AndroidManifest.xml |
| Lines | LockerCrypto.kt 28-62; AndroidManifest.xml 25 |
| Evidence | There is no key backup, escrow, or recovery mechanism. The key lives only in Android Keystore under alias `flipscan_locker_v1`. `android:allowBackup="false"` (AndroidManifest.xml line 25) prevents ADB/cloud backup of app data including the Keystore key. If the user clears app data, factory-resets the device, or the Keystore becomes corrupted, all encrypted locker documents are permanently unrecoverable — there is no recovery key, no recovery password, and no re-encryption path. The class comment (lines 23-26) accepts this deliberately to avoid invalidating documents on biometric re-enrollment. |
| Impact | Total, irreversible data loss of locker contents if app data is cleared or the device is reset. No recovery option exists. This is a confidentiality-by-design choice (no backdoor), but it is an availability risk users should be warned about. |
| Recommendation | Add a first-run warning in the locker UI that documents are unrecoverable if app data is cleared or the device is reset. Optionally offer a user-provided recovery passphrase that key-wraps the Keystore key (stored encrypted in the index), enabling recovery after app-data clear. |
| Verification | Manual test: clear app data via Settings, reopen locker, confirm prior documents cannot be decrypted (expected — unrecoverable). |

| Field | Description |
|---|---|
| Finding ID | SEC-009 |
| Category | Security |
| Severity | Informational |
| Status | Confirmed |
| File | app/src/main/AndroidManifest.xml |
| Lines | 23-28, 29-37 |
| Evidence | `android:allowBackup="false"` (line 25) excludes app data from backup. The FileProvider (lines 29-37) has `android:exported="false"` and authority `app.flipscan.fileprovider`. LockerStore documentation (line 79) states "Nothing here is reachable through FileProvider." Locker documents under `filesDir/locker` are never exposed via FileProvider; only Downloads exports use it on Android 7-9 (Downloads.kt line 71). |
| Impact | Locker data is not exfiltrated via backup or FileProvider. No finding — this is a correct control. |
| Recommendation | Maintain `allowBackup="false"` and keep FileProvider non-exported. |
| Verification | Confirm `allowBackup="false"` in release and debug builds; confirm no `<provider>` with `exported="true"` exposes locker paths. |

| Field | Description |
|---|---|
| Finding ID | SEC-010 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/MainActivity.kt |
| Lines | 167-177 |
| Evidence | `FLAG_SECURE` is gated on `screen.inLocker()` (line 167), which returns true for `LockerGate`, `LockerHome`, `LockerCard`, `LockerDoc`, `LockerPages`, and `LockerViewer` (lines 394-399). A `LaunchedEffect(secureScreen)` (lines 168-177) adds `FLAG_SECURE` when entering a locker screen and clears it 400ms after leaving (to protect the outgoing fade). Because `LaunchedEffect` runs after composition, there is a sub-frame window between the `screen` state changing to a locker screen and `FLAG_SECURE` actually being added to the Window. |
| Impact | A screenshot or recent-apps thumbnail could theoretically be captured during the single frame between the locker screen becoming visible and `FLAG_SECURE` taking effect. This window is extremely small (typically <16ms) and not practically exploitable, but it is not strictly zero. When leaving the locker, `FLAG_SECURE` is held for 400ms during the fade-out, which correctly protects the outgoing locker content. |
| Recommendation | Accept the sub-frame window as negligible; alternatively set `FLAG_SECURE` on the Window at theme level for the whole activity and clear it only when definitively outside the locker, though this complicates the fade transition. |
| Verification | Manual test: while a locker screen is visible, attempt a screenshot; confirm it fails or is blank. Confirm recent-apps task thumbnail is blank for locker screens. |

| Field | Description |
|---|---|
| Finding ID | SEC-011 |
| Category | Security |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/MainActivity.kt |
| Lines | 141-147 |
| Evidence | On every app start (`LaunchedEffect(Unit)`, lines 141-147), `MainActivity` runs `Work.clear(ctx)`, `LockerStore.clearTemps(ctx)`, and `LockerStore.sweep(ctx)`. `clearTemps` removes `noBackupFilesDir/locker_tmp` (staged edit plaintext). `sweep` removes orphan doc folders and `.tmp` indexes, but only if the index is readable (LockerStore.kt lines 309-322: if `readAll` fails, it returns early and deletes nothing). Critically, this startup cleanup does NOT remove `cacheDir/locker_export` (SEC-002). |
| Impact | Staged-edit plaintext (`noBackupFilesDir/locker_tmp`) and orphan encrypted folders are cleaned on start, protecting against plaintext remnants from a previous crashed session. However, plaintext export temp files in `cacheDir/locker_export` from an interrupted export survive a restart (covered under SEC-002). |
| Recommendation | Extend the startup sweep to also delete any `locker_export` directories under `cacheDir` and `noBackupFilesDir`. |
| Verification | Instrumented test: force-stop mid-crop-edit, reopen, confirm `noBackupFilesDir/locker_tmp` is empty. Force-stop mid-export, reopen, confirm `cacheDir/locker_export` is empty (after SEC-002 fix). |

| Field | Description |
|---|---|
| Finding ID | SEC-012 |
| Category | Security |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | 113-123, 162-171 |
| Evidence | `LockerOcr.detectAndStore` runs ML Kit's bundled Latin OCR on decrypted pages, one at a time, in memory (`LockerOcr.kt` lines 35-70). The result is stored in the encrypted index via `LockerStore.setNumber`. No plaintext files are written and no numbers are logged. The recognition runs once per document (`numberChecked` flag). |
| Impact | OCR is performed entirely on-device with no network (the ML Kit artifact is bundled, model ships in APK). No plaintext leaves the app. Confidentiality of document numbers is preserved. No finding — this is a correct control. |
| Recommendation | Maintain the on-device, bundled-model approach; do not switch to a cloud OCR model. |
| Verification | Confirm `text-recognition:16.0.1` is the bundled artifact (build.gradle.kts line 84) and no INTERNET permission is present (AndroidManifest.xml removes it at lines 8-13). |

| Field | Description |
|---|---|
| Finding ID | SEC-013 |
| Category | Security |
| Severity | Low |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerStore.kt |
| Lines | 116-151, 224-279 |
| Evidence | `import()` writes to a `$id.tmp` staging directory, renames to the final doc directory, then writes the index (lines 121-139). If the index write succeeds but the process dies before completion, the orphan doc folder has no index entry and is removed by `sweep` (lines 309-322). `writeEncrypted` (lines 335-346) uses `.tmp` + `renameTo` for atomicity. `commitEdit` and `discardEdit` always remove the plaintext temp folder in `finally` (lines 276-279, 281-283). |
| Impact | Failed operations do not leave inconsistent storage: `.tmp` files are renamed into place atomically, partial writes are cleaned by `sweep`, and plaintext edit temp folders are always removed. No finding — this is a correct control. |
| Recommendation | Maintain the tmp+rename write pattern and `finally` cleanup. |
| Verification | Instrumented test: kill process mid-import and mid-commit-edit; on restart, confirm `sweep` removes orphans and no plaintext remains. |

## Areas Reviewed (checklist)

- [x] AES-GCM encryption implementation — `LockerCrypto.kt` uses `AES/GCM/NoPadding` with 128-bit tag (line 31, 34).
- [x] Key generation and storage in Android Keystore — alias `flipscan_locker_v1`, 256-bit, GCM, generated once (LockerCrypto.kt lines 29-61).
- [x] IV handling (unique per record) — Keystore generates fresh random IV per `init(ENCRYPT_MODE)`; IV stored in envelope (lines 67-74).
- [x] Authentication tag verification — GCM 128-bit tag verified on every decrypt; `decrypt` throws on tag mismatch (line 92-93).
- [x] Encrypted index and document storage — `index.bin` and per-doc `p_i.jpg.enc`, `.orig.jpg.enc`, `.quad.enc`, `thumb.jpg.enc` all encrypted (LockerStore.kt).
- [x] Temporary plaintext file lifecycle — `noBackupFilesDir/locker_tmp` for edits; `cacheDir/locker_export` for export (SEC-002 gap).
- [x] BiometricPrompt integration — system BiometricPrompt, app never sees PIN (LockerAuth.kt).
- [x] Device credential fallback — `DEVICE_CREDENTIAL` allowed, `setDeviceCredentialAllowed(true)` pre-R (LockerAuth.kt lines 81-89).
- [x] Session expiration (5-minute cap) — `LOCKER_SESSION_MS = 5*60*1000`, coroutine checks every 1s (MainActivity.kt lines 62, 155-164).
- [x] Background locking (ON_STOP) — `Lifecycle.Event.ON_STOP` → `lockLocker()` (MainActivity.kt lines 150-152).
- [x] FLAG_SECURE on locker screens — applied to all `inLocker()` screens, 400ms fade-out hold (MainActivity.kt lines 167-177).
- [x] Corrupted encrypted data handling — `readEncrypted` swallows errors (SEC-004); index corruption surfaces `indexError` (LockerHomeScreen.kt lines 313-320).
- [x] Backup exclusion (`allowBackup=false`) — AndroidManifest.xml line 25.
- [x] Rollback on failed import/save — staging dir + `.tmp` + rename + sweep of orphans (LockerStore.kt lines 116-151, 309-322).

## Conclusion

The Digital Locker implements a fundamentally sound encryption-at-rest design: every record is sealed separately with AES-256-GCM under a Keystore-held 256-bit key, with fresh per-record IVs and GCM tag verification, all stored under `filesDir/locker` (app-private, not reachable via FileProvider, excluded from backup). Temporary plaintext is confined to `noBackupFilesDir/locker_tmp` and reliably cleaned in `finally` blocks plus on app start. The BiometricPrompt integration correctly delegates to the system with device-credential fallback, and the session model (ON_STOP lock + 5-minute cap + in-memory-only state) is correctly implemented. FLAG_SECURE covers all locker screens.

The principal weakness is architectural rather than a bug: the Keystore key is deliberately **not** bound to biometric authentication (SEC-001). Biometric unlock is therefore a UI gate only; the crypto layer itself authorizes any caller in the app process. This means the confidentiality of locker data rests entirely on Compose navigation state, not on hardware-backed user authorization. A secondary confirmed issue is that plaintext export temp files are written to `cacheDir/locker_export` and can survive a process death during export (SEC-002), unlike edit temp files which live in `noBackupFilesDir`. Corrupted page ciphertext is silently swallowed (SEC-004), and there is no key recovery path — clearing app data permanently destroys locker contents (SEC-008), which is by design but should be disclosed to users.

Readiness: The locker is **not yet ready for release** without addressing SEC-001 (bind key to biometric) and SEC-002 (move export temps to `noBackupFilesDir` + cleanup on start), and adding a user-facing data-loss warning for SEC-008. Once those are remediated, the locker's security posture is strong for a local-only, no-network application.
