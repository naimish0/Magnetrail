#!/usr/bin/env python3
from __future__ import annotations

from datetime import date
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent / "localized"
QA_ROOT = ROOT / "qa"
LOCALES = (
    "en",
    "hi-IN",
    "pt-BR",
    "id-ID",
    "es-MX",
    "tr-TR",
    "fil-PH",
    "th-TH",
    "de-DE",
    "ja-JP",
    "ko-KR",
    "fr-FR",
)
LEVEL_FILES = (
    "01-level-013.png",
    "02-level-075.png",
    "03-level-300.png",
    "04-level-045.png",
    "05-level-151.png",
    "06-level-202.png",
    "07-level-1034.png",
    "08-level-2020.png",
)
DEVICE_SPECS = (
    ("Phone", Path("screenshots"), (1080, 1920)),
    ("7-inch tablet", Path("screenshots/tablet-7-inch"), (1080, 1920)),
    ("10-inch tablet", Path("screenshots/tablet-10-inch"), (1440, 2560)),
)


def validate() -> tuple[list[str], int, int]:
    issues: list[str] = []
    checked = 0
    total_bytes = 0
    for locale in LOCALES:
        for device_name, relative_dir, dimensions in DEVICE_SPECS:
            directory = ROOT / locale / relative_dir
            actual_names = sorted(path.name for path in directory.glob("*.png"))
            if actual_names != list(LEVEL_FILES):
                issues.append(
                    f"{locale}/{relative_dir}: expected {list(LEVEL_FILES)}, found {actual_names}"
                )
            for filename in LEVEL_FILES:
                path = directory / filename
                if not path.is_file():
                    issues.append(f"Missing {path.relative_to(ROOT)}")
                    continue
                checked += 1
                total_bytes += path.stat().st_size
                try:
                    with Image.open(path) as image:
                        if image.format != "PNG":
                            issues.append(f"{path.relative_to(ROOT)}: format {image.format}, expected PNG")
                        if image.mode != "RGB":
                            issues.append(f"{path.relative_to(ROOT)}: mode {image.mode}, expected RGB")
                        if image.size != dimensions:
                            issues.append(
                                f"{path.relative_to(ROOT)}: dimensions {image.size}, expected {dimensions}"
                            )
                        width, height = image.size
                        if width * 16 != height * 9:
                            issues.append(f"{path.relative_to(ROOT)}: not exact 9:16 portrait")
                        if min(width, height) < 1080 or max(width, height) > 3840:
                            issues.append(
                                f"{path.relative_to(ROOT)}: dimensions outside Google Play bounds"
                            )
                        if max(width, height) > 2 * min(width, height):
                            issues.append(
                                f"{path.relative_to(ROOT)}: long dimension exceeds twice short dimension"
                            )
                except OSError as error:
                    issues.append(f"{path.relative_to(ROOT)}: unreadable PNG ({error})")
    return issues, checked, total_bytes


def create_contact_sheets() -> list[Path]:
    QA_ROOT.mkdir(parents=True, exist_ok=True)
    sheets: list[Path] = []
    thumb_size = (180, 320)
    label_width = 150
    header_height = 42
    sheet_size = (label_width + thumb_size[0] * len(LEVEL_FILES), header_height + thumb_size[1] * 3)

    for locale in LOCALES:
        sheet = Image.new("RGB", sheet_size, "#e8edf4")
        draw = ImageDraw.Draw(sheet)
        draw.text((12, 12), f"{locale} — eight real-game levels per device", fill="#172033")
        for row, (device_name, relative_dir, _) in enumerate(DEVICE_SPECS):
            y = header_height + row * thumb_size[1]
            draw.rectangle((0, y, label_width, y + thumb_size[1]), fill="#172033")
            draw.text((12, y + 14), device_name, fill="white")
            for column, filename in enumerate(LEVEL_FILES):
                with Image.open(ROOT / locale / relative_dir / filename) as source:
                    thumb = source.convert("RGB").resize(thumb_size, Image.Resampling.LANCZOS)
                sheet.paste(thumb, (label_width + column * thumb_size[0], y))
        output = QA_ROOT / f"{locale}-contact-sheet.png"
        sheet.save(output, optimize=True)
        sheets.append(output)

    master_thumb = (530, 334)
    master = Image.new("RGB", (master_thumb[0] * 3, master_thumb[1] * 4), "#111827")
    for index, sheet_path in enumerate(sheets):
        with Image.open(sheet_path) as sheet:
            preview = sheet.resize(master_thumb, Image.Resampling.LANCZOS)
        master.paste(preview, ((index % 3) * master_thumb[0], (index // 3) * master_thumb[1]))
    master_path = QA_ROOT / "ALL_LOCALES_CONTACT_SHEET.png"
    master.save(master_path, optimize=True)
    return sheets + [master_path]


def write_report(issues: list[str], checked: int, total_bytes: int, sheets: list[Path]) -> None:
    report_path = ROOT / "VALIDATION_REPORT.md"
    status = "PASS" if not issues and checked == 288 else "FAIL"
    lines = [
        "# Localized screenshot validation",
        "",
        f"Validated: {date.today().isoformat()}",
        "",
        f"Result: **{status}**",
        "",
        f"- Gameplay screenshots checked: {checked}/288",
        f"- Locales checked: {len(LOCALES)}/12",
        "- Device sets per locale: phone, 7-inch tablet, 10-inch tablet",
        "- Screenshots per device set: 8",
        "- Phone dimensions: 1080 × 1920",
        "- 7-inch tablet dimensions: 1080 × 1920",
        "- 10-inch tablet dimensions: 1440 × 2560",
        "- Aspect ratio: exact 9:16 portrait",
        "- Encoding: opaque 24-bit RGB PNG",
        f"- Total screenshot size: {total_bytes / (1024 * 1024):.2f} MiB",
        f"- QA contact sheets: {len(sheets)}",
        "",
        "Checks cover expected filenames, counts, PNG readability, RGB mode, dimensions, aspect ratio, and Google Play dimension bounds.",
        "",
        "## Issues",
        "",
    ]
    lines.extend(f"- {issue}" for issue in issues)
    if not issues:
        lines.append("- None.")
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    issues, checked, total_bytes = validate()
    sheets = create_contact_sheets() if checked == 288 else []
    write_report(issues, checked, total_bytes, sheets)
    print(f"{'PASS' if not issues and checked == 288 else 'FAIL'}: {checked}/288 screenshots")
    for issue in issues:
        print(f"- {issue}")
    return 0 if not issues and checked == 288 else 1


if __name__ == "__main__":
    raise SystemExit(main())
