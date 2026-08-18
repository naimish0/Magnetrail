# Codex Prompt — Magnetrail M2

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0 and M1 are complete.

---

You are implementing **M2 for Magnetrail**, an Android-only, offline-first deterministic arrow-and-magnet logic puzzle.

M0 delivered the pure Kotlin `:game-core` engine, level contracts, solver, and tests. M1 delivered the playable Jetpack Compose/Canvas prototype with all 12 canonical levels, engine-result-driven animation, undo, restart, level navigation, win, and deadlock handling. Preserve those working foundations.

## Inspect and verify before editing

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect the complete repository and current Git diff. Preserve unrelated user changes.
3. Read every file under `docs/`, using this authority order:
   1. `docs/Magnetrail_Rules_Contract.md`
   2. `docs/Magnetrail_Prototype_Levels_v1.json`
   3. `docs/Magnetrail_Android_Technical_Brief.md`
   4. `docs/Magnetrail_DESIGN.md`
   5. `docs/Magnetrail_Game_Design_Spec_v0.1.docx`
4. Read the M0 and M1 completion notes, if present.
5. Inspect the actual `GameEngine`, `ResolutionResult`, solver API, Compose renderer, animation coordinator, ViewModel, navigation, and tests. Adapt to real APIs; do not rewrite stable code merely to match example names here.
6. Run the existing core/app tests and `assembleDebug` before making changes. Record baseline results.
7. Summarize the current architecture and an incremental M2 plan before editing.

Do not silently alter the frozen game rules. If a material conflict or broken M1 invariant is discovered, explain it before expanding scope.

## M2 objective

Turn the functional gray-box prototype into a **polished, accessible, offline vertical slice** suitable for structured playtesting.

M2 includes:

- Magnetrail’s original Rail Dart visual identity
- Refined Pull/Push field visualization and result-driven motion
- Cohesive home, level selection, gameplay, completion, and settings surfaces
- Sound and haptic feedback with user controls
- Solver-backed, non-destructive hints
- Local settings and progress persistence
- Accessibility options including reduced motion, high-contrast fields, and path-preview assistance
- Regression, UI, persistence, and accessibility tests

M2 does **not** include procedural generation, 100+ levels, daily challenge, streak, coins/economy, stars/move grading, ads, analytics, accounts, backend, billing, release signing, or store listing work.

## Preserve these architectural invariants

- `:game-core` remains pure Kotlin/JVM and the only gameplay authority.
- UI and animation never recompute magnet control, line of sight, routes, collision, polarity, win, or deadlock.
- A tap is resolved once by the production engine; its `ResolutionResult` remains the animation script.
- The solver uses the production engine and does not duplicate rules.
- Visual effects, timing, sound, haptics, accessibility settings, and persistence never influence rule outcomes.
- No domain state is partially mutated during animation.
- All gameplay must remain fully usable without network access.

## Required work

### 1. Visual design system

Implement the design tokens in `Magnetrail_DESIGN.md` as reusable Compose theme values rather than scattering literals.

Core palette:

- Primary navy `#183153`
- Primary strong `#10223C`
- Pull cyan `#18A7B8`
- Pull soft `#E8F7F8`
- Push amber `#E79A2D`
- Push soft `#FFF4DF`
- Background `#F4F7FB`
- Surface `#FFFFFF`
- Ink `#172033`
- Muted slate `#5B6574`
- Border `#D7DEE7`
- Grid `#C9D3DF`
- Wall `#39414D`
- Success `#267A5B`
- Error `#B84343`

Use Manrope from a bundled, legally redistributable local font resource if it already exists or can be added with clear license attribution. Do not fetch fonts at runtime. If the font asset is unavailable, keep a clean system sans-serif fallback and document the deferment rather than downloading an unverified file.

Create consistent shapes, spacing, typography, icon-button sizes, board elevation, and component states. Maintain at least WCAG AA contrast for text and essential controls. Do not add dark theme in this milestone unless already substantially implemented.

### 2. Rail Dart game pieces

Replace placeholder arrows with an original **Rail Dart** shape drawn in code or from project-owned vector assets:

- Short single-cell silhouette
- Thick capsule-like stem
- Rounded triangular head
- Small split-tail rail detail
- Deep navy stationary body
- Clear orientation at small cell sizes
- Selected state using a restrained lift/outline, not a different game meaning

Do not imitate competitors’ long, bent, interlocking, maze-forming, or exact proprietary arrow silhouettes. Do not use standard Material arrow icons as final board pieces.

Make geometry scale cleanly across every supported board size and remain readable at 360–430 dp portrait widths. Keep hit-testing based on the shared M1 board transform, not the visible path outline.

### 3. Magnet and field identity

Render magnets as an abstract circular split-ring/rail core—never emoji or a red/blue horseshoe.

Polarity must be distinguishable by at least three cues:

- Pull: cyan, inward/converging lines or chevrons, accessible `PULL` label
- Push: amber, outward/diverging lines or chevrons, accessible `PUSH` label
- Shape/motion direction that remains understandable without color

Keep field effects controlled at approximately 10–18% opacity so they communicate force without obscuring grid entities. Occluded or inactive fields must not falsely imply a legal route. Do not calculate visibility in the renderer; consume engine/result data or a core-owned query. If core lacks a safe display query, use only non-rule-bearing ambient field decoration rather than duplicating line-of-sight logic.

### 4. Refined result-driven motion

Polish the existing animation pipeline while preserving exact `ResolutionResult` semantics:

- Tap acknowledgment: under 50 ms
- Travel: approximately 70–110 ms per cell with a sensible total-duration cap
- Unaffected exit: clean continuation beyond the correct edge with a brief navy trail
- Pull capture: cyan ease-in/compression into the controlling magnet
- Push exit: amber outward acceleration and dissipating trail
- Polarity flip: 220–320 ms core compression/rotation and field reversal
- Collision: 250–400 ms compact red impact ring plus rewind
- Invalid Pull exit: brief readable invalid response with unchanged state
- Completion: begin 450–700 ms after final movement resolves; use a magnetic ripple and a few geometric particles, not a coin/confetti shower

Animation may interpolate between route points, but must visit cells in the returned order and terminate exactly as described. Input remains locked during a turn. Sound/haptics fire from semantic animation events, never from recomposition.

Implement a central `MotionPolicy` or equivalent that supports normal and reduced-motion behavior. Reduced motion replaces rotation, bounce, long trails, and particles with short fades/direct translation and an instant polarity icon swap; it must not remove essential feedback.

### 5. Screen polish and navigation

Polish these existing flows without introducing M3 systems:

#### Home

- Temporary text wordmark `Magnetrail`
- Tagline `Bend the path. Clear the board.`
- Prominent Continue card when progress exists
- Primary Play/Continue action
- Secondary Level select action
- Quiet Settings access
- No coin counter, event rail, store, inbox, streak, or promotional banners

#### Level selection

- Three-column grid for the 12 levels
- Clear level number
- Completed state using a restrained cyan edge/check, not stars
- Lock only sequentially unavailable levels if progression is enabled
- Always allow replay of completed levels
- Keep a developer-friendly method for opening every level in debug builds if sequential locks would obstruct QA

#### Gameplay

- Board remains the dominant visual anchor
- Compact top bar and level label
- Remaining-arrow progress
- Bottom actions: Undo, Restart, Hint
- Pull/Push state is never communicated by color alone
- Preserve forgiving semantics overlays for Canvas objects

#### Completion

- `Board cleared`
- Compact metrics limited to `Moves` and `Hints` for playtest observation; do not award stars or coins
- Next level and Replay
- Brief, calm celebration

#### Deadlock

- Non-punitive concise explanation
- Undo and Restart remain immediately available
- No lives, loss currency, or blocking failure funnel

### 6. Solver-backed hint system

Use the existing production solver to provide a hint for the **current exact board state**, including current magnet polarities.

Requirements:

- Solver work runs off the main thread and is cancellable.
- Prevent duplicate concurrent hint jobs.
- Provide visible loading state only if computation is not immediate.
- If solvable, choose deterministically from the solver’s valid first actions/clean solution and highlight one arrow.
- The first hint identifies an actionable arrow; it does not automatically execute the move.
- Optionally preview only the route already obtained by resolving that suggested action through the engine. Never draw a guessed route.
- Requesting a hint must not mutate board state, undo history, moves, polarity, or completion.
- Increment the per-attempt hint counter only when a usable hint is actually shown.
- Clear stale hints after any successful move, undo, restart, or level change.
- If the state is deadlocked or the solver cannot return within a defensive bound, show a concise fallback and retain Undo/Restart.
- Hints are free and unlimited in M2. Do not add coins, ads, cooldowns, or purchases.

Add content descriptions and focus behavior so the suggested arrow is announced accessibly without forcing focus unexpectedly.

### 7. Sound

Add a small, original or properly licensed offline sound set stored locally. Keep files short and optimized. At minimum support semantic cues for:

- Tap/select
- Arrow travel/exit
- Pull capture
- Push exit
- Polarity flip
- Collision/invalid move
- Undo/restart
- Board completion

Use an appropriate lightweight Android audio API for short effects. Load/release resources safely, avoid overlapping cacophony, and never create playback objects during recomposition. Respect lifecycle and the Sound setting. Music is optional; if no original loop exists, omit it rather than sourcing questionable audio. Document asset provenance/licensing.

### 8. Haptics

Add restrained semantic haptics with graceful degradation:

- Light tick for selection
- Distinct confirmation for successful exit/capture
- Directional/step sensation only if subtle and device-safe
- Stronger but brief impact for collision
- Success pattern for board completion

Use platform/Compose haptic APIs compatible with the project’s minimum SDK. Do not require special vibration permission unless technically necessary for the chosen API. Respect the Haptics setting and system accessibility expectations. Do not fire haptics in previews or automated tests.

### 9. Settings and accessibility

Add a Settings screen or bottom sheet with locally persisted controls:

- Sound: on by default
- Haptics: on by default
- Reduced motion: default from system animator/accessibility behavior where feasible, otherwise off
- High-contrast fields: off by default
- Path-preview assistance: off by default

If Music is not implemented, do not show a non-functional Music toggle.

High-contrast fields must increase essential field distinction without changing rules. Path-preview assistance may show a short engine-derived projected segment after selection/hint, but must never reveal or compute a route independently. All controls require labels, roles, state descriptions, keyboard/switch compatibility where applicable, 48 dp targets, and screen-reader ordering.

Audit dynamic text scaling at least through common enlarged font settings. The board may retain square geometry, but surrounding text and controls must not overlap or become unreachable.

### 10. Local persistence

Use Jetpack DataStore Preferences unless the existing codebase already has an appropriate local store. No Room database is needed for M2.

Persist only versioned, non-sensitive local data:

- Highest unlocked level
- Completed level IDs
- Last selected/current level
- Per-level best move count, if moves are already tracked reliably
- Settings values listed above

Do not persist an in-flight animation. On process recreation, restore to a valid committed state or restart the selected level—choose the simpler robust behavior and document it. It is not necessary to persist mid-level board state or undo history in M2.

Use a repository abstraction so ViewModels do not depend directly on DataStore implementation details. Writes must not block the main thread. Validate stored level IDs and clamp corrupt/outdated values safely. Include a small schema/version key for future migration. Do not store accounts, advertising IDs, analytics identifiers, or cloud data.

Progress rules for M2:

- Fresh install starts at Level 1.
- Completing a level marks it complete and unlocks the next available canonical level.
- Replaying a completed level remains possible.
- Completing Level 12 does not invent Level 13.
- Clearing app data resets local progress; no account/backend recovery exists.

## Testing requirements

Keep all M0 and M1 tests passing. Add focused deterministic tests rather than brittle pixel snapshots.

### Unit tests

- Settings defaults and round-trip persistence
- Progress unlock, replay, Level 12 boundary, corrupt-value clamping, and schema version
- Hint request uses the current exact state
- Hint result is a solver-valid first action
- Hint does not mutate domain state or undo history
- Hint cancellation/stale-result protection after move/restart/level change
- Hint counter increments only when shown
- Motion policy selects normal vs reduced behavior
- Semantic feedback events map once to sound/haptic commands
- Sound/haptic disabled settings suppress output

Use fakes for persistence, clocks, audio, and haptic gateways. Never fake game rules by reimplementing them.

### Compose/UI tests

- Home shows Play/Continue and Level select
- Completed level state and sequential unlock are represented semantically
- Gameplay exposes Undo, Restart, and Hint
- Hint loading/result semantics are accessible
- Settings toggles persist through screen recreation
- Reduced-motion option changes the UI policy without changing the resulting board state
- Completion exposes Moves, Hints, Replay, and Next level
- Enlarged font/content does not hide primary actions in at least one representative test configuration

### Screenshot/golden tests

If the project already has a stable screenshot framework, add a small set of goldens for Pull, Push, collision, high-contrast, and completion states. Otherwise do not introduce a large screenshot stack solely for M2; document manual visual QA instead.

### Manual device QA

Document and execute where hardware/emulators are available:

- All 12 levels remain solvable and replayable
- Pull, Push, unaffected exit, collision, flip, undo, restart, win, and deadlock
- Hint before/after polarity flip and after undo
- Sound/Haptics on and off
- Reduced motion
- High-contrast fields
- Path-preview assistance
- Process kill/relaunch progress restoration
- Offline launch and complete gameplay with airplane mode
- TalkBack traversal and activation of at least one board
- Font scaling and 360/390/430 dp portrait widths
- At least one physical Android device for haptic/audio timing when available

## Suggested app-layer additions

Fit existing conventions and avoid unnecessary rewrites. A reasonable incremental organization is:

```text
app/src/main/kotlin/.../magnetrail/
├── data/
│   ├── PlayerPreferences.kt
│   ├── ProgressRepository.kt
│   └── DataStoreProgressRepository.kt
├── feedback/
│   ├── FeedbackEvent.kt
│   ├── SoundController.kt
│   └── HapticController.kt
├── game/
│   ├── HintCoordinator.kt
│   └── MotionPolicy.kt
├── home/
│   └── HomeScreen.kt
├── settings/
│   └── SettingsScreen.kt
└── ui/
    ├── components/
    └── theme/
```

Use dependency injection through constructors/manual composition unless the repository already uses a DI framework. Do not add a framework solely for M2.

## Explicitly out of scope

Do not add:

- New gameplay rules or changed M0 semantics
- More than the canonical 12 levels
- Procedural generation or generator UI
- Daily challenge or streak
- Coins, rewards, stars, move thresholds, economy, shop, lives, or boosters
- Rewarded ads, interstitials, banners, consent SDK, billing
- Analytics, attribution, remote config, crash reporting, Firebase
- Account, login, backend, cloud save, leaderboard, social features
- Internet permission or remote assets
- App Bundle signing, Play Console upload, ASO assets, or production release work

Do not delete, relax, or rewrite passing tests to conceal regressions.

## Verification commands

Use the actual project’s Gradle task names. At minimum run the equivalents of:

```bash
./gradlew :game-core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew lintDebug
```

If an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

If a device is unavailable, say so explicitly and do not claim device-only audio, haptic, TalkBack, or connected tests passed.

## M2 definition of done

M2 is complete only when:

- All M0/M1 tests still pass and debug app builds.
- All 12 levels remain playable offline.
- No UI or effect layer duplicates game rules.
- Rail Darts and abstract Pull/Push magnets have a distinct Magnetrail identity.
- Polarity remains understandable without color alone.
- Motion is polished, result-driven, interrupt-safe, and has a reduced-motion mode.
- Sound and haptics are semantic, controllable, lifecycle-safe, and locally sourced.
- Solver-backed hints are deterministic, cancellable, non-mutating, and accessible.
- Settings and sequential progress survive process restart through local DataStore.
- Home, level selection, gameplay, completion, deadlock, and settings form a coherent flow.
- Accessibility semantics, target sizes, contrast, font scaling, and TalkBack have been checked as far as the available environment permits.
- Unit tests, lint, and debug build pass; connected-test limitations are reported honestly.
- No M3/M4/release systems were pulled into scope.

## Final response

Report:

1. What was implemented.
2. Key files and assets created or changed.
3. How rule authority remains inside `:game-core`.
4. Hint, persistence, audio, haptic, motion, and accessibility design decisions.
5. Exact test/lint/build commands and results.
6. Manual checks completed, including device/emulator limitations.
7. Asset/font/audio provenance and any deferred polish.
8. The next safe milestone: **M3 content and retention systems**—generator-assisted curated levels toward 100+, daily challenge, streak, coins/rewards, move grading/stars, and versioned economy/progress—still without ads until those systems are validated.

Begin by inspecting the repository, reading the docs, and running the M0/M1 baseline verification. Then summarize the incremental plan and proceed unless a material blocker is found.
