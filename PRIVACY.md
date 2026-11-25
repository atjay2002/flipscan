# FlipScan Privacy Policy

Last updated: 26 November 2026

FlipScan is an offline document scanner. It does not collect, send, or share your data.

## No internet
FlipScan does not have the INTERNET permission, so it cannot upload anything to any server.

## What the app uses
- Camera: only to capture documents. Photos are processed on your device.
- Text recognition (OCR): used to find document numbers such as Aadhaar or PAN. It runs fully on your device using a bundled model.
- Biometric / screen lock: used only to unlock the Digital Locker, through the Android system prompt. FlipScan never sees your fingerprint or face data.

## Where your data is stored
- Normal scans are stored in the app's private storage.
- Digital Locker documents, names, and detected numbers are encrypted with AES-256-GCM. The key is held in the Android Keystore and never leaves your device.
- Locker screens block screenshots and screen recording.
- App data is excluded from Android backup. If you uninstall the app, clear its data, or change phones, locker documents cannot be recovered.

## Exports are not encrypted
If you export a document from the locker (PDF, JPG, or PNG), the copy is saved in Downloads/FlipScan without encryption. Anyone with access to your files can open it. Delete exported copies when you no longer need them.

## What we do not do
No ads, analytics, tracking, or third-party data collection.

## Your control
You can delete any scan or locker document inside the app. Uninstalling the app removes all of its stored data. Exported files in Downloads must be deleted by you.

## Open source
The source code is public: https://github.com/atjay2002/flipscan

## Contact
Questions or concerns: open an issue at https://github.com/atjay2002/flipscan/issues
