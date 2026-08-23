# Codex Prompt — Magnetrail M3

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0, M1, and M2 are complete.

---

You are implementing **M3 for Magnetrail**, an Android-only, offline-first deterministic arrow-and-magnet logic puzzle.

Completed foundations:

- **M0:** pure Kotlin/JVM game engine, immutable rules, level parser/validator, solver, and tests
- **M1:** playable Compose/Canvas app, 12 canonical levels, result-driven animation, undo/restart, win/deadlock, and level navigation
- **M2:** Magnetrail visual identity, sound/haptics, accessibility settings, solver-backed hints, and versioned local progress

Preserve these systems and their passing tests. M3 expands **content and retention**, not the core rules.

## Inspect and verify before editing

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect the repository, current branch/diff, module graph, build logic, assets, persistence schema, solver/generator-related APIs, and tests. Preserve unrelated user changes.
3. Read every file under `docs/`, using this authority order:
   1. `docs/Magnetrail_Rules_Contract.md`
   2. Existing canonical level files and their schemas
   3. `docs/Magnetrail_Android_Technical_Brief.md`
   4. `docs/Magnetrail_DESIGN.md`
   5. `docs/Magnetrail_Game_Design_Spec_v0.1.docx`
   6. M0–M2 prompts and completion notes, if present
4. Inspect the actual production `GameEngine`, solver, level loader, `ProgressRepository`, DataStore schema, ViewModels, and navigation. Adapt to the real code; do not rewrite stable APIs to match example names here.
5. Run the existing core tests, app unit tests, lint, and debug build. Record the baseline.
6. Summarize the architecture, existing content count, current persistence keys/version, and an incremental M3 plan before editing.

Do not silently change gameplay rules. If generation exposes a rules ambiguity, add a documented decision proposal and stop before altering the engine.

## M3 objective

Deliver an offline content-and-retention vertical slice containing:

- **At least 100 launch-quality campaign levels**
- A deterministic, versioned level-generation and certification pipeline
- Difficulty metrics and a reproducible content report
- Move/action grading and one-to-three-star replay goals
- An offline deterministic Daily Challenge
- A locally persisted daily streak
- A versioned local coin/reward economy
- Paid-with-coins hints, with no ads or purchases
- Safe migration from M2 progress

M3 must remain fully playable without a network, account, or custom backend.

## Non-negotiable architecture

- `:game-core` remains pure Kotlin/JVM and the only gameplay authority.
- The generator and solver must use the production engine; neither may reimplement line of sight, magnetic control, routes, collision, polarity, win, or deadlock.
- Generated randomness must come only from an explicit seed through one defined PRNG implementation. Never use implicit global randomness.
- A level is accepted only after parsing, structural validation, solver certification, and content-quality checks.
- Runtime UI never accepts an uncertified candidate merely because generation completed.
- Content, generator, daily-seed, grading, and economy versions are explicit and persisted where relevant.
- Ads, analytics, remote config, accounts, cloud sync, billing, and Internet permission remain absent.

## Required work

### 1. Versioned content schema and catalog

Extend the level metadata/schema only as needed, while keeping the original 12 levels backward compatible. Add stable fields or equivalent domain metadata for:

- Stable level ID
- Campaign sequence number
- Content/schema version
- Origin: `HANDCRAFTED` or `GENERATOR_ASSISTED`
- Generator version and seed when applicable
- Difficulty band
- Certified solution length
- Solution count capped at a documented limit
- Valid first-action count
- Explored-state count
- Grading thresholds
- Optional mechanic/tutorial tags
- Deterministic content hash or canonical fingerprint

Keep authored coordinates and the M0 rules contract unchanged. Reject duplicate IDs, duplicate sequence numbers, invalid metadata, and content-hash mismatch with useful errors.

Campaign requirements:

- Preserve the original 12 levels and their stable IDs/order unless a documented defect requires a migration.
- Reach **at least 100 accepted campaign levels**.
- Target approximately **30 handcrafted or intentionally hand-tuned levels** and **70+ generator-assisted, solver-certified, human-reviewed levels**. The original 12 count toward the handcrafted/tuned group.
- Check accepted level JSON into the repository/app assets. Do not regenerate the campaign randomly on a player’s device.
- Split files by pack/difficulty if that improves maintainability, with one catalog/manifest defining exact order.
- Keep an asset/content integrity test that loads and certifies the entire shipped catalog.

Do not claim that bulk-generated candidates are launch-quality without applying the acceptance gates below.

### 2. Deterministic generator

Implement a pure Kotlin deterministic generator, preferably in `:game-core` if it has no Android dependencies, plus a JVM tooling entry point or small `:level-tools` module for offline content production.

Use **reverse construction where practical**:

1. Start from an empty or terminally solvable board.
2. Add arrows/magnets/walls while retaining a known reverse solution or certified solvability.
3. Randomize only through the explicit seeded PRNG.
4. Validate with the production parser/validator.
5. Solve using the production engine.
6. Calculate metrics and reject weak candidates.
7. Emit canonical JSON and a reproducible report.

It is acceptable to combine templates, constrained placement, mutation, and rejection sampling. It is not acceptable to place entities randomly and ship the first board the solver happens to solve.

Generator API/output must be deterministic for:

```text
generatorVersion + seed + generationProfile = identical canonical level
```

Define bounded work: maximum attempts, solver state cap, timeout/cancellation handling, and explicit rejection reasons. Never risk an infinite loop on-device or in tests.

Add generation profiles for at least:

- Intro/easy
- Developing/medium
- Advanced/hard
- Daily challenge

Profiles may constrain board size, arrow count, magnet count, wall count, polarity mix, branching, required flips, and solver complexity. Do not introduce new special objects or new gameplay rules in M3.

### 3. Certification and quality gates

Create a reusable certification pipeline. Every accepted campaign and daily candidate must satisfy:

- Valid schema and entity placement
- Solvable through the production solver
- Certified solution replays successfully through the production engine
- No engine state mutation on failed actions
- At least one valid opening action
- No trivial already-won board
- No duplicate content fingerprint in the shipped catalog
- Board and entity counts within profile limits
- Solver work below a documented cap suitable for hints/runtime use
- Required mechanic/difficulty targets for its pack

Add content-quality heuristics, recorded in the report rather than hidden in UI:

- Solution length
- Number of distinct solutions up to cap
- Valid first-action count
- Average branching along one certified solution
- Number of magnet-controlled successful actions
- Number of polarity flips
- Presence of Pull, Push, walls, occlusion, cancellation, and dead-end opportunities
- Solver explored-state count
- Estimated difficulty score and assigned band

Do not equate a large explored-state count with good difficulty by itself. Reject obvious duplicates, mechanically empty boards, repeated near-identical layouts, excessive opening ambiguity in early packs, and levels whose intended mechanic never matters.

### 4. Reproducible content tooling

Provide a documented Gradle task or JVM command that can:

- Generate a requested number of candidates from a seed range/profile
- Certify candidates
- Deduplicate by fingerprint
- Write candidates to a staging directory, never directly overwrite shipped content by default
- Emit a CSV or JSON report with metrics and rejection reasons
- Promote explicitly selected candidate IDs into the campaign catalog through a separate deliberate command or documented manual step
- Re-run certification against all shipped levels

Generated content changes must be reviewable in Git. Do not make normal app builds silently rewrite assets.

Check in a content report for the final 100+ levels showing count by origin, board size, difficulty, primary mechanic tag, solver complexity range, and seed/generator version.

### 5. Campaign progression and packs

Organize 100+ levels into a deliberate learning curve. A reasonable structure is:

- Levels 1–10: movement, blocking, clean exits
- Levels 11–25: Pull introduction and simple capture
- Levels 26–40: Push and polarity flip
- Levels 41–60: multiple magnets and visibility/occlusion
- Levels 61–80: competing influences, cancellation, walls, order dependency
- Levels 81–100+: advanced combinations and denser sequencing

Use the actual mechanics supported by the frozen contract. Preserve tutorial clarity; do not introduce a mechanic merely through a hard level without explanation.

Update level selection for 100+ items with efficient lazy rendering, visible pack/difficulty grouping, completed/locked states, star totals, and accessible scrolling. Keep replay available. QA/debug builds must retain a clear way to open any level.

### 6. Move accounting and stars

Define one consistent player-action metric:

- Increment `actions` for each accepted arrow tap that reaches engine resolution, including failed launches.
- Do not count ignored taps, UI controls, animation-blocked taps, hints, undo, restart, or navigation.
- Track `overloads`/failed launches separately.
- Track hints shown separately.

Because every successful clear removes each arrow once, raw successful-move count alone cannot create meaningful optimization. Therefore use authored/certified thresholds based on total actions and hints:

- **3 stars:** board cleared with `actions <= parActions` and `hintsUsed == 0`
- **2 stars:** board cleared with `actions <= twoStarMaxActions`
- **1 star:** board cleared

Default certification values should normally use:

- `parActions = certified clean solution length`
- `twoStarMaxActions = parActions + max(2, ceil(parActions * 0.25))`

Allow explicit authored overrides only when validated and documented. Stars are based on the completed attempt and must not change mid-animation. Undo does not erase already-counted actions; restarting begins a new attempt at zero. A hint makes three stars unavailable for that attempt but must not prevent completion.

Persist each level’s best star count, lowest actions, lowest overloads, and lowest hints with monotonic best-value updates. Replaying can improve records but never reduce earned stars.

### 7. Coins and rewards

Implement a simple, versioned, local soft-currency economy. Freeze these M3 defaults in one central configuration object, not scattered UI literals:

- Starting balance: **150 coins**
- First campaign clear: **20 coins**
- Newly earned star: **5 coins per incremental star**
- First Daily Challenge clear for that date: **50 coins**
- Hint cost: **30 coins**
- Undo and restart remain free

Reward rules:

- First-clear rewards are granted once per stable level ID.
- Star rewards are granted only for newly earned stars above the previous best.
- Replaying/farming the same result grants no duplicate reward.
- Daily reward is granted once per local date/daily identity.
- Coin balance never becomes negative.
- Hint spend and display are one atomic transaction: do not charge if no usable solver hint is returned.
- Prevent double-tap/double-collection races with serialized repository updates.
- Completion UI must itemize first-clear reward, new-star reward, daily reward, and resulting balance.

If the player lacks coins, the Hint button may explain the balance requirement but gameplay, undo, restart, and level completion remain fully usable. Do not add rewarded ads, purchases, waiting timers, or manipulative prompts.

Add a developer-only economy simulation/test that walks representative progression, hint-use, replay, and daily scenarios. Report minimum/median balances and verify the economy neither overflows nor creates unavoidable progression blockage. Economy balancing is data/config, not hardcoded into composables.

### 8. Offline Daily Challenge

Create one deterministic Daily Challenge per local calendar date with no backend.

Use an explicit versioned identity such as:

```text
dailyId = localDate + dailyGeneratorVersion
seed = stableHash("Magnetrail" + localDate + dailyGeneratorVersion + fixedPublicSalt)
```

Requirements:

- Use `java.time.LocalDate` through an injected clock/date provider, respecting the device’s current zone.
- Do not use Kotlin/Java `hashCode()` as a cross-version seed contract; implement and test a stable documented hash.
- Same date, generator version, and salt must create identical content on every supported device.
- Daily generation runs deterministically with bounded work and off the main thread.
- Every daily board must pass the same structural validation and production-solver certification as campaign content.
- Cache the certified daily level and its identity locally so recomposition/process restart does not regenerate unnecessarily.
- If generation/certification fails within its cap, load a deterministic certified fallback from a bundled fallback bank.
- The Daily Challenge is playable offline and does not require an accurate server clock.
- Clearly label that the challenge follows the device’s local date; do not promise global leaderboard equivalence.
- Preserve historical completion/reward records by `dailyId`, not only by day-of-month.

Prefer a modest board/solver profile that works quickly on lower-end Android devices. Measure generation and solve time in a benchmark or JVM report; do not guess performance.

### 9. Daily streak

Persist:

- Current streak count
- Best streak count
- Last completed daily date/identity
- Set of rewarded daily IDs or bounded versioned history sufficient to prevent duplicate rewards

Rules:

- First completed Daily Challenge starts streak at 1.
- Completing again on the same local date does not increment it.
- Completing the immediately following local date increments it.
- Completing after a gap resets current streak to 1.
- If the observed date moves backward or equals an older completed date, never grant a duplicate reward and never increase the streak. Preserve the last trustworthy streak state until a later valid date.
- Time-zone/clock changes may affect which local puzzle appears; handle them conservatively and document that no backend means perfect anti-cheat/global-day enforcement is impossible.

Display current streak and today’s completion state on Home/Daily screens without urgency, guilt, or threatening copy. A missed day simply starts a new streak next time.

### 10. Persistence schema and M2 migration

Extend the existing DataStore-backed repository with an explicit schema migration. Preserve all valid M2 progress/settings.

Persist versioned data for:

- Campaign unlock and completion
- Per-level best stars/actions/overloads/hints
- First-clear reward flags
- Coin balance and economy version
- Daily cache identity/content fingerprint as appropriate
- Daily completion/reward IDs
- Current/best streak and last trusted completion date
- Content catalog version, generator version, and daily generator version
- Existing sound, haptics, reduced motion, high contrast, and path-preview settings

Use stable string level IDs, not only list indexes. Validate and clamp corrupt values. Make multi-field reward/progress operations atomic through one repository transaction or serialized update path. Do not persist in-flight animation. No account or cloud recovery exists.

Migration requirements:

- Existing completed level IDs and best move values survive.
- Existing highest-unlocked progress maps safely into the expanded catalog.
- New coin balance initializes once without resetting on every launch.
- Migration is idempotent and tested from fresh install, valid M2 data, partial/corrupt M2 data, and already-migrated M3 data.

### 11. UI integration

Extend the M2 visual system without overwhelming the board:

- Home: Continue, Daily Challenge card, current streak, and a compact coin balance
- Level selection: packs, stars, completion, locks, and total stars
- Gameplay: coin balance only where needed; Hint shows cost before confirmation/spend
- Completion: earned stars, best result, reward breakdown, Next/Replay
- Daily screen/result: date, completion state, streak, reward, Replay

Do not add a shop, coin purchase screen, countdown pressure, inbox, event carousel, leaderboard, lives, or promotional banners. Maintain TalkBack labels, dynamic type behavior, reduced motion, contrast settings, and 48 dp targets.

## Testing requirements

Keep all M0–M2 tests passing. Add deterministic tests at the correct layer.

### Generator and content tests

- Same version/profile/seed produces structurally identical canonical JSON
- Different representative seeds do not collapse to identical fingerprints
- Attempt and solver caps terminate deterministically
- Certification replays its solution through the production engine
- All 100+ campaign levels parse, validate, solve, and have unique IDs/fingerprints
- Metadata metrics and grading thresholds are internally consistent
- Campaign difficulty/pack requirements are represented
- Daily fallback bank is valid and solver-certified
- Shipped assets do not change during a normal build/test

### Grading/economy tests

- Action counting includes engine-resolved failures and excludes ignored taps/controls
- Three-, two-, and one-star boundaries
- Hint prevents three stars for that attempt
- Undo does not erase action count; restart resets attempt count
- Best records improve monotonically
- First-clear and incremental-star rewards are idempotent
- Daily reward is once per daily ID
- Hint charge is atomic and never produces a negative balance
- No charge on solver failure/cancellation/stale hint
- Concurrent completion/reward events cannot double-credit
- Economy simulation produces documented results

### Daily/streak tests

- Stable seed/hash golden vectors
- Same date/version yields the same certified daily board
- Date change yields the correct new identity
- Cache reuse and invalid-cache recovery
- Generator-cap failure selects deterministic bundled fallback
- First day, same day, next day, missed day, backward clock, and time-zone-change cases
- No duplicate daily reward across restart or replay

### Migration/persistence tests

- Fresh install defaults
- M2-to-M3 migration
- Idempotent repeated migration
- Partial/corrupt data recovery
- Level-ID stability across catalog version
- Process restart preserves campaign, stars, coins, daily completion, streak, and settings

### Compose/UI tests

- 100+ level catalog scrolls and exposes semantic pack/lock/star state
- Completion star/reward breakdown is correct
- Hint cost, insufficient balance, and successful atomic charge states
- Daily card and screen show deterministic completion/streak state
- Reduced-motion/accessibility settings still affect new animations/screens
- Large font and TalkBack semantics keep primary actions reachable

### Manual/benchmark QA

- Play representative levels from every difficulty band
- Replay to improve stars and verify no duplicate rewards
- Spend hints until insufficient balance and confirm no gameplay lockout
- Daily completion, replay, next-day simulation, gap, and backward-date behavior
- Offline airplane-mode launch and all M3 flows
- Cold-start catalog load and level-selection scroll performance
- Daily generation/solver timing on at least one lower-spec emulator/device where available
- TalkBack, font scaling, 360/390/430 dp portrait widths

## Suggested organization

Fit existing conventions and avoid reorganizing stable M0–M2 code unnecessarily:

```text
game-core/src/main/kotlin/.../magnetrail/core/
├── generation/
│   ├── GenerationProfile.kt
│   ├── SeededRandom.kt
│   ├── LevelGenerator.kt
│   ├── Candidate.kt
│   └── Certification.kt
├── difficulty/
│   ├── DifficultyMetrics.kt
│   └── DifficultyClassifier.kt
└── content/
    └── ContentFingerprint.kt

level-tools/src/main/kotlin/.../
├── GenerateCandidates.kt
├── CertifyCatalog.kt
└── ContentReport.kt

app/src/main/kotlin/.../magnetrail/
├── campaign/
├── daily/
│   ├── DailyChallengeService.kt
│   ├── DailySeed.kt
│   └── StreakPolicy.kt
├── economy/
│   ├── EconomyConfig.kt
│   ├── RewardPolicy.kt
│   └── CoinTransaction.kt
└── data/
    ├── ProgressSchema.kt
    └── ProgressMigration.kt
```

A new `:level-tools` JVM module is optional but preferred when it keeps content tooling out of the Android app. Do not add Android dependencies to `:game-core` or tooling.

## Explicitly out of scope

Do not add:

- New entity types or changes to frozen rules
- More retention systems beyond campaign, daily, streak, stars, coins, and hints
- Rewarded ads, interstitials, banners, ad consent SDK
- Purchases, billing, subscriptions, real-money store, paid currency
- Analytics, attribution, remote config, A/B testing, crash SDK, Firebase
- Account, login, backend, cloud save, server clock, leaderboard, social sharing
- Internet permission or remote assets/content
- App Bundle release signing, Play Console upload, ASO screenshots/listing, or production launch automation

Do not delete or weaken existing tests to make M3 pass.

## Verification commands

Use the repository’s actual Gradle tasks. At minimum run the equivalents of:

```bash
./gradlew :game-core:test
./gradlew :level-tools:test
./gradlew :app:testDebugUnitTest
./gradlew lintDebug
./gradlew :app:assembleDebug
./gradlew certifyCampaignContent
```

If `:level-tools` is not created, use the documented equivalent tasks. Run connected tests when an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

If hardware is unavailable, report daily performance, persistence, haptic/audio, TalkBack, and connected-test limitations honestly.

## M3 definition of done

M3 is complete only when:

- All previous tests remain passing and the debug app builds/lints.
- At least 100 checked-in campaign levels have stable IDs and unique fingerprints.
- Every shipped level validates, solves, and replays a certified solution through the production engine.
- The generator is deterministic, versioned, bounded, documented, and separate from normal app builds.
- A reproducible content-quality report exists and all shipped candidates passed review gates.
- Campaign progression forms a deliberate learning curve and remains accessible/performance-safe.
- Actions, overloads, hints, stars, and best records follow one tested policy.
- Coin rewards/spending are versioned, atomic, idempotent, non-negative, and do not block basic play.
- Daily Challenge is deterministic, certified, cached, bounded, offline, and has a bundled fallback.
- Streak/date behavior is conservative and duplicate rewards are prevented locally.
- M2 progress/settings migrate safely and idempotently.
- No ads, backend, account, billing, analytics, or Internet permission were introduced.

## Final response

Report:

1. What was implemented.
2. Final campaign level count and handcrafted/generator-assisted split.
3. Generator version, seed contract, profiles, bounds, and certification policy.
4. Content report summary: difficulty distribution, uniqueness, solution and solver metrics.
5. Grading, coins, rewards, Daily Challenge, and streak behavior.
6. Persistence migration and backward compatibility.
7. Exact test/lint/build/content-certification commands and results.
8. Manual/benchmark checks and device limitations.
9. Any rejected candidates, compromises, or deferred balancing concerns.
10. The next safe milestone: **M4 monetization and observability**—privacy/consent review, rewarded ads, carefully capped interstitials, analytics/crash reporting, and economy tuning behind abstractions—without changing core gameplay or making ads necessary to play.

Begin by inspecting the repository, reading the docs, running the M0–M2 baseline verification, and reporting the current content/persistence architecture. Then proceed unless a material blocker is found.
