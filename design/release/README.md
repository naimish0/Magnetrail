# Magnetrail release-art sources

These M5 sources use the original split-ring magnet and Rail Dart identity from the M2 design system. They contain no competitor artwork, emoji, stock assets, downloaded font files, or third-party vectors.

## Exports

- `icon-source/magnetrail-mark.svg`: master square mark. Export the Play icon at 512 × 512 as 32-bit PNG under 1024 KB. Android adaptive foreground/background/monochrome layers are maintained as vector drawables under `app/src/main/res`.
- `feature-graphic/magnetrail-feature-graphic.svg`: master 1024 × 500 feature graphic. Export as 24-bit PNG or JPEG without alpha.
- `screenshots/raw/`: untouched 1080×2400 release-candidate captures. `screenshots/phone/en-US/` contains Play-compliant 1080×1920 RGB exports made with uniform scaling and background-only side padding; gameplay pixels are not cropped, stretched, composited, or fabricated. The capture manifest identifies device/build/state and supplies owner-review captions and alt text.

Before upload, inspect icon circle/squircle/squircle masks, small-size legibility, light/dark launch transition, feature-graphic crop safety, and Play's current asset preview.
