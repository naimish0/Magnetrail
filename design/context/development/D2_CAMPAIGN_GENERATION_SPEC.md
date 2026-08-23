# Magnetrail D2 Campaign Generation Specification

Status: **implemented and owner-directed promotion completed**  
Generator: `5`  
Staging content version: `7`  
Rules authority: `magnetrail-core-1`

## Scope and invariants

D2 builds a new offline candidate-generation and diagnostic system. It does not change engine
semantics, Generator V4, Difficulty V4, or the runtime adaptive-selection path. All movement,
control, collision, line-of-sight, cancellation, and polarity outcomes come from the production
engine. Following the staging audit, the owner directed promotion of the 200 certified boards as
content version 7 / generator version 5.

Generator V5 writes candidates only under `docs/content/d2/staging`. A normal `build` does not
regenerate content. The separate `promoteD2Campaign` task is destructive, verifies frozen source
and candidate hashes, requires an explicit command confirmation, archives content v6, remaps
staging IDs to stable production IDs, recertifies every board, and atomically replaces the catalog.

## Architecture

```text
GenerationProfileV5
        ↓
LevelGeneratorV5 (deterministic, offline)
        ↓
CertificationPipelineV5
        ├─ production GameEngine
        ├─ production Solver
        ├─ solution replay / failed-action immutability
        ├─ Difficulty V4
        ├─ StructuralAnalyzerV5
        └─ existing Puzzle Quality V2 safety check
        ↓
immutable staging catalog + diagnostics
```

Adaptive difficulty is separated from generation:

```text
Selectable certified content
        ↓
DifficultySelectionV1 ← PlayerSkillStateV1
        ↑                    ↑
curriculum/exposure     bounded performance update
```

`DifficultySelectionV1` is an offline prototype. No app repository, UI, or persistence class
imports it in D2.

## Explicit generation dimensions

Each `GenerationProfileV5` explicitly defines:

1. structural difficulty band;
2. permitted grid sizes;
3. arrow, magnet, and wall ranges;
4. object-density range;
5. meaningful interaction-density range;
6. magnetic-distance profile;
7. dependency depth;
8. polarity-impact depth;
9. cancellation transitions;
10. mandatory-ordering depth;
11. consequence depth;
12. object-participation ratio;
13. safe-choice ceiling;
14. greedy completion ceiling;
15. random-success ceiling;
16. meaningful-failure floor;
17. recovery pressure;
18. strategic-choice density;
19. exposure/reveal events;
20. alternative-path pressure;
21. commutation-quotiented strategy diversity and permutation redundancy.

The six profiles are `TUTORIAL`, `EASY`, `MEDIUM`, `HARD`, `EXPERT`, and `MASTER`. An additional
`MASTER_9X9_EXPERIMENTAL` profile exists for diagnostics only and is excluded from the 200-level
staging target. Grid size is a permitted input, never a difficulty assignment: the matrix includes
8x8 Easy/Medium/Hard/Expert candidates and compact Hard candidates when structural gates permit.

## From-scratch generation

V5 does not mutate campaign levels or Generator V4 templates. A frozen SplitMix64 seed selects the
board size, density, object counts, locations, directions, and polarities. Placement is biased
toward aligned magnetic corridors, longer relationships, intervening blockers, cancellation
arrangements, and reveal opportunities. These biases do not certify the result. Every raw board is
subsequently accepted or rejected using production-engine evidence.

The generator is bounded by per-profile attempt, solver-state, reachable-state, action-resolution,
counterfactual, and strategy-enumeration limits. Any truncation is a rejection, not a score.

## Interaction graph

Nodes are authored arrows, magnets, and walls. Typed edges are:

- `COLLISION`
- `OCCLUSION`
- `MAGNET_CONTROL`
- `CANCELLATION`
- `POLARITY_DEPENDENCY`
- `ROUTE_BLOCK`
- `EXPOSURE`
- `ORDER_DEPENDENCY`
- `STATE_DEPENDENCY`
- `REVEAL`
- `ALTERNATIVE_PATH`

An edge is emitted only after the production engine or a production-engine counterfactual shows
the relationship. Static alignment alone is not a meaningful edge.

Interaction density is:

```text
unique authored object pairs with at least one meaningful typed edge
--------------------------------------------------------------------
              all unordered authored object pairs
```

Typed edge count is reported separately, so stacking many relationship labels onto one pair does
not inflate density. Connected components, isolated objects, component size, degrees, type
distribution, and a versioned graph fingerprint are also reported.

## Object relevance

For every authored object, the analyzer removes that object from a counterfactual board and repeats
bounded production-engine reachability analysis. It compares:

- root solvability;
- winning first actions after excluding the removed arrow itself;
- strategy/opening structure;
- dependency edges;
- polarity-dependent decisions;
- consequence breadth;
- mandatory ordering;
- line-of-sight relationships;
- cancellation transitions;
- route/collision structure;
- the object's participation degree in the canonical interaction graph.

The normalized evidence produces `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, or `IRRELEVANT`. A level
remaining solvable does not make an object irrelevant. `MEDIUM` and above count toward the profile's
object-participation gate. Incomplete counterfactual analysis rejects a candidate.

## Consequence, polarity, ordering, and strategies

For every successful action, the analyzer compares the other arrows' production outcomes before
and after the state transition. Controller, direction, terminal event, cancellation, or success
changes create exposure/reveal/state-dependency evidence. It also compares the real flipped state
with an otherwise identical unflipped state; only changed future actions count as polarity impact.

Ordering is based on solvable and fatal successor states plus Difficulty V4's mandatory pair and
commutation analysis. Raw winning sequences are diagnostic only. The generation signal uses the
canonical/meaningful strategy count after commuting permutations are collapsed, and separately
penalizes permutation redundancy.

## Certification sequence

Each candidate must pass, in order:

1. profile shape and density bounds;
2. failed-action state immutability;
3. complete production solver search;
4. solvability and a replayed clean solution;
5. complete Difficulty V4 analysis;
6. typed interaction analysis;
7. complete object counterfactual analysis;
8. dependency, polarity, ordering, consequence, exposure, greedy/random, and strategy gates;
9. existing Quality V2 hard-reject safety checks;
10. exact and symmetry uniqueness across the staging pool.

High V4 score, large grid, large object count, or raw solution count cannot independently certify a
candidate.

## Candidate distribution and staging

The initial 200-level pool targets:

| Band | Count |
|---|---:|
| Tutorial | 12 |
| Easy | 35 |
| Medium | 45 |
| Hard | 55 |
| Expert | 40 |
| Master | 13 |

This distribution is now present in production content. The offline selector prototype still
demonstrates dynamic deterministic selection only; runtime integration remains outside D2.
Candidate IDs retain the `d2-v5-*` namespace in staging, while promotion maps each candidate by
campaign number onto the existing production `proto-*` / `campaign-*` ID.

## Promotion and migration

The owner-directed promotion changed all 200 board fingerprints while preserving all 200 production
IDs. Each promoted level carries the exact content-v6 fingerprint in
`previousContentFingerprint`. On first DataStore read, completion, stars, unlocks, selection,
first-clear claims, coins, settings, monetization state, and Daily state remain unchanged. Old
board-specific action/overload/hint minima move into bounded legacy records, and new-board minima
start empty. The process is idempotent and prevents duplicate first-clear rewards.

The content-v6 source and the complete migration ledger are under `docs/content/d2/promotion/`.
Human ratings were not manufactured; content-v7 human calibration remains pending.

## Adaptive skill model

`PlayerSkillStateV1.skillScore` is bounded to `[0,100]` and uses an eight-result rolling window.
The transparent completion-quality observation is:

```text
35% completion
20% actions/par efficiency
15% restart pressure
15% hint pressure
10% deadlock pressure
 5% completion-time bucket
```

The update also includes a small challenge adjustment for the completed structural band. Each
observation changes skill by at most five points. One hard level therefore cannot cause a multi-band
drop. Money, coins, ads, identity, device, age, location, account, and network data do not exist in
the model.

## Deterministic selection

Selection uses content version, progression position, selection ordinal, selection version, player
skill state, curriculum progress, and certified metadata. It enforces:

- one-band maximum upward jump;
- tutorial/curriculum ceilings;
- prior mechanic exposure;
- recovery after repeated struggle, not one experiment;
- recent ID, grid, interaction, dependency, strategy, and mechanic anti-repetition;
- stable hash plus level ID tie-breaking;
- a finite deterministic fallback.

The result includes the selected level ID, band, selection version, and a human-readable reason.

## Human calibration

Automated certification is not human approval. D2 creates a blind review catalog and a rating sheet
using the 1–10 owner scale. The sample covers Easy, Medium, Hard, Expert, Master, easy large-grid
controls, and compact hard controls. Automated bands are omitted from the play sheet. Pearson,
Spearman, MAE, confidence, and any model change remain **NOT MEASURABLE WITH CURRENT
IMPLEMENTATION** until actual owner ratings are entered.

## Migration and promotion

The staging candidates use new IDs. D2 does not assume that stars, records, completion rewards, or
progress on an old puzzle transfer to a different puzzle. The offline migration checker requires a
complete, one-to-one, explicitly approved mapping and verifies that Daily state, settings, and
economy remain independent.

The generated promotion manifest therefore states:

```text
promotionAllowed = false
migrationProvenSafe = false
status = BLOCKED_PENDING_HUMAN_REVIEW_AND_MIGRATION_PROOF
```

There is no production promotion implementation in D2. This is the mandatory stop point.

## Commands

```text
./gradlew generateCampaignV5Candidates
./gradlew analyzeCampaignGenerationV5
./gradlew analyzeObjectRelevanceV5
./gradlew analyzeInteractionGraphsV5
./gradlew analyzeCampaignDifficultyV4
./gradlew analyzeCampaignQuality
./gradlew checkCampaignSymmetryDuplicates
./gradlew auditCampaignPacing
./gradlew calibrateDifficultyV4
./gradlew testAdaptiveDifficultySelection
```

Normal `./gradlew build` does not generate or promote campaign content.
