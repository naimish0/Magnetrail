# Magnetrail remaining development status

Updated: 2026-08-20 (Asia/Kolkata)

This file tracks the sequential phases in `CODEX_MASTER_REMAINING_DEVELOPMENT_PROMPT.md`.
Phases 0 and 1 are frozen. The 40 D1 human ratings remain valid historical evidence for their
content-v6 board fingerprints and are deliberately excluded from content-v7 calibration. D2
Generator V5 replaced the production 200-board campaign under the existing stable IDs. A later
owner-directed append added Levels 201–205 at content version 8; Expert is explicitly owner-waived
and must not be described as structurally or human certified. Master remains excluded.

| Phase | Scope | Status | Frozen versions / gate evidence |
|---:|---|---|---|
| 0 | Difficulty v3 and Levels 1–150 remediation | COMPLETE — HUMAN PLAYTEST PENDING | Campaign content 5/generator 3; search `magnetrail-search-v3.0`; Difficulty `magnetrail-difficulty-v3.0`; Quality `magnetrail-puzzle-quality-v2.0`; preference schema 6. Owner-approved 150-board mapping promoted and automated-certified. |
| 1 | Campaign Levels 151–200 | COMPLETE — HUMAN PLAYTEST PENDING | Campaign content 6/generator 4; 200 total levels. The owner-approved 50-board mapping is promoted and automated-certified; Levels 1–150 are fingerprint-stable. |
| 2 | Insulator prototype | NOT STARTED | Strict phase isolation. |
| 3 | 8×8 usability prototype | NOT STARTED | Strict phase isolation. |
| 4 | Campaign Levels 201–250 | PARTIAL OWNER-DIRECTED APPEND | Levels 201–205 exist; 206–250 are not implemented. Level 205 has an explicit structural waiver. |
| 5 | Campaign Levels 251–300 | NOT STARTED | Strict phase isolation. |
| 6 | Infinite Mode | NOT STARTED | Strict phase isolation. |
| 7 | Adaptive Infinite Mode | NOT STARTED | Strict phase isolation. |
| 8 | Ads-only monetization expansion | NOT STARTED | Strict phase isolation. Product override recorded: no Billing, purchases, subscriptions, Remove Ads, or paid gates. No Phase 8 implementation exists. |

## Content-v8 non-Master append — 2026-08-20

Status: **PROMOTED WITH EXPLICIT EXPERT STRUCTURAL WAIVER; HUMAN PLAYTEST PENDING**.

The guarded append preserved the exact 200 content-v7 level definitions, added new stable IDs
`campaign-201` through `campaign-205`, and excluded Master. Easy, Medium, Hard, and Very Hard pass
the current V5 pipeline. Expert is the current deterministic V5.1 seed `11510013`; it is solver
complete, replay-valid, V4 complete/non-truncated, and duplicate-free, but remains rejected by five
structural gates. The manifest records `OWNER_WAIVED_UNCERTIFIED_EXPERT`, zero automated human
approvals, and no human playtest. DataStore content-v8 migration moves players who completed Level
200 to Level 201 without invalidating existing progress.

Evidence is under `docs/content/v5_1_append/promotion/`. Content-v7 source SHA-256 is
`8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`; content-v8 SHA-256 is
`6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`.

## D1 Difficulty V4 diagnostic — 2026-08-19

Status: **IMPLEMENTED — CONTENT-V6 HUMAN RATINGS ARCHIVED; CONTENT-V7 CALIBRATION PENDING**.

D1 adds an isolated, bounded, deterministic Difficulty V4 analysis and calibration workflow. It
does not change the production engine, solver semantics, Difficulty V3, Quality, Generator v4,
campaign JSON, UI, progress, or monetization. The initial calibration-0 campaign run completed for
200/200 levels with no truncation. Its scores are provisional diagnostics, not human-validated
difficulty labels.

Required evidence is under `docs/development/`:

- `MAGNETRAIL_DIFFICULTY_V4_SPEC.md`
- `MAGNETRAIL_DIFFICULTY_V4_AUDIT.json`
- `MAGNETRAIL_DIFFICULTY_V4_AUDIT.md`
- `MAGNETRAIL_DIFFICULTY_V4_LEVEL_DIAGNOSTICS.csv`
- `MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json`
- `MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md`

The project owner directly supplied ratings for all 40 selected content-v6 boards; no values were
inferred for optional observations. Each rating is now bound to its board fingerprint. Because D2
replaced those boards, all 40 are excluded from content-v7 correlation rather than being silently
transferred across stable IDs. No V4 weights or thresholds changed.

## D2 campaign regeneration and adaptive-difficulty prototype — 2026-08-19

Status: **PROMOTED — MIGRATION PROVEN; HUMAN CALIBRATION STILL PENDING**.

D2 adds Generator V5, explicit structural profiles, typed interaction graphs, object-removal
counterfactual relevance, persistent polarity/consequence analysis, and an offline deterministic
`DifficultySelectionV1` prototype. It does not change the production engine, Generator V4,
Difficulty V4, or app runtime selection. The project owner subsequently directed promotion of the
certified catalog without a pre-promotion playtest gate.

The isolated catalog contains 200/200 completely analyzed and production-engine-certified
candidates with the proposed distribution `Tutorial=12`, `Easy=35`, `Medium=45`, `Hard=55`,
`Expert=40`, `Master=13`. Compared with the current campaign, the staging pool changes the key
automated structural signals from safe-choice ratio `0.9861 → 0.8252`, meaningful-failure rate
`0.0139 → 0.1748`, greedy completion `0.9200 → 0.4283`, random-success completion
`0.9272 → 0.4742`, and permutation redundancy `0.9910 → 0.8432`. These results do not constitute
human approval or calibrated difficulty labels.

The guarded promotion preserved all 200 production IDs by campaign number, attached every verified
content-v6 predecessor fingerprint, archived the exact content-v6 source, and recertified all 200
production boards. DataStore migration preserves completion, stars, unlocks, selected level,
first-clear claims, currency, settings, ads, and Daily state; incomparable move/overload/hint minima
are archived by old board fingerprint. The blind rating sheet still contains no manufactured
ratings, and automated certification is not recorded as human approval.

Required D2 evidence is:

- `D2_CAMPAIGN_GENERATION_SPEC.md`
- `D2_CAMPAIGN_GENERATION_AUDIT.json`
- `D2_CAMPAIGN_GENERATION_AUDIT.md`
- `D2_LEVEL_DIAGNOSTICS.csv`
- `D2_OBJECT_RELEVANCE.csv`
- `D2_INTERACTION_GRAPH.csv`
- `D2_CALIBRATION.json`
- `D2_CALIBRATION.md`
- `D2_PROMOTION_MANIFEST.json`
- `docs/content/d2/staging/D2_CAMPAIGN_V5_CANDIDATES.json`
- `docs/content/d2/staging/D2_HUMAN_REVIEW_CATALOG.json`
- `docs/content/d2/promotion/D2_SOURCE_CONTENT_V6.json`
- `docs/content/d2/promotion/D2_ID_MIGRATION.json`
- `docs/content/d2/promotion/D2_PROMOTION_RESULT.json`
- `docs/content/d2/promotion/D2_PROMOTION_RESULT.md`

## Phase 0 completion gate

The approved batch followed the required safety sequence: 450 candidates were generated and
analyzed, diagnostics and the 150-row `TUNE=66` / `REPLACE=84` proposal were published without
changing production content, and promotion occurred only after explicit project-owner approval.
The pre-promotion artifacts remain checked in as historical evidence.

Final recomputation against the promoted catalog reports:

- 150/150 production certifications accepted with deterministic solution replay and regenerated
  metadata equal to the shipped metadata;
- 150/150 complete, confidence-1.0 Difficulty v3 searches and campaign-position gates accepted;
- 150 exact and 150 symmetry fingerprints, with no hard duplicates;
- zero canonical guess-dependent choices;
- Quality `ACCEPT=81`, `REVIEW=69`, `REJECT=0`;
- rising progression medians through Level 100, no upper-campaign reset, and all range/percentile
  gates accepted;
- 150/150 stable IDs and v4→v5 predecessor fingerprints verified against the immutable source
  snapshot and owner-approved mapping;
- player preference schema 6 preserves earned progress while archiving incomparable v4 board
  records by fingerprint;
- automated approvals recorded as human approvals: 0;
- owner-approved assignments: 150/150; human-playtested assignments: 0/150 (`PENDING`).

The final evidence is:

- `PHASE0_FINAL_DIAGNOSTICS.json`
- `PHASE0_FINAL_CERTIFICATION.md`
- `PHASE0_FINAL_DISTRIBUTION_REPORT.md`
- `PHASE0_FINAL_HUMAN_REVIEW_CHECKLIST.md`
- `PHASE0_CONTENT_MIGRATION.json`
- `PHASE0_MIGRATION_REPORT.md`

## Safety and stop-condition audit

- No production engine ambiguity or solver/gameplay inconsistency was observed. The legacy
  production certification pipeline and Difficulty v3 analyzer both replay the promoted boards.
- The v4 source catalog is preserved exactly; every changed level embeds and reports its verified
  predecessor fingerprint. Stable IDs and earned player value are retained.
- No new mechanic or frozen-rule change was introduced.
- Difficulty is reported from complete searches; truncated analysis is never accepted.
- No future-phase dependency, production class, migration, UI, configuration, or feature was
  introduced.
- Human experience remains a separate gate. Automated certification and owner approval do not
  claim playtesting, and the Human Review Priority queue remains open.

## Verification

Focused production/content verification completed during promotion:

```text
./gradlew :game-core:test :level-tools:test
./gradlew :app:testDebugUnitTest
./gradlew finalizePhase0
```

`finalizePhase0` reports 150/150 production-certified levels, Quality
`{ACCEPT=81, REVIEW=69}`, zero guess-dependent canonical choices, and human playtesting
`PENDING`.

The full post-promotion regression passed:

```text
./gradlew :game-core:test :level-tools:test analyzeCampaignDifficulty \
  analyzeCampaignQuality checkCampaignSymmetryDuplicates auditCampaignPacing \
  certifyCampaignContent finalizePhase0 :app:testDebugUnitTest \
  :app:testReleaseUnitTest lintDebug lintRelease :app:assembleDebug --continue
```

Result: `BUILD SUCCESSFUL` in 44 seconds; 104 actionable tasks, 20 executed and 84 up-to-date.
Android AAR metadata checks, debug/release unit tests, both lint variants, content certification,
and debug APK assembly all passed.

## Phase 1 pre-promotion gate — 2026-08-19 (historical evidence)

Phase 1 staging follows the mandatory batch-safety boundary:

- the production campaign remained exactly 150 levels at content 5/generator 3 while the proposal was staged;
- a deterministic 200-candidate pool was generated using only existing pre-Insulator mechanics;
- every candidate uses a 6×6 or 7×7 board, passes its complete Difficulty v3 target gate, has
  zero guess-dependent canonical choices, and is unique under exact/symmetry fingerprints;
- a diversity-first selector proposed stable IDs `campaign-151` through `campaign-200` while
  preserving all 150 existing IDs and fingerprints;
- the proposed full 200-level catalog has 200 exact and 200 symmetry-unique fingerprints;
- all 50 additions are production-certifiable and target-gated, with no Quality `REJECT`;
- selected-to-selected structural near neighbors are zero; two rows are structurally near an
  existing level and remain explicitly flagged;
- the proposed split is 40 generator-assisted selections plus 10 generator-assisted heavy-tuning
  review slots; no generated board is mislabeled as handcrafted;
- final selected Quality is `ACCEPT=3`, `REVIEW=47`, `REJECT=0`; the 47 review rows exceed the
  conservative unused-space threshold even after a bounded production-engine pass added only
  counterfactually relevant walls. No threshold was weakened. Proposed decisions are `KEEP=3`,
  `TUNE=47`, `REPLACE=0`, all pending owner/player judgment;
- automated approvals: 0; owner approvals: 0; human playtests: 0.

Required review artifacts are `M5_3_PROPOSED_PROMOTION_MANIFEST.md`,
`M5_3_PROPOSED_DISTRIBUTION.md`, `M5_3_PROPOSED_DUPLICATE_REPORT.md`,
`M5_3_PROPOSED_PACING_REPORT.md`, `M5_3_PROPOSED_MANUAL_REVIEW.md`, and
`M5_3_PROPOSED_MIGRATION.md` under `docs/content/`.

Promotion was blocked until the project owner explicitly approved this >20-level batch. That
approval was subsequently received and is recorded separately from human playtesting. Phase 2
remains unimplemented.

Phase 1 staging verification passed:

```text
./gradlew :game-core:test :level-tools:test
./gradlew :game-core:test :level-tools:test analyzeCampaignDifficulty \
  analyzeCampaignQuality checkCampaignSymmetryDuplicates auditCampaignPacing \
  certifyCampaignContent finalizePhase0 :app:testDebugUnitTest \
  :app:testReleaseUnitTest lintDebug lintRelease :app:assembleDebug --continue
```

Full regression result: `BUILD SUCCESSFUL` in 45 seconds; 104 actionable tasks, 27 executed and
77 up-to-date. Re-running `publishPhase1Proposal` reproduced byte-identical candidate-pool,
manifest, and proposed-campaign SHA-256 hashes.

## Phase 1 completion gate — 2026-08-19

After explicit project-owner approval, the displayed 50-row proposal was promoted without
modifying any existing board:

- the checked-in campaign now has 200 stable IDs at content 6/generator 4;
- all 150 pre-existing IDs, campaign numbers, and exact board fingerprints match the immutable
  content-5 source snapshot;
- `campaign-151` through `campaign-200` are the only added IDs;
- all 50 additions reproduce identical production metadata and designed solutions when
  recertified with the gameplay engine and solver;
- Difficulty v3 search is complete and confidence-certified for 200/200 campaign boards;
- all 50 Phase 1 target gates pass, including the Level 200 finale gate;
- the complete campaign has 200 exact and 200 symmetry-unique fingerprints;
- canonical guess-dependent choices are zero across all 200 levels;
- Phase 1 Quality is `ACCEPT=3`, `REVIEW=47`, `REJECT=0`; review rows remain in the Human Review
  Priority queue rather than being mislabeled as human-approved player experience;
- two additions retain owner-accepted structural-comparison flags (`campaign-188` near
  `campaign-101`, and `campaign-196` near `campaign-073`); no two additions are structurally near;
- preference schema 6 migrates completed Level 150 to unlocked/selected Level 151 exactly once,
  leaves incomplete Level 150 locked, and clamps completion at Level 200;
- completion, records, first-clear rewards, currency, Daily state, settings, consent, and ad state
  are preserved;
- automated approvals: 0; owner-approved assignments: 50/50; human-playtested assignments: 0/50
  (`PENDING`).

Final Phase 1 evidence is under `docs/content/`:

- `M5_3_FINAL_DIAGNOSTICS.json`
- `M5_3_CAMPAIGN_151_200_REPORT.md`
- `M5_3_DUPLICATE_REPORT.md`
- `M5_3_PACING_REPORT.md`
- `M5_3_MANUAL_REVIEW.md`
- `M5_3_MIGRATION.md`
- `M5_3_FULL_200_REPORT.md`
- `M5_3_CONTENT_MIGRATION.json`

Phase 1 changes contain no Insulator mechanic, 8×8 board support, Infinite Mode, adaptive system,
or monetization expansion. Phase 2 is `NOT STARTED` under strict phase isolation.

Final post-promotion verification command:

```text
./gradlew :game-core:test :level-tools:test analyzeCampaignDifficulty \
  analyzeCampaignQuality checkCampaignSymmetryDuplicates auditCampaignPacing \
  certifyCampaignContent finalizePhase0 finalizePhase1 :app:testDebugUnitTest \
  :app:testReleaseUnitTest :app:compileDebugAndroidTestKotlin lintDebug lintRelease \
  :app:assembleDebug --continue
```

Result on the final tree: `BUILD SUCCESSFUL` in 12 seconds; 113 actionable tasks, 19 executed and
94 up-to-date.
The run analyzed and audited all 200 levels, production-certified 200 campaign levels and seven
Daily fallbacks, passed debug/release tests and lint, compiled Android UI tests, passed Android 37
AAR metadata checks, and assembled the debug APK without contacting an ad network.
