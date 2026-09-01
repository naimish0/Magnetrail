#!/usr/bin/env python3
"""Extract the required QA timeline frames for every final advertising video."""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont


ADS_ROOT = Path(__file__).resolve().parents[1]
FINAL = ADS_ROOT / "videos" / "final"
OUTPUT = ADS_ROOT / "qa" / "video_storyboards"
FFMPEG = Path("/opt/homebrew/bin/ffmpeg")
FFPROBE = Path("/opt/homebrew/bin/ffprobe")
FONT = Path("/System/Library/Fonts/Supplemental/Arial.ttf")
CELL_WIDTH = 300
PREVIEW_BOUNDS = (280, 480)


def duration(video: Path) -> float:
    result = subprocess.run(
        [
            str(FFPROBE), "-v", "error", "-show_entries", "format=duration",
            "-of", "json", str(video),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(json.loads(result.stdout)["format"]["duration"])


def main() -> None:
    videos = sorted(FINAL.glob("*/*.mp4"))
    if len(videos) != 6:
        raise ValueError(f"Expected six final videos, found {len(videos)}")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    label_font = ImageFont.truetype(str(FONT), 20)
    title_font = ImageFont.truetype(str(FONT), 22)

    with tempfile.TemporaryDirectory(prefix="magnetrail-storyboards-") as temporary:
        work = Path(temporary)
        for video in videos:
            length = duration(video)
            timestamps = [0.0, 2.0, 5.0, 10.0, max(0.0, length - 1.0)]
            previews: list[Image.Image] = []
            for index, timestamp in enumerate(timestamps):
                frame = work / f"{video.stem}_{index}.png"
                subprocess.run(
                    [
                        str(FFMPEG), "-hide_banner", "-loglevel", "error", "-y",
                        "-ss", f"{timestamp:.3f}", "-i", str(video),
                        "-frames:v", "1", str(frame),
                    ],
                    check=True,
                )
                with Image.open(frame) as image:
                    preview = image.convert("RGB")
                    preview.thumbnail(PREVIEW_BOUNDS, Image.Resampling.LANCZOS)
                    previews.append(preview.copy())

            title_height = 54
            label_height = 44
            content_height = max(image.height for image in previews)
            sheet = Image.new(
                "RGB",
                (CELL_WIDTH * len(previews), title_height + content_height + label_height),
                "#F4F7FB",
            )
            draw = ImageDraw.Draw(sheet)
            draw.text((14, 14), video.stem.replace("_", " "), fill="#172033", font=title_font)
            for index, (preview, timestamp) in enumerate(zip(previews, timestamps)):
                x = index * CELL_WIDTH + (CELL_WIDTH - preview.width) // 2
                y = title_height
                sheet.paste(preview, (x, y))
                label = f"{timestamp:.1f} s" if index < 4 else f"final - 1 s ({timestamp:.1f} s)"
                draw.text((index * CELL_WIDTH + 12, title_height + content_height + 10), label, fill="#183153", font=label_font)

            output = OUTPUT / f"{video.stem}_storyboard.png"
            sheet.save(output, format="PNG", optimize=True, compress_level=9)
            print(output.relative_to(ADS_ROOT))


if __name__ == "__main__":
    main()
