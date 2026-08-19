# M5.1 analyzer contract

`magnetrail-difficulty-v2.0` and `magnetrail-quality-v1.0` are deterministic JVM/build-time analyzers. They call the production `GameEngine` and `Solver`; they do not implement a second movement or magnetic-rules model. Runtime gameplay does not run this campaign analysis.

## Bounds and state identity

- Default solution-count cap: 64.
- Default solver-state cap: 50,000.
- Default diagnostic counterfactual cap: 256.
- Alternative states are memoized by the complete `StateKey`: level ID, dimensions, remaining arrow IDs/positions/directions, magnet IDs/positions/current polarities, and wall positions.
- A capped branch is recorded as unknown. It is never counted as a fatal choice.
- Metrics follow one deterministic clean solution returned by the production solver. Every successful Magnetrail action removes one arrow, so clean solution length is supporting order/look-ahead context rather than an independent dominant score.

## Difficulty metric definitions

- `cleanSolutionLength`: actions in the replayed certified clean solution.
- `plausibleOpeningActions`: all authored arrows present at the initial state.
- `successfulOpeningActions`: opening actions that resolve successfully and have a solver-proven completion.
- `averageSuccessfulBranching` / `maximumSuccessfulBranching`: mean and maximum count of solver-proven completion-preserving actions at states on the clean solution.
- `forcedMoveRatio`: clean-solution states with exactly one completion-preserving action divided by clean-solution states.
- `fatalChoiceRatio`: immediate-success alternatives, excluding the certified choice, proven unsolvable divided by all such alternatives with a known solver result. Immediate collisions and capped/unknown results are reported separately.
- `criticalOrderConstraintCount`: certified states having at least one immediate-success alternative proven unsolvable.
- `solutionDivergenceDepth`: zero-based earliest clean-solution state with another completion-preserving action; `null` means no proven alternative divergence.
- `magnetControlledSolutionActions`, `pullSolutionActions`, `pushSolutionActions`: certified actions controlled by a magnet, split by its polarity before the action.
- `polarityFlipCount`: successful certified actions that flip their controlling magnet.
- `controllingMagnetChangeCount`: changes between consecutive non-null controlling-magnet IDs on the clean solution.
- `occlusionDependencyCount`: certified actions whose rule result/control changes when a rule-relevant aligned blocker is removed, using the core diagnostic query and production resolver.
- `cancellationDependencyCount`: certified actions where the core trace reports more than one equal-nearest visible magnet and therefore no single controller.
- `wallDependencyCount`: occlusion dependencies specifically caused by a wall.
- `solverStatesExplored`: production solver work for the root certification query. It is evidence, not an independent difficulty definition.
- `solutionCountUpToCap`: production solver solution count with `solutionCountCapped` recorded separately.
- `boardDensity`: occupied authored cells divided by board cells.
- `visualCongestionScore`: clamped heuristic combining occupied-cell density (50%), adjacent-entity-pair ratio (25%), and arrows aligned with multiple magnets (25%). It is a readability signal, not a gameplay rule.

## Difficulty score V2

All caps, normalization targets, curve exponent, and weights live in serializable `DifficultyConfig`. Weight validation requires exactly 100: wrong-order/dead-end risk 25, branching 20, critical order 20, magnetic/polarity 20, look-ahead/divergence 10, and density/readability 5. Wrong-order risk combines solver-proven fatal alternatives with immediate failed selections, while preserving the separate raw counts. Inputs use clamped power curves. Reports preserve raw metrics, normalized components, weights, score, band, version, capped flags, and unknown flags.

Bands are Tutorial 0–15, Easy 16–30, Normal 31–45, Medium 46–60, Hard 61–75, Very Hard 76–90, and Expert 91–100. These are internal calibration labels and make no intelligence or health claim.

## Quality and duplicate gates

Quality is scored independently from difficulty. Stable reason codes cover replay, schema/ID/hash integrity, exact and D4 uniqueness, mechanic evidence, forcedness, opening ambiguity, local similarity, readability, solver cost, grading, curriculum position, and recovery windows. Unsolvable/incomplete content, replay/schema/hash failures, duplicate IDs, and exact or symmetry duplicates force `REJECT` regardless of numeric score.

Exact fingerprints retain orientation for content metadata compatibility. Symmetry fingerprints transform every arrow position/direction, magnet position/polarity, and wall under all eight D4 transforms on square boards. Non-square boards use identity, 180-degree rotation, horizontal reflection, and vertical reflection only. Entity IDs and non-rule campaign/UI metadata are excluded. The structural similarity signature is review-only and cannot reject content by itself.

## Recovery and fixed progression

`RecoveryPolicy` is configurable. Campaign lint looks for a level at least one band below a recent three-level Hard-or-higher peak and records warnings rather than rewriting the sequence. The checked-in campaign remains the sole deterministic order; IDs and progress semantics are unchanged.
