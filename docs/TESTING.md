# Testing and profiling on a real phone

## Functional checklist
1. First launch: camera permission prompt; deny, then use "Open app settings".
2. Double Side: front captured, preview slides to BACK slot, back captured, "Done" appears.
3. Tap a slot to retake it. Check the old photo is replaced.
4. Auto-capture: hold a card still ~1 s. It must NOT capture the same side twice; flip the card first.
5. Blur / glare hints appear (shiny laminated card under a lamp).
6. Result: switch A4/A5/Letter/Custom, stacked/side by side, real size vs fit, margin, cut line. Print at 100% and measure the card: 85.6 x 54 mm.
7. Filters, formats (PDF/JPG/PNG), size limits (200 KB / 500 KB). Check the banner when a limit cannot be reached.
8. Save to Downloads (Downloads/FlipScan), Share to WhatsApp/Gmail. The success banner must appear only after the copy finishes.
9. Regular mode: capture 3+ pages, reorder, delete one, add another, Continue, export.
10. Home: tap a scan → the action sheet lists Open, Edit, Rename, Share, Move to folder and Delete.
11. Android 7-9 only: saving asks for storage permission.
12. Theme: switch the system to dark mode. Every screen must stay readable (camera screens are intentionally always dark).

## Flash / torch
- Double Side and Document screens: flash button in the top bar, next to the title.
- Tap it: the LED turns on continuously (torch, not a still-photo flash) and the button turns amber with a bolt icon.
- Tap again: the LED turns off and the button returns to its dim state.
- Capture with the torch on and off; the photo must look the same otherwise (no exposure/focus change in the app).
- Leave the camera screen while the torch is on: the LED must go off. Reopen the camera: the button must show the real state (off), with no stale amber state or crash.
- Rotate/lock the phone, take a call, or send the app to the background with the torch on; returning must not leave the LED stuck on.
- On a camera without a flash unit: the button is dimmed and tapping explains that the camera has no flash.
- On the ID card screen, once both sides are captured the preview (and the camera) is released, so the
  flash button disappears; tapping a card to retake brings it back with the real torch state.

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

## Crop / rotate
- Result screen: tap Crop on Front, drag the corners (green corner = top-left of the result), Apply. Page and preview update.
- While a corner is held: a round 136 dp loupe appears above the finger (beside it for the two top corners), follows the corner, is clamped inside the editing area, shows real photo pixels at 2.4x with a crosshair, and disappears on release/cancel.
- Drag a corner into each edge and into the opposite corner: the magnified image must clamp at the photo edge with the crosshair still on the finger point (no blank bars, no crash).
- Rotate (↻) a card: it should become an upright card on the sheet, not stretched.
- Scans made before this version have no stored original: Crop shows a message there; rotate still works.
- Result screen: Save shows the "Saved to Downloads/FlipScan" banner (with the written file name) and returns to the Home dashboard
  after ~1.4 s; Share opens the system sheet and returns Home ~0.9 s later. A failure keeps you on the
  page with the error banner so you can retry.

## Digital Locker
Setup: set a screen lock and enrol a fingerprint (or face) on the phone. Then Home → Digital Locker.

- Gate: only a lock icon and text are visible — no document names, counts or thumbnails before unlocking.
- Success: the system prompt appears; after a match the locker home opens. Back/cancel from the prompt keeps
  you on the locked gate and nothing is read.
- Failure: cancel with the back button, try a non-enrolled finger, and (careful) fail five times to see the
  30-second lockout message. Nothing must crash and no document may appear.
- Credential fallback: on a phone with a PIN but no biometrics the gate says the screen lock is used, and the
  prompt's "Use PIN" path unlocks it. On a phone with no screen lock at all the gate explains and offers
  "Open security settings"; the unlock button must not appear.
- Backgrounding: with the locker open, press Home, then reopen the app. It returns to Home and Digital Locker
  asks to unlock again. Repeat from the viewer and from the locker camera.
- Session cap: unlock, sit on the locker home for 5 minutes without touching it; it locks by itself.
- Process death: with the locker open, `adb shell am force-stop app.flipscan`, reopen. It must start locked.
- Screenshots: while any locker screen is visible a screenshot fails or comes out blank, and the task
  switcher preview is blank. Normal screens still allow screenshots.
- Scan: Digital Locker → Scan sensitive document → pick Aadhaar (card) and Custom document (multi-page).
  For a card, one side is enough: after the first capture the camera stops and "Review" is available; tap the
  empty slot to add the other side. Each saved document appears under "My secure documents" with a thumbnail
  and a `Type · N pages` line.
- Separation: the same document must never appear in Recent scans, in any folder, or in Downloads/FlipScan,
  before or after a restart. Normal (non-locker) scans still need no authentication.
- Viewer: pages switch with the arrows and the page strip; Rotate, Crop (drag corners, apply), Rename and
  Delete all work and survive Back → reopen and an app restart.
- Encryption at rest (debug build): `adb shell run-as app.flipscan ls files/locker/docs/*` lists only
  `*.enc` files. `adb shell run-as app.flipscan cat files/locker/docs/<id>/p_0.jpg.enc | head -c 64` must not
  start with JPEG magic (`FF D8 FF`) and `index.bin` must not contain a readable document name.
- No plaintext leftovers: while cropping a locker page, `adb shell run-as app.flipscan ls no_backup/locker_tmp`
  shows one `edit-*` folder; after Apply, Cancel or locking, it is gone. Abandon a locker capture with Back
  (Discard) and check `files/work` is empty.
- Interrupted save: `adb shell am force-stop app.flipscan` right after tapping Review/Save. Relaunch: the
  half-written document is not listed, `files/work` has no page files, and the locker still opens normally.
- Tamper/corruption: with the app closed, `adb shell run-as app.flipscan` overwrite a byte in `index.bin`.
  The locker shows the "encrypted index could not be opened" message, does not list or delete anything, and
  recovers once the file is restored (a real Keystore failure is not recoverable, by design).
- Lock button in the locker header returns Home immediately and ends the session; the next visit asks again.

## Rename / file name
- Result screen: pencil icon → rename the scan. The header updates; Home shows the new name after Back.
- Kill and reopen the app: the renamed name must persist.
- Rename to spaces only, to `a/b:c*?"<>|` and to a 100+ character string: the dialog blocks empty input;
  invalid characters are replaced by spaces and long names are shortened.
- Export panel: the "File name" field starts with the scan name, shows a live "Saves as" preview, strips a
  typed `.pdf`/`.jpg`/`.png` so the extension is not doubled, and an empty field falls back to the scan name.
- Save the same name twice: the second copy becomes `name (2).ext`; nothing is overwritten.

## Folders
- Home → New folder: create two folders; they appear as tiles with scan counts; names persist after restart.
- Create a folder with an existing name: it is stored as "Name (2)".
- Scan menu → Move to folder: move a scan in and back to Main collection; the tile counts and the folder
  screen follow. Recent scans keeps listing every scan (with its folder name) either way.
- Folder screen: rename the folder from its ⋮ menu, open a scan, Edit, and Back returns to the folder.
- Delete a non-empty folder: the dialog says the scans are kept; after deleting, the scans appear again in
  Recent with no folder chip, and their page files still open.
- Delete a folder from Home and from inside the folder screen: no scan and no exported file is removed.

## Open vs Edit
- Tap a scan that was never exported → Open offers "Save & open"; after confirming, the file is created and
  the system viewer opens it, and the scan menu now shows that name under Open.
- Tap a scan with one saved export → Open goes straight to the viewer, no banner and no re-export.
- Export both a PDF and a JPG for one scan, then Open → the picker lists both (newest first).
- Delete the exported file from Downloads, then Open → "Saved file is missing" with "Save again".
- Open a scan on a phone with no PDF viewer installed: a readable message appears, the app does not crash.
- Android 7-9: "Save & open" on a scan that was never saved asks for storage permission first (deny it:
  a message explains why, nothing is written).
- Verify no `file://` URI is ever passed: open a PDF via Open while the viewer app shows the URI (adb logcat).

## Motion and layout
- Screen changes: short slide + fade, no black flash, no stutter on the camera preview.
- Settings > Accessibility > Remove animations (or Developer options > Animator duration scale = off): screen changes, the home entrance, thumbnail fades and the shutter press become instant; nothing disappears.
- Crop: dragging corners must track the finger with no lag (no animation is used there on purpose).
- Narrow/small phone: the result screen still scrolls, the bottom bar does not cover the last settings row.

## Release build (R8) smoke test
This is the build that is distributed; install it once before publishing:
- `bash gradlew assembleRelease` and install `app/build/outputs/apk/release/app-release-unsigned.apk` after signing.
- Open the camera, toggle the torch, capture a card both ways, crop with OpenCV perspective correction, rotate, save PDF/JPG/PNG, and share.
- Open the Digital Locker, unlock with the system prompt, save one card document and one multi-page document, rotate and crop a locker page, rename and delete it, then restart the app and confirm the locker still unlocks and opens the remaining document. R8 must not break Keystore (encryption), org.json (index) or androidx.biometric (prompt).
- If anything fails only in release, the first suspect is a missing keep rule in `app/proguard-rules.pro` (OpenCV JNI, CameraX runtime classes).
