# Agent 2 — OCR and Digital Pins Audit

## Repository Revision Inspected
- Branch: main
- Commit: 7ac29e3
- Files reviewed:
  - app/src/main/java/app/flipscan/data/LockerOcr.kt
  - app/src/main/java/app/flipscan/data/PinFormat.kt
  - app/src/main/java/app/flipscan/ui/DigitalPins.kt
  - app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt
  - app/src/main/java/app/flipscan/data/LockerStore.kt
  - app/src/main/java/app/flipscan/data/LockerCrypto.kt (supporting: encryption of stored numbers)
  - app/src/main/AndroidManifest.xml (supporting: INTERNET removal, no network permission)
  - app/build.gradle.kts (supporting: ML Kit bundled dependency declaration)

## Findings

| Field | Description |
|---|---|
| Finding ID | OCR-001 |
| Category | Digital Pins |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt |
| Lines | 169-175 |
| Evidence | `DigitalPinCard` shows a Pill labeled "Verified" when `doc.numberVerified == true` (line 171), and "Check digits" otherwise. `numberVerified` is set purely by `PinFormat.isValid(doc.type, value)` — a shape/format check — in `LockerOcr.findNumber()` (LockerOcr.kt line 63) and `LockerPinDialog.onSave()` (DigitalPins.kt line 250). It is never set by user confirmation and never reflects any authority check. |
| Impact | Users may believe a number has been "verified" by an authority or confirmed by them, when it has only passed a local format/shape regex. This overstates assurance and could lead users to trust an OCR-misread number. |
| Recommendation | Re-label the pill to reflect shape-only validation, e.g. "Format OK" vs "Format invalid", and keep user confirmation separate from format validity. The `numberVerified` flag should be reserved for explicit user confirmation, not OCR format matching. |
| Verification | Manual test: OCR-read a number that passes the format regex but is wrong; confirm the pill says "Verified" and consider whether that misleads. Then re-label and confirm pill text changes. |

| Field | Description |
|---|---|
| Finding ID | OCR-002 |
| Category | OCR |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/data/LockerOcr.kt |
| Lines | 98-101, 124-134 |
| Evidence | For PAN, the candidate regex `(?<![A-Z0-9])[A-Z]{5}[0-9OIL]{4}[A-Z]` (line 99) already accepts `O`, `I`, `L` inside the four-digit block. `fixDigits()` (lines 124-134) then forcibly maps `O`→`0` and `I`,`L`→`1` across positions 5..8 of the value. This is an unconditional heuristic: if a genuine PAN legitimately contains a letter `O` or `I` in that position (unlikely but possible in OCR noise contexts), it will be silently replaced with a digit. |
| Impact | OCR correction can mutate a real character into a different one, producing an internally consistent but wrong number. Because the result is stored as `numberVerified = true` when the format passes (OCR-001), a corrupted PAN may be presented as verified. |
| Recommendation | Make `fixDigits` a suggestion rather than a mutation: prefer the strict regex match first, and only apply O→0/I→1 substitution as a fallback candidate ranked lower than exact matches. Do not mark heuristic-corrected values as verified without user review. |
| Verification | Unit test `candidates(LockerDocType.PAN, text)` with input containing `O`/`I`/`L` in the digit block; confirm the strict `[A-Z]{5}[0-9]{4}[A-Z]` match is preferred over a fixDigits-corrected variant, and that corrected candidates are not auto-verified. |

| Field | Description |
|---|---|
| Finding ID | OCR-003 |
| Category | Digital Pins |
| Severity | Low |
| Status | Potential |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt |
| Lines | 318-328 |
| Evidence | `copyPin()` calls `manager?.setPrimaryClip(clip)` (line 326), which unconditionally replaces the entire system clipboard. It does not inspect whether newer content is already present, nor does it offer append/merge. On Android 13+ it correctly sets `ClipDescription.EXTRA_IS_SENSITIVE = true` (lines 321-324), so the system UI won't preview the value, but the overwrite itself is unguarded. |
| Impact | A user copying a sensitive number overwrites whatever was previously on the clipboard, potentially losing another sensitive value (e.g., a password or TAC) without warning. |
| Recommendation | Before overwriting, optionally show a confirmation if clipboard already contains non-empty content, or at minimum document that Copy replaces clipboard contents. Keep the sensitive-flag behavior (already correct). |
| Verification | Manual test: place non-sensitive text on clipboard, tap Copy in DigitalPins, confirm clipboard now holds the number (expected) — and evaluate whether a overwrite-warning is desired per product policy. |

| Field | Description |
|---|---|
| Finding ID | OCR-004 |
| Category | OCR |
| Severity | Informational |
| Status | Confirmed (no issue) |
| File | app/src/main/java/app/flipscan/data/LockerOcr.kt |
| Lines | 16, 35-65, 72-77 |
| Evidence | `DetectedPin` carries a `verified: Boolean` field (line 16). `findNumber()` sets `verified = PinFormat.isValid(doc.type, candidate)` (line 63). OCR failures are swallowed: `recognize()` resumes with `""` on failure (line 76), and `detectAndStore()` calls `markNumberChecked()` when nothing is found (line 42), preventing repeated automatic retries. Manual "Read again" in the UI (LockerViewerScreen.kt line 230) can still re-run. |
| Impact | Extracted values are not blindly trusted: they pass a format check only, and OCR failure is handled gracefully with a one-time check plus manual retry. No plaintext number is written to disk outside the encrypted index, and nothing is logged (grep for `Log.` in `app/flipscan/data` returned no matches; comment at LockerOcr.kt line 26 confirms no logging). |
| Recommendation | Maintain current behavior. Consider renaming `verified` to `formatValid` to avoid semantic confusion with user confirmation (see OCR-001). |
| Verification | Code review confirms no `android.util.Log` calls in OCR path; `runCatching` + `getOrNull` handles recognition exceptions. |

| Field | Description |
|---|---|
| Finding ID | OCR-005 |
| Category | Digital Pins |
| Severity | Medium |
| Status | Confirmed |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt |
| Lines | 169-175, 213-232 |
| Evidence | The UI presents `doc.numberVerified` as a binary "Verified" / "Check digits" pill (lines 171-175) and repeats the masked/revealed value in `DigitalPinCard` (line 213). The word "Verified" implies authoritative confirmation. In reality `numberVerified` is true whenever `PinFormat.isValid` passes — for Aadhaar that means the Verhoeff checksum passes (PinFormat.kt line 55), which is an internal consistency check, not government verification. |
| Impact | A user could mistake the "Verified" pill for confirmation that the issuing authority recognizes the number, or that they personally confirmed it. This is misleading for all document types, not just Aadhaar. |
| Recommendation | Change the pill text to describe format validity only (e.g., "Format OK" / "Invalid format"), and add a separate user-actionable "Confirm" state if confirmation tracking is ever needed. Update hint text (PinFormat.kt line 78: "Checksum matches — looks like a valid Aadhaar number.") to keep the cautious "looks like" phrasing. |
| Verification | Manual test: confirm pill reads "Verified" after OCR reads a format-valid number; after re-labeling, confirm it reads "Format OK" instead. |

| Field | Description |
|---|---|
| Finding ID | OCR-006 |
| Category | Digital Pins |
| Severity | Low |
| Status | Confirmed (appropriate) |
| File | app/src/main/java/app/flipscan/data/PinFormat.kt |
| Lines | 52-62 |
| Evidence | `isValid()` applies per-type regex: Aadhaar → Verhoeff checksum; PAN → `^[A-Z]{5}[0-9]{4}[A-Z]$`; Passport → `^[A-Z][0-9]{7}$`; Driving Licence → `^[A-Z]{2}[0-9]{11,13}$`; Voter ID → `^[A-Z]{3}[0-9]{7}$`; CUSTOM → `length >= 3`. These are explicitly documented as shape checks, not authority verification (PinFormat.kt lines 8-10). |
| Impact | Format checks are appropriate to each document type's known shape. They catch OCR misreads and typos but make no claim about government verification. No false assurance is encoded in the validation itself. |
| Recommendation | Keep as-is; ensure UI wording never says "government verified" (see OCR-001/OCR-005). |
| Verification | Unit test each `isValid` branch with valid and invalid samples; confirm CUSTOM accepts any 3+ chars and rejects shorter. |

| Field | Description |
|---|---|
| Finding ID | OCR-007 |
| Category | OCR |
| Severity | Informational |
| Status | Confirmed (consistent) |
| File | app/src/main/java/app/flipscan/data/PinFormat.kt; app/src/main/java/app/flipscan/data/LockerOcr.kt; app/src/main/java/app/flipscan/data/LockerStore.kt |
| Lines | PinFormat.kt 15-25; LockerOcr.kt 83-89; LockerStore.kt 182-188 |
| Evidence | `PinFormat.normalize()` is the single canonical normalizer (uppercase, strip non-alphanumeric, length cap per type). It is invoked in `LockerOcr.candidates()` (line 87), `LockerStore.setNumber()` (line 182), `LockerPinDialog` (line 269), and `PinFormat.masked()`/`display()`/`isValid()` internally. All code paths normalize identically before storage, masking, display, or validation. |
| Impact | No normalization inconsistency: a number stored, masked, displayed, or validated always derives from the same `normalize()` function. Whitespace, dashes, and letter case are handled uniformly. |
| Recommendation | Maintain the single normalizer; do not add ad-hoc normalization in UI or OCR code. |
| Verification | Unit test `normalize()` for each type with spaced/dashed/mixed-case input; confirm identical output across all callers. |

| Field | Description |
|---|---|
| Finding ID | OCR-008 |
| Category | Digital Pins |
| Severity | Informational |
| Status | Confirmed (correct) |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt; app/src/main/java/app/flipscan/data/PinFormat.kt |
| Lines | DigitalPins.kt 53, 104-105, 158-160, 331-338; PinFormat.kt 41-50 |
| Evidence | `REVEAL_MS = 20_000L` (line 53). `RevealTimeout()` uses `LaunchedEffect(revealed)` with a 20-second `delay` before calling `onTimeout()` to set `revealed = false` (lines 331-338). `masked()` hides Aadhaar first 8 digits (`•••• •••• XXXX`, lines 44-45) and hides all but last 4 for other types (lines 47-49). `display()` shows Aadhaar grouped (`XXXX XXXX XXXX`) and other types in full (lines 30-34). State is keyed by `remember(doc.id, number)` so reveal resets when the number changes. |
| Impact | Reveal/hide behaves per spec: 20-second auto-re-mask, Aadhaar first-8 hidden, others last-4 only, state scoped per document/number. No information leak through stale reveal state. |
| Recommendation | Maintain current reveal timeout and masking. |
| Verification | Manual test: reveal a number, wait >20 seconds, confirm it re-masks. Switch documents and confirm reveal state does not leak across docs. |

| Field | Description |
|---|---|
| Finding ID | OCR-009 |
| Category | Digital Pins |
| Severity | Informational |
| Status | Confirmed (correct) |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt |
| Lines | 318-328 |
| Evidence | `copyPin()` creates `ClipData.newPlainText("Document number", value)` (line 320). On `SDK_INT >= TIRAMISU` (Android 13+) it sets `ClipDescription.EXTRA_IS_SENSITIVE = true` (lines 321-324), preventing system UI preview of the sensitive value. `manager?.setPrimaryClip(clip)` (line 326) is the standard copy API. |
| Impact | Sensitive values are not previewed by the system clipboard UI on Android 13+, and the copy is a standard platform operation. No additional leakage path introduced. |
| Recommendation | Maintain the sensitive flag; see OCR-003 regarding clipboard overwrite behavior. |
| Verification | Manual test on Android 13+: copy a number, open system clipboard UI, confirm the value is not previewed (marked sensitive). |

| Field | Description |
|---|---|
| Finding ID | OCR-010 |
| Category | Digital Pins |
| Severity | Informational |
| Status | Confirmed (correct) |
| File | app/src/main/java/app/flipscan/data/LockerStore.kt; app/src/main/java/app/flipscan/data/LockerCrypto.kt |
| Lines | LockerStore.kt 170-191; LockerCrypto.kt 28-94 |
| Evidence | `LockerStore.setNumber()` normalizes the value via `PinFormat.normalize()` (line 182) and stores it in the encrypted index (`index.bin`, written by `writeIndex` lines 363-385). `LockerCrypto` uses AES-256-GCM with a fresh random IV per record (LockerCrypto.kt lines 65-78), key held in Android Keystore alias `flipscan_locker_v1`. Numbers are never written to plaintext files and never exposed via FileProvider. |
| Impact | Extracted numbers are encrypted at rest with the same key as locker pages, in the same encrypted index. No plaintext number persists outside encrypted storage. |
| Recommendation | Maintain encrypted storage; no change needed. |
| Verification | Confirm `number` appears only inside `index.json`-style encrypted index (writeIndex lines 373-376) and never as a standalone plaintext file. |

| Field | Description |
|---|---|
| Finding ID | OCR-011 |
| Category | OCR |
| Severity | Informational |
| Status | Confirmed (correct) |
| File | app/src/main/java/app/flipscan/data/LockerOcr.kt; app/src/main/java/app/flipscan/ui/LockerViewerScreen.kt |
| Lines | LockerOcr.kt 35-45, 48-70; LockerViewerScreen.kt 113-123, 162-171 |
| Evidence | `detectAndStore()` wraps `findNumber()` in `runCatching` and on success calls `setNumber(...checked = true)`, on failure calls `markNumberChecked(ctx, id)` (line 42) so the viewer does not auto-retry on every open. Manual re-read via "Read again" (LockerViewerScreen.kt line 230 → `readNumber()` line 162-171) bypasses the `numberChecked` guard and allows retry. UI shows "Could not read the number from this page." on failure (line 169). |
| Impact | OCR failure (no candidate found, or recognition exception) is handled gracefully: one automatic attempt, then manual retry only, with a user-visible error message. Numbers are not left in an ambiguous state. |
| Recommendation | Maintain current failure handling. |
| Verification | Manual test: run OCR on a page with no number; confirm "No number found yet" state and that auto-retry does not occur on reopen, but "Read again" works. |

| Field | Description |
|---|---|
| Finding ID | OCR-012 |
| Category | Digital Pins |
| Severity | Informational |
| Status | Confirmed (correct) |
| File | app/src/main/java/app/flipscan/ui/DigitalPins.kt; app/src/main/java/app/flipscan/data/LockerStore.kt |
| Lines | DigitalPins.kt 237-258, 306-309; LockerStore.kt 170-191 |
| Evidence | `LockerPinDialog` provides an edit field pre-filled with the current number (line 268), normalizes as the user types (line 269), validates with `isValid` (line 271), and offers Save (line 300-304) and Remove (line 309: `onSave(null)`). `setNumber()` with `value = null` clears `number`, `numberPage`, and `numberVerified` (LockerStore.kt lines 182-188). |
| Impact | Users can manually correct OCR errors or remove a number entirely. Edited values are re-normalized and re-validated before storage. |
| Recommendation | Maintain edit/remove flow. |
| Verification | Manual test: edit a number to an invalid value, confirm warning hint appears and Save is still enabled (save allowed but flagged); remove a number, confirm the field returns to "No number found yet". |

| Field | Description |
|---|---|
| Finding ID | OCR-013 |
| Category | Digital Pins |
| Severity | Informational |
| Status | Confirmed (no misleading claims) |
| File | app/src/main/java/app/flipscan/data/PinFormat.kt; app/src/main/java/app/flipscan/ui/DigitalPins.kt |
| Lines | PinFormat.kt 8-10, 55, 78-82; DigitalPins.kt 171-175 |
| Evidence | PinFormat.kt header comment (lines 8-10) explicitly states: "These checks are about *shape*, not about the authority having confirmed a document." `hint()` for Aadhaar says "Checksum matches — looks like a valid Aadhaar number" (line 78) — cautious wording. The only potentially misleading UI is the "Verified" pill (DigitalPins.kt line 171), flagged in OCR-001/OCR-005. There is no explicit "government verified" claim anywhere. |
| Impact | No direct false claim of government verification exists in code. The residual risk is the word "Verified" in the pill, which is addressed by OCR-001/OCR-005. |
| Recommendation | Re-label the pill per OCR-001 to fully close this gap. |
| Verification | Grep for "verif" and "govern" across the UI layer; confirm no "government verified" or "authority confirmed" strings remain. |

## Areas Reviewed (checklist)

- [x] ML Kit OCR integration (bundled model, offline) — `text-recognition:16.0.1` bundled artifact (build.gradle.kts line 84), `TextRecognizerOptions.DEFAULT_OPTIONS` (LockerOcr.kt line 49), `recognizer.close()` (line 67). No INTERNET permission (AndroidManifest.xml lines 8-13 remove ML Kit's merged INTERNET/ACCESS_NETWORK_STATE).
- [x] Per-document-type format validation — `PinFormat.isValid()` per-type regex (lines 52-62).
- [x] Aadhaar Verhoeff checksum implementation — `PinFormat.verhoeffValid()` (lines 89-96) with standard D/P tables (lines 98-120).
- [x] Number normalization (whitespace, dashes removal) — `PinFormat.normalize()` (lines 15-25), single canonical normalizer used by all callers.
- [x] Masking logic (Aadhaar first 8 hidden, others last-4 only) — `PinFormat.masked()` (lines 41-50).
- [x] Reveal/hide with 20-second timeout — `REVEAL_MS = 20_000L`, `RevealTimeout()` (DigitalPins.kt lines 53, 331-338).
- [x] Clipboard handling (sensitive flag on Android 13+) — `copyPin()` with `EXTRA_IS_SENSITIVE` (lines 318-328).
- [x] Encrypted storage of extracted numbers — `LockerStore.setNumber()` → encrypted `index.bin` via `LockerCrypto` AES-256-GCM.
- [x] OCR failure handling (no number found) — `runCatching` + `markNumberChecked`, UI error message, manual retry.
- [x] Manual edit and removal of numbers — `LockerPinDialog` Save/Remove, `setNumber(value=null)` clears.
- [x] No misleading "government verification" claims — no such string in code; only the "Verified" pill wording needs re-labeling (OCR-001/OCR-005).

## Conclusion

The OCR and Digital Pins implementation is well-engineered for a local-only, no-network app. ML Kit's bundled Latin model runs entirely on-device (no INTERNET permission, removed at manifest merge), pages are decrypted one at a time and recycled immediately, recognized numbers are stored only in the encrypted locker index (AES-256-GCM via Android Keystore), and nothing is logged or written to plaintext. Per-document format validation, the Aadhaar Verhoeff checksum, normalization, masking (Aadhaar first-8 hidden, others last-4), the 20-second reveal timeout, and Android 13+ sensitive clipboard flag are all correctly implemented. OCR failure, manual edit, and removal are handled gracefully.

The principal concerns are semantic rather than cryptographic: (1) the UI "Verified" pill overstates assurance by labeling a pure format/shape pass as "Verified" (OCR-001/OCR-005), and (2) PAN OCR's unconditional O→0 / I→1 character substitution can silently mutate a real character (OCR-002). Clipboard overwrite is standard behavior but unguarded (OCR-003). None of these break confidentiality, but they can mislead users about the trustworthiness of an OCR-extracted number.

Readiness: The OCR/Digital Pins feature is **functionally ready for release** with the caveat that OCR-001 (re-label the "Verified" pill) should be addressed before release to avoid overstating verification. OCR-002 and OCR-003 are lower-priority hardening items.