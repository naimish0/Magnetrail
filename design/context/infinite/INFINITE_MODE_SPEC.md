# Magnetrail Phase 6 — Infinite Mode

Status: implemented with a pre-certified offline content pool  
Catalog version: 1  
Selection version: 3  
Generator version: 5  
Rule version: `magnetrail-core-1`

## Product boundary

Infinite Mode is a separate deterministic mode, never campaign Level 206/301 and never a source of
campaign unlocks. Runtime code does not generate, solve, analyze, certify, or mutate boards. It only
selects from `docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json`, which is produced offline by
`generateInfiniteCertifiedCatalog` through the production engine, complete solver, Difficulty V4,
Quality V2, structural gates, and duplicate filters.

The mode is available immediately when its certified catalog loads. Home deliberately presents one
primary `Play · Level N` button with the actual selected difficulty as its second line: it resumes or selects the next Progressive puzzle directly without
showing a separate Continue, Level Select, or Progressive Journey control. A compact coin chip sits
at the top-left and an icon-only Settings control sits at the top-right. The centered content stack is
app name, tagline, `Play · Level N` plus difficulty, then Daily Challenge. Infinite completions have
their own completed count, streak, bounded history, and attempt results. Economy version 3 grants
10 coins exactly once for the first completion of each deterministic journey ordinal. Replay or
duplicate persistence for the same ordinal grants zero. Infinite Mode does not alter campaign
completion, stars, records, unlock position, or Daily Challenge progress.

## Certified catalog policy

- The checked-in pool contains 624 certified boards: 200 Easy, 270 Medium, 130 Hard, 12 Expert,
  and 12 Master.
- A generated candidate enters the catalog only after `CertificationPipelineV5.Accepted`.
- Incomplete solver/V4 analysis, truncation, replay failure, Quality rejection, structural gate
  failure, campaign duplication, pool duplication, or symmetry duplication excludes a candidate.
- High-band entries use Generator V5.2 purposeful space and pass the same unchanged gates. No
  runtime override exists.

## Identity and determinism

Every selected puzzle has the stable identity:

```text
generatorVersion + generationProfile + generationSeed + contentFingerprint
+ catalogVersion + selectionVersion + ruleVersion + analyzerVersion
```

The external ID is `infinite-v{generator}-{profile}-{seed}-{sha256}`. The selector uses catalog ID,
catalog version, requested difficulty, selection ordinal, and candidate ID for stable SHA-256
ordering. The selected ID, difficulty, ordinal, fingerprint, completion state, and bounded last 100
history entries are persisted in DataStore. Process restart resumes the same unfinished ID. A fresh
install starts from the same deterministic ordinal-zero choice; platform backup restoration also
restores the persisted identity.

## Difficulty selection

The selector is deterministic and non-adaptive in Phase 6. `Progressive Journey` is the default:

- Infinite Levels 1–10 are a guided animated tutorial using certified Easy boards. Levels 1–5 use
  deliberately compact boards (two arrows for 1–4 and three for Level 5), each with one certified
  solution family and one valid opening action. Levels 6–10 provide guided Easy practice for scanning,
  visibility, exposure, comparing choices, and combining the rules.
- The next arrow in a compatible authored solution receives a non-intercepting animated fingertip,
  pulsing focus ring, and printed-direction cue. After every successful removal the finger advances to
  the next arrow; a failed tap leaves it on the current step. Each lesson card pairs a scaled animated
  diagram with one short action prompt. Reduced Motion replaces looping animation with a static teaching
  frame. The same ten lessons also appear when numbered campaign Levels 1–10 are opened directly.
- Infinite Levels 11–20 request Medium.
- Infinite Levels 21–30 request Hard.
- From Level 31, the first mixed block is Hard, Super Hard, Easy, Expert, Medium, Easy, Master,
  Hard, Medium.
- Later nine-level blocks contain the same balanced ingredients in a versioned deterministic
  shuffle. This feels varied while remaining reproducible after restart.

Players can still choose a fixed thinking rhythm:

| Player choice | Repeating requested bands |
|---|---|
| Easy | Easy, Easy, Medium, Easy |
| Medium | Medium, Easy, Hard, Medium |
| Hard | Hard, Medium, Super Hard, Hard |
| Super Hard | Super Hard, Hard, Expert, Super Hard |
| Expert | Expert, Super Hard, Hard, Expert |
| Master | Master, Expert, Master, Super Hard |

When a requested band is absent, the closest available certified band is selected. The last 16
fingerprints are avoided when the relevant pool has
unused alternatives; reuse is deterministic only after that bounded pool is exhausted.

Super Hard is the player-facing name for the internal `VERY_HARD` band. Its older exact-band
constructor is intentionally not packaged because a bounded smoke run remained too expensive.
Selection therefore uses certified Expert content when Hard and Expert are equally close, avoiding
an easier-than-requested result, and records the fallback honestly.

This is not Phase 7 adaptive difficulty: there is no player skill score, performance-based band
change, ML, network input, ad/economy input, or runtime generation.

## Runtime flow

```text
Home Play
  -> resume/select Progressive difficulty
  -> resume persisted unfinished ID, or select deterministic next ID
  -> load immutable certified LevelDefinition
  -> production GameEngine play
  -> persist Infinite-only completion
  -> deterministic next certified puzzle
```

If the requested band or recent-history-filtered subset is empty, selection uses the nearest
certified band and finally the complete certified band as a bounded deterministic fallback. An empty
or invalid catalog fails closed at application initialization.

## Offline generation

Run:

```bash
./gradlew generateInfiniteCertifiedCatalog --configuration-cache
```

Defaults are a deterministic 624-board pool (600 standard plus 12 Expert and 12 Master), base
seed `6600001`, 24 retries per logical slot, and the profile candidate caps. Output is separate from
the numbered campaign:

- `docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json`
- `docs/infinite/INFINITE_GENERATOR_BENCHMARK.json`
- `docs/infinite/INFINITE_GENERATOR_BENCHMARK.csv`
- `docs/infinite/INFINITE_FALLBACK_BANK_REPORT.md`

Normal `build` synchronizes the checked-in catalog into app assets but never invokes generation.
