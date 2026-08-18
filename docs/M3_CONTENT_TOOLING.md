# Magnetrail M3 content tooling

M3 freezes content schema 2, content version 3, generator version 1, grading
version 1, economy version 1, and Daily generator version 1. Gameplay remains
`magnetrail-core-1` and is not reimplemented by tooling.

## Seed and construction contract

`SeededRandom` is the only generator PRNG. It is a frozen SplitMix64
implementation initialized from `seed xor stableFNV1a64(profileId)`. For a
fixed generator version, seed, profile, template bank, and request metadata,
the canonical JSON is identical.

The generator uses constrained, known-solution-preserving construction:

1. Select a reviewed, solver-certified template with the explicit PRNG.
2. Apply a seeded dihedral transform and bounded board embedding.
3. Add only bounded wall mutations that retain a known transformed solution.
4. Replay that path through `DefaultGameEngine`.
5. Independently solve and certify through the production `Solver` and engine.
6. Reject schema, profile, solver-cap, opening, mechanic, mutation, fingerprint,
   and near-duplicate failures.

It makes at most 64 intro, 96 developing, 128 advanced, or 48 Daily attempts.
Solver state caps are 2,000 / 8,000 / 20,000 / 8,000 respectively. Solution
count is capped at 32. Tool seed searches are also bounded; no loop is open-ended.

## Commands

Generate review candidates without changing shipped content:

```bash
./gradlew generateLevelCandidates \
  -PcandidateCount=12 \
  -PcandidateSeed=730000 \
  -PcandidateProfile=DEVELOPING_MEDIUM
```

Output is under `level-tools/build/m3-staging/`. Review its JSON and rejection
report in Git-independent staging.

Rebuild the deliberately selected stable campaign IDs and checked-in reports:

```bash
./gradlew promoteCampaignContent -PconfirmPromotion=true
```

The explicit confirmation is mandatory. Promotion is not a dependency of any
normal build or test task. Review the resulting JSON/CSV diff before accepting it.

Certify every shipped campaign and fallback board without rewriting assets:

```bash
./gradlew certifyCampaignContent
```

Benchmark 31 fixed Daily dates on the host JVM:

```bash
./gradlew benchmarkDailyChallenge
```

## Certification and report interpretation

Each accepted level records its origin, seed/profile when generated, difficulty,
shortest clean length, capped solution count, valid first actions, explored
states, grading thresholds, tags, and SHA-256 fingerprint. Certification also
replays the solver result, probes all initial failed actions for immutability,
requires at least one valid opening, enforces profile counts/caps, rejects exact
fingerprints, and rejects generated layouts that differ only by wall dressing.

Difficulty is a composite descriptive score using arrow/magnet/wall counts,
branching along one certified path, magnetic actions, flips, occlusion,
cancellation, and dead-end opportunity. Explored-state count is only one input;
it is never treated as difficulty by itself.
