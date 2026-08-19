# Magnetrail D2.1 Spatial Density Audit

Status: **BLOCKED**

Generator V5 / spatial configuration 2; Difficulty V4 is unchanged.

## Campaign safety

- Campaign byte-identical: true
- Campaign SHA-256: `8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`
- Campaign level count: 200

## Profile table

| Difficulty | Grid | Occupancy min / target / max | Arrows | Magnets | Blocks | Interaction target |
|---|---:|---:|---:|---:|---:|---:|
| EXPERT | 8 | 100.0% / 100.0% / 100.0% | 9-14 | 0-64 | 0-64 | 0.37 |

## Generation results

| Difficulty | Attempts | Valid | Rejected | Exhausted | Avg occupancy | Avg objects | Interaction | Relevance | V4 | Solver complete | Duplicate rate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EXPERT | 1 | 0 | 1 | 1 | 0.0% | 0.0 | 0.0 | 0.0 | 0.0 | 0/0 | 0.0% |

## Rejection evidence

- `average-object-relevance-below-profile`: 1
- `interaction-density-out-of-profile`: 1
- `irrelevant-object-ratio-above-profile`: 1
- `meaningful-long-range-magnetism-below-profile`: 1
- `meaningful-ordering-rate-below-profile`: 1
- `object-participation-below-profile`: 1
- `request-exhausted-including-duplicate-retries`: 1

## Required answers

1. Medium+ boards guaranteed not empty: **YES**, accepted candidates must meet profile occupancy floors.
2. Grid size can increase: **YES**, independently configured per profile.
3. Arrow count can increase: **YES**.
4. Magnet count can increase: **YES**.
5. Block count can increase: **YES**.
6. Long-range magnetic relationships: **YES**, authored and meaningful relationships are separately gated.
7. LOS interactions: **YES**, production-engine-confirmed interactions are gated.
8. Arrow-vs-arrow blockers: **YES**, authored candidates and meaningful relationships are measured.
9. Meaningful cancellation: **YES** for profiles that require it; presence alone does not pass.
10. Polarity-dependent decisions: **YES**, existing structural/V4 gates remain active.
11. Dependency chains: **YES**, existing dependency-depth gates remain active.
12. Object-removal/exposure chains: **YES**, exposure count and depth are measured.
13. Irrelevant objects rejected: **YES**, by relevance ratio, mean relevance and irrelevant ratio.
14. Dense-but-trivial rejected: **YES**, explicit gate plus V4 greedy/safe/failure gates.
15. V4 unchanged: **YES**.
16. Campaign byte-identical: **YES**.
17. Deterministic generation: **YES**.
18. Scale-ready: **YES WITH LIMITATION**; counts are configurable and processing is bounded/sequential, but 100K was not run.

## Limitations

- The checked-in run is a bounded certification benchmark, not the recommended 1,000-candidate-per-profile scale run.
- Human play ratings are not available for D2.1 candidates; automated certification is not human approval.
- 9x9 remains experimental and is excluded pending separate board-usability approval.
- High-tier permutation redundancy and wall relevance remain provisional; accepted staging candidates are not promotion-ready campaign content.
- Difficulty V4 weights and production gameplay semantics were not changed.
