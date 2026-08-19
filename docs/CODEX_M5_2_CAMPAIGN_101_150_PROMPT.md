# Codex Prompt — Magnetrail M5.2 Campaign Expansion: Levels 101–150

Copy everything below into Codex from the root of the Android Studio repository after M5.1 is fully implemented.

---

You are implementing **Magnetrail M5.2: Campaign Expansion from 100 to 150 Levels**.

The existing project already contains:

- Frozen deterministic Pull/Push Magnetrail rules
- Pure Kotlin/JVM engine and solver
- Versioned generator and certification tooling
- Difficulty Score v2 and independent Level Quality Score
- Exact and symmetry-normalized duplicate detection
- A curated, certified 100-level campaign
- Campaign pacing/recovery analysis and golden content tests
- Android UI, local progress, Daily Challenge, economy, accessibility, ads, and release infrastructure

Preserve all working systems. M5.2 is a content-development milestone, not a testing, monetization, or release milestone.

## Objective

Add **50 launch-quality campaign levels**, producing a fixed catalog of **150 total levels**.

Levels 101–150 must:

- Use only existing frozen mechanics.
- Be generated/constructed with the existing build-time tooling.
- Pass production-engine solver certification.
- Pass Difficulty v2, Quality, fingerprint, grading, and performance gates.
- Be deliberately ordered with difficulty waves and recovery levels.
- Receive manual review evidence before promotion.
- Preserve all original Levels 1–100 and existing player progress.

Target composition for the 50 new levels:

- Approximately 10 intentionally handcrafted or heavily hand-tuned levels.
- Approximately 40 generator-assisted, solver-certified, manually reviewed levels.

These are targets, not permission to mislabel untouched generated output as handcrafted.

## Not part of M5.2

Do not implement:

- Levels 151–300
- Infinite Mode
- Adaptive difficulty or personalized campaign order
- New arrows, magnet physics, objects, switches, teleporters, bombs, ice, moving magnets, field radius, or field strength
- 8×8 or 10×10 campaign boards
- Runtime procedural replacement of campaign levels
- New Daily Challenge mechanics
- New economy, ads, analytics, account, backend, or release/testing systems

## 1. Inspect and baseline

Before changing content:

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect Git status/diff and preserve unrelated user work.
3. Read all files under `docs/`, especially the rules contract, M3 content report, M5.1 campaign audit, Difficulty v2/Quality definitions, duplicate report, golden levels, and content roadmap.
4. Inspect the actual campaign manifest/schema, stable ID rules, generator profiles, certification pipeline, progress repository/migrations, level-selection UI, and tests.
5. Run the current M5.1 baseline:
   - Core and tooling tests
   - Difficulty/quality analysis
   - Symmetry duplicate detection
   - Campaign pacing audit
   - Certification of all current 100 levels
   - Relevant app tests/build
6. Report the current catalog version, exact level count, stable IDs, difficulty/quality distribution, maximum board size, solver caps, and unresolved M5.1 REVIEW findings.

Do not begin new content while any existing hard certification failure remains.

## 2. Preserve Levels 1–100

- Keep every existing stable level ID.
- Do not change existing board definitions merely to make the expanded distribution look smoother.
- Do not renumber existing stable IDs.
- Preserve existing completion, stars, best actions, overloads, hints, rewards, and first-clear flags.
- Existing content fingerprints must remain unchanged unless a separately documented M5.1 defect is being fixed.
- If an existing level must change, isolate it in a separate documented change and rerun its migration/compatibility analysis.

Levels 101–150 receive new stable IDs following the project’s established convention. Campaign sequence number is metadata and must not replace the stable ID.

## 3. New campaign curriculum

Levels 101–150 should feel like an advanced continuation, not fifty consecutive expert walls.

Use existing mechanics to emphasize:

- Multi-step polarity planning
- Pull-to-Push and Push-to-Pull order dependencies
- Multiple aligned magnets
- Arrow/magnet/wall occlusion
- Equal-distance cancellation
- Controlling-magnet changes after earlier arrows clear
- Valid actions that create later dead ends
- Longer dependency chains
- Dense-looking but readable layouts
- Alternate solutions where they remain meaningful and fair

Suggested internal arc:

- **101–110 — Reorientation:** re-establish advanced Pull/Push sequencing after Level 100; mostly Normal/Medium with one Hard peak.
- **111–125 — Crossfields:** multiple magnets, changing controllers, and occlusion; Medium/Hard with recovery levels.
- **126–140 — Polarity Chains:** deeper flip dependencies, cancellations, and plausible fatal choices; Hard/Very Hard with controlled relief.
- **141–150 — Mastery Set:** strongest existing-mechanic combinations; Hard/Very Hard, with Expert used sparingly and a fair final level.

Names are internal unless the existing UI already supports pack names. Do not add marketing copy or new visual themes solely for these labels.

## 4. Board constraints

Use only board sizes already proven readable and accessible in the current phone UI.

Default constraints:

- 6×6 and 7×7 only.
- Maintain effective arrow hit targets through the existing board geometry.
- Respect M5.1 visual-congestion thresholds.
- Avoid using density as a substitute for logic.
- Every entity and field state must remain distinguishable at 360 dp portrait width, large font settings, and high-contrast mode.

Do not introduce 8×8 or larger boards in this milestone.

## 5. Candidate generation

Use the existing deterministic, versioned build-time generator. Do not create a parallel generator.

Add or tune advanced generation profiles only through existing configuration mechanisms. Profiles may constrain:

- Board size
- Arrow count
- Magnet count/polarity mix
- Wall count
- Required magnet-controlled actions
- Required polarity flips
- Occlusion/cancellation dependencies
- Successful opening actions
- Fatal-choice ratio
- Forced-move ratio
- Critical-order constraints
- Target Difficulty v2 range
- Target Quality threshold
- Solver/counterfactual analysis caps

Generation workflow:

1. Produce a reproducible candidate pool in staging from explicit seed ranges.
2. Parse and structurally validate every candidate.
3. Solve with the production engine/solver.
4. Replay certified solutions.
5. Calculate Difficulty v2 and Quality.
6. Reject exact and symmetry duplicates against all existing and staged candidates.
7. Reject near-duplicates or repetitive mechanic/layout clusters through the review signature/report.
8. Apply performance and readability limits.
9. Produce candidate review sheets.
10. Promote only explicitly selected candidates into the shipped catalog.

Normal app builds must not generate candidates or rewrite campaign assets.

## 6. Candidate pool and selection

Generate substantially more candidates than required. Target at least 5–10 viable candidates per final generator-assisted slot when tooling time allows.

For every candidate, report:

- Candidate ID, seed, generator/profile version
- Canonical and symmetry fingerprint
- Board/entity counts
- Certified solution
- Solution count up to cap
- Difficulty v2 score/band/components
- Quality score/status/reasons
- Fatal-choice, branching, forced-move, and order metrics
- Pull/Push/flip/control-change metrics
- Occlusion/cancellation/wall dependencies
- Visual congestion
- Solver states/time/cap flags
- Similarity neighbors
- Proposed curriculum position

Reject candidates with:

- Any hard Quality failure
- Unknown/capped essential certification result
- No meaningful magnetic dependency
- Unreadable congestion
- Excessive repeated structure
- Trivial solution or near-total forcedness without teaching value
- Excessive ambiguity for its intended position
- Solver/hint cost outside runtime limits

## 7. Manual review gate

Algorithmic acceptance is necessary but not sufficient.

Create `docs/content/M5_2_MANUAL_REVIEW.md` with one row per promoted level and explicit reviewer fields:

- Stable ID and campaign number
- Visual inspection complete
- Certified solution manually replayed
- Pull/Push state readable
- Failure feedback understandable
- Intended mechanic actually matters
- Alternatives feel fair
- Difficulty placement feels reasonable
- Not repetitive relative to neighboring levels
- Recovery/peak role confirmed
- Reviewer notes
- Status: APPROVED, REVISE, REJECT

Codex may perform code/data inspection and deterministic replay, but do not claim real human playtesting occurred unless the owner/reviewer actually records it. Levels lacking required manual approval remain staging candidates and are not promoted.

## 8. Handcrafted/heavily tuned levels

Create approximately 10 levels whose logical structure is intentionally designed or substantially revised, rather than accepted directly from generated output.

Each should have a documented design intention, such as:

- Teach a subtle controller change
- Create one highly plausible but fatal ordering choice
- Use cancellation as the key insight
- Require alternating polarity use across magnets
- Provide a recovery puzzle with an elegant short insight
- Deliver a fair campaign finale using only established mechanics

Record authoring rationale and before/after candidate lineage when generator-assisted. Do not falsify origin metadata.

## 9. Difficulty waves and recovery

Run the M5.1 pacing audit over the full 150-level sequence.

Requirements for Levels 101–150:

- No more than three Hard-or-higher levels consecutively without explicit review.
- No abrupt multi-band jump without an intentional transition.
- Place a recovery level after demanding clusters, normally every 4–6 levels where metrics support it.
- Recovery levels must remain meaningful and reinforce mastered mechanics.
- Do not place all Expert levels at the end.
- Level 150 should feel conclusive and fair, not merely maximize score/density.

Pacing tools flag issues; they do not automatically reorder or mutate accepted levels.

## 10. Difficulty/quality targets

Do not force an exact quota if it damages quality. Use this initial target distribution for the 50-level pack:

- Normal: 5–10%
- Medium: 25–35%
- Hard: 35–45%
- Very Hard: 15–25%
- Expert: 0–10%

Every promoted level must be `Quality ACCEPT`, except a documented `REVIEW` may be allowed only after explicit manual approval and rationale. No `REJECT` may ship.

Keep raw metric/component diversity. Fifty levels with different final scores but nearly identical mechanic signatures are not an acceptable pack.

## 11. Grading and stars

Generate and validate grading metadata from certified solutions using the existing M3/M5.1 policy.

- `parActions` derives from the certified clean solution unless a validated authored override exists.
- Two-star threshold uses the current central grading policy.
- Hints and failed actions affect stars exactly as before.
- Do not change economy rewards or star rules.
- Validate all thresholds are internally consistent and reachable.

## 12. Content catalog and version migration

Increment the content catalog version according to existing conventions.

Migration requirements:

- Fresh installs contain 150 levels.
- Existing players retain all progress for Levels 1–100.
- Players who completed Level 100 unlock Level 101.
- Players who had Level 100 selected/complete receive a valid Continue destination.
- Players below Level 100 remain at their existing progress position.
- No first-clear/star/coin reward is duplicated.
- New Levels 101–150 start uncompleted with correct lock state.
- Completing Level 150 does not invent Level 151.
- Daily Challenge history/settings/ad caps remain unchanged.
- Migration is idempotent and corrupt-data recovery remains safe.

Use stable IDs, never only numeric indexes.

## 13. Android UI scalability

Verify the existing level-selection UI handles 150 levels efficiently:

- Lazy rendering and stable keys
- Pack grouping/headers if already supported
- Correct lock/completion/star semantics
- Scroll restoration and Continue destination
- TalkBack traversal and state descriptions
- Large font/display scaling
- 360/390/430 dp widths
- No eager solver/difficulty analysis during list rendering

Do not redesign the whole interface. Make only necessary scalability and content-label changes.

## 14. Content reports

Create/update:

- `docs/content/M5_2_CAMPAIGN_101_150_REPORT.md`
- `docs/content/m5_2_levels_101_150_metrics.csv` or JSON
- `docs/content/M5_2_DUPLICATE_REPORT.md`
- `docs/content/M5_2_PACING_REPORT.md`
- `docs/content/M5_2_MANUAL_REVIEW.md`
- `docs/content/M5_2_MIGRATION.md`
- Updated full 150-level campaign report

The summary must include:

- Final count
- Origin split
- Board-size distribution
- Difficulty/quality distribution
- Mechanic-tag distribution
- Solver complexity/time range
- Duplicate/similarity findings
- Rejected candidate counts by reason
- Seed/profile/generator/analyzer/catalog versions
- Remaining manual-review items

## 15. Golden tests

Add representative golden levels from 101–150 for:

- Controller changes after arrow removal
- Multi-magnet polarity chain
- Occlusion-dependent solution
- Cancellation-dependent solution
- Plausible fatal choice
- High branching but multiple fair solutions
- Recovery level
- Final mastery level

Assert stable solution replay, metric ranges, Quality status, fingerprints, grading metadata, and intended mechanic evidence.

## Tests

Keep all existing tests passing and add:

### Catalog/content

- Exactly 150 campaign levels
- Stable untouched IDs/fingerprints for Levels 1–100
- Unique IDs, sequence numbers, canonical fingerprints, and symmetry fingerprints
- All 150 parse, validate, solve, and replay certified solutions
- All shipped levels are Quality ACCEPT or explicitly approved REVIEW
- Grading metadata is reachable and consistent
- No unknown essential certification caps

### Curriculum/pacing

- Mechanic tags supported by actual diagnostic evidence
- Levels 101–150 satisfy configured difficulty-wave checks or have documented waivers
- Recovery designations satisfy policy
- Level 150 exists, solves, and is appropriately tagged

### Migration/progress

- Fresh install
- Player below Level 100
- Level 100 unlocked but incomplete
- Level 100 completed
- Full three-star progress through Level 100
- Repeated migration
- Partial/corrupt stored data
- No duplicate rewards
- Continue/Next behavior at Levels 100 and 150

### UI/performance

- Lazy level selection renders/scrolls to Level 150
- Semantic state for locked/completed/starred new levels
- Catalog load and list rendering stay within documented budgets
- Runtime never invokes full content analysis

## Implementation sequence

1. Inspect and run M5.1 baseline.
2. Add/update advanced generation profiles and staging reports.
3. Generate a large candidate pool.
4. Certify, analyze, deduplicate, and filter.
5. Design/tune the intentional handcrafted subset.
6. Produce manual-review sheets.
7. Promote only approved candidates.
8. Order Levels 101–150 with difficulty waves/recovery.
9. Add migration and UI scalability changes.
10. Regenerate the full 150-level reports.
11. Run all content, migration, app, lint, and build verification.

Use narrow tests during implementation. Run the complete verification only after the 150-level catalog is frozen.

## Verification commands

Use actual project task names. At minimum run equivalents of:

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
./gradlew :app:assembleDebug
```

Run connected level-selection/progress tests if a device/emulator is available. This is development verification, not the final M5 release audit or closed-test upload.

## Definition of done

M5.2 is complete only when:

- Exactly 150 campaign levels are checked in.
- Levels 1–100 retain stable IDs and progress compatibility.
- All 150 levels pass production-engine solver certification and replay.
- New levels pass Difficulty v2, independent Quality, exact/symmetry duplicate, grading, readability, and solver-performance gates.
- Approximately 10 new levels have documented intentional handcrafted/heavily tuned design.
- Approximately 40 are accurately labeled generator-assisted and manually reviewed.
- Levels 101–150 form a deliberate advanced curriculum with recovery levels.
- No new mechanic or frozen-rule change was introduced.
- Migration safely unlocks Level 101 for eligible existing players.
- Level selection and Continue/Next work through Level 150.
- Reports and manual-review evidence are complete.
- All existing/new tests, certification, lint, and development builds pass.

## Final response

Report:

1. Existing architecture reused and changed files.
2. Final catalog count and stable-ID preservation.
3. Handcrafted/heavily tuned vs generator-assisted split.
4. Candidate generation counts, rejection reasons, seeds/profiles/versions.
5. Difficulty, quality, mechanic, board-size, and solver distributions.
6. Duplicate/similarity findings.
7. Pacing/recovery decisions for 101–150.
8. Migration and level-selection behavior.
9. Exact tests/certification/lint/build results.
10. Manual-review items still pending.
11. Final status: `M5.2 IMPLEMENTATION COMPLETE — READY FOR NEXT DEVELOPMENT SET` or `M5.2 BLOCKED`.

Do not run or claim the final full M5 release audit, closed-test approval, Play upload, or production readiness in this task. Those gates occur only after all planned development sets are complete.

Begin by inspecting the repository and running the M5.1 baseline. Do not generate or promote new levels until the current 100-level catalog has no unresolved hard certification failure.
