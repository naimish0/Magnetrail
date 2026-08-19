# Magnetrail Expert/Master Occupancy Experiment

Date: 2026-08-20  
Status: **HYPOTHESIS REJECTED — GENERATION REMAINS BLOCKED**

## Scope

This was a bounded, staging-only investigation of Generator V5 `EXPERT` and `MASTER`. Easy,
Medium, Hard, Difficulty V4, gameplay rules, certification thresholds, and the canonical campaign
were not changed.

The tested hypothesis was that 100% occupancy prevents the solution-first constructor from
building sufficiently deep Expert/Master dependencies. A temporary experimental materializer
honored the existing spatial profile's target occupancy by reserving a deterministic contiguous
relief region. The experiment was removed after it produced no certification or quality gain.

## Baseline

The known deterministic profiles both reached complete solvability and complete Difficulty V4
analysis before rejection:

| Profile | Seed | Attempts | Constructed | Solvable | V4 complete | Certified | Rejections |
|---|---:|---:|---:|---:|---:|---:|---|
| Expert | 11510013 | 1 | 1 | 1 | 1 | 0 | `safe-choice-ratio-above-profile`, `ordering-depth-below-profile` |
| Master | 12510016 | 1 | 1 | 1 | 1 | 0 | `safe-choice-ratio-above-profile`, `ordering-depth-below-profile` |

No occupancy, solver, replay, V4-completeness, or wall-participation gate caused the initial
rejection. Certification stopped at the unchanged V4 pre-gate.

## Controlled experiment

One deterministic topology was tested at every requested occupancy for each band. This is enough
to diagnose the present constructor because its attempt seed only applies symmetry
reflection/rotation to the same dependency-complete topology; it does not create a different
Expert/Master dependency graph.

| Profile | Empty allowance | Actual empty cells | Occupancy | Solvable | V4 complete | V4 score | Meaningful decisions | Dependency depth | Ordering | Safe-choice ratio | Participating walls | Certified |
|---|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---|
| Expert | 0% | 0 | 1.0000 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Expert | 5% | 3 | 0.9531 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Expert | 10% | 6 | 0.9062 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Expert | 15% | 9 | 0.8594 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Master | 0% | 0 | 1.0000 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Master | 5% | 3 | 0.9531 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Master | 10% | 6 | 0.9062 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Master | 15% | 9 | 0.8594 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |
| Master | 20% | 12 | 0.8125 | Yes | Yes | 5 | 7 | 0 | 0.0000 | 0.9372 | 0 | No |

Every relaxed candidate was rejected for the same two reasons as baseline:

- `safe-choice-ratio-above-profile`
- `ordering-depth-below-profile`

Certification rate was 0% for every tested configuration. Wall participation remained zero.

## Root cause

Excessive occupancy is not the current Expert/Master certification bottleneck. The removed cells
were isolated walls that did not participate in reachable gameplay relationships, so removing
them changed neither the action graph nor Difficulty V4.

The actual bottleneck is the fixed high-band topology:

- the constructor materializes the same structural board for every attempt and varies only its
  symmetry;
- its three corridor modules demonstrate long-range control but do not impose globally mandatory
  order;
- successful choices remain safe at a ratio of `0.9372`;
- mandatory-ordering and dependency-graph depth remain zero;
- surrounding walls remain non-participating.

More attempts or more empty cells cannot certify this topology. A useful next change must remain
Expert/Master-only but add at least one alternative deterministic high-band topology whose empty
corridors are part of causal polarity/order structures. That is a construction change, not an
occupancy-profile relaxation.

## Decision

Selected Expert empty-cell allowance: **0% (unchanged)**.  
Selected Master empty-cell allowance: **0% (unchanged)**.

The experimental relaxation was not retained because the smallest tested allowance and every
larger allowance produced no measurable benefit. Gates and V4 were not weakened.

## Validation

Commands executed with Gradle configuration cache enabled:

```text
./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest.knownExpertSeedReportsItsRemainingCertificationBottleneck' --info

./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest.knownMasterSeedReportsItsRemainingCertificationBottleneck' --info

./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest.expertMasterControlledEmptyCellExperiment' --info

./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.D21SpatialDensityTest.profileMatrixSeparatesDensityFromDifficultyAndKeepsNineByNineExperimental' \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest.knownExpertSeedReportsItsRemainingCertificationBottleneck' \
  --tests 'com.rameshta.magnetrail.core.generation.ShippedContentTest'
```

All diagnostic and final focused regression tests passed. The matrix run completed in 1 minute 53
seconds; the final unchanged-source regression completed in 11 seconds. The temporary test and
experimental materializer were removed after the hypothesis was rejected.

Campaign SHA-256 remained:

`8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`
