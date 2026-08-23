# Magnetrail Puzzle Difficulty v3 specification

Version: `magnetrail-difficulty-v3.0`  
Quality supplement: `magnetrail-puzzle-quality-v2.0`  
Phase: 0 only

## Product objective

Puzzle Difficulty v3 estimates the player's perceived cognitive/search burden: whether the
board asks the player to infer a consequential order from visible routes and mechanic
interactions. It does not reward a board merely for being large, crowded, long, or full of
magnets. A busy forced puzzle must score below a sparse puzzle with several fair, consequential
decisions.

Correctness and structural acceptance remain automated. Human review remains a separate
experience gate and is never synthesized from an automated score.

## Search authority and bounds

The analyzer calls the production `GameEngine` for every remaining arrow at every retained
state. Successful actions always remove one arrow, so the reachable successful-action graph is
acyclic. States are keyed by the complete production `StateKey` and memoized.

Default bounds:

- unique expanded states: 100,000;
- action resolutions: 1,000,000;
- counted shortest solutions: 100,000;
- distinct solution-family signatures: 512;
- magnetic counterfactual checks: 10,000.

Every bound has a separate truncation flag. An incomplete state graph, unknown root
solvability, invalid clean replay, or confidence below the configured certification floor is a
hard rejection for changed campaign content. A cap is never interpreted as a fatal branch.

## Player-choice classification

The complete set of authored arrows is visible and tappable, but raw taps are not meaningful
branches. At each analyzed state:

- `immediatelyInvalid`: production resolution fails now (collision or invalid Pull exit). The
  consequence is derivable from the visible board and frozen rules, so this does not contribute
  to effective branching.
- `plausible`: production resolution succeeds now. This definition uses only the visible board
  and immediate rule outcome, not future solver knowledge.
- `strategicallyViable`: a plausible action whose resulting state has a solver-proven path to
  completion.
- `deceptiveButFair`: a plausible action whose resulting state is proven unsolvable, while its
  observable one-ply outcome differs from viable choices and exposes a route, controller,
  polarity, occlusion/cancellation, or next-action-count clue a player could reason about.
- `guessDependent`: a plausible action whose resulting state is proven unsolvable but lacks a
  differentiating player-observable clue, or has the same normalized observable one-ply profile
  as a viable choice.

The observable profile contains only information derivable from the current board and frozen
rules: terminal family, magnetic polarity/control, route-length bucket, polarity change, and
the multiset of immediate outcomes available after the move. This bounded one-step look-ahead
is player-reasonable route/polarity reasoning, not solver foreknowledge. It excludes future
solvability. A fatal action remains guess-dependent when a viable action has the same immediate
outcome and the same normalized next-outcome multiset.

Guess-dependent actions do not increase Difficulty. They reduce Quality. Deceptive-but-fair
choices may contribute to decision/dead-end complexity because the player has evidence to
distinguish them.

## Search and sequence metrics

- `minimumSolutionLength`: shortest production-engine completion depth.
- `shortestSolutionCount`: bounded number of shortest action sequences.
- `solutionFamilyCount`: bounded count of distinct shortest mechanic/constraint trace shapes;
  commuting orders with the same strategic shape collapse into one family.
- `meaningfulDecisionPoints`: canonical-shortest-path states with at least two distinct
  strategic outcome groups after immediately invalid and guess-dependent actions are removed.
- `effectiveBranchingFactor`: average distinct strategic outcome groups across canonical path
  states. Viable branches with identical future family/depth shape collapse.
- `forcedSequenceLength`: canonical steps having exactly one strategically viable action.
- `forcedMoveRatio`: forced sequence length divided by minimum solution length.
- `obviousNextActionRatio`: canonical steps having exactly one plausible immediate action.
- `averageDecisionSpacing`: mean forced/non-decision gap before, between, and after meaningful
  decision nodes. With no decisions it equals the full solution length.
- `maximumForcedRunLength`: longest consecutive canonical run with exactly one strategically
  viable action.
- `deadEndActionCount` / `deadEndStateCount`: proven fatal successful actions and reachable
  states with no successful completion.
- `deadEndProofDepth`: minimum number of further successful actions required to reach an
  obviously dead state; reported as average and maximum for fatal actions.
- `backtrackingPressure`: proven fatal successful edges divided by all known successful edges
  from solvable explored states.
- `uniqueStatesExpanded` and `depthDistribution`: stable search-effort evidence, never a
  standalone definition of difficulty.

## Dependency and mechanic metrics

For the canonical shortest solution, action `i` has a dependency edge to later action `j` when
removing/flipping at `i` changes `j`'s production-resolution signature. The longest dependency
path is `dependencyDepth`; the longest consecutive run of relevant route/controller/polarity
interactions is `multiStageInteractionDepth`.

Core-owned diagnostics report Pull, Push, polarity flips, controlling-magnet changes,
occlusion, cancellation, and wall contribution. `mechanicRelevanceRatio` is the portion of
canonical steps where a mechanic or dependency materially changes a route/control/future
choice. Object presence alone is not relevance.

## Purposeful space

`rawOccupancyRatio` is authored entity cells divided by board cells and is a readability fact,
not difficulty. Arrow cells are purposeful because they are player actions. A magnet or wall
cell is purposeful only when production analysis proves that it controls an action, participates
in cancellation/occlusion, or is a collision target. Empty cells are purposeful when they are
part of a production-traced route or a proven magnetic/blocking relationship. Merely authoring
an object does not make its cell purposeful.

Reports include:

- raw empty-space ratio;
- purposeful occupied/cell ratio;
- purposeful and irrelevant authored-object counts/ratios;
- purposeful empty-cell count and ratio;
- unused empty-cell count and ratio;
- portion of authored empty cells that participates in a route/field/blocking relationship.

Random walls and unreachable filler do not become purposeful merely by increasing occupancy.

## Normalized v3 components and weights

All component values are clamped to `0..1`; the serialized config contains every target and
weight. Curves use the documented saturating power function
`min(1, max(0, value / target)) ^ exponent`, with exponent `0.80` unless a component states
otherwise.

| Component | Weight | Primary evidence |
|---|---:|---|
| Solution complexity | 10% | Minimum length moderated by decision activity; forced length alone cannot saturate it. |
| Meaningful decision complexity | 25% | Decision count/density and decision spacing. |
| Effective branching complexity | 15% | Distinct strategic outcome groups, excluding invalid/guess branches. |
| Dependency depth | 15% | Dependency-chain and multi-stage interaction depth. |
| Fair dead-end/backtracking | 10% | Deceptive-but-fair branches and bounded proof depth; guess traps contribute zero. |
| Solution constraint | 10% | Non-forced portion plus bounded solution-family constraint/diversity. |
| Mechanic interaction | 10% | Material route/control/polarity/occlusion/cancellation/wall contribution. |
| Meaningful spatial/routing | 5% | Production route complexity and purposeful empty space, not raw board size/density. |

The weighted result is rounded and clamped to `0..100`. Confidence is reported separately and
never multiplies a weak structure into a stronger score. A score with incomplete essential
analysis is non-certifiable.

Bands remain internal: Tutorial 0–15, Easy 16–30, Normal 31–45, Medium 46–60, Hard 61–75,
Very Hard 76–90, Expert 91–100.

## Band-aware structural rejection

The numeric score is necessary but insufficient. Default floors:

| Target | Minimum solution | Decisions | Dependency depth | Effective branch factor | Non-forced portion | Mechanic relevance |
|---|---:|---:|---:|---:|---:|---:|
| Medium | 3 | 1 | 1 | 1.15 | 0.25 | 0.30 |
| Hard | 4 | 2 | 2 | 1.30 | 0.35 | 0.40 |
| Very Hard | 5 | 3 | 3 | 1.50 | 0.45 | 0.50 |
| Expert | 6 | 4 | 4 | 1.70 | 0.55 | 0.60 |

Hard-or-higher content is also rejected for guess-dependent ratio above 0.20, incomplete
analysis, confidence below 0.95, or no material mechanic/dependency evidence. Tutorial content
is rejected/reviewed for more than two plausible opening choices, any guess-dependent choice,
or excessive decision/dependency load. Machine-readable reason codes accompany every failure.

The Phase 0 range score targets are 5–15, 15–30, 30–45, 45–60, 60–75, and 75–90 for
displayed ranges 1–10 through 81–100. Levels 101–150 remain an upper-band continuation with
explicit recovery roles rather than a reset.

## Quality v2 supplement

Quality is calculated independently and penalizes:

- incomplete/low-confidence analysis;
- guess-dependent choices and arbitrary traps;
- long forced/non-decision runs after the tutorial;
- no meaningful decision structure in medium/high target content;
- irrelevant mechanics/objects and unused space;
- unreadable raw congestion;
- exact, symmetry, dependency-pattern, or near-structural repetition;
- low score margin near a review/reject boundary.

Hard correctness, replay, schema, hash, ID, and duplicate failures remain `REJECT` regardless
of numeric Quality. Status is `ACCEPT`, `REVIEW`, or `REJECT`; human status is separate.

## Human Review Priority

The review-routing score is normalized to `0..100` and combines difficulty confidence,
solver/diagnostic truncation, branching outliers, extreme score, Quality margin, structural
novelty, structural similarity, newly combined existing mechanics, and solution-depth outliers.
It prioritizes which boards a person should inspect first. It cannot approve or promote a board.
All generated rows default to human status `PENDING`.

## Calibration fixtures

Golden fixtures must include solvable/unsolvable states, minimum-depth alternatives, a fully
forced long path, a decision-rich path, fair and guess-dependent dead ends, multiple commuting
solutions, dependency chains, sparse complex versus busy forced boards, and every search cap.
The busy forced fixture must score below the sparse decision-rich fixture, and a nominal
difficulty-70 score must still fail Hard gates when structural floors are absent.
