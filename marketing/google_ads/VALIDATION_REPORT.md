# Google Ads asset validation report

Validation date: 2026-08-26

Overall automated result: **PASS**

## Automated checks

- SHORT_HEADLINES.csv: 15 rows, all at or below 30 characters
- LONG_HEADLINES.csv: 5 rows, all at or below 90 characters
- DESCRIPTIONS.csv: 5 rows, all at or below 90 characters
- landscape: 4 images at 1200x628, under 5 MB
- square: 4 images at 1200x1200, under 5 MB
- vertical: 4 images at 1200x1500, under 5 MB
- horizontal: 2 H.264/AAC videos at 1920x1080; durations 16.0s, 20.0s
- square: 2 H.264/AAC videos at 1080x1080; durations 16.0s, 20.0s
- vertical: 2 H.264/AAC videos at 1080x1920; durations 16.0s, 20.0s
- Image contact sheet exists
- Six video storyboards exist with 0, 2, 5, 10, and final-second frames
- Raw sources retained: 15 images and 2 videos
- Manifest contains 18 unique final assets.
- Final filenames are lowercase, unique, space-free, and describe orientation/dimensions.

## Errors

- None.

## Manual visual QA

The final regeneration was inspected at original resolution on 2026-08-26:

- Opened all 12 final images individually and opened the complete contact sheet.
- Opened all six final storyboards at 0, 2, 5, 10, and the final second.
- Boards, arrows, magnet polarities, walls, status counts, and intended decisions remain readable.
- Tight landscape crops prioritize the interaction area; no controlling piece needed to understand the scene is cut off.
- No final asset contains system UI, personal data, debug/test UI, consent, an ad, or an ad call-to-action.
- Colors, type, board art, completion UI, confetti, and official icon match the actual build/repository.
- Concept A shows its first real launch at about 1.4 seconds and two state changes in the first five seconds.
- Concept B shows a genuine overload/correction in the first two seconds and then continuously simplifies the board.
- Both concepts show action, reaction, decision, and actual completion before the 1.2-second brand finish.
- Captions were not added; all gameplay remains understandable muted, and the only added text is the product name on the brand finish.
