#!/usr/bin/env python3
"""Create one labeled QA contact sheet containing every final image."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ADS_ROOT = Path(__file__).resolve().parents[1]
FINAL = ADS_ROOT / "images" / "final"
OUTPUT = ADS_ROOT / "qa" / "IMAGE_CONTACT_SHEET.png"
ORIENTATIONS = (
    ("landscape", (360, 188), 250),
    ("square", (360, 360), 422),
    ("vertical", (320, 400), 462),
)
COLS = 4
CELL_WIDTH = 380


def fitted(image: Image.Image, bounds: tuple[int, int]) -> Image.Image:
    result = image.copy()
    result.thumbnail(bounds, Image.Resampling.LANCZOS)
    return result


def main() -> None:
    assets: list[tuple[str, Path, tuple[int, int], int]] = []
    for orientation, bounds, row_height in ORIENTATIONS:
        files = sorted((FINAL / orientation).glob("*.png"))
        if len(files) != 4:
            raise ValueError(f"Expected four {orientation} images, found {len(files)}")
        assets.extend((orientation, file, bounds, row_height) for file in files)

    total_height = sum(row_height for _, _, _, row_height in assets[::COLS])
    sheet = Image.new("RGB", (COLS * CELL_WIDTH, total_height), "#F4F7FB")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=14)
    row_y = 0
    for row_start in range(0, len(assets), COLS):
        row = assets[row_start : row_start + COLS]
        row_height = row[0][3]
        for column, (orientation, file, bounds, _) in enumerate(row):
            with Image.open(file) as source:
                preview = fitted(source.convert("RGB"), bounds)
            x = column * CELL_WIDTH + (CELL_WIDTH - preview.width) // 2
            y = row_y + 12
            sheet.paste(preview, (x, y))
            label = file.stem.replace(f"_{orientation}_", "\n").replace("_", " ")
            draw.multiline_text(
                (column * CELL_WIDTH + 10, y + preview.height + 8),
                label,
                fill="#172033",
                font=font,
                spacing=2,
                align="left",
            )
        row_y += row_height

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUTPUT, format="PNG", optimize=True, compress_level=9)
    print(OUTPUT.relative_to(ADS_ROOT))


if __name__ == "__main__":
    main()
