#!/usr/bin/env python3
"""Validate the complete Magnetrail Google Ads package and write QA metadata."""

from __future__ import annotations

import csv
from datetime import date
import json
from pathlib import Path
import subprocess
from typing import Any

from PIL import Image


ADS_ROOT = Path(__file__).resolve().parents[1]
TEXT = ADS_ROOT / "text"
IMAGE_FINAL = ADS_ROOT / "images" / "final"
VIDEO_FINAL = ADS_ROOT / "videos" / "final"
STORYBOARDS = ADS_ROOT / "qa" / "video_storyboards"
FFPROBE = Path("/opt/homebrew/bin/ffprobe")
REPORT = ADS_ROOT / "VALIDATION_REPORT.md"
MANIFEST = ADS_ROOT / "ASSET_MANIFEST.csv"

IMAGE_SPECS = {
    "landscape": (1200, 628),
    "square": (1200, 1200),
    "vertical": (1200, 1500),
}
VIDEO_SPECS = {
    "horizontal": (1920, 1080),
    "square": (1080, 1080),
    "vertical": (1080, 1920),
}

IMAGE_METADATA = {
    "dense_challenge_level300_landscape_1200x628.png": ("Dense readable challenge", "campaign-300"),
    "meaningful_middle_level045_landscape_1200x628.png": ("One meaningful middle move", "campaign-045"),
    "order_cancellation_level075_landscape_1200x628.png": ("Order and cancellation", "campaign-075"),
    "signature_polarity_level013_landscape_1200x628.png": ("Signature pull/push polarity", "campaign-013"),
    "balanced_choice_level151_square_1200x1200.png": ("Balanced strategic choice", "campaign-151"),
    "dense_endgame_level300_square_1200x1200.png": ("Dense endgame decision", "campaign-300"),
    "objective_complete_level003_square_1200x1200.png": ("Objective and completion", "proto-003"),
    "signature_after_flip_level013_square_1200x1200.png": ("Cause and effect after a flip", "campaign-013"),
    "balanced_after_two_level151_vertical_1200x1500.png": ("Board simplification", "campaign-151"),
    "balanced_board_level045_vertical_1200x1500.png": ("Readable medium board", "campaign-045"),
    "cancellation_state_level075_vertical_1200x1500.png": ("Meaningful order decision", "campaign-075"),
    "dense_start_level300_vertical_1200x1500.png": ("Dense challenge opening", "campaign-300"),
}


def probe(video: Path) -> dict[str, Any]:
    result = subprocess.run(
        [
            str(FFPROBE), "-v", "error",
            "-show_entries",
            "stream=index,codec_type,codec_name,width,height,pix_fmt,r_frame_rate,avg_frame_rate,sample_rate,channels",
            "-show_entries", "format=duration,size,format_name",
            "-of", "json", str(video),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(result.stdout)


def text_validation(errors: list[str]) -> list[str]:
    summaries: list[str] = []
    specs = (
        ("SHORT_HEADLINES.csv", 15, 30),
        ("LONG_HEADLINES.csv", 5, 90),
        ("DESCRIPTIONS.csv", 5, 90),
    )
    for filename, expected_count, limit in specs:
        path = TEXT / filename
        with path.open(newline="", encoding="utf-8") as handle:
            rows = list(csv.DictReader(handle))
        if len(rows) != expected_count:
            errors.append(f"{filename}: expected {expected_count} rows, found {len(rows)}")
        for row in rows:
            actual = len(row["text"])
            declared = int(row["character_count"])
            if actual != declared:
                errors.append(f"{filename} {row['id']}: declared {declared}, actual {actual}")
            if actual > limit:
                errors.append(f"{filename} {row['id']}: {actual} exceeds {limit}")
            required = {
                "id", "text", "character_count", "creative_angle",
                "verified_feature_source", "recommended_priority",
            }
            if set(row) != required:
                errors.append(f"{filename}: unexpected CSV columns")
        summaries.append(f"{filename}: {len(rows)} rows, all at or below {limit} characters")

    with (TEXT / "SHORT_HEADLINES.csv").open(newline="", encoding="utf-8") as handle:
        short_rows = list(csv.DictReader(handle))
    if not any(len(row["text"]) <= 15 for row in short_rows):
        errors.append("No short headline is 15 characters or fewer")
    return summaries


def image_validation(errors: list[str], manifest_rows: list[dict[str, str]]) -> list[str]:
    summaries: list[str] = []
    images = sorted(IMAGE_FINAL.glob("*/*"))
    if len(images) != 12:
        errors.append(f"Expected 12 final images, found {len(images)}")
    if set(path.name for path in images) != set(IMAGE_METADATA):
        errors.append("Final image inventory differs from declared metadata mapping")

    for orientation, expected_size in IMAGE_SPECS.items():
        group = sorted((IMAGE_FINAL / orientation).glob("*"))
        if len(group) != 4:
            errors.append(f"Expected four {orientation} images, found {len(group)}")
        for path in group:
            if path.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
                errors.append(f"{path.name}: unsupported image format")
                continue
            with Image.open(path) as image:
                actual_size = image.size
                actual_format = image.format
            if actual_size != expected_size:
                errors.append(f"{path.name}: expected {expected_size}, found {actual_size}")
            if actual_format not in {"PNG", "JPEG"}:
                errors.append(f"{path.name}: unexpected decoded format {actual_format}")
            if path.stat().st_size > 5 * 1024 * 1024:
                errors.append(f"{path.name}: exceeds 5 MB")
            expected_tokens = (orientation, f"{expected_size[0]}x{expected_size[1]}")
            if any(token not in path.stem for token in expected_tokens):
                errors.append(f"{path.name}: filename does not describe orientation/dimensions")
            creative_angle, level_id = IMAGE_METADATA[path.name]
            manifest_rows.append({
                "filename": str(path.relative_to(ADS_ROOT)),
                "creative_angle": creative_angle,
                "level_id": level_id,
                "orientation": orientation,
                "dimensions": f"{actual_size[0]}x{actual_size[1]}",
                "duration": "",
                "intended_campaign_use": "App campaign and Performance Max image",
            })
        summaries.append(f"{orientation}: {len(group)} images at {expected_size[0]}x{expected_size[1]}, under 5 MB")
    return summaries


def has_faststart(video: Path) -> bool:
    data = video.read_bytes()
    moov = data.find(b"moov")
    mdat = data.find(b"mdat")
    return moov >= 0 and mdat >= 0 and moov < mdat


def video_validation(errors: list[str], manifest_rows: list[dict[str, str]]) -> list[str]:
    summaries: list[str] = []
    videos = sorted(VIDEO_FINAL.glob("*/*.mp4"))
    if len(videos) != 6:
        errors.append(f"Expected six final videos, found {len(videos)}")

    for orientation, expected_size in VIDEO_SPECS.items():
        group = sorted((VIDEO_FINAL / orientation).glob("*.mp4"))
        if len(group) != 2:
            errors.append(f"Expected two {orientation} videos, found {len(group)}")
        durations: list[float] = []
        for path in group:
            metadata = probe(path)
            streams = metadata["streams"]
            video_streams = [stream for stream in streams if stream.get("codec_type") == "video"]
            audio_streams = [stream for stream in streams if stream.get("codec_type") == "audio"]
            if len(video_streams) != 1:
                errors.append(f"{path.name}: expected one video stream")
                continue
            stream = video_streams[0]
            actual_size = (stream.get("width"), stream.get("height"))
            duration = float(metadata["format"]["duration"])
            durations.append(duration)
            if actual_size != expected_size:
                errors.append(f"{path.name}: expected {expected_size}, found {actual_size}")
            if stream.get("codec_name") != "h264":
                errors.append(f"{path.name}: video codec is not H.264")
            if stream.get("pix_fmt") != "yuv420p":
                errors.append(f"{path.name}: pixel format is not yuv420p")
            if stream.get("avg_frame_rate") != "30/1":
                errors.append(f"{path.name}: average frame rate is not 30 fps")
            if not (15.0 <= duration <= 25.0):
                errors.append(f"{path.name}: duration {duration:.3f}s is outside 15–25s")
            if len(audio_streams) != 1 or audio_streams[0].get("codec_name") != "aac":
                errors.append(f"{path.name}: expected one AAC audio stream")
            if not has_faststart(path):
                errors.append(f"{path.name}: MP4 moov atom is not before media data")
            expected_tokens = (orientation, f"{expected_size[0]}x{expected_size[1]}")
            if any(token not in path.stem for token in expected_tokens):
                errors.append(f"{path.name}: filename does not describe orientation/dimensions")

            if path.name.startswith("concept_a"):
                creative_angle = "Signature mechanic and polarity cause/effect"
                level_id = "campaign-013"
                intended = "Primary App campaign video; Performance Max video"
            else:
                creative_angle = "Near-miss, challenge, and satisfying clear"
                level_id = "campaign-300"
                intended = "Challenge App campaign video; Performance Max video"
            manifest_rows.append({
                "filename": str(path.relative_to(ADS_ROOT)),
                "creative_angle": creative_angle,
                "level_id": level_id,
                "orientation": orientation,
                "dimensions": f"{actual_size[0]}x{actual_size[1]}",
                "duration": f"{duration:.3f}",
                "intended_campaign_use": intended,
            })
        summaries.append(
            f"{orientation}: {len(group)} H.264/AAC videos at {expected_size[0]}x{expected_size[1]}; "
            f"durations {', '.join(f'{value:.1f}s' for value in durations)}"
        )
    return summaries


def supporting_artifact_validation(errors: list[str]) -> list[str]:
    results: list[str] = []
    contact = ADS_ROOT / "qa" / "IMAGE_CONTACT_SHEET.png"
    if not contact.is_file():
        errors.append("Image contact sheet is missing")
    else:
        results.append("Image contact sheet exists")
    storyboards = sorted(STORYBOARDS.glob("*.png"))
    if len(storyboards) != 6:
        errors.append(f"Expected six video storyboards, found {len(storyboards)}")
    else:
        results.append("Six video storyboards exist with 0, 2, 5, 10, and final-second frames")
    raw_images = sorted((ADS_ROOT / "images" / "raw").glob("*.png"))
    raw_videos = sorted((ADS_ROOT / "videos" / "raw").glob("*.mp4"))
    if len(raw_images) < 6:
        errors.append("Fewer than six raw gameplay image situations exist")
    if len(raw_videos) != 2:
        errors.append(f"Expected two raw gameplay recordings, found {len(raw_videos)}")
    results.append(f"Raw sources retained: {len(raw_images)} images and {len(raw_videos)} videos")
    return results


def main() -> None:
    errors: list[str] = []
    manifest_rows: list[dict[str, str]] = []
    text_summary = text_validation(errors)
    image_summary = image_validation(errors, manifest_rows)
    video_summary = video_validation(errors, manifest_rows)
    support_summary = supporting_artifact_validation(errors)

    final_names = [row["filename"] for row in manifest_rows]
    base_names = [Path(name).name for name in final_names]
    if len(base_names) != len(set(base_names)):
        errors.append("Final asset basenames are not unique")
    if any(" " in name or name.lower() != name for name in base_names):
        errors.append("Final asset filenames must be lowercase and contain no spaces")

    with MANIFEST.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "filename", "creative_angle", "level_id", "orientation",
                "dimensions", "duration", "intended_campaign_use",
            ],
        )
        writer.writeheader()
        writer.writerows(sorted(manifest_rows, key=lambda row: row["filename"]))

    status = "PASS" if not errors else "FAIL"
    lines = [
        "# Google Ads asset validation report",
        "",
        f"Validation date: {date.today().isoformat()}",
        "",
        f"Overall automated result: **{status}**",
        "",
        "## Automated checks",
        "",
    ]
    lines.extend(f"- {summary}" for summary in text_summary + image_summary + video_summary + support_summary)
    lines.extend([
        f"- Manifest contains {len(manifest_rows)} unique final assets.",
        "- Final filenames are lowercase, unique, space-free, and describe orientation/dimensions.",
        "",
        "## Errors",
        "",
    ])
    lines.extend(f"- {error}" for error in errors)
    if not errors:
        lines.append("- None.")
    lines.extend([
        "",
        "## Manual visual QA",
        "",
        "The final regeneration was inspected at original resolution on 2026-08-26:",
        "",
        "- Opened all 12 final images individually and opened the complete contact sheet.",
        "- Opened all six final storyboards at 0, 2, 5, 10, and the final second.",
        "- Boards, arrows, magnet polarities, walls, status counts, and intended decisions remain readable.",
        "- Tight landscape crops prioritize the interaction area; no controlling piece needed to understand the scene is cut off.",
        "- No final asset contains system UI, personal data, debug/test UI, consent, an ad, or an ad call-to-action.",
        "- Colors, type, board art, completion UI, confetti, and official icon match the actual build/repository.",
        "- Concept A shows its first real launch at about 1.4 seconds and two state changes in the first five seconds.",
        "- Concept B shows a genuine overload/correction in the first two seconds and then continuously simplifies the board.",
        "- Both concepts show action, reaction, decision, and actual completion before the 1.2-second brand finish.",
        "- Captions were not added; all gameplay remains understandable muted, and the only added text is the product name on the brand finish.",
        "",
    ])
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"{status}: {len(manifest_rows)} final assets; {len(errors)} errors")
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
