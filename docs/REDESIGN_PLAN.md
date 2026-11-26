# FlipScan Redesign Plan (v0.8.0)

## Branch
`redesign-ui` (created from `main`). Never merge into `main`.

## Scope
UI and wording only. No changes to `data/`, `LockerCrypto`, scanning, OCR, edge detection, PDF/image export, or number-extraction logic. No new permissions, no network libraries, no heavy new dependencies.

## Screens (in implementation order)

| # | Screen | File | Changes |
|---|--------|------|---------|
| 1 | Theme / design system | `ui/Theme.kt` + new `ui/DesignSystem.kt` | Light/dark token palette per spec (`#F4F1FF` bg, `#17142B` ink, `#4B2FE0` brand, etc.), `GlassCard`, `PrimaryButton` (gradient + shine), `SecondaryTile`, `SectionHeader`, `PillBadge`, `GlowBackground`, reusable `pressable` modifier |
| 2 | Home screen | `ui/HomeScreen.kt` | Promo cards (FRONT/BACK illustration, lock chip), "Scan ID card" primary button, Document & Digital Locker tiles, Recent scans section with empty state, footer "Private by default. Shared only by you.", entrance/staggered animations, reduce-motion handling |
| 3 | Camera screens (Double Side + Regular) | `ui/DoubleSideScreen.kt`, `ui/RegularScreen.kt` | Keep full-bleed dark preview; glass controls (header, shutter, flash, status pill); clear front/back step indicator |
| 4 | Result / edit screen | `ui/ResultScreen.kt` | Glass panels, filter chips, paper/orientation/margin controls, rotate/crop per page, rename |
| 5 | Pages review | `ui/PagesScreen.kt` | Glass rows, reorder/delete buttons |
| 6 | Crop editor | `ui/CropScreen.kt` | Keep dark high-contrast surface; glass control buttons; handles stay high-contrast |
| 7 | Folders | `ui/FolderScreen.kt`, `ui/FoldersUi.kt` | Glass folder tiles, glass action sheet, glass dialogs |
| 8 | Recent scans row | `ui/ScanRow.kt` | Glass row, action menu trigger |
| 9 | Locker gate | `ui/LockerScreen.kt` (`LockerGateScreen`) | Glass surface, pulsing lock icon, same biometric gate |
| 10 | Locker home/list | `ui/LockerScreen.kt` (`LockerHomeScreen`) | Glass doc rows, glass scan panel, Digital Pins section |
| 11 | Locker viewer | `ui/LockerViewerScreen.kt` | Glass page stage, glass tool buttons, number copy UI (masked/reveal), export dialog |
| 12 | Common | `ui/Components.kt` | `ScreenHeader`, `CameraHeader`, `BottomActionBar`, `EmptyState`, `StepPill`, `StatusPill` restyled |

## Shared components (new file `ui/DesignSystem.kt`)
- `GlassCard` - white 55% alpha fill, 1dp white 75% border, brand-tinted soft shadow, inner top highlight, no RenderEffect/blur; glow blobs behind via `Brush.radialGradient`
- `PrimaryButton` - gradient brand-light→brand, 44dp icon tile, shine sweep animation
- `SecondaryTile` - glass tile, 92dp height, radius 20, icon + title + subtitle
- `SectionHeader` - title + optional link (e.g. "New folder")
- `PillBadge` - small rounded badge
- `GlowBackground` - radial-gradient blobs (purple top-left, amber right-middle, mint bottom-left), drift animation
- `pressable` modifier (already exists, reuse)

## Wording consistency
- Replace "No internet permission. Nothing leaves your phone." and similar absolute claims with "Private by default. Shared only by you."
- Home screen footer uses the tagline.
- Keep factual technical statements ("no INTERNET permission", "AES-256-GCM", etc.).

## Animations
- Home entrance: sections fade in + slide up 16dp, 600ms, staggered (50, 150, 450, 600, 750ms)
- Promo card 1: FRONT drops in (-24dp, rotate -8→-3°, 800ms overshoot); BACK flips in (rotateX -95→0°, 900ms, delay 700ms); both float 5dp over 4.5s (infinite, opposite phases)
- Background glows drift (~9s, translate ~20dp, scale 1.12)
- Primary button shine sweeps every 3.6s
- Offline pill and lock badge pulse every 2–2.4s
- Copy chip flashes amber every 4s
- Empty-state flip icon rotates Y 0→180→360 over 4s
- Reduce motion: skip all infinite/entrance animations when animator duration scale is 0; pause infinite animations when not RESUMED; skip glow drift and floating on low-RAM devices

## Verification after each phase
- `./gradlew assembleDebug` must pass
- Emulator checks: light/dark theme, font scale 1.3, 360dp-wide screen, no clipped text
