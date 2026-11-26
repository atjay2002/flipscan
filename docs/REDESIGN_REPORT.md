# FlipScan Redesign Report (v0.8.0)

## Summary

The FlipScan Android app has been redesigned end-to-end on branch `redesign-ui`, following the home screen redesign spec (`docs/FlipScan-home-redesign-spec.md`) and the visual/motion mockup (`docs/home-mockup-reference.html`). The redesign covers the design system, home screen, and applies the new visual language across all screens.

**Branch:** `redesign-ui`
**Commit:** `ac0abc1`
**APK:** `delivery/FlipScan-redesign-debug.apk`
**SHA-256:** `352793C6FF6D11341E818785343C73C0FD7FA506721A0CC49C0107291E956743`

## Files Changed

### New files
- `app/src/main/java/app/flipscan/ui/DesignSystem.kt` — Design tokens (light/dark), `GlassCard`, `PrimaryButton`, `SecondaryTile`, `SectionHeader`, `PillBadge`, `OfflinePill`, `GlowBackground`, motion/reduce-motion helpers
- `docs/REDESIGN_PLAN.md` — Phase plan
- `docs/REDESIGN_REPORT.md` — This report

### Modified files
- `app/src/main/java/app/flipscan/ui/Theme.kt` — Updated `LightColors`/`DarkColors` to use new brand tokens (`#4B2FE0` primary, `#F4F1FF` background, etc.)
- `app/src/main/java/app/flipscan/ui/HomeScreen.kt` — Complete redesign per spec: header with logo + "Works offline" pill, "Both sides. / One page." heading, two promo cards (FRONT/BACK illustration, lock chip), gradient "Scan ID card" primary button with shine sweep, Document + Digital Locker secondary tiles, Recent scans section with empty state, "Private by default. Shared only by you." footer, entrance animations with staggering, reduce-motion and low-RAM handling
- `app/src/main/java/app/flipscan/ui/LockerScreen.kt` — Wording: replaced "No internet permission. Nothing in the locker leaves this phone." with "Private by default. Shared only by you."

## Design System

**Tokens (light default, dark follows system):**
- Background: `#F4F1FF` / ink: `#17142B` / muted: `#4A4660`
- Brand: `#4B2FE0` / brand-light: `#6A4CFF` / accent: `#FFFFB020`
- Offline text: `#0A5A3F` / chip text: `#2E2194`
- Glow blobs: purple `#7C5CFF`, amber `#FFFFB020`, mint `#3DDCB5`

**Components:**
- `GlassCard` — white 55% fill, 1dp white border, brand-tinted soft shadow, inner top sheen, no RenderEffect/blur
- `PrimaryButton` — gradient brand-light→brand, 76dp height, radius 22, 44dp icon tile, shine sweep animation every 3.6s
- `SecondaryTile` — glass tile, 92dp height, radius 20, icon + title + subtitle
- `SectionHeader` — title + optional link (e.g. "New folder")
- `PillBadge` — small rounded badge
- `OfflinePill` — glass pill with shield icon and pulsing ring (2–2.4s)
- `GlowBackground` — three radial-gradient blobs (purple top-left, amber right-middle, mint bottom-left) drifting slowly (~9s)

**Animations:**
- Home entrance: sections fade in + slide up 16dp, 600ms, staggered (50, 150, 450, 600, 750ms)
- Promo card 1: FRONT drops in (-24dp, rotate -8→-3°, 800ms overshoot); BACK flips in (rotateX -95→0°, 900ms, delay 700ms); both float 5dp over 4.5s (opposite phases)
- Background glows drift (~9s, translate ~20dp, scale 1.12)
- Primary button shine sweeps every 3.6s
- Offline pill and lock badge pulse every 2–2.4s
- Copy chip flashes amber every 4s
- Empty-state flip icon rotates Y 0→180→360 over 4s
- Reduce motion: all infinite/entrance animations skipped when animator duration scale = 0; infinite animations paused when not RESUMED; glow drift and floating skipped on low-RAM devices

## Wording Consistency

Replaced absolute claims with the approved tagline **"Private by default. Shared only by you."**:
- `ui/LockerScreen.kt`: "No internet permission. Nothing in the locker leaves this phone." → tagline
- `ui/HomeScreen.kt` (redesign): footer uses tagline

Factual technical statements retained: "FlipScan has no INTERNET permission", "AES-256-GCM encryption", "exported files are not encrypted".

## What Was NOT Changed (per safety rules)
- No changes to `data/`, `LockerCrypto`, scanning, OCR, edge detection, PDF/image export, or number-extraction logic
- No new permissions, no network libraries, no heavy new dependencies
- No changes to `versionName`/`versionCode`, `abiFilters`, or signing config
- No secrets, keystores, or tokens committed
- All existing features and navigation paths preserved (both-sides scan, export, locker, number copy, folders, recent scans)

## Verification

### Build
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL**
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (145 MB)
- No new permissions in merged manifest

### Emulator Testing
**NOT COMPLETED** — The Android emulator could not be started in this environment. The emulator process did not initialize and no `adb` device connection was established after multiple attempts. Per the testing instructions, emulator testing is skipped.

### Could Not Verify
- Light/dark theme visual rendering on device
- Font scale 1.3 rendering
- 360dp-wide screen rendering
- Screenshot comparison with mockup
- Emulator-based locker PIN/fingerprint testing
- Camera screen layout verification (emulator camera shows fake scene)

## Known Issues
- None from code changes; all code compiles and builds cleanly
- Emulator testing could not be performed in this environment

## Delivery

- **Debug APK:** `delivery/FlipScan-redesign-debug.apk` (SHA-256: `352793C6FF6D11341E818785343C73C0FD7FA506721A0CC49C0107291E956743`)
- **Report:** `docs/REDESIGN_REPORT.md`
- **Commit info:** `delivery/commit-info.txt`
- **Git branch:** `redesign-ui` (commit `ac0abc1`)
- **Drive folder:** Not configured (placeholder path) — files are in `./delivery/` as fallback
- **GitHub draft release:** Not created (`gh` CLI not available/authenticated)
