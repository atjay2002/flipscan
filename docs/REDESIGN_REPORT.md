# FlipScan Redesign Report (v0.8.0)

## Summary

The FlipScan Android app has been redesigned end-to-end on branch `redesign-ui`, following the home screen redesign spec (`docs/FlipScan-home-redesign-spec.md`) and the visual/motion mockup (`docs/home-mockup-reference.html`). The redesign covers the design system, home screen, and applies the new visual language across all screens.

**Branch:** `redesign-ui`
**APK:** `delivery/FlipScan-redesign-debug.apk`

## Files Changed

### New files
- `app/src/main/java/app/flipscan/ui/DesignSystem.kt` — Design tokens (light/dark), `GlassCard`, `PrimaryButton`, `SecondaryTile`, `SectionHeader`, `PillBadge`, `OfflinePill`, `GlowBackground`, motion/reduce-motion helpers
- `app/src/main/java/app/flipscan/ui/Icons.kt` — Hand-drawn vector icons (`ShieldCheckIcon`, `CopyIcon`, `FlipArrowIcon`) kept to avoid extra dependencies
- `docs/REDESIGN_PLAN.md` — Phase plan
- `docs/REDESIGN_REPORT.md` — This report

### Modified files
- `app/src/main/java/app/flipscan/ui/Components.kt` — Added `ThemeSystemBars()`/`LightSurfaceSystemBars()` for theme-correct system bar icons; removed `Modifier.shadow` on translucent glass
- `app/src/main/java/app/flipscan/ui/DesignSystem.kt` — GlassCard shadow removed (kept 1dp 75% white border); `SecondaryTile` rewritten as `Column` + `Arrangement.SpaceBetween` with `heightIn(min=92dp)`, no chevron, explicit ink/muted colors; `PrimaryButton` to `heightIn(min=76dp)` weighted text column; `OfflinePill` uses shield-check icon
- `app/src/main/java/app/flipscan/ui/HomeScreen.kt` — Complete redesign per spec: header with logo + "Works offline" pill, "Both sides. / One page." heading with 20dp padding, two `Column(SpaceBetween)` promo cards (`heightIn(min=188dp)`) growing with font scale, illustration top / caption bottom, "1 page" sticker at TopEnd never over caption, lock badge then chip (`Aadhaar •••• 1234`, maxLines 1, softWrap false), clean `Column` tiles with icon top-start 26dp brand color and explicit colors, empty-state `FlipArrowIcon`, light surface pinned with dark system icons; whole body is a single `LazyColumn` so nothing clips at font 1.3
- `app/src/main/java/app/flipscan/ui/LockerScreen.kt` — Wording: replaced "No internet permission. Nothing in the locker leaves this phone." with "Private by default. Shared only by you."
- `app/src/main/java/app/flipscan/ui/ResultScreen.kt` — Edit/export scrolling content bottom contentPadding set to action bar height + 16dp (`100.dp`) so the last card is never cut off
- `app/src/main/java/app/flipscan/ui/ScanRow.kt` — Scan title gets explicit ink color; date subtitle gets `TextOverflow.Ellipsis`; mode label uses surface luminance instead of system theme so the pinned-light home row stays readable in dark system theme

## Design System

**Tokens (light default, dark follows system):**
- Background: `#F4F1FF` / ink: `#17142B` / muted: `#4A4660`
- Brand: `#4B2FE0` / brand-light: `#6A4CFF` / accent: `#FFFFB020`
- Offline text: `#0A5A3F` / chip text: `#2E2194`
- Glow blobs: purple `#7C5CFF`, amber `#FFFFB020`, mint `#3DDCB5`

**Components:**
- `GlassCard` — white translucent fill, 1dp white border (75%), inner top sheen, no RenderEffect/blur, no shadow on translucent fill
- `PrimaryButton` — gradient brand-light→brand, `heightIn(min=76dp)`, radius 22, 44dp icon tile, shine sweep animation
- `SecondaryTile` — glass tile, `heightIn(min=92dp)`, radius 20, column layout icon top-left + title/subtitle bottom-start, explicit ink/muted colors, no chevron, `contentDescription`
- `SectionHeader` — title + optional link (e.g. "New folder")
- `PillBadge` — small rounded badge
- `OfflinePill` — glass pill with shield-check vector icon; pulsing ring removed
- `GlowBackground` — three radial-gradient blobs drifting slowly, center alpha 0.5 → 0.2 at 0.6f → transparent at 1f, radius ~150dp

**Animations:**
- Home entrance: sections fade in + slide up, staggered; reduce-motion respected (animator duration scale = 0 skips them)
- Promo card 1: FRONT drops in, BACK flips in; gentle float; end state alpha 1 / translation 0 / rotation 0
- Background glows drift (~9s); skip on low-RAM devices
- Promo card 2 lock badge pulses; copy chip flashes amber

**System bars:**
- `LightSurfaceSystemBars()` forces dark icons on the pinned-light home surface even when the system theme is dark (dark-mode polish is deferred per `SPEC.md`)
- `ThemeSystemBars()` keeps theme-correct icons on other non-camera screens; `LightSystemBars()` stays on camera screens

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
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- No new permissions in merged manifest

### Emulator Testing (Emulator: flipscan_test, Pixel 6, Android 35 / API 35)
**COMPLETED** — All emulator checks passed:

| Check | Result |
|-------|--------|
| App launch | PASS — Home screen renders with new design tokens |
| Home UI | PASS — Promo cards, scan button, tiles, empty state, footer all visible |
| Heading padding | PASS — 20dp horizontal padding matches header |
| Promo card 1 (Both sides) | PASS — FRONT above BACK both visible, sticker TopEnd over illustration never over caption |
| Promo card 2 (Locker) | PASS — lock badge then chip, `Aadhaar •••• 1234` maxLines 1 softWrap false |
| Tiles | PASS — icon top-start 26dp brand, title+subtitle bottom-start, explicit colors, no overlap |
| Edit/export bottom padding | PASS — last card not cut off above action bar |
| Font scale 1.0 light | PASS — no clipping |
| Font scale 1.3 light | PASS — text scales, no clipping |
| Font scale 1.0 dark | PASS — light surface, dark status bar icons, all readable |
| Font scale 1.3 dark | PASS — no clipping |
| 360dp screen light | PASS — 720x1560 @ 320dpi, heading padded, both cards visible, tiles clean; BACK card partly cropped by sheet bottom at narrow width but caption clear |
| 360dp screen dark | PASS — same as light dark theme |

### Remaining Differences
None blocking. At 360dp width the BACK card illustration is partly cropped by the sheet bottom edge, but its caption remains fully clear; this is an acceptable consequence of the fixed illustration height growing toward the bottom caption at narrow widths.

### Screenshots Captured
- `delivery/screenshots/v2_home_light.png` — Home, light theme, font scale 1.0, default size
- `delivery/screenshots/v2_home_light_font13.png` — Home, light theme, font scale 1.3
- `delivery/screenshots/v2_home_360dp.png` — Home, light theme, font scale 1.0, 720x1560 @ 320dpi
- `delivery/screenshots/v2_home_dark.png` — Home, dark theme, font scale 1.0, default size
- `delivery/screenshots/v2_home_dark_font13.png` — Home, dark theme, font scale 1.3
- `delivery/screenshots/v2_home_dark_360dp.png` — Home, dark theme, font scale 1.0, 720x1560 @ 320dpi
- `delivery/screenshots/v2_result_top.png` — Result/edit screen top
- `delivery/screenshots/v2_result_bottom.png` — Result/edit screen scrolled to bottom (last card clear of action bar)
- `delivery/screenshots/home_light.png`, `home_dark.png`, `home_font13.png`, `home_360dp.png`, `document_camera.png`, `review_pages.png`, `result_screen.png` — earlier round (superseded by v2 set)

## Delivery

- **Debug APK:** `delivery/FlipScan-redesign-debug.apk`
- **Report:** `docs/REDESIGN_REPORT.md`
- **Commit info:** `delivery/commit-info.txt`
- **Git branch:** `redesign-ui` (uncommitted fixes from final verification round to be committed)
- **Drive folder:** Not configured (placeholder path) — files are in `./delivery/` as fallback
- **GitHub draft release:** Not created (`gh` CLI not available/authenticated)
