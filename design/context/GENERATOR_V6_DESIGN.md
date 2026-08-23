# Magnetrail Generator V6 design

Status: five-band technical pilots certified; awaiting real human calibration; production remains V10.

## Authority and boundaries

Generator V6 targets frozen rule version `magnetrail-core-1` and generator version `6`. It adds no
gameplay entity or runtime generator. `DefaultGameEngine` remains the only authority for movement,
control, collision, polarity mutation, win and deadlock. `:game-core` contains immutable pure-JVM
construction and analysis. `:level-tools` alone owns files, catalogs, reports and promotion.

V6 staging content uses schema `1` for V6 audit/certificate envelopes and level-catalog schema `2`,
content version `12`, generator version `6`. Staging version 12 is not production content version 12.

Technical refinement 3 freezes realizer version `state-conditioned-engine-guided-cegis-v3` and decision
analyzer version `complete-easiest-policy-dag-v2`. The realizer chooses the smallest deterministic
geometry from bounded engine-guided proposals or the MRV constraint fallback. Every proposal is
expanded by the production engine before causal roles are bound. The analyzer enumerates the full exact DAG first, then grades the least-cost
winning policy path through the quotient structure so commuting permutations cannot inflate
difficulty.

## Pipeline

```text
GeneratorV6Identity
  -> ReverseLogicalConstructorV6 / CausalHypergraphSpec
  -> deterministic engine-guided CEGIS realization / bounded CSP fallback
  -> production-engine authored-witness replay
  -> independent Solver replay
  -> complete pre-quotient Decision DAG
  -> production causal witnesses and trap contracts
  -> purposeful occupancy counterfactuals
  -> V4 and Quality supplementary rejection
  -> human-visible cognitive features and bounded policy ensemble
  -> exact semantic canonicalization
  -> deterministic MAP-Elites
  -> staging catalog and audit
```

The implementation lives under
`game-core/src/main/kotlin/com/rameshta/magnetrail/core/generation/v6/`. V5 remains readable for
reconstruction and comparison but is not called by V6.

## Versioned logical model

`CausalHypergraphSpec` contains typed logical entities, typed actions, state conditions, causal
hyperedges, a solution partial order and successful-trap contracts. A relationship is only
certified after `ProductionCausalVerifierV6` finds a reachable exact pre-state and observes its
trigger and counterfactual through `GameEngine.resolve`.

The six grammar identities are:

1. `POLARITY_LOCK_RELEASE`
2. `OCCLUSION_REVEAL_CHAIN`
3. `CANCELLATION_RELEASE`
4. `COMPETING_CONTROLLER_HANDOFF`
5. `FORK_JOIN_COUPLED`
6. `INTERACTING_CHAINS_DELAYED_TRAPS`

Graph instances vary action count, chain lengths and cross-chain dependencies. Coordinate changes
do not affect causal identity. Bucket 5 requires at least two interacting chains. V6 has five
empirical bands—Easy through Expert. Master was removed by owner authorization and is not a V6
generation, calibration, validation, or promotion target.

## Reverse construction

Construction starts from the set of actions absent in a solved state and adds required actions
backward. The result is an acyclic partial order. A deterministic lexicographic topological order is
stored only as an engine replay witness. It does not become the difficulty basis.

Successful traps are actions that remove an arrow and persist their polarity/controller change but
enter a completely proven losing sub-DAG. The same action must occur on a winning policy at the
correct state. Failed taps are immutable annotations and never count as traps.

## Spatial realization

`EngineGuidedSpatialRealizerV6` performs bounded deterministic causal mutations and complete
production-engine analysis before accepting geometry. It turns observed counterexamples into the
next stable proposal schedule and refuses exhausted niches. Its CSP fallback,
`DeterministicConstraintRealizerV6`, uses:

- minimum-remaining-values variable selection;
- forward checking after every assignment;
- least-constraining-value ordering;
- first-entity symmetry breaking;
- deterministic seed-derived tie breaking;
- bounded nogood recording;
- explicit state, nogood and elapsed-time caps.

Variables include entity positions, arrow directions and magnet polarities. The compiler adds
alignment, distance, between/occlusion and route-readability constraints. Static constraints only
prune. A realized board is untrusted until production-engine verification.

Default per-candidate caps are 6 construction attempts and at most four stable slot seeds, 100,000 realizer states, 20,000 nogoods,
1,000 ms, 200,000 decision states, 2,000,000 action resolutions, 500,000 counterfactuals and 200,000
canonical-backtracking states. Any cap is a structured rejection. At most two CEGIS refinement
iterations are represented by identity revisions 0–2.

The realizer's millisecond budget is converted to a frozen deterministic constraint-evaluation
budget (`2,000` work units/ms), so scheduling and machine speed cannot change catalog bytes. A
30× wall-clock watchdog aborts the entire run without a certificate; it never selects a candidate.

## Complete decision analysis

`CompleteDecisionDagAnalyzerV6` expands every remaining arrow in every reachable exact state.
Successful transitions remove one arrow and recurse. Failed resolutions are stored on the node and
must retain the exact input state. Solvability, shortest/longest completion and delayed deadlock are
computed only after complete expansion.

Two actions commute only when both orders succeed, reach the same exact production state, have the
same transition-effect multiset and have identical future solvability. Mandatory precedence is
derived from all winning states, not the authored witness. The analyzer reports transitive
reduction, poset width, forced runs, non-commuting decisions, successful losing branches,
deadlock/lookahead depth, polarity-memory span and winning-choice share. Any truncation nulls the
essential metrics.

## Purposeful occupancy

Occupancy is `(arrows + magnets + walls) / cells` and contributes no difficulty points.
`PurposefulOccupancyAnalyzerV6` requires reachable route/controller/polarity/order/trap evidence or
an engine counterfactual outcome change for an occupied object. Every magnet and wall needs a
witness. Empty cells are separately labelled for routes or line-of-sight separation.

Buckets 1–4 require purposeful occupied ratio at least 0.85; bucket 5 requires at least 0.90.
Inert occupied ratio may not exceed 0.10. Filler is rejected.

## Semantic uniqueness

V6 retains exact, D4, arrow, interactive and perceptual fingerprints. Rectangular D4 now includes
all eight transforms with transformed width/height. V6 adds relevance-pruned layout, causal
hypergraph, decision DAG, solution policy, meaningful-decision trace, mechanic rhythm and
behavioural descriptors.

Causal and policy graphs use a coloured incidence representation. Weisfeiler-Lehman-style colour
refinement is only pruning; unresolved cells are individualized through deterministic exhaustive
canonical backtracking. A cap rejects the candidate, so an approximate hash is never accepted as
proof.

## MAP-Elites and ordering

Only technically valid candidates enter `DeterministicMapElitesV6`. Cells use family, meaningful
decisions, trap/deadlock depth, polarity memory, poset dependency and purposeful occupancy.
Comparison is lexicographic across target-vector distance, guess dependence, purposeful relevance,
semantic novelty, readability margin, analysis cost and exact fingerprint. There is no aggregate
difficulty score. Parallel results must merge in seed/fingerprint order.

## Determinism

All entity/action/state iteration is explicitly sorted. Randomness is the checked-in SplitMix64
`SeededRandom`. Catalog bytes exclude wall-clock timings. Generation tasks are non-default and write
only versioned staging paths. Normal build/test tasks never generate or mutate canonical content.
