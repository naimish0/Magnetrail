# Generator V6.1 execution log

Run date: 22 August 2026 (Asia/Kolkata)  
Git revision: `01d9f3c7ce92d6922b3afd0b4a228d1767f33652` with a pre-existing dirty working tree  
Final campaign result: `AUTOMATED_CAMPAIGN_REJECTED`  
Production mutation: none

## Protected inputs

```text
8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9  docs/Magnetrail_Campaign_Levels_v3.json
c3500d87eedfa7cdbfdde1000bf49ae9af5c2a0e0c24c4ce5c7d977b5d6b4f83  docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json
3f21a633fa1e51d29938b830fe567103a52354e367d3177c20b8196c881156c8  docs/Magnetrail_Daily_Fallbacks_v1.json
035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184  docs/content/v11_pilot/V11_PILOT_CATALOG.json
```

The production campaign parsed as 2,205 levels, content version 10, generator version 5, and rules contract `magnetrail-core-1`. Levels 1–12 are the canonical tutorial range. The same hashes were rechecked after rejected staging generation; promotion was never attempted.

## Commands and results

| Command | Exit | Duration/result |
|---|---:|---|
| `./gradlew :level-tools:test --tests com.rameshta.magnetrail.tools.GeneratorV6PromotionTest --console=plain` | 0 | 2 s; rejected-certificate non-mutation and prior V6 promotion tests passed. |
| `./gradlew :game-core:test --tests '...interacting-chain Easy realization uses only witnessed controllers' --console=plain` | 0 | 3 s; delayed-trap Easy used three arrows, two witnessed magnets, no walls/filler. |
| `./gradlew :game-core:test --tests '...constraint guided Easy realization accepts independent deterministic seeds without filler' --tests '...interacting-chain Easy realization uses only witnessed controllers' --console=plain` | 0 | 2 s; specialized handoff path and delayed-trap repair both passed. |
| `./gradlew :game-core:test --tests '...later delayed-trap Hard families use witnessed cancellation branches' --console=plain` | 0 | 5 s; both 16-action Hard cancellation-branch topologies passed conjunctive evidence. |
| Direct bounded staging refinements (`maximum-attempts=64`) | 0 | 68 boards/Level 81, then 72/Level 85, 80/Level 93, and finally 81/Level 94; no gate changed. |
| `./gradlew :level-tools:generateGeneratorV61ProductionCandidates --console=plain` | 0 | 11 m 29 s; 80 Gradle tasks, mandatory prerequisites passed, then official staging reproduced 81/2,193 and Level 94 exhaustion. |
| `./gradlew :game-core:test --tests 'com.rameshta.magnetrail.core.generation.v6.AdversarialDifficultyV61Test' :level-tools:compileKotlin --console=plain` | 0 | 35 s; active V6.1 regressions passed and exploratory synthesis traces remained explicitly skipped. |
| `./gradlew :game-core:test --tests '...wrong now Expert avoids the preceding Expert policy and behavior cluster' --console=plain` | 0 | 9 s; archive- and cluster-aware Level 52 refinement passed. |
| `./gradlew :app:testDebugUnitTest --tests 'com.rameshta.magnetrail.AutoJourneyTest' :game-core:test --tests '...campaign admission rejects a forbidden recent strategy cluster' :level-tools:test --tests 'com.rameshta.magnetrail.tools.GeneratorV6PromotionTest' --console=plain && git diff --check` | 0 | 6 s; durable runtime pacing, cluster admission, and rejected-promotion non-mutation passed. |
| `./gradlew :app:verifyReleaseReadinessLocal --console=plain` | 0 | 1 m 5 s; 98 tasks, R8/minification, structural AAB, lint, privacy, manifest, tests, and debug-catalog exclusion passed. |
| `./gradlew :level-tools:benchmarkGeneratorV61AutoJourney --console=plain` | 0 | 8 s; five bounded host rows regenerated; not a device thermal/UI benchmark. |
| `./gradlew :game-core:test --tests '*ExpertLogicalSynthesisV61Test'` | 0 | 1 s final run; 48/48 exact canonical logical graphs were non-isomorphic and deterministic. An earlier run found 7/48 collisions and failed before graph structure was corrected. |
| `./gradlew :game-core:test --tests '*AdversarialDifficultyV61Test*Expert*'` | 0 | 23 s; graph-first Expert realization and archive-aware policy regressions passed. |
| `./gradlew :level-tools:proveGeneratorV61ExpertCapacity --no-configuration-cache` | 0 | 9 m 20 s; 24/24 archive-aware Expert boards passed with zero rejections. |
| `./gradlew :level-tools:verifyGeneratorV61ExpertCapacity --no-configuration-cache` | 0 | 1 s; proof/catalog/audit hashes and source freshness passed. |
| First `caffeinate -i ./gradlew :level-tools:generateGeneratorV61ProductionCandidates ...` | 1 | 19 s; Gradle 9 detected an undeclared docs-resource task-order hazard before generation. `game-core:processTestResources` ordering was fixed; no content was generated or promoted. |
| Intermediate `caffeinate -i ./gradlew :level-tools:generateGeneratorV61ProductionCandidates --no-configuration-cache -Pv61WorkingTreeIdentity=local-dirty-worktree-2026-08-22` | 0 | 10 m 50 s; accepted 85/2,193, but a post-run audit found one solution-policy collision with the new capacity sample. This staging output was superseded. |
| Final archive-bound `caffeinate -i ./gradlew :level-tools:generateGeneratorV61ProductionCandidates --no-configuration-cache -Pv61WorkingTreeIdentity=local-dirty-worktree-2026-08-22-capacity-archive-bound` | 0 | 11 m 1 s; capacity audit was an explicit comparison input, all cross-archive collisions were zero, and staging rejected at Level 98 Super Hard after 85/2,193. |
| `shasum -a 256 ...` | 0 | Reverified protected and generated artifact hashes below. |

The certification and promotion tasks were not invoked in the final iteration because the machine-readable certificate is explicitly rejected. Earlier negative-path tests prove that a rejected certificate cannot mutate production.

## Test evidence

The final campaign prerequisite run produced the following current XML totals:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `game-core:test` | 236 | 0 | 0 | 31 |
| `level-tools:test` | 44 | 0 | 0 | 0 |
| `app:testDebugUnitTest` | 119 | 0 | 0 | 0 |
| `app:testReleaseUnitTest` (current retained XML) | 118 | 0 | 0 | 0 |
| Total executions | 517 | 0 | 0 | 31 |

The 31 skips are bounded exploratory synthesis traces retained as diagnostics; focused production regressions are active. `compileDebugAndroidTestKotlin`, release Kotlin compilation, merged-manifest verification, release-pilot exclusion, release lint, R8, and bundle packaging passed. No connected/emulator instrumentation test was executed.

## Generation result

The graph-first prerequisite proof accepted 24/24 Expert boards against 3,000 existing fingerprints.
All 24 met at least six visible decisions, three delayed traps (observed minimum six), three phases,
two cross-chain dependencies, inferability 1.0, cheap-policy solve rate at most 10% (observed maximum
0.0), forced run at most three, and complete non-truncated replay. Logical synthesis, extracted
causal, decision-DAG, and solution-policy fingerprints were 24/24 distinct.

Only then did the official bounded production run start. It accepted 85 V6.1 boards, passed the
former Level 94 Expert ceiling, and stopped fail-closed at the first new unfillable slot:

```text
AUTOMATED_CAMPAIGN_REJECTED: generated 85/2193
level=98 band=Super Hard familyIndex=16
topology=SHARED_MAGNET_PARITY_BRAID variant=2 attempts=64
REJECT_NEAR_SEMANTIC_DUPLICATE=49
REJECT_STRATEGY_CLUSTER_PACING=15
```

No gate was weakened. The candidate JSON contains 12 preserved tutorials plus 85 staging boards and is not a promotable campaign. Accepted V6.1 counts are Easy 12, Medium 22, Hard 27, Super Hard 16, Expert 8; Master is owner-removed/historical-only. The accepted partial archive has zero exact, D4, causal-graph, decision-DAG, solution-policy, or above-threshold near-semantic duplicates. All eight accepted campaign Expert synthesis graphs are distinct, and all comparable collision counts between the capacity sample and campaign prefix are zero.

## Final generated hashes

```text
e0ab606a90290ddb12c4d795673be70744052c54b6c7d1562a8e2b7f1294ddcd  GENERATOR_V61_AUTOMATED_CERTIFICATE.json
3ceecf39799ec4fc4408497d4e0c39c18177509aea9ea20c0d3eb59d572a29ed  GENERATOR_V61_CAMPAIGN_AUDIT.json
9c2d70349a4674fbedd6f2e8668a8b5c3efbe8f76f79f76b2f69763c56c761b5  GENERATOR_V61_CAMPAIGN_CANDIDATE.json
4a8050e83b8d1097453fdc68ec7813f6675df3a15605749911b25d69a359116c  GENERATOR_V61_GENERATION_REPORT.md
a57a1709c0f82bd174291a472f44c42dcf32131a9548f0cea881aa980fdd1624  GENERATOR_V61_MANIFEST.json
72cb0336277cbbc7b26bdd1b4189dff6651a164ed3ff0a0c4c3cd48779f1575f  GENERATOR_V61_EXPERT_CAPACITY_AUDIT.json
4d237b78213dd5e46adaadbb2838bdf0e0d00dde2c0a864cc7f23684f06c26b4  GENERATOR_V61_EXPERT_CAPACITY_CATALOG.json
3f07b6aa6bb2838c8a089e64bb44d75c658ef10f78b1d14369df49b84c4a531c  GENERATOR_V61_EXPERT_CAPACITY_PROOF.json
b31fcd6305936f8619d1619454c7f638eff3b81c6bb827692f1ecd3d2d9451c1  GENERATOR_V61_EXPERT_CAPACITY_PROOF.md
55d4d1171f69a72bbe0e9029514c4a764b6253b9f63f7117c1218274d2952eb3  GENERATOR_V61_EXPERT_CAMPAIGN_CROSS_AUDIT.json
afecea034b9c5b2d66ce43f9ea551ab409836e2f7163f71d5084a8d9cd972b55  GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json
3e40a4e6c8891abae429a155679e8e4e77058f4ccc2d4c741ce827ca74ec44a7  GENERATOR_V61_HARD_NEGATIVE_REGRESSION.md
88e63e4ed2966fb80f7f62ba968bbbf42197053c82def4f417760364dcbb2809  GENERATOR_V61_AUTO_JOURNEY_HOST_BENCHMARK.json
4c4c57db5c80ff7503c4ad06fc426d1faed8caf1ab08586a2d0c4bcbbc4a5ec2  GENERATOR_V61_AUTO_JOURNEY_HOST_BENCHMARK.md
7f14784fae3fcd41e08d01cbd954936b1acf259b6d950be40c02fd3ed2ec10a5  app-release.aab
3054bc633286f3d254c9e444e70f79d58badd390805a26a7f724fdb75df7f357  mapping.txt
fc8f0cb5a81e109e2f639fe01175b825ade70e0324e4046352e84f87ac092694  merged release AndroidManifest.xml
```

The certificate and benchmark timestamps change when rerun; their hashes bind the recorded executions. The capacity-gated campaign run is the first run with the graph-first Expert synthesizer; its Level 98 Super Hard blocker is newly observed and was not inferred from the earlier Level 94 failure.

## Work not performed

- No campaign promotion, rollback, commit, push, PR, signing, Play upload, or rollout.
- No connected-device/manual playtest, Auto Journey thermal test, or rendered UI responsiveness benchmark.
- No claim of human validation. V6 human observations are used only as a frozen rejection/regression corpus.
