# Generator V6 command and result log

Run date: 2026-08-21 (Asia/Kolkata)

## Five-band completion — 2026-08-22

Master was removed from V6 by owner authorization. The following results supersede the earlier
six-bucket refinement history retained below.

| Command | Result |
|---|---|
| `./gradlew :game-core:test --tests 'com.rameshta.magnetrail.core.generation.v6.*' :level-tools:test --tests 'com.rameshta.magnetrail.tools.GeneratorV6PromotionTest' :app:testDebugUnitTest --tests 'com.rameshta.magnetrail.HumanPlaytestTest'` | PASS; five-band ordinal/model, V6 generation, promotion guard, and debug playtest tests. |
| `./gradlew :level-tools:probeGeneratorV6 --rerun-tasks` | PASS; calibration probe accepted 30/30 with all uniqueness and behavioural gates active. |
| Repeated `./gradlew :level-tools:generateGeneratorV6Pilot --rerun-tasks` | PASS; final calibration accepted 30/30 at `acc1742...cd17`; final sealed validation accepted 30/30 at `ab0058...89df`. A second complete run produced identical catalog and audit bytes. |
| Cross-catalog fingerprint intersection audit | PASS; zero exact, D4, causal-hypergraph, quotient-decision-DAG, and solution-policy overlaps. |
| `./gradlew :level-tools:certifyGeneratorV6Pilot :level-tools:calibrateHumanLikeDifficultyV1 :level-tools:validateGeneratorV6HumanCertificate -x :level-tools:generateGeneratorV6Pilot` | Pilot certification PASS; both human tasks correctly returned `AWAITING_HUMAN_CALIBRATION` and fitted no parameters. |
| `./gradlew test lint assembleRelease :app:verifyGeneratorV6ReleaseExclusion` | PASS in 2m58s after correcting a stale 1–6 fairness fixture; 104 tasks, 178 core tests plus app/tools suites, lint, R8 release assembly, and release exclusion passed. |
| `./gradlew :app:assembleDebug -Pv6PlaytestCatalog=sealed-validation --rerun-tasks` | PASS; isolated sealed debug APK contained catalog hash `ab0058...89df`. |
| `./gradlew :app:assembleDebug -Pv6PlaytestCatalog=calibration --rerun-tasks` | PASS; initial five-band calibration debug APK contained catalog hash `acc174...cd17`. Superseded by the verified handoff build below. |
| `./gradlew :app:testDebugUnitTest --tests 'com.rameshta.magnetrail.HumanPlaytestTest' :app:assembleDebug -Pv6PlaytestCatalog=calibration --rerun-tasks` | PASS in 15s after fixing the V6 session planner to select the catalog contract of six boards per band instead of the historical V11 default of ten. Final calibration APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `2beb6295c9f431c64c5f3af5c4d76fe6c21a637a52f3aed3e04c49231e8fbcf5`; embedded catalog SHA-256 `acc1742e921d73a7661756259239757052b95499161b3927ce8f9e9236f2cd17`. |
| `./gradlew :level-tools:generateGeneratorV6ProductionCandidates -x :level-tools:validateGeneratorV6HumanCertificate` | Expected refusal: `HUMAN_MODEL_CERTIFIED artifacts are absent`; no production candidate was written. |
| `./gradlew :level-tools:auditGeneratorV6Baseline --rerun-tasks` | PASS; `BASELINE_VERIFIED`, 2,889 protected boards and two representative clone groups. |
| Final `shasum -a 256` and `git diff --check` | PASS; Campaign, Infinite, Daily, and V11 match protected hashes; no whitespace errors. |

## Earlier six-bucket/refinement history

| Command | Result |
|---|---|
| `find .. -name AGENTS.md -print` and targeted repository inspection | No applicable `AGENTS.md`; unrelated dirty worktree preserved. |
| `sha256sum docs/Magnetrail_Campaign_Levels_v3.json docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json docs/Magnetrail_Daily_Fallbacks_v1.json docs/content/v11_pilot/V11_PILOT_CATALOG.json` | All four expected protected hashes matched. |
| `./gradlew --configuration-cache :game-core:compileKotlin` | PASS. |
| `./gradlew --configuration-cache :level-tools:auditGeneratorV6Baseline` | PASS; `BASELINE_VERIFIED`, 2,889 protected boards and two representative clone groups. |
| `./gradlew --configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.rameshta.magnetrail.HumanPlaytestTest' :game-core:test --tests 'com.rameshta.magnetrail.core.generation.v6.*' :level-tools:compileKotlin` | PASS. |
| First `./gradlew --configuration-cache :level-tools:generateGeneratorV6Pilot` | Intentionally interrupted before artifacts when the provisional 24×5-second per-board bound proved operationally excessive. No completed artifact was used. |
| Second `./gradlew --configuration-cache :level-tools:generateGeneratorV6Pilot` | Calibration evaluated 36/36 and accepted 0; report-sidecar serialization then failed. The complete audit/catalog survived; serializer fixed before sealed execution. |
| Direct sealed `:level-tools:run --args='generate-generator-v6-pilot ... --kind=validation'` | PASS as a bounded execution; sealed accepted 0/36 and emitted `FAIL_NO_PROMOTION`. |
| `./gradlew --configuration-cache :level-tools:calibrateHumanLikeDifficultyV1` | PASS as a gate; emitted `AWAITING_HUMAN_CALIBRATION`, no model parameters. |
| Final `./gradlew --configuration-cache :level-tools:generateGeneratorV6Pilot` | PASS as an execution in 5m01s; deterministic final implementation evaluated 72/72, calibration 0/36 and sealed 0/36, both `FAIL_NO_PROMOTION`. |
| `./gradlew --configuration-cache :game-core:test --tests 'com.rameshta.magnetrail.core.generation.v6.*' :app:testDebugUnitTest --tests 'com.rameshta.magnetrail.HumanPlaytestTest' :app:verifyGeneratorV6ReleaseExclusion` | PASS. |
| `./gradlew --configuration-cache :level-tools:test --tests 'com.rameshta.magnetrail.tools.GeneratorV6PromotionTest'` | PASS; verified hash guards, reuse of a pre-existing interrupted rollback snapshot, and atomic replacement of a temporary target. |
| `./gradlew --configuration-cache test lint assembleRelease :app:verifyGeneratorV6ReleaseExclusion` | PASS in 3m02s; 104 tasks, all unit suites, Android lint, R8 release assembly and debug-pilot release exclusion passed. |
| `./gradlew --configuration-cache :level-tools:certifyGeneratorV6Pilot -x :level-tools:generateGeneratorV6Pilot` | Expected FAIL: calibration audit is not `V6_TECHNICALLY_CERTIFIED`. |
| `./gradlew --configuration-cache :level-tools:generateGeneratorV6ProductionCandidates -x :level-tools:validateGeneratorV6HumanCertificate` | Expected FAIL: `HUMAN_MODEL_CERTIFIED artifacts are absent`. |
| `git diff --check` | PASS. |
| Final protected `sha256sum` | All four hashes still matched; no production catalog changed. |
| `./gradlew :game-core:test --tests '*GeneratorV6CoreTest*' --tests '*HumanLikeDifficultyV1Test*'` after refinement 2 | PASS; verified easiest-policy quotient grading, ID-invariant policies and contract-derived rectangular realization. |
| Repeated `./gradlew :game-core:test --tests '*V6ConstructionProbeTest*' --info` bounded construction probes | PASS; temporary probe code measured linked trap modules and was removed after its conclusions became focused production code/tests. |
| `./gradlew :game-core:test --tests '*V6ConstructionProbeTest.bounded random structural probe*' --info` | Bounded diagnostics found behavioral Expert/Master examples, then rejected them from certification for low-budget policy success and, for Master, inert walls. No probe artifact entered a catalog. |
| `./gradlew :game-core:test --tests '*V6ConstructionProbeTest.bounded linked-core augmentation probe*' --info` | PASS as a diagnostic; found a compact bucket-4 layout and no qualifying bucket-5 augmentation within the frozen space. Temporary probe removed. |
| `./gradlew :level-tools:probeGeneratorV6` after refinement 2 | PASS as a bounded diagnostic; accepted 1/36 calibration boards and retained 35 counterexamples. |
| `./gradlew :level-tools:generateGeneratorV6Pilot` after refinement 2 | PASS as an execution in 12s; calibration 1/36 (`f95a8a...e1307`) and sealed validation 1/36 (`d4d7ee...fb19`), both `FAIL_NO_PROMOTION`. |
| Second refinement-2 regeneration plus aggregate SHA-256 comparison | PASS in 10s; staging sidecar aggregate remained byte-identical at `02e6d9af35d9940c596f284a82cff99804225da80cde8b36be871e111127189c`. |
| `./gradlew :level-tools:calibrateHumanLikeDifficultyV1` | PASS as a refusal gate; `AWAITING_HUMAN_CALIBRATION`, with no parameters fitted. |
| Refinement-2 focused V6/core/app/promotion/release-exclusion test command | PASS in 9s; all selected JVM, Android debug playtest, promotion-guard and release-exclusion tests passed. |
| `./gradlew :level-tools:certifyGeneratorV6Pilot -x :level-tools:generateGeneratorV6Pilot` after refinement 2 | Expected FAIL: calibration audit remains `FAIL_NO_PROMOTION`; the guard refused certification. |
| `./gradlew test lint assembleRelease :app:verifyGeneratorV6ReleaseExclusion` after refinement 2 | PASS in 3m27s; 104 tasks, all unit suites, Android lint, R8 release assembly and release-asset exclusion passed. |

No device, emulator, manual gameplay or human participant testing was performed.
