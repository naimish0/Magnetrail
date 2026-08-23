# Generator V6 blinded human calibration protocol

Protocol version: `generator-v6-human-protocol-five-band-2`

## Evidence boundary

Only observations exported by a debug build loading the exact technically certified V6
calibration or sealed-validation catalog are admissible. Campaign V10/V11 observations and
automated policy results are controls only and cannot certify `HumanLikeDifficultyV1`. Never edit
an exported board fingerprint, assigned bucket, presentation order, or result.

## Study sequence

1. Generate both frozen pilots with `./gradlew :level-tools:generateGeneratorV6Pilot` and verify
   both audit statuses are `V6_TECHNICALLY_CERTIFIED`.
2. Record the catalog SHA-256 values and install a debug build. The debug loader selects V6 only
   when the complete 30-board catalog is present; release builds contain neither pilot. Build the
   calibration app with `./gradlew :app:assembleDebug`. Only after the model is frozen, build the
   isolated sealed app with
   `./gradlew :app:assembleDebug -Pv6PlaytestCatalog=sealed-validation --rerun-tasks`.
3. Give each participant a new anonymous 2–24 character code. Do not disclose bucket, family,
   seed, expected band, solver data, or authored solution.
4. Participants play the deterministic stratified order. After completion or abandonment they
   report five-point perceived difficulty (Easy through Expert), 1–5 fairness/predictability, whether guessing was
   required, whether the strategy felt repeated, and an optional comment.
5. Export each CSV without opening or aggregating the sealed-validation results. Preserve hinted
   sessions in the audit; the primary completion/time analysis excludes them.
6. Collect at least ten independent participants and five ratings per board, with no participant
   contributing more than 20% of observations.
7. Fit with `./gradlew :level-tools:calibrateHumanLikeDifficultyV1`. Freeze and hash the feature
   schema, calibration catalog, dataset, coefficients, thresholds, and model card before opening
   the sealed results.
8. Collect sealed-validation observations from new participants where possible, then run
   `./gradlew :level-tools:validateGeneratorV6HumanCertificate`. A failed gate requires a new
   versioned generator/model and a new sealed catalog; do not retune this frozen version.

Master is intentionally absent from the V6 UI, exports, model, and certification gates. Historical
V10/V11 Master observations are controls only and must not be merged into either V6 dataset.

## Calibration APK handoff

- Install `app/build/outputs/apk/debug/app-debug.apk` (SHA-256
  `2beb6295c9f431c64c5f3af5c4d76fe6c21a637a52f3aed3e04c49231e8fbcf5`).
- The embedded calibration catalog SHA-256 is
  `acc1742e921d73a7661756259239757052b95499161b3927ce8f9e9236f2cd17`.
- On the home screen, tap **Human Playtest**, enter a new anonymous participant code, tap
  **Create blind session**, then **Begin first board**. A V6 session contains all 30 calibration
  boards: six from each of Easy, Medium, Hard, Super Hard, and Expert.
- Export the CSV before starting another participant. Do not uninstall the app or clear its data
  while a participant session has unexported observations.
- Do not distribute the sealed-validation build until the calibration model and thresholds are
  frozen.

## Data integrity and exclusions

- Duplicate participant/board rows, unmatched fingerprints, unknown boards, cross-catalog board
  or semantic-cluster overlap, and participant overlap are fail-closed errors.
- Participants may stop at any time. Abandonment is recorded, not imputed as completion.
- Hints, restarts, failed actions, successful actions from abandoned/restarted attempts, deadlocks,
  and active elapsed time are recorded automatically.
- Optional comments must not contain names, email addresses, or other identifying information.
- The ordinal model is a human-calibrated predictor with confidence and OOD rejection. It is not a
  claim that an automated analyzer thinks like a person.
