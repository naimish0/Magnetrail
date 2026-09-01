# Localized Google Play artwork

This package contains one feature graphic plus eight portrait gameplay screenshots for phone, 7-inch tablet, and 10-inch tablet for every supported store-listing language.

## Country and language mapping

| Google Play market | Asset directory | Listing language |
| --- | --- | --- |
| India | `hi-IN` | Hindi |
| Brazil | `pt-BR` | Portuguese (Brazil) |
| Indonesia | `id-ID` | Indonesian |
| Mexico | `es-MX` | Spanish (Mexico) |
| Turkey | `tr-TR` | Turkish |
| Philippines | `fil-PH` | Filipino |
| Thailand | `th-TH` | Thai |
| United States, Canada, United Kingdom, Australia | `en` | English |
| Germany | `de-DE` | German |
| Japan | `ja-JP` | Japanese |
| South Korea | `ko-KR` | Korean |
| France | `fr-FR` | French |

## Files in each language directory

- `feature-graphic-1024x500.png`: opaque 24-bit RGB PNG, 1024 × 500. The artwork intentionally contains no marketing copy, so the same visual is suitable for every language without untranslated text.
- `screenshots/*.png`: eight phone screenshots at 1080 × 1920.
- `screenshots/tablet-7-inch/*.png`: eight 7-inch tablet screenshots at 1080 × 1920.
- `screenshots/tablet-10-inch/*.png`: eight 10-inch tablet screenshots at 1440 × 2560.

Every device set uses the same eight shipped campaign levels:

1. Level 13 — compact 5 × 5 polarity puzzle
2. Level 75 — readable 8 × 8 advanced puzzle
3. Level 300 — dense 8 × 8 advanced puzzle
4. Level 45 — sparse 6 × 6 path puzzle
5. Level 151 — balanced 5 × 5 puzzle
6. Level 202 — colorful 6 × 6 high-density puzzle
7. Level 1034 — compact 4 × 4 puzzle
8. Level 2020 — complex 7 × 7 puzzle

All screenshots are exact 9:16, opaque 24-bit RGB PNG files. They were captured from the real Compose game UI on an isolated Android 37 emulator without device frames, promotional overlays, system UI, notifications, personal data, live ads, or debug labels. The phone profile used 420 dpi, the 7-inch profile used 320 dpi, and the 10-inch profile used 320 dpi. The temporary debug-only capture activity was removed after capture and is not present in the completed source tree.

## Validation and visual QA

- `VALIDATION_REPORT.md` records the automated result for all 288 screenshots.
- `qa/ALL_LOCALES_CONTACT_SHEET.png` contains every screenshot for whole-package review.
- `qa/<locale>-contact-sheet.png` shows all 24 screenshots for one language at a larger review size.
- `../validate_localized_screenshots.py` reproduces file validation and the contact sheets.

All 288 files passed checks for count, filename, PNG readability, opaque RGB encoding, exact dimensions, exact 9:16 aspect ratio, and Google Play dimension bounds. The combined sheet and every locale sheet were visually inspected for localization, crop safety, board readability, system UI, debug content, and layout consistency.

The generated banner master is `../feature-graphic-gameplay-v2.png`. The earlier `../feature-graphic-1024x500.png` remains unchanged.

Google's requirements were live-verified on 2026-08-26. Google Play permits up to eight screenshots per supported device type and recommends 9:16 portrait screenshots with at least 1080px resolution for phones and large screens. The files satisfy the published image-format and dimension requirements, but final acceptance always remains subject to Google Play review.

- [Google Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Google Play store-listing best practices](https://support.google.com/googleplay/android-developer/answer/13393723)
