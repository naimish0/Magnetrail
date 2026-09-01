#!/usr/bin/env python3
"""Build six Google Ads videos from untouched real-game screen recordings."""

from __future__ import annotations

from array import array
import math
from pathlib import Path
import subprocess
import tempfile
import wave

from PIL import Image, ImageDraw, ImageFont


ADS_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ADS_ROOT.parents[1]
RAW = ADS_ROOT / "videos" / "raw"
FINAL = ADS_ROOT / "videos" / "final"
ICON = REPO_ROOT / "design" / "release" / "play-store" / "icon-512.png"
FONT = Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf")
FFMPEG = Path("/opt/homebrew/bin/ffmpeg")

SAMPLE_RATE = 22_050
BRAND_SECONDS = 1.2

TONE_SPECS = {
    "select": (460.0, 520.0, 0.038, 0.10, None),
    "travel": (520.0, 610.0, 0.072, 0.08, None),
    "completion": (520.0, 780.0, 0.230, 0.15, 1_040.0),
}

CONCEPTS = {
    "concept_a_signature_mechanic": {
        "source": RAW / "concept_a_signature_level013_1080x1920.mp4",
        "source_start": 2.2,
        "game_seconds": 14.8,
        "events": [
            (1.35, "select"), (1.40, "travel"),
            (2.82, "select"), (2.87, "travel"),
            (4.60, "select"), (4.65, "travel"),
            (6.40, "select"), (6.45, "travel"),
            (7.35, "completion"),
        ],
    },
    "concept_b_challenge_satisfaction": {
        "source": RAW / "concept_b_challenge_level300_1080x1920.mp4",
        "source_start": 4.9,
        "game_seconds": 18.8,
        "events": [
            (0.85, "select"), (0.90, "travel"),
            (2.33, "select"), (2.38, "travel"),
            (2.86, "select"), (2.91, "travel"),
            (3.87, "select"), (3.92, "travel"),
            (4.29, "select"), (4.34, "travel"),
            (4.65, "select"), (4.70, "travel"),
            (5.40, "select"), (5.45, "travel"),
            (6.38, "select"), (6.43, "travel"),
            (6.88, "select"), (6.93, "travel"),
            (8.15, "select"), (8.20, "travel"),
            (9.24, "select"), (9.29, "travel"),
            (9.64, "select"), (9.69, "travel"),
            (10.12, "select"), (10.17, "travel"),
            (10.42, "select"), (10.47, "travel"),
            (11.01, "select"), (11.06, "travel"),
            (11.93, "select"), (11.98, "travel"),
            (13.18, "select"), (13.23, "travel"),
            (14.15, "completion"),
        ],
    },
}

FORMATS = {
    "vertical": {
        "size": (1080, 1920),
        "filter": "crop=1080:1690:0:0,pad=1080:1920:0:115:color=0xF4F7FB",
        "suffix": "vertical_1080x1920",
    },
    "square": {
        "size": (1080, 1080),
        "filter": "crop=1080:1080:0:360",
        "suffix": "square_1080x1080",
    },
    "horizontal": {
        "size": (1920, 1080),
        "filter": "crop=1080:608:0:450,scale=1920:-2,crop=1920:1080",
        "suffix": "horizontal_1920x1080",
    },
}


def font(size: int) -> ImageFont.FreeTypeFont:
    if not FONT.is_file():
        raise FileNotFoundError(FONT)
    return ImageFont.truetype(str(FONT), size=size)


def brand_card(size: tuple[int, int], output: Path) -> None:
    width, height = size
    card = Image.new("RGB", size, "#F4F7FB")
    draw = ImageDraw.Draw(card)
    with Image.open(ICON) as source:
        icon_size = int(min(width, height) * (0.27 if width == height else 0.25))
        icon = source.convert("RGBA").resize((icon_size, icon_size), Image.Resampling.LANCZOS)

    if width > height:
        text_font = font(104)
        text = "Magnetrail"
        text_box = draw.textbbox((0, 0), text, font=text_font)
        group_width = icon_size + 72 + (text_box[2] - text_box[0])
        icon_x = (width - group_width) // 2
        icon_y = (height - icon_size) // 2
        text_x = icon_x + icon_size + 72
        text_y = (height - (text_box[3] - text_box[1])) // 2 - text_box[1]
    else:
        text_font = font(86 if width == height else 92)
        text = "Magnetrail"
        text_box = draw.textbbox((0, 0), text, font=text_font)
        group_height = icon_size + 64 + (text_box[3] - text_box[1])
        icon_x = (width - icon_size) // 2
        icon_y = (height - group_height) // 2
        text_x = (width - (text_box[2] - text_box[0])) // 2
        text_y = icon_y + icon_size + 64 - text_box[1]

    card.paste(icon, (icon_x, icon_y), icon)
    draw.text((text_x, text_y), text, fill="#183153", font=text_font)
    card.save(output, format="PNG", optimize=True, compress_level=9)


def tone_samples(name: str) -> array:
    start_hz, end_hz, seconds, gain, harmonic_hz = TONE_SPECS[name]
    frame_count = max(1, int(SAMPLE_RATE * seconds))
    samples = array("h")
    for index in range(frame_count):
        progress = index / frame_count
        envelope = max(0.0, math.sin(math.pi * progress)) * gain
        frequency = start_hz + (end_hz - start_hz) * progress
        harmonic = 0.0 if harmonic_hz is None else (
            math.sin(2.0 * math.pi * harmonic_hz * index / SAMPLE_RATE) * 0.34
        )
        value = (math.sin(2.0 * math.pi * frequency * index / SAMPLE_RATE) + harmonic) * envelope
        samples.append(int(max(-1.0, min(1.0, value)) * 32767))
    return samples


def feedback_track(duration: float, events: list[tuple[float, str]], output: Path) -> None:
    frame_count = int(math.ceil(duration * SAMPLE_RATE))
    mixed = [0] * frame_count
    for event_time, cue in events:
        start = int(event_time * SAMPLE_RATE)
        for offset, value in enumerate(tone_samples(cue)):
            index = start + offset
            if index >= frame_count:
                break
            mixed[index] = max(-32768, min(32767, mixed[index] + value))
    with wave.open(str(output), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(array("h", mixed).tobytes())


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def main() -> None:
    if not FFMPEG.is_file():
        raise FileNotFoundError(FFMPEG)
    if not ICON.is_file():
        raise FileNotFoundError(ICON)

    with tempfile.TemporaryDirectory(prefix="magnetrail-ads-") as temporary:
        work = Path(temporary)
        cards: dict[str, Path] = {}
        for orientation, format_spec in FORMATS.items():
            card = work / f"brand_{orientation}.png"
            brand_card(format_spec["size"], card)
            cards[orientation] = card

        for concept_name, concept in CONCEPTS.items():
            source = concept["source"]
            if not source.is_file():
                raise FileNotFoundError(source)
            total_seconds = concept["game_seconds"] + BRAND_SECONDS
            audio = work / f"{concept_name}.wav"
            feedback_track(total_seconds, concept["events"], audio)

            for orientation, format_spec in FORMATS.items():
                output_dir = FINAL / orientation
                output_dir.mkdir(parents=True, exist_ok=True)
                output = output_dir / f"{concept_name}_{format_spec['suffix']}.mp4"
                game_filter = format_spec["filter"]
                filter_complex = (
                    f"[0:v]trim=start={concept['source_start']}:duration={concept['game_seconds']},"
                    f"setpts=PTS-STARTPTS,{game_filter},fps=30,format=yuv420p[game];"
                    f"[1:v]trim=duration={BRAND_SECONDS},setpts=PTS-STARTPTS,"
                    "fps=30,format=yuv420p[brand];"
                    "[game][brand]concat=n=2:v=1:a=0[outv]"
                )
                run([
                    str(FFMPEG), "-hide_banner", "-loglevel", "error", "-y",
                    "-i", str(source),
                    "-loop", "1", "-framerate", "30", "-i", str(cards[orientation]),
                    "-i", str(audio),
                    "-filter_complex", filter_complex,
                    "-map", "[outv]", "-map", "2:a:0",
                    "-t", f"{total_seconds:.3f}",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "17",
                    "-profile:v", "high", "-level:v", "4.1", "-g", "60",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
                    "-movflags", "+faststart",
                    str(output),
                ])
                print(output.relative_to(ADS_ROOT))


if __name__ == "__main__":
    main()
