# FlipScan home screen redesign (v0.8.0): implementation spec

Reference mockup: `home-mockup-reference.html` (read its inline styles for exact sizes, colors and animation timings). This spec wins if they differ.

## Rules (read first)
- Work on a new branch `redesign-home`. Small commits.
- UI and wording only. Do NOT change anything under `data/`, `LockerCrypto`, scanning, OCR or export logic.
- Keep every existing home-screen behavior: folders, recent scans list, New folder, navigation, permission handling.
- No INTERNET permission, no new network libraries, no new heavy dependencies. Compose only.
- First read the existing theme files and `ui/HomeScreen.kt`, then write a short plan, then implement.

## Theme tokens (light default; support dark too, following system)
- background `#F4F1FF`, ink `#17142B`, muted text `#4A4660`
- brand `#4B2FE0`, brand-light `#6A4CFF`, accent `#FFB020`
- offline text `#0A5A3F`, chip text `#2E2194`
- glow colors: purple `#7C5CFF`, amber `#FFB020`, mint `#3DDCB5`
- Dark theme: derive from the current dark palette, same layout, text contrast at least 4.5:1.

## Glass surface (reusable composable `GlassCard`)
- Fill white at 55% alpha, 1dp border white at 75% alpha, soft shadow tinted brand, small inner top highlight.
- Do NOT use RenderEffect or backdrop blur. Create the colored glows behind with `Brush.radialGradient` blobs (purple top-left, amber right-middle, mint bottom-left). Same look on every Android version.

## Layout (reference width 390dp, 20dp horizontal padding, 14dp vertical gaps)
1. Header: logo mark 40dp (radius 12, brand), "FlipScan" 20sp ExtraBold. Right: glass pill "Works offline" with a shield-check icon, 13sp Bold, color offline text, soft pulsing ring.
2. Heading 34sp ExtraBold, line height about 1.05: "Both sides." then "One page." in brand color.
3. Promo row: two equal glass cards, 12dp gap, height 188dp, radius 24.
   - Card 1: mini white sheet (100x116dp) with FRONT card (brand, 78x46) above a BACK card (near-black `#17142B`, 78x46), amber "1 page" sticker rotated 6 degrees, caption "Both sides, one page" 14sp ExtraBold + "Print-ready, real card size" 12sp.
   - Card 2: 56dp brand gradient lock badge with pulsing ring, a white pill chip "Aadhaar •••• 1234" with a copy icon (ILLUSTRATION ONLY, never real data), caption "Locked on your phone" 14sp ExtraBold + "Copy ID numbers in one tap" 12sp.
4. Primary button "Scan ID card": full width, 76dp, radius 22, gradient brand-light to brand, 44dp icon tile, title 18sp ExtraBold, subtitle "Aadhaar, PAN, DL, voter ID" 13sp, chevron, shadow.
5. Tiles row (two equal, 92dp, radius 20, glass): "Document / Passbooks, notes" and "Digital Locker / Open your vault".
6. Section row: "Recent scans" 17sp ExtraBold and "New folder" 14sp Bold link. Below: the existing recent-scans list; when empty, a glass card with a flip icon, "Scan your first ID" and "Saved scans stay on this phone."
7. Footer 12sp: "Private by default. Shared only by you."
- Minimum text size 12sp on real UI.

## Tap behavior
- Promo card 1 opens the same ID card scan flow as the "Scan ID card" button.
- Promo card 2 opens the Digital Locker through the SAME biometric / screen-lock gate as the Locker tile. If no screen lock is set, show a friendly message instead of failing.
- Each card is one clickable target with role Button and a content description ("Scan ID card, both sides on one page", "Open Digital Locker"). Press animation: scale to 0.97.
- The home screen never shows real document numbers.

## Animations
- Entrance (once): sections fade in and slide up 16dp, 600ms, staggered at 50, 150, 450, 600, 750ms.
- Card 1: FRONT card drops in from -24dp with rotation -8 to -3 degrees (800ms, overshoot). BACK card flips in with rotationX -95 to 0 (900ms, delay 700ms, `cameraDistance` set). Afterwards both float up and down 5dp over 4.5 seconds (infinite, opposite phases).
- Background glows drift slowly (about 9 seconds, translate about 20dp, scale to 1.12).
- Scan ID card button: light shine sweeps left to right every 3.6 seconds.
- Offline pill and lock badge: pulsing ring about every 2 to 2.4 seconds.
- Copy chip background flashes amber briefly every 4 seconds.
- Empty-state flip icon: rotationY 0 to 180 to 360 over 4 seconds.
- Reduce motion: if animator duration scale is 0, skip all infinite and entrance animations. Pause infinite animations when the screen is not RESUMED. On low-end devices (`ActivityManager.isLowRamDevice`), skip the glow drift and floating.

## Done when
- Debug APK builds. Check light and dark theme, font scale 1.3, and a 360dp-wide screen with no clipped or overlapping text.
- No new permissions in the merged manifest.
- Both promo cards and the Locker tile open the right screens.
- Summarize files changed at the end.

## Wording consistency (privacy line)
The approved tagline is: **"Private by default. Shared only by you."**
Reason: users can export and share documents on purpose, so absolute claims like "nothing leaves your phone" are not accurate.

Do this across the whole repo:
1. Search the app (Kotlin, `strings.xml`, any hard-coded text) and the docs (`README.md`, release notes, `docs/`) for these phrases and similar absolute claims: "Nothing leaves your phone", "never leaves", "never leave your device", "No internet permission. Nothing leaves".
2. Replace them with the tagline, or with an accurate softer wording (for example "stays on your device unless you share it").
3. Use the tagline as the home screen footer (already in the layout above) and as the first line of the README. If the app has an About or Settings screen, add it there too.
4. KEEP factual technical statements that are true, such as "FlipScan has no INTERNET permission", "AES-256-GCM encryption", and the note that exported files are not encrypted. Do not edit `PRIVACY.md` except to fix an absolute claim if you find one.
5. List every file and line you changed in the final summary.
