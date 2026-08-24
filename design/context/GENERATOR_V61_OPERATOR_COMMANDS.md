# Generator V6.1 operator commands

Run long commands on macOS as `caffeinate -i -- ./gradlew …` when the laptop would otherwise sleep.

## Fingerprints and regression

```bash
./gradlew :level-tools:auditGeneratorV6Baseline
./gradlew :level-tools:analyzeGeneratorV61Regression
./gradlew :game-core:test --tests 'com.rameshta.magnetrail.core.generation.v6.*'
```

## Focused generation

```bash
./gradlew :level-tools:probeGeneratorV61 -Pv61ProbeAttempts=8
```

## Expert capacity proof

```bash
caffeinate -i -- ./gradlew :level-tools:proveGeneratorV61ExpertCapacity \
  -Pv61ExpertCapacitySize=24 \
  -Pv61ExpertCapacityAttempts=64
./gradlew :level-tools:verifyGeneratorV61ExpertCapacity
```

The first command writes the catalog, per-board audit, and hash-bound proof under
`docs/content/generator_v6_1/capacity/expert/`. It fails unless all 24 archive-aware Expert boards
meet the full conjunctive Expert contract and have distinct synthesis, production-causal,
decision-DAG, and solution-policy graphs. The verifier also rejects proof artifacts older than any
V6 synthesis source. Complete campaign generation depends on this verifier and cannot reuse stale or
rejected capacity evidence.

## Phased campaign generation

```bash
caffeinate -i -- ./gradlew :level-tools:generateGeneratorV61Phase1Candidates \
  -Pv61Phase1Attempts=64 \
  -Pv61WorkingTreeIdentity="$(git rev-parse --verify HEAD)-local"
./gradlew :level-tools:certifyGeneratorV61Phase1Candidates

caffeinate -i -- ./gradlew :level-tools:generateGeneratorV61Phase2Candidates \
  -Pv61Phase2Attempts=64 \
  -Pv61WorkingTreeIdentity="$(git rev-parse --verify HEAD)-local"
./gradlew :level-tools:certifyGeneratorV61Phase2Candidates
```

Phase 1 writes only `docs/content/generator_v6_1/staging/phase-1/` and must certify exactly
1,325 Easy, Medium, and Hard slots. Phase 2 writes only
`docs/content/generator_v6_1/staging/phase-2/` and must certify exactly 868 Super Hard and Expert
slots. Phase 2 is archive- and pacing-aware of Phase 1, and cannot start until the Phase 1
certificate passes. Both phases retain the original player-facing level numbers, ordinals, and
family allocations from the unsplit schedule.

Any missing slot rejects only that phase and stops its remaining generation. The active schedule
contains Easy through Expert; Master reproduction is historical-only and returns
`REJECT_MASTER_BAND_OWNER_REMOVED` as documented in
`CORE_RULESET_DIFFICULTY_CEILING_REPORT.md`.

To run the two phases and final merge as one dependency chain, use:

```bash
caffeinate -i -- ./gradlew :level-tools:generateGeneratorV61ProductionCandidates \
  -Pv61Phase1Attempts=64 \
  -Pv61Phase2Attempts=64 \
  -Pv61WorkingTreeIdentity="$(git rev-parse --verify HEAD)-local"
```

## Certification

```bash
./gradlew :level-tools:mergeAndCertifyGeneratorV61ProductionCandidates
./gradlew :level-tools:certifyGeneratorV61ProductionCandidates
```

The merge task accepts only hash-bound `V61_PHASE_1_CERTIFIED` and `V61_PHASE_2_CERTIFIED`
artifacts with counts 1,325 and 868. It restores Levels 1–12 from the protected V10 source, sorts
both phase catalogs into Levels 13–2205, then recomputes global exact, D4, relevance-pruned D4,
causal-graph, decision-DAG, solution-policy, synthesis-graph, and near-semantic uniqueness. It also
runs whole-sequence pacing and independently replays the complete solution witness and content hash
for every one of the 2,205 boards.

The final certification task intentionally fails unless the staged certificate is exactly
`AUTOMATED_CAMPAIGN_CERTIFIED`, both phase gates are true, and `certifiedBoardCount` is 2,205.
Phase 1 runs the mandatory core/tool/app tests, release lint and compilation, Android-test
compilation, merged-manifest audit, and debug-pilot release-exclusion proof. Their result is carried
through both phase certificates and the final hash-bound certificate.

## Promotion

First calculate the three staged hashes, then invoke the guarded task:

```bash
./gradlew :level-tools:promoteGeneratorV61Campaign \
  -Pv61ExpectedCandidateSha=... \
  -Pv61CertificateSha=... \
  -Pv61ManifestSha=...
```

No manual approval property exists. Hashes only bind inputs; promotion still parses the certificate and requires every certification gate. The task cannot run after a rejected certification.

## Rollback

Promotion preserves `docs/content/generator_v6_1/rollback/Magnetrail_Campaign_Levels_V10_SOURCE.json`. To roll back, use a repository-reviewed atomic copy procedure that verifies the preserved file equals the recorded pre-promotion SHA before replacing the campaign pointer. There is deliberately no default automatic rollback task while production remains V10; do not create or overwrite the artifact after a failed/non-promoted run.

## Auto Journey deterministic reproduction

The identity inputs are generator `V6.1`, public salt `magnetrail-v6.1-auto-journey-public-salt-1`, ordinal, scheduled band, family allocation, and attempt. Reproduce host candidates through `GeneratorV61.reproduceIdentity`/`generate` with the same `V61GenerationRequest`; verify the persisted board hash and receipt before comparison. Runtime records are private DataStore data and should be exported only from a debug/test build.

```bash
./gradlew :level-tools:benchmarkGeneratorV61AutoJourney
```

This records bounded host wall/CPU/heap diagnostics for the five active bands. It does not substitute for Android device thermal and UI-responsiveness measurement. Disregard the older Master row in pre-removal benchmark artifacts.

## Privacy and release readiness

```bash
./gradlew :app:verifyPrivacyPolicyArtifacts
./gradlew :app:verifyReleaseReadinessLocal
```

`verifyReleaseReadinessLocal` is repository-local. A production release additionally requires protected signing values, verified AdMob ownership/console configuration, audience/Play declarations, publisher identity/contact, and a hosted HTTPS privacy-policy URL. Developer analytics/crash reporting is intentionally absent this release cycle.
