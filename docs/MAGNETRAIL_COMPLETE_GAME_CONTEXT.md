# Magnetrail — Complete Game and Development Context

Last consolidated: **2026-08-20 (Asia/Kolkata)**

Repository: Android Studio project `Magnetrail`

Package: `com.rameshta.magnetrail`
Current checked-in campaign: **200 levels, content version 7, generator version 5**

Canonical campaign SHA-256:
`8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5`

This is the single read-first handoff document for Magnetrail. It consolidates the product vision,
frozen gameplay semantics, Android architecture, UI system, implemented milestones, campaign and
economy state, advertising/privacy behavior, build/release posture, known defects, safety rules,
and future roadmap.

It is intentionally self-contained at the system level. Canonical JSON remains the authority for
the exact cell-by-cell contents of every level; duplicating 200 complete boards inside Markdown
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
- The current campaign contains 200 levels, not the 100 levels still mentioned by some historical
  M3/M5 documents.
- The latest monetization directive is **ads only**. The Billing/Remove Ads section in
  `CODEX_MASTER_REMAINING_DEVELOPMENT_PROMPT.md` is superseded. Do not add Google Play Billing,
  purchases, subscriptions, paid content, premium currency, or Remove Ads.
- Automated solver or Quality approval is never human approval.
- The project owner/player completed the archived content-v6 campaign and found all 200 levels
  overwhelmingly easy. D1 diagnosed/calibrated that failure; D2 subsequently replaced all 200
  boards under stable IDs with content-v7 Generator-V5 boards. Content-v7 is technically
  certified, but it has not received human difficulty ratings and is not human-approved.
- D2.1 and the later Generator-V5 root-cause repair are staging/generator work only. They have not
  changed the canonical content-v7 campaign. The latest focused Expert repair remains blocked by
  safe-choice, mandatory-ordering, interaction-density, and counterfactual-analysis constraints.
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

### 3.6 Win, deadlock, undo, and restart

- Win immediately after the final arrow is removed.
- Deadlock means arrows remain but no successful action exists.
- The engine reports deadlock and never reshuffles.
- Undo restores the previous successful immutable state, including all polarities.
- Failed launches do not create undo entries.
- Restart restores the authored initial state and resets the current attempt.

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
8. Use free Undo/Restart or an optional solver-backed hint.
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
- `GameViewModel` owns committed board state, undo history, in-flight result, animation phase,
  attempt counters, completion state, navigation, hints, and progress integration.
- Domain state is never partially mutated during animation.
- Ads and analytics remain entirely in the application layer.
- Normal builds may copy checked-in content into assets but must never regenerate or overwrite
  campaign content.

### 6.3 Current navigation

The Compose app currently exposes:

- Home;
- Campaign level selection;
- Campaign/Daily gameplay;
- Settings;
- Completion and deadlock states within gameplay.

`GameMode` currently supports `CAMPAIGN` and `DAILY`. Infinite Mode does not exist.

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
4. Undo, Restart, Hint, pause/settings, and optional assistance.

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

### 8.6 Voice

Preferred: `Board cleared`, `Find the sequence`, `The field flipped`, `Path blocked`,
`Try another arrow`, `Clean solve`.

Avoid IQ claims, urgency, guilt, jackpot/casino language, “You failed,” “Only 1% can solve,” and
misleading reward language.

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
- Undo, Restart, level navigation, completion, and deadlock.
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
- The repaired seed `11510013` is still rejected. See Section 11.7 for the exact measurements.

---

## 10. Current campaign content

Canonical file: `docs/Magnetrail_Campaign_Levels_v3.json`.

| Property | Current value |
|---|---|
| Catalog schema | 2 |
| Rule version | `magnetrail-core-1` |
| Catalog ID | `magnetrail-campaign-v4` |
| Content version | 7 |
| Generator version | 5 |
| Level count | 200 |
| SHA-256 | `8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5` |
| Stable range | `proto-001`…`proto-012`, then campaign IDs through `campaign-200` |
| Current metadata origin | 200 `GENERATOR_ASSISTED` |
| Board sizes | 2 × 3×3; 16 × 4×4; 76 × 5×5; 27 × 6×6; 36 × 7×7; 43 × 8×8 |
| Exact fingerprints | 200 unique |
| Symmetry fingerprints | 200 unique |

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

Metadata mechanic-tag counts:

- Magnet control: 194
- Polarity dependency: 194
- Walls: 193
- Occlusion: 197
- Order dependency: 194
- Exposure/reveal: 197
- Cancellation: 48

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
- Content-v7 human difficulty remains unmeasured until the owner plays and rates the new boards.
- D2.1/V5 repair candidates are not part of this campaign. Failed or partially measured staging
  boards must never be copied into this canonical file.

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

### 11.7 Current Expert Generator V5 blocker — 2026-08-20

Known reproduction seed: `11510013`; canvas: 8×8. The current focused constructor uses 10 arrows,
3 magnets, and 51 walls, for 64 authored objects. It no longer creates `shielded-filler-*` magnets.

| Metric | Original reproduced Expert | Current focused repair | Required |
|---|---:|---:|---:|
| Interaction density | 0.0253 | 0.0288 | >= 0.04 |
| Relevant-object ratio | 0.2656 | NOT MEASURABLE WITHIN FAST BOUND | >= 0.28 |
| Average relevance | 0.0674 | NOT MEASURABLE WITHIN FAST BOUND | >= 0.11 |
| Meaningful long-range relationships | 0 | 3 | >= 3 |
| Meaningful ordering | 0.1389 | 0.0000 | >= 0.20 |
| Connected components | 46 | 52 | diagnostic |
| Largest connected component | 17 | 13 | diagnostic |
| Isolated objects | 44 | 51 | diagnostic |

Additional current evidence:

- all 8 declared semantic construction edges are physically verified before and after the seeded
  reflection/rotation;
- the physical analyzer observed 181 typed edges and all 3 required distance-four controllers;
- solver/canonical replay completes and Difficulty V4 search is complete without truncation;
- safe-choice ratio is `0.9372` against a maximum of `0.88`;
- meaningful failure rate is `0.0628`;
- mandatory-ordering depth is `0`;
- certification rejects the candidate for `safe-choice-ratio-above-profile` and
  `ordering-depth-below-profile` before expensive object relevance;
- one repair opportunity is inspected, but no mutation is applied because the dependency-complete
  board has no filler-safe operator;
- a 14-arrow intermediate reached interaction density `0.0546` and 3 long-range relationships, but
  V4 truncated on counterfactual/sequence enumeration, so it was discarded rather than certified;
- the 10-arrow full counterfactual relevance run exceeded the 90-second focused budget and was
  terminated. No number was invented and no broad benchmark was started.

Current status: **BLOCKED, staging only**. The next generator change must connect the corridor
modules into globally mandatory causal order and give the surrounding route-guard shell physical
participation, while retaining complete V4 search. Do not lower gates, raise bounds to hide the
problem, alter V4, or promote this candidate.

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
- Do not count ignored taps, animation-blocked taps, controls, hints, Undo, Restart, or navigation.
- Undo does not erase already-counted actions.
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

Economy version: 1.

| Rule | Value |
|---|---:|
| Starting balance | 150 coins |
| First campaign clear | 20 coins |
| Each newly earned star | 5 coins |
| First Daily clear for an identity | 50 coins |
| Solver hint | 30 coins |

- First-clear and star rewards are idempotent.
- Replays do not repeat already-earned rewards.
- Balance never becomes negative.
- Hint spend and usable hint display are atomic; no usable hint means no coin charge.
- Undo and Restart remain free.
- Currency can assist but never gates campaign/Daily progression.

The historical 100-level simulation reported minimum balances of 150/150/120 for clean,
occasional-hint, and every-level-hint scenarios. It is deterministic configuration evidence, not a
forecast for the current 200-level economy or future rewarded coins.

### 13.3 Persistence

Current preference schema: 6.

Persisted local state includes:

- highest unlocked level, completed stable IDs, and last selection;
- current-board best records and fingerprinted legacy records;
- first-clear reward IDs and coin balance;
- Daily cache, completions, rewards, streak, and trusted date;
- content/generator/Daily/economy versions;
- Sound, Haptics, Reduced Motion, High-Contrast Fields, Path Preview Assistance, Diagnostics;
- interstitial counters/dates and rewarded-hint transaction/cap state.

DataStore migrations are idempotent and validate/clamp corrupt values. Completing Level 150 under
content version 5 unlocks/selects Level 151 once after migration to version 6. Completing Level 200
does not invent Level 201.

There is no account, cloud sync, or backend recovery. Clearing storage/uninstalling removes local
progress, subject to platform behavior and the app’s disabled backup policy.

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
- A hint does not mutate board state or undo history.
- Stale results are discarded after move, Undo, Restart, or level change.
- The hint counter increases only when a usable hint is shown.
- Coin hints cost 30 coins atomically.
- Existing rewarded-hint credit is a voluntary alternative and must remain intact.

---

## 16. Existing advertising and consent

Current implemented advertising formats:

1. Rewarded ad for one solver hint credit.
2. Interstitial at a natural campaign completion boundary.

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

### 16.2 Interstitial policy

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
for an interstitial. Never show during gameplay, animation, failure, deadlock, Undo, Restart,
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
choice/spend/display, Daily start/complete, consent result, rewarded lifecycle, interstitial
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
- existing rewarded hint remains unchanged;
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

### 20.1 Latest focused Generator V5 verification

The 2026-08-20 root-cause repair deliberately did not run a full repository regression or staging
benchmark because the known Expert seed failed its focused gates.

Passing focused evidence:

- deterministic dependency contract reproduction;
- canonical production-engine replay and complete solver proof;
- successful-but-harmful-choice evidence from real Difficulty V4;
- no filler repair operator on dependency-complete Expert construction;
- 8/8 declared semantic edges physically verified;
- three meaningful distance-four relationships verified;
- campaign SHA remained unchanged;
- Gradle configuration-cache entries were stored/reused.

Rejected/terminated evidence:

- certification rejected seed `11510013` for excessive safe choices and missing ordering depth;
- physical interaction density remained below its gate;
- full counterfactual relevance analysis was stopped after 90 seconds under the mandatory fast
  stopping rule;
- no broad Generator-V5 benchmark, campaign promotion, APK release build, or device test followed.

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

Separate deterministic offline mode, never Level 301. Configurable unlock (default after Level 100),
Relaxed/Balanced/Challenging/Expert profiles, stable puzzle identity, bounded generation,
production certification, fallbacks, and bounded local history. Initially no repeatable coin reward.
Not implemented.

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

1. **Content-v7 human difficulty is uncalibrated.** Generator V5 replaced all 200 production boards,
   but automated certification does not establish perceived difficulty.
2. **D2 human review is incomplete.** The 40 D1 owner ratings belong to archived content-v6 board
   fingerprints. None of the 62 selected D2/content-v7 review boards has an owner rating yet.
3. **Generator V5 Expert repair is blocked.** The current dependency-complete seed proves three
   physical long-range relationships and complete V4 search, but it still has safe-choice ratio
   `0.9372`, ordering `0.0`, interaction density `0.0288`, 51 isolated objects, and no completed
   counterfactual relevance result within the fast bound. It is not certification/promotion ready.
4. **Historical Generator V5 PASS evidence is superseded for quality decisions.** Its Expert/Master
   staging thresholds had been lowered. Current required Expert gates are restored and must not be
   weakened to reproduce that PASS.
5. **Historical release artifact is stale.** The recorded M5 AAB predates the 200-level catalog and
   cannot represent the current final game.
6. **Production services are unconfigured.** Live AdMob, UMP console state, Firebase, privacy URL,
   audience choice, upload signing, and Play declarations remain blocked.
7. **Representative device/accessibility/release testing is incomplete.** API 24, mid-range/API 35,
   tablet/foldable, TalkBack/Switch Access, current upgrade, and production-like consent/ad tests
   require real evidence.
8. **Future game scope is intentionally absent.** Insulator, Levels 201–300, Infinite, Adaptive
   Infinite, and ads-only Phase 8 are documentation only. D2 includes 43 production 8×8 boards,
   but their representative-device usability still lacks human evidence; 9×9 remains excluded.

The immediate engineering action is a second, still-bounded Generator V5 construction repair:
make the three corridor modules causally depend on one another, ensure wrong successful ordering
removes future capability, and replace the isolated route-guard shell with physically participating
objects. Re-run only seed `11510013`; proceed to broader staging diagnostics only after it passes
all unchanged Expert gates with complete V4 and relevance analysis. The current repair phase
explicitly forbids promotion; a later promotion needs certified staging artifacts and a new explicit
owner direction. Human review remains required to validate player experience even when automated
certification passes.
