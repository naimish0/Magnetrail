#!/usr/bin/env python3
"""Build Google Ads image crops from untouched real-game emulator captures."""

from pathlib import Path
from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "images" / "raw"
FINAL = ROOT / "images" / "final"


ASSETS = (
    # Landscape crops tightly reframe the actual interaction area; no elements are overlaid.
    ("level013_start_1080x1920.png", "landscape/signature_polarity_level013_landscape_1200x628.png", (0, 476, 1080, 1042), (1200, 628)),
    ("level045_meaningful_middle_1080x1920.png", "landscape/meaningful_middle_level045_landscape_1200x628.png", (0, 710, 1080, 1276), (1200, 628)),
    ("level075_cancellation_state_1080x1920.png", "landscape/order_cancellation_level075_landscape_1200x628.png", (0, 650, 1080, 1216), (1200, 628)),
    ("level300_dense_start_1080x1920.png", "landscape/dense_challenge_level300_landscape_1200x628.png", (0, 668, 1080, 1234), (1200, 628)),
    # Square crops retain in-game status plus a complete or nearly complete board.
    ("level003_objective_complete_1080x1920.png", "square/objective_complete_level003_square_1200x1200.png", (0, 480, 1080, 1560), (1200, 1200)),
    ("level013_after_first_flip_1080x1920.png", "square/signature_after_flip_level013_square_1200x1200.png", (0, 368, 1080, 1448), (1200, 1200)),
    ("level151_balanced_choice_1080x1920.png", "square/balanced_choice_level151_square_1200x1200.png", (0, 368, 1080, 1448), (1200, 1200)),
    ("level300_endgame_decision_1080x1920.png", "square/dense_endgame_level300_square_1200x1200.png", (0, 368, 1080, 1448), (1200, 1200)),
    # Vertical crops preserve the genuine header/status and full board while excluding controls.
    ("level151_after_two_moves_1080x1920.png", "vertical/balanced_after_two_level151_vertical_1200x1500.png", (0, 65, 1080, 1415), (1200, 1500)),
    ("level045_balanced_start_1080x1920.png", "vertical/balanced_board_level045_vertical_1200x1500.png", (0, 65, 1080, 1415), (1200, 1500)),
    ("level075_cancellation_state_1080x1920.png", "vertical/cancellation_state_level075_vertical_1200x1500.png", (0, 65, 1080, 1415), (1200, 1500)),
    ("level300_dense_start_1080x1920.png", "vertical/dense_start_level300_vertical_1200x1500.png", (0, 65, 1080, 1415), (1200, 1500)),
)


def main() -> None:
    for source_name, relative_output, crop_box, target_size in ASSETS:
        source = RAW / source_name
        output = FINAL / relative_output
        if not source.is_file():
            raise FileNotFoundError(source)
        output.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(source) as image:
            source_rgb = image.convert("RGB")
            reframed = source_rgb.crop(crop_box)
            result = ImageOps.fit(
                reframed,
                target_size,
                method=Image.Resampling.LANCZOS,
                centering=(0.5, 0.5),
            )
            result.save(output, format="PNG", optimize=True, compress_level=9)
        print(output.relative_to(ROOT))


if __name__ == "__main__":
    main()
