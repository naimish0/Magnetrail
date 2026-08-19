# Codex Prompt — Magnetrail M5.1 Level Quality Calibration

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0–M5 are complete and the 100-level campaign is release-candidate ready.

---

You are implementing **Magnetrail M5.1: Level Quality and Difficulty Calibration**, a narrowly scoped pre–closed-testing milestone.

The existing implementation already contains the frozen deterministic game engine, solver, generator/certification tooling, 100+ campaign levels, Daily Challenge, progression, persistence, accessibility, monetization, release configuration, and tests. Preserve them.

This milestone incorporates only the useful parts of the supplemental “Arrow + Magnet: Level System, Solver, Difficulty & Auto Progression” proposal:

- Separate objective difficulty from level quality.
- Improve Magnetrail-specific difficulty metrics.
- Detect duplicates under rotation and reflection.
- Audit the fixed 100-level campaign for mechanic teaching and difficulty waves.
- Add recovery levels after demanding sequences.
- Strengthen golden content tests and reports.
- Preserve extension points for a later 200–300 level catalog and optional Infinite Mode.

Do **not** rebuild the engine/solver/generator or implement adaptive campaign ordering, Infinite Mode, 200 additional levels, field radius/strength, attraction-only magnets, moving magnets, realistic physics, or arrow animation states in `:game-core`.

## Inspect and baseline first

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect the current Git status/diff and preserve unrelated user changes.
3. Read all files under `docs/`, especially:
   - Frozen Magnetrail rules contract
   - M3 content report and generation/certification design
   - M5 release and QA reports
   - Campaign catalog/level schemas
   - Milestone completion notes
4. Inspect the production engine, solver, state key, generator, certification pipeline, difficulty metrics/classifier, content fingerprint, campaign ordering, DataStore migration, and content tests.
5. Run the complete existing baseline: core tests, level tools, campaign certification, app tests, lint, and relevant build.
6. Report the exact existing 100-level count, difficulty distribution, duplicate strategy, stable ID/order contract, and whether any released/external player progress exists.
7. Summarize an incremental plan before editing.

Do not silently change frozen rules. Do not replace working APIs just to match names in this prompt.

## Architectural invariants

- `:game-core` remains pure Kotlin/JVM and the only rule authority.
- Difficulty and quality analysis use the production engine/solver; they never duplicate movement or magnet rules.
- Campaign order remains a checked-in deterministic catalog, never personalized at runtime.
- Level IDs remain stable.
- All 100 shipped levels remain solver-certified.
- Any changed/replacement board receives a new content fingerprint and passes every certification gate.
- Normal builds never generate or rewrite campaign content.
- Metrics are deterministic for the same content version, analyzer version, and configuration.

## 1. Magnetrail-specific difficulty metrics

Create or extend a versioned `DifficultyMetrics` model. Retain useful existing metrics and add the following where they are not already present:

- `cleanSolutionLength`
- `successfulOpeningActions`
- `plausibleOpeningActions`
- `averageSuccessfulBranching`
- `maximumSuccessfulBranching`
- `forcedMoveRatio`
- `fatalChoiceRatio`
- `criticalOrderConstraintCount`
- `solutionDivergenceDepth`
- `magnetControlledSolutionActions`
- `pullSolutionActions`
- `pushSolutionActions`
- `polarityFlipCount`
- `controllingMagnetChangeCount`
- `occlusionDependencyCount`
- `cancellationDependencyCount`
- `wallDependencyCount`
- `solverStatesExplored`
- `solutionCountUpToCap`
- `boardDensity`
- `visualCongestionScore`

Use precise documented definitions. Do not expose a metric whose value cannot be explained or tested.

### Bounded analysis

Some metrics require counterfactual solving. Keep analysis deterministic and bounded:

- Analyze every state along one certified clean solution.
- At each such state, classify remaining arrow actions through the production engine.
- For successful alternatives, use the production solver with explicit state/solution caps to determine whether the resulting state remains solvable.
- Record capped/unknown outcomes separately; do not silently classify a timeout as fatal.
- Reuse memoized solver results keyed by the complete core state key.
- Never run unbounded exhaustive analysis on-device.
- Campaign analysis belongs in JVM tooling/build-time certification. Runtime UI consumes checked-in metadata.

### Metric definitions

Use these starting definitions or clearly document a more accurate equivalent:

- `forcedMoveRatio`: fraction of certified-solution states having exactly one successful action that preserves solvability.
- `fatalChoiceRatio`: solvability-proven fatal successful alternatives divided by all analyzed successful alternatives excluding the certified choice.
- `criticalOrderConstraintCount`: count of certified actions for which at least one currently successful alternative makes the state unsolvable or forces a materially different dependency chain.
- `solutionDivergenceDepth`: earliest action index at which capped alternative clean solutions meaningfully diverge; normalize so earlier/high-impact divergence increases difficulty.
- `occlusionDependencyCount`: certified actions whose controlling influence/result depends on an arrow, wall, or magnet blocking another aligned magnet, obtained from engine/core-owned trace metadata rather than UI geometry.
- `cancellationDependencyCount`: certified actions using equal-nearest cancellation in their resolution.
- `visualCongestionScore`: deterministic density/readability heuristic based on occupied cells, adjacent entities, and field overlap metadata; it is not a gameplay rule.

If the current engine result lacks safe rule-owned metadata for occlusion or cancellation, add a pure diagnostic explanation/query inside `:game-core` that reuses the production rule resolver. Do not make the analyzer or UI reconstruct rules.

## 2. Difficulty score v2

Implement a configurable, versioned `DifficultyConfig` and `DifficultyScoreV2` normalized to `0..100`.

Use this initial weighting model:

| Component | Weight |
|---|---:|
| Wrong-order and dead-end risk | 25% |
| Branching complexity | 20% |
| Critical order constraints | 20% |
| Magnetic and polarity complexity | 20% |
| Look-ahead/divergence depth | 10% |
| Board density/readability | 5% |

Requirements:

- Weights and normalization curves live in one serializable/configurable object.
- Validate that weights sum to 100%.
- Use clamped, non-linear normalization curves where appropriate.
- `cleanSolutionLength` may contribute inside order/look-ahead context but must not independently dominate difficulty because every clean completion normally removes each arrow once.
- Solver explored-state count is supporting evidence, not a standalone definition of difficulty.
- Record raw metrics, normalized components, weights, final score, band, analyzer version, and any capped/unknown flags.
- Preserve old score metadata long enough to emit a v1→v2 comparison report; do not overwrite evidence before comparison.

Use internal bands initially:

- `0–15`: Tutorial
- `16–30`: Easy
- `31–45`: Normal
- `46–60`: Medium
- `61–75`: Hard
- `76–90`: Very Hard
- `91–100`: Expert

Bands are internal and configurable. Do not show “IQ,” intelligence, or cognitive-health claims in UI.

## 3. Separate quality score

Create a distinct, versioned `LevelQualityMetrics` and `LevelQualityScore`. Difficulty must not substitute for quality.

Quality gates/metrics should cover:

- Solvability and certified solution replay
- Structural/schema validity
- Stable unique ID
- Exact and symmetry-normalized uniqueness
- Non-triviality
- Mechanic relevance: claimed mechanic tags materially affect at least one certified or meaningful counterfactual action
- Meaningful decision count
- Excessive forcedness
- Excessive opening ambiguity for tutorial/early levels
- Repeated near-identical structure within a local campaign window
- Visual density/readability bounds
- Solver/hint performance bounds
- Grading metadata consistency
- Appropriate difficulty for assigned curriculum position

Produce:

```text
qualityScore: 0..100
qualityStatus: ACCEPT | REVIEW | REJECT
qualityReasons: stable reason codes
```

Hard failures such as unsolvable content, duplicate ID, invalid schema, failed solution replay, exact/symmetry duplicate, or content-hash mismatch must be `REJECT` regardless of numeric score.

Do not require a unique solution universally. Multiple solutions may be good when choices remain meaningful. Record capped solution count and use curriculum-specific limits.

## 4. Symmetry-aware duplicate detection

Extend the canonical content fingerprint to detect equivalent boards under valid geometric symmetries.

For square boards, evaluate the dihedral transforms:

- Identity
- Rotations 90°, 180°, 270°
- Horizontal reflection
- Vertical reflection
- Main-diagonal reflection
- Anti-diagonal reflection

Transform every position and cardinal direction correctly. Preserve entity type, wall geometry, magnet polarity, and all rule-relevant authored properties. Normalize entity IDs so arbitrary ID naming does not defeat equivalence detection.

For non-square boards, apply only transforms that preserve dimensions unless the schema/catalog explicitly supports swapped dimensions.

Create:

- Exact fingerprint
- Symmetry-normalized fingerprint: lexicographically smallest canonical serialization across valid transforms
- Optional structural similarity signature for review-only near-duplicate detection

Do not include campaign number, difficulty label, author metadata, tutorial copy, runtime state, or UI information in rule-content fingerprints.

Add golden transform tests for positions and all four directions. Add pairs known to be equivalent and non-equivalent, including Pull/Push polarity differences.

## 5. Fixed campaign curriculum and pacing audit

Do not implement a runtime `ProgressionEngine` that chooses different numbered levels for different players. Audit and, where safe, improve the checked-in 100-level sequence.

Use these curriculum ranges as a starting point, adapted to the actual frozen mechanics and current tutorial placement:

- Levels 1–10: arrow fundamentals, exits, blocking
- Levels 11–25: Pull and simple captures
- Levels 26–40: Push and polarity flips
- Levels 41–60: multiple magnets and occlusion
- Levels 61–80: cancellation, walls, and order dependencies
- Levels 81–100: advanced combinations

Generate a deterministic pacing report containing for each level:

- Stable ID and campaign number
- Old and v2 difficulty scores/bands
- Quality score/status/reasons
- Primary and secondary mechanic tags
- Clean solution length
- Fatal-choice/forced-move/branching summary
- Similarity/duplicate findings
- Recommended action: KEEP, MOVE, TUNE_METADATA, REPLACE, or MANUAL_REVIEW

Pacing rules should flag—not automatically rewrite—the following:

- Mechanic used before teaching/introduction
- More than three `Hard` or higher levels consecutively
- Large unexplained difficulty jumps
- Long stretches with nearly identical mechanic/layout signatures
- Recovery level missing after a demanding cluster
- Tutorial level with excessive branching/fatal-choice risk
- Late campaign with no meaningful order/magnetic complexity

Use a general rhythm such as `Easy/Normal → Medium → Medium/Hard → Recovery`, not a rigid repeating template.

### Applying campaign changes

First generate `docs/content/M5_1_CAMPAIGN_AUDIT.md` and a machine-readable report. Then apply only high-confidence changes.

- Never change stable level IDs.
- Prefer metadata/tag/band correction over board replacement.
- If the product is not yet externally released, catalog sequence may be reordered while keeping IDs stable, but add a tested content/progress migration and document old→new sequence mapping.
- If any external tester/user progress exists, do not reorder silently. Preserve completed IDs/stars and map Continue to the next valid incomplete level.
- Replace a board only for a hard quality failure or clearly documented weakness.
- Every replacement must pass full certification, uniqueness, quality, grading, hint-performance, and manual visual inspection.
- Do not expand beyond the current 100-level release catalog in this milestone.

## 6. Recovery-level policy

Recovery does not mean trivial. Define a recovery level as one that:

- Scores at least one band below the recent peak or materially reduces fatal-choice/branching load.
- Reinforces an already taught mechanic.
- Remains aesthetically and mechanically worthwhile.
- Does not introduce a new rule.

Add campaign lint warnings for missing recovery opportunities, but keep thresholds configurable until closed-test data calibrates them.

## 7. Golden content suite

Expand the golden test corpus with representative stable levels for:

- Tutorial/fundamentals
- Pull capture
- Push exit
- Polarity-dependent ordering
- Multiple magnets
- Occlusion by arrow, wall, and magnet
- Equal-distance cancellation
- A forced sequence
- A high-branching but fair level
- A valid fatal-choice/dead-end level
- A recovery level
- A Very Hard/Expert candidate

For every golden level assert, as applicable:

- Solvability
- Certified clean solution replay
- Stable solution length
- Stable raw metric vector
- Expected v2 difficulty score range/band, not an unnecessarily brittle exact score
- Expected quality status
- Exact and symmetry fingerprint
- Expected critical mechanic evidence

When weights/config versions change deliberately, golden updates require a checked-in comparison report and rationale. Do not casually regenerate expected values.

## 8. Tooling and reports

Extend the existing JVM content tooling rather than adding a parallel framework.

Provide tasks/commands equivalent to:

```text
analyzeCampaignDifficulty
analyzeCampaignQuality
checkCampaignSymmetryDuplicates
auditCampaignPacing
certifyCampaignContent
```

One combined deterministic task is acceptable if it emits separate sections/artifacts.

Required generated/checked-in artifacts:

- `docs/content/M5_1_CAMPAIGN_AUDIT.md`
- `docs/content/m5_1_campaign_metrics.json` or CSV
- `docs/content/m5_1_v1_v2_difficulty_comparison.csv`
- `docs/content/m5_1_duplicate_report.md`
- `docs/content/m5_1_sequence_migration.md` only if ordering changes

Normal app builds must consume checked-in certified metadata and must not recalculate the full analysis. Analysis tasks write to a staging/build directory first; promotion to checked-in reports/content is explicit.

## 9. Closed-test calibration hooks

Do not implement adaptive difficulty before closed testing. Prepare only a privacy-safe offline/analytics mapping so later reports can compare objective metrics with aggregated player outcomes:

- Starts/completions
- Restarts
- Resolved failed launches/overloads
- Hints shown
- Actions above par
- Solve-duration bucket
- Exit/abandon level
- Star result

Use the typed M4 analytics layer and effective consent policy. Do not add new tracking SDKs, exact board-state logging, user IDs, or adaptive runtime selection.

Create `docs/content/M5_1_CLOSED_TEST_CALIBRATION_PLAN.md` specifying how to compare these outcomes against v2 scores after sufficient tester data exists. State clearly that objective scores are hypotheses until calibrated.

## 10. Future extension documentation only

Update the content roadmap without implementing it:

- Current closed-test catalog: 100 levels
- First validated content update: target 150
- Second update: target 200
- Mature curated catalog: target approximately 300
- Optional Infinite Mode only after generator quality, performance, and player demand are proven

Future adaptive logic may tune optional generated/bonus content or assistance, but must not secretly replace/reorder the fixed numbered campaign.

No production code for Infinite Mode, player skill estimation, or adaptive selection belongs in M5.1.

## Explicitly rejected supplemental requirements

Do not implement:

- Attraction-only magnets
- Magnet `fieldRadius`, `strength`, moving magnets, or realistic physics
- New polarity rules
- `IDLE`, `MOVING`, `BLOCKED`, `CLEARED` inside core Arrow domain state
- A second rules engine, solver, state model, generator, repository, or persistence layer
- Generic game engine/GameFoundry/ECS architecture
- Runtime procedural replacement of campaign levels
- Personalized numbered campaign order
- 8×8 or 10×10 campaign boards without separate device/readability validation
- 200 additional levels in this milestone
- Infinite Mode or adaptive difficulty
- Backend/cloud content
- New monetization or higher ad frequency

## Tests

Keep all M0–M5 tests passing and add:

### Metric tests

- Deterministic output for same analyzer/config/content version
- Forced-move and fatal-choice definitions on constructed known states
- Branching and critical-order metrics
- Pull/Push/flip/control-change metrics
- Occlusion and cancellation evidence from core-owned diagnostics
- Capped/unknown counterfactual handling
- Weight validation and component clamping
- V1/V2 comparison stability

### Quality tests

- ACCEPT/REVIEW/REJECT boundaries
- Hard failure overrides numeric score
- Mechanic-tag relevance
- Tutorial ambiguity and visual-density checks
- Multiple-solution level not automatically rejected

### Symmetry tests

- Every valid transform for positions and directions
- Equivalent rotations/reflections share symmetry fingerprint
- Non-equivalent polarity/entity/configuration differs
- ID renaming does not change fingerprint
- Non-square safe transforms

### Catalog/migration tests

- All 100 levels retain unique stable IDs
- Every level parses, validates, solves, and replays certification
- No exact/symmetry duplicates
- Pacing audit is deterministic
- Curriculum mechanic introduction order
- Sequence mapping preserves completed IDs, stars, rewards, and Continue behavior if reorder occurs
- No normal build rewrites assets/reports

### Performance tests

- Full campaign analysis finishes within a documented JVM tooling budget
- Runtime app does not perform full counterfactual analysis
- Hint solver performance remains within M3/M5 caps

## Implementation and verification sequence

Do not rerun the complete M5 release audit after every intermediate change. Use this sequence:

1. Implement the M5.1 analyzer, quality, fingerprint, tooling, and test changes using narrow relevant tests.
2. Generate the first campaign audit before changing campaign order or board content.
3. Review every `REVIEW` and `REJECT` finding.
4. Apply only justified metadata, pacing, sequence, or replacement changes.
5. Regenerate all M5.1 reports and resolve remaining hard failures.
6. Complete manual level-quality and visual-readability review.
7. Freeze the updated 100-level catalog.
8. Run the complete M5.1 verification suite below once the implementation and catalog work are finished.
9. Report M5.1 completion and any remaining blocker.
10. Stop. The owner will run the separate full M5 release audit afterward as the final `GO FOR CLOSED TESTING` gate.

During implementation, run the narrowest applicable tests for fast feedback. Do not repeatedly run `bundleRelease`, connected tests, or the complete M5 audit unless a specific change requires them.

## M5.1 final verification

Use actual project task names. After all implementation, report review, campaign changes, and manual review are complete, run equivalents of:

```bash
./gradlew :game-core:test
./gradlew :level-tools:test
./gradlew analyzeCampaignDifficulty
./gradlew analyzeCampaignQuality
./gradlew checkCampaignSymmetryDuplicates
./gradlew auditCampaignPacing
./gradlew certifyCampaignContent
./gradlew :app:testDebugUnitTest
./gradlew :app:testReleaseUnitTest
./gradlew lintDebug lintRelease
./gradlew :app:bundleRelease
```

Run relevant connected tests if campaign order/progress UI changes. Report unavailable hardware honestly.

This verifies M5.1 itself. It does not replace the separate full M5 release audit, which is intentionally deferred until M5.1 is completely finished.

## Definition of done

M5.1 is complete only when:

- Existing M0–M5 verification remains passing.
- All 100 levels retain stable IDs and full solver certification.
- Difficulty v2 uses documented Magnetrail-specific metrics and configurable weights.
- Quality is scored independently with stable reason codes.
- Exact and symmetry-equivalent duplicates are rejected or explicitly resolved.
- A deterministic campaign audit and v1→v2 comparison exist.
- Curriculum order teaches mechanics before combining them.
- Difficulty pacing avoids unreviewed hard clusters and includes appropriate recovery levels.
- Any reorder/replacement has a tested progress/content migration.
- Golden tests cover every critical Magnetrail mechanic and representative difficulty pattern.
- Runtime behavior and frozen rules are unchanged.
- No Infinite Mode, adaptive campaign, new magnet physics, extra levels, or new monetization was added.
- The updated catalog is frozen and the repository is explicitly marked ready for the separate full M5 release audit.

## Final response

Report:

1. Existing implementation inspected and reused.
2. Difficulty v2 metrics, formulas/config version, and differences from v1.
3. Quality score/gates and every REVIEW/REJECT level.
4. Exact/symmetry duplicate findings and resolutions.
5. Campaign pacing findings, recovery changes, reorder/replacement decisions.
6. Stable-ID/progress migration impact.
7. Final difficulty and quality distribution for all 100 levels.
8. Exact analysis/certification/test/lint/build commands and results.
9. Generated reports and any manual review still required.
10. Final M5.1 status: `IMPLEMENTATION COMPLETE — READY FOR FULL M5 AUDIT` or `M5.1 BLOCKED`, with exact reasons.

Begin by inspecting the repository and running the existing baseline needed to establish a trustworthy starting point. Produce the first campaign audit before changing level order or content. Then implement only evidence-backed changes within this scope. Do not run or claim the final full M5 release audit inside this task; it will be performed separately after M5.1 is fully complete.
