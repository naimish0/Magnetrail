# Magnetrail D2.1 — Spatial Density and Object Participation

Status: implemented as an offline Generator V5 configuration. This phase does not alter production campaign content, gameplay rules, Difficulty V4 weights, UI, progress, economy, Daily Challenge, or runtime generation.

## Purpose

D2.1 separates two concepts that must not be conflated:

- Board complexity: grid dimensions, occupied cells, object counts, spatial density and typed interaction density.
- Puzzle difficulty: consequential decisions, harmful successful choices, dependencies, polarity effects, ordering, persistence, recovery pressure, greedy resistance and commutation-quotiented strategies.

Occupancy is a candidate-generation constraint. Difficulty V4 remains the structural difficulty authority.

## Configuration version

- Generator architecture: V5
- D2.1 spatial configuration: 1
- Structural analyzer: V5, extended with D2.1 spatial and participation diagnostics
- Gameplay/rule authority: the production `magnetrail-core-1` engine
- Generation: offline only and deterministic from generator version, profile, configuration and seed

The original D2 profiles remain available. D2.1 adds isolated `v5-d2.1-*` profiles, so the promoted 200-level campaign and prior Generator V5 evidence are not regenerated.

## Profile table

| Difficulty | Grid | Occupancy min / target / max | Arrows | Magnets | Blocks | Meaningful interaction range |
|---|---:|---:|---:|---:|---:|---:|
| Tutorial | 3/4 | 10% / 24% / 40% | 1–3 | 0–2 | 0–3 | 0–45% |
| Easy | 4/5 | 18% / 32% / 46% | 2–5 | 1–3 | 1–7 | 4–55% |
| Medium | 5/6 | 36% / 46% / 56% | 5–8 | 2–4 | 3–12 | 6–58% |
| Hard | 6/7 | 42% / 54% / 66% | 6–10 | 3–6 | 6–19 | 5–62% |
| Very Hard | 7/8 | 48% / 59% / 71% | 8–12 | 4–7 | 10–26 | 6–66% |
| Expert | 8 | 53% / 64% / 76% | 9–14 | 5–9 | 14–30 | 4–70% |
| Master | 8 | 58% / 69% / 80% | 9–15 | 5–9 | 17–32 | 4–74% |

The 9×9 Master profile remains experimental and is excluded from the benchmark until separate board-usability approval. Grid size is never converted directly into a difficulty band.

The interaction-density denominator is the number of possible unordered object pairs. The numerator is the number of unique object pairs connected by at least one production-engine-observed typed relationship. Consequently, dense boards can have lower ratios despite more absolute interactions; the profile ranges reflect that normalization.

## Occupancy and object counts

The spatial analyzer reports:

- board, occupied and empty cells;
- occupancy ratio;
- arrow, magnet and wall ratios;
- total object count;
- overlapping authored cells;
- authored aligned and long-range magnetic relationships;
- arrows aligned with multiple magnets;
- authored arrow blockers and wall-occlusion candidates.

Object counts are selected as a deterministic tuple, not by independently filling random cells. Every tuple must satisfy the profile's independent count ranges and occupancy interval. The target ratio guides selection while minimum and maximum ratios are hard certification gates. No authored overlap is accepted.

Medium and higher profiles therefore cannot certify an empty-looking board. Sparse boards remain permitted for Tutorial, Easy and future recovery profiles.

## Constructive interaction patterns

Higher profiles use deterministic polarity-order chains. A chain deliberately creates:

1. a long-range Pull relationship;
2. an arrow whose removal exposes future control;
3. a required Push/Pull alternation;
4. an early successful move that leaves a harmful polarity state;
5. a persistent controller/actionability change;
6. a route blocker and an ordering dependency.

Additional placement targets aligned arrows, arrow blockers, magnetic corridors, cancellation arrangements and route-role walls. Protected solution corridors reduce the former high-density failure mode where random walls made almost every board unsolvable. The production solver still decides solvability; the generator pattern is not a second gameplay implementation.

## Meaningful participation

The structural analyzer explores production-engine states and records typed edges:

`COLLISION`, `OCCLUSION`, `MAGNET_CONTROL`, `CANCELLATION`, `POLARITY_DEPENDENCY`, `ROUTE_BLOCK`, `EXPOSURE`, `ORDER_DEPENDENCY`, `STATE_DEPENDENCY`, `REVEAL`, and `ALTERNATIVE_PATH`.

For every arrow, magnet and wall, the analyzer removes that object and compares the counterfactual with the canonical board. It compares solvability, winning first actions, strategy-family evidence, dependency structure, polarity transitions, decisions, ordering, typed LOS edges, cancellation and typed route edges. D2.1 fixes route/LOS comparison to use edge identity rather than only edge counts; rewiring to a different blocker is therefore observable.

Objects remain classified `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, or `IRRELEVANT`. Profiles gate relevant-object ratio, mean relevance, irrelevant-object ratio and interacting-object ratio independently of occupancy.

The checked-in benchmark intentionally reports a limitation: high-tier wall relevance remains the weakest metric. The accepted boards pass their configured participation bounds, but those bounds must be tightened after a larger benchmark and human review; the benchmark is not campaign-promotion evidence.

## Dense-but-trivial rejection

Medium+ candidates cannot pass merely by meeting occupancy. Certification still requires the production solver, complete V4 analysis, Quality V2, dependency/polarity/order/consequence targets, greedy and random resistance, object participation, LOS, arrow blockers and deterministic fingerprints.

There is also an explicit `dense-but-trivial` rejection when a board is at or above target occupancy while all greedy policies solve, at least 95% of successful choices are safe, and meaningful failure is below 2%.

## Determinism and scale

The offline task is configurable:

```text
./gradlew generateD21SpatialDensityCandidates \
  -Pd21CandidatesPerProfile=1000 \
  -Pd21AttemptsPerCandidate=500 \
  -Pd21Seed=6210001
```

Generation is sequential and bounded by candidate-attempt, solver-state, V4-analysis and counterfactual caps. Accepted boards retain only certified staging content; runtime never receives the rejected pool. Exact and symmetry fingerprints are retained in sets, so duplicate checking scales linearly in accepted candidate count rather than comparing every board pair.

The checked-in audit is deliberately a small certification benchmark. A 1,000-per-profile run and later 10K/50K/100K runs are supported through configuration, but were not executed in D2.1. Truncation is never treated as certification.

## Safety

The D2.1 task:

- reads the canonical campaign only to record its byte hash and level count;
- writes candidates only to `docs/content/d2_1/staging/`;
- writes diagnostics only to `docs/development/`;
- verifies the campaign bytes before and after generation;
- is not part of normal `build`;
- has no promotion behavior.

Difficulty V4 scoring and weights are unchanged. Generator V4 and the original V5 profiles remain available. The final campaign is not modified by this phase.
