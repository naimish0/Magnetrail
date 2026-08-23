# Magnetrail Expert/Master Topology V5.1

Date: 2026-08-20  
Status: **PARTIAL SUCCESS — STRUCTURE IMPROVED BUT CERTIFICATION STILL BLOCKED**

## Scope and decision

This was a bounded iteration on `EXPERT_ORDERED_POLARITY_V1`. Difficulty V4, the production
engine, certification thresholds, occupancy, the canonical campaign, and the Tutorial through
Very Hard constructor paths were not changed.

Expert now extends its existing top-row causal corridor with `eastGate` and an opposing
`eastController`. Two wall-shielded competing fields connect that extension to the same causal
component. The resulting board physically verifies a third distance-four-or-greater controller
relationship and one additional wall occlusion without adding an independent mini-puzzle.

The corresponding Master extension was not retained. A one-action corridor extension preserved
ordering depth 5 and improved interaction density to `0.0337`, but Difficulty V4 correctly marked
the analysis incomplete with `COUNTERFACTUAL_OBJECT_SEQUENCE_ENUMERATION_CAP`. A no-extra-action
vertical-controller variant failed the physical semantic pre-check because it competed with the
existing C-stage controller. Master was restored to the last complete, non-truncated V5 topology.

## Before and after

| Metric | Previous V5 Expert | V5.1 Expert | Previous V5 Master | V5.1 Master |
|---|---:|---:|---:|---:|
| Certified | 0 | 0 | 0 | 0 |
| Difficulty V4 score | 59 | 61 | 66 | 66 |
| Ordering depth | 3 | 3 | 5 | 5 |
| Long-range relationships | 2 | 3 | 1 | 1 |
| Safe-choice ratio | 0.6619 | 0.5996 | 0.5154 | 0.5154 |
| Relevant-object ratio | 0.2031 | 0.2344 | 0.2500 | 0.2500 |
| Average relevance | 0.0538 | 0.0604 | 0.0615 | 0.0615 |
| Wall participation count | 2 | 3 | 2 | 2 |
| Interaction density | 0.0248 | 0.0303 | 0.0283 | 0.0283 |
| Mandatory-ordering ratio | 0.1944 | 0.2000 | 0.4444 | 0.4444 |
| V4 complete | Yes | Yes | Yes | Yes |

The previous Master relevance figures were measured during this audit because the V5 comparison
did not record them. The final topology is restored to the complete V5 construction, so its
before/after values are identical.

## Physical verification and rejection report

The final Expert candidate has 64 authored objects. Its physical interaction graph contains 210
typed edges, 47 connected components, a largest component of 18 objects, and 46 isolated objects.
All 9 declared construction edges are physically verified before and after the deterministic
transform. `eastController` physically controls `must0` after the existing corridor clears while
`reveal0` still shields the west controller.

The focused benchmark evaluated one final deterministic candidate per profile:

| Profile | Attempts | Constructed | Solved | V4 complete | Certified |
|---|---:|---:|---:|---:|---:|
| Expert | 1 | 1 | 1 | 1 | 0 |
| Master | 1 | 1 | 1 | 1 | 0 |

Expert had one rejection at each of: `interaction-density-out-of-profile`,
`object-participation-below-profile`, `interacting-object-ratio-below-profile`,
`average-object-relevance-below-profile`, and `participating-wall-ratio-below-profile`.

Master had one rejection at each of: `object-participation-below-profile`,
`interacting-object-ratio-below-profile`, `average-object-relevance-below-profile`, and
`participating-wall-ratio-below-profile`.

The former Expert failures for fewer than three long-range relationships and ordering below `0.20`
are resolved. Certification remains blocked because most of the 64-cell shell is still outside the
main causal component. The safe next change is not another action: it must make existing static
objects causally relevant without increasing winning-sequence counterfactual cost.

## Validation

```text
./gradlew --configuration-cache :game-core:test \
  --tests 'com.rameshta.magnetrail.core.generation.v5.SolutionFirstConstructionTest'
```

Result: `BUILD SUCCESSFUL` in 3m 32s; configuration cache reused.

```text
./gradlew --configuration-cache build certifyCampaignContent
```

Result: `BUILD SUCCESSFUL` in 5m 43s with 281 actionable tasks (43 executed, 238 up-to-date),
configuration cache reused. Certification passed for all 200 campaign levels and seven Daily
fallbacks.

Campaign SHA-256 remained:

```text
8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5
```

**PARTIAL SUCCESS — STRUCTURE IMPROVED BUT CERTIFICATION STILL BLOCKED**
