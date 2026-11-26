# FlipScan Redesign Report (v0.8.0)

## Summary

The FlipScan Android app has been redesigned end-to-end on branch `redesign-ui`, following the home screen redesign spec (`docs/FlipScan-home-redesign-spec.md`) and the visual/motion mockup (`docs/home-mockup-reference.html`). The redesign covers the design system, home screen, and applies the new visual language across all screens.

**Branch:** `redesign-ui`
**Commit:** `4039f87`
**APK:** `delivery/FlipScan-redesign-debug.apk`
**SHA-256:** `E012D8F07E2091F5FF19A54A43440D94ED06A13EDA9188D1FB40E679429C5101`

## Files Changed

### New files
- `app/src/main/java/app/flipscan/ui/DesignSystem.kt` — Design tokens (light/dark), `GlassCard`, `PrimaryButton`, `SecondaryTile`, `SectionHeader`, `PillBadge`, `OfflinePill`, `GlowBackground`, motion/reduce-motion helpers
- `docs/REDESIGN_PLAN.md` — Phase plan
- `docs/REDESIGN_REPORT.md` — This report

### Modified files
- `app/src/main/java/app/flipscan/ui/Theme.kt` — Updated `LightColors`/`DarkColors` to use new brand tokens (`#4B2FE0` primary, `#F4F1FF` background, etc.)
- `app/src/main/java/app/flipscan/ui/HomeScreen.kt` — Complete redesign per spec: header with logo + "Works offline" pill, "Both sides. / One page." heading with 20dp padding, two promo cards (FRONT/BACK illustration, lock chip), gradient "Scan ID card" primary button with shine sweep, Document + Digital Locker secondary tiles with column layout and explicit text colors, Recent scans section with empty state, "Private by default. Shared only by you." footer, entrance animations with staggering, reduce-motion and low-RAM handling, light status bar icons
- `app/src/main/java/app/flipscan/ui/LockerScreen.kt` — Wording: replaced "No internet permission. Nothing in the locker leaves this phone." with "Private by default. Shared only by you."

## Design System

**Tokens (light default, dark follows system):**
- Background: `#F4F1FF` / ink: `#17142B` / muted: `#4A4660`
- Brand: `#4B2FE0` / brand-light: `#6A4CFF` / accent: `#FFFFB020`
- Offline text: `#0A5A3F` / chip text: `#2E2194`
- Glow blobs: purple `#7C5CFF`, amber `#FFFFB020`, mint `#3DDCB5`

**Components:**
- `GlassCard` — white 55% fill, 1dp white border, brand-tinted soft shadow, inner top sheen, no RenderEffect/blur
- `PrimaryButton` — gradient brand-light→brand, 76dp height, radius 22, 44dp icon tile, shine sweep animation every 3.6s with peak alpha 0.25
- `SecondaryTile` — glass tile, 92dp height, radius 20, column layout with icon top-left, title 16sp Bold in ink #17142B, subtitle 12sp in muted #4A4660, no shadow on translucent fill
- `SectionHeader` — title + optional link (e.g. "New folder")
- `PillBadge` — small rounded badge
- `OfflinePill` — glass pill with shield-check vector icon and pill-shaped pulsing ring centered on pill
- `GlowBackground` — three radial-gradient blobs (purple top-left, amber right-middle, mint bottom-left) drifting slowly (~9s), center alpha 0.5 → 0.2 at 0.6f → transparent at 1f, radius 140-160dp

**Animations:**
- Home entrance: sections fade in + slide up 16dp, 600ms, staggered (50, 150, 450, 600, 750ms)
- Promo card 1: FRONT drops in, BACK flips in; both float 5dp over 4.5s (opposite phases)
- Background glows drift (~9s, translate ~20dp, scale 1.12)
- Primary button shine sweeps every 3.6s
- Offline pill and lock badge pulse every 2–2.4s
- Copy chip flashes amber every 4s
- Empty-state flip arrow rotates Y 0→180→360 over 4s
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

### Emulator Testing (Emulator: flipscan_test, Pixel 6, Android 35 / API 35)
**COMPLETED** — All emulator checks passed:

| Check | Result |
|-------|--------|
| App launch | PASS — Home screen renders with new design tokens |
| Home UI | PASS — Promo cards, scan button, tiles, empty state, footer all visible |
| Camera permission | PASS — Dialog appears, permission granted |
| Document capture | PASS — Shutter works, page count 0→1, "Done (1)" visible |
| Review pages | PASS — Page thumbnails, reorder/delete controls, Continue works |
| Result screen | PASS — Paper size, Orientation, Margin, Filter, Format panels all render |
| Folder create | PASS — Dialog opens, name entry, Create works |
| Light theme | PASS — Light status bar icons visible, no white-on-white |
| Dark theme | PASS — App recomposes with dark theme |
| Font scale 1.3 | PASS — Text scales, no clipping |
| 360dp screen | PASS — 720x1560 @ 320dpi, no clipped text |

### Screenshots Captured
- `delivery/screenshots/home_light.png` — Home, light theme, font scale 1.0
- `delivery/screenshots/home_dark.png` — Home, dark theme
- `delivery/screenshots/home_dark_font13.png` — Home, dark theme, font scale 1.3
- `delivery/screenshots/home_font13.png` — Home, light theme, font scale 1.3
- `delivery/screenshots/home_360dp.png` — Home, 720x1560 @ 320dpi
- `delivery/screenshots/document_camera.png` — Document camera screen
- `delivery/screenshots/review_pages.png` — Review pages screen
- `delivery/screenshots/result_screen.png` — Result screen

## Known Issues
- None from code changes; all code compiles and builds cleanly
- Emulator camera shows fake scene (expected), camera screens judged by layout only

## Delivery

- **Debug APK:** `delivery/FlipScan-redesign-debug.apk` (SHA-256: `E012D8F07E2091F5FF19A54A43440D94ED06A13EDA9188D1FB40E679429C5101`)
- **Report:** `docs/REDESIGN_REPORT.md`
- **Commit info:** `delivery/commit-info.txt`
- **Git branch:** `redesign-ui` (commit `4039f87`, pushed to origin)
- **Drive folder:** Not configured (placeholder path) — files are in `./delivery/` as fallback
- **GitHub draft release:** Not created (`gh` CLI not available/authenticated)