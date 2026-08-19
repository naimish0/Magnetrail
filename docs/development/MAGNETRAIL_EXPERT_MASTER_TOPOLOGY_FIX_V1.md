# Magnetrail Expert/Master Topology Fix v1

Date: 2026-08-20  
Status: **PARTIAL SUCCESS — STRUCTURE IMPROVED BUT CERTIFICATION STILL BLOCKED**

## Scope

This was a bounded, Expert/Master-only Generator V5 change. It did not change the canonical
campaign, gameplay, Difficulty V4, certification thresholds, or the Easy through Very Hard
profiles. The failed empty-cell experiment was not retained; both new boards remain at the
profiles' original 100% authored occupancy.

## Implementation

Generator V5 now selects the deterministic internal topology family
`EXPERT_ORDERED_POLARITY_V1` only for `v5-d2.1-expert` and `v5-d2.1-master`.

The topology constructs actual production-engine relationships:

- an early successful reveal flips a controller and makes a must-first arrow fatal;
- each reveal also blocks or exposes the following must-first arrow;
- `revealA` changes the controller state required to remove `gateA`;
- removing `gateA` exposes long-range controller relationships;
- a wall prevents a competing field from reaching `mustB`, producing two measured wall
  occlusion relationships;
- Master adds a fourth must/reveal stage rather than merely adding occupancy.

The existing physical semantic verifier checks the declared exposure, state, polarity, and
long-range relationships before and after deterministic geometry transformation. Canonical replay,
complete solver proof, and complete Difficulty V4 analysis remain mandatory.

## Baseline versus focused result

| Metric | Previous | New Expert | New Master |
|---|---:|---:|---:|
| Certified | 0 | 0 | 0 |
| Difficulty V4 score | 5 | 59 | 66 |
| Ordering depth | 0 | 3 | 5 |
| Mandatory-ordering ratio | 0.0000 | 0.1944 | 0.4444 |
| Safe-choice ratio | 0.9372 | 0.6619 | 0.5154 |
| Wall occlusion participation | 0 | 2 | 2 |
| Solvable | Yes | Yes | Yes |
| V4 complete | Yes | Yes | Yes |

The new topology therefore fixes the original failure mode: it no longer has zero ordering depth,
near-universal safe choices, or zero wall participation. Expert and Master are also structurally
different: Expert has ordering depth 3; Master extends the causal chain to depth 5.

## Focused certification result

The run was intentionally stopped after one deterministic candidate per profile because the new
topology proved the requested structural change but still failed unchanged gates.

| Profile | Attempts | Constructed | Solved | V4 complete | Certified |
|---|---:|---:|---:|---:|---:|
| Expert | 1 | 1 | 1 | 1 | 0 |
| Master | 1 | 1 | 1 | 1 | 0 |

Expert first stops at the authored spatial gate because it physically verifies two long-range
relationships while the unchanged profile requires three. Its direct structural diagnostic also
remains below later Expert targets: interaction density `0.0248`, relevant-object ratio `0.2031`,
average relevance `0.0538`, and ordering `0.1944` versus the `0.20` target. The two participating
wall relationships are real, but their ratio across the fully occupied board remains too low.

Master passes the ordering/V4 failure that blocked the old topology. Its remaining rejection
reasons are:

- `object-participation-below-profile`
- `interacting-object-ratio-below-profile`
- `average-object-relevance-below-profile`
- `participating-wall-ratio-below-profile`

No threshold was changed to obtain these results. No large seed matrix was run after the focused
gate failures were known.

## Regression and safety

- Expert/Master selection is based on exact profile IDs, so Easy, Medium, Hard, and Very Hard keep
  their existing constructor paths.
- Occupancy relaxation is absent.
- The topology remains deterministic from the existing seed and transform path.
- Semantic edges are verified on the physical board before and after transformation.
- The repair loop has no filler mutation operator for this topology.
- Gameplay, Generator V4, Difficulty V4, and certification gates were not modified.
- The production campaign remains unchanged at SHA-256
  `8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`.

Validation commands:

```text
./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest'

./gradlew --configuration-cache build certifyCampaignContent
```

Both passed. The full build completed in 4 minutes 38 seconds with 281 actionable tasks (49
executed, 232 up-to-date). It included game-core, level-tools, and app unit tests, Android lint,
debug/release/benchmark packaging, and certification of all 200 campaign levels plus seven Daily
fallbacks.

## Decision

The new topology is worth retaining as a focused architectural improvement, but it is not eligible
for promotion. The next bounded topology iteration must connect more of the 64 authored objects to
the causal graph, raise Expert from two to three physical long-range relationships, and increase
wall participation without adding independent actions that make V4 counterfactual analysis
truncate.

**PARTIAL SUCCESS — STRUCTURE IMPROVED BUT CERTIFICATION STILL BLOCKED**
