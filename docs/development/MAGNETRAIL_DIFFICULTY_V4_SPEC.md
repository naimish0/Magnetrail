# Magnetrail Difficulty V4 Diagnostic Specification

Status: D1 implementation, calibration version 0. **Human rating collection complete: 40/40 selected levels rated; model calibration remains preliminary.**

> V4 is an experimental diagnostic model and is not yet a production difficulty authority.

## Motivation

The owner completed all 200 campaign levels and found them very easy. That human observation outranks the existing numerical certification as a product signal. The preceding audit found that 98.3% of plausible choices were strategically viable, many winning sequences were reorderings of independent actions, and polarity/walls were often present without changing the decision. Difficulty V3 measures useful correctness and structural signals, but raw activity, branching, solution length, and solution-family counts can remain high when the player can safely choose almost anything.

V4 asks a different question: **if the player chooses the wrong successful action, does it materially change what they can do next?** It is an offline, read-only diagnostic. It does not replace V3, quality certification, gameplay rules, or human review.

## Architecture and isolation

V4 lives under `game-core/.../difficulty/v4` and calls the production `GameEngine` and immutable `StateKey`. It does not alter engine, solver, generator, campaign, UI, or runtime code. V3 and V4 are independently runnable. The `level-tools` commands are explicit offline tasks and are not dependencies of builds, app tests, or campaign promotion.

## State graph and action vocabulary

For each reachable state, V4 enumerates every remaining arrow tap in stable arrow-ID order and records its production resolution.

- `plausibleChoiceCount`: all visible arrow taps in completely analyzed solvable states.
- `immediatelyInvalidChoiceCount`: taps whose production resolution fails and preserves state.
- `successfulChoiceCount`: taps whose production resolution removes the selected arrow.
- `safeSuccessfulChoiceCount`: successful actions with a proven solvable child.
- `futureDeadEndChoiceCount`: successful actions with a completely explored, proven unsolvable child.
- `capabilityChangingSuccessfulChoiceCount`: viable actions that differ from an alternative in future successful/viable/fatal action counts, solution-freedom bucket, or proven commutation.
- `solutionReducingSuccessfulChoiceCount`: viable actions with fewer winning continuations than the least-constraining viable alternative at that state.
- `meaningfulSuccessfulChoiceCount`: the union of future-dead-end, capability-changing, and solution-reducing successful actions.

Unknown descendants caused by a bound are never classified as safe or harmful.

## Meaningful and harmful decisions

A state is a **meaningful decision state** when it has at least two successful choices and at least one of these is proven:

1. one successful choice is viable and another produces a future dead end;
2. viable choices have different future capability signatures;
3. viable choices do not commute to the same production state.

A raw state-signature difference or route-animation difference alone does not qualify.

A **harmful decision state** is the stricter subset containing both a proven viable successful choice and a successful choice whose completely explored descendant cannot reach a win.

Formulas:

```text
meaningfulFailureRate = futureDeadEndChoiceCount / successfulChoiceCount
harmfulDecisionDensity = harmfulDecisionStateCount / meaningfulDecisionStateCount
safeChoiceRatio = safeSuccessfulChoiceCount / successfulChoiceCount
meaningfulSuccessfulChoiceRatio = meaningfulSuccessfulChoiceCount / successfulChoiceCount
```

Zero denominators produce `0`, not an invented difficulty signal.

## Consequence persistence and depth

Calibration-0 uses a conservative proof for harmful consequences. For every successful action with a completely proven unsolvable child, it measures the shortest remaining successful-action depth required to prove deadlock. The initial harmful action counts as depth one. It reports minimum, maximum, mean, and median depth.

Consequence depth also counts meaningful decision states encountered on a shortest dead-end proof path. This distinguishes a consequence that persists through later decisions from an immediate deadlock. Non-fatal capability differences are reported as capability changes but are not assigned a speculative persistence depth in calibration-0.

## Mandatory ordering

When complete winning-sequence enumeration is available, each arrow pair is classified from all winning sequences:

- mandatory `A → B`: every winning sequence places A before B;
- mandatory `B → A`: every winning sequence places B before A;
- flexible: both orders occur.

V4 reports the mandatory pair count/ratio, longest mandatory chain, dependency graph depth, and observed commuting/independent pairs. Different but winning orders do not become mandatory merely because their intermediate states differ.

## Polarity-dependent actionability

Every successful action with a production polarity callback is evaluated against a counterfactual child state where only that flip is reverted. V4 compares:

- next successful action IDs;
- next viable action IDs;
- descendant solvability;
- route-resolution signatures.

A flip is strategically impactful only when successful/viable availability or solvability changes. A route signature change without those effects is `routeOnly`. An actionability change also counts as an ordering impact for calibration-0. The aggregate metric is:

```text
polarityImpactRatio = strategicallyImpactfulPolarityFlips / polarityFlips
```

## Commutation-quotiented strategies

Actions A and B commute at a state only when both succeed in both orders and the two-action results have the same canonical `StateKey`. This exact-state criterion is conservative: it may under-collapse strategically equivalent states, but it will not merge states merely because both remain solvable.

For complete bounded winning-sequence enumeration, sequences are unioned when one can be transformed into the other by an adjacent proven-commuting swap. The number of disjoint sets is the canonical strategy count.

```text
permutationRedundancy = (rawWinningSequences - canonicalStrategies) / rawWinningSequences
commutationRatio = commutingViablePairs / testedViablePairs
```

Enumeration and quotienting are bounded. If the sequence or counterfactual cap is reached, canonical count and redundancy are `null`, analysis is incomplete, and the provisional score is withheld.

## Greedy and random-success resistance

The deterministic greedy policy is `stable-authored-order-successful-v1`: at each state it chooses the first still-present arrow in authored order whose immediate production action succeeds. It reports completion, first divergence into a non-viable child, and recovery need. It deliberately does not pretend to model visual salience.

The random-success diagnostic is `fixed-seed-uniform-successful-v1`. For every explicit serialized seed, it uniformly chooses among successful actions sorted by ID. It reports completion/deadlock rates, mean actions/failures/recovery depth, and action-count variance. It is deterministic offline evidence, not runtime randomness or a complete human model.

## Recovery, forcedness, and decision concentration

Every proven future-dead-end action is locally recoverable through Undo because gameplay retains the previous successful state; it still imposes backtracking/restart pressure. V4 reports count, shortest dead-end proof depth, maximum recovery depth, restart pressure, and normalized pressure.

The canonical path is the lexicographically stable first viable path. A step is forced when only one viable successful continuation exists. V4 reports solution length, forced-step count, longest forced run, meaningful-decision count/density, first/last decision depth, and maximum/average gaps. Forced-run excess is a penalty, not positive difficulty.

## Wall and magnet relevance

Each wall and magnet is removed independently in a bounded counterfactual. It is strategically relevant when its removal changes solvability or the complete set of winning action sequences. A magnet is also relevant when its polarity has proven actionability impact. Counts become `null` when the base/counterfactual search or sequence comparison is incomplete. Mere presence is never rewarded.

## Provisional score — calibration version 0

For a raw metric `x` and documented target `t`:

```text
curve(x,t) = clamp(x / t, 0, 1)^0.80
score = clamp(round(sum(positive contributions) - sum(penalties)), 0, 100)
```

Positive weights sum to 100:

| Component | Normalization target | Weight |
|---|---:|---:|
| Meaningful failure rate | 0.25 | 15 |
| Harmful decision density | 0.60 | 20 |
| Average consequence persistence | 3 decisions/actions | 10 |
| Mandatory ordering | 0.40 ratio (65%) + chain depth 4 (35%) | 15 |
| Polarity actionability | 0.50 impact ratio | 10 |
| Greedy resistance | binary greedy failure | 10 |
| Random-success resistance | deadlock rate | 10 |
| Recovery pressure | 0.35 | 5 |
| Meaningful decision density | 0.50 | 5 |

Penalties are subtracted:

| Penalty | Weight |
|---|---:|
| Safe-choice ratio | 20 |
| Permutation redundancy | 15 |
| Longest forced run / solution length | 10 |
| Irrelevant walls and magnets / total walls and magnets | 5 |

These weights are explicit engineering hypotheses, not validated psychometric weights. Raw winning-sequence count is never a positive component. A score is `null` if the base state graph, winning-strategy quotient, or ordering analysis is incomplete.

## Confidence and truncation

Confidence begins at 1.0 and visibly deducts for incomplete work: base search 0.55, strategies 0.15, ordering 0.10, polarity 0.08, object relevance 0.08, and random trials 0.04. Reasons and per-family statuses accompany every score.

Default explicit bounds:

| Bound | Default |
|---|---:|
| Expanded base states | 100,000 |
| Base action resolutions | 1,000,000 |
| Search depth | 32 |
| Enumerated winning sequences | 100,000 |
| Canonical representatives | 100,000 |
| Counterfactual states | 200,000 |
| Counterfactual resolutions | 2,000,000 |
| Polarity counterfactuals | 100,000 |
| Object counterfactuals | 128 |
| Fixed random trials | 256 |

All bounds and seeds are serializable in `DifficultyV4Config`. An optional JSON config is supplied with `-PdifficultyV4Config=/absolute/or/project/path.json`. No truncation is silently treated as complete.

## Determinism

State/action traversal uses stable IDs, report rows use level number, maps with externally visible ordering are stable, random trials use explicit seeds and a frozen SplitMix64 implementation, and no time/network/device randomness is read. Equal campaign + analyzer version + configuration + seeds must yield byte-identical JSON/CSV output when the human calibration file is unchanged.

## Human calibration

The human file has explicit `referenceStrong`, `weakControl`, and `easyControl` sampling groups. `referenceStrong` means “strongest current reference,” not objective Hard truth. A rater supplies a 1–8 difficulty rating and may supply nullable observations about obviousness, mistakes, ordering, polarity, interchangeability, Undo/restart, fairness, and comments. Multiple raters are supported.

`calibrateDifficultyV4` compares human ratings with V3 and V4 using Pearson when appropriate, Spearman rank correlation, MAE on a declared 0–100 mapping, rank disagreement, over/underestimates, and metric-level correlations. Reports always warn `SAMPLE SIZE LIMITED` and `CALIBRATION PRELIMINARY`. Calibration never changes weights, thresholds, levels, or production code automatically.

Campaign-wide feedback such as “all levels were very easy” is important product evidence, but it
must not be expanded automatically into invented per-level ratings. The project owner has now
supplied actual ratings for all 40 selected levels. Automation compares only these supplied
observations; optional fields remain unrated.

Workflow:

1. Run `./gradlew analyzeCampaignDifficultyV4`.
2. Fill `MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json`.
3. Run `./gradlew calibrateDifficultyV4`.
4. Review `MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md` before proposing any model change.

## Examples

Three independent actions with all six permutations winning have high raw sequence count but one canonical commuting strategy, high safe-choice ratio, high redundancy, no harmful decision, and a low V4 diagnostic.

If A and B both succeed now, but A changes polarity so B later collides while B then A wins, the root is a harmful meaningful decision; B-before-A is mandatory; the polarity flip changes actionability; greedy/random failure and recovery evidence increase the provisional diagnostic.

## Limitations

The analyzer cannot reliably measure human-perceived obviousness, visual fairness, player attention, noticing an occlusion, visual route confusion, emotional difficulty, or satisfaction. Exact-state commutation can under-collapse semantically equivalent strategies. Counterfactual removal measures structural relevance, not whether the player notices the object. The deterministic policies do not model gaze or learned heuristics. Human calibration exists because solver evidence is not human experience.

Automated analysis certifies bounded structural evidence only. It is neither human validation nor human approval.
