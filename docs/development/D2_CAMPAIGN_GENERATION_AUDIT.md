# D2 Campaign Generation Audit

Status: **PROMOTED BY OWNER DIRECTIVE; HUMAN CALIBRATION PENDING**

Generator V5 produced 200 production-engine-certified candidates in an isolated catalog. The
staging generation itself did not modify production; the later guarded promotion replaced all 200
boards under their existing stable production IDs.

## Safety

- Source campaign SHA-256: `1852f5eee4792cf937adb17d7443041bd879486cbf33d3e7294d09542eef6ec8`
- Source level count: 200
- Campaign changed by staging generation: **NO**
- Campaign changed by guarded promotion: **YES**
- Runtime generation: **NO**
- Guarded automated promotion execution: **YES**
- Production content/generator: **7 / 5**
- Stable production IDs preserved: **200 / 200**
- Truncated promoted boards: **0**

## Candidate summary

- Generated/certified: 200/200
- Rejected attempts before acceptance: 83591
- Truncated candidates: 0
- Difficulty distribution: {EASY=35, EXPERT=40, HARD=55, MASTER=13, MEDIUM=45, TUTORIAL=12}
- Grid distribution: {3x3=2, 4x4=16, 5x5=76, 6x6=27, 7x7=36, 8x8=43}

## Structural aggregate

| Metric | D2 candidates |
|---|---:|
| Safe-choice ratio | 0.8252 |
| Meaningful failure rate | 0.1748 |
| Harmful decision density | 0.7325 |
| Relevant-object ratio | 0.7245 |
| Interaction density | 0.3288 |
| Dependency depth | 4.1700 |
| Polarity impact depth | 3.4600 |
| Cancellation-relevant level rate | 0.5700 |
| Ordering depth | 2.5050 |
| Consequence depth | 4.2050 |
| Greedy solved rate | 0.4283 |
| Random-success rate | 0.4742 |
| Permutation redundancy | 0.8432 |

## Old versus new

| Metric | Old 200 | D2 | Status |
|---|---:|---:|---|
| meaningfulFailureRate | 0.0139 | 0.1748 | MEASURED |
| safeChoiceRatio | 0.9861 | 0.8252 | MEASURED |
| harmfulDecisionDensity | 0.13 | 0.7325 | MEASURED |
| objectRelevance | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 0.7245 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| interactionDensity | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 0.3288 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| dependencyDepth | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 4.1700 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| polarityImpactDepth | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 3.4600 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| cancellationRelevance | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 0.5700 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| orderingDepth | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 2.5050 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| consequencePersistence | NOT MEASURABLE WITH CURRENT IMPLEMENTATION | 4.2050 | NEW_ONLY; OLD STRUCTURAL ANALYZER DATA UNAVAILABLE |
| greedySolveRate | 0.9200 | 0.4283 | MEASURED |
| randomSuccessRate | 0.9272 | 0.4742 | MEASURED |
| strategyFamilyCount | 633 | 281 | MEASURED |
| permutationRedundancy | 0.991 | 0.8432 | MEASURED |

## Promotion result

The project owner explicitly directed promotion without another approval checkpoint. Migration was
proven with stable IDs and board-fingerprint record archival before replacement. Automated
certification remains distinct from human approval; the promoted boards have zero human ratings.
See `docs/content/d2/promotion/D2_PROMOTION_RESULT.md` and `D2_ID_MIGRATION.json`.

## Limitations

- Human ratings are not available for D2 candidates; automated certification is not human approval.
- Existing-player progress migration is proven for the stable-ID board-revision policy.
- 9x9 is experimental and excluded from the staging campaign pending separate usability approval.
- Human-perceived obviousness and visual fairness are NOT MEASURABLE WITH CURRENT IMPLEMENTATION.
