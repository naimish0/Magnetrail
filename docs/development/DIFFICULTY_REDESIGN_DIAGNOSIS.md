# Phase 0 difficulty redesign diagnosis

Diagnosis date: 2026-08-19 (Asia/Kolkata)  
Evidence basis: checked-in 150-level `magnetrail-campaign-v4` catalog, existing
`magnetrail-difficulty-v2.0` analysis, production `GameEngine`/`Solver`, and the successful
pre-change baseline recorded in `MASTER_DEVELOPMENT_STATUS.md`.

This diagnosis was written before changing difficulty scoring or campaign boards.

## Product defect confirmed

The player report that the campaign feels easy, flat, and visually empty is consistent with
the repository evidence. The current content is valid and solver-certified, but certification
mostly proves that a board can be cleared. It does not prove that the player must make a
meaningful sequence of decisions.

Across all 150 levels:

- 86 have no solver-proven critical order constraint and the same 86 have zero fatal-choice
  ratio under v2's one-solution-path analysis.
- 105 have no measured occlusion, cancellation, wall, or controller-change dependency.
- 58 expose at least 80% of their starting arrows as completion-preserving openings.
- 41 have a certified solution of only one or two actions.
- 34 are at least 80% forced along the selected clean solution.
- 33 reach the solution-count cap; this is recorded, but v2 counts action sequences rather
  than distinguishing genuinely different solution families.
- The final v2 distribution has no `Very Hard` or `Expert` levels: Tutorial 11, Easy 22,
  Normal 24, Medium 73, and Hard 20.

The late campaign does not meet its intended challenge. Levels 81–100 have median v2 score
46, zero critical constraints in 14 of 20 boards, zero fatal-choice ratio in 14 of 20,
82.8% of opening choices completion-preserving on average, and a median of only 24 expanded
states. Their average raw empty-space ratio is 78.1%, far above the Phase 0 calibration
guidance of roughly 20–35% for that range.

## Range evidence from the current catalog

| Range | Median v2 | Avg solution | Avg forced ratio | Avg viable branching | Zero critical | Avg raw empty space |
|---|---:|---:|---:|---:|---:|---:|
| 1–10 | 18.5 | 1.80 | 0.883 | 1.117 | 8/10 | 87.8% |
| 11–25 | 29 | 2.20 | 0.822 | 1.178 | 10/15 | 88.0% |
| 26–40 | 33 | 3.40 | 0.569 | 1.942 | 14/15 | 84.5% |
| 41–60 | 33.5 | 2.50 | 0.802 | 1.248 | 12/20 | 83.7% |
| 61–80 | 46 | 3.55 | 0.567 | 1.883 | 15/20 | 80.9% |
| 81–100 | 46 | 4.65 | 0.372 | 2.503 | 14/20 | 78.1% |
| 101–125 | 60 | 3.96 | 0.486 | 1.874 | 6/25 | 77.4% |
| 126–150 | 60 | 4.28 | 0.453 | 1.960 | 7/25 | 76.0% |

The apparent reduction in forced ratio late in the campaign is not enough: it is often
caused by many safe, commuting choices. For Levels 81–100 the bounded solution count averages
38.6, while critical constraints average only 0.3. Wide safe solution sets raise raw
branching without necessarily creating reasoning pressure.

## Repetitive logical shapes

Exact and D4 fingerprints are unique, yet coarse logical vectors repeat heavily:

- 39 levels share the same three-step / 1.333 branching / 0.667 forced / one-critical-choice
  metric shape.
- 16 share a three-step shape with no fatal choice or critical constraint.
- 15 share a six-step, six-opening, 3.5-branching shape with no fatal or critical choice.
- 12 share a fully forced two-step shape with no critical dependency.
- 12 share a six-step, five-opening shape with no fatal or critical choice.

This proves that geometric uniqueness is not structural diversity. Transforming, embedding,
or wall-dressing a known board can preserve essentially the same dependency graph and
solution shape.

## Pipeline trace and causes

1. Authored/generated candidates are parsed and validated into immutable core levels.
2. `LevelGenerator` selects an existing known-solvable template, applies a D4 transform and
   board embedding, and adds bounded random walls only when the known path still replays.
   It does not synthesize a new decision/dependency graph.
3. `CertificationPipeline` checks profile object counts, board size, failed-action immutability,
   solvability, opening count, clean replay, limited mechanic counts, and a v2 score range.
   It has no purposeful-space, player-choice-quality, forced-run, solution-family, or
   band-aware strategic floor.
4. `Solver` memoizes production-engine states and counts successful action sequences. It
   returns one solution, a bounded count, minimum depth, valid first actions, and an expanded
   state count. It does not expose the explored decision graph, failed actions, dead-end proof
   depth, decision spacing, effective branch equivalence, or player-observable plausibility.
5. `DifficultyAnalyzer` follows one selected solution and separately asks whether successful
   alternatives remain solvable. This is useful but path-dependent. V2 has no distinction
   between long forced play and long decision-making play, no guess-dependence classification,
   and no meaningful/unused space analysis.
6. V2 gives 20% weight to raw completion-preserving branching. Many interchangeable safe
   orders can therefore look complex. Density is only 5%, but magnetic action/flip counts and
   solution length also add score without proving consequential decisions.
7. Quality v1 derives `meaningfulDecisionCount` from critical constraints, alternate recovery
   windows, and maximum branching. It can reward numerous safe alternatives, does not detect
   observationally indistinguishable guess traps, and has no margin-aware human-review priority.
8. Runtime loads the checked-in catalog through the app asset synchronization path; normal
   builds do not generate boards. That separation is sound and should be retained.

## Required Phase 0 correction

The safe reusable foundation is the production `GameEngine`, immutable `StateKey`, core-owned
magnetic diagnostics, parser/validator, deterministic PRNG, certification/replay, and exact/D4
fingerprints. Phase 0 should extend these rather than create another rules engine.

The replacement analysis must retain the bounded explored graph and classify every remaining
arrow at analyzed states as immediately invalid or player-plausible. Plausible actions must then
be separated by solver evidence into strategically viable, deceptive-but-fair, and
guess-dependent choices. Guess-dependent traps reduce Quality and must not inflate Difficulty.

It must report minimum solution length separately from forced sequence length, decision-node
count, average decision spacing, and maximum forced run. Effective branches must collapse
strategically equivalent safe orders. It must also measure dependency depth, dead-end proof
depth, solution families, mechanic constraint contribution, purposeful route/line-of-sight
space, confidence, and truncation.

Finally, high-band acceptance must use structural floors. A nominal score cannot rescue a busy,
long, or magnetic board with no consequential decisions. The remediation workflow must stage an
oversized candidate pool and a complete 150-row `KEEP`/`TUNE`/`REPLACE` proposal before any
campaign content is overwritten. All human-review fields remain `PENDING` until a person records
the review.

