# Magnetrail — Complete Game and Development Context

Last consolidated: **2026-08-21 (Asia/Kolkata)**

Repository: Android Studio project `Magnetrail`

Package: `com.rameshta.magnetrail`
Current checked-in campaign: **2,205 levels, content version 10, generator version 5**

Canonical campaign SHA-256:
`8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`

This is the single read-first handoff document for Magnetrail. It consolidates the product vision,
frozen gameplay semantics, Android architecture, UI system, implemented milestones, campaign and
economy state, advertising/privacy behavior, build/release posture, known defects, safety rules,
and future roadmap.

It is intentionally self-contained at the system level. Section 25 contains a generated inventory
for every checked-in Campaign, Infinite, Daily fallback, and V11 pilot board, including grid size,
arrow direction counts, magnet polarity counts, wall/block count, profile, band, and solution
metadata. Canonical JSON remains the authority for exact object IDs, cell coordinates, designed
solution order, mechanic tags, seeds, and fingerprints; the inventory is a human-readable snapshot
bound to the catalog hashes recorded here.

---

## 1. Authority and conflict resolution

Use this order when sources disagree:

1. The latest explicit project-owner instruction.
2. `Magnetrail_Rules_Contract.md` for existing gameplay semantics.
3. Checked-in canonical level JSON and current production source/tests for implemented behavior.
4. This consolidated context for overall product and repository state.
5. `Magnetrail_Android_Technical_Brief.md` for architectural boundaries.
6. `Magnetrail_DESIGN.md` for visual and interaction design.
7. The original `Magnetrail_Game_Design_Spec_v0.1.docx` for product intent where it has not been
   superseded by later approved milestones.
8. Milestone prompts and historical reports for the intent and evidence of their respective phase.

Important conflict resolutions:

- The immutable existing rules remain `magnetrail-core-1`.
- The current campaign contains 2,205 levels, not the smaller totals still mentioned by historical
  M3/M5 documents.
- The latest monetization directive is **ads only**. The Billing/Remove Ads section in
  `CODEX_MASTER_REMAINING_DEVELOPMENT_PROMPT.md` is superseded. Do not add Google Play Billing,
  purchases, subscriptions, paid content, premium currency, or Remove Ads.
- Automated solver or Quality approval is never human approval.
- The project owner/player completed the archived content-v6 campaign and found all 200 levels
  overwhelmingly easy. D1 diagnosed/calibrated that failure; D2 subsequently replaced all 200
  boards under stable IDs with content-v7 Generator-V5 boards. Content-v7 is technically
  certified, but it has not received human difficulty ratings and is not human-approved.
- D2.1 and the later Generator-V5 repair began as staging work. On 2026-08-20 the owner directed an
  append promotion: four current V5-certified boards became Levels 201–204 and the deterministic
  V5.1 Expert board became Level 205 under an explicit structural-certification waiver. Master is excluded.
- On 2026-08-21 the owner directed Campaign V9: 2,000 production-certified, layout-unique boards
  were appended as Levels 206–2,205, with all six requested tiers interleaved and balanced.
- Campaign V9 human playtesting rejected its six-band calibration and confirmed repeated high-band
  interactive templates. Campaign V10 then remediated Levels 206–2,205 with denser ordered arrow
  cascades, unique visual/interactive fingerprints, and content version 10, while preserving
  Levels 1–205.
- The current Campaign V10 human evidence also rejects the six difficulty labels: 68/68 recorded
  boards were completed, only 26/68 ratings were within one assigned band, 55/68 were perceived
  Easy, and the median perceived rating remained Easy for five bands and 1.5 between Easy/Medium
  for Master. V10 density, arrow count, and profile names are not accepted difficulty evidence.
- A separate content-v11 pilot currently contains 53 isolated, newly generated boards: ten each
  for Easy through Expert and three Master. It is debug/staging content only, has not modified the
  production campaign, and remains pending blinded human review.
- Phase 6 is implemented with a separate 624-board offline-certified Infinite catalog. Generator
  V5.2's purposeful-space weave now certifies 12 Expert and 12 Master entries without changing
  V4 or any structural gate. Runtime still performs selection only, never board generation.
- Strict phase isolation applies. Do not implement future-phase functionality while working on a
  current or reopened phase.

---

## 2. Product identity

| Item | Definition |
|---|---|
| Product name | Magnetrail |
| Store-facing title | Magnetrail: Arrow Puzzle |
| Tagline | Bend the path. Clear the board. |
| Genre | Single-player deterministic logic puzzle |
| Platform | Android only |
| Session target | Approximately 30 seconds to 4 minutes per puzzle |
| Orientation | Portrait-first, responsive rather than manifest-locked |
| Connectivity | Offline-first; no account or backend required for gameplay |
| Core promise | Every cleared magnet-controlled arrow changes what that magnet will do next |
| Player objective | Clear every arrow by discovering a valid action order |

One-sentence pitch:

> Clear every arrow, but magnets redirect aligned arrows and flip polarity after each successful
> interaction. Solve the changing board in the right sequence.

### Product pillars

- **Instant readability:** relevant forces, blockers, polarity, and board state are visible before
  the player acts.
- **Determinism:** the same board state and tap always produce the same result.
- **Stateful sequencing:** successful magnetic actions flip polarity and change future outcomes.
- **Fast recovery:** invalid moves teach without lives, waits, reshuffles, or opaque punishment.
- **Satisfying motion:** rules are discrete and grid-based; presentation may use polished curves,
  trails, anticipation, impact, sound, and haptics.
- **Cognitive challenge:** difficulty should come from meaningful choices, consequences, ordering,
  dependencies, spatial reasoning, interacting mechanics, and fair uncertainty—not clutter.

### Desired player experience

The intended thought is:

> “I need to figure out what to do.”

The game should not communicate:

> “This is difficult only because the board is crowded.”

Avoid random clutter, arbitrary traps, excessive forced sequences, unnecessarily large boards,
guessing, and repetition disguised as difficulty.

---

## 3. Frozen gameplay rules

Rule version: `magnetrail-core-1`.

The core simulation is discrete. Do not implement continuous magnetic force, velocity,
acceleration, or rigid-body physics.

### 3.1 Coordinate system

- Authored coordinates are one-based.
- Row 1 is the top; column 1 is the left.
- North decreases row, east increases column, south increases row, and west decreases column.
- Internal conversion is allowed, but authored serialization and tests must preserve coordinates.

### 3.2 Existing entities

- **Arrow:** stable ID, cell, and printed cardinal direction.
- **Magnet:** stable ID, cell, and mutable-in-state polarity: `PULL` or `PUSH`.
- **Wall:** permanent occupied cell.
- At most one authored entity occupies a cell.

### 3.3 Turn resolution

1. The player selects one remaining arrow.
2. Capture the complete immutable board state at tap time.
3. Find magnets aligned with the arrow on the same row or column.
4. A candidate magnet is visible only when no arrow, wall, or other magnet lies strictly between
   it and the selected arrow.
5. The nearest visible aligned magnet controls the arrow.
6. If multiple nearest magnets tie at the same distance, their influence cancels and the arrow
   follows its printed direction.
7. `PULL` changes the effective direction toward the controller.
8. `PUSH` changes the effective direction directly away from the controller.
9. With no controller, use the printed direction.
10. Trace one cell at a time until a terminal event occurs.

### 3.4 Terminal events

- **Exit:** the arrow crosses the board boundary. This succeeds unless a controlling Pull magnet
  existed; a Pull-controlled arrow must reach its magnet.
- **Pull capture:** the arrow reaches its controlling Pull magnet and is removed successfully.
- **Collision:** the arrow encounters another arrow, a wall, or a non-controlling magnet. The
  launch fails.
- **Invalid Pull exit:** a Pull-controlled arrow exits without capture. The launch fails.

### 3.5 State mutation

On successful magnet-controlled movement:

- Remove the selected arrow.
- Flip only the controlling magnet from Pull to Push or Push to Pull.

On successful unaffected movement:

- Remove the selected arrow.
- Do not change any magnet.

On collision or invalid Pull exit:

- Preserve the exact original state.
- Do not remove an arrow.
- Do not flip a magnet.

### 3.6 Win, deadlock, and restart

- Win immediately after the final arrow is removed.
- Deadlock means arrows remain but no successful action exists.
- The engine reports deadlock and never reshuffles.
- Gameplay does not automatically announce deadlock or show a failure card. The board and Restart
  stay available, but Hint is disabled because the solver has proven that no completion route
  remains; no coins or rewarded-hint credit can be spent in that state.
- Restart restores the authored initial state and resets the current attempt.
- Player-facing Undo was removed on 2026-08-20. Engine states remain immutable for solver,
  replay, animation, diagnostics, and test use, but gameplay no longer exposes rollback.

### 3.7 Resolution output

Every action returns a `ResolutionResult` containing at least:

- success/failure;
- original and resulting states;
- selected arrow ID;
- printed and effective direction;
- controlling magnet ID, if any;
- traversed cells in order;
- terminal event;
- collision target, if any;
- polarity change, if any;
- win and deadlock flags.

The renderer treats this result as an animation script. UI, animation, analytics, ads, and
persistence must not recompute gameplay.

### 3.8 Determinism

`BoardState + PlayerAction -> ResolutionResult` must be structurally deterministic. Time, random
numbers, frame rate, animation, device density, sound, haptics, ads, analytics, and lifecycle must
never influence the rules result.

---

## 4. Core player loop

1. Choose Campaign, Infinite, or Daily Challenge; debug builds may instead enter the blinded
   Human Difficulty Playtest.
2. Read arrow directions, magnet polarities, line-of-sight blockers, and walls.
3. Tap an arrow.
4. The production engine resolves the action once.
5. The app animates the exact route/result and commits state only at animation completion.
6. A failure increments overload/action accounting but leaves the board unchanged.
7. Continue until all arrows are removed or the board is deadlocked.
8. Use Restart or an optional solver-backed hint.
9. On completion, award stars/rewards atomically and offer Replay/Next.

No player action should be gated by money, an ad, a life timer, or a network connection.

---

## 5. Prototype learning sequence

The original 12 boards are still the conceptual rules tutorial, although their currently shipped
campaign representations were later regenerated/remediated and now carry generator-assisted
metadata.

| # | Stable ID | Title | Board | Primary lesson | Designed clean path |
|---:|---|---|---|---|---|
| 1 | `proto-001` | First release | 4×4 | Basic exit | A |
| 2 | `proto-002` | Clear the blocker | 4×4 | Arrows block arrows | B → A |
| 3 | `proto-003` | Pull | 5×5 | Pull capture | A |
| 4 | `proto-004` | Push | 5×5 | Push exit | A |
| 5 | `proto-005` | Automatic flip | 5×5 | Safe polarity experiment | A → B |
| 6 | `proto-006` | Order matters | 5×5 | Pull trapped arrow, then Push | B → A |
| 7 | `proto-007` | Field occlusion | 5×5 | Arrow blocks field visibility | B → A |
| 8 | `proto-008` | Shielded field | 5×5 | Wall blocks magnetic visibility | A |
| 9 | `proto-009` | Alternating gates | 5×5 | Pull-only gate plus relay | B → A → C |
| 10 | `proto-010` | Reveal the relay | 6×6 | Remove blocker before controlled move | A → C → B |
| 11 | `proto-011` | Reverse gate | 5×5 | Begin in Push, finish in Pull | A → B |
| 12 | `proto-012` | Prototype capstone | 7×7 | Four-arrow alternating sequence | A → B → C → D |

Tutorial policy:

- Teach a new rule on a safe board first.
- Use at most one short sentence, then remove the explanation on the next level.
- Reveal the signature magnet mechanic early.
- Failed predictions must make the exact blocker/result readable.

Canonical original layouts: `docs/Magnetrail_Prototype_Levels_v1.json`.

---

## 6. Android architecture

### 6.1 Modules

| Module | Responsibility |
|---|---|
| `:game-core` | Pure Kotlin/JVM models, engine, route tracing, validation, parser, solver, deterministic generation, certification, grading, economy, Daily identity/streak, difficulty and Quality analysis |
| `:app` | Android/Compose UI, Canvas rendering, ViewModel/state, DataStore, feedback, Daily orchestration, ads, consent, analytics, crash reporting, release configuration |
| `:level-tools` | Offline JVM generation, certification, audits, candidate staging, reports, and explicit promotion tasks |
| `:baseline-profile` | Baseline/startup profile generation and Macrobenchmark support |

### 6.2 Non-negotiable boundaries

- `:game-core` must not import Android SDK, Compose, `Activity`, `Context`, Play Services, AdMob,
  Firebase, analytics, or lifecycle APIs.
- The production engine is the only gameplay authority.
- Solver, generator, hints, certification, and diagnostics call the production engine.
- Compose renders immutable state and emits intents.
- `GameViewModel` owns committed board state, in-flight result, animation phase,
  attempt counters, completion state, navigation, hints, and progress integration.
- Domain state is never partially mutated during animation.
- Ads and analytics remain entirely in the application layer.
- Normal builds may copy checked-in content into assets but must never regenerate or overwrite
  campaign content.

### 6.3 Current navigation

The Compose app currently exposes:

- Home;
- Campaign level selection;
- Campaign/Daily/Infinite gameplay;
- Infinite difficulty selection;
- a debug-only blinded Human Difficulty Playtest entry and playtest gameplay mode;
- Settings;
- Completion state within gameplay; deadlock remains an internal engine/analytics fact and is not
  presented as an automatic failure screen.

`GameMode` supports `CAMPAIGN`, `DAILY`, `INFINITE`, and the debug-only `PLAYTEST`. The Progressive Journey is immediately
available when its certified catalog loads, selects only immutable pre-certified catalog boards,
and persists its own
selection/completion history independently of campaign and Daily progress.

Debug builds can load the V10 campaign or sealed V6 calibration catalog for isolated playtesting;
the excluded V11 archive is no longer packaged as an app asset. The Human Difficulty Playtest hides the assigned label during play, records completion or
abandonment, actions, overloads, hints, restarts, duration, object counts, perceived rating, and
content fingerprint, persists an in-progress session locally, and exports CSV through Android's
document picker. Release builds do not expose this playtest entry.

Campaign selection is range-based rather than one unbounded list. Ranges are computed dynamically
in 50-level chunks, Previous/Next changes ranges, Go To validates a direct level number, and the
initial range contains the current/next level. Only the active range is materialized for UI. A
metadata-only logical index and deterministic reconstruction identity have tests covering 10,000
logical entries without generating boards on the UI thread.

### 6.4 Turn transaction in the app

1. Ignore input when disabled, complete, or animating.
2. Resolve the selected arrow exactly once with the engine.
3. Store the result and disable input.
4. Animate route, terminal event, collision/rewind, and polarity change from the result.
5. Commit `resultingState` at the defined completion boundary.
6. Clear in-flight state and re-enable input unless completion blocks it.

Lifecycle interruption must never create a board state the engine did not produce.

---

## 7. Current build and dependency configuration

| Item | Current value |
|---|---|
| Application ID / namespace | `com.rameshta.magnetrail` |
| Version | code `1`, name `1.0` |
| Minimum SDK | 24 |
| Target SDK | 36 |
| Compile SDK | 37 |
| Java/Kotlin bytecode target | Java 11 |
| Android Gradle Plugin | 9.1.1 |
| Kotlin | 2.2.10 |
| Kotlinx Serialization JSON | 1.9.0 |
| AndroidX Core KTX | 1.19.0 |
| Lifecycle | 2.11.0 |
| Activity Compose | 1.13.0 |
| DataStore | 1.2.1 |
| Google Mobile Ads | 25.4.0 |
| Google UMP | 4.0.0 |
| Firebase BoM | 34.17.0 |
| WorkManager pin | 2.11.2 |

`compileSdk 37` is required because current AndroidX Core 1.19.0 and Lifecycle 2.11.0 declare a
minimum compile API of 37. `targetSdk 36` is intentionally independent and remains the runtime
behavior opt-in level. `minSdk 24` remains the install floor.

Debug uses official Google test ad identifiers. Release is fail-closed and remains structurally
non-monetized unless every production input, audience choice, Firebase configuration, and upload
signing input is supplied and validated.

Generated `.class`, `.tab`, `.keystream`, and `.len` files belong only in ignored build/compiler
directories. They are not production source or campaign assets and must not be checked in.

---

## 8. Visual design system

### 8.1 Design character

The interface should feel calm, tactile, intelligent, premium, readable, and like a precision
physical logic toy. It must not resemble a casino, children’s learning app, neon sci-fi panel,
generic hyper-casual arrow clone, or a competing Arrow Out product.

Visual hierarchy:

1. Board state and paths.
2. Magnet polarity.
3. Level objective/progress.
4. Restart, Hint, pause/settings, and optional assistance.

### 8.2 Core palette

| Token | Value | Use |
|---|---|---|
| Primary navy | `#183153` | Brand, arrows, controls |
| Primary strong | `#10223C` | Strong structure/scrim base |
| Pull cyan | `#18A7B8` | Pull field/state |
| Pull soft | `#E8F7F8` | Pull surfaces |
| Push amber | `#E79A2D` | Push field/state, stars |
| Push soft | `#FFF4DF` | Push surfaces |
| Background | `#F4F7FB` | App canvas |
| Surface | `#FFFFFF` | Board/cards |
| Ink | `#172033` | Primary text |
| Muted slate | `#5B6574` | Secondary text |
| Border | `#D7DEE7` | Quiet separation |
| Grid | `#C9D3DF` | Board grid |
| Wall | `#39414D` | Permanent blockers |
| Success | `#267A5B` | Completion |
| Error | `#B84343` | Collision impact only |

Pull must combine cyan, inward chevrons/converging geometry, and a `PULL` label. Push combines
amber, outward geometry, and a `PUSH` label. Color is never the only state indicator.

The active Material 3 light scheme maps navy to `primary`, white to `onPrimary`/`surface`, Pull
cyan to `secondary`, Push amber to `tertiary`, the pale Pull surface to both `primaryContainer` and
`surfaceVariant`, ink to foreground text, border to `outline`, and collision red to `error`. There
is no dark theme in current source.

### 8.3 Typography, shapes, and spacing

- Current typeface: Compose `FontFamily.SansSerif`; Manrope remains a design preference but is not
  bundled or selected by current source.
- Current scale: display 34/38 sp ExtraBold; headline 26/31 Bold; large title 20/25 Bold; medium
  title 16/20 Bold; large body 16/24 Medium; medium body 14/20 Medium; labels 15/18, 13/16, and
  11/14 Bold.
- Base spacing rhythm: 4 px with 8/12/16/24/32/48 steps.
- Minimum target: approximately 48×48 dp.
- Cards/board: 24–30 px radii; buttons: 16 px; compact icon controls: circular.
- Use quiet tonal depth and restrained soft shadows; no glassmorphism, fake metal, neon glow, or
  glossy 3D controls.

### 8.4 Board objects

- Rail Darts use a short, thick navy capsule stem, rounded triangular head, and split-tail rail
  detail. They are not generic Material icons.
- Magnets use an abstract circular split-ring/rail core, never emoji or a horseshoe magnet.
- Walls are dense charcoal blocks.
- Grid lines are subtle; cells are not individually raised tiles.
- The board needs outer clearance so exit animation is not clipped.

Current renderer details:

- the board is white with subtle `#C9D3DF` grid lines and a `#D7DEE7` rounded outline;
- walls/blocks are `#39414D` rounded squares at 58% of cell size with a translucent white inset;
- Rail Darts are `#183153`; selected/tutorial arrows use a cyan pulse and outline;
- Pull magnets use cyan rings, inward chevrons, and a `PULL` text label; Push magnets use amber
  rings, outward chevrons, and `PUSH`;
- route trails inherit Pull cyan, Push amber, or navy when unaffected; hints use cyan; and
- collisions use the red `#B84343` impact ring. High-Contrast Fields increases ring opacity and
  stroke width without changing rules.

### 8.5 Motion targets

| Moment | Target |
|---|---:|
| Tap response | under 50 ms |
| Cell traversal | approximately 70–110 ms per cell, bounded |
| Polarity flip | 220–320 ms |
| Collision/rewind | 250–400 ms |
| Completion transition | 450–700 ms after final route lands |

Reduced Motion replaces bounce, particles, curves, and rotation with short fades/direct motion and
an instant polarity swap without removing essential feedback.

Completion celebration is performance-sensitive and occurs only after the board is cleared. A
clean three-star solve with no hint or overload receives the strongest short full-screen falling-
confetti overlay, card emoji pair, and varied praise line. Other two/three-star clears receive a
lighter contextual overlay and message; rough one-star clears remain intentionally calm. The
variant is derived deterministically from the stable level identity and attempt metrics, never
from gameplay randomness. Reduced Motion keeps a static emoji/message for strong play and removes
both overlay and card-particle movement. Unit and Compose UI tests verify the performance tiers and
the reduced-motion behavior.

### 8.6 Voice

Preferred: `Board cleared`, `Find the sequence`, `The field flipped`, `Path blocked`,
`Try another arrow`, `Clean solve`.

Avoid IQ claims, urgency, guilt, jackpot/casino language, “You failed,” “Only 1% can solve,” and
misleading reward language.

### 8.7 Current Home and Game screen composition

Home uses a deliberately sparse hierarchy: the compact coin balance is at top left, Settings is
an icon-only circular action at top right, the centered brand/tagline sits immediately above one
primary `Play · Level N` button with the actual selected difficulty, and Daily Challenge follows
below. It does not expose separate Continue, Level Select, or Progressive Journey actions.
Debug builds append an isolated Human Difficulty Playtest card; it is absent from release UI.

The approved Game screen is implemented as native Compose UI rather than a bitmap mockup:

- a compact top header uses circular Home and Settings icon actions around the centered level
  context and puzzle title;
- arrows remaining, actions, and overloads share one quiet raised HUD;
- the current prompt and Pull/Push legend are compact semantic chips, with text/geometry retaining
  meaning independently of color;
- the board receives the largest flexible region and retains the production renderer, hit targets,
  paths, animations, and tutorial focus layer unchanged;
- Restart is secondary and Hint is the primary action inside a raised bottom dock; Hint states the
  actual 30-coin cost or `AD`, spends directly through the existing guarded flow, and is disabled
  when the solver reports no completable route. A full-width secondary Skip action clearly states
  `AD` and `+10 coins` before playback and appears only for Campaign/Infinite play;
- deadlock does not display a failure banner or force a restart, leaving the player free to inspect
  the board or restart voluntarily;
- the between-game completion state dynamically recognizes strong play with deterministic emoji,
  contextual praise, and a bounded full-screen confetti overlay while keeping ordinary clears
  restrained; and
- large text stacks dense rows/buttons vertically, while Reduced Motion and accessibility semantics
  remain authoritative.

---

## 9. Implemented product systems

### M0 — Rule harness

- Pure immutable game model and engine.
- Route tracing and complete resolution output.
- JSON parsing/validation.
- Production-engine solver and state keys.
- Deterministic replay and rule tests.

### M1 — Playable gray-box Android app

- Jetpack Compose/Canvas board.
- Shared board geometry for draw, hit-testing, animation, and semantics.
- Result-driven animation.
- Restart, level navigation, completion, and non-blocking internal deadlock detection.
- All original 12 prototype levels reachable.

### M2 — Presentation and accessibility vertical slice

- Rail Dart and split-ring magnet identity.
- Pull/Push fields with redundant non-color cues.
- Home, level selection, gameplay, completion, and Settings.
- Solver-backed non-mutating hints from the exact current state.
- Sound and haptic semantic feedback.
- Reduced Motion, High-Contrast Fields, Path Preview Assistance, Sound, and Haptics settings.
- Versioned DataStore progress/settings.

### M3 — Content and retention

- Checked-in campaign content and deterministic offline generator/certifier.
- Explicit seeded PRNG and bounded generation profiles.
- Content hashes, versions, origins, seeds, profiles, grading, and mechanic tags.
- Stars, records, local coins/rewards, paid-with-coins hints.
- Offline deterministic Daily Challenge, fallback bank, cache, and streak.
- Reproducible `:level-tools` staging/promotion/certification commands.

### M4 — Ads, consent, diagnostics

- UMP consent refresh/orchestration and Privacy options.
- One voluntary rewarded-hint placement.
- Conservative campaign-boundary interstitial policy.
- Full-screen coordination and fake/no-op providers.
- Typed analytics abstraction and consent/diagnostics gating.
- Crash reporting abstraction and safe non-sensitive keys.
- Offline/no-consent/no-fill behavior remains fully playable.

### M5 — Release hardening

- Central release identity and production input validation.
- R8/resource shrinking, no cleartext, no backup, manifest verification.
- Sample/test ID rejection from production release artifacts.
- Baseline/startup profile infrastructure.
- 16 KB page-size and native-library audits.
- Release/store/privacy/closed-test/rollout documentation.
- Local structural release evidence exists, but production upload remains blocked.

### M5.1 / M5.2 / Phase 0 / Phase 1 — Content analysis and expansion

- Difficulty v2 and v3 analyzers, independent Quality scores, exact and symmetry fingerprints,
  pacing/recovery audits, player-choice diagnostics, forced-sequence metrics, and human-review
  priority.
- Campaign expanded through 100, 150, and then 200 levels using staged pools and explicit
  promotion manifests.
- Content and preference migrations preserve stable IDs and earned player value.
- No Phase 2 mechanic or later feature was implemented.

### D1 — Difficulty V4 and human calibration

- The full 200-level content-v6 campaign was audited after the owner completed it and reported that
  every level was easy.
- Difficulty V4 was implemented as a structural diagnostic emphasizing consequential decisions,
  harmful choices, mandatory ordering, polarity consequences, recovery, commutation, persistent
  consequences, greedy/random resistance, and confidence/truncation.
- Forty board-fingerprint-bound owner ratings were recorded: 32 Trivial, 4 Very Easy, 2 Easy,
  1 Moderate, and 1 Challenging. No automated process was treated as a human rating.
- Calibration results were: V3 Pearson `0.3319`, Spearman `0.5852`, MAE `62.47`; V4 Pearson
  `0.5939`, Spearman `0.5718`, MAE `8.68`. V4 improved absolute prediction but remains preliminary.
- The audit found 98.61% safe successful choices, 1.39% meaningful failure, 99.10% permutation
  redundancy, 91.25% viable-pair commutation, 183/200 stable-order greedy solves, and 92.72%
  random-success completion in content v6.

### D2 — Generator V5, deterministic selector prototype, and promotion

- Generator V5 added explicit structural profiles, typed interaction graphs, object-removal
  relevance, dependency/polarity/exposure diagnostics, staging catalogs, bounded deterministic
  generation, and duplicate fingerprints.
- `DifficultySelectionV1` and `PlayerSkillStateV1` exist only as offline deterministic prototypes.
  They are not integrated into app UI, persistence, or runtime campaign routing.
- D2 generated and certified 200 staged boards, then owner-directed guarded promotion replaced all
  200 production boards while preserving stable IDs and archiving old fingerprint-bound records.
- Production moved to content version 7 / generator version 5. The old content-v6 source remains
  archived. No runtime generation was introduced.
- D2 automated aggregate evidence improved substantially: safe-choice ratio `0.8252`, meaningful
  failure `0.1748`, harmful-decision density `0.7325`, relevant-object ratio `0.7245`, interaction
  density `0.3288`, mean dependency depth `4.17`, mean polarity-impact depth `3.46`, mean ordering
  depth `2.505`, greedy solve rate `0.4283`, random-success rate `0.4742`, and permutation
  redundancy `0.8432`.
- The promoted distribution is 12 Tutorial, 35 Easy, 45 Medium, 55 Hard, 40 Expert, and 13 Master.
  None of the content-v7 boards has an owner difficulty rating, so automated promotion is not human
  approval.

### D2.1 — Spatial density and current Generator V5 repair

- D2.1 introduced explicit occupancy/object-count profiles, physical interaction diagnostics,
  long-range relationship reporting, participating-wall checks, exposure depth, persistent
  consequences, and dense-but-trivial rejection.
- The first Expert staging attempt failed because a small 17-object puzzle was surrounded by 47
  strategically irrelevant occupancy objects, including 41 `shielded-filler-*` magnets.
- A later benchmark temporarily passed only after Expert/Master staging thresholds were reduced;
  that historical result is superseded as quality evidence. The required Expert gates are restored
  to interaction density `>= 0.04`, relevant-object ratio `>= 0.28`, average relevance `>= 0.11`,
  at least 3 meaningful distance-four relationships, and meaningful ordering `>= 0.20`.
- The focused dependency-complete repair now authors three real long-range controller corridors,
  verifies declared semantic edges against reachable production-engine states, verifies those
  edges before/after geometry transformation, and refuses filler-based repair when no
  structure-preserving mutation exists.
- V5.2 keeps the repaired ordered-polarity weave but removes the inert wall shell. The known Expert
  and Master seeds now pass the unchanged solver, replay, V4, structural-density, relevance, wall,
  and Quality gates. Historical failed measurements remain documented in Sections 11.7–11.9.

### Campaign V10 — density remediation, promotion, and human rejection

- The Campaign V9 playtest rejected the original six-band calibration and confirmed that 34 tested
  high-band boards came from only two base arrow/core templates despite unique full-board D4 hashes.
- V10 preserved Levels 1–205 and rebuilt Levels 206–2,205 from their V9 cores. It added deterministic
  horizontal, vertical, or mixed ordered arrow cascades, then only enough solution-preserving walls
  to reach the profile density floor.
- V10 raised the 2,000 remediated boards from 14,250 to 25,509 arrows and mean occupancy from 41.4%
  to 66.9%. Exact boards, D4 boards, arrow silhouettes, and arrow-plus-magnet layouts are unique
  within the remediated set, with no collisions against Infinite.
- V10 deliberately used mechanical human-calibration certification: profile shape/count/density,
  a production-engine solution witness, immutable replay, and fingerprints. Automated difficulty
  gates were disabled, so the six labels were candidates rather than certified human difficulty.
- The owner-directed promotion moved the canonical campaign to content version 10 while recording
  each V9 fingerprint as `previousContentFingerprint`.
- The subsequent 68-board V10 run rejected the labels again. This is the current production-content
  difficulty finding; visual uniqueness and density did not create human-perceived difficulty.
- On 2026-08-23 the owner explicitly authorized reuse of the exact V10 catalog under a hash-bound
  legacy mechanical waiver. The six labels remain as owner-defined navigation bands, not certified
  human difficulty; the direct CSV and rejection summary remain retained evidence. This waiver is
  `LEGACY_MECHANICAL_OWNER_APPROVED`, never `AUTOMATED_CAMPAIGN_CERTIFIED`.

### V11 — isolated human-difficulty pilot, not production

- V11 starts from newly generated puzzle cores rather than remediating V9/V10 cores.
- Candidate generation is bounded by opening-branch, winning-order, meaningful-failure,
  consequence-depth, ordering-depth, topology-family, count-variation, perceptual-template, and
  collision gates. Five observed mechanic families are represented: `BLOCKER_WEAVE`,
  `CANCELLATION_RELEASE`, `POLARITY_LOCK`, `WALL_OCCLUSION`, and `MIXED_INTERLOCK`.
- The checked-in pilot currently contains 53 of the intended 60 boards: ten each for Easy, Medium,
  Hard, Super Hard, and Expert, plus three Master. It has 53/53 exact, D4, arrow-layout,
  interactive-layout, and perceptual fingerprints and no production/Infinite exact or D4 collision.
- The pilot is content version 11 and generator version 5. Its source evidence is retained as an
  excluded archive, but its generator/profile/runtime integration has been reverted. It is not
  promoted, does not replace any of the 2,205 production boards, and does not prove
  that its candidate bands are perceptually separated.

---

## 10. Current campaign content

Canonical file: `docs/Magnetrail_Campaign_Levels_v3.json`.

| Property | Current value |
|---|---|
| Catalog schema | 2 |
| Rule version | `magnetrail-core-1` |
| Catalog ID | `magnetrail-campaign-v3` |
| Content version | 10 |
| Generator version | 5 |
| Level count | 2,205 |
| SHA-256 | `8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9` |
| Stable range | `proto-001`…`proto-012`, then campaign IDs through `campaign-2205` |
| Current metadata origin | 2,205 `GENERATOR_ASSISTED` |
| Board sizes | 2 × 3×3; 109 × 4×4; 611 × 5×5; 226 × 6×6; 82 × 7×7; 1,175 × 8×8 |
| Exact fingerprints | 2,205 unique |
| Symmetry fingerprints | 2,205 unique |
| Total authored cells | 104,391 |
| Occupied cells | 69,098 (66.19%) |
| Arrows | 26,472 total; 1–18 per board; mean 12.01 |
| Magnets | 13,887 total; 0–45 per board; mean 6.30 |
| Initial magnet polarity | 13,187 Pull; 700 Push |
| Walls/blocks | 28,739 total; 0–47 per board; mean 13.03 |
| Legacy metadata bands | 12 Intro; 750 Developing; 1,443 Advanced |
| Per-level metadata content | 200 at v7; 5 at v8; 2,000 at v10 |

Current packs:

| Range | Pack |
|---|---|
| 1–2,200 | `magnetic-circuit-01` through `magnetic-circuit-110`, 20 levels each |
| 2,201–2,205 | `magnetic-circuit-111` |

Metadata mechanic-tag counts:

- Magnet control: 2,199
- Polarity dependency: 2,191
- Walls: 2,160
- Occlusion: 202
- Order dependency: 2,188
- Exposure/reveal: 202
- Cancellation: 52

These are metadata claims backed by automated analysis, not proof that a player experiences the
claimed mechanic as difficult or central.

Campaign object totals by grid:

| Grid | Levels | Arrows | Magnets | Walls/blocks |
|---|---:|---:|---:|---:|
| 3×3 | 2 | 2 | 3 | 1 |
| 4×4 | 109 | 599 | 205 | 146 |
| 5×5 | 611 | 4,893 | 1,587 | 2,222 |
| 6×6 | 226 | 2,264 | 663 | 1,691 |
| 7×7 | 82 | 820 | 330 | 696 |
| 8×8 | 1,175 | 17,894 | 11,099 | 23,983 |

Arrow printed-direction totals are East 7,293, North 6,360, South 5,980, and West 6,839. Two
campaign boards have no magnets and 45 have no walls. Wall and block mean the same permanent
`Wall` entity throughout this document.

The V10 portion, Levels 206–2,205, is balanced by candidate profile: Easy 334, Medium 334, and 333
each for Hard, Super Hard, Expert, and Master. Those names are generation-profile identities, not
accepted human difficulty labels. The production UI metadata still collapses Tutorial to `INTRO`,
Easy/Medium to `DEVELOPING`, and Hard or above to `ADVANCED`.

Other checked-in board catalogs:

| Catalog | Status | Levels | Arrows | Magnets | Walls/blocks | SHA-256 |
|---|---|---:|---:|---:|---:|---|
| Daily fallback v1 | Production fallback | 7 | 21 | 7 | 26 | `3f21a633fa1e51d29938b830fe567103a52354e367d3177c20b8196c881156c8` |
| Infinite v1 | Production, pre-certified | 624 | 2,800 | 1,707 | 1,901 | `c3500d87eedfa7cdbfdde1000bf49ae9af5c2a0e0c24c4ce5c7d977b5d6b4f83` |
| V11 pilot | Debug/staging only | 53 | 404 | 228 | 588 | `035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184` |

Section 25 provides the per-board object inventory for all four catalogs.

### 10.1 Historical human difficulty failure and D2 replacement

The project owner/player manually completed all 200 levels and reported:

> “the difficulty level is very poor.”

Read-only follow-up analysis found a strong explanation:

- Across all 200 levels, 2,620 of 2,665 canonical plausible choices were strategically viable:
  **98.3%**.
- Only 45 choices were classified as deceptive-but-fair.
- The analyzers counted 15,100 solution families in aggregate.
- Many nominal Hard/Expert boards offer numerous interchangeable winning orders, so branching and
  solution length inflated numeric difficulty without forcing consequential planning.
- Levels 151–200 alone had 891 viable choices out of 907 plausible choices and 8,500 solution
  families.

This demonstrates the difference between technical branching and player-facing decisions in the
archived content-v6 campaign. The content-v6 source is retained at
`docs/content/d2/promotion/D2_SOURCE_CONTENT_V6.json`.

Current disposition:

- All 200 boards were replaced by the owner-directed D2 promotion while retaining their stable
  production IDs.
- Content-v7 structural diagnostics show lower safe-choice, greedy, random-success, and permutation
  redundancy measurements, but these remain automated evidence.
- The 40 historical D1 ratings are fingerprint-bound to content v6 and are excluded from v7
  calibration.
- Content-v7 Levels 1–200 and content-v8 Levels 201–205 remain without current fingerprint-bound
  owner difficulty ratings. Content-v10 Levels 206–2,205 have a partial 68-board owner run that
  rejects the assigned six-band calibration; it is negative evidence, not approval.
- Levels 201–204 are current V5-certified. Level 205 is solver-certified and V4-complete but not
  structurally certified; the explicit owner waiver and failed gates are preserved in the manifest.

---

## 11. Difficulty, Quality, and review model

### 11.1 Required player-choice reporting

Report choices from information available to the player before a move:

- plausible choices;
- immediately invalid choices;
- strategically viable choices;
- deceptive-but-fair choices;
- guess-dependent choices.

A technically legal action is not automatically a meaningful branch. Guess-dependent difficulty
must be penalized. Current human evidence also shows that a large number of viable branches must
not automatically increase difficulty when they are interchangeable winning permutations.

### 11.2 Sequence reporting

Always separate:

- total solution length;
- forced sequence length;
- number of decision nodes;
- average decision spacing;
- maximum forced-run length.

A long forced sequence is not equivalent to a long decision-making sequence.

### 11.3 Other Difficulty v3 inputs

- effective branching;
- dependency depth and dependency edges;
- solution-family count/constraint;
- dead-end proof depth and backtracking pressure;
- multi-stage mechanic interaction;
- Pull/Push/flips/controller changes;
- occlusion/cancellation/wall relevance;
- route length and purposeful space;
- complete-search/truncation/confidence evidence.

### 11.4 Independent Quality

Quality is separate from difficulty. It checks solvability, complete search, replay, schema,
fingerprints, duplicates, mechanic relevance, non-triviality, forced-run excess, purposeful space,
guess dependence, readability, curriculum position, grading consistency, and analysis caps.

Automated `ACCEPT` means structural gates passed. It is not proof of fun, fairness, readability,
or suitable human difficulty.

### 11.5 Human Review Priority

Prioritize manual review using:

- difficulty confidence;
- solver truncation;
- unusual branching;
- extreme numeric difficulty;
- low Quality margin;
- novel structural pattern;
- similarity to another level;
- new mechanic interaction;
- unusual solution depth.

Automated approval must never be recorded as human approval.

### 11.6 Difficulty V4 authority and calibration limits

Difficulty V4 is the current structural difficulty authority for generation/certification. It does
not replace the production engine, Quality analysis, or human playtesting. Its most important
distinction is between raw legal/solution branching and choices whose outcomes materially change
future solvability, actionability, ordering, polarity, controller visibility, or recovery.

V4 expands the reachable production-engine state graph, enumerates bounded winning sequences,
tests action commutation, evaluates greedy and fixed-seed random policies, and runs polarity/object
counterfactuals. Its provisional 0–100 score is the rounded positive weighted sum of meaningful
failure, harmful-decision density, consequence persistence, mandatory ordering, polarity
actionability, greedy/random resistance, recovery pressure, and decision density, minus penalties
for safe choices, permutation redundancy, excessive forced runs, and irrelevant structure. The
score becomes null when essential search/strategy/ordering analysis is incomplete. There is no
authoritative V4 numeric score-to-human-band conversion.

The 40 D1 human ratings apply only to their archived content-v6 fingerprints. They must not be
joined to content-v7 or staging candidates by stable ID alone because the physical boards changed.
No automated label, V4 band, solver score, or generator profile may be written as a human rating.
Campaign V10 goes further: its profiles explicitly disable automated difficulty gates, and its
mechanical calibration certifier proves only profile shape/count/density, a concrete solution
witness, immutable replay, and metadata. Therefore a V10 `master` profile is not evidence that the
board is Master difficulty.

### 11.7 Expert/Master ordered-polarity topology — 2026-08-20

Generator V5 now selects one deterministic topology family named
`EXPERT_ORDERED_POLARITY_V1` only for the exact D2.1 Expert and Master profile IDs. It uses real
must-before-reveal traps, polarity flips, exposure chains, a polarity-dependent corridor gate, and
a wall-shielded competing field. Master adds a deeper must/reveal stage. Easy through Very Hard
remain on their previous constructor paths.

| Metric | Previous topology | New Expert | New Master |
|---|---:|---:|---:|
| Difficulty V4 score | 5 | 59 | 66 |
| Ordering depth | 0 | 3 | 5 |
| Mandatory-ordering ratio | 0.0000 | 0.1944 | 0.4444 |
| Safe-choice ratio | 0.9372 | 0.6619 | 0.5154 |
| Wall occlusion participation | 0 | 2 | 2 |
| Solvable and replayable | Yes | Yes | Yes |
| V4 complete | Yes | Yes | Yes |
| Certified | No | No | No |

The original failure mode is therefore repaired: ordering is no longer zero, safe choices are
materially lower, polarity changes affect later actionability, and walls participate in physical
LOS relationships. Semantic construction edges are verified before and after seeded symmetry
transformation.

Certification is still blocked. Expert verifies only two of the three required long-range
relationships and its direct diagnostics remain below later interaction/relevance targets. Master
passes its ordering/V4 requirements but remains below object-participation, interacting-object,
average-relevance, and participating-wall-ratio gates. An attempted third independent Expert probe
made counterfactual sequence analysis truncate; it was removed rather than weakening V4 or hiding
truncation. See `docs/development/MAGNETRAIL_EXPERT_MASTER_TOPOLOGY_FIX_V1.md`.

### 11.8 Expert/Master occupancy experiment — 2026-08-20

A focused staging experiment tested the existing high-band topology at 0%, 5%, 10%, and 15%
allowed empty space for Expert, plus 0%, 5%, 10%, 15%, and 20% for Master. Every board remained
solvable and V4-complete, but every configuration produced the identical V4 score `5`, 7 meaningful
decisions, dependency depth `0`, mandatory ordering `0.0`, safe-choice ratio `0.9372`, zero
participating walls, and the same two certification failures: excessive safe choices and missing
ordering depth.

The occupancy hypothesis is therefore rejected for the current constructor. Its attempt seed only
reflects/rotates one fixed high-band topology, and the removed walls are isolated from the reachable
action graph. Expert and Master occupancy remain unchanged at 100%; the temporary relaxation was
not retained. See `docs/development/MAGNETRAIL_EXPERT_MASTER_OCCUPANCY_EXPERIMENT.md`.

### 11.9 Expert/Master causal-density topology V5.1 — 2026-08-20

V5.1 retains `EXPERT_ORDERED_POLARITY_V1` and extends only its Expert geometry. `eastGate`, an
opposing long-range controller, and two wall-shielded competing fields join the existing top-row
causal corridor. The physical verifier confirms all nine declared edges and the seeded transform
preserves them.

Expert improved from V4 `59` to `61`, safe-choice ratio `0.6619` to `0.5996`, mandatory ordering
`0.1944` to `0.2000`, long-range relationships `2` to `3`, wall occlusions `2` to `3`, interaction
density `0.0248` to `0.0303`, relevant-object ratio `0.2031` to `0.2344`, and average relevance
`0.0538` to `0.0604`. Ordering depth remains `3`, solver/V4 analysis remains complete, and the
former long-range and ordering gates now pass.

Master remains on its complete V5 depth-5 topology. A one-action extension improved density but
caused `COUNTERFACTUAL_OBJECT_SEQUENCE_ENUMERATION_CAP`; a no-extra-action controller variant
failed physical semantic verification. Both were rejected before final acceptance. Master remains
V4 `66`, safe-choice `0.5154`, ordering depth `5`, interaction density `0.0283`, relevant-object
ratio `0.2500`, average relevance `0.0615`, and two wall occlusions.

Certification remains blocked for causal density, object participation/relevance, and wall ratio.
See `docs/development/MAGNETRAIL_EXPERT_MASTER_TOPOLOGY_V5_1.md`.

### 11.10 Content-v8 append promotion — 2026-08-20

The owner explicitly directed an append of all available non-Master content, including the
uncertified Expert. Levels 1–200 remain identical to the archived content-v7 definitions; new IDs
`campaign-201` through `campaign-205` were appended. Levels 201–204 pass the current V5 pipeline.
Level 205 is the current V5.1 Expert topology reconstructed from seed `11510013`; it is solver and
replay valid, V4 complete/non-truncated, and unique, but remains structurally rejected for five
interaction/relevance/wall-participation gates. Master is excluded. Human playtesting was not
performed and automated human approvals are zero.

The exact source snapshot, authorization, fingerprints, per-level statuses, and Expert rejection
reasons are under `docs/content/v5_1_append/promotion/`. The recoverable content-v7 source SHA-256
is `8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`.

### 11.11 Multi-topology Generator V5 architecture — 2026-08-20

The offline generator no longer represents high-band generation as one constructor entry point.
It now has versioned logical identities, a causal-graph fingerprint, a topology registry, a
board-realizer boundary, an exact/structural duplicate-filtered candidate pool, a profile-bounded
purposeful-empty policy, and mandatory post-materialization physical semantic verification.

Four deterministic high-band families are represented:

- `ORDERED_POLARITY_V1`, the retained V5.1 complete-analysis topology;
- `CAUSAL_POLARITY_TAIL_V2`, a physically distinct causal extension that is correctly rejected
  when V4 sequence enumeration truncates;
- `ORDERED_POLARITY_STAIRCASE_V3`, a separate alternating must/reveal construction grammar. Its
  focused diagnostic reached complete V4 scores `70`/`71`, ordering rate `1.0`, ordering depths
  `10`/`12`, and relevant-object ratios `0.2812`/`0.3125`, but it remains staging-only because it
  does not meet all unchanged long-range/density/wall-participation gates.
- `ORDERED_LONG_RANGE_WEAVE_V4`, a bounded ten-action topology that makes selected existing
  controllers and walls participate in verified LOS relationships without adding independent
  actions. It reached complete V4 scores `61`/`67`, safe-choice ratios `0.5996`/`0.5154`, ordering
  rates `0.20`/`0.4444`, and ordering depths `3`/`5` for Expert/Master. It remains staging-only:
  Expert still fails interaction density, object participation, average relevance, and wall
  participation; Master fails object participation and wall participation.

An additional six-cell long-range corridor experiment was physically valid but caused incomplete
V4 analysis. It was removed rather than retained as a slow failing generation path. Expert/Master
occupancy profiles and all V4/certification thresholds remain unchanged. The result is an
extensible, fail-closed architecture—not a claim that Expert or Master certification is complete.

The V4 weave's declared physical contract is checked both before and after symmetry transform. A
proposed Master wall-to-arrow relation was removed when the verifier disproved it. The bounded
certification run had zero solver failures, zero V4 truncations, and zero ordering failures; it
stopped after one candidate per profile in accordance with the no-brute-force rule.
The family remains explicitly reproducible but is excluded from automatic bounded attempts so a
known structural rejection does not impose repeated counterfactual-analysis cost.

### 11.12 Purposeful-space Generator V5.2 certification — 2026-08-20

The earlier percentage-based empty-cell experiment removed arbitrary isolated walls and therefore
could not change topology. V5.2 instead retains the existing V4 causal weave, identifies its
required route/LOS guards, and omits the inert shell around that component. Every omitted cell is
deterministically declared; semantic edges are still physically verified before/after transform.

| Metric | Certified Expert | Certified Master |
|---|---:|---:|
| Known-seed V4 score | 63 | 68 |
| Safe-choice ratio | 0.5996 | 0.5154 |
| Mandatory ordering depth | 5 | 5 |
| Interaction density | 0.1552 | 0.1453 |
| Relevant-object ratio | 0.5517 | 0.5517 |
| Average object relevance | 0.1940 | 0.2202 |
| Meaningful wall occlusions | 5 | 6 |
| Solver/V4 complete; truncated | Yes; No | Yes; No |
| Unchanged-gate certificate | Pass | Pass |

Four unique variants of each band are packaged in the Infinite catalog. Difficulty V4, gameplay,
the numbered campaign, and all non-high-band generator profiles are unchanged. This is automated
structural certification only; human ratings remain required.

### 11.13 Campaign V9 expansion — 2026-08-21

The owner directed a 2,000-level numbered-campaign expansion. Levels 1–205 are preserved
definition-for-definition, and new stable IDs `campaign-206` through `campaign-2205` are appended.
The new sequence interleaves Easy, Medium, Hard, Super Hard, Expert, and Master. Its frozen
allocation is Easy 334, Medium 334, and 333 each for Hard, Super Hard, Expert, and Master.

Every appended board passed the production engine, complete solver and replay, Difficulty V4,
Quality V2, structural and relevance gates. Across the final campaign, exact fingerprints are
2,205/2,205 unique and rotation/reflection-normalized fingerprints are 2,205/2,205 unique. New
boards have zero exact or symmetry collisions with the separate 624-board Infinite catalog.
Content-v9 progress migration moves a player who completed Level 205 to Level 206 without changing
their prior completion or reward records. Automated tier labels and certificates are not human
difficulty ratings or human playtest approval.

The source snapshot, full per-level generation audit, report, and promotion result are under
`docs/content/v9_expansion/`. The content-v8 source SHA-256 is
`6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`; the promoted content-v9
SHA-256 is `3f8415f30b721dd97c21becca130b84839810b03d2eeba3f9a26a7d054452f3a`.

### 11.14 Campaign V10 density remediation and promotion — 2026-08-21

V10 preserved Levels 1–205 and remediated 2,000 V9 boards. For each source board it selected a
seeded `HORIZONTAL_CASCADES`, `VERTICAL_CASCADES`, or `MIXED_CASCADES` topology, added a required
arrow cascade plus seeded free-cell arrows, found a production-engine solution with bounded
memoized depth-first search, and added only solution-preserving walls until the density floor was
met. Candidate retry identity is deterministic from the base seed, level number, and retry.

V10 rejects exact boards, D4-normalized full boards, D4-normalized arrow silhouettes, and
D4-normalized arrow-plus-magnet layouts that collide with the campaign or Infinite catalogs. All
2,000 remediated arrow silhouettes and interactive layouts are unique by those fingerprints. This
fixed visual duplication at the measured fingerprint levels but did not guarantee different
dependency graphs or human solving strategies.

The campaign was owner-directed promoted to content version 10 with SHA-256
`8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`. The six labels were explicitly
pending new blinded human review at promotion time.

### 11.15 Campaign V10 human difficulty rejection — 2026-08-21

The checked-in CSV `docs/magnetrail-playtest-pet-52c263fb.csv` currently contains 68 observations
from one participant, all completed. Directly computed rating evidence is:

| Assigned candidate | n | Perceived ratings | Median perceived |
|---|---:|---|---:|
| Easy | 12 | 12 Easy | 1.0 Easy |
| Medium | 11 | 11 Easy | 1.0 Easy |
| Hard | 11 | 8 Easy, 1 Medium, 1 Hard, 1 Super Hard | 1.0 Easy |
| Super Hard | 11 | 9 Easy, 2 Medium | 1.0 Easy |
| Expert | 11 | 9 Easy, 2 Medium | 1.0 Easy |
| Master | 12 | 6 Easy, 5 Medium, 1 Hard | 1.5 Easy/Medium |

Only 26/68 ratings (38.2%) were within one band of the assigned label; 55/68 were Easy. The run
recorded 150 overloads, 54 restarts, and 10 hints, with all ten hints on one Hard observation. The
CSV's current SHA-256 is
`276fa032aec81cceefc8271b5ef6d5e3a65b2425c5d746716378e14fb1dda845`.

The derived `V10_HUMAN_PLAYTEST_REJECTION.md` currently contains two stale details: it says all
boards used no hints and records a different CSV hash. Direct CSV data is authoritative until that
derived note is regenerated. Neither discrepancy changes the rejection conclusion.

Structural follow-up found that all 11 tested Super Hard boards shared one D4-normalized
non-remediation core, all 11 Expert boards shared one, and all 12 Master boards shared one. The 34
high-band observations came from only two base arrow/core templates. Median opening-valid choices
were 11/14, 12/16, and 11/18 for Super Hard, Expert, and Master. The current conclusion is explicit:
density, total arrows, walls, geometric uniqueness, solvability, and long forced replay length are
not accepted proxies for human difficulty.

### 11.16 V11 isolated pilot — current staging state

V11 attempts to correct the documented V10 failure without modifying production content. It uses
new cores, stricter opening-choice and winning-order caps, Difficulty V4 behavioural gates,
observed mechanic-family checks, object-count variation, a longest-straight-run limit, edge-arrow
concentration limit, and five independent duplicate/perceptual fingerprints.

Current pilot state:

| Candidate | Boards | Grid | Arrows | Magnets | Walls | Winning orders |
|---|---:|---|---:|---:|---:|---:|
| Easy | 10 | 4×4–5×5 | 3–5 | 1–3 | 1–5 | 1–40 |
| Medium | 10 | 5×5–6×6 | 4–7 | 2–4 | 3–10 | 1–42 |
| Hard | 10 | 6×6–7×7 | 6–9 | 3–5 | 5–13 | 2–21 |
| Super Hard | 10 | 7×7–8×8 | 8–10 | 5–6 | 11–18 | 5–70 |
| Expert | 10 | 8×8 | 10–11 | 4–6 | 17–21 | 18–483 |
| Master | 3 | 8×8 | 10–12 | 5–7 | 19–22 | 4–109 |

The 53-board catalog is incomplete against its target of 60 because Master has only three accepted
candidates. Its current status is
`V10_DIFFICULTY_REJECTED_V11_PARTIAL_PILOT_PENDING_HUMAN_REVIEW`. It is now evidence-only: the app
does not package it and no generator command remains. No V11 human result exists in the repository,
and no V11 promotion is authorized.

---

## 12. Content generation and promotion safety

For every batch larger than 20 levels:

1. Generate candidates into staging only.
2. Analyze solver, difficulty, Quality, player-choice, purposeful-space, pacing, fingerprints, and
   similarity evidence.
3. Produce reports.
4. Show proposed KEEP/TUNE/REPLACE decisions.
5. Wait for explicit owner approval.
6. Only then promote exact approved candidates into checked-in content.

Never silently overwrite existing campaign levels. Normal builds/tests cannot rewrite campaign
JSON. Generated candidates must not be mislabeled handcrafted. Human review/playtesting status
must remain explicit.

Generator contract:

- `generatorVersion + seed + profile + template/content inputs` produces deterministic canonical
  content.
- `SeededRandom` is a frozen SplitMix64-style explicit PRNG seeded with stable profile material.
- Generation and solver work are bounded by attempts/state caps.
- Certification validates schema, uses the production engine/solver, replays a solution, checks
  failed-action immutability, validates profile targets, and emits fingerprints/metadata.
- Runtime never presents uncertified generated campaign content.
- Generator V5 construction contracts are obligations, not authority. Declared semantic edges must
  be observed on reachable production-engine states after materialization and after any geometry
  transformation.
- A repair is acceptable only when replay, complete solvability, required physical semantic edges,
  ordering/polarity/exposure evidence, long-range relationships, and participating-wall structure
  are preserved. When no safe operator exists, repair must decline instead of mutating.
- A solvable candidate that fails V4 or structural gates remains rejected staging content.
- Campaign V10 is a recorded exception only in the sense that its human-calibration profiles
  explicitly disabled automated difficulty gates. Its promoted labels are now human-rejected and
  must not be used as a precedent for bypassing difficulty gates in another campaign replacement.
- D4 visual uniqueness is insufficient. New candidate systems must also constrain causal-core,
  interaction/dependency-graph, winning-strategy, meaningful-decision-trace, and perceptual-template
  similarity so geometry cannot disguise the same puzzle.
- Never generate another 2,000-board campaign before a small blinded pilot demonstrates separable
  human bands.

Useful commands:

```text
./gradlew generateLevelCandidates
./gradlew certifyCampaignContent
./gradlew analyzeCampaignDifficulty
./gradlew analyzeCampaignQuality
./gradlew checkCampaignSymmetryDuplicates
./gradlew auditCampaignPacing
./gradlew finalizePhase0
./gradlew finalizePhase1
./gradlew generateCampaignV5Candidates
./gradlew analyzeCampaignGenerationV5
./gradlew analyzeObjectRelevanceV5
./gradlew analyzeInteractionGraphsV5
./gradlew analyzeCampaignDifficultyV4
./gradlew testAdaptiveDifficultySelection
./gradlew probeCampaignV10Remediation
./gradlew generateCampaignV10Remediation
```

Promotion tasks require explicit confirmation properties and are not dependencies of normal builds.
Gradle configuration cache is enabled in `gradle.properties`; focused generator work should use
the narrowest relevant test/task before any bounded multi-profile benchmark.

Recommended replacement direction, not implemented: a Generator V6 should author a causal
dependency graph first, realize it spatially with constraint/backtracking search, use solver-guided
mutation plus a MAP-Elites/novelty archive, reject semantic graph/strategy duplicates, and assign
difficulty only after behavioural analysis and blinded human calibration. The production engine,
state-key solver, replay verifier, and content-migration protections should be retained. This is a
documented design recommendation, not current production code or authorization to promote content.

---

## 13. Grading, progress, and economy

### 13.1 Action accounting and stars

- Count each arrow tap that reaches engine resolution, including failed launches.
- Do not count ignored taps, animation-blocked taps, controls, hints, Restart, or navigation.
- Restart begins a new zeroed attempt.

Current grading version: 1.

- 3 stars: `actions <= parActions` and zero hints.
- 2 stars: `actions <= twoStarMaxActions`.
- 1 star: any completion.
- Default par is the certified clean solution length.
- Default two-star maximum is `par + max(2, ceil(par * 0.25))`.

Best stars can only improve. Lowest actions, overloads, and hints are retained by board fingerprint.
When a board fingerprint changes, earned value is preserved and incomparable old best records are
archived as legacy records.

### 13.2 Economy

Economy version: 3.

| Rule | Value |
|---|---:|
| Starting balance | 150 coins |
| First campaign clear | 10 coins |
| Each newly earned star | 0 coins |
| First Daily clear for an identity | 10 coins |
| First completion or rewarded skip of each Progressive/Infinite level ordinal | 10 coins |
| First rewarded skip of an uncleared campaign level | 10 coins |
| Solver hint | 30 coins |

- First-clear/skip rewards are idempotent; stars remain grading/progress evidence and grant no coins.
- Replays do not repeat already-earned rewards.
- Balance never becomes negative.
- Hint spend and usable hint display are atomic; no usable hint means no coin charge.
- Restart remains free.
- Currency can assist but never gates campaign/Daily progression.

The historical 100-level simulation reported minimum balances of 150/150/120 for clean,
occasional-hint, and every-level-hint scenarios. It is deterministic configuration evidence, not a
forecast for the current 200-level economy or future rewarded coins.

### 13.3 Persistence

Current preference schema: 7.

Persisted local state includes:

- highest unlocked level, completed stable IDs, and last selection;
- current-board best records and fingerprinted legacy records;
- first-clear reward IDs and coin balance;
- Daily cache, completions, rewards, streak, and trusted date;
- content/generator/Daily/economy versions;
- Sound, Haptics, Reduced Motion, High-Contrast Fields, Path Preview Assistance, Diagnostics;
- interstitial counters/dates and rewarded-hint transaction/cap state.
- Infinite selected ID/difficulty/ordinal, completed count, streak, and bounded 100-entry history.

DataStore migrations are idempotent and validate/clamp corrupt values. Completing Level 150 under
content version 5 unlocks/selects Level 151 once after migration to version 6. A player who already
completed Level 200 under version 7 unlocks/selects Level 201 once after migration to version 8.

There is no account, cloud sync, or backend recovery. Clearing storage/uninstalling removes local
progress, subject to platform behavior and the app’s disabled backup policy.

### 13.4 Infinite Mode progress

Infinite identity combines Generator V5, profile, seed, content SHA-256, catalog/selector versions,
rules, and Difficulty V4 analyzer version. DataStore persists the selected stable ID so process
restart resumes the same unfinished board. Since app backup is disabled, uninstall clears history;
a clean reinstall still reproduces the same ordinal-zero board for the same chosen difficulty.
Each Infinite journey ordinal grants 10 coins exactly once when first completed or voluntarily
skipped after a verified rewarded ad. Replay and duplicate completion/reward callbacks for that
ordinal grant zero; the same certified board may earn the reward again
only when the deterministic selector assigns it to a different journey ordinal. Infinite clears do
not modify campaign unlocks/completions/records or Daily state.

---

## 14. Daily Challenge

Daily Challenge is offline, deterministic, free, and separate from numbered campaign progression.

Identity contract:

```text
dailyId = localDate + "-v" + dailyGeneratorVersion
seed = first signed 64 bits of SHA-256(
  "Magnetrail|" + localDate + "|" + generatorVersion + "|" + fixedPublicSalt
)
```

Current Daily generator version: 1. The fixed public salt is
`magnetrail-daily-public-salt-v1`.

- Uses injected local date/zone behavior.
- Runs deterministic bounded generation off the main thread.
- Caches the certified result by identity/fingerprint.
- Falls back deterministically to one of seven checked-in certified fallback boards.
- Never requires an ad, coin spend, purchase, network, or accurate server clock.
- Daily reward is granted once per Daily identity.
- Same-day completion does not increase streak twice.
- Consecutive trusted dates increase streak; a gap restarts it at one.
- Backward/equal dates never grant duplicate reward or increase streak.

Historical host benchmark: 31 dates, median 0 ms, p95 2 ms, maximum 14 ms, five to seven explored
states. Lower-end device performance still requires real-device evidence.

---

## 15. Hints

The solver evaluates the exact current `BoardState`, including current polarities.

- It runs off the main thread and is cancellable.
- It returns/highlights one deterministic solver-valid first action.
- It never launches the arrow automatically.
- Optional path preview is derived from a real engine resolution.
- A hint does not mutate board state.
- Stale results are discarded after move, Restart, or level change.
- The hint counter increases only when a usable hint is shown.
- At balances of 30 coins or more, tapping Hint atomically spends 30 coins and shows the solver
  hint directly; there is no intermediate choice or confirmation dialog.
- Below 30 coins, no balance is deducted and the Hint control uses the voluntary rewarded-ad path.
- The Hint control is disabled/debounced while a hint/ad transaction is in progress.

---

## 16. Existing advertising and consent

Current implemented advertising formats:

1. Rewarded ad for one solver hint credit.
2. Rewarded ad to skip the current Campaign/Infinite level and grant its 10-coin progression reward.
3. Interstitial at a natural campaign completion boundary.

No banners, native ads, app-open ads, rewarded interstitials, offerwalls, splash ads, pause/failure
ads, mediation, cross-promotion, or Billing exist.

### 16.1 Rewarded hint

- Explicit player opt-in: `Watch an ad for one hint`.
- Coin hint remains the predictable primary option.
- Grant occurs only after the SDK reward callback.
- Grant uses a durable unique local transaction and is idempotent.
- At most one unconsumed hint credit.
- Maximum five rewarded-hint grants per local day.
- Early dismissal grants nothing.
- Credit is consumed only when a usable solver hint is shown; solver failure/staleness preserves it.
- No ad availability failure penalizes or blocks the player.

### 16.2 Rewarded level skip

- Explicit player opt-in: `Skip level · AD · +10 coins` communicates the exchange before playback.
- Available only during unfinished Campaign and Infinite play; Daily Challenge cannot be skipped.
- Progression and 10 coins are committed only after the SDK reward callback.
- The unique reward transaction, completion/ordinal state, unlock, and balance update share one
  atomic DataStore edit; duplicate callbacks grant nothing twice.
- A skipped Campaign level is progression-complete but receives no fabricated star/action record.
- A skipped Infinite ordinal advances deterministically and resets the solved-level streak; its
  identity remains in bounded local history so restart cannot resume it.
- Early dismissal, missing consent, unavailable ads, SDK failure, or background state grants
  nothing and leaves the current level playable.

### 16.3 Interstitial policy

Interstitials may appear only after the player taps `Next level` on a rendered campaign completion
screen and all gates pass:

- at least 10 lifetime campaign completions;
- forward/first-clear progression, not replay;
- at least three eligible completions since the previous interstitial;
- 120-second full-screen/rewarded cooldown;
- fewer than four interstitials on the local date;
- consent permits requests;
- ad is already loaded;
- foreground/resumed expected screen;
- no other full-screen content.

If unavailable or failed, navigation continues immediately. Never wait or show a loading spinner
for an interstitial. Never show during gameplay, animation, failure, deadlock, Restart,
Daily Challenge, launch/resume, back navigation, or app exit.

### 16.4 Consent/privacy behavior

- UMP refreshes consent information once per process launch.
- `canRequestAds()` is the ad request gate.
- Required Privacy options are reachable from Settings.
- Raw TCF strings/geography are not stored or logged.
- Denial/error never blocks gameplay.
- A full-screen coordinator prevents consent/rewarded/interstitial overlap.
- Debug uses official Google test units.
- Live release ads remain blocked until production IDs, audience, privacy URL, account/console
  configuration, Firebase configuration, and signing inputs are valid.

---

## 17. Analytics and crash reporting

Analytics and Crashlytics are application-layer abstractions with no-op/fake implementations.
Collection defaults off and requires the effective consent policy plus local Diagnostics opt-in.

Tracked data is coarse and product-focused, including level start/complete/restart/deadlock, hint
spend/display, Daily start/complete, consent result, rewarded lifecycle, interstitial
eligibility/show/dismissal, and coarse ad failure categories.

Never log:

- name, email, phone, contacts, DOB, precise location, or free text;
- custom user ID, advertising ID, Firebase installation ID, or raw consent string;
- exact Daily date/seed;
- full board state/action sequence;
- raw ad payload or reward callback;
- purchase token (purchases are forbidden anyway).

Crash reports may include non-sensitive version/screen/state categories, but not board snapshots,
preferences, consent strings, ad payloads, or identifiers. Routine offline/no-fill behavior is not
a crash.

---

## 18. Final monetization rule: ads only

Magnetrail is a **free-to-play, ads-only game**.

Never implement:

- Google Play Billing;
- in-app purchases;
- Remove Ads purchase;
- subscriptions;
- paid level packs or unlocks;
- consumable coin purchases or premium currency;
- loot boxes or randomized paid rewards.

All campaign levels, Daily Challenge, and future Infinite Mode must remain playable without money
or mandatory ads.

The only permitted ad formats are interstitial and rewarded ads. The current M4 behavior should be
extended rather than replaced with a second architecture.

If the future ads-only Phase 8 is started, its approved new placement is:

> Watch an ad for 60 coins

Required future rules:

- maximum three rewarded coin grants per local day;
- centralized/versioned reward and frequency configuration;
- reward only from the verified SDK reward callback;
- atomic, idempotent local transaction across duplicate callback, activity recreation, background,
  and restart;
- no unlimited farming loop;
- existing rewarded hint and rewarded level skip remain unchanged;
- deterministic economy simulation before enablement;
- ads never gate Campaign, Daily, or Infinite Mode;
- tests use fake/test providers and never contact a real network.

Phase 8 has not been implemented.

---

## 19. Release posture

Current recommendation: **NO-GO for production upload**.

Engineering hardening exists:

- R8 optimization and resource shrinking;
- non-debuggable release;
- cleartext disabled;
- backup disabled;
- merged manifest verification;
- sample/test ad ID rejection from production artifacts;
- release configuration validation;
- Baseline Profile infrastructure;
- 16 KB native alignment checks;
- local bundletool/launch evidence from the earlier 100-level candidate.

However, historical M5 binary hashes/counts describe a 100-level artifact and are stale after the
content expansions. A new signed/release candidate must be rebuilt and re-audited after content is
accepted. More importantly, the current production Campaign V10 difficulty calibration is rejected;
the app is not content-ready for production merely because it builds or solves mechanically.

Open owner/external blockers:

- permanent package/version history confirmation;
- owner-authorized upload key and Play App Signing records;
- production AdMob IDs and UMP/target-audience/account configuration;
- genuine Firebase project/configuration or an explicit decision not to ship optional diagnostics;
- public HTTPS privacy policy and legal/support identity;
- Play Data safety, Ads, Advertising ID, target audience, content rating, category, and app access
  declarations;
- API 24, mid-range/API 35, tablet/foldable, accessibility, upgrade, consent/ad E2E, and current
  campaign QA;
- closed-test applicability/evidence and Play production-access requirements;
- current screenshots/store assets from the final accepted signed build;
- Play pre-launch/device-catalog and rollout decisions.

No key generation, Play upload, console mutation, live-ad impression, or production release action
is authorized by repository development.

---

## 20. Verification snapshot

The last full post-Phase-1 local regression recorded on the 200-level tree was:

```text
./gradlew :game-core:test :level-tools:test analyzeCampaignDifficulty \
  analyzeCampaignQuality checkCampaignSymmetryDuplicates auditCampaignPacing \
  certifyCampaignContent finalizePhase0 finalizePhase1 :app:testDebugUnitTest \
  :app:testReleaseUnitTest :app:compileDebugAndroidTestKotlin lintDebug lintRelease \
  :app:assembleDebug --continue
```

Result:

- `BUILD SUCCESSFUL`;
- 113 actionable tasks: 19 executed, 94 up-to-date;
- all 200 campaign levels and seven Daily fallbacks production-certified;
- exact and symmetry duplicates: zero;
- debug/release unit tests and lint passed;
- Android UI tests compiled;
- Android 37 AAR metadata checks passed;
- debug APK assembled;
- no real ad network contacted.

This historical regression does not override the later human finding that the archived content-v6
campaign was easy, and it does not constitute human evidence for the replacement content-v7
campaign. It also does not prove currently connected-device QA, production ads/consent/Firebase,
signing, store declarations, closed testing, or release readiness.

### 20.1 Latest focused Generator V5.1 verification

The 2026-08-20 V5.1 `EXPERT_ORDERED_POLARITY_V1` run deliberately stopped after one deterministic
candidate per high-band profile rather than running a large seed matrix.

Passing focused evidence:

- deterministic Expert/Master-only selection and canonical replay;
- complete solver and Difficulty V4 analysis without truncation;
- Expert V4 `61`, ordering depth `3`, safe-choice ratio `0.5996`;
- Master V4 `66`, ordering depth `5`, safe-choice ratio `0.5154`;
- Expert has three physical long-range relationships and three wall occlusions; Master retains one
  measured long-range relationship and two wall occlusions;
- declared exposure, state, polarity, and long-range edges survive materialization and transform;
- 100% occupancy remains in force; no occupancy relaxation was retained;
- Gradle configuration-cache entries were stored/reused.

Remaining failures:

- Expert now passes the three-long-range and `0.20` ordering pre-checks but remains short of
  interaction/object-relevance/participating-wall margins;
- Master fails object-participation, interacting-object-ratio, average-relevance, and
  participating-wall-ratio gates;
- no gate, V4 setting, gameplay rule, or campaign content was changed;
- at that point no broad benchmark, campaign promotion, release build, or device test had followed;
  the later content-v8 append is recorded in Sections 11.10 and 20.2.

### 20.2 Content-v8 append verification

The owner-directed append was verified with Gradle configuration cache enabled:

```text
./gradlew --configuration-cache :level-tools:test
./gradlew --configuration-cache :app:testDebugUnitTest :level-tools:certifyCampaign
./gradlew --configuration-cache build
```

All commands passed. The certification run verified 205 campaign levels and seven Daily fallbacks;
it surfaced rather than hid the exact Level 205 structural waiver. The full build completed 279
tasks (36 executed, 243 up-to-date) and produced local debug/release variants. The release remains
structural and non-uploadable because live ads, UMP, signing, and owner production configuration
are intentionally unavailable.

### 20.3 Master implementation verification

The 2026-08-20 player-flow and multi-topology update passed focused app/core/tools tests, Android
UI-test Kotlin compilation, release Kotlin compilation, `certifyCampaignContent`, and the full
Gradle build with configuration cache enabled. The full build completed 279 tasks (56 executed,
223 up-to-date). Campaign certification replayed all 205 campaign levels and seven Daily fallbacks
and explicitly reported the existing Level 205 structural waiver. The canonical content-v8 SHA-256
remained `6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`.

### 20.4 Bounded ordered-long-range weave verification

`ORDERED_LONG_RANGE_WEAVE_V4` was evaluated with one deterministic Expert candidate and one
Master candidate. Physical edge verification, canonical replay, and complete unchanged V4 analysis
passed. Expert produced score `61`, safe-choice `0.5996`, ordering rate `0.20`, and depth `3`;
Master produced score `67`, safe-choice `0.5154`, ordering rate `0.4444`, and depth `5`.

The unchanged certifier correctly returned zero certificates. Expert failed interaction density,
object participation, average object relevance, and wall participation. Master failed object
participation and wall participation. No seed sweep, campaign mutation, gate relaxation, or
automated-as-human approval followed. The canonical campaign SHA-256 remained
`6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`.

Final regression passed `:game-core:test :level-tools:test :app:testDebugUnitTest` with Gradle
configuration cache reused, followed by `certifyCampaignContent`: 205/205 campaign levels and all
seven Daily fallbacks were certified, with the existing Level 205 owner waiver surfaced.

### 20.5 Campaign V10 verification and disposition

The V10 generation report records 2,000/2,000 remediated boards, 2,205/2,205 exact and D4-unique
final boards, 2,000/2,000 unique remediated arrow silhouettes and interactive layouts, zero
Infinite collisions, content/generator version 10/5, and canonical campaign SHA-256
`8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`.

That mechanical evidence is superseded for difficulty approval by the V10 human run in Section
11.15. V10 remains the checked-in production catalog, but its six difficulty labels are rejected
and its high-band causal/template diversity is unacceptable.

### 20.6 V11 pilot verification and limitation

The checked-in V11 report records 53 pilot boards, with 53 unique exact, D4, arrow-layout,
interactive-layout, and perceptual fingerprints and zero production/Infinite exact or D4
collisions. The isolated catalog SHA-256 is
`035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184`.

This is only a preserved partial-pilot archive: the Master profile produced three of the intended
ten boards, no V11 human observations or compatible certificate are checked in, and no promotion
has occurred. Automated profile summaries and rejection counters are generation diagnostics, not
board certification or human difficulty approval.

---

## 21. Mandatory development safety

### 21.1 Strict phase isolation

- Implement only the active phase.
- Do not create speculative production code, placeholder future classes, migrations, UI,
  configuration, dependencies, or mechanics.
- Future documentation may be consulted, but functionality remains unimplemented until its phase.

### 21.2 Stop and report `BLOCKED` instead of guessing when

- production engine semantics are ambiguous;
- fingerprints cannot be preserved/migrated safely;
- solver and gameplay engine disagree;
- a new mechanic contradicts a frozen rule;
- player progress migration cannot be proven safe;
- difficulty cannot be measured reliably;
- an unexpected future-phase dependency is required;
- a required external API/policy cannot be verified from official documentation.

The current human difficulty mismatch is evidence that the existing numerical model is not a
reliable proxy for perceived difficulty. Do not use it unchanged to justify another bulk campaign
replacement.

### 21.3 Destructive/content safety

- Never silently overwrite checked-in campaign content.
- For batches larger than 20, publish diagnostics and the remediation manifest before promotion.
- Preserve stable IDs, content versions, fingerprints, player records, rewards, settings, Daily
  history, consent, and ad state.
- Keep source snapshots for any content migration.
- Do not weaken gates merely to make a candidate pool pass.

### 21.4 Testing honesty

- Never claim connected/device/manual/console testing that was not performed.
- Automated approval is not human approval.
- Human completion is not automatically positive difficulty/fairness approval.
- Automated tests use fake ads/analytics/clocks and never contact live services.
- Record exact commands/results and identify environment limitations.

---

## 22. Future roadmap — not implemented

All phases below are blocked from implementation until explicitly started and the active earlier
quality gate is resolved.

### Phase 2 — Insulator prototype

Proposed single new mechanic: a static non-colliding object that blocks magnetic line of sight but
does not block arrow travel. It has a stable ID/cell, never moves/flips/attracts/repels, and only
affects cancellation by hiding magnets. Required work includes versioned backward-compatible rules
and schema, diagnostics, rendering/accessibility, 20 non-campaign prototypes, relevance metrics,
and golden backward-compatibility tests. Not implemented.

### Phase 3 — 8×8 usability prototype

Create 12 non-campaign 8×8 boards and evaluate 360/390/430 dp phones plus tablet/foldable windows,
touch ambiguity, recognition, fields, paths, semantics, large text, contrast, reduced motion,
clipping, performance, and memory. Decide approved-for-phone, large-screen-only, or rejected. No
10×10 scope. Not implemented.

### Phase 4 — Levels 201–250

The original 250-level plan is superseded. Content-v8 added Levels 201–205, Campaign V9 added
Levels 206–2,205, and Campaign V10 subsequently remediated those 2,000 boards while preserving
Levels 1–205. No Insulator mechanic was introduced.

### Phase 5 — Levels 251–300

The original stop-at-300 plan is superseded. Levels 251–2,205 are implemented in Campaign V10, but
the balanced six-tier labels have been human-rejected and require replacement/recalibration.

### Phase 6 — Infinite Mode

Implemented as a separate deterministic offline mode, never Level 206/301. The single Home `Play`
button shows the next journey level number and actual selected difficulty, then launches or resumes
it directly; Home does not show Continue, Level Select, or a separate
Progressive Journey card. Daily Challenge remains on Home. The default Progressive Journey requests
compact certified Easy boards for Infinite Levels 1–5, followed by five guided Easy practice levels.
The ten lessons cover tap, blocking, magnetic control, polarity, ordering, scanning, visibility,
exposure, comparing choices, and combining the rules. Levels 1–4 have two arrows; Level 5 has three;
each of those compact boards has one valid opening and one solution family. A compatible authored
solution is taught step by step with an animated fingertip, pulsing focus ring, printed-direction cue,
scaled lesson diagram, and concise action prompt. Successful removals advance the finger; failed taps
leave it on the current step. The same lessons appear when numbered campaign Levels 1–10 are opened
directly. Tutorial animation never intercepts board input, respects Reduced Motion, and ends after
Level 10. Medium follows for
11–20, Hard for 21–30, then uses a deterministic shuffled rhythm spanning Easy, Medium, Hard,
Super Hard, Expert, and Master. Fixed-band choices remain available over a 624-board pre-certified
catalog (Easy 200, Medium 270, Hard 130, Expert 12, Master 12). Stable
puzzle identities, nearest-certified fallback selection, recent-fingerprint avoidance, and bounded
local history are implemented. Every Expert/Master row passed unchanged gates; there is no runtime
board generation. Each journey ordinal awards 10 coins once on its first completion.

### Phase 7 — Adaptive Infinite

Explicit opt-in only. Versioned local 0–100 skill estimate from a rolling window of 10 Infinite
attempts using completion, abandonment, restarts, overloads, hints, par delta, duration bucket, and
selected difficulty. Exclude ads, purchases, balance, device, age, identity, and campaign spending.
Bound changes, add recovery after measured struggle, allow reset/fixed mode. Not implemented.

### Phase 8 — Ads-only expansion

No Billing. Add only the bounded optional rewarded-coin placement and conservative centralized
interstitial configuration after economy simulation and fake-provider tests. Preserve optional
rewarded hints, consent, and full free access. Not implemented.

---

## 23. Canonical file map

| Concern | Canonical location |
|---|---|
| Consolidated context | `docs/MAGNETRAIL_COMPLETE_GAME_CONTEXT.md` |
| Existing gameplay rules | `docs/Magnetrail_Rules_Contract.md` |
| Original product specification | `docs/Magnetrail_Game_Design_Spec_v0.1.docx` |
| Android architecture | `docs/Magnetrail_Android_Technical_Brief.md` |
| UI/design system | `docs/Magnetrail_DESIGN.md` |
| Original prototypes | `docs/Magnetrail_Prototype_Levels_v1.json` |
| Current campaign | `docs/Magnetrail_Campaign_Levels_v3.json` |
| Daily fallbacks | `docs/Magnetrail_Daily_Fallbacks_v1.json` |
| Current development status/evidence | `docs/development/MASTER_DEVELOPMENT_STATUS.md` |
| Difficulty v3 specification | `docs/development/DIFFICULTY_V3_SPEC.md` |
| Difficulty V4 specification | `docs/development/MAGNETRAIL_DIFFICULTY_V4_SPEC.md` |
| Difficulty V4 audit | `docs/development/MAGNETRAIL_DIFFICULTY_V4_AUDIT.md` |
| Human calibration | `docs/development/MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md` |
| Full content-v6 difficulty diagnosis | `docs/development/MAGNETRAIL_DIFFICULTY_AUDIT.md` |
| Phase 0 final diagnostics | `docs/development/PHASE0_FINAL_DIAGNOSTICS.json` |
| Phase 1 final diagnostics | `docs/content/M5_3_FINAL_DIAGNOSTICS.json` |
| Full 200-level automated report | `docs/content/M5_3_FULL_200_REPORT.md` |
| Human review queue | `docs/content/M5_3_MANUAL_REVIEW.md` |
| D2 Generator V5 specification | `docs/development/D2_CAMPAIGN_GENERATION_SPEC.md` |
| D2 staging audit | `docs/development/D2_CAMPAIGN_GENERATION_AUDIT.md` |
| D2 candidate pool | `docs/content/d2/staging/D2_CAMPAIGN_V5_CANDIDATES.json` |
| D2 blind human review pool | `docs/content/d2/staging/D2_HUMAN_REVIEW_CATALOG.json` |
| D2 content-v6 source archive | `docs/content/d2/promotion/D2_SOURCE_CONTENT_V6.json` |
| D2 ID/progress migration | `docs/content/d2/promotion/D2_ID_MIGRATION.json` |
| D2 promotion result | `docs/content/d2/promotion/D2_PROMOTION_RESULT.md` |
| D2.1 spatial-density specification | `docs/development/MAGNETRAIL_D2_1_SPATIAL_DENSITY_SPEC.md` |
| D2.1 latest checked-in audit | `docs/development/MAGNETRAIL_D2_1_AUDIT.md` |
| Generator V5 repair specification | `docs/development/MAGNETRAIL_GENERATOR_V5_SPEC.md` |
| Historical Generator V5 repair audit | `docs/development/MAGNETRAIL_GENERATOR_V5_AUDIT.md` |
| Expert/Master topology fix v1 | `docs/development/MAGNETRAIL_EXPERT_MASTER_TOPOLOGY_FIX_V1.md` |
| Expert/Master topology V5.1 | `docs/development/MAGNETRAIL_EXPERT_MASTER_TOPOLOGY_V5_1.md` |
| Master implementation report | `docs/development/MAGNETRAIL_MASTER_IMPLEMENTATION_REPORT.md` |
| Infinite Mode specification | `docs/infinite/INFINITE_MODE_SPEC.md` |
| Infinite Mode QA | `docs/infinite/INFINITE_MODE_QA.md` |
| Infinite certified catalog | `docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json` |
| Infinite generation benchmark | `docs/infinite/INFINITE_GENERATOR_BENCHMARK.json` |
| Content-v8 append source snapshot | `docs/content/v5_1_append/promotion/SOURCE_CONTENT_V7.json` |
| Content-v8 append manifest | `docs/content/v5_1_append/promotion/V5_1_APPEND_PROMOTION_MANIFEST.json` |
| Content-v8 append result | `docs/content/v5_1_append/promotion/V5_1_APPEND_PROMOTION_RESULT.md` |
| Content-v9 source snapshot | `docs/content/v9_expansion/SOURCE_CONTENT_V8.json` |
| Content-v9 generation audit | `docs/content/v9_expansion/CAMPAIGN_V9_GENERATION_AUDIT.json` |
| Content-v9 promotion result | `docs/content/v9_expansion/CAMPAIGN_V9_PROMOTION_RESULT.md` |
| Campaign V10 source snapshot | `docs/content/v10_density_remediation/SOURCE_CONTENT_V9.json` |
| Campaign V10 generation audit | `docs/content/v10_density_remediation/CAMPAIGN_V10_GENERATION_AUDIT.json` |
| Campaign V10 generation report | `docs/content/v10_density_remediation/CAMPAIGN_V10_GENERATION_REPORT.md` |
| Campaign V10 promotion result | `docs/content/v10_density_remediation/CAMPAIGN_V10_PROMOTION_RESULT.md` |
| Campaign V9 human rejection | `docs/content/v10_density_remediation/V9_HUMAN_PLAYTEST_FINDINGS.md` |
| Campaign V10 human-playtest CSV | `docs/magnetrail-playtest-pet-52c263fb.csv` |
| Campaign V10 rejection summary | `docs/content/v11_pilot/V10_HUMAN_PLAYTEST_REJECTION.md` |
| V11 isolated pilot catalog | `docs/content/v11_pilot/V11_PILOT_CATALOG.json` |
| V11 pilot audit/report | `docs/content/v11_pilot/V11_PILOT_AUDIT.json`, `V11_PILOT_REPORT.md` |
| Human difficulty playtest protocol | `docs/HUMAN_DIFFICULTY_PLAYTEST_PROTOCOL.md` |
| M4 privacy/ad inventory | `docs/M4_COMPLIANCE_NOTES.md` |
| Analytics events | `docs/M4_EVENT_CATALOG.md` |
| Release blockers | `docs/release/RELEASE_BLOCKER_LOG.md` |
| Release requirements | `docs/release/M5_RELEASE_REQUIREMENTS.md` |

### Key production source areas

```text
game-core/src/main/kotlin/com/rameshta/magnetrail/core/
  model/ engine/ solver/ level/ generation/ difficulty/ quality/ grading/ economy/ daily/

app/src/main/java/com/rameshta/magnetrail/
  game/ home/ levels/ settings/ data/ daily/ feedback/ playtest/ ads/ privacy/ analytics/ crash/ release/

level-tools/src/main/kotlin/com/rameshta/magnetrail/tools/
```

---

## 24. Current unresolved issues

1. **Campaign V10 difficulty is rejected.** In the current 68-board human run, 55 ratings were Easy,
   Expert was 9 Easy/2 Medium, Master was 6 Easy/5 Medium/1 Hard, and only 26/68 ratings landed
   within one band of the assigned label. Master, Expert, Super Hard, and Hard frequently feel Easy.
2. **Geometric uniqueness did not produce semantic uniqueness.** V10 achieved exact, D4,
   arrow-layout, and interactive-layout uniqueness, yet the tested high bands still collapsed to two
   base cores. Current fingerprints do not canonicalize dependency graphs, meaningful-decision
   traces, or winning-strategy equivalence.
3. **The current algorithm emphasizes the wrong variables.** V10 primarily adds arrow cascades and
   density-preserving walls to an existing core, while its automated difficulty gates are disabled.
   Arrow count, occupancy, par, and one valid replay can rise without increasing human reasoning.
4. **Difficulty V4 remains provisional.** Its weighted structural score is useful for diagnostics
   and rejection, but it has no accepted mapping to six human bands. Historical content-v6 ratings
   cannot calibrate changed content-v7/v8/v10 fingerprints.
5. **V11 is archived and excluded.** The isolated pilot has 53/60 target boards because only three
   Master candidates were accepted. Its audit is pending human review and lacks the complete,
   analyzer-version-bound per-board evidence required for combined-campaign admission.
6. **Current playtest documentation has a small evidence mismatch.** The direct V10 CSV contains
   one observation with ten hints and hashes to `276fa032aec8…`; the derived rejection note says
   all observations used no hints and records another hash. Regenerate the derived note from the
   CSV before treating it as immutable evidence.
7. **Level 205 retains an explicit structural waiver.** It is solver/replay valid and V4-complete,
   but the waiver must remain visible in certification, testing, and any human-review dataset.
8. **No uploadable current release exists.** The recorded M5 AAB is stale; local release builds are
   deliberately structural/non-uploadable without owner production configuration.
9. **Production services are unconfigured.** Live AdMob, UMP console state, Firebase, privacy URL,
   audience choice, upload signing, and Play declarations remain blocked.
10. **Representative device/accessibility/release testing is incomplete.** API 24, mid-range/API 35,
   tablet/foldable, TalkBack/Switch Access, current upgrade, and production-like consent/ad tests
   require real evidence.
11. **Future game scope is intentionally absent.** Insulator, Adaptive Infinite, and ads-only Phase
   8 are documentation only. Campaign Levels 206–2,205 and Infinite Mode are implemented. The large
   8×8 campaign population still lacks representative-device human usability evidence; 9×9 remains
   excluded.

Do not resume, finish, remediate, or expand V11. Its 53-board source remains an excluded comparison
archive. Any future content work must begin from a separately authorized plan and must not treat
these generation diagnostics as certification.

---

## 25. Generated per-level object and metadata inventory

This inventory is generated from the exact checked-in catalogs named in each subsection. `A`
reports total arrows and printed directions as North/East/South/West. `M` reports total magnets and
initial Pull/Push counts. `W` is the permanent wall/block count. `Open` is certified valid first
actions; `Solns` is the metadata solution count followed by `+` when capped. Candidate band names
are derived from the immutable generation-profile identity and must not be read as human approval.
Exact cell coordinates, IDs, initial polarities, complete designed solution order, mechanic tags,
seed, and fingerprint remain in the source JSON.

### 25.1 Production Campaign V10 — 2,205 levels

Source: `docs/Magnetrail_Campaign_Levels_v3.json`, SHA-256
`8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`.

| # | ID | Grid | A (N/E/S/W) | M (Pull/Push) | W | Candidate profile | UI band | Par | Open | Solns | Pack |
|---:|---|---|---|---|---:|---|---|---:|---:|---:|---|
<!-- BEGIN GENERATED CAMPAIGN INVENTORY -->
| 1 | `proto-001` | 4×4 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-tutorial` | `INTRO` | 3 | 2 | 3 | `magnetic-circuit-01` |
| 2 | `proto-002` | 4×4 | 1 (N0/E0/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-tutorial` | `INTRO` | 1 | 1 | 1 | `magnetic-circuit-01` |
| 3 | `proto-003` | 3×3 | 1 (N0/E0/S0/W1) | 1 (Pull0/Push1) | 1 | `v5-tutorial` | `INTRO` | 1 | 1 | 1 | `magnetic-circuit-01` |
| 4 | `proto-004` | 4×4 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-tutorial` | `INTRO` | 3 | 3 | 6 | `magnetic-circuit-01` |
| 5 | `proto-005` | 3×3 | 1 (N1/E0/S0/W0) | 2 (Pull2/Push0) | 0 | `v5-tutorial` | `INTRO` | 1 | 1 | 1 | `magnetic-circuit-01` |
| 6 | `proto-006` | 4×4 | 3 (N0/E1/S1/W1) | 1 (Pull0/Push1) | 2 | `v5-tutorial` | `INTRO` | 3 | 1 | 1 | `magnetic-circuit-01` |
| 7 | `proto-007` | 4×4 | 2 (N1/E1/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-tutorial` | `INTRO` | 2 | 2 | 2 | `magnetic-circuit-01` |
| 8 | `proto-008` | 4×4 | 3 (N1/E0/S1/W1) | 1 (Pull0/Push1) | 2 | `v5-tutorial` | `INTRO` | 3 | 3 | 6 | `magnetic-circuit-01` |
| 9 | `proto-009` | 4×4 | 3 (N1/E2/S0/W0) | 1 (Pull0/Push1) | 2 | `v5-tutorial` | `INTRO` | 3 | 1 | 1 | `magnetic-circuit-01` |
| 10 | `proto-010` | 4×4 | 2 (N1/E1/S0/W0) | 0 (Pull0/Push0) | 2 | `v5-tutorial` | `INTRO` | 2 | 1 | 1 | `magnetic-circuit-01` |
| 11 | `proto-011` | 4×4 | 2 (N0/E0/S2/W0) | 2 (Pull2/Push0) | 2 | `v5-tutorial` | `INTRO` | 2 | 1 | 1 | `magnetic-circuit-01` |
| 12 | `proto-012` | 4×4 | 3 (N3/E0/S0/W0) | 0 (Pull0/Push0) | 0 | `v5-tutorial` | `INTRO` | 3 | 2 | 3 | `magnetic-circuit-01` |
| 13 | `campaign-013` | 5×5 | 4 (N0/E0/S2/W2) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-01` |
| 14 | `campaign-014` | 4×4 | 3 (N0/E1/S2/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 2 | 2 | `magnetic-circuit-01` |
| 15 | `campaign-015` | 8×8 | 4 (N3/E1/S0/W0) | 2 (Pull1/Push1) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-01` |
| 16 | `campaign-016` | 7×7 | 5 (N0/E1/S3/W1) | 6 (Pull4/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-01` |
| 17 | `campaign-017` | 6×6 | 5 (N1/E1/S1/W2) | 2 (Pull1/Push1) | 4 | `v5-hard` | `ADVANCED` | 5 | 3 | 15 | `magnetic-circuit-01` |
| 18 | `campaign-018` | 5×5 | 6 (N3/E2/S0/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 48 | `magnetic-circuit-01` |
| 19 | `campaign-019` | 8×8 | 5 (N1/E0/S0/W4) | 3 (Pull2/Push1) | 3 | `v5-master` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-01` |
| 20 | `campaign-020` | 5×5 | 4 (N0/E1/S1/W2) | 3 (Pull0/Push3) | 3 | `v5-easy` | `DEVELOPING` | 4 | 2 | 3 | `magnetic-circuit-01` |
| 21 | `campaign-021` | 5×5 | 4 (N0/E1/S1/W2) | 3 (Pull3/Push0) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-02` |
| 22 | `campaign-022` | 8×8 | 5 (N0/E1/S2/W2) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 40 | `magnetic-circuit-02` |
| 23 | `campaign-023` | 6×6 | 4 (N0/E0/S1/W3) | 2 (Pull1/Push1) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-02` |
| 24 | `campaign-024` | 8×8 | 5 (N1/E0/S2/W2) | 4 (Pull3/Push1) | 4 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-02` |
| 25 | `campaign-025` | 5×5 | 4 (N1/E1/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `magnetic-circuit-02` |
| 26 | `campaign-026` | 6×6 | 6 (N0/E2/S3/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 78 | `magnetic-circuit-02` |
| 27 | `campaign-027` | 8×8 | 6 (N1/E3/S2/W0) | 3 (Pull2/Push1) | 2 | `v5-master` | `ADVANCED` | 6 | 2 | 7 | `magnetic-circuit-02` |
| 28 | `campaign-028` | 5×5 | 5 (N2/E1/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 50 | `magnetic-circuit-02` |
| 29 | `campaign-029` | 5×5 | 6 (N3/E1/S0/W2) | 3 (Pull0/Push3) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 36 | `magnetic-circuit-02` |
| 30 | `campaign-030` | 4×4 | 4 (N1/E1/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `magnetic-circuit-02` |
| 31 | `campaign-031` | 6×6 | 5 (N2/E1/S0/W2) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 40 | `magnetic-circuit-02` |
| 32 | `campaign-032` | 7×7 | 5 (N1/E2/S0/W2) | 3 (Pull3/Push0) | 2 | `v5-expert` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-02` |
| 33 | `campaign-033` | 7×7 | 4 (N1/E1/S1/W1) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-02` |
| 34 | `campaign-034` | 6×6 | 3 (N0/E0/S2/W1) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-02` |
| 35 | `campaign-035` | 8×8 | 5 (N0/E2/S1/W2) | 4 (Pull3/Push1) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-02` |
| 36 | `campaign-036` | 4×4 | 3 (N0/E0/S1/W2) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-02` |
| 37 | `campaign-037` | 5×5 | 5 (N1/E0/S2/W2) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-02` |
| 38 | `campaign-038` | 5×5 | 5 (N0/E1/S1/W3) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `magnetic-circuit-02` |
| 39 | `campaign-039` | 8×8 | 4 (N0/E1/S3/W0) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-02` |
| 40 | `campaign-040` | 7×7 | 6 (N0/E1/S3/W2) | 6 (Pull3/Push3) | 2 | `v5-expert` | `ADVANCED` | 6 | 3 | 30 | `magnetic-circuit-02` |
| 41 | `campaign-041` | 5×5 | 5 (N3/E1/S1/W0) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 2 | 3 | `magnetic-circuit-03` |
| 42 | `campaign-042` | 5×5 | 5 (N1/E1/S0/W3) | 2 (Pull1/Push1) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-03` |
| 43 | `campaign-043` | 8×8 | 5 (N3/E0/S2/W0) | 3 (Pull1/Push2) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-03` |
| 44 | `campaign-044` | 8×8 | 5 (N1/E2/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-03` |
| 45 | `campaign-045` | 6×6 | 5 (N0/E1/S2/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-03` |
| 46 | `campaign-046` | 8×8 | 5 (N3/E1/S1/W0) | 3 (Pull2/Push1) | 4 | `v5-easy` | `DEVELOPING` | 5 | 5 | 50 | `magnetic-circuit-03` |
| 47 | `campaign-047` | 5×5 | 3 (N0/E1/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-03` |
| 48 | `campaign-048` | 7×7 | 5 (N1/E1/S3/W0) | 3 (Pull2/Push1) | 4 | `v5-expert` | `ADVANCED` | 5 | 2 | 20 | `magnetic-circuit-03` |
| 49 | `campaign-049` | 6×6 | 5 (N2/E1/S2/W0) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 17 | `magnetic-circuit-03` |
| 50 | `campaign-050` | 6×6 | 3 (N1/E0/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-03` |
| 51 | `campaign-051` | 8×8 | 5 (N0/E3/S0/W2) | 4 (Pull3/Push1) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-03` |
| 52 | `campaign-052` | 4×4 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-03` |
| 53 | `campaign-053` | 5×5 | 4 (N1/E2/S0/W1) | 4 (Pull2/Push2) | 3 | `v5-hard` | `ADVANCED` | 4 | 2 | 5 | `magnetic-circuit-03` |
| 54 | `campaign-054` | 8×8 | 5 (N1/E4/S0/W0) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 60 | `magnetic-circuit-03` |
| 55 | `campaign-055` | 6×6 | 6 (N2/E0/S4/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 6 | 4 | 60 | `magnetic-circuit-03` |
| 56 | `campaign-056` | 8×8 | 5 (N3/E0/S1/W1) | 5 (Pull4/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-03` |
| 57 | `campaign-057` | 6×6 | 5 (N1/E2/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 55 | `magnetic-circuit-03` |
| 58 | `campaign-058` | 5×5 | 4 (N1/E2/S1/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-03` |
| 59 | `campaign-059` | 8×8 | 5 (N1/E1/S2/W1) | 4 (Pull3/Push1) | 3 | `v5-master` | `ADVANCED` | 5 | 2 | 16 | `magnetic-circuit-03` |
| 60 | `campaign-060` | 5×5 | 4 (N0/E0/S3/W1) | 2 (Pull1/Push1) | 2 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-03` |
| 61 | `campaign-061` | 5×5 | 4 (N0/E2/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-04` |
| 62 | `campaign-062` | 5×5 | 5 (N0/E0/S1/W4) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `magnetic-circuit-04` |
| 63 | `campaign-063` | 5×5 | 3 (N3/E0/S0/W0) | 4 (Pull1/Push3) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-04` |
| 64 | `campaign-064` | 7×7 | 5 (N0/E1/S4/W0) | 2 (Pull2/Push0) | 3 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-04` |
| 65 | `campaign-065` | 6×6 | 4 (N1/E2/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-04` |
| 66 | `campaign-066` | 5×5 | 6 (N2/E4/S0/W0) | 1 (Pull0/Push1) | 2 | `v5-medium` | `DEVELOPING` | 6 | 2 | 6 | `magnetic-circuit-04` |
| 67 | `campaign-067` | 8×8 | 5 (N2/E0/S1/W2) | 2 (Pull2/Push0) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-04` |
| 68 | `campaign-068` | 8×8 | 3 (N2/E0/S1/W0) | 3 (Pull2/Push1) | 3 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-04` |
| 69 | `campaign-069` | 5×5 | 6 (N1/E4/S1/W0) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 44 | `magnetic-circuit-04` |
| 70 | `campaign-070` | 5×5 | 5 (N1/E0/S3/W1) | 1 (Pull0/Push1) | 4 | `v5-easy` | `DEVELOPING` | 5 | 3 | 25 | `magnetic-circuit-04` |
| 71 | `campaign-071` | 6×6 | 5 (N1/E3/S1/W0) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `magnetic-circuit-04` |
| 72 | `campaign-072` | 7×7 | 6 (N0/E1/S2/W3) | 4 (Pull2/Push2) | 3 | `v5-expert` | `ADVANCED` | 6 | 2 | 12 | `magnetic-circuit-04` |
| 73 | `campaign-073` | 6×6 | 6 (N0/E1/S1/W4) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 2 | 16 | `magnetic-circuit-04` |
| 74 | `campaign-074` | 5×5 | 5 (N2/E1/S1/W1) | 3 (Pull2/Push1) | 4 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `magnetic-circuit-04` |
| 75 | `campaign-075` | 8×8 | 5 (N2/E2/S1/W0) | 4 (Pull2/Push2) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 15 | `magnetic-circuit-04` |
| 76 | `campaign-076` | 8×8 | 5 (N1/E2/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 30 | `magnetic-circuit-04` |
| 77 | `campaign-077` | 5×5 | 6 (N3/E0/S3/W0) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 90 | `magnetic-circuit-04` |
| 78 | `campaign-078` | 5×5 | 5 (N1/E1/S1/W2) | 2 (Pull1/Push1) | 1 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `magnetic-circuit-04` |
| 79 | `campaign-079` | 5×5 | 6 (N0/E2/S3/W1) | 3 (Pull2/Push1) | 4 | `v5-medium` | `DEVELOPING` | 6 | 5 | 150 | `magnetic-circuit-04` |
| 80 | `campaign-080` | 7×7 | 5 (N0/E2/S0/W3) | 5 (Pull3/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 4 | 50 | `magnetic-circuit-04` |
| 81 | `campaign-081` | 5×5 | 6 (N0/E1/S3/W2) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 2 | 6 | `magnetic-circuit-05` |
| 82 | `campaign-082` | 5×5 | 5 (N2/E1/S0/W2) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `magnetic-circuit-05` |
| 83 | `campaign-083` | 8×8 | 7 (N2/E1/S1/W3) | 2 (Pull1/Push1) | 2 | `v5-master` | `ADVANCED` | 7 | 2 | 10 | `magnetic-circuit-05` |
| 84 | `campaign-084` | 8×8 | 4 (N1/E1/S2/W0) | 3 (Pull2/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-05` |
| 85 | `campaign-085` | 5×5 | 4 (N2/E0/S0/W2) | 5 (Pull2/Push3) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-05` |
| 86 | `campaign-086` | 4×4 | 4 (N2/E1/S1/W0) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 5 | `magnetic-circuit-05` |
| 87 | `campaign-087` | 5×5 | 6 (N2/E1/S2/W1) | 1 (Pull0/Push1) | 4 | `v5-medium` | `DEVELOPING` | 6 | 2 | 43 | `magnetic-circuit-05` |
| 88 | `campaign-088` | 7×7 | 6 (N2/E1/S2/W1) | 3 (Pull2/Push1) | 2 | `v5-expert` | `ADVANCED` | 6 | 3 | 40 | `magnetic-circuit-05` |
| 89 | `campaign-089` | 7×7 | 5 (N3/E1/S0/W1) | 2 (Pull1/Push1) | 2 | `v5-hard` | `ADVANCED` | 5 | 3 | 10 | `magnetic-circuit-05` |
| 90 | `campaign-090` | 5×5 | 5 (N0/E1/S2/W2) | 1 (Pull0/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 6 | `magnetic-circuit-05` |
| 91 | `campaign-091` | 8×8 | 5 (N1/E0/S3/W1) | 3 (Pull3/Push0) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-05` |
| 92 | `campaign-092` | 5×5 | 4 (N1/E2/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-05` |
| 93 | `campaign-093` | 6×6 | 5 (N1/E1/S1/W2) | 2 (Pull1/Push1) | 4 | `v5-hard` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-05` |
| 94 | `campaign-094` | 8×8 | 4 (N0/E1/S1/W2) | 2 (Pull1/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `magnetic-circuit-05` |
| 95 | `campaign-095` | 5×5 | 4 (N0/E1/S3/W0) | 2 (Pull1/Push1) | 3 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `magnetic-circuit-05` |
| 96 | `campaign-096` | 8×8 | 6 (N2/E1/S2/W1) | 3 (Pull3/Push0) | 2 | `v5-expert` | `ADVANCED` | 6 | 2 | 20 | `magnetic-circuit-05` |
| 97 | `campaign-097` | 6×6 | 4 (N1/E1/S1/W1) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-05` |
| 98 | `campaign-098` | 5×5 | 4 (N1/E0/S0/W3) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `magnetic-circuit-05` |
| 99 | `campaign-099` | 8×8 | 5 (N1/E1/S2/W1) | 4 (Pull2/Push2) | 3 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-05` |
| 100 | `campaign-100` | 5×5 | 5 (N1/E2/S0/W2) | 2 (Pull1/Push1) | 0 | `v5-easy` | `DEVELOPING` | 5 | 2 | 2 | `magnetic-circuit-05` |
| 101 | `campaign-101` | 5×5 | 5 (N0/E0/S1/W4) | 3 (Pull2/Push1) | 5 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-06` |
| 102 | `campaign-102` | 5×5 | 4 (N0/E1/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-06` |
| 103 | `campaign-103` | 6×6 | 3 (N0/E1/S2/W0) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 1 | 1 | `magnetic-circuit-06` |
| 104 | `campaign-104` | 7×7 | 5 (N2/E2/S1/W0) | 4 (Pull3/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 4 | 40 | `magnetic-circuit-06` |
| 105 | `campaign-105` | 8×8 | 4 (N2/E0/S1/W1) | 4 (Pull2/Push2) | 3 | `v5-hard` | `ADVANCED` | 4 | 2 | 4 | `magnetic-circuit-06` |
| 106 | `campaign-106` | 5×5 | 3 (N1/E0/S2/W0) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-06` |
| 107 | `campaign-107` | 8×8 | 5 (N0/E2/S2/W1) | 3 (Pull3/Push0) | 2 | `v5-master` | `ADVANCED` | 5 | 2 | 10 | `magnetic-circuit-06` |
| 108 | `campaign-108` | 5×5 | 4 (N0/E1/S3/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 2 | 2 | `magnetic-circuit-06` |
| 109 | `campaign-109` | 5×5 | 5 (N2/E1/S1/W1) | 5 (Pull1/Push4) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 15 | `magnetic-circuit-06` |
| 110 | `campaign-110` | 5×5 | 4 (N0/E0/S2/W2) | 3 (Pull1/Push2) | 0 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `magnetic-circuit-06` |
| 111 | `campaign-111` | 5×5 | 4 (N0/E1/S1/W2) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-06` |
| 112 | `campaign-112` | 7×7 | 5 (N0/E3/S2/W0) | 4 (Pull2/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-06` |
| 113 | `campaign-113` | 5×5 | 6 (N0/E3/S2/W1) | 4 (Pull1/Push3) | 3 | `v5-hard` | `ADVANCED` | 6 | 2 | 6 | `magnetic-circuit-06` |
| 114 | `campaign-114` | 8×8 | 4 (N0/E1/S2/W1) | 3 (Pull1/Push2) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-06` |
| 115 | `campaign-115` | 8×8 | 5 (N0/E1/S3/W1) | 3 (Pull3/Push0) | 2 | `v5-master` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-06` |
| 116 | `campaign-116` | 8×8 | 5 (N1/E2/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 25 | `magnetic-circuit-06` |
| 117 | `campaign-117` | 6×6 | 4 (N0/E2/S0/W2) | 3 (Pull2/Push1) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-06` |
| 118 | `campaign-118` | 5×5 | 5 (N0/E0/S4/W1) | 3 (Pull1/Push2) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 35 | `magnetic-circuit-06` |
| 119 | `campaign-119` | 5×5 | 4 (N0/E1/S1/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-06` |
| 120 | `campaign-120` | 8×8 | 5 (N0/E2/S1/W2) | 6 (Pull3/Push3) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-06` |
| 121 | `campaign-121` | 5×5 | 4 (N2/E0/S1/W1) | 4 (Pull1/Push3) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `magnetic-circuit-07` |
| 122 | `campaign-122` | 5×5 | 4 (N0/E2/S1/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-07` |
| 123 | `campaign-123` | 7×7 | 4 (N1/E2/S0/W1) | 4 (Pull2/Push2) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-07` |
| 124 | `campaign-124` | 8×8 | 5 (N3/E0/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `magnetic-circuit-07` |
| 125 | `campaign-125` | 6×6 | 6 (N2/E1/S2/W1) | 4 (Pull1/Push3) | 1 | `v5-hard` | `ADVANCED` | 6 | 4 | 36 | `magnetic-circuit-07` |
| 126 | `campaign-126` | 5×5 | 4 (N1/E0/S2/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 1 | 3 | `magnetic-circuit-07` |
| 127 | `campaign-127` | 5×5 | 5 (N0/E2/S1/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-07` |
| 128 | `campaign-128` | 8×8 | 6 (N1/E1/S4/W0) | 6 (Pull2/Push4) | 3 | `v5-expert` | `ADVANCED` | 6 | 2 | 18 | `magnetic-circuit-07` |
| 129 | `campaign-129` | 7×7 | 4 (N0/E0/S3/W1) | 5 (Pull3/Push2) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-07` |
| 130 | `campaign-130` | 5×5 | 6 (N0/E2/S3/W1) | 2 (Pull0/Push2) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 84 | `magnetic-circuit-07` |
| 131 | `campaign-131` | 7×7 | 5 (N1/E0/S3/W1) | 3 (Pull1/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-07` |
| 132 | `campaign-132` | 4×4 | 3 (N2/E0/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-07` |
| 133 | `campaign-133` | 7×7 | 5 (N2/E1/S0/W2) | 3 (Pull1/Push2) | 2 | `v5-hard` | `ADVANCED` | 5 | 2 | 20 | `magnetic-circuit-07` |
| 134 | `campaign-134` | 8×8 | 5 (N1/E1/S2/W1) | 1 (Pull0/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `magnetic-circuit-07` |
| 135 | `campaign-135` | 6×6 | 4 (N1/E0/S1/W2) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-07` |
| 136 | `campaign-136` | 8×8 | 6 (N0/E2/S2/W2) | 4 (Pull4/Push0) | 4 | `v5-expert` | `ADVANCED` | 6 | 4 | 60 | `magnetic-circuit-07` |
| 137 | `campaign-137` | 5×5 | 7 (N0/E2/S4/W1) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 7 | 4 | 104 | `magnetic-circuit-07` |
| 138 | `campaign-138` | 5×5 | 5 (N1/E0/S1/W3) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `magnetic-circuit-07` |
| 139 | `campaign-139` | 7×7 | 5 (N0/E2/S2/W1) | 3 (Pull1/Push2) | 3 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-07` |
| 140 | `campaign-140` | 8×8 | 4 (N0/E1/S1/W2) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-07` |
| 141 | `campaign-141` | 5×5 | 5 (N0/E2/S1/W2) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-08` |
| 142 | `campaign-142` | 8×8 | 4 (N0/E2/S2/W0) | 3 (Pull1/Push2) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-08` |
| 143 | `campaign-143` | 5×5 | 6 (N0/E2/S2/W2) | 1 (Pull0/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 30 | `magnetic-circuit-08` |
| 144 | `campaign-144` | 7×7 | 6 (N1/E0/S3/W2) | 4 (Pull2/Push2) | 2 | `v5-expert` | `ADVANCED` | 6 | 2 | 6 | `magnetic-circuit-08` |
| 145 | `campaign-145` | 7×7 | 4 (N0/E2/S0/W2) | 5 (Pull2/Push3) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-08` |
| 146 | `campaign-146` | 5×5 | 6 (N1/E2/S2/W1) | 2 (Pull0/Push2) | 1 | `v5-medium` | `DEVELOPING` | 6 | 4 | 104 | `magnetic-circuit-08` |
| 147 | `campaign-147` | 8×8 | 6 (N2/E1/S1/W2) | 4 (Pull2/Push2) | 2 | `v5-expert` | `ADVANCED` | 6 | 2 | 6 | `magnetic-circuit-08` |
| 148 | `campaign-148` | 5×5 | 4 (N0/E2/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `magnetic-circuit-08` |
| 149 | `campaign-149` | 6×6 | 4 (N1/E3/S0/W0) | 3 (Pull2/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-08` |
| 150 | `campaign-150` | 8×8 | 5 (N0/E1/S3/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-08` |
| 151 | `campaign-151` | 5×5 | 5 (N0/E1/S3/W1) | 3 (Pull1/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `magnetic-circuit-08` |
| 152 | `campaign-152` | 7×7 | 5 (N1/E1/S2/W1) | 3 (Pull2/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-08` |
| 153 | `campaign-153` | 5×5 | 5 (N1/E2/S2/W0) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 50 | `magnetic-circuit-08` |
| 154 | `campaign-154` | 6×6 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-08` |
| 155 | `campaign-155` | 7×7 | 5 (N1/E1/S1/W2) | 4 (Pull2/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-08` |
| 156 | `campaign-156` | 5×5 | 4 (N2/E1/S1/W0) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-08` |
| 157 | `campaign-157` | 5×5 | 5 (N2/E3/S0/W0) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 40 | `magnetic-circuit-08` |
| 158 | `campaign-158` | 7×7 | 6 (N3/E1/S1/W1) | 4 (Pull1/Push3) | 3 | `v5-expert` | `ADVANCED` | 6 | 2 | 32 | `magnetic-circuit-08` |
| 159 | `campaign-159` | 8×8 | 5 (N1/E1/S1/W2) | 1 (Pull0/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 4 | 40 | `magnetic-circuit-08` |
| 160 | `campaign-160` | 7×7 | 5 (N2/E1/S1/W1) | 4 (Pull2/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-08` |
| 161 | `campaign-161` | 5×5 | 4 (N1/E2/S0/W1) | 5 (Pull2/Push3) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-09` |
| 162 | `campaign-162` | 6×6 | 4 (N2/E0/S2/W0) | 2 (Pull1/Push1) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-09` |
| 163 | `campaign-163` | 7×7 | 5 (N1/E1/S2/W1) | 2 (Pull2/Push0) | 3 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-09` |
| 164 | `campaign-164` | 5×5 | 6 (N1/E1/S4/W0) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 6 | 2 | 24 | `magnetic-circuit-09` |
| 165 | `campaign-165` | 5×5 | 4 (N1/E1/S1/W1) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-09` |
| 166 | `campaign-166` | 7×7 | 5 (N0/E2/S2/W1) | 5 (Pull3/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 15 | `magnetic-circuit-09` |
| 167 | `campaign-167` | 5×5 | 5 (N4/E0/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-09` |
| 168 | `campaign-168` | 7×7 | 5 (N2/E1/S2/W0) | 5 (Pull4/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 40 | `magnetic-circuit-09` |
| 169 | `campaign-169` | 5×5 | 5 (N2/E2/S0/W1) | 2 (Pull1/Push1) | 5 | `v5-hard` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-09` |
| 170 | `campaign-170` | 5×5 | 5 (N2/E0/S2/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-09` |
| 171 | `campaign-171` | 8×8 | 5 (N0/E2/S2/W1) | 6 (Pull5/Push1) | 4 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-09` |
| 172 | `campaign-172` | 5×5 | 5 (N0/E0/S1/W4) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-09` |
| 173 | `campaign-173` | 7×7 | 5 (N1/E2/S2/W0) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 2 | 4 | `magnetic-circuit-09` |
| 174 | `campaign-174` | 7×7 | 5 (N2/E0/S3/W0) | 5 (Pull2/Push3) | 2 | `v5-expert` | `ADVANCED` | 5 | 4 | 30 | `magnetic-circuit-09` |
| 175 | `campaign-175` | 6×6 | 4 (N0/E3/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-09` |
| 176 | `campaign-176` | 8×8 | 5 (N4/E0/S1/W0) | 6 (Pull4/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 4 | 40 | `magnetic-circuit-09` |
| 177 | `campaign-177` | 5×5 | 4 (N2/E0/S0/W2) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `magnetic-circuit-09` |
| 178 | `campaign-178` | 5×5 | 5 (N0/E0/S2/W3) | 3 (Pull1/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 38 | `magnetic-circuit-09` |
| 179 | `campaign-179` | 7×7 | 5 (N1/E3/S1/W0) | 5 (Pull3/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 25 | `magnetic-circuit-09` |
| 180 | `campaign-180` | 6×6 | 4 (N1/E0/S2/W1) | 3 (Pull2/Push1) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-09` |
| 181 | `campaign-181` | 6×6 | 6 (N0/E2/S2/W2) | 2 (Pull1/Push1) | 9 | `v5-hard` | `ADVANCED` | 6 | 3 | 75 | `magnetic-circuit-10` |
| 182 | `campaign-182` | 8×8 | 6 (N3/E1/S2/W0) | 2 (Pull1/Push1) | 4 | `v5-expert` | `ADVANCED` | 6 | 2 | 15 | `magnetic-circuit-10` |
| 183 | `campaign-183` | 5×5 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `magnetic-circuit-10` |
| 184 | `campaign-184` | 7×7 | 6 (N2/E1/S2/W1) | 6 (Pull4/Push2) | 3 | `v5-expert` | `ADVANCED` | 6 | 3 | 40 | `magnetic-circuit-10` |
| 185 | `campaign-185` | 6×6 | 5 (N0/E1/S2/W2) | 4 (Pull4/Push0) | 3 | `v5-hard` | `ADVANCED` | 5 | 2 | 10 | `magnetic-circuit-10` |
| 186 | `campaign-186` | 5×5 | 3 (N0/E0/S2/W1) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `magnetic-circuit-10` |
| 187 | `campaign-187` | 7×7 | 5 (N2/E1/S1/W1) | 3 (Pull1/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 2 | 10 | `magnetic-circuit-10` |
| 188 | `campaign-188` | 5×5 | 6 (N2/E2/S2/W0) | 5 (Pull4/Push1) | 3 | `v5-hard` | `ADVANCED` | 6 | 3 | 18 | `magnetic-circuit-10` |
| 189 | `campaign-189` | 5×5 | 4 (N0/E2/S2/W0) | 5 (Pull4/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `magnetic-circuit-10` |
| 190 | `campaign-190` | 7×7 | 5 (N0/E2/S1/W2) | 3 (Pull2/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 3 | 40 | `magnetic-circuit-10` |
| 191 | `campaign-191` | 5×5 | 5 (N1/E3/S0/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `magnetic-circuit-10` |
| 192 | `campaign-192` | 7×7 | 5 (N3/E0/S0/W2) | 6 (Pull4/Push2) | 2 | `v5-expert` | `ADVANCED` | 5 | 2 | 5 | `magnetic-circuit-10` |
| 193 | `campaign-193` | 6×6 | 4 (N0/E1/S0/W3) | 4 (Pull3/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `magnetic-circuit-10` |
| 194 | `campaign-194` | 7×7 | 5 (N0/E1/S2/W2) | 3 (Pull3/Push0) | 3 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-10` |
| 195 | `campaign-195` | 5×5 | 6 (N1/E2/S1/W2) | 3 (Pull0/Push3) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 40 | `magnetic-circuit-10` |
| 196 | `campaign-196` | 7×7 | 5 (N2/E1/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-expert` | `ADVANCED` | 5 | 2 | 10 | `magnetic-circuit-10` |
| 197 | `campaign-197` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull2/Push1) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `magnetic-circuit-10` |
| 198 | `campaign-198` | 7×7 | 5 (N2/E2/S1/W0) | 3 (Pull1/Push2) | 3 | `v5-expert` | `ADVANCED` | 5 | 3 | 20 | `magnetic-circuit-10` |
| 199 | `campaign-199` | 5×5 | 6 (N0/E2/S2/W2) | 5 (Pull3/Push2) | 3 | `v5-hard` | `ADVANCED` | 6 | 2 | 6 | `magnetic-circuit-10` |
| 200 | `campaign-200` | 8×8 | 5 (N1/E1/S1/W2) | 3 (Pull2/Push1) | 4 | `v5-expert` | `ADVANCED` | 5 | 2 | 15 | `magnetic-circuit-10` |
| 201 | `campaign-201` | 4×4 | 2 (N1/E0/S0/W1) | 1 (Pull1/Push0) | 1 | `v5-d2.1-easy` | `DEVELOPING` | 2 | 2 | 2 | `magnetic-circuit-11` |
| 202 | `campaign-202` | 6×6 | 8 (N1/E6/S0/W1) | 18 (Pull13/Push5) | 10 | `v5-d2.1-medium` | `DEVELOPING` | 8 | 4 | 616 | `magnetic-circuit-11` |
| 203 | `campaign-203` | 7×7 | 8 (N4/E1/S2/W1) | 31 (Pull22/Push9) | 10 | `v5-d2.1-hard` | `ADVANCED` | 8 | 6 | 8400 | `magnetic-circuit-11` |
| 204 | `campaign-204` | 8×8 | 9 (N1/E5/S1/W2) | 45 (Pull31/Push14) | 10 | `v5-d2.1-very-hard` | `ADVANCED` | 9 | 5 | 10584 | `magnetic-circuit-11` |
| 205 | `campaign-205` | 8×8 | 10 (N0/E10/S0/W0) | 7 (Pull7/Push0) | 47 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `magnetic-circuit-11` |
| 206 | `campaign-206` | 5×5 | 7 (N3/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1050 | `magnetic-circuit-11` |
| 207 | `campaign-207` | 6×6 | 10 (N0/E6/S3/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 3 | 630 | `magnetic-circuit-11` |
| 208 | `campaign-208` | 5×5 | 10 (N3/E4/S0/W3) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 100000+ | `magnetic-circuit-11` |
| 209 | `campaign-209` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-11` |
| 210 | `campaign-210` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-11` |
| 211 | `campaign-211` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 100000+ | `magnetic-circuit-11` |
| 212 | `campaign-212` | 8×8 | 12 (N3/E8/S0/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 8514 | `magnetic-circuit-11` |
| 213 | `campaign-213` | 5×5 | 8 (N0/E5/S2/W1) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1624 | `magnetic-circuit-11` |
| 214 | `campaign-214` | 6×6 | 12 (N0/E1/S10/W1) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 100000+ | `magnetic-circuit-11` |
| 215 | `campaign-215` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-11` |
| 216 | `campaign-216` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 5 | 100000+ | `magnetic-circuit-11` |
| 217 | `campaign-217` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-11` |
| 218 | `campaign-218` | 8×8 | 12 (N1/E11/S0/W0) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1620 | `magnetic-circuit-11` |
| 219 | `campaign-219` | 5×5 | 8 (N1/E2/S1/W4) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 48 | `magnetic-circuit-11` |
| 220 | `campaign-220` | 6×6 | 12 (N8/E4/S0/W0) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 744 | `magnetic-circuit-11` |
| 221 | `campaign-221` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-12` |
| 222 | `campaign-222` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 100000+ | `magnetic-circuit-12` |
| 223 | `campaign-223` | 8×8 | 18 (N0/E12/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-12` |
| 224 | `campaign-224` | 5×5 | 7 (N2/E2/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 168 | `magnetic-circuit-12` |
| 225 | `campaign-225` | 5×5 | 8 (N1/E5/S2/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1104 | `magnetic-circuit-12` |
| 226 | `campaign-226` | 5×5 | 10 (N3/E1/S1/W5) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 32400 | `magnetic-circuit-12` |
| 227 | `campaign-227` | 8×8 | 14 (N6/E4/S4/W0) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-12` |
| 228 | `campaign-228` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-12` |
| 229 | `campaign-229` | 8×8 | 18 (N6/E6/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-12` |
| 230 | `campaign-230` | 4×4 | 6 (N0/E2/S0/W4) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 25 | `magnetic-circuit-12` |
| 231 | `campaign-231` | 8×8 | 14 (N2/E9/S1/W2) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 5 | 100000+ | `magnetic-circuit-12` |
| 232 | `campaign-232` | 7×7 | 14 (N8/E1/S3/W2) | 2 (Pull2/Push0) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-12` |
| 233 | `campaign-233` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-12` |
| 234 | `campaign-234` | 8×8 | 16 (N0/E6/S0/W10) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-12` |
| 235 | `campaign-235` | 8×8 | 18 (N4/E8/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-12` |
| 236 | `campaign-236` | 8×8 | 12 (N6/E2/S4/W0) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 100000+ | `magnetic-circuit-12` |
| 237 | `campaign-237` | 5×5 | 8 (N0/E2/S0/W6) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 5040 | `magnetic-circuit-12` |
| 238 | `campaign-238` | 5×5 | 10 (N5/E1/S4/W0) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 1620 | `magnetic-circuit-12` |
| 239 | `campaign-239` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-12` |
| 240 | `campaign-240` | 8×8 | 16 (N0/E10/S0/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-12` |
| 241 | `campaign-241` | 8×8 | 18 (N12/E0/S6/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-13` |
| 242 | `campaign-242` | 8×8 | 12 (N3/E6/S3/W0) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 100000+ | `magnetic-circuit-13` |
| 243 | `campaign-243` | 8×8 | 14 (N0/E13/S1/W0) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 100000+ | `magnetic-circuit-13` |
| 244 | `campaign-244` | 6×6 | 12 (N2/E6/S2/W2) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 17352 | `magnetic-circuit-13` |
| 245 | `campaign-245` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-13` |
| 246 | `campaign-246` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-13` |
| 247 | `campaign-247` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 4 | 100000+ | `magnetic-circuit-13` |
| 248 | `campaign-248` | 5×5 | 7 (N0/E5/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 630 | `magnetic-circuit-13` |
| 249 | `campaign-249` | 6×6 | 10 (N2/E3/S0/W5) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 19800 | `magnetic-circuit-13` |
| 250 | `campaign-250` | 6×6 | 12 (N5/E2/S4/W1) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 100000+ | `magnetic-circuit-13` |
| 251 | `campaign-251` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-13` |
| 252 | `campaign-252` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-13` |
| 253 | `campaign-253` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-13` |
| 254 | `campaign-254` | 4×4 | 6 (N5/E1/S0/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 45 | `magnetic-circuit-13` |
| 255 | `campaign-255` | 6×6 | 10 (N1/E4/S0/W5) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 50400 | `magnetic-circuit-13` |
| 256 | `campaign-256` | 5×5 | 10 (N3/E1/S5/W1) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 100000+ | `magnetic-circuit-13` |
| 257 | `campaign-257` | 8×8 | 14 (N8/E0/S6/W0) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-13` |
| 258 | `campaign-258` | 8×8 | 16 (N6/E6/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-13` |
| 259 | `campaign-259` | 8×8 | 18 (N6/E0/S12/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 5 | 100000+ | `magnetic-circuit-13` |
| 260 | `campaign-260` | 5×5 | 7 (N1/E1/S1/W4) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 105 | `magnetic-circuit-13` |
| 261 | `campaign-261` | 6×6 | 10 (N1/E8/S0/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 42750 | `magnetic-circuit-14` |
| 262 | `campaign-262` | 7×7 | 14 (N5/E0/S8/W1) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-14` |
| 263 | `campaign-263` | 8×8 | 14 (N0/E4/S4/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-14` |
| 264 | `campaign-264` | 8×8 | 16 (N0/E10/S0/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-14` |
| 265 | `campaign-265` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-14` |
| 266 | `campaign-266` | 8×8 | 12 (N9/E0/S2/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 11880 | `magnetic-circuit-14` |
| 267 | `campaign-267` | 5×5 | 8 (N1/E4/S2/W1) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 4040 | `magnetic-circuit-14` |
| 268 | `campaign-268` | 5×5 | 10 (N5/E3/S1/W1) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 16100 | `magnetic-circuit-14` |
| 269 | `campaign-269` | 8×8 | 14 (N4/E0/S6/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-14` |
| 270 | `campaign-270` | 8×8 | 16 (N0/E6/S0/W10) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-14` |
| 271 | `campaign-271` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-14` |
| 272 | `campaign-272` | 4×4 | 6 (N2/E1/S2/W1) | 1 (Pull0/Push1) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 45 | `magnetic-circuit-14` |
| 273 | `campaign-273` | 5×5 | 8 (N1/E4/S1/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 504 | `magnetic-circuit-14` |
| 274 | `campaign-274` | 5×5 | 10 (N1/E0/S9/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 3600 | `magnetic-circuit-14` |
| 275 | `campaign-275` | 8×8 | 14 (N4/E4/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-14` |
| 276 | `campaign-276` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-14` |
| 277 | `campaign-277` | 8×8 | 18 (N12/E0/S6/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-14` |
| 278 | `campaign-278` | 5×5 | 7 (N1/E0/S5/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 420 | `magnetic-circuit-14` |
| 279 | `campaign-279` | 5×5 | 8 (N2/E2/S2/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 280 | `magnetic-circuit-14` |
| 280 | `campaign-280` | 6×6 | 12 (N1/E1/S10/W0) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 100000+ | `magnetic-circuit-14` |
| 281 | `campaign-281` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-15` |
| 282 | `campaign-282` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-15` |
| 283 | `campaign-283` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-15` |
| 284 | `campaign-284` | 5×5 | 7 (N1/E2/S4/W0) | 3 (Pull1/Push2) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 525 | `magnetic-circuit-15` |
| 285 | `campaign-285` | 6×6 | 10 (N2/E2/S2/W4) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 63000 | `magnetic-circuit-15` |
| 286 | `campaign-286` | 5×5 | 10 (N1/E1/S6/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 15390 | `magnetic-circuit-15` |
| 287 | `campaign-287` | 8×8 | 14 (N4/E4/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-15` |
| 288 | `campaign-288` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-15` |
| 289 | `campaign-289` | 8×8 | 18 (N12/E0/S6/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 5 | 100000+ | `magnetic-circuit-15` |
| 290 | `campaign-290` | 8×8 | 12 (N3/E3/S2/W4) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 100000+ | `magnetic-circuit-15` |
| 291 | `campaign-291` | 6×6 | 10 (N2/E6/S2/W0) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 75600 | `magnetic-circuit-15` |
| 292 | `campaign-292` | 6×6 | 12 (N5/E4/S2/W1) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 100000+ | `magnetic-circuit-15` |
| 293 | `campaign-293` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-15` |
| 294 | `campaign-294` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-15` |
| 295 | `campaign-295` | 8×8 | 18 (N7/E4/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-15` |
| 296 | `campaign-296` | 4×4 | 6 (N0/E4/S0/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 25 | `magnetic-circuit-15` |
| 297 | `campaign-297` | 6×6 | 10 (N0/E8/S0/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 12600 | `magnetic-circuit-15` |
| 298 | `campaign-298` | 5×5 | 10 (N1/E2/S7/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 36230 | `magnetic-circuit-15` |
| 299 | `campaign-299` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-15` |
| 300 | `campaign-300` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-15` |
| 301 | `campaign-301` | 8×8 | 18 (N3/E6/S5/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-16` |
| 302 | `campaign-302` | 8×8 | 12 (N6/E2/S3/W1) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 100000+ | `magnetic-circuit-16` |
| 303 | `campaign-303` | 5×5 | 8 (N0/E6/S0/W2) | 3 (Pull3/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 8400 | `magnetic-circuit-16` |
| 304 | `campaign-304` | 5×5 | 10 (N0/E2/S7/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 26800 | `magnetic-circuit-16` |
| 305 | `campaign-305` | 8×8 | 14 (N4/E6/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-16` |
| 306 | `campaign-306` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-16` |
| 307 | `campaign-307` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 100000+ | `magnetic-circuit-16` |
| 308 | `campaign-308` | 5×5 | 7 (N3/E1/S0/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 840 | `magnetic-circuit-16` |
| 309 | `campaign-309` | 5×5 | 8 (N2/E2/S1/W3) | 3 (Pull3/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 376 | `magnetic-circuit-16` |
| 310 | `campaign-310` | 5×5 | 10 (N2/E3/S5/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 81360 | `magnetic-circuit-16` |
| 311 | `campaign-311` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-16` |
| 312 | `campaign-312` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-16` |
| 313 | `campaign-313` | 8×8 | 18 (N2/E6/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-16` |
| 314 | `campaign-314` | 5×5 | 7 (N2/E0/S2/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 735 | `magnetic-circuit-16` |
| 315 | `campaign-315` | 5×5 | 8 (N0/E5/S2/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 2576 | `magnetic-circuit-16` |
| 316 | `campaign-316` | 5×5 | 10 (N1/E7/S2/W0) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 100000+ | `magnetic-circuit-16` |
| 317 | `campaign-317` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-16` |
| 318 | `campaign-318` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-16` |
| 319 | `campaign-319` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-16` |
| 320 | `campaign-320` | 5×5 | 7 (N2/E3/S0/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 210 | `magnetic-circuit-16` |
| 321 | `campaign-321` | 6×6 | 10 (N3/E0/S1/W6) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 11880 | `magnetic-circuit-17` |
| 322 | `campaign-322` | 5×5 | 10 (N1/E5/S2/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 50400 | `magnetic-circuit-17` |
| 323 | `campaign-323` | 8×8 | 14 (N2/E4/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-17` |
| 324 | `campaign-324` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-17` |
| 325 | `campaign-325` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-17` |
| 326 | `campaign-326` | 8×8 | 12 (N3/E0/S1/W8) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 3 | 144 | `magnetic-circuit-17` |
| 327 | `campaign-327` | 6×6 | 10 (N1/E0/S3/W6) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1420 | `magnetic-circuit-17` |
| 328 | `campaign-328` | 7×7 | 14 (N9/E2/S2/W1) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 10 | 100000+ | `magnetic-circuit-17` |
| 329 | `campaign-329` | 8×8 | 14 (N4/E6/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-17` |
| 330 | `campaign-330` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-17` |
| 331 | `campaign-331` | 8×8 | 18 (N5/E7/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 100000+ | `magnetic-circuit-17` |
| 332 | `campaign-332` | 4×4 | 6 (N3/E2/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 60 | `magnetic-circuit-17` |
| 333 | `campaign-333` | 5×5 | 8 (N1/E1/S2/W4) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 3360 | `magnetic-circuit-17` |
| 334 | `campaign-334` | 5×5 | 10 (N2/E1/S7/W0) | 3 (Pull1/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 28500 | `magnetic-circuit-17` |
| 335 | `campaign-335` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-17` |
| 336 | `campaign-336` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-17` |
| 337 | `campaign-337` | 8×8 | 18 (N6/E6/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-17` |
| 338 | `campaign-338` | 8×8 | 12 (N9/E2/S0/W1) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 17028 | `magnetic-circuit-17` |
| 339 | `campaign-339` | 5×5 | 8 (N2/E4/S2/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 2688 | `magnetic-circuit-17` |
| 340 | `campaign-340` | 6×6 | 12 (N8/E2/S1/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 3 | 660 | `magnetic-circuit-17` |
| 341 | `campaign-341` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-18` |
| 342 | `campaign-342` | 8×8 | 16 (N6/E0/S4/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-18` |
| 343 | `campaign-343` | 8×8 | 18 (N0/E12/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 100000+ | `magnetic-circuit-18` |
| 344 | `campaign-344` | 5×5 | 7 (N1/E0/S5/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 168 | `magnetic-circuit-18` |
| 345 | `campaign-345` | 8×8 | 14 (N2/E3/S1/W8) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 5 | 100000+ | `magnetic-circuit-18` |
| 346 | `campaign-346` | 5×5 | 10 (N5/E1/S2/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 100000+ | `magnetic-circuit-18` |
| 347 | `campaign-347` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-18` |
| 348 | `campaign-348` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 100000+ | `magnetic-circuit-18` |
| 349 | `campaign-349` | 8×8 | 18 (N0/E6/S4/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-18` |
| 350 | `campaign-350` | 8×8 | 12 (N1/E0/S10/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 100000+ | `magnetic-circuit-18` |
| 351 | `campaign-351` | 5×5 | 8 (N2/E1/S1/W4) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1072 | `magnetic-circuit-18` |
| 352 | `campaign-352` | 5×5 | 10 (N1/E2/S7/W0) | 5 (Pull3/Push2) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 8500 | `magnetic-circuit-18` |
| 353 | `campaign-353` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 8 | 100000+ | `magnetic-circuit-18` |
| 354 | `campaign-354` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-18` |
| 355 | `campaign-355` | 8×8 | 18 (N2/E4/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-18` |
| 356 | `campaign-356` | 5×5 | 7 (N1/E4/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 105 | `magnetic-circuit-18` |
| 357 | `campaign-357` | 6×6 | 10 (N2/E1/S1/W6) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 15120 | `magnetic-circuit-18` |
| 358 | `campaign-358` | 5×5 | 10 (N5/E2/S3/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 100000+ | `magnetic-circuit-18` |
| 359 | `campaign-359` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-18` |
| 360 | `campaign-360` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-18` |
| 361 | `campaign-361` | 8×8 | 18 (N2/E6/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-19` |
| 362 | `campaign-362` | 4×4 | 6 (N4/E0/S1/W1) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 240 | `magnetic-circuit-19` |
| 363 | `campaign-363` | 5×5 | 8 (N2/E4/S2/W0) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 8400 | `magnetic-circuit-19` |
| 364 | `campaign-364` | 5×5 | 10 (N3/E0/S6/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 100000+ | `magnetic-circuit-19` |
| 365 | `campaign-365` | 8×8 | 14 (N4/E6/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 8 | 100000+ | `magnetic-circuit-19` |
| 366 | `campaign-366` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-19` |
| 367 | `campaign-367` | 8×8 | 18 (N4/E4/S4/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-19` |
| 368 | `campaign-368` | 8×8 | 12 (N1/E5/S1/W5) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 100000+ | `magnetic-circuit-19` |
| 369 | `campaign-369` | 5×5 | 8 (N3/E4/S1/W0) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 2828 | `magnetic-circuit-19` |
| 370 | `campaign-370` | 5×5 | 10 (N8/E1/S0/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 24480 | `magnetic-circuit-19` |
| 371 | `campaign-371` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-19` |
| 372 | `campaign-372` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-19` |
| 373 | `campaign-373` | 8×8 | 18 (N2/E6/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-19` |
| 374 | `campaign-374` | 4×4 | 6 (N2/E0/S2/W2) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 180 | `magnetic-circuit-19` |
| 375 | `campaign-375` | 6×6 | 10 (N0/E3/S2/W5) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 100000+ | `magnetic-circuit-19` |
| 376 | `campaign-376` | 6×6 | 12 (N9/E1/S2/W0) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 100000+ | `magnetic-circuit-19` |
| 377 | `campaign-377` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-19` |
| 378 | `campaign-378` | 8×8 | 16 (N6/E0/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-19` |
| 379 | `campaign-379` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-19` |
| 380 | `campaign-380` | 5×5 | 7 (N0/E3/S4/W0) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 546 | `magnetic-circuit-19` |
| 381 | `campaign-381` | 8×8 | 14 (N2/E9/S0/W3) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 5 | 100000+ | `magnetic-circuit-20` |
| 382 | `campaign-382` | 7×7 | 14 (N2/E2/S9/W1) | 5 (Pull5/Push0) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-20` |
| 383 | `campaign-383` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-20` |
| 384 | `campaign-384` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-20` |
| 385 | `campaign-385` | 8×8 | 18 (N5/E6/S3/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-20` |
| 386 | `campaign-386` | 4×4 | 6 (N1/E0/S5/W0) | 2 (Pull2/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 40 | `magnetic-circuit-20` |
| 387 | `campaign-387` | 5×5 | 8 (N1/E1/S2/W4) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 280 | `magnetic-circuit-20` |
| 388 | `campaign-388` | 7×7 | 14 (N1/E2/S9/W2) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 100000+ | `magnetic-circuit-20` |
| 389 | `campaign-389` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-20` |
| 390 | `campaign-390` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-20` |
| 391 | `campaign-391` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-20` |
| 392 | `campaign-392` | 8×8 | 12 (N1/E1/S1/W9) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 26730 | `magnetic-circuit-20` |
| 393 | `campaign-393` | 6×6 | 10 (N1/E7/S0/W2) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 10200 | `magnetic-circuit-20` |
| 394 | `campaign-394` | 5×5 | 10 (N6/E2/S1/W1) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 30240 | `magnetic-circuit-20` |
| 395 | `campaign-395` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-20` |
| 396 | `campaign-396` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-20` |
| 397 | `campaign-397` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-20` |
| 398 | `campaign-398` | 5×5 | 7 (N1/E3/S0/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 210 | `magnetic-circuit-20` |
| 399 | `campaign-399` | 8×8 | 14 (N1/E11/S0/W2) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 4 | 100000+ | `magnetic-circuit-20` |
| 400 | `campaign-400` | 5×5 | 10 (N2/E7/S1/W0) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 96840 | `magnetic-circuit-20` |
| 401 | `campaign-401` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-21` |
| 402 | `campaign-402` | 8×8 | 16 (N6/E6/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-21` |
| 403 | `campaign-403` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-21` |
| 404 | `campaign-404` | 5×5 | 7 (N2/E4/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 420 | `magnetic-circuit-21` |
| 405 | `campaign-405` | 6×6 | 10 (N2/E1/S2/W5) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 15070 | `magnetic-circuit-21` |
| 406 | `campaign-406` | 6×6 | 12 (N2/E5/S1/W4) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 93060 | `magnetic-circuit-21` |
| 407 | `campaign-407` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-21` |
| 408 | `campaign-408` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-21` |
| 409 | `campaign-409` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-21` |
| 410 | `campaign-410` | 5×5 | 7 (N1/E2/S0/W4) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 245 | `magnetic-circuit-21` |
| 411 | `campaign-411` | 5×5 | 8 (N1/E6/S0/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 722 | `magnetic-circuit-21` |
| 412 | `campaign-412` | 6×6 | 12 (N4/E2/S5/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 100000+ | `magnetic-circuit-21` |
| 413 | `campaign-413` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-21` |
| 414 | `campaign-414` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 100000+ | `magnetic-circuit-21` |
| 415 | `campaign-415` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-21` |
| 416 | `campaign-416` | 5×5 | 7 (N2/E3/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 420 | `magnetic-circuit-21` |
| 417 | `campaign-417` | 8×8 | 14 (N2/E1/S0/W11) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 5 | 100000+ | `magnetic-circuit-21` |
| 418 | `campaign-418` | 5×5 | 10 (N4/E0/S2/W4) | 4 (Pull4/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 100000+ | `magnetic-circuit-21` |
| 419 | `campaign-419` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-21` |
| 420 | `campaign-420` | 8×8 | 16 (N4/E6/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-21` |
| 421 | `campaign-421` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-22` |
| 422 | `campaign-422` | 4×4 | 6 (N3/E0/S0/W3) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 180 | `magnetic-circuit-22` |
| 423 | `campaign-423` | 6×6 | 10 (N2/E6/S1/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 55440 | `magnetic-circuit-22` |
| 424 | `campaign-424` | 5×5 | 10 (N5/E3/S2/W0) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 900 | `magnetic-circuit-22` |
| 425 | `campaign-425` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-22` |
| 426 | `campaign-426` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-22` |
| 427 | `campaign-427` | 8×8 | 18 (N2/E4/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-22` |
| 428 | `campaign-428` | 8×8 | 12 (N2/E9/S1/W0) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 3 | 21120 | `magnetic-circuit-22` |
| 429 | `campaign-429` | 5×5 | 8 (N4/E3/S1/W0) | 1 (Pull0/Push1) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 176 | `magnetic-circuit-22` |
| 430 | `campaign-430` | 5×5 | 10 (N2/E1/S7/W0) | 4 (Pull2/Push2) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 100000+ | `magnetic-circuit-22` |
| 431 | `campaign-431` | 8×8 | 14 (N0/E4/S4/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 5 | 100000+ | `magnetic-circuit-22` |
| 432 | `campaign-432` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-22` |
| 433 | `campaign-433` | 8×8 | 18 (N2/E6/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-22` |
| 434 | `campaign-434` | 8×8 | 12 (N2/E2/S8/W0) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 15840 | `magnetic-circuit-22` |
| 435 | `campaign-435` | 6×6 | 10 (N1/E5/S2/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 25200 | `magnetic-circuit-22` |
| 436 | `campaign-436` | 5×5 | 10 (N1/E1/S7/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 11087 | `magnetic-circuit-22` |
| 437 | `campaign-437` | 8×8 | 14 (N0/E6/S4/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-22` |
| 438 | `campaign-438` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-22` |
| 439 | `campaign-439` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-22` |
| 440 | `campaign-440` | 8×8 | 12 (N1/E2/S3/W6) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 100000+ | `magnetic-circuit-22` |
| 441 | `campaign-441` | 5×5 | 8 (N0/E1/S1/W6) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 5040 | `magnetic-circuit-23` |
| 442 | `campaign-442` | 5×5 | 10 (N2/E1/S7/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 45360 | `magnetic-circuit-23` |
| 443 | `campaign-443` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-23` |
| 444 | `campaign-444` | 8×8 | 16 (N4/E6/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-23` |
| 445 | `campaign-445` | 8×8 | 18 (N3/E6/S5/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-23` |
| 446 | `campaign-446` | 8×8 | 12 (N3/E0/S8/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 3 | 2974 | `magnetic-circuit-23` |
| 447 | `campaign-447` | 5×5 | 8 (N0/E5/S1/W2) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1182 | `magnetic-circuit-23` |
| 448 | `campaign-448` | 5×5 | 10 (N6/E1/S0/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 16662 | `magnetic-circuit-23` |
| 449 | `campaign-449` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-23` |
| 450 | `campaign-450` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-23` |
| 451 | `campaign-451` | 8×8 | 18 (N6/E4/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-23` |
| 452 | `campaign-452` | 4×4 | 6 (N0/E3/S2/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 162 | `magnetic-circuit-23` |
| 453 | `campaign-453` | 5×5 | 8 (N0/E1/S0/W7) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 168 | `magnetic-circuit-23` |
| 454 | `campaign-454` | 5×5 | 10 (N2/E1/S6/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 91530 | `magnetic-circuit-23` |
| 455 | `campaign-455` | 8×8 | 14 (N0/E4/S4/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-23` |
| 456 | `campaign-456` | 8×8 | 16 (N8/E0/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 100000+ | `magnetic-circuit-23` |
| 457 | `campaign-457` | 8×8 | 18 (N4/E6/S0/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 100000+ | `magnetic-circuit-23` |
| 458 | `campaign-458` | 5×5 | 7 (N0/E3/S1/W3) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 798 | `magnetic-circuit-23` |
| 459 | `campaign-459` | 6×6 | 10 (N2/E7/S0/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 1500 | `magnetic-circuit-23` |
| 460 | `campaign-460` | 5×5 | 10 (N1/E6/S1/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 54000 | `magnetic-circuit-23` |
| 461 | `campaign-461` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-24` |
| 462 | `campaign-462` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 6 | 100000+ | `magnetic-circuit-24` |
| 463 | `campaign-463` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-24` |
| 464 | `campaign-464` | 8×8 | 12 (N5/E6/S0/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 83160 | `magnetic-circuit-24` |
| 465 | `campaign-465` | 5×5 | 8 (N2/E3/S2/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 7560 | `magnetic-circuit-24` |
| 466 | `campaign-466` | 5×5 | 10 (N4/E4/S1/W1) | 5 (Pull5/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 100000+ | `magnetic-circuit-24` |
| 467 | `campaign-467` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-24` |
| 468 | `campaign-468` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-24` |
| 469 | `campaign-469` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-24` |
| 470 | `campaign-470` | 5×5 | 7 (N0/E2/S2/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 588 | `magnetic-circuit-24` |
| 471 | `campaign-471` | 5×5 | 8 (N1/E4/S1/W2) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 440 | `magnetic-circuit-24` |
| 472 | `campaign-472` | 5×5 | 10 (N4/E1/S3/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 50400 | `magnetic-circuit-24` |
| 473 | `campaign-473` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-24` |
| 474 | `campaign-474` | 8×8 | 16 (N6/E4/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-24` |
| 475 | `campaign-475` | 8×8 | 18 (N6/E4/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-24` |
| 476 | `campaign-476` | 4×4 | 6 (N1/E2/S0/W3) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 180 | `magnetic-circuit-24` |
| 477 | `campaign-477` | 5×5 | 8 (N2/E6/S0/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 168 | `magnetic-circuit-24` |
| 478 | `campaign-478` | 5×5 | 10 (N3/E3/S1/W3) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 17604 | `magnetic-circuit-24` |
| 479 | `campaign-479` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-24` |
| 480 | `campaign-480` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-24` |
| 481 | `campaign-481` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 6 | 100000+ | `magnetic-circuit-25` |
| 482 | `campaign-482` | 4×4 | 6 (N0/E1/S3/W2) | 1 (Pull1/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 165 | `magnetic-circuit-25` |
| 483 | `campaign-483` | 5×5 | 8 (N2/E2/S0/W4) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1064 | `magnetic-circuit-25` |
| 484 | `campaign-484` | 6×6 | 12 (N9/E0/S2/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 100000+ | `magnetic-circuit-25` |
| 485 | `campaign-485` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-25` |
| 486 | `campaign-486` | 8×8 | 16 (N6/E0/S4/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 8 | 100000+ | `magnetic-circuit-25` |
| 487 | `campaign-487` | 8×8 | 18 (N0/E12/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-25` |
| 488 | `campaign-488` | 8×8 | 12 (N1/E1/S3/W7) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 100000+ | `magnetic-circuit-25` |
| 489 | `campaign-489` | 8×8 | 14 (N1/E9/S1/W3) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 100000+ | `magnetic-circuit-25` |
| 490 | `campaign-490` | 5×5 | 10 (N3/E0/S6/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 88200 | `magnetic-circuit-25` |
| 491 | `campaign-491` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-25` |
| 492 | `campaign-492` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-25` |
| 493 | `campaign-493` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-25` |
| 494 | `campaign-494` | 5×5 | 7 (N2/E0/S0/W5) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 70 | `magnetic-circuit-25` |
| 495 | `campaign-495` | 5×5 | 8 (N1/E6/S0/W1) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 168 | `magnetic-circuit-25` |
| 496 | `campaign-496` | 5×5 | 10 (N6/E2/S1/W1) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 3 | 1377 | `magnetic-circuit-25` |
| 497 | `campaign-497` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 7 | 100000+ | `magnetic-circuit-25` |
| 498 | `campaign-498` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 7 | 100000+ | `magnetic-circuit-25` |
| 499 | `campaign-499` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 7 | 100000+ | `magnetic-circuit-25` |
| 500 | `campaign-500` | 4×4 | 6 (N2/E0/S3/W1) | 2 (Pull2/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 75 | `magnetic-circuit-25` |
| 501 | `campaign-501` | 5×5 | 8 (N1/E5/S0/W2) | 3 (Pull3/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 6720 | `magnetic-circuit-26` |
| 502 | `campaign-502` | 6×6 | 12 (N5/E2/S4/W1) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 100000+ | `magnetic-circuit-26` |
| 503 | `campaign-503` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 6 | 100000+ | `magnetic-circuit-26` |
| 504 | `campaign-504` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 100000+ | `magnetic-circuit-26` |
| 505 | `campaign-505` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 8 | 100000+ | `magnetic-circuit-26` |
| 506 | `campaign-506` | 8×8 | 12 (N0/E8/S2/W2) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 100000+ | `magnetic-circuit-26` |
| 507 | `campaign-507` | 5×5 | 8 (N2/E1/S1/W4) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 11760 | `magnetic-circuit-26` |
| 508 | `campaign-508` | 5×5 | 10 (N5/E2/S2/W1) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 2300 | `magnetic-circuit-26` |
| 509 | `campaign-509` | 8×8 | 14 (N3/E6/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-26` |
| 510 | `campaign-510` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-26` |
| 511 | `campaign-511` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-26` |
| 512 | `campaign-512` | 5×5 | 7 (N1/E4/S0/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-26` |
| 513 | `campaign-513` | 5×5 | 8 (N2/E2/S1/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-26` |
| 514 | `campaign-514` | 6×6 | 12 (N1/E2/S8/W1) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-26` |
| 515 | `campaign-515` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-26` |
| 516 | `campaign-516` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-26` |
| 517 | `campaign-517` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-26` |
| 518 | `campaign-518` | 5×5 | 7 (N2/E1/S1/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-26` |
| 519 | `campaign-519` | 6×6 | 10 (N1/E4/S2/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-26` |
| 520 | `campaign-520` | 7×7 | 14 (N12/E0/S1/W1) | 3 (Pull2/Push1) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 1+ | `magnetic-circuit-26` |
| 521 | `campaign-521` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-27` |
| 522 | `campaign-522` | 8×8 | 16 (N6/E6/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-27` |
| 523 | `campaign-523` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-27` |
| 524 | `campaign-524` | 5×5 | 7 (N5/E2/S0/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 1+ | `magnetic-circuit-27` |
| 525 | `campaign-525` | 5×5 | 8 (N0/E3/S1/W4) | 3 (Pull1/Push2) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-27` |
| 526 | `campaign-526` | 5×5 | 10 (N2/E1/S6/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-27` |
| 527 | `campaign-527` | 8×8 | 14 (N3/E4/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-27` |
| 528 | `campaign-528` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-27` |
| 529 | `campaign-529` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-27` |
| 530 | `campaign-530` | 4×4 | 6 (N0/E2/S2/W2) | 1 (Pull1/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-27` |
| 531 | `campaign-531` | 5×5 | 8 (N1/E2/S1/W4) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-27` |
| 532 | `campaign-532` | 5×5 | 10 (N0/E1/S6/W3) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-27` |
| 533 | `campaign-533` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-27` |
| 534 | `campaign-534` | 8×8 | 16 (N6/E0/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-27` |
| 535 | `campaign-535` | 8×8 | 18 (N2/E4/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-27` |
| 536 | `campaign-536` | 5×5 | 7 (N1/E2/S3/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 1+ | `magnetic-circuit-27` |
| 537 | `campaign-537` | 5×5 | 8 (N0/E0/S4/W4) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-27` |
| 538 | `campaign-538` | 5×5 | 10 (N1/E2/S7/W0) | 5 (Pull3/Push2) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-27` |
| 539 | `campaign-539` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-27` |
| 540 | `campaign-540` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-27` |
| 541 | `campaign-541` | 8×8 | 18 (N4/E6/S4/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-28` |
| 542 | `campaign-542` | 8×8 | 12 (N7/E0/S3/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-28` |
| 543 | `campaign-543` | 5×5 | 8 (N2/E2/S3/W1) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-28` |
| 544 | `campaign-544` | 7×7 | 14 (N3/E1/S8/W2) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-28` |
| 545 | `campaign-545` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-28` |
| 546 | `campaign-546` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-28` |
| 547 | `campaign-547` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-28` |
| 548 | `campaign-548` | 8×8 | 12 (N2/E1/S4/W5) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-28` |
| 549 | `campaign-549` | 6×6 | 10 (N0/E7/S3/W0) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-28` |
| 550 | `campaign-550` | 5×5 | 10 (N1/E2/S6/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-28` |
| 551 | `campaign-551` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-28` |
| 552 | `campaign-552` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-28` |
| 553 | `campaign-553` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-28` |
| 554 | `campaign-554` | 5×5 | 7 (N0/E1/S5/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-28` |
| 555 | `campaign-555` | 5×5 | 8 (N2/E4/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-28` |
| 556 | `campaign-556` | 6×6 | 12 (N10/E1/S1/W0) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-28` |
| 557 | `campaign-557` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-28` |
| 558 | `campaign-558` | 8×8 | 16 (N6/E0/S4/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-28` |
| 559 | `campaign-559` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-28` |
| 560 | `campaign-560` | 5×5 | 7 (N3/E4/S0/W0) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 1+ | `magnetic-circuit-28` |
| 561 | `campaign-561` | 5×5 | 8 (N5/E1/S0/W2) | 3 (Pull1/Push2) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 2 | 1+ | `magnetic-circuit-29` |
| 562 | `campaign-562` | 5×5 | 10 (N7/E1/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-29` |
| 563 | `campaign-563` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-29` |
| 564 | `campaign-564` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-29` |
| 565 | `campaign-565` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-29` |
| 566 | `campaign-566` | 5×5 | 7 (N3/E1/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-29` |
| 567 | `campaign-567` | 5×5 | 8 (N1/E5/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-29` |
| 568 | `campaign-568` | 7×7 | 14 (N1/E0/S10/W3) | 4 (Pull2/Push2) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 6 | 1+ | `magnetic-circuit-29` |
| 569 | `campaign-569` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-29` |
| 570 | `campaign-570` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-29` |
| 571 | `campaign-571` | 8×8 | 18 (N1/E11/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-29` |
| 572 | `campaign-572` | 5×5 | 7 (N4/E1/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-29` |
| 573 | `campaign-573` | 6×6 | 10 (N2/E4/S1/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-29` |
| 574 | `campaign-574` | 5×5 | 10 (N1/E2/S7/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-29` |
| 575 | `campaign-575` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-29` |
| 576 | `campaign-576` | 8×8 | 16 (N4/E0/S6/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-29` |
| 577 | `campaign-577` | 8×8 | 18 (N8/E6/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-29` |
| 578 | `campaign-578` | 5×5 | 7 (N4/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-29` |
| 579 | `campaign-579` | 5×5 | 8 (N2/E1/S2/W3) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-29` |
| 580 | `campaign-580` | 5×5 | 10 (N3/E0/S3/W4) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-29` |
| 581 | `campaign-581` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-30` |
| 582 | `campaign-582` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-30` |
| 583 | `campaign-583` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-30` |
| 584 | `campaign-584` | 5×5 | 7 (N1/E4/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-30` |
| 585 | `campaign-585` | 6×6 | 10 (N3/E2/S2/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-30` |
| 586 | `campaign-586` | 5×5 | 10 (N9/E1/S0/W0) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-30` |
| 587 | `campaign-587` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-30` |
| 588 | `campaign-588` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-30` |
| 589 | `campaign-589` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-30` |
| 590 | `campaign-590` | 4×4 | 6 (N3/E2/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-30` |
| 591 | `campaign-591` | 5×5 | 8 (N0/E5/S0/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-30` |
| 592 | `campaign-592` | 7×7 | 14 (N7/E3/S3/W1) | 3 (Pull2/Push1) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 1+ | `magnetic-circuit-30` |
| 593 | `campaign-593` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-30` |
| 594 | `campaign-594` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-30` |
| 595 | `campaign-595` | 8×8 | 18 (N5/E6/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-30` |
| 596 | `campaign-596` | 5×5 | 7 (N3/E0/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-30` |
| 597 | `campaign-597` | 5×5 | 8 (N2/E2/S3/W1) | 2 (Pull2/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-30` |
| 598 | `campaign-598` | 5×5 | 10 (N3/E1/S6/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-30` |
| 599 | `campaign-599` | 8×8 | 14 (N0/E4/S3/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-30` |
| 600 | `campaign-600` | 8×8 | 16 (N6/E0/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-30` |
| 601 | `campaign-601` | 8×8 | 18 (N4/E6/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-31` |
| 602 | `campaign-602` | 5×5 | 7 (N1/E2/S3/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-31` |
| 603 | `campaign-603` | 6×6 | 10 (N1/E3/S4/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-31` |
| 604 | `campaign-604` | 5×5 | 10 (N3/E2/S3/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-31` |
| 605 | `campaign-605` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-31` |
| 606 | `campaign-606` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-31` |
| 607 | `campaign-607` | 8×8 | 18 (N1/E9/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-31` |
| 608 | `campaign-608` | 8×8 | 12 (N1/E3/S5/W3) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-31` |
| 609 | `campaign-609` | 6×6 | 10 (N2/E5/S0/W3) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-31` |
| 610 | `campaign-610` | 5×5 | 10 (N3/E5/S1/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-31` |
| 611 | `campaign-611` | 8×8 | 14 (N4/E4/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-31` |
| 612 | `campaign-612` | 8×8 | 16 (N9/E1/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-31` |
| 613 | `campaign-613` | 8×8 | 18 (N2/E9/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-31` |
| 614 | `campaign-614` | 4×4 | 6 (N5/E0/S0/W1) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-31` |
| 615 | `campaign-615` | 8×8 | 14 (N2/E4/S3/W5) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-31` |
| 616 | `campaign-616` | 5×5 | 10 (N4/E3/S1/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-31` |
| 617 | `campaign-617` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-31` |
| 618 | `campaign-618` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-31` |
| 619 | `campaign-619` | 8×8 | 18 (N3/E9/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-31` |
| 620 | `campaign-620` | 5×5 | 7 (N1/E1/S4/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-31` |
| 621 | `campaign-621` | 6×6 | 10 (N3/E4/S1/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-32` |
| 622 | `campaign-622` | 5×5 | 10 (N4/E2/S3/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-32` |
| 623 | `campaign-623` | 8×8 | 14 (N1/E4/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-32` |
| 624 | `campaign-624` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-32` |
| 625 | `campaign-625` | 8×8 | 18 (N3/E7/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-32` |
| 626 | `campaign-626` | 4×4 | 6 (N3/E1/S0/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-32` |
| 627 | `campaign-627` | 5×5 | 8 (N1/E3/S3/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-32` |
| 628 | `campaign-628` | 5×5 | 10 (N1/E6/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-32` |
| 629 | `campaign-629` | 8×8 | 14 (N4/E3/S6/W1) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-32` |
| 630 | `campaign-630` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-32` |
| 631 | `campaign-631` | 8×8 | 18 (N1/E5/S5/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-32` |
| 632 | `campaign-632` | 4×4 | 6 (N2/E2/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-32` |
| 633 | `campaign-633` | 5×5 | 8 (N2/E5/S1/W0) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 2 | 1+ | `magnetic-circuit-32` |
| 634 | `campaign-634` | 5×5 | 10 (N3/E6/S1/W0) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-32` |
| 635 | `campaign-635` | 8×8 | 14 (N0/E5/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-32` |
| 636 | `campaign-636` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-32` |
| 637 | `campaign-637` | 8×8 | 18 (N1/E7/S1/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-32` |
| 638 | `campaign-638` | 8×8 | 12 (N2/E2/S6/W2) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 10 | 1+ | `magnetic-circuit-32` |
| 639 | `campaign-639` | 8×8 | 14 (N0/E8/S2/W4) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-32` |
| 640 | `campaign-640` | 5×5 | 10 (N1/E2/S5/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-32` |
| 641 | `campaign-641` | 8×8 | 14 (N3/E6/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-33` |
| 642 | `campaign-642` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-33` |
| 643 | `campaign-643` | 8×8 | 18 (N4/E6/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-33` |
| 644 | `campaign-644` | 5×5 | 7 (N4/E1/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-33` |
| 645 | `campaign-645` | 6×6 | 10 (N6/E2/S0/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-33` |
| 646 | `campaign-646` | 6×6 | 12 (N4/E3/S4/W1) | 5 (Pull2/Push3) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-33` |
| 647 | `campaign-647` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-33` |
| 648 | `campaign-648` | 8×8 | 16 (N9/E1/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-33` |
| 649 | `campaign-649` | 8×8 | 18 (N1/E7/S4/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-33` |
| 650 | `campaign-650` | 4×4 | 6 (N4/E0/S2/W0) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-33` |
| 651 | `campaign-651` | 6×6 | 10 (N6/E1/S1/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-33` |
| 652 | `campaign-652` | 6×6 | 12 (N0/E1/S6/W5) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-33` |
| 653 | `campaign-653` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-33` |
| 654 | `campaign-654` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-33` |
| 655 | `campaign-655` | 8×8 | 18 (N1/E6/S4/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-33` |
| 656 | `campaign-656` | 8×8 | 12 (N4/E1/S4/W3) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-33` |
| 657 | `campaign-657` | 8×8 | 14 (N5/E1/S1/W7) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-33` |
| 658 | `campaign-658` | 5×5 | 10 (N4/E1/S1/W4) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-33` |
| 659 | `campaign-659` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-33` |
| 660 | `campaign-660` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-33` |
| 661 | `campaign-661` | 8×8 | 18 (N0/E8/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-34` |
| 662 | `campaign-662` | 4×4 | 6 (N2/E3/S0/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-34` |
| 663 | `campaign-663` | 8×8 | 14 (N2/E4/S2/W6) | 4 (Pull4/Push0) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 10 | 1+ | `magnetic-circuit-34` |
| 664 | `campaign-664` | 6×6 | 12 (N4/E3/S4/W1) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-34` |
| 665 | `campaign-665` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-34` |
| 666 | `campaign-666` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-34` |
| 667 | `campaign-667` | 8×8 | 18 (N4/E7/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-34` |
| 668 | `campaign-668` | 8×8 | 12 (N3/E5/S4/W0) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-34` |
| 669 | `campaign-669` | 5×5 | 8 (N2/E1/S4/W1) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-34` |
| 670 | `campaign-670` | 6×6 | 12 (N2/E2/S3/W5) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-34` |
| 671 | `campaign-671` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-34` |
| 672 | `campaign-672` | 8×8 | 16 (N5/E3/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-34` |
| 673 | `campaign-673` | 8×8 | 18 (N0/E6/S4/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-34` |
| 674 | `campaign-674` | 8×8 | 12 (N3/E5/S1/W3) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-34` |
| 675 | `campaign-675` | 5×5 | 8 (N0/E6/S1/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-34` |
| 676 | `campaign-676` | 7×7 | 14 (N6/E4/S1/W3) | 2 (Pull2/Push0) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-34` |
| 677 | `campaign-677` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-34` |
| 678 | `campaign-678` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-34` |
| 679 | `campaign-679` | 8×8 | 18 (N1/E8/S1/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-34` |
| 680 | `campaign-680` | 8×8 | 12 (N3/E4/S0/W5) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-34` |
| 681 | `campaign-681` | 5×5 | 8 (N0/E4/S1/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-35` |
| 682 | `campaign-682` | 5×5 | 10 (N1/E8/S0/W1) | 5 (Pull4/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-35` |
| 683 | `campaign-683` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-35` |
| 684 | `campaign-684` | 8×8 | 16 (N9/E0/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-35` |
| 685 | `campaign-685` | 8×8 | 18 (N2/E6/S4/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-35` |
| 686 | `campaign-686` | 5×5 | 7 (N5/E0/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-35` |
| 687 | `campaign-687` | 6×6 | 10 (N1/E2/S2/W5) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 1+ | `magnetic-circuit-35` |
| 688 | `campaign-688` | 5×5 | 10 (N0/E5/S3/W2) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-35` |
| 689 | `campaign-689` | 8×8 | 14 (N2/E8/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-35` |
| 690 | `campaign-690` | 8×8 | 16 (N6/E2/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-35` |
| 691 | `campaign-691` | 8×8 | 18 (N3/E6/S2/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-35` |
| 692 | `campaign-692` | 5×5 | 7 (N1/E0/S5/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-35` |
| 693 | `campaign-693` | 5×5 | 8 (N4/E2/S1/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-35` |
| 694 | `campaign-694` | 5×5 | 10 (N2/E3/S0/W5) | 5 (Pull5/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-35` |
| 695 | `campaign-695` | 8×8 | 14 (N0/E4/S3/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-35` |
| 696 | `campaign-696` | 8×8 | 16 (N9/E0/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-35` |
| 697 | `campaign-697` | 8×8 | 18 (N3/E7/S0/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-35` |
| 698 | `campaign-698` | 4×4 | 6 (N2/E0/S2/W2) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-35` |
| 699 | `campaign-699` | 5×5 | 8 (N1/E0/S5/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-35` |
| 700 | `campaign-700` | 7×7 | 14 (N3/E7/S1/W3) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-35` |
| 701 | `campaign-701` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-36` |
| 702 | `campaign-702` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-36` |
| 703 | `campaign-703` | 8×8 | 18 (N1/E6/S3/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-36` |
| 704 | `campaign-704` | 5×5 | 7 (N5/E0/S1/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-36` |
| 705 | `campaign-705` | 8×8 | 14 (N9/E1/S1/W3) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-36` |
| 706 | `campaign-706` | 6×6 | 12 (N5/E3/S3/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-36` |
| 707 | `campaign-707` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-36` |
| 708 | `campaign-708` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-36` |
| 709 | `campaign-709` | 8×8 | 18 (N1/E6/S5/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-36` |
| 710 | `campaign-710` | 5×5 | 7 (N3/E1/S1/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-36` |
| 711 | `campaign-711` | 6×6 | 10 (N3/E2/S5/W0) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-36` |
| 712 | `campaign-712` | 7×7 | 14 (N4/E3/S6/W1) | 3 (Pull2/Push1) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-36` |
| 713 | `campaign-713` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-36` |
| 714 | `campaign-714` | 8×8 | 16 (N7/E5/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-36` |
| 715 | `campaign-715` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-36` |
| 716 | `campaign-716` | 8×8 | 12 (N1/E1/S2/W8) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-36` |
| 717 | `campaign-717` | 6×6 | 10 (N6/E2/S0/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-36` |
| 718 | `campaign-718` | 5×5 | 10 (N3/E3/S4/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-36` |
| 719 | `campaign-719` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-36` |
| 720 | `campaign-720` | 8×8 | 16 (N9/E0/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-36` |
| 721 | `campaign-721` | 8×8 | 18 (N1/E7/S1/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-37` |
| 722 | `campaign-722` | 4×4 | 6 (N2/E1/S2/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-37` |
| 723 | `campaign-723` | 5×5 | 8 (N0/E3/S2/W3) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-37` |
| 724 | `campaign-724` | 5×5 | 10 (N3/E2/S1/W4) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-37` |
| 725 | `campaign-725` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-37` |
| 726 | `campaign-726` | 8×8 | 16 (N6/E4/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-37` |
| 727 | `campaign-727` | 8×8 | 18 (N2/E9/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-37` |
| 728 | `campaign-728` | 4×4 | 6 (N1/E2/S0/W3) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-37` |
| 729 | `campaign-729` | 6×6 | 10 (N2/E4/S3/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-37` |
| 730 | `campaign-730` | 6×6 | 12 (N2/E4/S4/W2) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-37` |
| 731 | `campaign-731` | 8×8 | 14 (N0/E4/S2/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-37` |
| 732 | `campaign-732` | 8×8 | 16 (N7/E2/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-37` |
| 733 | `campaign-733` | 8×8 | 18 (N1/E7/S2/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-37` |
| 734 | `campaign-734` | 5×5 | 7 (N1/E5/S0/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-37` |
| 735 | `campaign-735` | 5×5 | 8 (N0/E3/S4/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-37` |
| 736 | `campaign-736` | 5×5 | 10 (N4/E4/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-37` |
| 737 | `campaign-737` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-37` |
| 738 | `campaign-738` | 8×8 | 16 (N6/E1/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-37` |
| 739 | `campaign-739` | 8×8 | 18 (N4/E7/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-37` |
| 740 | `campaign-740` | 5×5 | 7 (N1/E2/S2/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-37` |
| 741 | `campaign-741` | 6×6 | 10 (N1/E4/S1/W4) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-38` |
| 742 | `campaign-742` | 5×5 | 10 (N1/E2/S5/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-38` |
| 743 | `campaign-743` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-38` |
| 744 | `campaign-744` | 8×8 | 16 (N4/E2/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-38` |
| 745 | `campaign-745` | 8×8 | 18 (N3/E7/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-38` |
| 746 | `campaign-746` | 4×4 | 6 (N0/E3/S1/W2) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-38` |
| 747 | `campaign-747` | 5×5 | 8 (N1/E3/S1/W3) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-38` |
| 748 | `campaign-748` | 5×5 | 10 (N3/E4/S2/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-38` |
| 749 | `campaign-749` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-38` |
| 750 | `campaign-750` | 8×8 | 16 (N6/E2/S5/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-38` |
| 751 | `campaign-751` | 8×8 | 18 (N1/E6/S4/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-38` |
| 752 | `campaign-752` | 5×5 | 7 (N2/E2/S0/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-38` |
| 753 | `campaign-753` | 6×6 | 10 (N3/E0/S2/W5) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-38` |
| 754 | `campaign-754` | 5×5 | 10 (N3/E3/S3/W1) | 3 (Pull1/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-38` |
| 755 | `campaign-755` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-38` |
| 756 | `campaign-756` | 8×8 | 16 (N5/E1/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-38` |
| 757 | `campaign-757` | 8×8 | 18 (N2/E8/S0/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-38` |
| 758 | `campaign-758` | 5×5 | 7 (N2/E3/S0/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-38` |
| 759 | `campaign-759` | 5×5 | 8 (N2/E3/S2/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-38` |
| 760 | `campaign-760` | 6×6 | 12 (N2/E2/S6/W2) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 10 | 1+ | `magnetic-circuit-38` |
| 761 | `campaign-761` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-39` |
| 762 | `campaign-762` | 8×8 | 16 (N8/E0/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-39` |
| 763 | `campaign-763` | 8×8 | 18 (N1/E8/S1/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-39` |
| 764 | `campaign-764` | 5×5 | 7 (N3/E1/S3/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-39` |
| 765 | `campaign-765` | 5×5 | 8 (N0/E3/S2/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-39` |
| 766 | `campaign-766` | 5×5 | 10 (N4/E0/S3/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-39` |
| 767 | `campaign-767` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-39` |
| 768 | `campaign-768` | 8×8 | 16 (N4/E5/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-39` |
| 769 | `campaign-769` | 8×8 | 18 (N4/E8/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-39` |
| 770 | `campaign-770` | 5×5 | 7 (N1/E2/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-39` |
| 771 | `campaign-771` | 6×6 | 10 (N2/E5/S1/W2) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-39` |
| 772 | `campaign-772` | 5×5 | 10 (N4/E0/S2/W4) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-39` |
| 773 | `campaign-773` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-39` |
| 774 | `campaign-774` | 8×8 | 16 (N7/E2/S4/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-39` |
| 775 | `campaign-775` | 8×8 | 18 (N0/E8/S3/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-39` |
| 776 | `campaign-776` | 5×5 | 7 (N2/E2/S0/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-39` |
| 777 | `campaign-777` | 6×6 | 10 (N2/E1/S1/W6) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-39` |
| 778 | `campaign-778` | 5×5 | 10 (N2/E2/S2/W4) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-39` |
| 779 | `campaign-779` | 8×8 | 14 (N2/E4/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-39` |
| 780 | `campaign-780` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-39` |
| 781 | `campaign-781` | 8×8 | 18 (N0/E7/S5/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-40` |
| 782 | `campaign-782` | 8×8 | 12 (N2/E1/S6/W3) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-40` |
| 783 | `campaign-783` | 8×8 | 14 (N4/E6/S3/W1) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 9 | 1+ | `magnetic-circuit-40` |
| 784 | `campaign-784` | 6×6 | 12 (N3/E2/S7/W0) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-40` |
| 785 | `campaign-785` | 8×8 | 14 (N3/E6/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-40` |
| 786 | `campaign-786` | 8×8 | 16 (N7/E3/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-40` |
| 787 | `campaign-787` | 8×8 | 18 (N3/E8/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-40` |
| 788 | `campaign-788` | 5×5 | 7 (N3/E2/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-40` |
| 789 | `campaign-789` | 5×5 | 8 (N2/E1/S2/W3) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-40` |
| 790 | `campaign-790` | 6×6 | 12 (N3/E3/S5/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-40` |
| 791 | `campaign-791` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-40` |
| 792 | `campaign-792` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-40` |
| 793 | `campaign-793` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-40` |
| 794 | `campaign-794` | 5×5 | 7 (N2/E2/S1/W2) | 2 (Pull0/Push2) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-40` |
| 795 | `campaign-795` | 5×5 | 8 (N1/E2/S2/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-40` |
| 796 | `campaign-796` | 6×6 | 12 (N1/E2/S6/W3) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 1+ | `magnetic-circuit-40` |
| 797 | `campaign-797` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-40` |
| 798 | `campaign-798` | 8×8 | 16 (N6/E2/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-40` |
| 799 | `campaign-799` | 8×8 | 18 (N2/E7/S3/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-40` |
| 800 | `campaign-800` | 5×5 | 7 (N3/E2/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-40` |
| 801 | `campaign-801` | 5×5 | 8 (N3/E3/S0/W2) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-41` |
| 802 | `campaign-802` | 5×5 | 10 (N4/E4/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-41` |
| 803 | `campaign-803` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-41` |
| 804 | `campaign-804` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-41` |
| 805 | `campaign-805` | 8×8 | 18 (N5/E7/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-41` |
| 806 | `campaign-806` | 4×4 | 6 (N2/E2/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-41` |
| 807 | `campaign-807` | 6×6 | 10 (N3/E1/S5/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-41` |
| 808 | `campaign-808` | 6×6 | 12 (N3/E2/S5/W2) | 5 (Pull3/Push2) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-41` |
| 809 | `campaign-809` | 8×8 | 14 (N2/E7/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-41` |
| 810 | `campaign-810` | 8×8 | 16 (N6/E5/S5/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-41` |
| 811 | `campaign-811` | 8×8 | 18 (N4/E4/S2/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-41` |
| 812 | `campaign-812` | 8×8 | 12 (N2/E2/S7/W1) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-41` |
| 813 | `campaign-813` | 6×6 | 10 (N2/E2/S1/W5) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-41` |
| 814 | `campaign-814` | 5×5 | 10 (N1/E2/S5/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-41` |
| 815 | `campaign-815` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-41` |
| 816 | `campaign-816` | 8×8 | 16 (N8/E0/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-41` |
| 817 | `campaign-817` | 8×8 | 18 (N2/E8/S4/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-41` |
| 818 | `campaign-818` | 5×5 | 7 (N1/E4/S2/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-41` |
| 819 | `campaign-819` | 6×6 | 10 (N5/E4/S1/W0) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-41` |
| 820 | `campaign-820` | 5×5 | 10 (N2/E5/S2/W1) | 3 (Pull3/Push0) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-41` |
| 821 | `campaign-821` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-42` |
| 822 | `campaign-822` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-42` |
| 823 | `campaign-823` | 8×8 | 18 (N1/E5/S5/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-42` |
| 824 | `campaign-824` | 8×8 | 12 (N8/E1/S2/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 10 | 1+ | `magnetic-circuit-42` |
| 825 | `campaign-825` | 8×8 | 14 (N8/E1/S1/W4) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-42` |
| 826 | `campaign-826` | 5×5 | 10 (N1/E2/S4/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-42` |
| 827 | `campaign-827` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-42` |
| 828 | `campaign-828` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-42` |
| 829 | `campaign-829` | 8×8 | 18 (N0/E8/S4/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-42` |
| 830 | `campaign-830` | 5×5 | 7 (N4/E1/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-42` |
| 831 | `campaign-831` | 5×5 | 8 (N0/E2/S3/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-42` |
| 832 | `campaign-832` | 5×5 | 10 (N4/E3/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-42` |
| 833 | `campaign-833` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-42` |
| 834 | `campaign-834` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-42` |
| 835 | `campaign-835` | 8×8 | 18 (N3/E5/S1/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-42` |
| 836 | `campaign-836` | 4×4 | 6 (N3/E1/S1/W1) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-42` |
| 837 | `campaign-837` | 5×5 | 8 (N1/E1/S3/W3) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-42` |
| 838 | `campaign-838` | 5×5 | 10 (N6/E2/S0/W2) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-42` |
| 839 | `campaign-839` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-42` |
| 840 | `campaign-840` | 8×8 | 16 (N8/E0/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-42` |
| 841 | `campaign-841` | 8×8 | 18 (N1/E8/S1/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-43` |
| 842 | `campaign-842` | 4×4 | 6 (N3/E2/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-43` |
| 843 | `campaign-843` | 6×6 | 10 (N4/E2/S2/W2) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-43` |
| 844 | `campaign-844` | 5×5 | 10 (N1/E2/S5/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-43` |
| 845 | `campaign-845` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-43` |
| 846 | `campaign-846` | 8×8 | 16 (N8/E1/S4/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-43` |
| 847 | `campaign-847` | 8×8 | 18 (N1/E6/S4/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-43` |
| 848 | `campaign-848` | 4×4 | 6 (N1/E0/S2/W3) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-43` |
| 849 | `campaign-849` | 6×6 | 10 (N6/E1/S1/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-43` |
| 850 | `campaign-850` | 5×5 | 10 (N4/E3/S0/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-43` |
| 851 | `campaign-851` | 8×8 | 14 (N3/E4/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-43` |
| 852 | `campaign-852` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-43` |
| 853 | `campaign-853` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-43` |
| 854 | `campaign-854` | 4×4 | 6 (N1/E3/S0/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-43` |
| 855 | `campaign-855` | 8×8 | 14 (N5/E1/S2/W6) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 9 | 1+ | `magnetic-circuit-43` |
| 856 | `campaign-856` | 6×6 | 12 (N4/E1/S6/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-43` |
| 857 | `campaign-857` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-43` |
| 858 | `campaign-858` | 8×8 | 16 (N6/E0/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-43` |
| 859 | `campaign-859` | 8×8 | 18 (N1/E9/S0/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-43` |
| 860 | `campaign-860` | 5×5 | 7 (N2/E2/S2/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-43` |
| 861 | `campaign-861` | 6×6 | 10 (N2/E2/S2/W4) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-44` |
| 862 | `campaign-862` | 5×5 | 10 (N2/E1/S4/W3) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-44` |
| 863 | `campaign-863` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-44` |
| 864 | `campaign-864` | 8×8 | 16 (N7/E1/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-44` |
| 865 | `campaign-865` | 8×8 | 18 (N1/E6/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-44` |
| 866 | `campaign-866` | 8×8 | 12 (N3/E2/S5/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-44` |
| 867 | `campaign-867` | 6×6 | 10 (N2/E7/S0/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-44` |
| 868 | `campaign-868` | 5×5 | 10 (N5/E1/S1/W3) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-44` |
| 869 | `campaign-869` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-44` |
| 870 | `campaign-870` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-44` |
| 871 | `campaign-871` | 8×8 | 18 (N0/E5/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-44` |
| 872 | `campaign-872` | 4×4 | 6 (N0/E2/S2/W2) | 2 (Pull1/Push1) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-44` |
| 873 | `campaign-873` | 5×5 | 8 (N3/E1/S1/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-44` |
| 874 | `campaign-874` | 5×5 | 10 (N2/E4/S3/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-44` |
| 875 | `campaign-875` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-44` |
| 876 | `campaign-876` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-44` |
| 877 | `campaign-877` | 8×8 | 18 (N5/E8/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-44` |
| 878 | `campaign-878` | 5×5 | 7 (N0/E1/S1/W5) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-44` |
| 879 | `campaign-879` | 6×6 | 10 (N3/E3/S0/W4) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-44` |
| 880 | `campaign-880` | 5×5 | 10 (N2/E4/S3/W1) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-44` |
| 881 | `campaign-881` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-45` |
| 882 | `campaign-882` | 8×8 | 16 (N7/E0/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-45` |
| 883 | `campaign-883` | 8×8 | 18 (N3/E8/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-45` |
| 884 | `campaign-884` | 5×5 | 7 (N4/E1/S0/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-45` |
| 885 | `campaign-885` | 5×5 | 8 (N1/E3/S2/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-45` |
| 886 | `campaign-886` | 7×7 | 14 (N9/E1/S1/W3) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 6 | 1+ | `magnetic-circuit-45` |
| 887 | `campaign-887` | 8×8 | 14 (N0/E4/S3/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-45` |
| 888 | `campaign-888` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-45` |
| 889 | `campaign-889` | 8×8 | 18 (N4/E7/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-45` |
| 890 | `campaign-890` | 8×8 | 12 (N2/E4/S0/W6) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-45` |
| 891 | `campaign-891` | 6×6 | 10 (N3/E5/S2/W0) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-45` |
| 892 | `campaign-892` | 5×5 | 10 (N3/E2/S4/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-45` |
| 893 | `campaign-893` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-45` |
| 894 | `campaign-894` | 8×8 | 16 (N7/E0/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-45` |
| 895 | `campaign-895` | 8×8 | 18 (N4/E4/S3/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-45` |
| 896 | `campaign-896` | 5×5 | 7 (N2/E3/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-45` |
| 897 | `campaign-897` | 6×6 | 10 (N3/E2/S0/W5) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-45` |
| 898 | `campaign-898` | 5×5 | 10 (N0/E3/S5/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-45` |
| 899 | `campaign-899` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-45` |
| 900 | `campaign-900` | 8×8 | 16 (N9/E0/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-45` |
| 901 | `campaign-901` | 8×8 | 18 (N4/E6/S0/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-46` |
| 902 | `campaign-902` | 5×5 | 7 (N2/E1/S2/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-46` |
| 903 | `campaign-903` | 5×5 | 8 (N4/E2/S0/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-46` |
| 904 | `campaign-904` | 7×7 | 14 (N2/E4/S6/W2) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-46` |
| 905 | `campaign-905` | 8×8 | 14 (N3/E7/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-46` |
| 906 | `campaign-906` | 8×8 | 16 (N7/E5/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-46` |
| 907 | `campaign-907` | 8×8 | 18 (N3/E7/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-46` |
| 908 | `campaign-908` | 5×5 | 7 (N3/E3/S1/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-46` |
| 909 | `campaign-909` | 6×6 | 10 (N2/E4/S0/W4) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-46` |
| 910 | `campaign-910` | 5×5 | 10 (N1/E1/S6/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-46` |
| 911 | `campaign-911` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-46` |
| 912 | `campaign-912` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-46` |
| 913 | `campaign-913` | 8×8 | 18 (N3/E6/S0/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-46` |
| 914 | `campaign-914` | 5×5 | 7 (N1/E3/S3/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-46` |
| 915 | `campaign-915` | 5×5 | 8 (N0/E0/S7/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-46` |
| 916 | `campaign-916` | 5×5 | 10 (N4/E2/S2/W2) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-46` |
| 917 | `campaign-917` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-46` |
| 918 | `campaign-918` | 8×8 | 16 (N7/E1/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-46` |
| 919 | `campaign-919` | 8×8 | 18 (N1/E6/S5/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-46` |
| 920 | `campaign-920` | 5×5 | 7 (N1/E1/S3/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-46` |
| 921 | `campaign-921` | 6×6 | 10 (N1/E4/S1/W4) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-47` |
| 922 | `campaign-922` | 5×5 | 10 (N1/E2/S5/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-47` |
| 923 | `campaign-923` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-47` |
| 924 | `campaign-924` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-47` |
| 925 | `campaign-925` | 8×8 | 18 (N0/E8/S2/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-47` |
| 926 | `campaign-926` | 5×5 | 7 (N0/E4/S0/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-47` |
| 927 | `campaign-927` | 6×6 | 10 (N4/E1/S2/W3) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-47` |
| 928 | `campaign-928` | 5×5 | 10 (N3/E3/S2/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-47` |
| 929 | `campaign-929` | 8×8 | 14 (N0/E6/S4/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-47` |
| 930 | `campaign-930` | 8×8 | 16 (N9/E3/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-47` |
| 931 | `campaign-931` | 8×8 | 18 (N2/E8/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-47` |
| 932 | `campaign-932` | 8×8 | 12 (N4/E2/S3/W3) | 1 (Pull0/Push1) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-47` |
| 933 | `campaign-933` | 8×8 | 14 (N10/E1/S0/W3) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 6 | 1+ | `magnetic-circuit-47` |
| 934 | `campaign-934` | 5×5 | 10 (N2/E4/S1/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-47` |
| 935 | `campaign-935` | 8×8 | 14 (N0/E4/S4/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-47` |
| 936 | `campaign-936` | 8×8 | 16 (N4/E3/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-47` |
| 937 | `campaign-937` | 8×8 | 18 (N2/E6/S1/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-47` |
| 938 | `campaign-938` | 8×8 | 12 (N5/E0/S2/W5) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-47` |
| 939 | `campaign-939` | 8×8 | 14 (N2/E2/S4/W6) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-47` |
| 940 | `campaign-940` | 5×5 | 10 (N2/E1/S4/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-47` |
| 941 | `campaign-941` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-48` |
| 942 | `campaign-942` | 8×8 | 16 (N7/E0/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-48` |
| 943 | `campaign-943` | 8×8 | 18 (N2/E7/S3/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-48` |
| 944 | `campaign-944` | 8×8 | 12 (N2/E3/S3/W4) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-48` |
| 945 | `campaign-945` | 5×5 | 8 (N1/E0/S3/W4) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-48` |
| 946 | `campaign-946` | 6×6 | 12 (N5/E4/S2/W1) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 10 | 1+ | `magnetic-circuit-48` |
| 947 | `campaign-947` | 8×8 | 14 (N2/E5/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-48` |
| 948 | `campaign-948` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-48` |
| 949 | `campaign-949` | 8×8 | 18 (N1/E8/S4/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-48` |
| 950 | `campaign-950` | 8×8 | 12 (N4/E3/S2/W3) | 1 (Pull0/Push1) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-48` |
| 951 | `campaign-951` | 8×8 | 14 (N2/E3/S4/W5) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 10 | 1+ | `magnetic-circuit-48` |
| 952 | `campaign-952` | 5×5 | 10 (N4/E4/S1/W1) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-48` |
| 953 | `campaign-953` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-48` |
| 954 | `campaign-954` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-48` |
| 955 | `campaign-955` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-48` |
| 956 | `campaign-956` | 5×5 | 7 (N1/E3/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-48` |
| 957 | `campaign-957` | 6×6 | 10 (N1/E0/S4/W5) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-48` |
| 958 | `campaign-958` | 5×5 | 10 (N4/E2/S2/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-48` |
| 959 | `campaign-959` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-48` |
| 960 | `campaign-960` | 8×8 | 16 (N7/E1/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-48` |
| 961 | `campaign-961` | 8×8 | 18 (N4/E8/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-49` |
| 962 | `campaign-962` | 5×5 | 7 (N3/E1/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-49` |
| 963 | `campaign-963` | 6×6 | 10 (N4/E3/S2/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-49` |
| 964 | `campaign-964` | 7×7 | 14 (N1/E4/S4/W5) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-49` |
| 965 | `campaign-965` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-49` |
| 966 | `campaign-966` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-49` |
| 967 | `campaign-967` | 8×8 | 18 (N1/E10/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-49` |
| 968 | `campaign-968` | 5×5 | 7 (N3/E2/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-49` |
| 969 | `campaign-969` | 5×5 | 8 (N2/E3/S1/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-49` |
| 970 | `campaign-970` | 5×5 | 10 (N7/E2/S0/W1) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-49` |
| 971 | `campaign-971` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-49` |
| 972 | `campaign-972` | 8×8 | 16 (N4/E5/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-49` |
| 973 | `campaign-973` | 8×8 | 18 (N0/E8/S2/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-49` |
| 974 | `campaign-974` | 4×4 | 6 (N2/E3/S1/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-49` |
| 975 | `campaign-975` | 6×6 | 10 (N3/E5/S1/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-49` |
| 976 | `campaign-976` | 5×5 | 10 (N3/E3/S0/W4) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-49` |
| 977 | `campaign-977` | 8×8 | 14 (N3/E6/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-49` |
| 978 | `campaign-978` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-49` |
| 979 | `campaign-979` | 8×8 | 18 (N4/E4/S2/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-49` |
| 980 | `campaign-980` | 5×5 | 7 (N0/E5/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-49` |
| 981 | `campaign-981` | 6×6 | 10 (N3/E1/S4/W2) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-50` |
| 982 | `campaign-982` | 5×5 | 10 (N1/E4/S2/W3) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-50` |
| 983 | `campaign-983` | 8×8 | 14 (N0/E4/S3/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-50` |
| 984 | `campaign-984` | 8×8 | 16 (N4/E4/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-50` |
| 985 | `campaign-985` | 8×8 | 18 (N0/E7/S2/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-50` |
| 986 | `campaign-986` | 4×4 | 6 (N2/E2/S0/W2) | 1 (Pull0/Push1) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-50` |
| 987 | `campaign-987` | 5×5 | 8 (N1/E2/S3/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-50` |
| 988 | `campaign-988` | 5×5 | 10 (N4/E1/S3/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-50` |
| 989 | `campaign-989` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-50` |
| 990 | `campaign-990` | 8×8 | 16 (N6/E1/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-50` |
| 991 | `campaign-991` | 8×8 | 18 (N3/E7/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-50` |
| 992 | `campaign-992` | 8×8 | 12 (N0/E5/S3/W4) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-50` |
| 993 | `campaign-993` | 5×5 | 8 (N3/E0/S1/W4) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-50` |
| 994 | `campaign-994` | 5×5 | 10 (N5/E2/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-50` |
| 995 | `campaign-995` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-50` |
| 996 | `campaign-996` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-50` |
| 997 | `campaign-997` | 8×8 | 18 (N3/E6/S0/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-50` |
| 998 | `campaign-998` | 5×5 | 7 (N3/E1/S0/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-50` |
| 999 | `campaign-999` | 5×5 | 8 (N0/E2/S3/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-50` |
| 1000 | `campaign-1000` | 5×5 | 10 (N5/E1/S2/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-50` |
| 1001 | `campaign-1001` | 8×8 | 14 (N2/E8/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-51` |
| 1002 | `campaign-1002` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-51` |
| 1003 | `campaign-1003` | 8×8 | 18 (N3/E8/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-51` |
| 1004 | `campaign-1004` | 5×5 | 7 (N3/E3/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-51` |
| 1005 | `campaign-1005` | 5×5 | 8 (N1/E5/S1/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-51` |
| 1006 | `campaign-1006` | 7×7 | 14 (N3/E5/S3/W3) | 2 (Pull2/Push0) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-51` |
| 1007 | `campaign-1007` | 8×8 | 14 (N0/E4/S2/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-51` |
| 1008 | `campaign-1008` | 8×8 | 16 (N4/E0/S7/W5) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-51` |
| 1009 | `campaign-1009` | 8×8 | 18 (N5/E7/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-51` |
| 1010 | `campaign-1010` | 5×5 | 7 (N4/E2/S0/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-51` |
| 1011 | `campaign-1011` | 6×6 | 10 (N2/E5/S0/W3) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-51` |
| 1012 | `campaign-1012` | 5×5 | 10 (N4/E2/S1/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-51` |
| 1013 | `campaign-1013` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-51` |
| 1014 | `campaign-1014` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-51` |
| 1015 | `campaign-1015` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-51` |
| 1016 | `campaign-1016` | 8×8 | 12 (N2/E2/S6/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-51` |
| 1017 | `campaign-1017` | 5×5 | 8 (N3/E2/S2/W1) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-51` |
| 1018 | `campaign-1018` | 7×7 | 14 (N2/E5/S6/W1) | 2 (Pull2/Push0) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-51` |
| 1019 | `campaign-1019` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-51` |
| 1020 | `campaign-1020` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-51` |
| 1021 | `campaign-1021` | 8×8 | 18 (N3/E7/S3/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-52` |
| 1022 | `campaign-1022` | 4×4 | 6 (N2/E2/S1/W1) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-52` |
| 1023 | `campaign-1023` | 5×5 | 8 (N3/E2/S1/W2) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-52` |
| 1024 | `campaign-1024` | 7×7 | 14 (N7/E2/S2/W3) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-52` |
| 1025 | `campaign-1025` | 8×8 | 14 (N2/E7/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-52` |
| 1026 | `campaign-1026` | 8×8 | 16 (N8/E1/S4/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-52` |
| 1027 | `campaign-1027` | 8×8 | 18 (N3/E6/S1/W8) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-52` |
| 1028 | `campaign-1028` | 5×5 | 7 (N2/E2/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-52` |
| 1029 | `campaign-1029` | 6×6 | 10 (N1/E4/S1/W4) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-52` |
| 1030 | `campaign-1030` | 6×6 | 12 (N2/E0/S2/W8) | 5 (Pull3/Push2) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 1+ | `magnetic-circuit-52` |
| 1031 | `campaign-1031` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-52` |
| 1032 | `campaign-1032` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-52` |
| 1033 | `campaign-1033` | 8×8 | 18 (N5/E8/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-52` |
| 1034 | `campaign-1034` | 4×4 | 6 (N1/E1/S0/W4) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-52` |
| 1035 | `campaign-1035` | 8×8 | 14 (N7/E2/S2/W3) | 4 (Pull3/Push1) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-52` |
| 1036 | `campaign-1036` | 5×5 | 10 (N3/E2/S3/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-52` |
| 1037 | `campaign-1037` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-52` |
| 1038 | `campaign-1038` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-52` |
| 1039 | `campaign-1039` | 8×8 | 18 (N2/E8/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-52` |
| 1040 | `campaign-1040` | 5×5 | 7 (N1/E2/S2/W2) | 1 (Pull0/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-52` |
| 1041 | `campaign-1041` | 5×5 | 8 (N1/E5/S0/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-53` |
| 1042 | `campaign-1042` | 8×8 | 16 (N9/E1/S0/W6) | 5 (Pull3/Push2) | 19 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 8 | 1+ | `magnetic-circuit-53` |
| 1043 | `campaign-1043` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-53` |
| 1044 | `campaign-1044` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-53` |
| 1045 | `campaign-1045` | 8×8 | 18 (N2/E7/S0/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-53` |
| 1046 | `campaign-1046` | 5×5 | 7 (N2/E3/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-53` |
| 1047 | `campaign-1047` | 6×6 | 10 (N4/E2/S3/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-53` |
| 1048 | `campaign-1048` | 5×5 | 10 (N3/E4/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-53` |
| 1049 | `campaign-1049` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-53` |
| 1050 | `campaign-1050` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-53` |
| 1051 | `campaign-1051` | 8×8 | 18 (N6/E4/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-53` |
| 1052 | `campaign-1052` | 8×8 | 12 (N1/E1/S9/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-53` |
| 1053 | `campaign-1053` | 5×5 | 8 (N2/E2/S2/W2) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-53` |
| 1054 | `campaign-1054` | 7×7 | 14 (N2/E3/S5/W4) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-53` |
| 1055 | `campaign-1055` | 8×8 | 14 (N1/E5/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-53` |
| 1056 | `campaign-1056` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-53` |
| 1057 | `campaign-1057` | 8×8 | 18 (N4/E7/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-53` |
| 1058 | `campaign-1058` | 5×5 | 7 (N2/E0/S2/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-53` |
| 1059 | `campaign-1059` | 5×5 | 8 (N3/E1/S0/W4) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-53` |
| 1060 | `campaign-1060` | 5×5 | 10 (N3/E4/S2/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-53` |
| 1061 | `campaign-1061` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-54` |
| 1062 | `campaign-1062` | 8×8 | 16 (N7/E2/S4/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-54` |
| 1063 | `campaign-1063` | 8×8 | 18 (N2/E5/S5/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-54` |
| 1064 | `campaign-1064` | 4×4 | 6 (N3/E1/S1/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-54` |
| 1065 | `campaign-1065` | 5×5 | 8 (N1/E3/S1/W3) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-54` |
| 1066 | `campaign-1066` | 5×5 | 10 (N5/E2/S2/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-54` |
| 1067 | `campaign-1067` | 8×8 | 14 (N0/E4/S3/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-54` |
| 1068 | `campaign-1068` | 8×8 | 16 (N6/E3/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-54` |
| 1069 | `campaign-1069` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-54` |
| 1070 | `campaign-1070` | 5×5 | 7 (N2/E2/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-54` |
| 1071 | `campaign-1071` | 5×5 | 8 (N4/E3/S0/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-54` |
| 1072 | `campaign-1072` | 6×6 | 12 (N3/E1/S5/W3) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-54` |
| 1073 | `campaign-1073` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-54` |
| 1074 | `campaign-1074` | 8×8 | 16 (N6/E1/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-54` |
| 1075 | `campaign-1075` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-54` |
| 1076 | `campaign-1076` | 5×5 | 7 (N2/E2/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-54` |
| 1077 | `campaign-1077` | 6×6 | 10 (N2/E2/S4/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-54` |
| 1078 | `campaign-1078` | 5×5 | 10 (N3/E2/S0/W5) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-54` |
| 1079 | `campaign-1079` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-54` |
| 1080 | `campaign-1080` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-54` |
| 1081 | `campaign-1081` | 8×8 | 18 (N7/E7/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-55` |
| 1082 | `campaign-1082` | 4×4 | 6 (N2/E2/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-55` |
| 1083 | `campaign-1083` | 5×5 | 8 (N1/E4/S3/W0) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-55` |
| 1084 | `campaign-1084` | 5×5 | 10 (N6/E3/S0/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-55` |
| 1085 | `campaign-1085` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-55` |
| 1086 | `campaign-1086` | 8×8 | 16 (N7/E5/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-55` |
| 1087 | `campaign-1087` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-55` |
| 1088 | `campaign-1088` | 4×4 | 6 (N1/E1/S3/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-55` |
| 1089 | `campaign-1089` | 6×6 | 10 (N2/E1/S1/W6) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-55` |
| 1090 | `campaign-1090` | 5×5 | 10 (N5/E3/S2/W0) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-55` |
| 1091 | `campaign-1091` | 8×8 | 14 (N3/E4/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-55` |
| 1092 | `campaign-1092` | 8×8 | 16 (N9/E0/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-55` |
| 1093 | `campaign-1093` | 8×8 | 18 (N0/E7/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-55` |
| 1094 | `campaign-1094` | 4×4 | 6 (N2/E0/S2/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-55` |
| 1095 | `campaign-1095` | 5×5 | 8 (N2/E2/S2/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-55` |
| 1096 | `campaign-1096` | 8×8 | 16 (N3/E4/S7/W2) | 5 (Pull4/Push1) | 19 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 9 | 1+ | `magnetic-circuit-55` |
| 1097 | `campaign-1097` | 8×8 | 14 (N7/E0/S6/W1) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-55` |
| 1098 | `campaign-1098` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-55` |
| 1099 | `campaign-1099` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-55` |
| 1100 | `campaign-1100` | 5×5 | 7 (N1/E2/S2/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-55` |
| 1101 | `campaign-1101` | 6×6 | 10 (N3/E2/S2/W3) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-56` |
| 1102 | `campaign-1102` | 5×5 | 10 (N1/E3/S6/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-56` |
| 1103 | `campaign-1103` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-56` |
| 1104 | `campaign-1104` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-56` |
| 1105 | `campaign-1105` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-56` |
| 1106 | `campaign-1106` | 5×5 | 7 (N1/E3/S1/W2) | 1 (Pull0/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-56` |
| 1107 | `campaign-1107` | 6×6 | 10 (N0/E4/S6/W0) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-56` |
| 1108 | `campaign-1108` | 8×8 | 16 (N1/E5/S7/W3) | 2 (Pull1/Push1) | 22 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 7 | 1+ | `magnetic-circuit-56` |
| 1109 | `campaign-1109` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-56` |
| 1110 | `campaign-1110` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-56` |
| 1111 | `campaign-1111` | 8×8 | 18 (N6/E4/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-56` |
| 1112 | `campaign-1112` | 4×4 | 6 (N2/E0/S4/W0) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-56` |
| 1113 | `campaign-1113` | 5×5 | 8 (N2/E3/S1/W2) | 4 (Pull2/Push2) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-56` |
| 1114 | `campaign-1114` | 5×5 | 10 (N3/E0/S6/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 1+ | `magnetic-circuit-56` |
| 1115 | `campaign-1115` | 8×8 | 14 (N0/E4/S2/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-56` |
| 1116 | `campaign-1116` | 8×8 | 16 (N5/E0/S7/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-56` |
| 1117 | `campaign-1117` | 8×8 | 18 (N0/E6/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-56` |
| 1118 | `campaign-1118` | 8×8 | 12 (N2/E0/S3/W7) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-56` |
| 1119 | `campaign-1119` | 5×5 | 8 (N4/E0/S4/W0) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-56` |
| 1120 | `campaign-1120` | 7×7 | 14 (N8/E2/S2/W2) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-56` |
| 1121 | `campaign-1121` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-57` |
| 1122 | `campaign-1122` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-57` |
| 1123 | `campaign-1123` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-57` |
| 1124 | `campaign-1124` | 5×5 | 7 (N4/E3/S0/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-57` |
| 1125 | `campaign-1125` | 6×6 | 10 (N2/E0/S4/W4) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-57` |
| 1126 | `campaign-1126` | 6×6 | 12 (N9/E0/S0/W3) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-57` |
| 1127 | `campaign-1127` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-57` |
| 1128 | `campaign-1128` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-57` |
| 1129 | `campaign-1129` | 8×8 | 18 (N1/E6/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-57` |
| 1130 | `campaign-1130` | 5×5 | 7 (N4/E0/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-57` |
| 1131 | `campaign-1131` | 6×6 | 10 (N8/E0/S1/W1) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-57` |
| 1132 | `campaign-1132` | 6×6 | 12 (N2/E5/S2/W3) | 5 (Pull3/Push2) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-57` |
| 1133 | `campaign-1133` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-57` |
| 1134 | `campaign-1134` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-57` |
| 1135 | `campaign-1135` | 8×8 | 18 (N0/E4/S7/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-57` |
| 1136 | `campaign-1136` | 5×5 | 7 (N3/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-57` |
| 1137 | `campaign-1137` | 5×5 | 8 (N0/E3/S1/W4) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-57` |
| 1138 | `campaign-1138` | 5×5 | 10 (N4/E2/S2/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-57` |
| 1139 | `campaign-1139` | 8×8 | 14 (N2/E4/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-57` |
| 1140 | `campaign-1140` | 8×8 | 16 (N9/E0/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-57` |
| 1141 | `campaign-1141` | 8×8 | 18 (N7/E7/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-58` |
| 1142 | `campaign-1142` | 8×8 | 12 (N4/E1/S2/W5) | 1 (Pull0/Push1) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-58` |
| 1143 | `campaign-1143` | 6×6 | 10 (N2/E6/S2/W0) | 4 (Pull1/Push3) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-58` |
| 1144 | `campaign-1144` | 5×5 | 10 (N1/E3/S3/W3) | 5 (Pull3/Push2) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-58` |
| 1145 | `campaign-1145` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-58` |
| 1146 | `campaign-1146` | 8×8 | 16 (N6/E1/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-58` |
| 1147 | `campaign-1147` | 8×8 | 18 (N0/E11/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-58` |
| 1148 | `campaign-1148` | 4×4 | 6 (N2/E2/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-58` |
| 1149 | `campaign-1149` | 5×5 | 8 (N2/E3/S2/W1) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-58` |
| 1150 | `campaign-1150` | 6×6 | 12 (N1/E2/S7/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-58` |
| 1151 | `campaign-1151` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-58` |
| 1152 | `campaign-1152` | 8×8 | 16 (N5/E0/S6/W5) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-58` |
| 1153 | `campaign-1153` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-58` |
| 1154 | `campaign-1154` | 8×8 | 12 (N0/E2/S6/W4) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-58` |
| 1155 | `campaign-1155` | 6×6 | 10 (N3/E1/S2/W4) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-58` |
| 1156 | `campaign-1156` | 5×5 | 10 (N0/E2/S6/W2) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-58` |
| 1157 | `campaign-1157` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-58` |
| 1158 | `campaign-1158` | 8×8 | 16 (N7/E0/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-58` |
| 1159 | `campaign-1159` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-58` |
| 1160 | `campaign-1160` | 5×5 | 7 (N1/E3/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-58` |
| 1161 | `campaign-1161` | 6×6 | 10 (N2/E5/S0/W3) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-59` |
| 1162 | `campaign-1162` | 6×6 | 12 (N2/E2/S6/W2) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-59` |
| 1163 | `campaign-1163` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-59` |
| 1164 | `campaign-1164` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-59` |
| 1165 | `campaign-1165` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-59` |
| 1166 | `campaign-1166` | 4×4 | 6 (N3/E3/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-59` |
| 1167 | `campaign-1167` | 6×6 | 10 (N4/E2/S1/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 1+ | `magnetic-circuit-59` |
| 1168 | `campaign-1168` | 6×6 | 12 (N1/E1/S7/W3) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-59` |
| 1169 | `campaign-1169` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-59` |
| 1170 | `campaign-1170` | 8×8 | 16 (N8/E0/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-59` |
| 1171 | `campaign-1171` | 8×8 | 18 (N2/E4/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-59` |
| 1172 | `campaign-1172` | 5×5 | 7 (N2/E3/S0/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-59` |
| 1173 | `campaign-1173` | 5×5 | 8 (N0/E1/S5/W2) | 3 (Pull1/Push2) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-59` |
| 1174 | `campaign-1174` | 7×7 | 14 (N2/E3/S7/W2) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-59` |
| 1175 | `campaign-1175` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-59` |
| 1176 | `campaign-1176` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-59` |
| 1177 | `campaign-1177` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-59` |
| 1178 | `campaign-1178` | 5×5 | 7 (N3/E1/S3/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-59` |
| 1179 | `campaign-1179` | 5×5 | 8 (N3/E1/S2/W2) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-59` |
| 1180 | `campaign-1180` | 5×5 | 10 (N7/E1/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-59` |
| 1181 | `campaign-1181` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-60` |
| 1182 | `campaign-1182` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-60` |
| 1183 | `campaign-1183` | 8×8 | 18 (N1/E4/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-60` |
| 1184 | `campaign-1184` | 4×4 | 6 (N2/E3/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-60` |
| 1185 | `campaign-1185` | 5×5 | 8 (N1/E5/S0/W2) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-60` |
| 1186 | `campaign-1186` | 5×5 | 10 (N3/E5/S2/W0) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-60` |
| 1187 | `campaign-1187` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-60` |
| 1188 | `campaign-1188` | 8×8 | 16 (N8/E0/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-60` |
| 1189 | `campaign-1189` | 8×8 | 18 (N0/E6/S0/W12) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-60` |
| 1190 | `campaign-1190` | 4×4 | 6 (N1/E3/S2/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-60` |
| 1191 | `campaign-1191` | 5×5 | 8 (N1/E0/S4/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-60` |
| 1192 | `campaign-1192` | 7×7 | 14 (N11/E0/S1/W2) | 3 (Pull1/Push2) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 6 | 1+ | `magnetic-circuit-60` |
| 1193 | `campaign-1193` | 8×8 | 14 (N0/E7/S2/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-60` |
| 1194 | `campaign-1194` | 8×8 | 16 (N6/E1/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-60` |
| 1195 | `campaign-1195` | 8×8 | 18 (N0/E10/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-60` |
| 1196 | `campaign-1196` | 4×4 | 6 (N3/E0/S2/W1) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-60` |
| 1197 | `campaign-1197` | 8×8 | 14 (N2/E8/S3/W1) | 4 (Pull3/Push1) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 10 | 1+ | `magnetic-circuit-60` |
| 1198 | `campaign-1198` | 5×5 | 10 (N3/E4/S3/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-60` |
| 1199 | `campaign-1199` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-60` |
| 1200 | `campaign-1200` | 8×8 | 16 (N4/E2/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-60` |
| 1201 | `campaign-1201` | 8×8 | 18 (N0/E7/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-61` |
| 1202 | `campaign-1202` | 4×4 | 6 (N3/E2/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-61` |
| 1203 | `campaign-1203` | 5×5 | 8 (N0/E3/S2/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-61` |
| 1204 | `campaign-1204` | 5×5 | 10 (N4/E4/S1/W1) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-61` |
| 1205 | `campaign-1205` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-61` |
| 1206 | `campaign-1206` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 9 | 1+ | `magnetic-circuit-61` |
| 1207 | `campaign-1207` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-61` |
| 1208 | `campaign-1208` | 8×8 | 12 (N4/E0/S6/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-61` |
| 1209 | `campaign-1209` | 5×5 | 8 (N3/E2/S1/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-61` |
| 1210 | `campaign-1210` | 5×5 | 10 (N5/E3/S2/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-61` |
| 1211 | `campaign-1211` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-61` |
| 1212 | `campaign-1212` | 8×8 | 16 (N9/E1/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-61` |
| 1213 | `campaign-1213` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-61` |
| 1214 | `campaign-1214` | 4×4 | 6 (N0/E1/S4/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-61` |
| 1215 | `campaign-1215` | 6×6 | 10 (N5/E2/S2/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-61` |
| 1216 | `campaign-1216` | 5×5 | 10 (N7/E2/S1/W0) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-61` |
| 1217 | `campaign-1217` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-61` |
| 1218 | `campaign-1218` | 8×8 | 16 (N7/E0/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-61` |
| 1219 | `campaign-1219` | 8×8 | 18 (N6/E5/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-61` |
| 1220 | `campaign-1220` | 4×4 | 6 (N4/E1/S1/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-61` |
| 1221 | `campaign-1221` | 6×6 | 10 (N2/E4/S2/W2) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-62` |
| 1222 | `campaign-1222` | 7×7 | 14 (N0/E4/S9/W1) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-62` |
| 1223 | `campaign-1223` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-62` |
| 1224 | `campaign-1224` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-62` |
| 1225 | `campaign-1225` | 8×8 | 18 (N1/E7/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-62` |
| 1226 | `campaign-1226` | 4×4 | 6 (N2/E4/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-62` |
| 1227 | `campaign-1227` | 5×5 | 8 (N2/E5/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-62` |
| 1228 | `campaign-1228` | 5×5 | 10 (N4/E1/S2/W3) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-62` |
| 1229 | `campaign-1229` | 8×8 | 14 (N0/E7/S2/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-62` |
| 1230 | `campaign-1230` | 8×8 | 16 (N8/E0/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-62` |
| 1231 | `campaign-1231` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-62` |
| 1232 | `campaign-1232` | 5×5 | 7 (N3/E0/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-62` |
| 1233 | `campaign-1233` | 5×5 | 8 (N3/E3/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-62` |
| 1234 | `campaign-1234` | 5×5 | 10 (N0/E7/S2/W1) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-62` |
| 1235 | `campaign-1235` | 8×8 | 14 (N1/E5/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-62` |
| 1236 | `campaign-1236` | 8×8 | 16 (N9/E0/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-62` |
| 1237 | `campaign-1237` | 8×8 | 18 (N7/E7/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-62` |
| 1238 | `campaign-1238` | 8×8 | 12 (N1/E2/S1/W8) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-62` |
| 1239 | `campaign-1239` | 5×5 | 8 (N0/E1/S2/W5) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-62` |
| 1240 | `campaign-1240` | 6×6 | 12 (N8/E3/S1/W0) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 4 | 1+ | `magnetic-circuit-62` |
| 1241 | `campaign-1241` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-63` |
| 1242 | `campaign-1242` | 8×8 | 16 (N7/E0/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-63` |
| 1243 | `campaign-1243` | 8×8 | 18 (N0/E4/S7/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-63` |
| 1244 | `campaign-1244` | 5×5 | 7 (N3/E1/S1/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-63` |
| 1245 | `campaign-1245` | 6×6 | 10 (N1/E2/S0/W7) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-63` |
| 1246 | `campaign-1246` | 5×5 | 10 (N0/E3/S6/W1) | 4 (Pull3/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-63` |
| 1247 | `campaign-1247` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-63` |
| 1248 | `campaign-1248` | 8×8 | 16 (N5/E1/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-63` |
| 1249 | `campaign-1249` | 8×8 | 18 (N0/E8/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-63` |
| 1250 | `campaign-1250` | 5×5 | 7 (N2/E2/S0/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-63` |
| 1251 | `campaign-1251` | 8×8 | 14 (N9/E4/S0/W1) | 1 (Pull0/Push1) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 3 | 1+ | `magnetic-circuit-63` |
| 1252 | `campaign-1252` | 6×6 | 12 (N4/E1/S4/W3) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-63` |
| 1253 | `campaign-1253` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-63` |
| 1254 | `campaign-1254` | 8×8 | 16 (N7/E0/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-63` |
| 1255 | `campaign-1255` | 8×8 | 18 (N0/E11/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-63` |
| 1256 | `campaign-1256` | 4×4 | 6 (N3/E1/S0/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-63` |
| 1257 | `campaign-1257` | 5×5 | 8 (N4/E3/S1/W0) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-63` |
| 1258 | `campaign-1258` | 6×6 | 12 (N8/E3/S0/W1) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-63` |
| 1259 | `campaign-1259` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-63` |
| 1260 | `campaign-1260` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-63` |
| 1261 | `campaign-1261` | 8×8 | 18 (N0/E6/S0/W12) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-64` |
| 1262 | `campaign-1262` | 4×4 | 6 (N0/E6/S0/W0) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-64` |
| 1263 | `campaign-1263` | 5×5 | 8 (N3/E2/S2/W1) | 4 (Pull2/Push2) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-64` |
| 1264 | `campaign-1264` | 5×5 | 10 (N6/E2/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-64` |
| 1265 | `campaign-1265` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-64` |
| 1266 | `campaign-1266` | 8×8 | 16 (N6/E1/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-64` |
| 1267 | `campaign-1267` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-64` |
| 1268 | `campaign-1268` | 4×4 | 6 (N0/E3/S3/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-64` |
| 1269 | `campaign-1269` | 6×6 | 10 (N3/E3/S0/W4) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-64` |
| 1270 | `campaign-1270` | 8×8 | 16 (N3/E1/S2/W10) | 4 (Pull3/Push1) | 20 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 7 | 1+ | `magnetic-circuit-64` |
| 1271 | `campaign-1271` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-64` |
| 1272 | `campaign-1272` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-64` |
| 1273 | `campaign-1273` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-64` |
| 1274 | `campaign-1274` | 5×5 | 7 (N2/E3/S0/W2) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-64` |
| 1275 | `campaign-1275` | 6×6 | 10 (N0/E7/S1/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-64` |
| 1276 | `campaign-1276` | 5×5 | 10 (N2/E2/S3/W3) | 5 (Pull2/Push3) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-64` |
| 1277 | `campaign-1277` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-64` |
| 1278 | `campaign-1278` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-64` |
| 1279 | `campaign-1279` | 8×8 | 18 (N1/E11/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-64` |
| 1280 | `campaign-1280` | 5×5 | 7 (N1/E4/S2/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-64` |
| 1281 | `campaign-1281` | 6×6 | 10 (N2/E3/S2/W3) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-65` |
| 1282 | `campaign-1282` | 6×6 | 12 (N3/E1/S7/W1) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-65` |
| 1283 | `campaign-1283` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-65` |
| 1284 | `campaign-1284` | 8×8 | 16 (N5/E4/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-65` |
| 1285 | `campaign-1285` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-65` |
| 1286 | `campaign-1286` | 4×4 | 6 (N1/E2/S0/W3) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-65` |
| 1287 | `campaign-1287` | 8×8 | 14 (N4/E3/S6/W1) | 4 (Pull2/Push2) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-65` |
| 1288 | `campaign-1288` | 5×5 | 10 (N3/E2/S2/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-65` |
| 1289 | `campaign-1289` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-65` |
| 1290 | `campaign-1290` | 8×8 | 16 (N8/E0/S5/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-65` |
| 1291 | `campaign-1291` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-65` |
| 1292 | `campaign-1292` | 5×5 | 7 (N1/E2/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-65` |
| 1293 | `campaign-1293` | 5×5 | 8 (N4/E3/S0/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-65` |
| 1294 | `campaign-1294` | 5×5 | 10 (N2/E1/S6/W1) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-65` |
| 1295 | `campaign-1295` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-65` |
| 1296 | `campaign-1296` | 8×8 | 16 (N5/E2/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-65` |
| 1297 | `campaign-1297` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-65` |
| 1298 | `campaign-1298` | 5×5 | 7 (N2/E1/S4/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-65` |
| 1299 | `campaign-1299` | 6×6 | 10 (N5/E0/S4/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-65` |
| 1300 | `campaign-1300` | 6×6 | 12 (N8/E1/S2/W1) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-65` |
| 1301 | `campaign-1301` | 8×8 | 14 (N3/E7/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-66` |
| 1302 | `campaign-1302` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-66` |
| 1303 | `campaign-1303` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-66` |
| 1304 | `campaign-1304` | 5×5 | 7 (N1/E3/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-66` |
| 1305 | `campaign-1305` | 8×8 | 14 (N4/E8/S2/W0) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-66` |
| 1306 | `campaign-1306` | 5×5 | 10 (N3/E1/S3/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-66` |
| 1307 | `campaign-1307` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-66` |
| 1308 | `campaign-1308` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-66` |
| 1309 | `campaign-1309` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-66` |
| 1310 | `campaign-1310` | 5×5 | 7 (N3/E3/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-66` |
| 1311 | `campaign-1311` | 5×5 | 8 (N2/E5/S0/W1) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-66` |
| 1312 | `campaign-1312` | 5×5 | 10 (N0/E1/S4/W5) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-66` |
| 1313 | `campaign-1313` | 8×8 | 14 (N2/E8/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-66` |
| 1314 | `campaign-1314` | 8×8 | 16 (N7/E3/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-66` |
| 1315 | `campaign-1315` | 8×8 | 18 (N0/E6/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-66` |
| 1316 | `campaign-1316` | 5×5 | 7 (N1/E4/S0/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-66` |
| 1317 | `campaign-1317` | 5×5 | 8 (N3/E4/S0/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-66` |
| 1318 | `campaign-1318` | 7×7 | 14 (N8/E3/S2/W1) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-66` |
| 1319 | `campaign-1319` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-66` |
| 1320 | `campaign-1320` | 8×8 | 16 (N5/E3/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-66` |
| 1321 | `campaign-1321` | 8×8 | 18 (N6/E6/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-67` |
| 1322 | `campaign-1322` | 5×5 | 7 (N1/E2/S2/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-67` |
| 1323 | `campaign-1323` | 5×5 | 8 (N3/E3/S2/W0) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-67` |
| 1324 | `campaign-1324` | 5×5 | 10 (N3/E4/S1/W2) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-67` |
| 1325 | `campaign-1325` | 8×8 | 14 (N2/E7/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-67` |
| 1326 | `campaign-1326` | 8×8 | 16 (N7/E3/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-67` |
| 1327 | `campaign-1327` | 8×8 | 18 (N0/E5/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-67` |
| 1328 | `campaign-1328` | 5×5 | 7 (N2/E3/S0/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-67` |
| 1329 | `campaign-1329` | 5×5 | 8 (N4/E1/S2/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-67` |
| 1330 | `campaign-1330` | 5×5 | 10 (N2/E3/S4/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-67` |
| 1331 | `campaign-1331` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-67` |
| 1332 | `campaign-1332` | 8×8 | 16 (N6/E2/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-67` |
| 1333 | `campaign-1333` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-67` |
| 1334 | `campaign-1334` | 4×4 | 6 (N2/E1/S0/W3) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-67` |
| 1335 | `campaign-1335` | 5×5 | 8 (N2/E3/S0/W3) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-67` |
| 1336 | `campaign-1336` | 6×6 | 12 (N3/E8/S1/W0) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-67` |
| 1337 | `campaign-1337` | 8×8 | 14 (N3/E6/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-67` |
| 1338 | `campaign-1338` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-67` |
| 1339 | `campaign-1339` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-67` |
| 1340 | `campaign-1340` | 5×5 | 7 (N2/E1/S4/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-67` |
| 1341 | `campaign-1341` | 5×5 | 8 (N1/E1/S4/W2) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-68` |
| 1342 | `campaign-1342` | 8×8 | 16 (N2/E2/S10/W2) | 4 (Pull2/Push2) | 20 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 7 | 1+ | `magnetic-circuit-68` |
| 1343 | `campaign-1343` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-68` |
| 1344 | `campaign-1344` | 8×8 | 16 (N4/E4/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-68` |
| 1345 | `campaign-1345` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-68` |
| 1346 | `campaign-1346` | 8×8 | 12 (N2/E2/S4/W4) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-68` |
| 1347 | `campaign-1347` | 6×6 | 10 (N2/E3/S1/W4) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-68` |
| 1348 | `campaign-1348` | 5×5 | 10 (N0/E5/S3/W2) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-68` |
| 1349 | `campaign-1349` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-68` |
| 1350 | `campaign-1350` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-68` |
| 1351 | `campaign-1351` | 8×8 | 18 (N7/E4/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-68` |
| 1352 | `campaign-1352` | 5×5 | 7 (N4/E1/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-68` |
| 1353 | `campaign-1353` | 5×5 | 8 (N0/E4/S2/W2) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-68` |
| 1354 | `campaign-1354` | 5×5 | 10 (N3/E2/S3/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-68` |
| 1355 | `campaign-1355` | 8×8 | 14 (N3/E4/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-68` |
| 1356 | `campaign-1356` | 8×8 | 16 (N6/E1/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-68` |
| 1357 | `campaign-1357` | 8×8 | 18 (N0/E7/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-68` |
| 1358 | `campaign-1358` | 4×4 | 6 (N2/E0/S1/W3) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-68` |
| 1359 | `campaign-1359` | 6×6 | 10 (N1/E3/S3/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-68` |
| 1360 | `campaign-1360` | 5×5 | 10 (N2/E4/S1/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-68` |
| 1361 | `campaign-1361` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-69` |
| 1362 | `campaign-1362` | 8×8 | 16 (N6/E5/S5/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-69` |
| 1363 | `campaign-1363` | 8×8 | 18 (N7/E4/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-69` |
| 1364 | `campaign-1364` | 4×4 | 6 (N2/E1/S1/W2) | 2 (Pull2/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-69` |
| 1365 | `campaign-1365` | 5×5 | 8 (N2/E4/S0/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-69` |
| 1366 | `campaign-1366` | 5×5 | 10 (N5/E3/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-69` |
| 1367 | `campaign-1367` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-69` |
| 1368 | `campaign-1368` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-69` |
| 1369 | `campaign-1369` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-69` |
| 1370 | `campaign-1370` | 4×4 | 6 (N1/E2/S2/W1) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-69` |
| 1371 | `campaign-1371` | 5×5 | 8 (N2/E1/S3/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-69` |
| 1372 | `campaign-1372` | 6×6 | 12 (N9/E1/S0/W2) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-69` |
| 1373 | `campaign-1373` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-69` |
| 1374 | `campaign-1374` | 8×8 | 16 (N7/E0/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-69` |
| 1375 | `campaign-1375` | 8×8 | 18 (N1/E6/S2/W9) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 14 | 1+ | `magnetic-circuit-69` |
| 1376 | `campaign-1376` | 5×5 | 7 (N4/E0/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-69` |
| 1377 | `campaign-1377` | 6×6 | 10 (N3/E2/S1/W4) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-69` |
| 1378 | `campaign-1378` | 5×5 | 10 (N4/E2/S1/W3) | 5 (Pull3/Push2) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-69` |
| 1379 | `campaign-1379` | 8×8 | 14 (N0/E5/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-69` |
| 1380 | `campaign-1380` | 8×8 | 16 (N6/E3/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-69` |
| 1381 | `campaign-1381` | 8×8 | 18 (N6/E7/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-70` |
| 1382 | `campaign-1382` | 5×5 | 7 (N1/E3/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-70` |
| 1383 | `campaign-1383` | 5×5 | 8 (N3/E1/S1/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-70` |
| 1384 | `campaign-1384` | 6×6 | 12 (N1/E1/S8/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-70` |
| 1385 | `campaign-1385` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-70` |
| 1386 | `campaign-1386` | 8×8 | 16 (N6/E2/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-70` |
| 1387 | `campaign-1387` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-70` |
| 1388 | `campaign-1388` | 5×5 | 7 (N4/E0/S2/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-70` |
| 1389 | `campaign-1389` | 5×5 | 8 (N4/E3/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-70` |
| 1390 | `campaign-1390` | 5×5 | 10 (N4/E3/S2/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-70` |
| 1391 | `campaign-1391` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-70` |
| 1392 | `campaign-1392` | 8×8 | 16 (N6/E0/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-70` |
| 1393 | `campaign-1393` | 8×8 | 18 (N6/E8/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-70` |
| 1394 | `campaign-1394` | 5×5 | 7 (N3/E1/S2/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-70` |
| 1395 | `campaign-1395` | 6×6 | 10 (N4/E3/S2/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-70` |
| 1396 | `campaign-1396` | 5×5 | 10 (N3/E4/S1/W2) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-70` |
| 1397 | `campaign-1397` | 8×8 | 14 (N0/E7/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-70` |
| 1398 | `campaign-1398` | 8×8 | 16 (N8/E1/S5/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-70` |
| 1399 | `campaign-1399` | 8×8 | 18 (N0/E10/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-70` |
| 1400 | `campaign-1400` | 8×8 | 12 (N1/E7/S3/W1) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-70` |
| 1401 | `campaign-1401` | 6×6 | 10 (N4/E1/S2/W3) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-71` |
| 1402 | `campaign-1402` | 5×5 | 10 (N2/E2/S5/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-71` |
| 1403 | `campaign-1403` | 8×8 | 14 (N0/E4/S2/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-71` |
| 1404 | `campaign-1404` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-71` |
| 1405 | `campaign-1405` | 8×8 | 18 (N1/E6/S0/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-71` |
| 1406 | `campaign-1406` | 8×8 | 12 (N2/E2/S1/W7) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-71` |
| 1407 | `campaign-1407` | 6×6 | 10 (N3/E2/S1/W4) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-71` |
| 1408 | `campaign-1408` | 6×6 | 12 (N3/E7/S0/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-71` |
| 1409 | `campaign-1409` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-71` |
| 1410 | `campaign-1410` | 8×8 | 16 (N8/E0/S5/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-71` |
| 1411 | `campaign-1411` | 8×8 | 18 (N7/E4/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-71` |
| 1412 | `campaign-1412` | 5×5 | 7 (N3/E1/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-71` |
| 1413 | `campaign-1413` | 6×6 | 10 (N5/E1/S4/W0) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-71` |
| 1414 | `campaign-1414` | 6×6 | 12 (N0/E7/S2/W3) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-71` |
| 1415 | `campaign-1415` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-71` |
| 1416 | `campaign-1416` | 8×8 | 16 (N5/E2/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-71` |
| 1417 | `campaign-1417` | 8×8 | 18 (N1/E7/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-71` |
| 1418 | `campaign-1418` | 5×5 | 7 (N1/E4/S1/W1) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-71` |
| 1419 | `campaign-1419` | 8×8 | 14 (N1/E5/S1/W7) | 3 (Pull3/Push0) | 19 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 9 | 1+ | `magnetic-circuit-71` |
| 1420 | `campaign-1420` | 5×5 | 10 (N1/E0/S5/W4) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-71` |
| 1421 | `campaign-1421` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-72` |
| 1422 | `campaign-1422` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-72` |
| 1423 | `campaign-1423` | 8×8 | 18 (N0/E11/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-72` |
| 1424 | `campaign-1424` | 8×8 | 12 (N4/E3/S0/W5) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-72` |
| 1425 | `campaign-1425` | 5×5 | 8 (N5/E1/S1/W1) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-72` |
| 1426 | `campaign-1426` | 5×5 | 10 (N3/E3/S4/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-72` |
| 1427 | `campaign-1427` | 8×8 | 14 (N3/E4/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-72` |
| 1428 | `campaign-1428` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-72` |
| 1429 | `campaign-1429` | 8×8 | 18 (N11/E1/S6/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-72` |
| 1430 | `campaign-1430` | 4×4 | 6 (N1/E1/S2/W2) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-72` |
| 1431 | `campaign-1431` | 6×6 | 10 (N2/E4/S2/W2) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-72` |
| 1432 | `campaign-1432` | 5×5 | 10 (N2/E5/S2/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-72` |
| 1433 | `campaign-1433` | 8×8 | 14 (N2/E7/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-72` |
| 1434 | `campaign-1434` | 8×8 | 16 (N7/E3/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-72` |
| 1435 | `campaign-1435` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-72` |
| 1436 | `campaign-1436` | 4×4 | 6 (N2/E2/S0/W2) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-72` |
| 1437 | `campaign-1437` | 6×6 | 10 (N2/E3/S4/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-72` |
| 1438 | `campaign-1438` | 5×5 | 10 (N5/E2/S3/W0) | 5 (Pull1/Push4) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-72` |
| 1439 | `campaign-1439` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-72` |
| 1440 | `campaign-1440` | 8×8 | 16 (N7/E0/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-72` |
| 1441 | `campaign-1441` | 8×8 | 18 (N1/E7/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-73` |
| 1442 | `campaign-1442` | 8×8 | 12 (N2/E0/S4/W6) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-73` |
| 1443 | `campaign-1443` | 6×6 | 10 (N3/E2/S3/W2) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-73` |
| 1444 | `campaign-1444` | 5×5 | 10 (N2/E0/S4/W4) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-73` |
| 1445 | `campaign-1445` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-73` |
| 1446 | `campaign-1446` | 8×8 | 16 (N7/E1/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-73` |
| 1447 | `campaign-1447` | 8×8 | 18 (N1/E11/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-73` |
| 1448 | `campaign-1448` | 8×8 | 12 (N2/E2/S5/W3) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-73` |
| 1449 | `campaign-1449` | 8×8 | 14 (N1/E0/S7/W6) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-73` |
| 1450 | `campaign-1450` | 6×6 | 12 (N5/E5/S1/W1) | 3 (Pull1/Push2) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-73` |
| 1451 | `campaign-1451` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-73` |
| 1452 | `campaign-1452` | 8×8 | 16 (N6/E2/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-73` |
| 1453 | `campaign-1453` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-73` |
| 1454 | `campaign-1454` | 5×5 | 7 (N4/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-73` |
| 1455 | `campaign-1455` | 8×8 | 14 (N6/E4/S1/W3) | 1 (Pull0/Push1) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 4 | 1+ | `magnetic-circuit-73` |
| 1456 | `campaign-1456` | 6×6 | 12 (N0/E2/S9/W1) | 5 (Pull2/Push3) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-73` |
| 1457 | `campaign-1457` | 8×8 | 14 (N2/E8/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-73` |
| 1458 | `campaign-1458` | 8×8 | 16 (N6/E1/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-73` |
| 1459 | `campaign-1459` | 8×8 | 18 (N6/E5/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-73` |
| 1460 | `campaign-1460` | 8×8 | 12 (N3/E1/S2/W6) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-73` |
| 1461 | `campaign-1461` | 5×5 | 8 (N4/E2/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-74` |
| 1462 | `campaign-1462` | 8×8 | 16 (N3/E1/S10/W2) | 4 (Pull2/Push2) | 20 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 6 | 1+ | `magnetic-circuit-74` |
| 1463 | `campaign-1463` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-74` |
| 1464 | `campaign-1464` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-74` |
| 1465 | `campaign-1465` | 8×8 | 18 (N0/E7/S0/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-74` |
| 1466 | `campaign-1466` | 5×5 | 7 (N2/E3/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-74` |
| 1467 | `campaign-1467` | 5×5 | 8 (N1/E5/S0/W2) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-74` |
| 1468 | `campaign-1468` | 5×5 | 10 (N4/E3/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-74` |
| 1469 | `campaign-1469` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-74` |
| 1470 | `campaign-1470` | 8×8 | 16 (N7/E0/S9/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-74` |
| 1471 | `campaign-1471` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-74` |
| 1472 | `campaign-1472` | 5×5 | 7 (N2/E2/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-74` |
| 1473 | `campaign-1473` | 5×5 | 8 (N3/E2/S0/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-74` |
| 1474 | `campaign-1474` | 7×7 | 14 (N3/E1/S9/W1) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-74` |
| 1475 | `campaign-1475` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-74` |
| 1476 | `campaign-1476` | 8×8 | 16 (N5/E3/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-74` |
| 1477 | `campaign-1477` | 8×8 | 18 (N1/E7/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-74` |
| 1478 | `campaign-1478` | 4×4 | 6 (N3/E2/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-74` |
| 1479 | `campaign-1479` | 6×6 | 10 (N2/E2/S2/W4) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-74` |
| 1480 | `campaign-1480` | 6×6 | 12 (N5/E1/S4/W2) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-74` |
| 1481 | `campaign-1481` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-75` |
| 1482 | `campaign-1482` | 8×8 | 16 (N6/E3/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-75` |
| 1483 | `campaign-1483` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-75` |
| 1484 | `campaign-1484` | 8×8 | 12 (N8/E2/S0/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-75` |
| 1485 | `campaign-1485` | 6×6 | 10 (N4/E5/S1/W0) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-75` |
| 1486 | `campaign-1486` | 5×5 | 10 (N3/E1/S5/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-75` |
| 1487 | `campaign-1487` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-75` |
| 1488 | `campaign-1488` | 8×8 | 16 (N5/E2/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-75` |
| 1489 | `campaign-1489` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-75` |
| 1490 | `campaign-1490` | 5×5 | 7 (N3/E1/S1/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-75` |
| 1491 | `campaign-1491` | 5×5 | 8 (N2/E2/S4/W0) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-75` |
| 1492 | `campaign-1492` | 6×6 | 12 (N2/E1/S9/W0) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-75` |
| 1493 | `campaign-1493` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-75` |
| 1494 | `campaign-1494` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-75` |
| 1495 | `campaign-1495` | 8×8 | 18 (N7/E4/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-75` |
| 1496 | `campaign-1496` | 5×5 | 7 (N5/E2/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-75` |
| 1497 | `campaign-1497` | 5×5 | 8 (N2/E1/S2/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-75` |
| 1498 | `campaign-1498` | 5×5 | 10 (N4/E2/S2/W2) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-75` |
| 1499 | `campaign-1499` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-75` |
| 1500 | `campaign-1500` | 8×8 | 16 (N7/E0/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-75` |
| 1501 | `campaign-1501` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-76` |
| 1502 | `campaign-1502` | 5×5 | 7 (N2/E0/S3/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-76` |
| 1503 | `campaign-1503` | 6×6 | 10 (N4/E2/S2/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-76` |
| 1504 | `campaign-1504` | 5×5 | 10 (N5/E1/S0/W4) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-76` |
| 1505 | `campaign-1505` | 8×8 | 14 (N3/E6/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-76` |
| 1506 | `campaign-1506` | 8×8 | 16 (N8/E3/S4/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-76` |
| 1507 | `campaign-1507` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-76` |
| 1508 | `campaign-1508` | 5×5 | 7 (N1/E1/S2/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-76` |
| 1509 | `campaign-1509` | 6×6 | 10 (N1/E4/S2/W3) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-76` |
| 1510 | `campaign-1510` | 5×5 | 10 (N5/E1/S0/W4) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-76` |
| 1511 | `campaign-1511` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-76` |
| 1512 | `campaign-1512` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-76` |
| 1513 | `campaign-1513` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-76` |
| 1514 | `campaign-1514` | 8×8 | 12 (N1/E1/S5/W5) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-76` |
| 1515 | `campaign-1515` | 5×5 | 8 (N1/E7/S0/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-76` |
| 1516 | `campaign-1516` | 6×6 | 12 (N6/E0/S3/W3) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-76` |
| 1517 | `campaign-1517` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-76` |
| 1518 | `campaign-1518` | 8×8 | 16 (N8/E2/S4/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-76` |
| 1519 | `campaign-1519` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-76` |
| 1520 | `campaign-1520` | 5×5 | 7 (N2/E0/S2/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-76` |
| 1521 | `campaign-1521` | 6×6 | 10 (N4/E2/S1/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-77` |
| 1522 | `campaign-1522` | 6×6 | 12 (N3/E2/S4/W3) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-77` |
| 1523 | `campaign-1523` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-77` |
| 1524 | `campaign-1524` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-77` |
| 1525 | `campaign-1525` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-77` |
| 1526 | `campaign-1526` | 8×8 | 12 (N2/E2/S5/W3) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 10 | 1+ | `magnetic-circuit-77` |
| 1527 | `campaign-1527` | 6×6 | 10 (N3/E5/S1/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-77` |
| 1528 | `campaign-1528` | 6×6 | 12 (N7/E3/S0/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-77` |
| 1529 | `campaign-1529` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-77` |
| 1530 | `campaign-1530` | 8×8 | 16 (N7/E2/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-77` |
| 1531 | `campaign-1531` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-77` |
| 1532 | `campaign-1532` | 4×4 | 6 (N1/E3/S1/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-77` |
| 1533 | `campaign-1533` | 5×5 | 8 (N1/E3/S1/W3) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-77` |
| 1534 | `campaign-1534` | 5×5 | 10 (N0/E3/S4/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-77` |
| 1535 | `campaign-1535` | 8×8 | 14 (N2/E4/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-77` |
| 1536 | `campaign-1536` | 8×8 | 16 (N5/E2/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-77` |
| 1537 | `campaign-1537` | 8×8 | 18 (N7/E7/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-77` |
| 1538 | `campaign-1538` | 4×4 | 6 (N1/E3/S1/W1) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-77` |
| 1539 | `campaign-1539` | 5×5 | 8 (N1/E4/S3/W0) | 3 (Pull2/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-77` |
| 1540 | `campaign-1540` | 6×6 | 12 (N0/E9/S2/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-77` |
| 1541 | `campaign-1541` | 8×8 | 14 (N3/E6/S1/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-78` |
| 1542 | `campaign-1542` | 8×8 | 16 (N2/E6/S0/W8) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-78` |
| 1543 | `campaign-1543` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-78` |
| 1544 | `campaign-1544` | 5×5 | 7 (N2/E1/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-78` |
| 1545 | `campaign-1545` | 5×5 | 8 (N1/E0/S6/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-78` |
| 1546 | `campaign-1546` | 5×5 | 10 (N2/E3/S4/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-78` |
| 1547 | `campaign-1547` | 8×8 | 14 (N2/E4/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-78` |
| 1548 | `campaign-1548` | 8×8 | 16 (N1/E9/S0/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-78` |
| 1549 | `campaign-1549` | 8×8 | 18 (N2/E6/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-78` |
| 1550 | `campaign-1550` | 4×4 | 6 (N2/E2/S0/W2) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-78` |
| 1551 | `campaign-1551` | 8×8 | 14 (N5/E7/S2/W0) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-78` |
| 1552 | `campaign-1552` | 5×5 | 10 (N1/E4/S3/W2) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-78` |
| 1553 | `campaign-1553` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-78` |
| 1554 | `campaign-1554` | 8×8 | 16 (N6/E4/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-78` |
| 1555 | `campaign-1555` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-78` |
| 1556 | `campaign-1556` | 8×8 | 12 (N4/E2/S3/W3) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-78` |
| 1557 | `campaign-1557` | 5×5 | 8 (N3/E0/S3/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-78` |
| 1558 | `campaign-1558` | 5×5 | 10 (N2/E3/S3/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-78` |
| 1559 | `campaign-1559` | 8×8 | 14 (N2/E5/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-78` |
| 1560 | `campaign-1560` | 8×8 | 16 (N4/E0/S9/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-78` |
| 1561 | `campaign-1561` | 8×8 | 18 (N0/E6/S1/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-79` |
| 1562 | `campaign-1562` | 5×5 | 7 (N3/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-79` |
| 1563 | `campaign-1563` | 5×5 | 8 (N3/E0/S1/W4) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-79` |
| 1564 | `campaign-1564` | 5×5 | 10 (N2/E1/S4/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-79` |
| 1565 | `campaign-1565` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-79` |
| 1566 | `campaign-1566` | 8×8 | 16 (N6/E4/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-79` |
| 1567 | `campaign-1567` | 8×8 | 18 (N2/E10/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-79` |
| 1568 | `campaign-1568` | 4×4 | 6 (N2/E1/S3/W0) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 6 | 1+ | `magnetic-circuit-79` |
| 1569 | `campaign-1569` | 5×5 | 8 (N1/E2/S2/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-79` |
| 1570 | `campaign-1570` | 5×5 | 10 (N2/E4/S1/W3) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-79` |
| 1571 | `campaign-1571` | 8×8 | 14 (N2/E5/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-79` |
| 1572 | `campaign-1572` | 8×8 | 16 (N5/E0/S7/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-79` |
| 1573 | `campaign-1573` | 8×8 | 18 (N6/E7/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-79` |
| 1574 | `campaign-1574` | 4×4 | 6 (N0/E3/S2/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-79` |
| 1575 | `campaign-1575` | 6×6 | 10 (N2/E2/S3/W3) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-79` |
| 1576 | `campaign-1576` | 7×7 | 14 (N3/E8/S1/W2) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 1+ | `magnetic-circuit-79` |
| 1577 | `campaign-1577` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-79` |
| 1578 | `campaign-1578` | 8×8 | 16 (N7/E1/S5/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-79` |
| 1579 | `campaign-1579` | 8×8 | 18 (N1/E10/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-79` |
| 1580 | `campaign-1580` | 5×5 | 7 (N3/E2/S1/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-79` |
| 1581 | `campaign-1581` | 6×6 | 10 (N3/E0/S4/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-80` |
| 1582 | `campaign-1582` | 5×5 | 10 (N1/E1/S4/W4) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-80` |
| 1583 | `campaign-1583` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-80` |
| 1584 | `campaign-1584` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-80` |
| 1585 | `campaign-1585` | 8×8 | 18 (N1/E7/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-80` |
| 1586 | `campaign-1586` | 5×5 | 7 (N1/E5/S1/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-80` |
| 1587 | `campaign-1587` | 6×6 | 10 (N3/E1/S3/W3) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-80` |
| 1588 | `campaign-1588` | 5×5 | 10 (N3/E3/S2/W2) | 5 (Pull2/Push3) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 1+ | `magnetic-circuit-80` |
| 1589 | `campaign-1589` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-80` |
| 1590 | `campaign-1590` | 8×8 | 16 (N8/E2/S4/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-80` |
| 1591 | `campaign-1591` | 8×8 | 18 (N1/E4/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-80` |
| 1592 | `campaign-1592` | 5×5 | 7 (N2/E3/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-80` |
| 1593 | `campaign-1593` | 5×5 | 8 (N3/E3/S0/W2) | 1 (Pull0/Push1) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-80` |
| 1594 | `campaign-1594` | 7×7 | 14 (N7/E2/S3/W2) | 2 (Pull1/Push1) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 6 | 1+ | `magnetic-circuit-80` |
| 1595 | `campaign-1595` | 8×8 | 14 (N6/E0/S7/W1) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-80` |
| 1596 | `campaign-1596` | 8×8 | 16 (N6/E3/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-80` |
| 1597 | `campaign-1597` | 8×8 | 18 (N10/E2/S6/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-80` |
| 1598 | `campaign-1598` | 5×5 | 7 (N6/E1/S0/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-80` |
| 1599 | `campaign-1599` | 6×6 | 10 (N1/E5/S0/W4) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-80` |
| 1600 | `campaign-1600` | 6×6 | 12 (N4/E3/S0/W5) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-80` |
| 1601 | `campaign-1601` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-81` |
| 1602 | `campaign-1602` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-81` |
| 1603 | `campaign-1603` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-81` |
| 1604 | `campaign-1604` | 4×4 | 6 (N0/E2/S3/W1) | 2 (Pull2/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 6 | 1+ | `magnetic-circuit-81` |
| 1605 | `campaign-1605` | 5×5 | 8 (N2/E4/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-81` |
| 1606 | `campaign-1606` | 5×5 | 10 (N1/E4/S3/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-81` |
| 1607 | `campaign-1607` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-81` |
| 1608 | `campaign-1608` | 8×8 | 16 (N6/E1/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-81` |
| 1609 | `campaign-1609` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-81` |
| 1610 | `campaign-1610` | 5×5 | 7 (N3/E0/S2/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-81` |
| 1611 | `campaign-1611` | 6×6 | 10 (N5/E1/S3/W1) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-81` |
| 1612 | `campaign-1612` | 6×6 | 12 (N9/E1/S1/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-81` |
| 1613 | `campaign-1613` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-81` |
| 1614 | `campaign-1614` | 8×8 | 16 (N6/E3/S5/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-81` |
| 1615 | `campaign-1615` | 8×8 | 18 (N7/E4/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-81` |
| 1616 | `campaign-1616` | 5×5 | 7 (N2/E2/S1/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-81` |
| 1617 | `campaign-1617` | 5×5 | 8 (N3/E4/S0/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-81` |
| 1618 | `campaign-1618` | 5×5 | 10 (N1/E2/S4/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-81` |
| 1619 | `campaign-1619` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-81` |
| 1620 | `campaign-1620` | 8×8 | 16 (N5/E2/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-81` |
| 1621 | `campaign-1621` | 8×8 | 18 (N0/E6/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-82` |
| 1622 | `campaign-1622` | 5×5 | 7 (N1/E2/S1/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-82` |
| 1623 | `campaign-1623` | 5×5 | 8 (N0/E6/S0/W2) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-82` |
| 1624 | `campaign-1624` | 5×5 | 10 (N3/E0/S3/W4) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-82` |
| 1625 | `campaign-1625` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-82` |
| 1626 | `campaign-1626` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-82` |
| 1627 | `campaign-1627` | 8×8 | 18 (N6/E4/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-82` |
| 1628 | `campaign-1628` | 5×5 | 7 (N1/E1/S1/W4) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-82` |
| 1629 | `campaign-1629` | 6×6 | 10 (N4/E1/S4/W1) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-82` |
| 1630 | `campaign-1630` | 6×6 | 12 (N3/E1/S7/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-82` |
| 1631 | `campaign-1631` | 8×8 | 14 (N2/E4/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-82` |
| 1632 | `campaign-1632` | 8×8 | 16 (N6/E1/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-82` |
| 1633 | `campaign-1633` | 8×8 | 18 (N1/E6/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-82` |
| 1634 | `campaign-1634` | 8×8 | 12 (N4/E5/S1/W2) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 10 | 1+ | `magnetic-circuit-82` |
| 1635 | `campaign-1635` | 6×6 | 10 (N2/E3/S2/W3) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-82` |
| 1636 | `campaign-1636` | 5×5 | 10 (N2/E3/S2/W3) | 5 (Pull2/Push3) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 3 | 1+ | `magnetic-circuit-82` |
| 1637 | `campaign-1637` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-82` |
| 1638 | `campaign-1638` | 8×8 | 16 (N6/E2/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-82` |
| 1639 | `campaign-1639` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-82` |
| 1640 | `campaign-1640` | 4×4 | 6 (N2/E0/S3/W1) | 1 (Pull1/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-82` |
| 1641 | `campaign-1641` | 5×5 | 8 (N2/E4/S1/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-83` |
| 1642 | `campaign-1642` | 5×5 | 10 (N5/E1/S3/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-83` |
| 1643 | `campaign-1643` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-83` |
| 1644 | `campaign-1644` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-83` |
| 1645 | `campaign-1645` | 8×8 | 18 (N0/E6/S1/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-83` |
| 1646 | `campaign-1646` | 5×5 | 7 (N2/E0/S3/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-83` |
| 1647 | `campaign-1647` | 5×5 | 8 (N2/E3/S2/W1) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-83` |
| 1648 | `campaign-1648` | 5×5 | 10 (N2/E5/S2/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-83` |
| 1649 | `campaign-1649` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-83` |
| 1650 | `campaign-1650` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-83` |
| 1651 | `campaign-1651` | 8×8 | 18 (N6/E5/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-83` |
| 1652 | `campaign-1652` | 5×5 | 7 (N2/E1/S2/W2) | 2 (Pull0/Push2) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 1+ | `magnetic-circuit-83` |
| 1653 | `campaign-1653` | 6×6 | 10 (N4/E1/S4/W1) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-83` |
| 1654 | `campaign-1654` | 5×5 | 10 (N5/E3/S1/W1) | 5 (Pull3/Push2) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-83` |
| 1655 | `campaign-1655` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-83` |
| 1656 | `campaign-1656` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-83` |
| 1657 | `campaign-1657` | 8×8 | 18 (N0/E7/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-83` |
| 1658 | `campaign-1658` | 4×4 | 6 (N1/E1/S2/W2) | 2 (Pull1/Push1) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-83` |
| 1659 | `campaign-1659` | 6×6 | 10 (N4/E2/S2/W2) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-83` |
| 1660 | `campaign-1660` | 5×5 | 10 (N4/E3/S0/W3) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-83` |
| 1661 | `campaign-1661` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-84` |
| 1662 | `campaign-1662` | 8×8 | 16 (N6/E3/S5/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-84` |
| 1663 | `campaign-1663` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-84` |
| 1664 | `campaign-1664` | 4×4 | 6 (N5/E1/S0/W0) | 1 (Pull0/Push1) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 2 | 1+ | `magnetic-circuit-84` |
| 1665 | `campaign-1665` | 5×5 | 8 (N3/E2/S1/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-84` |
| 1666 | `campaign-1666` | 5×5 | 10 (N1/E3/S6/W0) | 3 (Pull2/Push1) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-84` |
| 1667 | `campaign-1667` | 8×8 | 14 (N1/E4/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-84` |
| 1668 | `campaign-1668` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-84` |
| 1669 | `campaign-1669` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-84` |
| 1670 | `campaign-1670` | 5×5 | 7 (N1/E2/S1/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-84` |
| 1671 | `campaign-1671` | 8×8 | 14 (N7/E4/S1/W2) | 1 (Pull0/Push1) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 3 | 1+ | `magnetic-circuit-84` |
| 1672 | `campaign-1672` | 5×5 | 10 (N3/E2/S4/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-84` |
| 1673 | `campaign-1673` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-84` |
| 1674 | `campaign-1674` | 8×8 | 16 (N7/E5/S4/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-84` |
| 1675 | `campaign-1675` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-84` |
| 1676 | `campaign-1676` | 8×8 | 12 (N1/E2/S2/W7) | 1 (Pull0/Push1) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 1+ | `magnetic-circuit-84` |
| 1677 | `campaign-1677` | 5×5 | 8 (N3/E0/S1/W4) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-84` |
| 1678 | `campaign-1678` | 5×5 | 10 (N1/E2/S4/W3) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-84` |
| 1679 | `campaign-1679` | 8×8 | 14 (N0/E4/S2/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-84` |
| 1680 | `campaign-1680` | 8×8 | 16 (N5/E1/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-84` |
| 1681 | `campaign-1681` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-85` |
| 1682 | `campaign-1682` | 8×8 | 12 (N0/E2/S8/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-85` |
| 1683 | `campaign-1683` | 5×5 | 8 (N2/E2/S4/W0) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-85` |
| 1684 | `campaign-1684` | 5×5 | 10 (N5/E2/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-85` |
| 1685 | `campaign-1685` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-85` |
| 1686 | `campaign-1686` | 8×8 | 16 (N7/E0/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-85` |
| 1687 | `campaign-1687` | 8×8 | 18 (N0/E4/S8/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-85` |
| 1688 | `campaign-1688` | 5×5 | 7 (N1/E3/S2/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-85` |
| 1689 | `campaign-1689` | 6×6 | 10 (N2/E3/S1/W4) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-85` |
| 1690 | `campaign-1690` | 5×5 | 10 (N2/E2/S4/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-85` |
| 1691 | `campaign-1691` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-85` |
| 1692 | `campaign-1692` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-85` |
| 1693 | `campaign-1693` | 8×8 | 18 (N0/E7/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-85` |
| 1694 | `campaign-1694` | 8×8 | 12 (N0/E2/S0/W10) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-85` |
| 1695 | `campaign-1695` | 6×6 | 10 (N1/E3/S5/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-85` |
| 1696 | `campaign-1696` | 5×5 | 10 (N3/E3/S4/W0) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-85` |
| 1697 | `campaign-1697` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-85` |
| 1698 | `campaign-1698` | 8×8 | 16 (N7/E2/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-85` |
| 1699 | `campaign-1699` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-85` |
| 1700 | `campaign-1700` | 8×8 | 12 (N2/E8/S1/W1) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-85` |
| 1701 | `campaign-1701` | 5×5 | 8 (N5/E1/S0/W2) | 1 (Pull0/Push1) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-86` |
| 1702 | `campaign-1702` | 5×5 | 10 (N1/E1/S7/W1) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-86` |
| 1703 | `campaign-1703` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-86` |
| 1704 | `campaign-1704` | 8×8 | 16 (N6/E2/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-86` |
| 1705 | `campaign-1705` | 8×8 | 18 (N0/E7/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-86` |
| 1706 | `campaign-1706` | 5×5 | 7 (N2/E0/S4/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-86` |
| 1707 | `campaign-1707` | 5×5 | 8 (N1/E2/S3/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-86` |
| 1708 | `campaign-1708` | 7×7 | 14 (N1/E1/S4/W8) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-86` |
| 1709 | `campaign-1709` | 8×8 | 14 (N1/E6/S2/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-86` |
| 1710 | `campaign-1710` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-86` |
| 1711 | `campaign-1711` | 8×8 | 18 (N6/E4/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-86` |
| 1712 | `campaign-1712` | 5×5 | 7 (N0/E1/S3/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-86` |
| 1713 | `campaign-1713` | 5×5 | 8 (N0/E5/S0/W3) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-86` |
| 1714 | `campaign-1714` | 7×7 | 14 (N2/E2/S6/W4) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-86` |
| 1715 | `campaign-1715` | 8×8 | 14 (N2/E4/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-86` |
| 1716 | `campaign-1716` | 8×8 | 16 (N5/E3/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-86` |
| 1717 | `campaign-1717` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-86` |
| 1718 | `campaign-1718` | 4×4 | 6 (N2/E1/S2/W1) | 2 (Pull2/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-86` |
| 1719 | `campaign-1719` | 6×6 | 10 (N2/E1/S3/W4) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-86` |
| 1720 | `campaign-1720` | 5×5 | 10 (N4/E3/S1/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 10 | 1+ | `magnetic-circuit-86` |
| 1721 | `campaign-1721` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-87` |
| 1722 | `campaign-1722` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-87` |
| 1723 | `campaign-1723` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-87` |
| 1724 | `campaign-1724` | 5×5 | 7 (N2/E2/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-87` |
| 1725 | `campaign-1725` | 5×5 | 8 (N1/E4/S0/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-87` |
| 1726 | `campaign-1726` | 5×5 | 10 (N2/E3/S4/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-87` |
| 1727 | `campaign-1727` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-87` |
| 1728 | `campaign-1728` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-87` |
| 1729 | `campaign-1729` | 8×8 | 18 (N1/E6/S1/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-87` |
| 1730 | `campaign-1730` | 5×5 | 7 (N2/E1/S4/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-87` |
| 1731 | `campaign-1731` | 8×8 | 14 (N4/E7/S1/W2) | 4 (Pull4/Push0) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 10 | 1+ | `magnetic-circuit-87` |
| 1732 | `campaign-1732` | 6×6 | 12 (N1/E2/S6/W3) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-87` |
| 1733 | `campaign-1733` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-87` |
| 1734 | `campaign-1734` | 8×8 | 16 (N8/E0/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-87` |
| 1735 | `campaign-1735` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-87` |
| 1736 | `campaign-1736` | 5×5 | 7 (N1/E4/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-87` |
| 1737 | `campaign-1737` | 5×5 | 8 (N3/E3/S1/W1) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 3 | 1+ | `magnetic-circuit-87` |
| 1738 | `campaign-1738` | 7×7 | 14 (N2/E0/S9/W3) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 6 | 1+ | `magnetic-circuit-87` |
| 1739 | `campaign-1739` | 8×8 | 14 (N0/E5/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-87` |
| 1740 | `campaign-1740` | 8×8 | 16 (N5/E3/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-87` |
| 1741 | `campaign-1741` | 8×8 | 18 (N1/E6/S0/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-88` |
| 1742 | `campaign-1742` | 8×8 | 12 (N8/E0/S4/W0) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-88` |
| 1743 | `campaign-1743` | 5×5 | 8 (N4/E3/S1/W0) | 3 (Pull2/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-88` |
| 1744 | `campaign-1744` | 6×6 | 12 (N2/E7/S0/W3) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-88` |
| 1745 | `campaign-1745` | 8×8 | 14 (N3/E7/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-88` |
| 1746 | `campaign-1746` | 8×8 | 16 (N7/E0/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-88` |
| 1747 | `campaign-1747` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-88` |
| 1748 | `campaign-1748` | 5×5 | 7 (N1/E0/S2/W4) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 7 | 1+ | `magnetic-circuit-88` |
| 1749 | `campaign-1749` | 5×5 | 8 (N1/E1/S4/W2) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-88` |
| 1750 | `campaign-1750` | 5×5 | 10 (N3/E2/S5/W0) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 1+ | `magnetic-circuit-88` |
| 1751 | `campaign-1751` | 8×8 | 14 (N0/E5/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-88` |
| 1752 | `campaign-1752` | 8×8 | 16 (N8/E2/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-88` |
| 1753 | `campaign-1753` | 8×8 | 18 (N0/E7/S0/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-88` |
| 1754 | `campaign-1754` | 5×5 | 7 (N1/E3/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-88` |
| 1755 | `campaign-1755` | 5×5 | 8 (N1/E2/S2/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-88` |
| 1756 | `campaign-1756` | 5×5 | 10 (N2/E3/S3/W2) | 4 (Pull1/Push3) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-88` |
| 1757 | `campaign-1757` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-88` |
| 1758 | `campaign-1758` | 8×8 | 16 (N6/E2/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 10 | 1+ | `magnetic-circuit-88` |
| 1759 | `campaign-1759` | 8×8 | 18 (N1/E4/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-88` |
| 1760 | `campaign-1760` | 8×8 | 12 (N1/E4/S4/W3) | 3 (Pull2/Push1) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-88` |
| 1761 | `campaign-1761` | 6×6 | 10 (N2/E1/S5/W2) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-89` |
| 1762 | `campaign-1762` | 7×7 | 14 (N8/E1/S4/W1) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-89` |
| 1763 | `campaign-1763` | 8×8 | 14 (N3/E4/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-89` |
| 1764 | `campaign-1764` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-89` |
| 1765 | `campaign-1765` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-89` |
| 1766 | `campaign-1766` | 5×5 | 7 (N4/E2/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-89` |
| 1767 | `campaign-1767` | 5×5 | 8 (N1/E5/S0/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-89` |
| 1768 | `campaign-1768` | 6×6 | 12 (N6/E3/S2/W1) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 4 | 1+ | `magnetic-circuit-89` |
| 1769 | `campaign-1769` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-89` |
| 1770 | `campaign-1770` | 8×8 | 16 (N7/E2/S5/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-89` |
| 1771 | `campaign-1771` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-89` |
| 1772 | `campaign-1772` | 8×8 | 12 (N2/E1/S7/W2) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-89` |
| 1773 | `campaign-1773` | 5×5 | 8 (N1/E3/S2/W2) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-89` |
| 1774 | `campaign-1774` | 8×8 | 16 (N3/E5/S8/W0) | 2 (Pull2/Push0) | 22 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 16 | 8 | 1+ | `magnetic-circuit-89` |
| 1775 | `campaign-1775` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-89` |
| 1776 | `campaign-1776` | 8×8 | 16 (N5/E4/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-89` |
| 1777 | `campaign-1777` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-89` |
| 1778 | `campaign-1778` | 5×5 | 7 (N3/E2/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-89` |
| 1779 | `campaign-1779` | 5×5 | 8 (N0/E4/S2/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-89` |
| 1780 | `campaign-1780` | 6×6 | 12 (N8/E1/S1/W2) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-89` |
| 1781 | `campaign-1781` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-90` |
| 1782 | `campaign-1782` | 8×8 | 16 (N8/E0/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-90` |
| 1783 | `campaign-1783` | 8×8 | 18 (N7/E4/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-90` |
| 1784 | `campaign-1784` | 5×5 | 7 (N2/E2/S3/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-90` |
| 1785 | `campaign-1785` | 8×8 | 14 (N0/E7/S5/W2) | 4 (Pull4/Push0) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 10 | 1+ | `magnetic-circuit-90` |
| 1786 | `campaign-1786` | 7×7 | 14 (N2/E0/S10/W2) | 3 (Pull2/Push1) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-90` |
| 1787 | `campaign-1787` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-90` |
| 1788 | `campaign-1788` | 8×8 | 16 (N4/E4/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-90` |
| 1789 | `campaign-1789` | 8×8 | 18 (N0/E6/S1/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-90` |
| 1790 | `campaign-1790` | 5×5 | 7 (N2/E0/S2/W3) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-90` |
| 1791 | `campaign-1791` | 5×5 | 8 (N2/E2/S2/W2) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-90` |
| 1792 | `campaign-1792` | 5×5 | 10 (N3/E4/S3/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-90` |
| 1793 | `campaign-1793` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-90` |
| 1794 | `campaign-1794` | 8×8 | 16 (N8/E2/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-90` |
| 1795 | `campaign-1795` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-90` |
| 1796 | `campaign-1796` | 4×4 | 6 (N2/E4/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-90` |
| 1797 | `campaign-1797` | 6×6 | 10 (N2/E0/S5/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-90` |
| 1798 | `campaign-1798` | 6×6 | 12 (N8/E1/S2/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-90` |
| 1799 | `campaign-1799` | 8×8 | 14 (N0/E4/S4/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-90` |
| 1800 | `campaign-1800` | 8×8 | 16 (N5/E2/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-90` |
| 1801 | `campaign-1801` | 8×8 | 18 (N0/E7/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-91` |
| 1802 | `campaign-1802` | 8×8 | 12 (N7/E3/S0/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-91` |
| 1803 | `campaign-1803` | 8×8 | 14 (N3/E8/S2/W1) | 4 (Pull4/Push0) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-91` |
| 1804 | `campaign-1804` | 7×7 | 14 (N1/E3/S3/W7) | 4 (Pull3/Push1) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-91` |
| 1805 | `campaign-1805` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-91` |
| 1806 | `campaign-1806` | 8×8 | 16 (N6/E1/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-91` |
| 1807 | `campaign-1807` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-91` |
| 1808 | `campaign-1808` | 8×8 | 12 (N2/E1/S2/W7) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-91` |
| 1809 | `campaign-1809` | 5×5 | 8 (N0/E1/S1/W6) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-91` |
| 1810 | `campaign-1810` | 6×6 | 12 (N4/E3/S4/W1) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-91` |
| 1811 | `campaign-1811` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-91` |
| 1812 | `campaign-1812` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-91` |
| 1813 | `campaign-1813` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-91` |
| 1814 | `campaign-1814` | 4×4 | 6 (N1/E2/S3/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-91` |
| 1815 | `campaign-1815` | 5×5 | 8 (N4/E2/S2/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-91` |
| 1816 | `campaign-1816` | 5×5 | 10 (N3/E2/S0/W5) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-91` |
| 1817 | `campaign-1817` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-91` |
| 1818 | `campaign-1818` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-91` |
| 1819 | `campaign-1819` | 8×8 | 18 (N8/E4/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-91` |
| 1820 | `campaign-1820` | 4×4 | 6 (N3/E1/S0/W2) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-91` |
| 1821 | `campaign-1821` | 6×6 | 10 (N3/E1/S4/W2) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-92` |
| 1822 | `campaign-1822` | 5×5 | 10 (N3/E3/S1/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-92` |
| 1823 | `campaign-1823` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-92` |
| 1824 | `campaign-1824` | 8×8 | 16 (N9/E1/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-92` |
| 1825 | `campaign-1825` | 8×8 | 18 (N7/E6/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-92` |
| 1826 | `campaign-1826` | 8×8 | 12 (N2/E0/S9/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 5 | 1+ | `magnetic-circuit-92` |
| 1827 | `campaign-1827` | 5×5 | 8 (N1/E4/S1/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-92` |
| 1828 | `campaign-1828` | 5×5 | 10 (N3/E2/S4/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-92` |
| 1829 | `campaign-1829` | 8×8 | 14 (N0/E7/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-92` |
| 1830 | `campaign-1830` | 8×8 | 16 (N8/E3/S5/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-92` |
| 1831 | `campaign-1831` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-92` |
| 1832 | `campaign-1832` | 5×5 | 7 (N3/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-92` |
| 1833 | `campaign-1833` | 6×6 | 10 (N2/E0/S5/W3) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-92` |
| 1834 | `campaign-1834` | 5×5 | 10 (N3/E2/S0/W5) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-92` |
| 1835 | `campaign-1835` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-92` |
| 1836 | `campaign-1836` | 8×8 | 16 (N6/E4/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-92` |
| 1837 | `campaign-1837` | 8×8 | 18 (N7/E7/S0/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-92` |
| 1838 | `campaign-1838` | 5×5 | 7 (N1/E2/S1/W3) | 1 (Pull0/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 3 | 1+ | `magnetic-circuit-92` |
| 1839 | `campaign-1839` | 5×5 | 8 (N2/E4/S0/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-92` |
| 1840 | `campaign-1840` | 5×5 | 10 (N3/E1/S3/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-92` |
| 1841 | `campaign-1841` | 8×8 | 14 (N2/E6/S1/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-93` |
| 1842 | `campaign-1842` | 8×8 | 16 (N7/E0/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-93` |
| 1843 | `campaign-1843` | 8×8 | 18 (N0/E5/S6/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-93` |
| 1844 | `campaign-1844` | 8×8 | 12 (N2/E7/S3/W0) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-93` |
| 1845 | `campaign-1845` | 5×5 | 8 (N1/E0/S3/W4) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-93` |
| 1846 | `campaign-1846` | 5×5 | 10 (N0/E1/S6/W3) | 5 (Pull4/Push1) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-93` |
| 1847 | `campaign-1847` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-93` |
| 1848 | `campaign-1848` | 8×8 | 16 (N7/E1/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-93` |
| 1849 | `campaign-1849` | 8×8 | 18 (N6/E7/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-93` |
| 1850 | `campaign-1850` | 8×8 | 12 (N2/E2/S3/W5) | 3 (Pull3/Push0) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-93` |
| 1851 | `campaign-1851` | 5×5 | 8 (N2/E0/S3/W3) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-93` |
| 1852 | `campaign-1852` | 5×5 | 10 (N2/E3/S3/W2) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-93` |
| 1853 | `campaign-1853` | 8×8 | 14 (N0/E8/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-93` |
| 1854 | `campaign-1854` | 8×8 | 16 (N7/E3/S5/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-93` |
| 1855 | `campaign-1855` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-93` |
| 1856 | `campaign-1856` | 5×5 | 7 (N2/E1/S3/W1) | 2 (Pull1/Push1) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-93` |
| 1857 | `campaign-1857` | 6×6 | 10 (N3/E0/S2/W5) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-93` |
| 1858 | `campaign-1858` | 6×6 | 12 (N7/E0/S4/W1) | 4 (Pull2/Push2) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-93` |
| 1859 | `campaign-1859` | 8×8 | 14 (N6/E1/S6/W1) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-93` |
| 1860 | `campaign-1860` | 8×8 | 16 (N5/E2/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-93` |
| 1861 | `campaign-1861` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-94` |
| 1862 | `campaign-1862` | 8×8 | 12 (N3/E6/S2/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-94` |
| 1863 | `campaign-1863` | 5×5 | 8 (N3/E3/S0/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-94` |
| 1864 | `campaign-1864` | 5×5 | 10 (N1/E4/S3/W2) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 9 | 1+ | `magnetic-circuit-94` |
| 1865 | `campaign-1865` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-94` |
| 1866 | `campaign-1866` | 8×8 | 16 (N7/E2/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-94` |
| 1867 | `campaign-1867` | 8×8 | 18 (N6/E4/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-94` |
| 1868 | `campaign-1868` | 5×5 | 7 (N5/E2/S0/W0) | 1 (Pull0/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-94` |
| 1869 | `campaign-1869` | 8×8 | 14 (N7/E1/S2/W4) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-94` |
| 1870 | `campaign-1870` | 5×5 | 10 (N0/E2/S7/W1) | 4 (Pull2/Push2) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-94` |
| 1871 | `campaign-1871` | 8×8 | 14 (N0/E5/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-94` |
| 1872 | `campaign-1872` | 8×8 | 16 (N5/E1/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-94` |
| 1873 | `campaign-1873` | 8×8 | 18 (N6/E7/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-94` |
| 1874 | `campaign-1874` | 5×5 | 7 (N1/E5/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-94` |
| 1875 | `campaign-1875` | 5×5 | 8 (N0/E1/S1/W6) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-94` |
| 1876 | `campaign-1876` | 7×7 | 14 (N2/E1/S8/W3) | 4 (Pull4/Push0) | 13 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-94` |
| 1877 | `campaign-1877` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-94` |
| 1878 | `campaign-1878` | 8×8 | 16 (N6/E1/S5/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-94` |
| 1879 | `campaign-1879` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-94` |
| 1880 | `campaign-1880` | 8×8 | 12 (N3/E2/S2/W5) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 10 | 1+ | `magnetic-circuit-94` |
| 1881 | `campaign-1881` | 6×6 | 10 (N6/E1/S0/W3) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-95` |
| 1882 | `campaign-1882` | 6×6 | 12 (N3/E2/S1/W6) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-95` |
| 1883 | `campaign-1883` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-95` |
| 1884 | `campaign-1884` | 8×8 | 16 (N10/E0/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-95` |
| 1885 | `campaign-1885` | 8×8 | 18 (N0/E6/S1/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-95` |
| 1886 | `campaign-1886` | 8×8 | 12 (N4/E1/S2/W5) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-95` |
| 1887 | `campaign-1887` | 5×5 | 8 (N5/E2/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-95` |
| 1888 | `campaign-1888` | 7×7 | 14 (N3/E1/S8/W2) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-95` |
| 1889 | `campaign-1889` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-95` |
| 1890 | `campaign-1890` | 8×8 | 16 (N2/E6/S3/W5) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-95` |
| 1891 | `campaign-1891` | 8×8 | 18 (N1/E10/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-95` |
| 1892 | `campaign-1892` | 8×8 | 12 (N4/E4/S1/W3) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 9 | 1+ | `magnetic-circuit-95` |
| 1893 | `campaign-1893` | 6×6 | 10 (N3/E3/S3/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-95` |
| 1894 | `campaign-1894` | 5×5 | 10 (N4/E5/S1/W0) | 3 (Pull3/Push0) | 5 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-95` |
| 1895 | `campaign-1895` | 8×8 | 14 (N1/E4/S2/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-95` |
| 1896 | `campaign-1896` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-95` |
| 1897 | `campaign-1897` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-95` |
| 1898 | `campaign-1898` | 8×8 | 12 (N4/E3/S3/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-95` |
| 1899 | `campaign-1899` | 5×5 | 8 (N1/E1/S2/W4) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-95` |
| 1900 | `campaign-1900` | 7×7 | 14 (N2/E9/S1/W2) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-95` |
| 1901 | `campaign-1901` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-96` |
| 1902 | `campaign-1902` | 8×8 | 16 (N7/E0/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-96` |
| 1903 | `campaign-1903` | 8×8 | 18 (N1/E11/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-96` |
| 1904 | `campaign-1904` | 5×5 | 7 (N1/E3/S3/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-96` |
| 1905 | `campaign-1905` | 6×6 | 10 (N8/E1/S1/W0) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-96` |
| 1906 | `campaign-1906` | 5×5 | 10 (N6/E2/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-96` |
| 1907 | `campaign-1907` | 8×8 | 14 (N0/E5/S3/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-96` |
| 1908 | `campaign-1908` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-96` |
| 1909 | `campaign-1909` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-96` |
| 1910 | `campaign-1910` | 4×4 | 6 (N0/E5/S0/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-96` |
| 1911 | `campaign-1911` | 8×8 | 14 (N2/E2/S2/W8) | 1 (Pull1/Push0) | 21 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-96` |
| 1912 | `campaign-1912` | 5×5 | 10 (N2/E4/S3/W1) | 2 (Pull2/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-96` |
| 1913 | `campaign-1913` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-96` |
| 1914 | `campaign-1914` | 8×8 | 16 (N6/E0/S10/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-96` |
| 1915 | `campaign-1915` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-96` |
| 1916 | `campaign-1916` | 8×8 | 12 (N1/E5/S0/W6) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-96` |
| 1917 | `campaign-1917` | 5×5 | 8 (N0/E4/S1/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-96` |
| 1918 | `campaign-1918` | 5×5 | 10 (N2/E1/S4/W3) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-96` |
| 1919 | `campaign-1919` | 8×8 | 14 (N3/E4/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-96` |
| 1920 | `campaign-1920` | 8×8 | 16 (N5/E1/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-96` |
| 1921 | `campaign-1921` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-97` |
| 1922 | `campaign-1922` | 5×5 | 7 (N2/E0/S4/W1) | 1 (Pull0/Push1) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-97` |
| 1923 | `campaign-1923` | 5×5 | 8 (N2/E1/S2/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-97` |
| 1924 | `campaign-1924` | 5×5 | 10 (N2/E1/S4/W3) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-97` |
| 1925 | `campaign-1925` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-97` |
| 1926 | `campaign-1926` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-97` |
| 1927 | `campaign-1927` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-97` |
| 1928 | `campaign-1928` | 4×4 | 6 (N2/E0/S1/W3) | 3 (Pull1/Push2) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-97` |
| 1929 | `campaign-1929` | 6×6 | 10 (N2/E2/S4/W2) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-97` |
| 1930 | `campaign-1930` | 5×5 | 10 (N4/E3/S2/W1) | 3 (Pull1/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-97` |
| 1931 | `campaign-1931` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-97` |
| 1932 | `campaign-1932` | 8×8 | 16 (N8/E0/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-97` |
| 1933 | `campaign-1933` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-97` |
| 1934 | `campaign-1934` | 5×5 | 7 (N0/E1/S3/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-97` |
| 1935 | `campaign-1935` | 5×5 | 8 (N2/E2/S3/W1) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-97` |
| 1936 | `campaign-1936` | 6×6 | 12 (N1/E2/S9/W0) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 1+ | `magnetic-circuit-97` |
| 1937 | `campaign-1937` | 8×8 | 14 (N3/E6/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-97` |
| 1938 | `campaign-1938` | 8×8 | 16 (N7/E3/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-97` |
| 1939 | `campaign-1939` | 8×8 | 18 (N7/E1/S4/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-97` |
| 1940 | `campaign-1940` | 4×4 | 6 (N3/E1/S0/W2) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-97` |
| 1941 | `campaign-1941` | 8×8 | 14 (N10/E1/S2/W1) | 4 (Pull4/Push0) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 9 | 1+ | `magnetic-circuit-98` |
| 1942 | `campaign-1942` | 6×6 | 12 (N3/E5/S3/W1) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-98` |
| 1943 | `campaign-1943` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-98` |
| 1944 | `campaign-1944` | 8×8 | 16 (N5/E3/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-98` |
| 1945 | `campaign-1945` | 8×8 | 18 (N0/E7/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-98` |
| 1946 | `campaign-1946` | 8×8 | 12 (N4/E1/S7/W0) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-98` |
| 1947 | `campaign-1947` | 5×5 | 8 (N3/E2/S1/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-98` |
| 1948 | `campaign-1948` | 5×5 | 10 (N3/E1/S5/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-98` |
| 1949 | `campaign-1949` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-98` |
| 1950 | `campaign-1950` | 8×8 | 16 (N8/E1/S4/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-98` |
| 1951 | `campaign-1951` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-98` |
| 1952 | `campaign-1952` | 5×5 | 7 (N2/E4/S1/W0) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-98` |
| 1953 | `campaign-1953` | 8×8 | 14 (N2/E1/S2/W9) | 4 (Pull2/Push2) | 18 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-98` |
| 1954 | `campaign-1954` | 6×6 | 12 (N4/E1/S0/W7) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 6 | 1+ | `magnetic-circuit-98` |
| 1955 | `campaign-1955` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-98` |
| 1956 | `campaign-1956` | 8×8 | 16 (N6/E0/S7/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-98` |
| 1957 | `campaign-1957` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-98` |
| 1958 | `campaign-1958` | 4×4 | 6 (N2/E2/S2/W0) | 3 (Pull1/Push2) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-98` |
| 1959 | `campaign-1959` | 5×5 | 8 (N1/E3/S3/W1) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-98` |
| 1960 | `campaign-1960` | 6×6 | 12 (N4/E2/S0/W6) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 4 | 1+ | `magnetic-circuit-98` |
| 1961 | `campaign-1961` | 8×8 | 14 (N7/E0/S7/W0) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-99` |
| 1962 | `campaign-1962` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-99` |
| 1963 | `campaign-1963` | 8×8 | 18 (N1/E4/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-99` |
| 1964 | `campaign-1964` | 5×5 | 7 (N1/E4/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-99` |
| 1965 | `campaign-1965` | 8×8 | 14 (N0/E10/S0/W4) | 2 (Pull1/Push1) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-99` |
| 1966 | `campaign-1966` | 7×7 | 14 (N10/E2/S0/W2) | 2 (Pull1/Push1) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 1+ | `magnetic-circuit-99` |
| 1967 | `campaign-1967` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-99` |
| 1968 | `campaign-1968` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-99` |
| 1969 | `campaign-1969` | 8×8 | 18 (N7/E6/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-99` |
| 1970 | `campaign-1970` | 5×5 | 7 (N2/E4/S0/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-99` |
| 1971 | `campaign-1971` | 6×6 | 10 (N3/E4/S2/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-99` |
| 1972 | `campaign-1972` | 5×5 | 10 (N4/E3/S3/W0) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-99` |
| 1973 | `campaign-1973` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-99` |
| 1974 | `campaign-1974` | 8×8 | 16 (N7/E2/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-99` |
| 1975 | `campaign-1975` | 8×8 | 18 (N6/E5/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-99` |
| 1976 | `campaign-1976` | 5×5 | 7 (N1/E4/S1/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-99` |
| 1977 | `campaign-1977` | 6×6 | 10 (N3/E1/S4/W2) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-99` |
| 1978 | `campaign-1978` | 7×7 | 14 (N4/E3/S1/W6) | 3 (Pull1/Push2) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-99` |
| 1979 | `campaign-1979` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-99` |
| 1980 | `campaign-1980` | 8×8 | 16 (N4/E3/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-99` |
| 1981 | `campaign-1981` | 8×8 | 18 (N1/E6/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-100` |
| 1982 | `campaign-1982` | 5×5 | 7 (N2/E1/S2/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-100` |
| 1983 | `campaign-1983` | 5×5 | 8 (N0/E3/S3/W2) | 3 (Pull2/Push1) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-100` |
| 1984 | `campaign-1984` | 7×7 | 14 (N1/E8/S2/W3) | 2 (Pull1/Push1) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-100` |
| 1985 | `campaign-1985` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-100` |
| 1986 | `campaign-1986` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-100` |
| 1987 | `campaign-1987` | 8×8 | 18 (N0/E5/S7/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-100` |
| 1988 | `campaign-1988` | 4×4 | 6 (N2/E1/S1/W2) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-100` |
| 1989 | `campaign-1989` | 6×6 | 10 (N1/E3/S3/W3) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 4 | 1+ | `magnetic-circuit-100` |
| 1990 | `campaign-1990` | 5×5 | 10 (N5/E1/S2/W2) | 3 (Pull1/Push2) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 4 | 1+ | `magnetic-circuit-100` |
| 1991 | `campaign-1991` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-100` |
| 1992 | `campaign-1992` | 8×8 | 16 (N5/E0/S8/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-100` |
| 1993 | `campaign-1993` | 8×8 | 18 (N6/E7/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-100` |
| 1994 | `campaign-1994` | 5×5 | 7 (N2/E3/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-100` |
| 1995 | `campaign-1995` | 5×5 | 8 (N3/E2/S1/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-100` |
| 1996 | `campaign-1996` | 5×5 | 10 (N5/E4/S1/W0) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-100` |
| 1997 | `campaign-1997` | 8×8 | 14 (N0/E6/S4/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-100` |
| 1998 | `campaign-1998` | 8×8 | 16 (N8/E0/S4/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-100` |
| 1999 | `campaign-1999` | 8×8 | 18 (N1/E10/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-100` |
| 2000 | `campaign-2000` | 8×8 | 12 (N4/E3/S2/W3) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-100` |
| 2001 | `campaign-2001` | 5×5 | 8 (N7/E0/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-101` |
| 2002 | `campaign-2002` | 5×5 | 10 (N4/E1/S4/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-101` |
| 2003 | `campaign-2003` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-101` |
| 2004 | `campaign-2004` | 8×8 | 16 (N7/E2/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-101` |
| 2005 | `campaign-2005` | 8×8 | 18 (N1/E6/S0/W11) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-101` |
| 2006 | `campaign-2006` | 5×5 | 7 (N1/E2/S3/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-101` |
| 2007 | `campaign-2007` | 5×5 | 8 (N1/E4/S2/W1) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-101` |
| 2008 | `campaign-2008` | 5×5 | 10 (N3/E3/S3/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-101` |
| 2009 | `campaign-2009` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-101` |
| 2010 | `campaign-2010` | 8×8 | 16 (N6/E4/S6/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-101` |
| 2011 | `campaign-2011` | 8×8 | 18 (N6/E5/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-101` |
| 2012 | `campaign-2012` | 8×8 | 12 (N1/E2/S8/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-101` |
| 2013 | `campaign-2013` | 8×8 | 14 (N1/E5/S1/W7) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 8 | 1+ | `magnetic-circuit-101` |
| 2014 | `campaign-2014` | 5×5 | 10 (N0/E5/S5/W0) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-101` |
| 2015 | `campaign-2015` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-101` |
| 2016 | `campaign-2016` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-101` |
| 2017 | `campaign-2017` | 8×8 | 18 (N0/E6/S2/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-101` |
| 2018 | `campaign-2018` | 4×4 | 6 (N0/E1/S5/W0) | 3 (Pull3/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 6 | 1+ | `magnetic-circuit-101` |
| 2019 | `campaign-2019` | 5×5 | 8 (N3/E2/S1/W2) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-101` |
| 2020 | `campaign-2020` | 7×7 | 14 (N1/E8/S3/W2) | 5 (Pull4/Push1) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 9 | 1+ | `magnetic-circuit-101` |
| 2021 | `campaign-2021` | 8×8 | 14 (N2/E6/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-102` |
| 2022 | `campaign-2022` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-102` |
| 2023 | `campaign-2023` | 8×8 | 18 (N0/E10/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-102` |
| 2024 | `campaign-2024` | 5×5 | 7 (N1/E4/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-102` |
| 2025 | `campaign-2025` | 5×5 | 8 (N3/E1/S3/W1) | 1 (Pull1/Push0) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-102` |
| 2026 | `campaign-2026` | 6×6 | 12 (N5/E1/S3/W3) | 5 (Pull4/Push1) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-102` |
| 2027 | `campaign-2027` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-102` |
| 2028 | `campaign-2028` | 8×8 | 16 (N6/E1/S7/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-102` |
| 2029 | `campaign-2029` | 8×8 | 18 (N6/E6/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-102` |
| 2030 | `campaign-2030` | 4×4 | 6 (N3/E2/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-102` |
| 2031 | `campaign-2031` | 5×5 | 8 (N0/E3/S2/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-102` |
| 2032 | `campaign-2032` | 5×5 | 10 (N5/E3/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-102` |
| 2033 | `campaign-2033` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-102` |
| 2034 | `campaign-2034` | 8×8 | 16 (N8/E1/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-102` |
| 2035 | `campaign-2035` | 8×8 | 18 (N0/E10/S1/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-102` |
| 2036 | `campaign-2036` | 4×4 | 6 (N1/E3/S1/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-102` |
| 2037 | `campaign-2037` | 6×6 | 10 (N4/E4/S2/W0) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-102` |
| 2038 | `campaign-2038` | 7×7 | 14 (N1/E2/S9/W2) | 5 (Pull3/Push2) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-102` |
| 2039 | `campaign-2039` | 8×8 | 14 (N1/E6/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-102` |
| 2040 | `campaign-2040` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-102` |
| 2041 | `campaign-2041` | 8×8 | 18 (N1/E6/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-103` |
| 2042 | `campaign-2042` | 4×4 | 6 (N0/E3/S3/W0) | 3 (Pull2/Push1) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 6 | 1+ | `magnetic-circuit-103` |
| 2043 | `campaign-2043` | 5×5 | 8 (N3/E3/S0/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-103` |
| 2044 | `campaign-2044` | 6×6 | 12 (N9/E1/S0/W2) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-103` |
| 2045 | `campaign-2045` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-103` |
| 2046 | `campaign-2046` | 8×8 | 16 (N6/E0/S8/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-103` |
| 2047 | `campaign-2047` | 8×8 | 18 (N0/E4/S7/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-103` |
| 2048 | `campaign-2048` | 4×4 | 6 (N0/E4/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-103` |
| 2049 | `campaign-2049` | 5×5 | 8 (N1/E3/S3/W1) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-103` |
| 2050 | `campaign-2050` | 6×6 | 12 (N4/E4/S2/W2) | 5 (Pull3/Push2) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 10 | 1+ | `magnetic-circuit-103` |
| 2051 | `campaign-2051` | 8×8 | 14 (N0/E7/S1/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-103` |
| 2052 | `campaign-2052` | 8×8 | 16 (N6/E2/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-103` |
| 2053 | `campaign-2053` | 8×8 | 18 (N11/E0/S7/W0) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-103` |
| 2054 | `campaign-2054` | 8×8 | 12 (N3/E2/S1/W6) | 2 (Pull2/Push0) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-103` |
| 2055 | `campaign-2055` | 5×5 | 8 (N1/E1/S2/W4) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-103` |
| 2056 | `campaign-2056` | 6×6 | 12 (N1/E3/S4/W4) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 9 | 1+ | `magnetic-circuit-103` |
| 2057 | `campaign-2057` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-103` |
| 2058 | `campaign-2058` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-103` |
| 2059 | `campaign-2059` | 8×8 | 18 (N1/E5/S6/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 13 | 1+ | `magnetic-circuit-103` |
| 2060 | `campaign-2060` | 5×5 | 7 (N3/E0/S3/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-103` |
| 2061 | `campaign-2061` | 8×8 | 14 (N3/E3/S8/W0) | 2 (Pull1/Push1) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 5 | 1+ | `magnetic-circuit-104` |
| 2062 | `campaign-2062` | 6×6 | 12 (N7/E2/S2/W1) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 10 | 1+ | `magnetic-circuit-104` |
| 2063 | `campaign-2063` | 8×8 | 14 (N6/E1/S4/W3) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-104` |
| 2064 | `campaign-2064` | 8×8 | 16 (N4/E0/S7/W5) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-104` |
| 2065 | `campaign-2065` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-104` |
| 2066 | `campaign-2066` | 5×5 | 7 (N0/E2/S2/W3) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-104` |
| 2067 | `campaign-2067` | 5×5 | 8 (N1/E2/S2/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 8 | 1+ | `magnetic-circuit-104` |
| 2068 | `campaign-2068` | 6×6 | 12 (N3/E2/S3/W4) | 4 (Pull4/Push0) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 8 | 1+ | `magnetic-circuit-104` |
| 2069 | `campaign-2069` | 8×8 | 14 (N2/E7/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-104` |
| 2070 | `campaign-2070` | 8×8 | 16 (N9/E0/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-104` |
| 2071 | `campaign-2071` | 8×8 | 18 (N0/E10/S2/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-104` |
| 2072 | `campaign-2072` | 4×4 | 6 (N2/E0/S4/W0) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-104` |
| 2073 | `campaign-2073` | 5×5 | 8 (N0/E3/S2/W3) | 4 (Pull4/Push0) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-104` |
| 2074 | `campaign-2074` | 5×5 | 10 (N4/E4/S0/W2) | 4 (Pull3/Push1) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-104` |
| 2075 | `campaign-2075` | 8×8 | 14 (N2/E5/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-104` |
| 2076 | `campaign-2076` | 8×8 | 16 (N8/E1/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-104` |
| 2077 | `campaign-2077` | 8×8 | 18 (N1/E6/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-104` |
| 2078 | `campaign-2078` | 4×4 | 6 (N4/E1/S1/W0) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 4 | 1+ | `magnetic-circuit-104` |
| 2079 | `campaign-2079` | 5×5 | 8 (N1/E4/S1/W2) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-104` |
| 2080 | `campaign-2080` | 6×6 | 12 (N1/E4/S0/W7) | 5 (Pull5/Push0) | 6 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-104` |
| 2081 | `campaign-2081` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-105` |
| 2082 | `campaign-2082` | 8×8 | 16 (N6/E0/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-105` |
| 2083 | `campaign-2083` | 8×8 | 18 (N1/E10/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-105` |
| 2084 | `campaign-2084` | 8×8 | 12 (N8/E0/S2/W2) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-105` |
| 2085 | `campaign-2085` | 8×8 | 14 (N9/E3/S1/W1) | 2 (Pull2/Push0) | 20 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 14 | 7 | 1+ | `magnetic-circuit-105` |
| 2086 | `campaign-2086` | 5×5 | 10 (N2/E2/S1/W5) | 4 (Pull3/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-105` |
| 2087 | `campaign-2087` | 8×8 | 14 (N0/E6/S0/W8) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-105` |
| 2088 | `campaign-2088` | 8×8 | 16 (N5/E4/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-105` |
| 2089 | `campaign-2089` | 8×8 | 18 (N0/E6/S7/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-105` |
| 2090 | `campaign-2090` | 8×8 | 12 (N4/E2/S5/W1) | 2 (Pull1/Push1) | 18 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-105` |
| 2091 | `campaign-2091` | 6×6 | 10 (N2/E5/S2/W1) | 3 (Pull1/Push2) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-105` |
| 2092 | `campaign-2092` | 5×5 | 10 (N6/E1/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-105` |
| 2093 | `campaign-2093` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-105` |
| 2094 | `campaign-2094` | 8×8 | 16 (N7/E3/S4/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-105` |
| 2095 | `campaign-2095` | 8×8 | 18 (N7/E4/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-105` |
| 2096 | `campaign-2096` | 8×8 | 12 (N3/E1/S7/W1) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 7 | 1+ | `magnetic-circuit-105` |
| 2097 | `campaign-2097` | 6×6 | 10 (N3/E3/S0/W4) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-105` |
| 2098 | `campaign-2098` | 6×6 | 12 (N3/E1/S1/W7) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 1+ | `magnetic-circuit-105` |
| 2099 | `campaign-2099` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-105` |
| 2100 | `campaign-2100` | 8×8 | 16 (N5/E3/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-105` |
| 2101 | `campaign-2101` | 8×8 | 18 (N0/E7/S6/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-106` |
| 2102 | `campaign-2102` | 8×8 | 12 (N2/E7/S1/W2) | 1 (Pull1/Push0) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 6 | 1+ | `magnetic-circuit-106` |
| 2103 | `campaign-2103` | 5×5 | 8 (N1/E1/S3/W3) | 4 (Pull3/Push1) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-106` |
| 2104 | `campaign-2104` | 5×5 | 10 (N6/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-106` |
| 2105 | `campaign-2105` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-106` |
| 2106 | `campaign-2106` | 8×8 | 16 (N6/E2/S7/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-106` |
| 2107 | `campaign-2107` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 9 | 1+ | `magnetic-circuit-106` |
| 2108 | `campaign-2108` | 4×4 | 6 (N2/E2/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-106` |
| 2109 | `campaign-2109` | 5×5 | 8 (N1/E4/S2/W1) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-106` |
| 2110 | `campaign-2110` | 5×5 | 10 (N1/E2/S2/W5) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-106` |
| 2111 | `campaign-2111` | 8×8 | 14 (N6/E1/S5/W2) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-106` |
| 2112 | `campaign-2112` | 8×8 | 16 (N4/E2/S6/W4) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-106` |
| 2113 | `campaign-2113` | 8×8 | 18 (N6/E6/S2/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-106` |
| 2114 | `campaign-2114` | 5×5 | 7 (N3/E1/S3/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-106` |
| 2115 | `campaign-2115` | 6×6 | 10 (N4/E2/S3/W1) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 5 | 1+ | `magnetic-circuit-106` |
| 2116 | `campaign-2116` | 6×6 | 12 (N7/E3/S1/W1) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 5 | 1+ | `magnetic-circuit-106` |
| 2117 | `campaign-2117` | 8×8 | 14 (N1/E6/S3/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-106` |
| 2118 | `campaign-2118` | 8×8 | 16 (N7/E3/S4/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-106` |
| 2119 | `campaign-2119` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-106` |
| 2120 | `campaign-2120` | 4×4 | 6 (N2/E1/S2/W1) | 1 (Pull0/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-106` |
| 2121 | `campaign-2121` | 6×6 | 10 (N4/E3/S1/W2) | 2 (Pull2/Push0) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-107` |
| 2122 | `campaign-2122` | 5×5 | 10 (N4/E2/S3/W1) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 7 | 1+ | `magnetic-circuit-107` |
| 2123 | `campaign-2123` | 8×8 | 14 (N1/E6/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-107` |
| 2124 | `campaign-2124` | 8×8 | 16 (N7/E1/S8/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-107` |
| 2125 | `campaign-2125` | 8×8 | 18 (N6/E7/S1/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-107` |
| 2126 | `campaign-2126` | 8×8 | 12 (N2/E2/S4/W4) | 3 (Pull1/Push2) | 17 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 8 | 1+ | `magnetic-circuit-107` |
| 2127 | `campaign-2127` | 6×6 | 10 (N2/E5/S3/W0) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 9 | 1+ | `magnetic-circuit-107` |
| 2128 | `campaign-2128` | 5×5 | 10 (N4/E0/S5/W1) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 6 | 1+ | `magnetic-circuit-107` |
| 2129 | `campaign-2129` | 8×8 | 14 (N1/E7/S2/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-107` |
| 2130 | `campaign-2130` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-107` |
| 2131 | `campaign-2131` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-107` |
| 2132 | `campaign-2132` | 5×5 | 7 (N2/E2/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-107` |
| 2133 | `campaign-2133` | 5×5 | 8 (N3/E3/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-107` |
| 2134 | `campaign-2134` | 7×7 | 14 (N4/E7/S0/W3) | 2 (Pull1/Push1) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 7 | 1+ | `magnetic-circuit-107` |
| 2135 | `campaign-2135` | 8×8 | 14 (N0/E7/S0/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-107` |
| 2136 | `campaign-2136` | 8×8 | 16 (N5/E3/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-107` |
| 2137 | `campaign-2137` | 8×8 | 18 (N0/E7/S7/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-107` |
| 2138 | `campaign-2138` | 4×4 | 6 (N2/E1/S0/W3) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 3 | 1+ | `magnetic-circuit-107` |
| 2139 | `campaign-2139` | 5×5 | 8 (N4/E3/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-107` |
| 2140 | `campaign-2140` | 7×7 | 14 (N4/E6/S3/W1) | 3 (Pull3/Push0) | 14 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 8 | 1+ | `magnetic-circuit-107` |
| 2141 | `campaign-2141` | 8×8 | 14 (N0/E6/S3/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-108` |
| 2142 | `campaign-2142` | 8×8 | 16 (N7/E4/S5/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-108` |
| 2143 | `campaign-2143` | 8×8 | 18 (N0/E11/S1/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-108` |
| 2144 | `campaign-2144` | 4×4 | 6 (N1/E0/S4/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-108` |
| 2145 | `campaign-2145` | 5×5 | 8 (N2/E1/S2/W3) | 2 (Pull2/Push0) | 5 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-108` |
| 2146 | `campaign-2146` | 5×5 | 10 (N3/E4/S1/W2) | 4 (Pull3/Push1) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-108` |
| 2147 | `campaign-2147` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-108` |
| 2148 | `campaign-2148` | 8×8 | 16 (N6/E3/S7/W0) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-108` |
| 2149 | `campaign-2149` | 8×8 | 18 (N2/E6/S0/W10) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-108` |
| 2150 | `campaign-2150` | 5×5 | 7 (N2/E1/S2/W2) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 7 | 1+ | `magnetic-circuit-108` |
| 2151 | `campaign-2151` | 5×5 | 8 (N2/E2/S2/W2) | 1 (Pull1/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-108` |
| 2152 | `campaign-2152` | 6×6 | 12 (N4/E3/S1/W4) | 3 (Pull3/Push0) | 8 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 12 | 7 | 1+ | `magnetic-circuit-108` |
| 2153 | `campaign-2153` | 8×8 | 14 (N2/E6/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-108` |
| 2154 | `campaign-2154` | 8×8 | 16 (N6/E0/S9/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-108` |
| 2155 | `campaign-2155` | 8×8 | 18 (N0/E12/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-108` |
| 2156 | `campaign-2156` | 5×5 | 7 (N2/E0/S4/W1) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-108` |
| 2157 | `campaign-2157` | 5×5 | 8 (N1/E1/S3/W3) | 1 (Pull0/Push1) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 4 | 1+ | `magnetic-circuit-108` |
| 2158 | `campaign-2158` | 7×7 | 14 (N2/E0/S8/W4) | 5 (Pull5/Push0) | 12 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-108` |
| 2159 | `campaign-2159` | 8×8 | 14 (N0/E6/S1/W7) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-108` |
| 2160 | `campaign-2160` | 8×8 | 16 (N6/E3/S6/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-108` |
| 2161 | `campaign-2161` | 8×8 | 18 (N0/E6/S8/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 12 | 1+ | `magnetic-circuit-109` |
| 2162 | `campaign-2162` | 4×4 | 6 (N1/E3/S1/W1) | 1 (Pull1/Push0) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-109` |
| 2163 | `campaign-2163` | 5×5 | 8 (N0/E2/S2/W4) | 4 (Pull2/Push2) | 3 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 7 | 1+ | `magnetic-circuit-109` |
| 2164 | `campaign-2164` | 5×5 | 10 (N3/E5/S1/W1) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 10 | 1+ | `magnetic-circuit-109` |
| 2165 | `campaign-2165` | 8×8 | 14 (N0/E6/S2/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-109` |
| 2166 | `campaign-2166` | 8×8 | 16 (N7/E1/S5/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 14 | 1+ | `magnetic-circuit-109` |
| 2167 | `campaign-2167` | 8×8 | 18 (N7/E5/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-109` |
| 2168 | `campaign-2168` | 4×4 | 6 (N0/E3/S1/W2) | 1 (Pull1/Push0) | 2 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-109` |
| 2169 | `campaign-2169` | 6×6 | 10 (N3/E2/S4/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 7 | 1+ | `magnetic-circuit-109` |
| 2170 | `campaign-2170` | 5×5 | 10 (N2/E3/S4/W1) | 4 (Pull4/Push0) | 2 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-109` |
| 2171 | `campaign-2171` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-109` |
| 2172 | `campaign-2172` | 8×8 | 16 (N7/E1/S6/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-109` |
| 2173 | `campaign-2173` | 8×8 | 18 (N6/E6/S1/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-109` |
| 2174 | `campaign-2174` | 5×5 | 7 (N2/E3/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 6 | 1+ | `magnetic-circuit-109` |
| 2175 | `campaign-2175` | 6×6 | 10 (N3/E3/S3/W1) | 1 (Pull1/Push0) | 10 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-109` |
| 2176 | `campaign-2176` | 5×5 | 10 (N2/E2/S4/W2) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-109` |
| 2177 | `campaign-2177` | 8×8 | 14 (N3/E6/S0/W5) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 10 | 1+ | `magnetic-circuit-109` |
| 2178 | `campaign-2178` | 8×8 | 16 (N6/E4/S4/W2) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 12 | 1+ | `magnetic-circuit-109` |
| 2179 | `campaign-2179` | 8×8 | 18 (N0/E4/S7/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-109` |
| 2180 | `campaign-2180` | 5×5 | 7 (N2/E1/S1/W3) | 1 (Pull1/Push0) | 5 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 4 | 1+ | `magnetic-circuit-109` |
| 2181 | `campaign-2181` | 5×5 | 8 (N2/E4/S2/W0) | 3 (Pull3/Push0) | 4 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 5 | 1+ | `magnetic-circuit-110` |
| 2182 | `campaign-2182` | 7×7 | 14 (N6/E7/S0/W1) | 2 (Pull1/Push1) | 15 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 14 | 5 | 1+ | `magnetic-circuit-110` |
| 2183 | `campaign-2183` | 8×8 | 14 (N3/E5/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-110` |
| 2184 | `campaign-2184` | 8×8 | 16 (N1/E7/S2/W6) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-110` |
| 2185 | `campaign-2185` | 8×8 | 18 (N6/E7/S0/W5) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-110` |
| 2186 | `campaign-2186` | 4×4 | 6 (N1/E2/S2/W1) | 2 (Pull2/Push0) | 1 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 6 | 1+ | `magnetic-circuit-110` |
| 2187 | `campaign-2187` | 6×6 | 10 (N4/E3/S2/W1) | 2 (Pull1/Push1) | 9 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-110` |
| 2188 | `campaign-2188` | 5×5 | 10 (N2/E0/S3/W5) | 3 (Pull3/Push0) | 3 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-110` |
| 2189 | `campaign-2189` | 8×8 | 14 (N3/E7/S0/W4) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 11 | 1+ | `magnetic-circuit-110` |
| 2190 | `campaign-2190` | 8×8 | 16 (N6/E1/S6/W3) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 11 | 1+ | `magnetic-circuit-110` |
| 2191 | `campaign-2191` | 8×8 | 18 (N1/E10/S0/W7) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-110` |
| 2192 | `campaign-2192` | 8×8 | 12 (N1/E2/S6/W3) | 1 (Pull0/Push1) | 19 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 12 | 4 | 1+ | `magnetic-circuit-110` |
| 2193 | `campaign-2193` | 5×5 | 8 (N2/E3/S2/W1) | 2 (Pull2/Push0) | 6 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 8 | 6 | 1+ | `magnetic-circuit-110` |
| 2194 | `campaign-2194` | 5×5 | 10 (N5/E2/S1/W2) | 5 (Pull5/Push0) | 1 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 8 | 1+ | `magnetic-circuit-110` |
| 2195 | `campaign-2195` | 8×8 | 14 (N7/E3/S4/W0) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-110` |
| 2196 | `campaign-2196` | 8×8 | 16 (N4/E3/S8/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-110` |
| 2197 | `campaign-2197` | 8×8 | 18 (N0/E8/S6/W4) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 11 | 1+ | `magnetic-circuit-110` |
| 2198 | `campaign-2198` | 5×5 | 7 (N2/E1/S0/W4) | 3 (Pull2/Push1) | 3 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 7 | 5 | 1+ | `magnetic-circuit-110` |
| 2199 | `campaign-2199` | 6×6 | 10 (N2/E2/S4/W2) | 3 (Pull2/Push1) | 8 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 6 | 1+ | `magnetic-circuit-110` |
| 2200 | `campaign-2200` | 5×5 | 10 (N5/E2/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-campaign-v10-hard-dense` | `ADVANCED` | 10 | 5 | 1+ | `magnetic-circuit-110` |
| 2201 | `campaign-2201` | 8×8 | 14 (N1/E7/S0/W6) | 11 (Pull11/Push0) | 19 | `v5-campaign-v10-super-hard-dense` | `ADVANCED` | 14 | 12 | 1+ | `magnetic-circuit-111` |
| 2202 | `campaign-2202` | 8×8 | 16 (N8/E3/S4/W1) | 11 (Pull11/Push0) | 21 | `v5-campaign-v10-expert-dense` | `ADVANCED` | 16 | 13 | 1+ | `magnetic-circuit-111` |
| 2203 | `campaign-2203` | 8×8 | 18 (N0/E12/S0/W6) | 10 (Pull10/Push0) | 24 | `v5-campaign-v10-master-dense` | `ADVANCED` | 18 | 10 | 1+ | `magnetic-circuit-111` |
| 2204 | `campaign-2204` | 4×4 | 6 (N3/E2/S0/W1) | 3 (Pull3/Push0) | 0 | `v5-campaign-v10-easy-dense` | `DEVELOPING` | 6 | 5 | 1+ | `magnetic-circuit-111` |
| 2205 | `campaign-2205` | 6×6 | 10 (N0/E2/S6/W2) | 4 (Pull3/Push1) | 7 | `v5-campaign-v10-medium-dense` | `DEVELOPING` | 10 | 8 | 1+ | `magnetic-circuit-111` |
<!-- END GENERATED CAMPAIGN INVENTORY -->

### 25.2 Production Infinite V1 — 624 levels

Source: `docs/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json`, SHA-256
`c3500d87eedfa7cdbfdde1000bf49ae9af5c2a0e0c24c4ce5c7d977b5d6b4f83`.

| # | ID | Grid | A (N/E/S/W) | M (Pull/Push) | W | Candidate profile | UI band | Par | Open | Solns | Pack |
|---:|---|---|---|---|---:|---|---|---|---:|---:|---|
<!-- BEGIN GENERATED INFINITE INVENTORY -->
| 1 | `infinite-v1-easy-0001` | 8×8 | 3 (N0/E0/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 2 | `infinite-v1-medium-0002` | 6×6 | 4 (N2/E1/S1/W0) | 2 (Pull1/Push1) | 2 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 3 | `infinite-v1-hard-0003` | 5×5 | 5 (N2/E2/S1/W0) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 4 | `infinite-v1-easy-0004` | 4×4 | 5 (N0/E2/S2/W1) | 1 (Pull1/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 2 | 2 | `infinite-v1` |
| 5 | `infinite-v1-medium-0005` | 6×6 | 3 (N0/E2/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 6 | `infinite-v1-hard-0006` | 6×6 | 4 (N2/E1/S0/W1) | 5 (Pull2/Push3) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 8 | `infinite-v1` |
| 7 | `infinite-v1-easy-0007` | 4×4 | 4 (N1/E1/S2/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 8 | `infinite-v1-medium-0008` | 6×6 | 3 (N0/E2/S1/W0) | 4 (Pull4/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 9 | `infinite-v1-hard-0009` | 5×5 | 6 (N1/E1/S3/W1) | 3 (Pull2/Push1) | 3 | `v5-hard` | `ADVANCED` | 6 | 2 | 30 | `infinite-v1` |
| 10 | `infinite-v1-easy-0010` | 5×5 | 3 (N0/E1/S2/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 11 | `infinite-v1-medium-0011` | 6×6 | 6 (N2/E0/S1/W3) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 6 | 2 | 20 | `infinite-v1` |
| 12 | `infinite-v1-hard-0012` | 6×6 | 4 (N3/E1/S0/W0) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 13 | `infinite-v1-easy-0013` | 5×5 | 5 (N2/E1/S0/W2) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 14 | `infinite-v1-medium-0014` | 5×5 | 4 (N0/E0/S3/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 15 | `infinite-v1-hard-0015` | 5×5 | 4 (N3/E0/S1/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 16 | `infinite-v1-easy-0016` | 5×5 | 3 (N1/E1/S0/W1) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 2 | `infinite-v1` |
| 17 | `infinite-v1-medium-0017` | 6×6 | 5 (N3/E1/S1/W0) | 2 (Pull1/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 18 | `infinite-v1-hard-0018` | 5×5 | 5 (N2/E2/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 19 | `infinite-v1-easy-0019` | 5×5 | 4 (N1/E1/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 20 | `infinite-v1-medium-0020` | 5×5 | 4 (N0/E2/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 3 | `infinite-v1` |
| 21 | `infinite-v1-hard-0021` | 5×5 | 5 (N2/E1/S0/W2) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 50 | `infinite-v1` |
| 22 | `infinite-v1-easy-0022` | 5×5 | 3 (N2/E1/S0/W0) | 3 (Pull1/Push2) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 23 | `infinite-v1-medium-0023` | 5×5 | 4 (N1/E0/S1/W2) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 24 | `infinite-v1-hard-0024` | 6×6 | 6 (N1/E3/S1/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 4 | 80 | `infinite-v1` |
| 25 | `infinite-v1-easy-0025` | 8×8 | 3 (N2/E0/S0/W1) | 1 (Pull0/Push1) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 2 | `infinite-v1` |
| 26 | `infinite-v1-medium-0026` | 5×5 | 5 (N2/E2/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 27 | `infinite-v1-hard-0027` | 5×5 | 5 (N1/E1/S0/W3) | 2 (Pull2/Push0) | 6 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 28 | `infinite-v1-easy-0028` | 5×5 | 5 (N1/E3/S0/W1) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 5 | 50 | `infinite-v1` |
| 29 | `infinite-v1-medium-0029` | 5×5 | 4 (N2/E0/S1/W1) | 2 (Pull2/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 30 | `infinite-v1-hard-0030` | 5×5 | 5 (N0/E1/S2/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 31 | `infinite-v1-easy-0031` | 8×8 | 5 (N1/E2/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 32 | `infinite-v1-medium-0032` | 6×6 | 6 (N2/E2/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 6 | 5 | 186 | `infinite-v1` |
| 33 | `infinite-v1-hard-0033` | 5×5 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 34 | `infinite-v1-easy-0034` | 8×8 | 4 (N1/E2/S0/W1) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 35 | `infinite-v1-medium-0035` | 5×5 | 5 (N2/E3/S0/W0) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 36 | `infinite-v1-hard-0036` | 5×5 | 4 (N1/E0/S1/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 37 | `infinite-v1-easy-0037` | 5×5 | 5 (N3/E1/S1/W0) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 38 | `infinite-v1-medium-0038` | 6×6 | 5 (N2/E2/S0/W1) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 39 | `infinite-v1-hard-0039` | 7×7 | 4 (N1/E1/S1/W1) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 40 | `infinite-v1-easy-0040` | 8×8 | 4 (N1/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 41 | `infinite-v1-medium-0041` | 5×5 | 3 (N1/E2/S0/W0) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 42 | `infinite-v1-hard-0042` | 5×5 | 4 (N0/E2/S1/W1) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 43 | `infinite-v1-easy-0043` | 5×5 | 4 (N3/E0/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 44 | `infinite-v1-medium-0044` | 6×6 | 5 (N0/E4/S0/W1) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 46 | `infinite-v1` |
| 45 | `infinite-v1-hard-0045` | 6×6 | 4 (N0/E2/S0/W2) | 2 (Pull1/Push1) | 3 | `v5-hard` | `ADVANCED` | 4 | 2 | 4 | `infinite-v1` |
| 46 | `infinite-v1-easy-0046` | 5×5 | 3 (N0/E1/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 47 | `infinite-v1-medium-0047` | 8×8 | 5 (N2/E2/S0/W1) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 48 | `infinite-v1-hard-0048` | 5×5 | 5 (N4/E1/S0/W0) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 2 | 20 | `infinite-v1` |
| 49 | `infinite-v1-easy-0049` | 8×8 | 5 (N2/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 50 | `infinite-v1-medium-0050` | 5×5 | 4 (N0/E2/S1/W1) | 4 (Pull4/Push0) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 51 | `infinite-v1-hard-0051` | 5×5 | 3 (N1/E1/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 52 | `infinite-v1-easy-0052` | 5×5 | 4 (N1/E0/S2/W1) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 53 | `infinite-v1-medium-0053` | 6×6 | 5 (N2/E1/S0/W2) | 2 (Pull2/Push0) | 4 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 54 | `infinite-v1-hard-0054` | 6×6 | 5 (N2/E2/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 55 | `infinite-v1-easy-0055` | 8×8 | 4 (N1/E3/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 56 | `infinite-v1-medium-0056` | 6×6 | 5 (N1/E1/S2/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 57 | `infinite-v1-hard-0057` | 5×5 | 4 (N2/E2/S0/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 58 | `infinite-v1-easy-0058` | 5×5 | 5 (N3/E1/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 3 | 25 | `infinite-v1` |
| 59 | `infinite-v1-medium-0059` | 5×5 | 3 (N1/E0/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 60 | `infinite-v1-hard-0060` | 5×5 | 4 (N2/E2/S0/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `infinite-v1` |
| 61 | `infinite-v1-easy-0061` | 5×5 | 3 (N0/E1/S1/W1) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 62 | `infinite-v1-medium-0062` | 6×6 | 3 (N0/E2/S1/W0) | 2 (Pull1/Push1) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 63 | `infinite-v1-hard-0063` | 8×8 | 5 (N2/E1/S1/W1) | 4 (Pull3/Push1) | 3 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 64 | `infinite-v1-easy-0064` | 4×4 | 3 (N1/E0/S0/W2) | 1 (Pull1/Push0) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 65 | `infinite-v1-medium-0065` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 66 | `infinite-v1-hard-0066` | 6×6 | 6 (N3/E2/S0/W1) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 6 | 2 | 3 | `infinite-v1` |
| 67 | `infinite-v1-easy-0067` | 4×4 | 3 (N1/E1/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 68 | `infinite-v1-medium-0068` | 6×6 | 5 (N3/E1/S1/W0) | 2 (Pull1/Push1) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 69 | `infinite-v1-hard-0069` | 6×6 | 4 (N2/E0/S1/W1) | 4 (Pull4/Push0) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 70 | `infinite-v1-easy-0070` | 5×5 | 4 (N3/E0/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 71 | `infinite-v1-medium-0071` | 8×8 | 6 (N3/E0/S1/W2) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 6 | 3 | 60 | `infinite-v1` |
| 72 | `infinite-v1-hard-0072` | 6×6 | 4 (N1/E1/S1/W1) | 4 (Pull3/Push1) | 4 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `infinite-v1` |
| 73 | `infinite-v1-easy-0073` | 5×5 | 4 (N2/E1/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 74 | `infinite-v1-medium-0074` | 5×5 | 3 (N0/E0/S2/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 75 | `infinite-v1-hard-0075` | 5×5 | 5 (N2/E1/S2/W0) | 4 (Pull3/Push1) | 4 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 76 | `infinite-v1-easy-0076` | 5×5 | 4 (N0/E2/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 77 | `infinite-v1-medium-0077` | 6×6 | 5 (N1/E1/S1/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 78 | `infinite-v1-hard-0078` | 7×7 | 4 (N1/E0/S2/W1) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 79 | `infinite-v1-easy-0079` | 5×5 | 4 (N2/E2/S0/W0) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 80 | `infinite-v1-medium-0080` | 5×5 | 5 (N2/E1/S0/W2) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 81 | `infinite-v1-hard-0081` | 5×5 | 6 (N2/E1/S1/W2) | 3 (Pull3/Push0) | 4 | `v5-hard` | `ADVANCED` | 6 | 4 | 42 | `infinite-v1` |
| 82 | `infinite-v1-easy-0082` | 5×5 | 4 (N2/E0/S1/W1) | 3 (Pull2/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 83 | `infinite-v1-medium-0083` | 5×5 | 5 (N2/E3/S0/W0) | 3 (Pull2/Push1) | 4 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 84 | `infinite-v1-hard-0084` | 5×5 | 4 (N1/E2/S0/W1) | 2 (Pull1/Push1) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 85 | `infinite-v1-easy-0085` | 5×5 | 5 (N2/E1/S1/W1) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 86 | `infinite-v1-medium-0086` | 5×5 | 5 (N2/E0/S3/W0) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 87 | `infinite-v1-hard-0087` | 5×5 | 5 (N1/E2/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 88 | `infinite-v1-easy-0088` | 5×5 | 3 (N0/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 89 | `infinite-v1-medium-0089` | 6×6 | 3 (N1/E0/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 90 | `infinite-v1-hard-0090` | 8×8 | 4 (N1/E2/S0/W1) | 4 (Pull3/Push1) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 91 | `infinite-v1-easy-0091` | 8×8 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 92 | `infinite-v1-medium-0092` | 8×8 | 5 (N1/E2/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 93 | `infinite-v1-hard-0093` | 5×5 | 5 (N1/E2/S2/W0) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 94 | `infinite-v1-easy-0094` | 8×8 | 4 (N2/E0/S2/W0) | 3 (Pull3/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 95 | `infinite-v1-medium-0095` | 5×5 | 5 (N0/E4/S0/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 22 | `infinite-v1` |
| 96 | `infinite-v1-hard-0096` | 6×6 | 5 (N1/E3/S1/W0) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 97 | `infinite-v1-easy-0097` | 5×5 | 5 (N1/E2/S1/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 98 | `infinite-v1-medium-0098` | 8×8 | 4 (N3/E0/S0/W1) | 4 (Pull3/Push1) | 3 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 99 | `infinite-v1-hard-0099` | 6×6 | 4 (N0/E0/S2/W2) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 100 | `infinite-v1-easy-0100` | 5×5 | 4 (N1/E1/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 101 | `infinite-v1-medium-0101` | 6×6 | 4 (N2/E1/S0/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 102 | `infinite-v1-hard-0102` | 6×6 | 4 (N0/E1/S2/W1) | 4 (Pull3/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 103 | `infinite-v1-easy-0103` | 4×4 | 2 (N0/E2/S0/W0) | 3 (Pull2/Push1) | 1 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 104 | `infinite-v1-medium-0104` | 6×6 | 4 (N0/E0/S1/W3) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 105 | `infinite-v1-hard-0105` | 6×6 | 4 (N2/E0/S0/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 106 | `infinite-v1-easy-0106` | 4×4 | 4 (N0/E1/S2/W1) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 107 | `infinite-v1-medium-0107` | 5×5 | 6 (N1/E4/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 6 | 4 | 48 | `infinite-v1` |
| 108 | `infinite-v1-hard-0108` | 6×6 | 5 (N1/E3/S1/W0) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 109 | `infinite-v1-easy-0109` | 8×8 | 5 (N0/E3/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 110 | `infinite-v1-medium-0110` | 6×6 | 5 (N2/E0/S2/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 18 | `infinite-v1` |
| 111 | `infinite-v1-hard-0111` | 5×5 | 5 (N2/E1/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 112 | `infinite-v1-easy-0112` | 5×5 | 3 (N1/E1/S0/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 113 | `infinite-v1-medium-0113` | 8×8 | 5 (N3/E0/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 114 | `infinite-v1-hard-0114` | 6×6 | 4 (N0/E1/S1/W2) | 3 (Pull3/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 115 | `infinite-v1-easy-0115` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 116 | `infinite-v1-medium-0116` | 5×5 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 117 | `infinite-v1-hard-0117` | 5×5 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 118 | `infinite-v1-easy-0118` | 8×8 | 4 (N2/E1/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 2 | 3 | `infinite-v1` |
| 119 | `infinite-v1-medium-0119` | 6×6 | 5 (N3/E0/S0/W2) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 120 | `infinite-v1-hard-0120` | 7×7 | 5 (N4/E0/S1/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 121 | `infinite-v1-easy-0121` | 5×5 | 3 (N1/E1/S1/W0) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 122 | `infinite-v1-medium-0122` | 8×8 | 5 (N1/E0/S1/W3) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 123 | `infinite-v1-hard-0123` | 6×6 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 124 | `infinite-v1-easy-0124` | 4×4 | 2 (N1/E1/S0/W0) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 125 | `infinite-v1-medium-0125` | 5×5 | 5 (N2/E0/S1/W2) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 126 | `infinite-v1-hard-0126` | 6×6 | 5 (N0/E2/S0/W3) | 3 (Pull1/Push2) | 4 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 127 | `infinite-v1-easy-0127` | 5×5 | 3 (N1/E1/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 128 | `infinite-v1-medium-0128` | 6×6 | 5 (N3/E1/S0/W1) | 4 (Pull3/Push1) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 129 | `infinite-v1-hard-0129` | 6×6 | 5 (N1/E1/S1/W2) | 3 (Pull1/Push2) | 2 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 130 | `infinite-v1-easy-0130` | 4×4 | 2 (N0/E0/S1/W1) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 131 | `infinite-v1-medium-0131` | 5×5 | 5 (N3/E2/S0/W0) | 1 (Pull0/Push1) | 4 | `v5-medium` | `DEVELOPING` | 5 | 3 | 10 | `infinite-v1` |
| 132 | `infinite-v1-hard-0132` | 5×5 | 4 (N2/E0/S2/W0) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 133 | `infinite-v1-easy-0133` | 4×4 | 2 (N0/E2/S0/W0) | 1 (Pull1/Push0) | 4 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 134 | `infinite-v1-medium-0134` | 8×8 | 5 (N2/E1/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 135 | `infinite-v1-hard-0135` | 5×5 | 5 (N1/E1/S2/W1) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 136 | `infinite-v1-easy-0136` | 8×8 | 5 (N1/E1/S1/W2) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 2 | 5 | `infinite-v1` |
| 137 | `infinite-v1-medium-0137` | 5×5 | 3 (N1/E0/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 138 | `infinite-v1-hard-0138` | 6×6 | 3 (N3/E0/S0/W0) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 139 | `infinite-v1-easy-0139` | 8×8 | 4 (N1/E1/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 140 | `infinite-v1-medium-0140` | 5×5 | 4 (N0/E1/S2/W1) | 2 (Pull2/Push0) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 141 | `infinite-v1-hard-0141` | 5×5 | 6 (N2/E2/S1/W1) | 2 (Pull2/Push0) | 6 | `v5-hard` | `ADVANCED` | 6 | 3 | 60 | `infinite-v1` |
| 142 | `infinite-v1-easy-0142` | 5×5 | 3 (N0/E0/S2/W1) | 3 (Pull3/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 143 | `infinite-v1-medium-0143` | 6×6 | 5 (N3/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 144 | `infinite-v1-hard-0144` | 6×6 | 5 (N1/E2/S0/W2) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 145 | `infinite-v1-easy-0145` | 8×8 | 5 (N1/E1/S2/W1) | 3 (Pull2/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 146 | `infinite-v1-medium-0146` | 5×5 | 5 (N1/E2/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 147 | `infinite-v1-hard-0147` | 6×6 | 4 (N0/E1/S3/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 9 | `infinite-v1` |
| 148 | `infinite-v1-easy-0148` | 5×5 | 4 (N1/E0/S0/W3) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 4 | 4 | 12 | `infinite-v1` |
| 149 | `infinite-v1-medium-0149` | 5×5 | 3 (N1/E1/S0/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 150 | `infinite-v1-hard-0150` | 5×5 | 5 (N1/E1/S2/W1) | 2 (Pull2/Push0) | 3 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 151 | `infinite-v1-easy-0151` | 5×5 | 2 (N0/E0/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 152 | `infinite-v1-medium-0152` | 8×8 | 5 (N2/E1/S1/W1) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 153 | `infinite-v1-hard-0153` | 7×7 | 5 (N1/E1/S1/W2) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 2 | 17 | `infinite-v1` |
| 154 | `infinite-v1-easy-0154` | 5×5 | 3 (N2/E0/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 155 | `infinite-v1-medium-0155` | 6×6 | 4 (N2/E1/S0/W1) | 3 (Pull1/Push2) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 156 | `infinite-v1-hard-0156` | 8×8 | 4 (N0/E2/S0/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 2 | 2 | `infinite-v1` |
| 157 | `infinite-v1-easy-0157` | 8×8 | 3 (N1/E2/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 158 | `infinite-v1-medium-0158` | 6×6 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 159 | `infinite-v1-hard-0159` | 6×6 | 5 (N1/E2/S1/W1) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 160 | `infinite-v1-easy-0160` | 5×5 | 5 (N3/E1/S1/W0) | 3 (Pull1/Push2) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 161 | `infinite-v1-medium-0161` | 5×5 | 3 (N0/E2/S1/W0) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 162 | `infinite-v1-hard-0162` | 5×5 | 5 (N1/E1/S3/W0) | 5 (Pull5/Push0) | 4 | `v5-hard` | `ADVANCED` | 5 | 3 | 30 | `infinite-v1` |
| 163 | `infinite-v1-easy-0163` | 4×4 | 2 (N0/E1/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 164 | `infinite-v1-medium-0164` | 6×6 | 5 (N3/E0/S1/W1) | 3 (Pull1/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 165 | `infinite-v1-hard-0165` | 6×6 | 4 (N1/E1/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 166 | `infinite-v1-easy-0166` | 5×5 | 4 (N2/E1/S1/W0) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 167 | `infinite-v1-medium-0167` | 6×6 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 168 | `infinite-v1-hard-0168` | 5×5 | 5 (N2/E1/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 169 | `infinite-v1-easy-0169` | 5×5 | 4 (N2/E0/S2/W0) | 2 (Pull1/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 170 | `infinite-v1-medium-0170` | 5×5 | 4 (N0/E2/S1/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 171 | `infinite-v1-hard-0171` | 7×7 | 5 (N2/E2/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 40 | `infinite-v1` |
| 172 | `infinite-v1-easy-0172` | 4×4 | 3 (N2/E0/S1/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 173 | `infinite-v1-medium-0173` | 5×5 | 4 (N1/E1/S1/W1) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 174 | `infinite-v1-hard-0174` | 6×6 | 5 (N2/E1/S0/W2) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 10 | `infinite-v1` |
| 175 | `infinite-v1-easy-0175` | 8×8 | 5 (N2/E0/S0/W3) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 176 | `infinite-v1-medium-0176` | 5×5 | 3 (N0/E1/S0/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 177 | `infinite-v1-hard-0177` | 5×5 | 5 (N2/E1/S2/W0) | 2 (Pull2/Push0) | 4 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 178 | `infinite-v1-easy-0178` | 5×5 | 4 (N2/E0/S1/W1) | 1 (Pull0/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 179 | `infinite-v1-medium-0179` | 6×6 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 180 | `infinite-v1-hard-0180` | 5×5 | 4 (N4/E0/S0/W0) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 181 | `infinite-v1-easy-0181` | 4×4 | 4 (N2/E0/S2/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 182 | `infinite-v1-medium-0182` | 5×5 | 3 (N1/E1/S0/W1) | 4 (Pull3/Push1) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 183 | `infinite-v1-hard-0183` | 6×6 | 5 (N1/E1/S2/W1) | 3 (Pull3/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 184 | `infinite-v1-easy-0184` | 8×8 | 5 (N1/E2/S1/W1) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 185 | `infinite-v1-medium-0185` | 5×5 | 5 (N1/E1/S2/W1) | 4 (Pull4/Push0) | 4 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 186 | `infinite-v1-hard-0186` | 6×6 | 4 (N2/E1/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 187 | `infinite-v1-easy-0187` | 8×8 | 4 (N1/E1/S0/W2) | 3 (Pull3/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 188 | `infinite-v1-medium-0188` | 5×5 | 4 (N0/E1/S3/W0) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 189 | `infinite-v1-hard-0189` | 6×6 | 4 (N1/E1/S1/W1) | 4 (Pull3/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 190 | `infinite-v1-easy-0190` | 4×4 | 4 (N2/E1/S0/W1) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 191 | `infinite-v1-medium-0191` | 6×6 | 5 (N3/E1/S0/W1) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 192 | `infinite-v1-hard-0192` | 6×6 | 5 (N1/E0/S2/W2) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 35 | `infinite-v1` |
| 193 | `infinite-v1-easy-0193` | 8×8 | 4 (N0/E0/S2/W2) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 194 | `infinite-v1-medium-0194` | 5×5 | 5 (N2/E2/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 195 | `infinite-v1-hard-0195` | 5×5 | 5 (N2/E2/S0/W1) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 20 | `infinite-v1` |
| 196 | `infinite-v1-easy-0196` | 8×8 | 4 (N2/E2/S0/W0) | 3 (Pull1/Push2) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 197 | `infinite-v1-medium-0197` | 5×5 | 4 (N1/E2/S1/W0) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 198 | `infinite-v1-hard-0198` | 6×6 | 5 (N1/E1/S1/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 50 | `infinite-v1` |
| 199 | `infinite-v1-easy-0199` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 200 | `infinite-v1-medium-0200` | 6×6 | 4 (N0/E0/S2/W2) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 201 | `infinite-v1-hard-0201` | 5×5 | 3 (N2/E1/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 202 | `infinite-v1-easy-0202` | 8×8 | 3 (N2/E0/S0/W1) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 3 | 3 | `infinite-v1` |
| 203 | `infinite-v1-medium-0203` | 5×5 | 5 (N4/E0/S0/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 22 | `infinite-v1` |
| 204 | `infinite-v1-hard-0204` | 6×6 | 5 (N1/E2/S1/W1) | 5 (Pull4/Push1) | 3 | `v5-hard` | `ADVANCED` | 5 | 4 | 50 | `infinite-v1` |
| 205 | `infinite-v1-easy-0205` | 5×5 | 3 (N1/E2/S0/W0) | 3 (Pull2/Push1) | 4 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 206 | `infinite-v1-medium-0206` | 5×5 | 4 (N3/E1/S0/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 207 | `infinite-v1-hard-0207` | 6×6 | 5 (N0/E1/S1/W3) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 60 | `infinite-v1` |
| 208 | `infinite-v1-easy-0208` | 8×8 | 5 (N2/E1/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 209 | `infinite-v1-medium-0209` | 5×5 | 6 (N2/E1/S1/W2) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 6 | 3 | 60 | `infinite-v1` |
| 210 | `infinite-v1-hard-0210` | 5×5 | 5 (N2/E1/S2/W0) | 3 (Pull1/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 211 | `infinite-v1-easy-0211` | 5×5 | 4 (N1/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 212 | `infinite-v1-medium-0212` | 6×6 | 5 (N4/E1/S0/W0) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 2 | 20 | `infinite-v1` |
| 213 | `infinite-v1-hard-0213` | 5×5 | 4 (N0/E2/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 214 | `infinite-v1-easy-0214` | 4×4 | 2 (N2/E0/S0/W0) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 215 | `infinite-v1-medium-0215` | 8×8 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 216 | `infinite-v1-hard-0216` | 5×5 | 4 (N1/E2/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 217 | `infinite-v1-easy-0217` | 5×5 | 4 (N1/E2/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 218 | `infinite-v1-medium-0218` | 5×5 | 3 (N0/E1/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 219 | `infinite-v1-hard-0219` | 7×7 | 5 (N1/E3/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 220 | `infinite-v1-easy-0220` | 5×5 | 5 (N2/E0/S1/W2) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 221 | `infinite-v1-medium-0221` | 5×5 | 5 (N4/E1/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 222 | `infinite-v1-hard-0222` | 6×6 | 5 (N1/E2/S0/W2) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 30 | `infinite-v1` |
| 223 | `infinite-v1-easy-0223` | 4×4 | 4 (N1/E1/S1/W1) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 16 | `infinite-v1` |
| 224 | `infinite-v1-medium-0224` | 5×5 | 5 (N2/E3/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 225 | `infinite-v1-hard-0225` | 5×5 | 6 (N1/E4/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-hard` | `ADVANCED` | 6 | 4 | 90 | `infinite-v1` |
| 226 | `infinite-v1-easy-0226` | 5×5 | 4 (N2/E2/S0/W0) | 2 (Pull0/Push2) | 0 | `v5-easy` | `DEVELOPING` | 4 | 2 | 2 | `infinite-v1` |
| 227 | `infinite-v1-medium-0227` | 8×8 | 5 (N2/E1/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 228 | `infinite-v1-hard-0228` | 5×5 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 229 | `infinite-v1-easy-0229` | 4×4 | 2 (N0/E1/S1/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 230 | `infinite-v1-medium-0230` | 5×5 | 4 (N0/E1/S2/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 231 | `infinite-v1-hard-0231` | 6×6 | 4 (N1/E2/S0/W1) | 3 (Pull3/Push0) | 5 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 232 | `infinite-v1-easy-0232` | 8×8 | 4 (N0/E2/S0/W2) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 4 | 12 | `infinite-v1` |
| 233 | `infinite-v1-medium-0233` | 6×6 | 4 (N0/E4/S0/W0) | 4 (Pull1/Push3) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 234 | `infinite-v1-hard-0234` | 6×6 | 6 (N2/E1/S2/W1) | 2 (Pull1/Push1) | 5 | `v5-medium` | `DEVELOPING` | 6 | 4 | 66 | `infinite-v1` |
| 235 | `infinite-v1-easy-0235` | 4×4 | 3 (N0/E1/S1/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 236 | `infinite-v1-medium-0236` | 5×5 | 3 (N0/E0/S1/W2) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 237 | `infinite-v1-hard-0237` | 5×5 | 3 (N2/E0/S1/W0) | 3 (Pull3/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 238 | `infinite-v1-easy-0238` | 5×5 | 4 (N0/E1/S3/W0) | 2 (Pull1/Push1) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 239 | `infinite-v1-medium-0239` | 6×6 | 4 (N3/E0/S0/W1) | 1 (Pull0/Push1) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 240 | `infinite-v1-hard-0240` | 5×5 | 5 (N3/E1/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 241 | `infinite-v1-easy-0241` | 4×4 | 3 (N0/E2/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 242 | `infinite-v1-medium-0242` | 6×6 | 5 (N3/E2/S0/W0) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 243 | `infinite-v1-hard-0243` | 5×5 | 4 (N1/E2/S1/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 244 | `infinite-v1-easy-0244` | 5×5 | 5 (N2/E2/S0/W1) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 245 | `infinite-v1-medium-0245` | 6×6 | 6 (N1/E0/S2/W3) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 14 | `infinite-v1` |
| 246 | `infinite-v1-hard-0246` | 5×5 | 4 (N1/E0/S2/W1) | 5 (Pull2/Push3) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 247 | `infinite-v1-easy-0247` | 8×8 | 4 (N3/E1/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 248 | `infinite-v1-medium-0248` | 5×5 | 4 (N1/E1/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 249 | `infinite-v1-hard-0249` | 6×6 | 5 (N2/E0/S2/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 30 | `infinite-v1` |
| 250 | `infinite-v1-easy-0250` | 5×5 | 5 (N3/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 2 | 10 | `infinite-v1` |
| 251 | `infinite-v1-medium-0251` | 6×6 | 4 (N2/E1/S1/W0) | 3 (Pull3/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 252 | `infinite-v1-hard-0252` | 6×6 | 4 (N1/E1/S0/W2) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 253 | `infinite-v1-easy-0253` | 8×8 | 4 (N1/E0/S2/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 254 | `infinite-v1-medium-0254` | 6×6 | 6 (N2/E1/S1/W2) | 1 (Pull0/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 2 | 4 | `infinite-v1` |
| 255 | `infinite-v1-hard-0255` | 5×5 | 4 (N2/E0/S1/W1) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 256 | `infinite-v1-easy-0256` | 5×5 | 5 (N0/E1/S1/W3) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 257 | `infinite-v1-medium-0257` | 5×5 | 4 (N1/E0/S1/W2) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 258 | `infinite-v1-hard-0258` | 5×5 | 5 (N3/E2/S0/W0) | 1 (Pull0/Push1) | 5 | `v5-medium` | `DEVELOPING` | 5 | 1 | 1 | `infinite-v1` |
| 259 | `infinite-v1-easy-0259` | 5×5 | 5 (N3/E1/S0/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 260 | `infinite-v1-medium-0260` | 6×6 | 3 (N0/E0/S2/W1) | 2 (Pull1/Push1) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 261 | `infinite-v1-hard-0261` | 5×5 | 6 (N2/E2/S1/W1) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 2 | 6 | `infinite-v1` |
| 262 | `infinite-v1-easy-0262` | 4×4 | 4 (N1/E0/S2/W1) | 2 (Pull1/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 263 | `infinite-v1-medium-0263` | 5×5 | 5 (N1/E2/S2/W0) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 264 | `infinite-v1-hard-0264` | 6×6 | 6 (N3/E0/S3/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 6 | 4 | 72 | `infinite-v1` |
| 265 | `infinite-v1-easy-0265` | 5×5 | 4 (N2/E0/S1/W1) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 9 | `infinite-v1` |
| 266 | `infinite-v1-medium-0266` | 5×5 | 3 (N1/E0/S2/W0) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 267 | `infinite-v1-hard-0267` | 5×5 | 6 (N2/E2/S0/W2) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 6 | 4 | 60 | `infinite-v1` |
| 268 | `infinite-v1-easy-0268` | 8×8 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 269 | `infinite-v1-medium-0269` | 5×5 | 5 (N2/E2/S0/W1) | 4 (Pull4/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 270 | `infinite-v1-hard-0270` | 6×6 | 4 (N2/E1/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 4 | `infinite-v1` |
| 271 | `infinite-v1-easy-0271` | 5×5 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 272 | `infinite-v1-medium-0272` | 6×6 | 5 (N1/E2/S0/W2) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 273 | `infinite-v1-hard-0273` | 5×5 | 6 (N1/E1/S3/W1) | 4 (Pull4/Push0) | 2 | `v5-medium` | `DEVELOPING` | 6 | 3 | 36 | `infinite-v1` |
| 274 | `infinite-v1-easy-0274` | 8×8 | 4 (N1/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 275 | `infinite-v1-medium-0275` | 5×5 | 4 (N2/E1/S1/W0) | 4 (Pull4/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 276 | `infinite-v1-hard-0276` | 7×7 | 5 (N1/E4/S0/W0) | 5 (Pull3/Push2) | 2 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 277 | `infinite-v1-easy-0277` | 5×5 | 5 (N1/E1/S1/W2) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 278 | `infinite-v1-medium-0278` | 8×8 | 4 (N2/E0/S2/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 279 | `infinite-v1-hard-0279` | 6×6 | 4 (N1/E0/S2/W1) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 280 | `infinite-v1-easy-0280` | 5×5 | 4 (N0/E2/S0/W2) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 281 | `infinite-v1-medium-0281` | 8×8 | 5 (N2/E2/S0/W1) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 282 | `infinite-v1-hard-0282` | 5×5 | 3 (N0/E1/S0/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 283 | `infinite-v1-easy-0283` | 5×5 | 5 (N2/E2/S1/W0) | 3 (Pull2/Push1) | 2 | `v5-easy` | `DEVELOPING` | 5 | 3 | 35 | `infinite-v1` |
| 284 | `infinite-v1-medium-0284` | 5×5 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 285 | `infinite-v1-hard-0285` | 5×5 | 4 (N1/E1/S2/W0) | 4 (Pull2/Push2) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 286 | `infinite-v1-easy-0286` | 5×5 | 5 (N1/E2/S2/W0) | 2 (Pull1/Push1) | 3 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 287 | `infinite-v1-medium-0287` | 5×5 | 5 (N2/E1/S1/W1) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 288 | `infinite-v1-hard-0288` | 8×8 | 4 (N0/E0/S1/W3) | 5 (Pull4/Push1) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 289 | `infinite-v1-easy-0289` | 5×5 | 3 (N0/E2/S1/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 290 | `infinite-v1-medium-0290` | 5×5 | 6 (N1/E0/S3/W2) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 90 | `infinite-v1` |
| 291 | `infinite-v1-hard-0291` | 6×6 | 5 (N2/E3/S0/W0) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 50 | `infinite-v1` |
| 292 | `infinite-v1-easy-0292` | 5×5 | 4 (N1/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 293 | `infinite-v1-medium-0293` | 6×6 | 5 (N1/E2/S0/W2) | 3 (Pull2/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 294 | `infinite-v1-hard-0294` | 6×6 | 4 (N0/E2/S1/W1) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 295 | `infinite-v1-easy-0295` | 8×8 | 5 (N2/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 296 | `infinite-v1-medium-0296` | 5×5 | 6 (N3/E0/S2/W1) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 6 | 3 | 13 | `infinite-v1` |
| 297 | `infinite-v1-hard-0297` | 5×5 | 4 (N2/E0/S2/W0) | 3 (Pull2/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 8 | `infinite-v1` |
| 298 | `infinite-v1-easy-0298` | 8×8 | 5 (N0/E1/S3/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 299 | `infinite-v1-medium-0299` | 6×6 | 4 (N2/E0/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 300 | `infinite-v1-hard-0300` | 5×5 | 4 (N2/E1/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 301 | `infinite-v1-easy-0301` | 4×4 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 302 | `infinite-v1-medium-0302` | 5×5 | 4 (N1/E3/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 303 | `infinite-v1-hard-0303` | 5×5 | 3 (N2/E1/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 304 | `infinite-v1-easy-0304` | 5×5 | 4 (N2/E0/S1/W1) | 3 (Pull3/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 4 | 12 | `infinite-v1` |
| 305 | `infinite-v1-medium-0305` | 6×6 | 6 (N1/E3/S0/W2) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 6 | 4 | 48 | `infinite-v1` |
| 306 | `infinite-v1-hard-0306` | 5×5 | 6 (N3/E2/S1/W0) | 4 (Pull4/Push0) | 2 | `v5-hard` | `ADVANCED` | 6 | 3 | 12 | `infinite-v1` |
| 307 | `infinite-v1-easy-0307` | 8×8 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 308 | `infinite-v1-medium-0308` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 309 | `infinite-v1-hard-0309` | 6×6 | 5 (N1/E2/S0/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 310 | `infinite-v1-easy-0310` | 8×8 | 3 (N1/E1/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 1 | 1 | `infinite-v1` |
| 311 | `infinite-v1-medium-0311` | 6×6 | 3 (N0/E2/S1/W0) | 3 (Pull3/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 312 | `infinite-v1-hard-0312` | 5×5 | 4 (N2/E0/S0/W2) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 313 | `infinite-v1-easy-0313` | 5×5 | 4 (N1/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 4 | 11 | `infinite-v1` |
| 314 | `infinite-v1-medium-0314` | 8×8 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 315 | `infinite-v1-hard-0315` | 6×6 | 5 (N0/E1/S3/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 20 | `infinite-v1` |
| 316 | `infinite-v1-easy-0316` | 5×5 | 4 (N1/E0/S2/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 317 | `infinite-v1-medium-0317` | 5×5 | 5 (N2/E1/S2/W0) | 4 (Pull4/Push0) | 4 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 318 | `infinite-v1-hard-0318` | 5×5 | 5 (N3/E0/S2/W0) | 2 (Pull2/Push0) | 7 | `v5-hard` | `ADVANCED` | 5 | 3 | 15 | `infinite-v1` |
| 319 | `infinite-v1-easy-0319` | 5×5 | 4 (N1/E2/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 320 | `infinite-v1-medium-0320` | 5×5 | 4 (N2/E0/S0/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 321 | `infinite-v1-hard-0321` | 5×5 | 4 (N2/E1/S0/W1) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 322 | `infinite-v1-easy-0322` | 5×5 | 4 (N0/E0/S2/W2) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 323 | `infinite-v1-medium-0323` | 5×5 | 4 (N1/E2/S1/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 324 | `infinite-v1-hard-0324` | 5×5 | 5 (N2/E2/S1/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 325 | `infinite-v1-easy-0325` | 8×8 | 5 (N2/E2/S1/W0) | 3 (Pull3/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 4 | 55 | `infinite-v1` |
| 326 | `infinite-v1-medium-0326` | 5×5 | 5 (N4/E1/S0/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 327 | `infinite-v1-hard-0327` | 5×5 | 4 (N2/E0/S1/W1) | 2 (Pull2/Push0) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 328 | `infinite-v1-easy-0328` | 4×4 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 329 | `infinite-v1-medium-0329` | 6×6 | 4 (N1/E1/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 330 | `infinite-v1-hard-0330` | 6×6 | 5 (N1/E2/S0/W2) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 331 | `infinite-v1-easy-0331` | 8×8 | 5 (N2/E0/S1/W2) | 3 (Pull2/Push1) | 1 | `v5-easy` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 332 | `infinite-v1-medium-0332` | 5×5 | 4 (N2/E1/S1/W0) | 4 (Pull4/Push0) | 3 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 333 | `infinite-v1-hard-0333` | 5×5 | 4 (N1/E0/S2/W1) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 334 | `infinite-v1-easy-0334` | 5×5 | 4 (N1/E1/S0/W2) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 335 | `infinite-v1-medium-0335` | 8×8 | 3 (N3/E0/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 336 | `infinite-v1-hard-0336` | 6×6 | 4 (N1/E3/S0/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 337 | `infinite-v1-easy-0337` | 4×4 | 4 (N3/E0/S1/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 338 | `infinite-v1-medium-0338` | 6×6 | 5 (N3/E1/S1/W0) | 4 (Pull3/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 339 | `infinite-v1-hard-0339` | 7×7 | 4 (N1/E3/S0/W0) | 3 (Pull2/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 340 | `infinite-v1-easy-0340` | 4×4 | 2 (N1/E0/S1/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 341 | `infinite-v1-medium-0341` | 5×5 | 4 (N0/E0/S1/W3) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 342 | `infinite-v1-hard-0342` | 5×5 | 4 (N1/E2/S1/W0) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 343 | `infinite-v1-easy-0343` | 5×5 | 3 (N1/E0/S1/W1) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 344 | `infinite-v1-medium-0344` | 5×5 | 4 (N2/E0/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 2 | 2 | `infinite-v1` |
| 345 | `infinite-v1-hard-0345` | 5×5 | 4 (N0/E3/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 8 | `infinite-v1` |
| 346 | `infinite-v1-easy-0346` | 8×8 | 5 (N2/E1/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 347 | `infinite-v1-medium-0347` | 5×5 | 4 (N0/E0/S3/W1) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 348 | `infinite-v1-hard-0348` | 6×6 | 3 (N3/E0/S0/W0) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 349 | `infinite-v1-easy-0349` | 5×5 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 350 | `infinite-v1-medium-0350` | 5×5 | 3 (N1/E2/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 351 | `infinite-v1-hard-0351` | 6×6 | 6 (N3/E1/S1/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 6 | 4 | 66 | `infinite-v1` |
| 352 | `infinite-v1-easy-0352` | 5×5 | 5 (N0/E1/S3/W1) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 353 | `infinite-v1-medium-0353` | 5×5 | 6 (N2/E0/S1/W3) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 60 | `infinite-v1` |
| 354 | `infinite-v1-hard-0354` | 5×5 | 4 (N1/E0/S2/W1) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 355 | `infinite-v1-easy-0355` | 5×5 | 3 (N0/E1/S0/W2) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 356 | `infinite-v1-medium-0356` | 5×5 | 3 (N1/E2/S0/W0) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 357 | `infinite-v1-hard-0357` | 6×6 | 5 (N2/E2/S0/W1) | 3 (Pull2/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 358 | `infinite-v1-easy-0358` | 5×5 | 3 (N1/E0/S2/W0) | 1 (Pull1/Push0) | 4 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 359 | `infinite-v1-medium-0359` | 6×6 | 5 (N1/E1/S1/W2) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 360 | `infinite-v1-hard-0360` | 5×5 | 3 (N1/E2/S0/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 361 | `infinite-v1-easy-0361` | 8×8 | 5 (N2/E1/S0/W2) | 2 (Pull1/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 362 | `infinite-v1-medium-0362` | 8×8 | 5 (N1/E3/S1/W0) | 4 (Pull3/Push1) | 3 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 363 | `infinite-v1-hard-0363` | 5×5 | 4 (N0/E1/S1/W2) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 364 | `infinite-v1-easy-0364` | 5×5 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 365 | `infinite-v1-medium-0365` | 5×5 | 4 (N2/E0/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 366 | `infinite-v1-hard-0366` | 5×5 | 4 (N3/E0/S1/W0) | 2 (Pull2/Push0) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 367 | `infinite-v1-easy-0367` | 8×8 | 5 (N2/E1/S1/W1) | 3 (Pull2/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 25 | `infinite-v1` |
| 368 | `infinite-v1-medium-0368` | 6×6 | 5 (N2/E2/S1/W0) | 3 (Pull2/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 369 | `infinite-v1-hard-0369` | 6×6 | 4 (N2/E1/S0/W1) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 370 | `infinite-v1-easy-0370` | 4×4 | 4 (N1/E1/S0/W2) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 11 | `infinite-v1` |
| 371 | `infinite-v1-medium-0371` | 5×5 | 5 (N4/E0/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 5 | 4 | 16 | `infinite-v1` |
| 372 | `infinite-v1-hard-0372` | 6×6 | 4 (N2/E0/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 373 | `infinite-v1-easy-0373` | 4×4 | 4 (N1/E1/S2/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 374 | `infinite-v1-medium-0374` | 6×6 | 4 (N2/E1/S0/W1) | 4 (Pull2/Push2) | 3 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 375 | `infinite-v1-hard-0375` | 6×6 | 5 (N1/E1/S3/W0) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 376 | `infinite-v1-easy-0376` | 5×5 | 4 (N1/E0/S1/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 377 | `infinite-v1-medium-0377` | 6×6 | 5 (N1/E3/S1/W0) | 3 (Pull2/Push1) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 378 | `infinite-v1-hard-0378` | 5×5 | 4 (N1/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 379 | `infinite-v1-easy-0379` | 4×4 | 2 (N1/E0/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 380 | `infinite-v1-medium-0380` | 5×5 | 5 (N1/E0/S1/W3) | 2 (Pull2/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 4 | 20 | `infinite-v1` |
| 381 | `infinite-v1-hard-0381` | 5×5 | 4 (N3/E1/S0/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 382 | `infinite-v1-easy-0382` | 8×8 | 5 (N1/E1/S3/W0) | 3 (Pull2/Push1) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 383 | `infinite-v1-medium-0383` | 5×5 | 5 (N1/E2/S2/W0) | 1 (Pull0/Push1) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 384 | `infinite-v1-hard-0384` | 5×5 | 5 (N1/E1/S2/W1) | 3 (Pull2/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 2 | 5 | `infinite-v1` |
| 385 | `infinite-v1-easy-0385` | 4×4 | 4 (N0/E2/S0/W2) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 4 | 12 | `infinite-v1` |
| 386 | `infinite-v1-medium-0386` | 5×5 | 4 (N1/E0/S1/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 1 | 3 | `infinite-v1` |
| 387 | `infinite-v1-hard-0387` | 7×7 | 5 (N2/E1/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 25 | `infinite-v1` |
| 388 | `infinite-v1-easy-0388` | 4×4 | 2 (N1/E1/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 389 | `infinite-v1-medium-0389` | 5×5 | 6 (N1/E1/S2/W2) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 6 | 2 | 6 | `infinite-v1` |
| 390 | `infinite-v1-hard-0390` | 5×5 | 5 (N2/E0/S1/W2) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 30 | `infinite-v1` |
| 391 | `infinite-v1-easy-0391` | 5×5 | 3 (N0/E1/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 392 | `infinite-v1-medium-0392` | 8×8 | 4 (N3/E1/S0/W0) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 393 | `infinite-v1-hard-0393` | 5×5 | 4 (N2/E0/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 4 | `infinite-v1` |
| 394 | `infinite-v1-easy-0394` | 5×5 | 3 (N0/E1/S2/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 395 | `infinite-v1-medium-0395` | 5×5 | 3 (N1/E0/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 396 | `infinite-v1-hard-0396` | 5×5 | 4 (N0/E2/S2/W0) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 397 | `infinite-v1-easy-0397` | 5×5 | 5 (N1/E2/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-easy` | `DEVELOPING` | 5 | 1 | 4 | `infinite-v1` |
| 398 | `infinite-v1-medium-0398` | 5×5 | 3 (N0/E2/S0/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 399 | `infinite-v1-hard-0399` | 5×5 | 3 (N1/E1/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 400 | `infinite-v1-easy-0400` | 4×4 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 401 | `infinite-v1-medium-0401` | 5×5 | 4 (N2/E1/S0/W1) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 402 | `infinite-v1-hard-0402` | 5×5 | 5 (N2/E1/S2/W0) | 3 (Pull2/Push1) | 2 | `v5-hard` | `ADVANCED` | 5 | 3 | 25 | `infinite-v1` |
| 403 | `infinite-v1-easy-0403` | 4×4 | 4 (N1/E1/S1/W1) | 3 (Pull1/Push2) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 404 | `infinite-v1-medium-0404` | 5×5 | 5 (N2/E1/S2/W0) | 2 (Pull1/Push1) | 5 | `v5-medium` | `DEVELOPING` | 5 | 2 | 10 | `infinite-v1` |
| 405 | `infinite-v1-hard-0405` | 5×5 | 5 (N2/E1/S0/W2) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 406 | `infinite-v1-easy-0406` | 5×5 | 4 (N2/E1/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 407 | `infinite-v1-medium-0407` | 8×8 | 5 (N2/E2/S0/W1) | 3 (Pull3/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 408 | `infinite-v1-hard-0408` | 5×5 | 4 (N1/E0/S2/W1) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 409 | `infinite-v1-easy-0409` | 5×5 | 3 (N0/E2/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 410 | `infinite-v1-medium-0410` | 5×5 | 4 (N1/E1/S2/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 411 | `infinite-v1-hard-0411` | 5×5 | 6 (N3/E0/S3/W0) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 18 | `infinite-v1` |
| 412 | `infinite-v1-easy-0412` | 8×8 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 413 | `infinite-v1-medium-0413` | 5×5 | 5 (N2/E2/S1/W0) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 414 | `infinite-v1-hard-0414` | 5×5 | 4 (N1/E0/S3/W0) | 4 (Pull4/Push0) | 2 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 415 | `infinite-v1-easy-0415` | 5×5 | 4 (N0/E2/S1/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 416 | `infinite-v1-medium-0416` | 8×8 | 5 (N1/E1/S1/W2) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 2 | 10 | `infinite-v1` |
| 417 | `infinite-v1-hard-0417` | 5×5 | 6 (N1/E2/S1/W2) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 42 | `infinite-v1` |
| 418 | `infinite-v1-easy-0418` | 5×5 | 5 (N3/E2/S0/W0) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 2 | 15 | `infinite-v1` |
| 419 | `infinite-v1-medium-0419` | 6×6 | 3 (N1/E1/S0/W1) | 3 (Pull3/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 420 | `infinite-v1-hard-0420` | 5×5 | 5 (N3/E1/S0/W1) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 15 | `infinite-v1` |
| 421 | `infinite-v1-easy-0421` | 8×8 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 422 | `infinite-v1-medium-0422` | 5×5 | 5 (N2/E2/S1/W0) | 2 (Pull2/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 4 | 46 | `infinite-v1` |
| 423 | `infinite-v1-hard-0423` | 5×5 | 4 (N0/E4/S0/W0) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 8 | `infinite-v1` |
| 424 | `infinite-v1-easy-0424` | 5×5 | 4 (N1/E1/S0/W2) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 425 | `infinite-v1-medium-0425` | 5×5 | 5 (N0/E1/S4/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 426 | `infinite-v1-hard-0426` | 5×5 | 5 (N1/E3/S1/W0) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 25 | `infinite-v1` |
| 427 | `infinite-v1-easy-0427` | 4×4 | 3 (N0/E0/S0/W3) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 428 | `infinite-v1-medium-0428` | 5×5 | 4 (N0/E3/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 429 | `infinite-v1-hard-0429` | 5×5 | 6 (N1/E3/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 6 | 3 | 18 | `infinite-v1` |
| 430 | `infinite-v1-easy-0430` | 8×8 | 5 (N4/E1/S0/W0) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 5 | 60 | `infinite-v1` |
| 431 | `infinite-v1-medium-0431` | 5×5 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 432 | `infinite-v1-hard-0432` | 6×6 | 5 (N0/E1/S1/W3) | 4 (Pull3/Push1) | 4 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 433 | `infinite-v1-easy-0433` | 5×5 | 3 (N1/E1/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 434 | `infinite-v1-medium-0434` | 6×6 | 4 (N1/E0/S2/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 435 | `infinite-v1-hard-0435` | 7×7 | 4 (N0/E1/S1/W2) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 436 | `infinite-v1-easy-0436` | 5×5 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 437 | `infinite-v1-medium-0437` | 5×5 | 5 (N1/E0/S3/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 35 | `infinite-v1` |
| 438 | `infinite-v1-hard-0438` | 6×6 | 4 (N1/E1/S1/W1) | 5 (Pull4/Push1) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 439 | `infinite-v1-easy-0439` | 4×4 | 4 (N1/E2/S1/W0) | 2 (Pull2/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 440 | `infinite-v1-medium-0440` | 6×6 | 6 (N2/E4/S0/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 6 | 4 | 36 | `infinite-v1` |
| 441 | `infinite-v1-hard-0441` | 5×5 | 4 (N1/E1/S1/W1) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 8 | `infinite-v1` |
| 442 | `infinite-v1-easy-0442` | 8×8 | 5 (N4/E0/S0/W1) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 443 | `infinite-v1-medium-0443` | 5×5 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 444 | `infinite-v1-hard-0444` | 6×6 | 4 (N1/E1/S1/W1) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 445 | `infinite-v1-easy-0445` | 5×5 | 5 (N1/E2/S0/W2) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 446 | `infinite-v1-medium-0446` | 8×8 | 5 (N2/E0/S1/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 447 | `infinite-v1-hard-0447` | 5×5 | 3 (N1/E1/S0/W1) | 2 (Pull1/Push1) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 448 | `infinite-v1-easy-0448` | 5×5 | 5 (N1/E1/S3/W0) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 449 | `infinite-v1-medium-0449` | 8×8 | 5 (N1/E3/S0/W1) | 2 (Pull0/Push2) | 3 | `v5-medium` | `DEVELOPING` | 5 | 2 | 11 | `infinite-v1` |
| 450 | `infinite-v1-hard-0450` | 5×5 | 4 (N0/E1/S1/W2) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 451 | `infinite-v1-easy-0451` | 4×4 | 4 (N1/E2/S0/W1) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 452 | `infinite-v1-medium-0452` | 6×6 | 4 (N0/E1/S1/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 453 | `infinite-v1-hard-0453` | 6×6 | 5 (N1/E3/S0/W1) | 4 (Pull2/Push2) | 3 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 454 | `infinite-v1-easy-0454` | 5×5 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 455 | `infinite-v1-medium-0455` | 5×5 | 3 (N0/E2/S1/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 456 | `infinite-v1-hard-0456` | 8×8 | 5 (N1/E2/S0/W2) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 40 | `infinite-v1` |
| 457 | `infinite-v1-easy-0457` | 5×5 | 4 (N1/E2/S1/W0) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 458 | `infinite-v1-medium-0458` | 5×5 | 4 (N1/E2/S0/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 459 | `infinite-v1-hard-0459` | 6×6 | 4 (N2/E0/S2/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 460 | `infinite-v1-easy-0460` | 4×4 | 3 (N0/E0/S1/W2) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 461 | `infinite-v1-medium-0461` | 5×5 | 5 (N2/E1/S2/W0) | 4 (Pull4/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 462 | `infinite-v1-hard-0462` | 5×5 | 4 (N0/E2/S1/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 463 | `infinite-v1-easy-0463` | 5×5 | 3 (N1/E1/S1/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 464 | `infinite-v1-medium-0464` | 6×6 | 5 (N1/E1/S1/W2) | 3 (Pull3/Push0) | 3 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 465 | `infinite-v1-hard-0465` | 5×5 | 6 (N2/E1/S2/W1) | 3 (Pull1/Push2) | 1 | `v5-hard` | `ADVANCED` | 6 | 2 | 5 | `infinite-v1` |
| 466 | `infinite-v1-easy-0466` | 5×5 | 5 (N3/E2/S0/W0) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 467 | `infinite-v1-medium-0467` | 6×6 | 4 (N0/E0/S3/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 468 | `infinite-v1-hard-0468` | 5×5 | 4 (N1/E2/S1/W0) | 4 (Pull2/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 469 | `infinite-v1-easy-0469` | 5×5 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 470 | `infinite-v1-medium-0470` | 6×6 | 4 (N1/E1/S0/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 471 | `infinite-v1-hard-0471` | 5×5 | 5 (N2/E2/S1/W0) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 472 | `infinite-v1-easy-0472` | 5×5 | 3 (N0/E1/S1/W1) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 473 | `infinite-v1-medium-0473` | 5×5 | 5 (N0/E2/S1/W2) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 474 | `infinite-v1-hard-0474` | 6×6 | 3 (N0/E1/S2/W0) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 475 | `infinite-v1-easy-0475` | 5×5 | 5 (N2/E1/S2/W0) | 3 (Pull3/Push0) | 0 | `v5-easy` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 476 | `infinite-v1-medium-0476` | 5×5 | 4 (N2/E0/S2/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 477 | `infinite-v1-hard-0477` | 5×5 | 4 (N0/E3/S1/W0) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 478 | `infinite-v1-easy-0478` | 4×4 | 3 (N1/E0/S2/W0) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 479 | `infinite-v1-medium-0479` | 5×5 | 5 (N1/E2/S0/W2) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 480 | `infinite-v1-hard-0480` | 5×5 | 4 (N2/E0/S2/W0) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 481 | `infinite-v1-easy-0481` | 4×4 | 3 (N0/E1/S0/W2) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 482 | `infinite-v1-medium-0482` | 5×5 | 4 (N1/E1/S1/W1) | 2 (Pull2/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 483 | `infinite-v1-hard-0483` | 6×6 | 4 (N2/E0/S1/W1) | 5 (Pull4/Push1) | 3 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 484 | `infinite-v1-easy-0484` | 5×5 | 5 (N1/E3/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 5 | 5 | 40 | `infinite-v1` |
| 485 | `infinite-v1-medium-0485` | 5×5 | 5 (N0/E3/S1/W1) | 1 (Pull1/Push0) | 4 | `v5-medium` | `DEVELOPING` | 5 | 4 | 36 | `infinite-v1` |
| 486 | `infinite-v1-hard-0486` | 5×5 | 6 (N1/E1/S3/W1) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 6 | 3 | 42 | `infinite-v1` |
| 487 | `infinite-v1-easy-0487` | 8×8 | 5 (N1/E4/S0/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 10 | `infinite-v1` |
| 488 | `infinite-v1-medium-0488` | 5×5 | 4 (N1/E1/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 489 | `infinite-v1-hard-0489` | 5×5 | 5 (N2/E2/S0/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 490 | `infinite-v1-easy-0490` | 5×5 | 4 (N0/E3/S0/W1) | 1 (Pull0/Push1) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 491 | `infinite-v1-medium-0491` | 5×5 | 5 (N1/E2/S1/W1) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 5 | 3 | 6 | `infinite-v1` |
| 492 | `infinite-v1-hard-0492` | 6×6 | 4 (N0/E2/S1/W1) | 4 (Pull4/Push0) | 2 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 493 | `infinite-v1-easy-0493` | 8×8 | 5 (N3/E1/S0/W1) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 4 | 60 | `infinite-v1` |
| 494 | `infinite-v1-medium-0494` | 5×5 | 4 (N0/E4/S0/W0) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 495 | `infinite-v1-hard-0495` | 5×5 | 4 (N1/E2/S0/W1) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 496 | `infinite-v1-easy-0496` | 4×4 | 3 (N1/E1/S1/W0) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 497 | `infinite-v1-medium-0497` | 6×6 | 4 (N1/E2/S0/W1) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 498 | `infinite-v1-hard-0498` | 8×8 | 6 (N2/E2/S1/W1) | 2 (Pull1/Push1) | 3 | `v5-hard` | `ADVANCED` | 6 | 2 | 6 | `infinite-v1` |
| 499 | `infinite-v1-easy-0499` | 5×5 | 5 (N3/E2/S0/W0) | 2 (Pull2/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 3 | 25 | `infinite-v1` |
| 500 | `infinite-v1-medium-0500` | 5×5 | 5 (N1/E0/S2/W2) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 2 | 5 | `infinite-v1` |
| 501 | `infinite-v1-hard-0501` | 5×5 | 4 (N0/E2/S1/W1) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 8 | `infinite-v1` |
| 502 | `infinite-v1-easy-0502` | 4×4 | 2 (N0/E1/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 503 | `infinite-v1-medium-0503` | 5×5 | 3 (N2/E0/S1/W0) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 504 | `infinite-v1-hard-0504` | 7×7 | 4 (N1/E1/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 505 | `infinite-v1-easy-0505` | 8×8 | 5 (N1/E2/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 5 | 55 | `infinite-v1` |
| 506 | `infinite-v1-medium-0506` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 507 | `infinite-v1-hard-0507` | 8×8 | 5 (N0/E0/S4/W1) | 3 (Pull3/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 508 | `infinite-v1-easy-0508` | 5×5 | 5 (N1/E2/S1/W1) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 3 | 9 | `infinite-v1` |
| 509 | `infinite-v1-medium-0509` | 5×5 | 5 (N2/E2/S0/W1) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 510 | `infinite-v1-hard-0510` | 5×5 | 3 (N1/E0/S1/W1) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 511 | `infinite-v1-easy-0511` | 5×5 | 2 (N0/E0/S1/W1) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 512 | `infinite-v1-medium-0512` | 6×6 | 4 (N2/E1/S0/W1) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 513 | `infinite-v1-hard-0513` | 7×7 | 6 (N3/E1/S1/W1) | 5 (Pull4/Push1) | 5 | `v5-hard` | `ADVANCED` | 6 | 3 | 40 | `infinite-v1` |
| 514 | `infinite-v1-easy-0514` | 5×5 | 4 (N2/E0/S0/W2) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 5 | `infinite-v1` |
| 515 | `infinite-v1-medium-0515` | 8×8 | 5 (N3/E0/S1/W1) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 516 | `infinite-v1-hard-0516` | 5×5 | 3 (N1/E1/S0/W1) | 3 (Pull2/Push1) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 517 | `infinite-v1-easy-0517` | 4×4 | 2 (N0/E1/S1/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 518 | `infinite-v1-medium-0518` | 5×5 | 4 (N1/E2/S1/W0) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 519 | `infinite-v1-hard-0519` | 5×5 | 4 (N2/E0/S2/W0) | 1 (Pull1/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 520 | `infinite-v1-easy-0520` | 4×4 | 3 (N1/E2/S0/W0) | 2 (Pull1/Push1) | 2 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 521 | `infinite-v1-medium-0521` | 5×5 | 4 (N2/E0/S1/W1) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 522 | `infinite-v1-hard-0522` | 5×5 | 5 (N3/E0/S2/W0) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 40 | `infinite-v1` |
| 523 | `infinite-v1-easy-0523` | 8×8 | 5 (N2/E1/S2/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 524 | `infinite-v1-medium-0524` | 8×8 | 5 (N0/E1/S1/W3) | 2 (Pull1/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 525 | `infinite-v1-hard-0525` | 6×6 | 4 (N1/E0/S0/W3) | 5 (Pull3/Push2) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 526 | `infinite-v1-easy-0526` | 5×5 | 5 (N1/E1/S1/W2) | 3 (Pull3/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 527 | `infinite-v1-medium-0527` | 5×5 | 5 (N3/E1/S0/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 528 | `infinite-v1-hard-0528` | 7×7 | 4 (N0/E0/S3/W1) | 5 (Pull5/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 529 | `infinite-v1-easy-0529` | 5×5 | 4 (N2/E0/S0/W2) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 530 | `infinite-v1-medium-0530` | 6×6 | 5 (N3/E2/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 13 | `infinite-v1` |
| 531 | `infinite-v1-hard-0531` | 7×7 | 4 (N2/E1/S1/W0) | 2 (Pull2/Push0) | 4 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 532 | `infinite-v1-easy-0532` | 8×8 | 4 (N0/E3/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 533 | `infinite-v1-medium-0533` | 5×5 | 6 (N1/E3/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 30 | `infinite-v1` |
| 534 | `infinite-v1-hard-0534` | 5×5 | 5 (N1/E1/S1/W2) | 5 (Pull4/Push1) | 3 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 535 | `infinite-v1-easy-0535` | 5×5 | 5 (N3/E1/S0/W1) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 5 | 3 | 10 | `infinite-v1` |
| 536 | `infinite-v1-medium-0536` | 8×8 | 4 (N4/E0/S0/W0) | 1 (Pull1/Push0) | 6 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 537 | `infinite-v1-hard-0537` | 5×5 | 3 (N2/E0/S0/W1) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 538 | `infinite-v1-easy-0538` | 8×8 | 4 (N2/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 539 | `infinite-v1-medium-0539` | 6×6 | 4 (N2/E1/S1/W0) | 4 (Pull2/Push2) | 4 | `v5-medium` | `DEVELOPING` | 4 | 2 | 3 | `infinite-v1` |
| 540 | `infinite-v1-hard-0540` | 5×5 | 5 (N2/E2/S1/W0) | 4 (Pull3/Push1) | 2 | `v5-hard` | `ADVANCED` | 5 | 3 | 15 | `infinite-v1` |
| 541 | `infinite-v1-easy-0541` | 4×4 | 4 (N3/E0/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 3 | 7 | `infinite-v1` |
| 542 | `infinite-v1-medium-0542` | 6×6 | 4 (N2/E1/S0/W1) | 4 (Pull3/Push1) | 4 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 543 | `infinite-v1-hard-0543` | 5×5 | 5 (N3/E1/S1/W0) | 2 (Pull1/Push1) | 3 | `v5-hard` | `ADVANCED` | 5 | 2 | 10 | `infinite-v1` |
| 544 | `infinite-v1-easy-0544` | 5×5 | 4 (N3/E0/S1/W0) | 1 (Pull1/Push0) | 2 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 545 | `infinite-v1-medium-0545` | 6×6 | 5 (N3/E0/S1/W1) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 546 | `infinite-v1-hard-0546` | 6×6 | 4 (N2/E2/S0/W0) | 2 (Pull2/Push0) | 6 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 547 | `infinite-v1-easy-0547` | 8×8 | 3 (N2/E0/S1/W0) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 548 | `infinite-v1-medium-0548` | 8×8 | 4 (N1/E2/S1/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 549 | `infinite-v1-hard-0549` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull3/Push0) | 6 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 550 | `infinite-v1-easy-0550` | 8×8 | 5 (N1/E3/S0/W1) | 1 (Pull1/Push0) | 4 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 551 | `infinite-v1-medium-0551` | 5×5 | 6 (N4/E0/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 42 | `infinite-v1` |
| 552 | `infinite-v1-hard-0552` | 5×5 | 6 (N2/E3/S1/W0) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 6 | 5 | 43 | `infinite-v1` |
| 553 | `infinite-v1-easy-0553` | 8×8 | 5 (N2/E1/S1/W1) | 3 (Pull3/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 20 | `infinite-v1` |
| 554 | `infinite-v1-medium-0554` | 6×6 | 5 (N3/E0/S0/W2) | 1 (Pull0/Push1) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 10 | `infinite-v1` |
| 555 | `infinite-v1-hard-0555` | 5×5 | 4 (N0/E2/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 556 | `infinite-v1-easy-0556` | 8×8 | 5 (N1/E1/S1/W2) | 3 (Pull2/Push1) | 2 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 557 | `infinite-v1-medium-0557` | 6×6 | 6 (N2/E1/S3/W0) | 4 (Pull2/Push2) | 1 | `v5-medium` | `DEVELOPING` | 6 | 3 | 30 | `infinite-v1` |
| 558 | `infinite-v1-hard-0558` | 8×8 | 5 (N3/E1/S1/W0) | 2 (Pull2/Push0) | 4 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 559 | `infinite-v1-easy-0559` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 560 | `infinite-v1-medium-0560` | 5×5 | 5 (N0/E2/S1/W2) | 4 (Pull3/Push1) | 2 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 561 | `infinite-v1-hard-0561` | 5×5 | 3 (N1/E2/S0/W0) | 4 (Pull3/Push1) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 562 | `infinite-v1-easy-0562` | 4×4 | 3 (N1/E2/S0/W0) | 3 (Pull2/Push1) | 0 | `v5-easy` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 563 | `infinite-v1-medium-0563` | 5×5 | 4 (N1/E1/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 564 | `infinite-v1-hard-0564` | 5×5 | 5 (N1/E2/S1/W1) | 3 (Pull2/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 2 | 11 | `infinite-v1` |
| 565 | `infinite-v1-easy-0565` | 5×5 | 5 (N2/E1/S2/W0) | 2 (Pull2/Push0) | 2 | `v5-easy` | `DEVELOPING` | 5 | 4 | 45 | `infinite-v1` |
| 566 | `infinite-v1-medium-0566` | 8×8 | 6 (N3/E2/S0/W1) | 1 (Pull0/Push1) | 7 | `v5-medium` | `DEVELOPING` | 6 | 2 | 6 | `infinite-v1` |
| 567 | `infinite-v1-hard-0567` | 5×5 | 4 (N1/E0/S2/W1) | 4 (Pull4/Push0) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 568 | `infinite-v1-easy-0568` | 4×4 | 5 (N3/E0/S1/W1) | 2 (Pull2/Push0) | 0 | `v5-easy` | `DEVELOPING` | 5 | 2 | 5 | `infinite-v1` |
| 569 | `infinite-v1-medium-0569` | 6×6 | 4 (N3/E0/S1/W0) | 2 (Pull2/Push0) | 2 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 570 | `infinite-v1-hard-0570` | 5×5 | 5 (N1/E0/S2/W2) | 2 (Pull2/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 24 | `infinite-v1` |
| 571 | `infinite-v1-easy-0571` | 8×8 | 5 (N0/E1/S3/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 15 | `infinite-v1` |
| 572 | `infinite-v1-medium-0572` | 6×6 | 6 (N3/E2/S0/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 6 | 3 | 18 | `infinite-v1` |
| 573 | `infinite-v1-hard-0573` | 5×5 | 4 (N1/E0/S1/W2) | 5 (Pull4/Push1) | 2 | `v5-hard` | `ADVANCED` | 4 | 2 | 6 | `infinite-v1` |
| 574 | `infinite-v1-easy-0574` | 4×4 | 2 (N1/E1/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 2 | 1 | 1 | `infinite-v1` |
| 575 | `infinite-v1-medium-0575` | 5×5 | 4 (N0/E0/S4/W0) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 576 | `infinite-v1-hard-0576` | 5×5 | 3 (N1/E2/S0/W0) | 3 (Pull3/Push0) | 1 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 577 | `infinite-v1-easy-0577` | 8×8 | 4 (N2/E1/S1/W0) | 2 (Pull2/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 4 | 12 | `infinite-v1` |
| 578 | `infinite-v1-medium-0578` | 5×5 | 4 (N1/E0/S3/W0) | 2 (Pull2/Push0) | 5 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 579 | `infinite-v1-hard-0579` | 5×5 | 4 (N1/E1/S1/W1) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 3 | 12 | `infinite-v1` |
| 580 | `infinite-v1-easy-0580` | 8×8 | 4 (N1/E2/S0/W1) | 3 (Pull3/Push0) | 5 | `v5-easy` | `DEVELOPING` | 4 | 3 | 8 | `infinite-v1` |
| 581 | `infinite-v1-medium-0581` | 5×5 | 4 (N1/E0/S3/W0) | 2 (Pull2/Push0) | 1 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 582 | `infinite-v1-hard-0582` | 5×5 | 5 (N2/E1/S2/W0) | 5 (Pull4/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 40 | `infinite-v1` |
| 583 | `infinite-v1-easy-0583` | 8×8 | 5 (N2/E2/S1/W0) | 2 (Pull2/Push0) | 4 | `v5-easy` | `DEVELOPING` | 5 | 4 | 40 | `infinite-v1` |
| 584 | `infinite-v1-medium-0584` | 8×8 | 5 (N2/E3/S0/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 585 | `infinite-v1-hard-0585` | 8×8 | 5 (N2/E1/S2/W0) | 2 (Pull2/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 4 | 50 | `infinite-v1` |
| 586 | `infinite-v1-easy-0586` | 5×5 | 4 (N1/E0/S2/W1) | 3 (Pull3/Push0) | 1 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 587 | `infinite-v1-medium-0587` | 5×5 | 4 (N0/E0/S2/W2) | 2 (Pull1/Push1) | 2 | `v5-medium` | `DEVELOPING` | 4 | 2 | 6 | `infinite-v1` |
| 588 | `infinite-v1-hard-0588` | 6×6 | 4 (N2/E1/S0/W1) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 589 | `infinite-v1-easy-0589` | 8×8 | 5 (N2/E2/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 590 | `infinite-v1-medium-0590` | 6×6 | 5 (N3/E0/S2/W0) | 2 (Pull1/Push1) | 1 | `v5-medium` | `DEVELOPING` | 5 | 3 | 30 | `infinite-v1` |
| 591 | `infinite-v1-hard-0591` | 5×5 | 5 (N2/E1/S0/W2) | 3 (Pull3/Push0) | 1 | `v5-hard` | `ADVANCED` | 5 | 3 | 20 | `infinite-v1` |
| 592 | `infinite-v1-easy-0592` | 5×5 | 4 (N2/E2/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 2 | 4 | `infinite-v1` |
| 593 | `infinite-v1-medium-0593` | 6×6 | 3 (N1/E1/S0/W1) | 1 (Pull1/Push0) | 2 | `v5-medium` | `DEVELOPING` | 3 | 2 | 3 | `infinite-v1` |
| 594 | `infinite-v1-hard-0594` | 5×5 | 4 (N2/E1/S1/W0) | 2 (Pull1/Push1) | 1 | `v5-hard` | `ADVANCED` | 4 | 2 | 4 | `infinite-v1` |
| 595 | `infinite-v1-easy-0595` | 8×8 | 4 (N2/E0/S0/W2) | 3 (Pull3/Push0) | 3 | `v5-easy` | `DEVELOPING` | 4 | 3 | 12 | `infinite-v1` |
| 596 | `infinite-v1-medium-0596` | 5×5 | 3 (N1/E1/S0/W1) | 2 (Pull2/Push0) | 4 | `v5-medium` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 597 | `infinite-v1-hard-0597` | 5×5 | 5 (N2/E1/S2/W0) | 4 (Pull3/Push1) | 1 | `v5-hard` | `ADVANCED` | 5 | 4 | 45 | `infinite-v1` |
| 598 | `infinite-v1-easy-0598` | 5×5 | 3 (N0/E2/S0/W1) | 1 (Pull1/Push0) | 5 | `v5-easy` | `DEVELOPING` | 3 | 1 | 2 | `infinite-v1` |
| 599 | `infinite-v1-medium-0599` | 8×8 | 5 (N3/E2/S0/W0) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 5 | 3 | 40 | `infinite-v1` |
| 600 | `infinite-v1-hard-0600` | 6×6 | 6 (N2/E1/S1/W2) | 1 (Pull1/Push0) | 7 | `v5-medium` | `DEVELOPING` | 6 | 4 | 90 | `infinite-v1` |
| 601 | `infinite-v1-expert-0601` | 8×8 | 10 (N6/E0/S4/W0) | 11 (Pull11/Push0) | 10 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 602 | `infinite-v1-expert-0602` | 8×8 | 10 (N0/E4/S0/W6) | 11 (Pull11/Push0) | 12 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 603 | `infinite-v1-expert-0603` | 8×8 | 10 (N4/E0/S6/W0) | 11 (Pull11/Push0) | 9 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 604 | `infinite-v1-expert-0604` | 8×8 | 10 (N0/E6/S0/W4) | 11 (Pull11/Push0) | 10 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 605 | `infinite-v1-expert-0605` | 8×8 | 10 (N6/E0/S4/W0) | 11 (Pull11/Push0) | 9 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 606 | `infinite-v1-expert-0606` | 8×8 | 10 (N0/E4/S0/W6) | 11 (Pull11/Push0) | 11 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 607 | `infinite-v1-expert-0607` | 8×8 | 10 (N4/E0/S6/W0) | 11 (Pull11/Push0) | 11 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 608 | `infinite-v1-expert-0608` | 8×8 | 10 (N0/E6/S0/W4) | 11 (Pull11/Push0) | 9 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 609 | `infinite-v1-expert-0609` | 8×8 | 10 (N6/E0/S4/W0) | 11 (Pull11/Push0) | 9 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 610 | `infinite-v1-expert-0610` | 8×8 | 10 (N0/E4/S0/W6) | 11 (Pull11/Push0) | 11 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 611 | `infinite-v1-expert-0611` | 8×8 | 10 (N4/E0/S6/W0) | 11 (Pull11/Push0) | 10 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 612 | `infinite-v1-expert-0612` | 8×8 | 10 (N0/E6/S0/W4) | 11 (Pull11/Push0) | 11 | `v5-d2.1-expert` | `ADVANCED` | 10 | 4 | 14312 | `infinite-v1` |
| 613 | `infinite-v1-master-0613` | 8×8 | 10 (N6/E0/S4/W0) | 10 (Pull10/Push0) | 9 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 614 | `infinite-v1-master-0614` | 8×8 | 10 (N0/E4/S0/W6) | 10 (Pull10/Push0) | 11 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 615 | `infinite-v1-master-0615` | 8×8 | 10 (N4/E0/S6/W0) | 10 (Pull10/Push0) | 11 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 616 | `infinite-v1-master-0616` | 8×8 | 10 (N0/E6/S0/W4) | 10 (Pull10/Push0) | 11 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 617 | `infinite-v1-master-0617` | 8×8 | 10 (N6/E0/S4/W0) | 10 (Pull10/Push0) | 11 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 618 | `infinite-v1-master-0618` | 8×8 | 10 (N0/E4/S0/W6) | 10 (Pull10/Push0) | 13 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 619 | `infinite-v1-master-0619` | 8×8 | 10 (N4/E0/S6/W0) | 10 (Pull10/Push0) | 10 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 620 | `infinite-v1-master-0620` | 8×8 | 10 (N0/E6/S0/W4) | 10 (Pull10/Push0) | 11 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 621 | `infinite-v1-master-0621` | 8×8 | 10 (N6/E0/S4/W0) | 10 (Pull10/Push0) | 10 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 622 | `infinite-v1-master-0622` | 8×8 | 10 (N0/E4/S0/W6) | 10 (Pull10/Push0) | 12 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 623 | `infinite-v1-master-0623` | 8×8 | 10 (N4/E0/S6/W0) | 10 (Pull10/Push0) | 12 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
| 624 | `infinite-v1-master-0624` | 8×8 | 10 (N0/E6/S0/W4) | 10 (Pull10/Push0) | 10 | `v5-d2.1-master` | `ADVANCED` | 10 | 3 | 477 | `infinite-v1` |
<!-- END GENERATED INFINITE INVENTORY -->

### 25.3 Production Daily fallback V1 — 7 levels

Source: `docs/Magnetrail_Daily_Fallbacks_v1.json`, SHA-256
`3f21a633fa1e51d29938b830fe567103a52354e367d3177c20b8196c881156c8`.

| # | ID | Grid | A (N/E/S/W) | M (Pull/Push) | W | Candidate profile | UI band | Par | Open | Solns | Pack |
|---:|---|---|---|---|---:|---|---|---|---:|---:|---|
<!-- BEGIN GENERATED DAILY INVENTORY -->
| 1 | `daily-fallback-01` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 4 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 2 | `daily-fallback-02` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 2 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 3 | `daily-fallback-03` | 6×6 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 4 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 4 | `daily-fallback-04` | 6×6 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 5 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 5 | `daily-fallback-05` | 6×6 | 3 (N1/E1/S1/W0) | 1 (Pull1/Push0) | 5 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 6 | `daily-fallback-06` | 6×6 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 3 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
| 7 | `daily-fallback-07` | 6×6 | 3 (N1/E0/S1/W1) | 1 (Pull1/Push0) | 3 | `daily-v1` | `DEVELOPING` | 3 | 2 | 2 | `daily-fallback` |
<!-- END GENERATED DAILY INVENTORY -->

### 25.4 Debug/staging V11 pilot — 53 levels

Source: `docs/content/v11_pilot/V11_PILOT_CATALOG.json`, SHA-256
`035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184`.

| # | ID | Grid | A (N/E/S/W) | M (Pull/Push) | W | Candidate profile | UI band | Par | Open | Solns | Pack |
|---:|---|---|---|---|---:|---|---|---:|---:|---:|---|
<!-- BEGIN GENERATED V11 INVENTORY -->
| 1 | `v11-pilot-001` | 5×5 | 5 (N3/E0/S2/W0) | 3 (Pull2/Push1) | 5 | `v5-pilot-v11-easy` | `DEVELOPING` | 5 | 3 | 40 | `v11-human-difficulty-pilot` |
| 2 | `v11-pilot-002` | 4×4 | 3 (N3/E0/S0/W0) | 1 (Pull1/Push0) | 3 | `v5-pilot-v11-easy` | `DEVELOPING` | 3 | 1 | 2 | `v11-human-difficulty-pilot` |
| 3 | `v11-pilot-003` | 5×5 | 5 (N1/E0/S4/W0) | 3 (Pull3/Push0) | 3 | `v5-pilot-v11-easy` | `DEVELOPING` | 5 | 3 | 30 | `v11-human-difficulty-pilot` |
| 4 | `v11-pilot-004` | 5×5 | 4 (N2/E0/S2/W0) | 2 (Pull2/Push0) | 4 | `v5-pilot-v11-easy` | `DEVELOPING` | 4 | 2 | 3 | `v11-human-difficulty-pilot` |
| 5 | `v11-pilot-005` | 4×4 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 2 | `v5-pilot-v11-easy` | `DEVELOPING` | 3 | 1 | 1 | `v11-human-difficulty-pilot` |
| 6 | `v11-pilot-006` | 5×5 | 4 (N3/E0/S1/W0) | 2 (Pull1/Push1) | 5 | `v5-pilot-v11-easy` | `DEVELOPING` | 4 | 2 | 4 | `v11-human-difficulty-pilot` |
| 7 | `v11-pilot-007` | 4×4 | 3 (N2/E1/S0/W0) | 1 (Pull1/Push0) | 1 | `v5-pilot-v11-easy` | `DEVELOPING` | 3 | 1 | 2 | `v11-human-difficulty-pilot` |
| 8 | `v11-pilot-008` | 5×5 | 5 (N3/E1/S0/W1) | 3 (Pull2/Push1) | 3 | `v5-pilot-v11-easy` | `DEVELOPING` | 5 | 4 | 40 | `v11-human-difficulty-pilot` |
| 9 | `v11-pilot-009` | 5×5 | 5 (N2/E2/S0/W1) | 3 (Pull2/Push1) | 3 | `v5-pilot-v11-easy` | `DEVELOPING` | 5 | 2 | 10 | `v11-human-difficulty-pilot` |
| 10 | `v11-pilot-010` | 5×5 | 4 (N0/E2/S0/W2) | 3 (Pull2/Push1) | 3 | `v5-pilot-v11-easy` | `DEVELOPING` | 4 | 2 | 8 | `v11-human-difficulty-pilot` |
| 11 | `v11-pilot-011` | 6×6 | 6 (N1/E3/S0/W2) | 4 (Pull3/Push1) | 6 | `v5-pilot-v11-medium` | `DEVELOPING` | 6 | 1 | 8 | `v11-human-difficulty-pilot` |
| 12 | `v11-pilot-012` | 5×5 | 4 (N2/E0/S1/W1) | 2 (Pull1/Push1) | 4 | `v5-pilot-v11-medium` | `DEVELOPING` | 4 | 1 | 1 | `v11-human-difficulty-pilot` |
| 13 | `v11-pilot-013` | 6×6 | 7 (N4/E2/S1/W0) | 4 (Pull3/Push1) | 8 | `v5-pilot-v11-medium` | `DEVELOPING` | 7 | 1 | 1 | `v11-human-difficulty-pilot` |
| 14 | `v11-pilot-014` | 5×5 | 5 (N2/E1/S1/W1) | 2 (Pull2/Push0) | 5 | `v5-pilot-v11-medium` | `DEVELOPING` | 5 | 2 | 10 | `v11-human-difficulty-pilot` |
| 15 | `v11-pilot-015` | 6×6 | 5 (N2/E1/S1/W1) | 4 (Pull2/Push2) | 5 | `v5-pilot-v11-medium` | `DEVELOPING` | 5 | 2 | 10 | `v11-human-difficulty-pilot` |
| 16 | `v11-pilot-016` | 6×6 | 6 (N5/E0/S0/W1) | 2 (Pull1/Push1) | 6 | `v5-pilot-v11-medium` | `DEVELOPING` | 6 | 2 | 6 | `v11-human-difficulty-pilot` |
| 17 | `v11-pilot-017` | 6×6 | 6 (N2/E2/S1/W1) | 4 (Pull3/Push1) | 10 | `v5-pilot-v11-medium` | `DEVELOPING` | 6 | 2 | 25 | `v11-human-difficulty-pilot` |
| 18 | `v11-pilot-018` | 6×6 | 5 (N2/E1/S2/W0) | 3 (Pull2/Push1) | 5 | `v5-pilot-v11-medium` | `DEVELOPING` | 5 | 2 | 7 | `v11-human-difficulty-pilot` |
| 19 | `v11-pilot-019` | 6×6 | 7 (N2/E1/S0/W4) | 4 (Pull3/Push1) | 5 | `v5-pilot-v11-medium` | `DEVELOPING` | 7 | 2 | 42 | `v11-human-difficulty-pilot` |
| 20 | `v11-pilot-020` | 5×5 | 4 (N1/E1/S2/W0) | 3 (Pull3/Push0) | 3 | `v5-pilot-v11-medium` | `DEVELOPING` | 4 | 1 | 1 | `v11-human-difficulty-pilot` |
| 21 | `v11-pilot-021` | 6×6 | 7 (N1/E0/S3/W3) | 3 (Pull2/Push1) | 5 | `v5-pilot-v11-hard` | `ADVANCED` | 7 | 1 | 5 | `v11-human-difficulty-pilot` |
| 22 | `v11-pilot-022` | 6×6 | 7 (N2/E2/S2/W1) | 4 (Pull4/Push0) | 8 | `v5-pilot-v11-hard` | `ADVANCED` | 7 | 1 | 2 | `v11-human-difficulty-pilot` |
| 23 | `v11-pilot-023` | 6×6 | 6 (N1/E0/S2/W3) | 3 (Pull3/Push0) | 6 | `v5-pilot-v11-hard` | `ADVANCED` | 6 | 1 | 6 | `v11-human-difficulty-pilot` |
| 24 | `v11-pilot-024` | 7×7 | 8 (N2/E2/S1/W3) | 4 (Pull4/Push0) | 9 | `v5-pilot-v11-hard` | `ADVANCED` | 8 | 1 | 21 | `v11-human-difficulty-pilot` |
| 25 | `v11-pilot-025` | 6×6 | 7 (N2/E2/S0/W3) | 5 (Pull5/Push0) | 9 | `v5-pilot-v11-hard` | `ADVANCED` | 7 | 1 | 10 | `v11-human-difficulty-pilot` |
| 26 | `v11-pilot-026` | 7×7 | 8 (N1/E5/S2/W0) | 4 (Pull4/Push0) | 10 | `v5-pilot-v11-hard` | `ADVANCED` | 8 | 1 | 15 | `v11-human-difficulty-pilot` |
| 27 | `v11-pilot-027` | 6×6 | 7 (N2/E3/S1/W1) | 5 (Pull3/Push2) | 6 | `v5-pilot-v11-hard` | `ADVANCED` | 7 | 1 | 5 | `v11-human-difficulty-pilot` |
| 28 | `v11-pilot-028` | 7×7 | 9 (N3/E4/S0/W2) | 5 (Pull5/Push0) | 8 | `v5-pilot-v11-hard` | `ADVANCED` | 9 | 1 | 16 | `v11-human-difficulty-pilot` |
| 29 | `v11-pilot-029` | 7×7 | 8 (N0/E1/S3/W4) | 5 (Pull4/Push1) | 13 | `v5-pilot-v11-hard` | `ADVANCED` | 8 | 1 | 16 | `v11-human-difficulty-pilot` |
| 30 | `v11-pilot-030` | 6×6 | 7 (N3/E3/S0/W1) | 4 (Pull3/Push1) | 5 | `v5-pilot-v11-hard` | `ADVANCED` | 7 | 1 | 2 | `v11-human-difficulty-pilot` |
| 31 | `v11-pilot-031` | 8×8 | 9 (N4/E2/S2/W1) | 6 (Pull4/Push2) | 15 | `v5-pilot-v11-super-hard` | `ADVANCED` | 9 | 2 | 20 | `v11-human-difficulty-pilot` |
| 32 | `v11-pilot-032` | 8×8 | 9 (N6/E0/S3/W0) | 6 (Pull5/Push1) | 16 | `v5-pilot-v11-super-hard` | `ADVANCED` | 9 | 1 | 5 | `v11-human-difficulty-pilot` |
| 33 | `v11-pilot-033` | 8×8 | 10 (N5/E2/S1/W2) | 6 (Pull5/Push1) | 18 | `v5-pilot-v11-super-hard` | `ADVANCED` | 10 | 2 | 6 | `v11-human-difficulty-pilot` |
| 34 | `v11-pilot-034` | 8×8 | 9 (N5/E1/S1/W2) | 6 (Pull6/Push0) | 17 | `v5-pilot-v11-super-hard` | `ADVANCED` | 9 | 1 | 70 | `v11-human-difficulty-pilot` |
| 35 | `v11-pilot-035` | 8×8 | 10 (N3/E2/S4/W1) | 6 (Pull6/Push0) | 18 | `v5-pilot-v11-super-hard` | `ADVANCED` | 10 | 1 | 36 | `v11-human-difficulty-pilot` |
| 36 | `v11-pilot-036` | 8×8 | 10 (N2/E5/S2/W1) | 6 (Pull4/Push2) | 18 | `v5-pilot-v11-super-hard` | `ADVANCED` | 10 | 2 | 30 | `v11-human-difficulty-pilot` |
| 37 | `v11-pilot-037` | 7×7 | 8 (N1/E3/S3/W1) | 6 (Pull5/Push1) | 11 | `v5-pilot-v11-super-hard` | `ADVANCED` | 8 | 2 | 37 | `v11-human-difficulty-pilot` |
| 38 | `v11-pilot-038` | 7×7 | 9 (N2/E5/S2/W0) | 5 (Pull4/Push1) | 11 | `v5-pilot-v11-super-hard` | `ADVANCED` | 9 | 2 | 46 | `v11-human-difficulty-pilot` |
| 39 | `v11-pilot-039` | 8×8 | 10 (N2/E2/S3/W3) | 6 (Pull3/Push3) | 13 | `v5-pilot-v11-super-hard` | `ADVANCED` | 10 | 2 | 14 | `v11-human-difficulty-pilot` |
| 40 | `v11-pilot-040` | 8×8 | 9 (N1/E6/S1/W1) | 6 (Pull6/Push0) | 17 | `v5-pilot-v11-super-hard` | `ADVANCED` | 9 | 2 | 21 | `v11-human-difficulty-pilot` |
| 41 | `v11-pilot-041` | 8×8 | 10 (N2/E2/S3/W3) | 6 (Pull6/Push0) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 10 | 1 | 18 | `v11-human-difficulty-pilot` |
| 42 | `v11-pilot-042` | 8×8 | 11 (N2/E1/S1/W7) | 6 (Pull5/Push1) | 17 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 396 | `v11-human-difficulty-pilot` |
| 43 | `v11-pilot-043` | 8×8 | 10 (N4/E3/S1/W2) | 6 (Pull6/Push0) | 19 | `v5-pilot-v11-expert` | `ADVANCED` | 10 | 2 | 39 | `v11-human-difficulty-pilot` |
| 44 | `v11-pilot-044` | 8×8 | 11 (N1/E6/S1/W3) | 6 (Pull6/Push0) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 1 | 21 | `v11-human-difficulty-pilot` |
| 45 | `v11-pilot-045` | 8×8 | 11 (N2/E2/S7/W0) | 5 (Pull5/Push0) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 166 | `v11-human-difficulty-pilot` |
| 46 | `v11-pilot-046` | 8×8 | 11 (N4/E3/S3/W1) | 4 (Pull4/Push0) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 456 | `v11-human-difficulty-pilot` |
| 47 | `v11-pilot-047` | 8×8 | 11 (N2/E1/S6/W2) | 6 (Pull4/Push2) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 483 | `v11-human-difficulty-pilot` |
| 48 | `v11-pilot-048` | 8×8 | 11 (N3/E0/S5/W3) | 6 (Pull5/Push1) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 102 | `v11-human-difficulty-pilot` |
| 49 | `v11-pilot-049` | 8×8 | 11 (N2/E4/S2/W3) | 4 (Pull3/Push1) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 11 | 2 | 36 | `v11-human-difficulty-pilot` |
| 50 | `v11-pilot-050` | 8×8 | 10 (N3/E2/S2/W3) | 6 (Pull6/Push0) | 21 | `v5-pilot-v11-expert` | `ADVANCED` | 10 | 2 | 276 | `v11-human-difficulty-pilot` |
| 51 | `v11-pilot-051` | 8×8 | 12 (N2/E3/S5/W2) | 6 (Pull6/Push0) | 19 | `v5-pilot-v11-master` | `ADVANCED` | 12 | 1 | 12 | `v11-human-difficulty-pilot` |
| 52 | `v11-pilot-052` | 8×8 | 10 (N3/E1/S3/W3) | 7 (Pull7/Push0) | 22 | `v5-pilot-v11-master` | `ADVANCED` | 10 | 2 | 4 | `v11-human-difficulty-pilot` |
| 53 | `v11-pilot-053` | 8×8 | 12 (N3/E1/S5/W3) | 5 (Pull4/Push1) | 21 | `v5-pilot-v11-master` | `ADVANCED` | 12 | 2 | 109 | `v11-human-difficulty-pilot` |
<!-- END GENERATED V11 INVENTORY -->

## 26. Generator V6.1, Auto Journey, five-clear ads, and privacy audit (22 August 2026)

### 26.1 Current production and frozen evidence

Production remains Campaign V10: 2,205 levels, content version 10, generator version 5, rules `magnetrail-core-1`, SHA-256 `8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`. Infinite remains `c3500d87eedfa7cdbfdde1000bf49ae9af5c2a0e0c24c4ce5c7d977b5d6b4f83`, Daily remains `3f21a633fa1e51d29938b830fe567103a52354e367d3177c20b8196c881156c8`, and the debug V11 pilot remains `035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184`.

The V6 owner CSV is frozen as a hard-negative corpus: 30/30 no-hint completions; 17 Easy, 11 Medium, 2 Hard; no Super Hard/Expert; 11/12 assigned high boards rated Easy/Medium; Spearman 0.498; within-one 50%; guessing 23/30; repeated strategy 1/30; V6 Expert median 43 seconds but Medium perception; V6 median final attempt 5.5 actions versus historical V10 14. `campaign-508` and `campaign-1828` remain diagnostic anchors, never templates.

### 26.2 V6.1 meaning and limits

`Generator V6.1 — Semantic Novelty plus Cued Multi-Phase Adversarial Difficulty` separates semantic novelty from conjunctive challenge evidence. It preserves V6 exact/D4/relevance/causal/DAG/policy novelty and adds production-transition, mechanic-rhythm, family, and behavior-cluster fingerprints. It grades the easiest available winning policy with an orientation-aware adversarial policy ensemble. Critical decisions require successful non-commuting safe/trap choices, a delayed persistent consequence, and a visible production trace; failed taps and forced runs do not count.

Expert synthesis is graph-first as of the final 22 August refinement. Before any coordinate exists,
`ExpertLogicalGraphSynthesizerV61` constructs a variable 18-action, three-chain, three-phase partial
order with six non-commuting decision pairs, delayed-trap contracts, and an identity-selected acyclic
set of cross-chain dependencies. Exact coloured-graph canonicalization removes role IDs and proves
the synthesis signature. Spatial kernels are only search scaffolds; the realized board must still
produce a distinct production causal graph, complete decision DAG, solution policy, visible proofs,
and solver replay. The synthesis signature itself never certifies gameplay or raises difficulty.

Band classification is the minimum independent evidence cap. Size (maximum 8×8), occupancy, arrow/wall/magnet count, solution length, authored tier, and family name cannot upgrade it. Occupancy is a presentation gate with object-level semantic witnesses, not difficulty evidence. These are automated difficulty classifications with model limitations; they are not human-certified or population-validated.

V6.1 actively synthesizes Easy, Medium, Hard, Super Hard, and Expert. Master remains parseable for older content but is rejected by new synthesis with `REJECT_MASTER_BAND_OWNER_REMOVED`; Expert is not renamed. Two bounded Master refinements failed the four-phase/early-decision contract or complete-analysis resource bounds, including one 22-action 6×6 diagnostic that exhausted the 512 MiB JVM worker and was removed. See `docs/CORE_RULESET_DIFFICULTY_CEILING_REPORT.md`.

Behavior-cluster schema `v61b2` is derived from production decision/policy evidence and is invariant to IDs/seeds/authored tier. Recent-20 spacing and the 2%-per-band cap are enforced during admission so campaign synthesis fails early at archive saturation rather than producing a complete but un-certifiable catalog.

The V6 regression passes: all 12 former V6 high assignments are demoted, all 23 reported-guess boards are rejected/capped, V10 anchors do not become high from length, and the original V6 exact/D4/causal/DAG/policy uniqueness remains intact.

### 26.3 Expert capacity proof and full-campaign outcome

The required pre-campaign proof loaded 3,000 fingerprints: Campaign 2,205, Infinite 624, Daily 7,
V11 53, rejected-V6 audit 30, and prior accepted V6.1 prefix 81. It accepted 24/24 Expert boards
with no rejection. Every board had at least six visible critical decisions, six persistent delayed
traps, three causal phases, two cross-chain dependencies, inferability 1.0, cheap-policy solve rate
0.0, maximum forced run three, and complete non-truncated production/solver replay. The 24 exact
canonical synthesis graphs, extracted production causal graphs, quotient decision DAGs, and
solution policies were independently distinct. Catalog SHA-256 is
`4d237b78213dd5e46adaadbb2838bdf0e0d00dde2c0a864cc7f23684f06c26b4`; audit SHA-256 is
`72cb0336277cbbc7b26bdd1b4189dff6651a164ed3ff0a0c4c3cd48779f1575f`; proof SHA-256 is
`3f07b6aa6bb2838c8a089e64bb44d75c658ef10f78b1d14369df49b84c4a531c`.

Only after that proof passed, one 64-attempt-per-slot production synthesis ran. It is
`AUTOMATED_CAMPAIGN_REJECTED`, but it passed the former Level 94 Expert blocker and advanced to
85/2,193 non-tutorial boards: Easy 12, Medium 22, Hard 27, Super Hard 16, and Expert 8. It stopped
at Level 98 Super Hard (`SHARED_MAGNET_PARITY_BRAID-v3`): 49 attempts were near-semantic duplicates
and 15 hit the forbidden recent strategy cluster. The accepted prefix has zero exact, D4,
causal-graph, decision-DAG, solution-policy, or above-threshold near-semantic duplicates. Partial
output cannot certify; no gate was lowered, no V12 production campaign exists, and promotion was
not invoked. The capacity audit was an explicit final-run comparison corpus; the cross-audit found
zero exact, D4, relevance, causal, decision-DAG, solution-policy, or synthesis-graph collisions.
The next evidence-backed blocker is Super Hard archive/cluster capacity, not Expert capacity and
not an engine/solver disagreement.

Evidence: `docs/content/generator_v6_1/staging/production/`. Architecture and commands: `docs/GENERATOR_V61_ARCHITECTURE.md` and `docs/GENERATOR_V61_OPERATOR_COMMANDS.md`.

### 26.4 Auto Journey

The app contains a fail-closed Auto Journey path beginning at player-facing Level 2206. IDs are `auto-journey-v1-{ordinal}`. Generation and complete certification run off the main thread with pure Kotlin bounded budgets; four boards are prepared ahead. Exact canonical JSON, identities, band, seed/attempt, all uniqueness fingerprints, certificate receipt, completion, and history are durable before presentation, so process death restores the same board.

Auto Journey passes the last eight durable causal families and last nineteen durable `v61b2` strategy clusters into every request. The same exclusions are rechecked for certified fallbacks and transactionally when a record is persisted, so a custom callback or process restart cannot bypass pacing.

No complete V6.1 campaign or fallback pool passed the current strict synthesis. Partial technically
accepted staging boards are not runtime fallbacks. Therefore the V10 boundary shows a retryable
preparation state rather than an uncertified or duplicate board. Clearing app data or uninstalling
resets local Auto Journey history because no cloud synchronization exists. The refreshed host-only
diagnostic recorded Easy 133 ms/8.3 MB heap delta, Medium 4,111 ms/173.3 MB, Hard 84 ms/4.1 MB,
Super Hard 565 ms/116.8 MB, and Expert 2,314 ms/96.7 MB. These are host wall/allocation deltas, not
Android peak memory, thermal, ANR, or UI-responsiveness evidence. Host tests cover restoration,
collision/failure behavior, idempotent completion, and off-main scheduling; device thermal/UI
benchmarking remains required before release.

### 26.5 Interstitial cadence

Only a first successful completion of a non-tutorial numbered campaign level or Auto Journey level increments the persisted counter. Levels 1–12, replays, duplicate completion callbacks, failed attempts, restarts, hints, ordinary taps, Daily/Infinite play, and rewarded skips do not count. The first opportunity is after Levels 13–17. The opportunity is claimed at the natural next-level boundary after completion persistence; denied/unresolved consent, no-fill/not-loaded, offline, cooldown, recent rewarded, App Open/full-screen ownership, background, wrong screen, or failure skips immediately with no spinner and no later ad debt. A process-wide coordinator (including a reserved App Open owner even though no App Open ad service is currently present) and minimum 120-second cooldown prevent overlap.

Debug uses Google test IDs. Release configuration fails closed if a sample/test ID, missing live ID, unverified audience, missing Firebase configuration, missing signing inputs, or unsafe/missing privacy URL would be used for a requested production release.

### 26.6 Fairness and privacy

Blind playtest schema 5 exposes five complete fairness anchors from “Completely unfair — I could only guess” to “Completely fair — outcomes followed visible rules.” Guessing is separately No/Unsure/Yes, with an explanation distinguishing immutable failed exploratory taps from indistinguishable successful choices. The old `guess_required` CSV column remains; schema 5 appends response/anchor/decision-state fields, and schema 4 stored sessions migrate.

`docs/privacy-policy.md`, `docs/privacy-policy.html`, and `docs/DATA_SAFETY_MAPPING.md` match the audited SDK/storage configuration. Settings always provides an accessible in-app fallback if no browser/public URL is available. Mobile Ads/UMP and conditional opt-in Firebase processing are disclosed; the policy does not claim “no data.” Backups/transfer and cleartext are disabled.

External blocker: the repository still lacks a verified legal publisher identity, privacy contact/postal decision, active public HTTPS policy URL, live AdMob values and console evidence, genuine Firebase configuration/retention choices, upload signing credentials, and verified Play audience/Data Safety/App content declarations. The local HTML is deployable but is not itself a Play URL.

### 26.7 Rollback and operator status

Promotion is hash-bound and parses all certificate gates. Only `AUTOMATED_CAMPAIGN_CERTIFIED` with exactly 2,205 levels can create the immutable V10 rollback artifact and atomically replace the production catalog. Because the current certificate is rejected, no rollback artifact or promotion receipt was created and V10 is already the rollback state.

### 26.8 Final verification receipt

The capacity proof command completed in 9 minutes 20 seconds. The subsequent campaign generation
command completed in 11 minutes 1 second after its mandatory core/tools/app/release-lint/manifest
prerequisites. Current retained XML totals are 236 core tests (31 explicitly skipped exploratory
synthesis traces), 44 tool tests, 119 app debug tests, and 118
app release tests, with zero failures or errors. Focused Easy, Super Hard, Hard delayed-cancellation,
and archive-aware Expert regressions passed separately. The final local readiness aggregate then
completed in 1 minute 5 seconds and reran R8, resource shrinking, structural bundle packaging,
privacy checks, manifest checks, lint, and debug-catalog exclusion. The connected/device test suite
was not run.

The guarded certification remains unavailable for the rejected staging artifact. Production was
re-hashed after generation and remains
`8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9`. The final rejected
certificate SHA-256 is `e0ab606a90290ddb12c4d795673be70744052c54b6c7d1562a8e2b7f1294ddcd`;
the partial staging candidate hash is
`9c2d70349a4674fbedd6f2e8668a8b5c3efbe8f76f79f76b2f69763c56c761b5`. Exact commands and
release findings are frozen in `docs/GENERATOR_V61_EXECUTION_LOG.md` and
`docs/GENERATOR_V61_RELEASE_READINESS_AUDIT.md`.
