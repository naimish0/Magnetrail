# Magnetrail — Complete Game and Development Context

Last consolidated: **2026-08-20 (Asia/Kolkata)**

Repository: Android Studio project `Magnetrail`

Package: `com.rameshta.magnetrail`
Current checked-in campaign: **205 levels, content version 8, generator version 5**

Canonical campaign SHA-256:
`6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128`

This is the single read-first handoff document for Magnetrail. It consolidates the product vision,
frozen gameplay semantics, Android architecture, UI system, implemented milestones, campaign and
economy state, advertising/privacy behavior, build/release posture, known defects, safety rules,
and future roadmap.

It is intentionally self-contained at the system level. Canonical JSON remains the authority for
the exact cell-by-cell contents of every level; duplicating 205 complete boards inside Markdown
would create a second content source that could drift.

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
- The current campaign contains 205 levels, not the 100 or 200 levels still mentioned by some historical
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

1. Choose Campaign or Daily Challenge.
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
- Settings;
- Completion state within gameplay; deadlock remains an internal engine/analytics fact and is not
  presented as an automatic failure screen.

`GameMode` supports `CAMPAIGN`, `DAILY`, and `INFINITE`. The Progressive Journey is immediately
available when its certified catalog loads, selects only immutable pre-certified catalog boards,
and persists its own
selection/completion history independently of campaign and Daily progress.

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

### 8.3 Typography, shapes, and spacing

- Preferred typeface: bundled Manrope; otherwise a clean local system sans-serif.
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

---

## 10. Current campaign content

Canonical file: `docs/Magnetrail_Campaign_Levels_v3.json`.

| Property | Current value |
|---|---|
| Catalog schema | 2 |
| Rule version | `magnetrail-core-1` |
| Catalog ID | `magnetrail-campaign-v4` |
| Content version | 8 |
| Generator version | 5 |
| Level count | 205 |
| SHA-256 | `6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128` |
| Stable range | `proto-001`…`proto-012`, then campaign IDs through `campaign-205` |
| Current metadata origin | 205 `GENERATOR_ASSISTED` |
| Board sizes | 2 × 3×3; 17 × 4×4; 76 × 5×5; 28 × 6×6; 37 × 7×7; 45 × 8×8 |
| Exact fingerprints | 205 unique |
| Symmetry fingerprints | 205 unique |

Current packs:

| Range | Pack |
|---|---|
| 1–20 | `magnetic-circuit-01` |
| 21–40 | `magnetic-circuit-02` |
| 41–60 | `magnetic-circuit-03` |
| 61–80 | `magnetic-circuit-04` |
| 81–100 | `magnetic-circuit-05` |
| 101–120 | `magnetic-circuit-06` |
| 121–140 | `magnetic-circuit-07` |
| 141–160 | `magnetic-circuit-08` |
| 161–180 | `magnetic-circuit-09` |
| 181–200 | `magnetic-circuit-10` |
| 201–205 | `magnetic-circuit-11` |

Metadata mechanic-tag counts:

- Magnet control: 199
- Polarity dependency: 199
- Walls: 198
- Occlusion: 202
- Order dependency: 198
- Exposure/reveal: 202
- Cancellation: 52

These are metadata claims backed by automated analysis, not proof that a player experiences the
claimed mechanic as difficult or central.

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
- Content-v7 Levels 1–200 and content-v8 Levels 201–205 remain without owner difficulty ratings.
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

The 40 D1 human ratings apply only to their archived content-v6 fingerprints. They must not be
joined to content-v7 or staging candidates by stable ID alone because the physical boards changed.
No automated label, V4 band, solver score, or generator profile may be written as a human rating.

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
```

Promotion tasks require explicit confirmation properties and are not dependencies of normal builds.
Gradle configuration cache is enabled in `gradle.properties`; focused generator work should use
the narrowest relevant test/task before any bounded multi-profile benchmark.

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

### 16.3 Consent/privacy behavior

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
content-6 expansion. A new signed/release candidate must be rebuilt and re-audited after content is
accepted.

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

Only after Insulator and board-size gates: introduce Insulator gradually, preserve 1–200, support
200→201 and stop at 250, and publish full candidate/duplicate/pacing/migration/manual-review
evidence. Not implemented.

### Phase 5 — Levels 251–300

Complete and freeze the numbered campaign with varied mastery, recovery, and a memorable fair
Level 300. Preserve 1–250 and stop cleanly at 300. Not implemented.

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
| M4 privacy/ad inventory | `docs/M4_COMPLIANCE_NOTES.md` |
| Analytics events | `docs/M4_EVENT_CATALOG.md` |
| Release blockers | `docs/release/RELEASE_BLOCKER_LOG.md` |
| Release requirements | `docs/release/M5_RELEASE_REQUIREMENTS.md` |

### Key production source areas

```text
game-core/src/main/kotlin/com/rameshta/magnetrail/core/
  model/ engine/ solver/ level/ generation/ difficulty/ quality/ grading/ economy/ daily/

app/src/main/java/com/rameshta/magnetrail/
  game/ home/ levels/ settings/ data/ daily/ feedback/ ads/ privacy/ analytics/ crash/ release/

level-tools/src/main/kotlin/com/rameshta/magnetrail/tools/
```

---

## 24. Current unresolved issues

1. **Current human difficulty is uncalibrated.** Generator V5 replaced the first 200 production
   boards and content v8 appended five more, but automated checks do not establish perceived difficulty.
2. **D2 human review is incomplete.** The 40 D1 owner ratings belong to archived content-v6 board
   fingerprints. None of the 62 selected D2/content-v7 review boards has an owner rating yet.
3. **Expert/Master automated certification now passes; human calibration is still required.**
   Purposeful-space V5.2 produces certified Expert V4 `62–63` boards with safe-choice ratio
   `0.5996`, ordering depth `5`, and three meaningful long-range relationships. Certified Master
   boards score V4 `68`, with safe-choice ratio `0.5154` and ordering depth `5`. The Infinite pool
   includes 12 unique entries in each high band. This does not retroactively certify waived campaign
   Level 205 or count as human approval.
4. **Historical Generator V5 PASS evidence is superseded for quality decisions.** Its Expert/Master
   staging thresholds had been lowered. Current required Expert gates are restored and must not be
   weakened to reproduce that PASS.
5. **No uploadable current release exists.** The recorded M5 AAB is stale; the current content-v8
   release build is deliberately structural/non-uploadable.
6. **Production services are unconfigured.** Live AdMob, UMP console state, Firebase, privacy URL,
   audience choice, upload signing, and Play declarations remain blocked.
7. **Representative device/accessibility/release testing is incomplete.** API 24, mid-range/API 35,
   tablet/foldable, TalkBack/Switch Access, current upgrade, and production-like consent/ad tests
   require real evidence.
8. **Future game scope is intentionally absent.** Insulator, Levels 206–300, Adaptive Infinite,
   and ads-only Phase 8 are documentation only. Infinite Mode itself is implemented. D2 includes 43 production 8×8 boards,
   but their representative-device usability still lacks human evidence; 9×9 remains excluded.

The immediate product action is blind human playtesting of the four certified Expert and four
certified Master Infinite boards, alongside Levels 201–205 with the Level 205 waiver visible. Use
those ratings to verify that the measured consequence gap is felt by players; do not treat the
automated certificate as human approval.
