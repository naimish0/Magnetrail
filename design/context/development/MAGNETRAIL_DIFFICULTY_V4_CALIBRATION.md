# Magnetrail Difficulty V4 Human Calibration

Status: **Awaiting human calibration.**

- Human ratings supplied: NO
- Rated observations: 0
- Distinct rated levels: 0
- Stale-board ratings excluded: 40
- V4 calibrated: NO
- Evidence: EVIDENCE_INCONCLUSIVE_AWAITING_HUMAN_CALIBRATION
- Warnings: SAMPLE SIZE LIMITED; CALIBRATION PRELIMINARY; STALE BOARD RATINGS EXCLUDED

## Comparison

| Model | N | Pearson | Spearman | MAE (0–100) | Mean rank disagreement |
|---|---:|---:|---:|---:|---:|
| V3 | 0 | null | null | null | null |
| V4 | 0 | null | null | null | null |

Pearson/Spearman are null when fewer than three usable observations exist or either series has no variance. Ratings are mapped linearly from 1–8 to 0–100 only for comparison; this does not alter V4 weights.

## Metric-level correlations

No rated observations. Metric correlations are not measurable.

## Strongest model disagreements

No rated observations; overestimates and underestimates are not measurable.

## Workflow

1. Run `./gradlew analyzeCampaignDifficultyV4`.
2. Open `docs/development/MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json`.
3. Add a rater and 1–8 ratings to the selected reference/control entries; optional booleans may remain null.
4. Run `./gradlew calibrateDifficultyV4`.
5. Review this report. Do not automatically change production thresholds.

Automated analysis is not human validation. No campaign promotion is authorized by this report.
