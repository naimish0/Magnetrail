# Magnetrail screenshot capture manifest

Captured **2026-08-19, Asia/Kolkata** from the actual M5 R8/resource-shrunk AAB through bundletool-generated device splits. Device: Pixel 7a AVD, Android 16/API 36, arm64-v8a, 16,384-byte page size, 1080×2400 portrait. AAB SHA-256: `810dcc3bb9071e6370c9faedf89e1962008c25333a60cbd2b7316265c671b116`.

The AAB itself is unsigned. The local APK set was signed only with the standard Android debug key so it could be installed for QA; it is not uploadable. Before capture, app data was cleared. The structural release has no production IDs/config, does not request UMP or ads, and contains no debug/test label. Dates and game state are real for the capture device. Captions below are proposed store overlays/callouts; raw screenshots have not been cosmetically altered.

Untouched 1080×2400 RGBA captures are retained under `raw/phone/en-US/`. Because Play currently rejects screenshots whose longest side exceeds twice the shortest side and recommends 1080×1920 portrait game images, the upload exports under `phone/en-US/` are 1080×1920 RGB PNGs. Each raw frame was uniformly scaled to 864×1920 and centered on 108-pixel side panels using the app's `#F4F7FB` background. No gameplay pixels were cropped, non-uniformly stretched, overlaid, or invented.

| File | Valid in-app state | Proposed caption | Alt text | SHA-256 |
|---|---|---|---|---|
| `phone/en-US/01-home.png` | Fresh home; 150 starting coins; Daily card for 2026-08-19 | A calm directional puzzle | Magnetrail home with Play, Level select, and Daily Challenge cards | `79984830061d6579ed6d5b5cf4d4ebd1373d48855af8acebd0fdfca0601bbcde` |
| `phone/en-US/02-field-basics.png` | Campaign level 1 before its single legal move | Clear every arrow | Intro board with one east-facing Rail Dart on a square grid | `b75486e0da6d0bc4bad0c25977e0ca01e80c2a6a89c7d10d2a831113eabb704e` |
| `phone/en-US/03-level-complete.png` | Level 1 cleared in one action; three stars; first-clear rewards | Find the perfect sequence | Board-cleared result showing three stars, one action, and earned coins | `86ec34c80498094ef31afef1537874ac449db727ed58af86e0a88586d2bb7ac1` |
| `phone/en-US/04-level-catalog.png` | Campaign after level 1; catalog reports 1 of 100 boards | 100 handcrafted campaign boards | Campaign catalog with level cards, progress, Pull, Push, and Automatic flip names | `02b132db022f3fe068a1fa790e173bfcd13204c5d086e0d2b70945a80c41b40d` |
| `phone/en-US/05-hint-choice.png` | Level 2 hint dialog; sufficient coins; ads unavailable in structural release | Choose an optional hint | Solver hint dialog offering 30 coins while the unavailable ad option is disabled | `4aa355ddb38ff6c015d959874317252359e5c7e5c7e29e7f88623c8a05ddc5f1` |
| `phone/en-US/06-solver-hint.png` | Level 2 after a 30-coin hint; solver selects arrow B | Plan the next safe move | Two-arrow board with arrow B highlighted by a solver-verified hint | `f3c2f415823a9fc496d9e4637adb57a71f27ab5a3c645aff38540807b5d8d8d2` |
| `phone/en-US/07-settings.png` | Settings; diagnostics off; reduced motion on | Comfortable, accessible controls | Settings for sound, diagnostics, haptics, reduced motion, contrast, and path preview | `b1bb999620bb4c6b8fd0c7acfd77d744c7b26d49393ff6d06d982142762686c8` |
| `phone/en-US/08-daily-challenge.png` | Generated Daily board for device-local date 2026-08-19 | A fresh daily magnetic challenge | Daily puzzle board with three arrows, blockers, and a Pull magnet | `042bb37fb59e3d659acd6cc1638f7df79a20356289d0deda2acdeedda665cf70` |

Before upload, recapture from the final **signed** candidate if its binary hash, configuration, UI, date policy, or content changes. Recalculate all hashes, perform owner copy/visual review, and use Play's previews to verify legibility and crop safety. Do not overlay a claim that the pictured state does not demonstrate.
