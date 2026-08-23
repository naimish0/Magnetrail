# M5.1 closed-test calibration plan

## Purpose

Validate whether V2 difficulty ordering, quality warnings, and recovery-policy thresholds predict real player friction without personalizing the fixed campaign. The checked-in score is a hypothesis; telemetry and tester feedback calibrate a later versioned config.

## Cohort and duration

- Use the planned Google Play closed-testing cohort for at least 14 days and until at least 30 testers have attempted 20 or more campaign levels.
- Keep one campaign catalog/content version and one analyzer/config version throughout the measurement window.
- Exclude internal automation and reset-heavy developer accounts from behavioral aggregates.
- Review accessibility users separately where sample size permits; do not infer cognitive ability or health.

## Evidence to collect

For each stable level ID and content fingerprint, aggregate attempts, completion rate, first-attempt completion, restarts, immediate failed actions, hints used, actions above par, time-to-complete, abandon/return behavior, and the next level attempted. Pair this with optional tester ratings for clarity, enjoyment, perceived difficulty, visual readability, and whether a hard stretch had a fair recovery.

Do not collect raw touch coordinates, free-form personal data, or more identity data than the approved M5 analytics/privacy contract allows. Enforce the existing consent and deletion behavior.

## Calibration checks

1. Compare ordered V2 score/band against median attempts, completion time, hint rate, and abandon rate using rank correlation rather than assuming a linear relation.
2. Inspect outliers where observed friction differs by two or more bands, prioritizing tutorial levels and repeated tester comments.
3. Compare `fatalChoiceRatio`, critical constraints, and forcedness with restarts and immediate failures.
4. Compare congestion with readability feedback and accessibility-mode outcomes.
5. Review every `MANUAL_REVIEW`/`TUNE_METADATA` audit row; do not convert a warning into a board change from telemetry alone.
6. Validate recovery warnings after Hard-or-higher clusters using abandon/return behavior on the following two levels.
7. Check solver/counterfactual caps remain zero for unknown alternatives; raise bounds only in JVM tooling and only with benchmark evidence.

## Decision thresholds

- Go: no hard certification failure; no exact/symmetry duplicate; all changed boards manually reviewed; tutorial completion/friction has no severe unexplained outlier; crash/ANR and release gates remain green.
- Calibrate config: multiple levels show a consistent one-band mismatch while mechanics and quality are sound. Version the config/analyzer and regenerate comparison evidence.
- Tune metadata: a claimed tag/band lacks material mechanic evidence but the board is otherwise sound.
- Replace a board: only for a hard quality failure or repeated, high-confidence clarity/fairness evidence, followed by full certification and a new fingerprint.
- Hold rollout: any hard gate, progress regression, privacy mismatch, or severe tutorial blocker.

## Expansion gates

Do not add levels during M5.1. After the 100-level campaign is validated, consider staged catalogs of 150, then 200, then approximately 300 levels, with each expansion passing the same quality, symmetry, pacing, migration, and closed-test gates. Infinite Mode remains optional future work after retention and content-quality evidence; it is not an automatic next milestone.
