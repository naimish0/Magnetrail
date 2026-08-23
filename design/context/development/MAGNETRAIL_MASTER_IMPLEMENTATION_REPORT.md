# Magnetrail master implementation report

Updated: 2026-08-20 (Asia/Kolkata)

## Status

**PASS — V5.2 EXPERT/MASTER AUTOMATED CERTIFICATION COMPLETE**

The player-flow, economy, hint, scalable navigation, and multi-topology architecture work is
implemented. The V5.2 purposeful-space weave now passes the unchanged Expert/Master certification
gates. No automated result is represented as human approval.

## V5.2 certification update

The earlier blocked results below are retained as historical evidence. V5.2 did not lower a gate:
it removed 35 inert shell cells from each 8×8 high-band board and retained the physical route/LOS
guards that participate in the ordered polarity component. Known-seed results are V4 `63` Expert
and `68` Master, both complete/non-truncated, with ordering depth `5`, relevance ratio `0.5517`,
interaction density `0.1552`/`0.1453`, and average relevance `0.1940`/`0.2202`.

The checked-in Infinite catalog contains four unique certified Expert and four unique certified
Master variants. The catalog has zero exact or symmetry duplicates. Campaign content is unchanged.

## Player-facing implementation

- Removed Undo from gameplay controls, UI state, actions, feedback, analytics, and tests.
- Economy configuration version 3 awards 10 coins once per campaign level, Daily identity, or
  Progressive journey ordinal first completion and zero coins for newly earned stars. Replay and
  duplicate completion remain reward-safe.
- Hint cost remains 30 coins. At 30 or more, Hint atomically spends and shows without a popup. At
  0–29, no coins are spent and the existing voluntary rewarded-ad path is used. One in-flight hint
  job prevents duplicate charging.
- Level Select calculates 50-level ranges dynamically, renders only the active range, supports
  Previous/Next and Go To, and opens around the current/next level.
- A metadata-only logical index and versioned reconstruction identity support at least 10,000
  logical levels without generating boards during list rendering.

## Generator V5 architecture

- Added versioned `LogicalLevelIdentityV5` reconstruction inputs.
- Added an acyclic `CausalGraphV5` and deterministic structural fingerprint.
- Added a board-realizer boundary and deterministic factory.
- Added a certified-only candidate pool with exact and structural duplicate rejection.
- Added a bounded purposeful-empty policy. V5.2 Expert/Master use explicit causal-focus space up to
  their configured limits; other generation profiles retain their existing occupancy behavior.
- Added mandatory physical verification of declared semantic relationships after materialization
  and transformation.
- Repair acceptance continues to require solvability, canonical replay, and preservation of
  ordering, polarity, exposure, long-range, and participating-wall structure.

Supported high-band families:

1. `ORDERED_POLARITY_V1` — retained V5.1 topology and default.
2. `CAUSAL_POLARITY_TAIL_V2` — materially different board; V4 truncation is surfaced and rejected.
3. `ORDERED_POLARITY_STAIRCASE_V3` — separate alternating must/reveal grammar with a coherent
   causal component rather than an appended independent mini-puzzle.
4. `ORDERED_LONG_RANGE_WEAVE_V4` — a bounded ten-action weave that replaces selected inert shell
   walls with physically verified magnetic endpoints. It keeps the V5.1 ordering solution space,
   makes the reveal/controller state counterfactually relevant, and adds no independent actions.

## Expert/Master certification result

The new staircase was evaluated with the real production engine and unchanged V4:

| Metric | Staircase Expert | Staircase Master |
|---|---:|---:|
| V4 score | 70 | 71 |
| V4 complete | Yes | Yes |
| Mandatory ordering rate | 1.0000 | 1.0000 |
| Mandatory ordering depth | 10 | 12 |
| Relevant-object ratio | 0.2812 | 0.3125 |
| Average relevance | 0.0626 | 0.0686 |
| Interaction density | 0.0233 | 0.0312 |
| Long-range relationships | 0 | 0 |
| Certified | No | No |

The staircase proves the generator can create a distinct, fully ordered causal grammar without
modifying V4. Expert still lacks required meaningful long-range/density/relevance/wall evidence.
Master remains short on alternative-path and participating-wall evidence. A later purposeful
long-range corridor preserved physical semantics but exceeded V4 sequence enumeration; it was
removed immediately instead of increasing caps or weakening gates.

The retained V5.1 baseline remains:

| Metric | V5.1 Expert | V5.1 Master |
|---|---:|---:|
| V4 score | 61 | 66 |
| Ordering depth | 3 | 5 |
| Safe-choice ratio | 0.5996 | 0.5154 |
| Long-range relationships | 3 | 1 |
| Wall occlusions | 3 | 2 |
| Interaction density | 0.0303 | 0.0283 |
| Relevant-object ratio | 0.2344 | 0.2500 |
| Average relevance | 0.0604 | 0.0615 |
| Certified | No | No |

Therefore the latest request to “fix Master and Expert certification” is **BLOCKED by measured
candidate quality**, not by solver failure or an implementation bypass. Certification cannot be
honestly completed without another causal topology that combines V5.1 long-range relationships
with staircase ordering/relevance while remaining inside V4 enumeration bounds.

### Bounded V4 causal-weave result

The requested fourth topology was implemented and evaluated once per profile with seed
`11510013 + profileIndex`. All declared wall/controller occlusion edges were verified on the
physical board before and after deterministic transformation. One proposed Master edge was
removed because the physical verifier proved that it did not exist.

| Metric | V4 Expert | V4 Master |
|---|---:|---:|
| V4 score | 61 | 67 |
| V4 complete / truncated | Yes / No | Yes / No |
| Safe-choice ratio | 0.5996 | 0.5154 |
| Mandatory-ordering ratio | 0.2000 | 0.4444 |
| Mandatory-ordering depth | 3 | 5 |
| Solver/V4 states | 576 | 340 |
| Certified | No | No |

The bounded unchanged-gate certifier rejected Expert for interaction density, object
participation, average object relevance, and participating-wall ratio. It rejected Master only for
object participation and participating-wall ratio. Telemetry for both profiles recorded one
construction, no solver failure, no V4 truncation, no ordering failure, no safe-choice failure, no
commutation failure, and no consequence failure. The result is therefore **PARTIAL SUCCESS —
STRUCTURE IMPROVED BUT CERTIFICATION STILL BLOCKED**. Per the stopping rule, no seed search,
threshold relaxation, analyzer change, or campaign promotion followed.
Because this topology deterministically fails unchanged participation gates, it remains available
only by explicit family selection and is excluded from the normal bounded-attempt rotation.

## Campaign safety

- Canonical campaign changed by this milestone: **No**
- Current canonical level count: **205**
- Current content version: **8**
- Production IDs changed: **No**
- Gameplay engine semantics changed: **No**
- Difficulty V4 changed: **No**
- Generator V4 changed: **No**
- Certification thresholds weakened: **No**
- Automated approvals recorded as human approvals: **0**

Expected canonical SHA-256:

`6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`

## Validation

Passed commands:

```text
./gradlew --configuration-cache :app:testDebugUnitTest :game-core:test :level-tools:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.ProceduralTopologyV5Test' \
  --tests 'com.rameshta.magnetrail.core.economy.*' \
  --tests 'com.rameshta.magnetrail.core.retention.RetentionPolicyTest' \
  --tests 'com.rameshta.magnetrail.tools.ContentArtifactsTest'

./gradlew --configuration-cache :app:compileDebugAndroidTestKotlin certifyCampaignContent
./gradlew --configuration-cache build
./gradlew --configuration-cache :app:testDebugUnitTest :level-tools:test :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.ProceduralTopologyV5Test.known truncated family is never selected by automatic bounded attempts'
./gradlew --configuration-cache :app:compileReleaseKotlin

./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.retention.RetentionPolicyTest' \
  :app:testDebugUnitTest \
  --tests 'com.rameshta.magnetrail.M3ProgressRepositoryTest' \
  :app:compileReleaseKotlin

./gradlew :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.ProceduralTopologyV5Test.ordered long range weave stays inside unchanged V4 bounds' \
  --configuration-cache

./gradlew :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.ProceduralTopologyV5Test.ordered long range weave fails closed when structural gates remain unmet' \
  --configuration-cache
```

Evidence:

- Final `:game-core:test :level-tools:test :app:testDebugUnitTest` regression passed in `7m 10s`
  with configuration cache reused (`37` actionable tasks; `5` executed, `32` up-to-date).
- Final `certifyCampaignContent` passed in `2m 37s`: Quality certified `205/205`, the production
  engine/solver certified 205 campaign levels plus seven Daily fallbacks, and the existing Level
  205 owner waiver was reported rather than hidden.
- Focused app/core/tools tests: passed.
- Android UI-test Kotlin compilation: passed.
- Campaign Quality: 205/205 passed with no hard Quality or symmetry-duplicate failure.
- Production solver certification: 205 campaign levels and seven Daily fallbacks passed; Level
  205’s existing structural waiver was reported, not hidden.
- Full build after all final changes: passed, 279 tasks (`49 executed`, `230 up-to-date`).
- Release Kotlin compilation after final player-flow adjustment: passed.
- Gradle configuration cache: enabled in `gradle.properties`, stored and reused.
- `git diff --check`: passed.
- First 200 level definitions match the archived content-v7 source after canonical JSON sorting.

## Required next content action

Run a blind human calibration of the four certified Expert and four certified Master Infinite
boards. Compare perceived difficulty with V4 `62–63`/`68`, safe-choice `0.5996`/`0.5154`, and
ordering depth `5`. Do not modify the numbered campaign or treat these automated certificates as
human approval until those ratings exist.
