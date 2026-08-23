# Codex Prompt — Magnetrail M1

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0 is complete.

---

You are implementing **M1 for Magnetrail**, an Android-only deterministic arrow-and-magnet logic puzzle. M0 is complete: the pure Kotlin/JVM `:game-core` module, production game engine, level parsing/validation, solver, prototype level data, and M0 tests already exist and pass.

## Read and inspect first

Before changing code:

1. Read `AGENTS.md` and any repository-specific instructions, if present.
2. Inspect the repository, current Gradle modules, package names, min/target SDK settings, and the public API actually exposed by `:game-core`.
3. Read every file under `docs/`. Treat the Magnetrail sources as authoritative in this order:
   1. `docs/Magnetrail_Rules_Contract.md`
   2. `docs/Magnetrail_Prototype_Levels_v1.json`
   3. `docs/Magnetrail_Android_Technical_Brief.md`
   4. `docs/Magnetrail_DESIGN.md`
   5. `docs/Magnetrail_Game_Design_Spec_v0.1.docx`
4. Run the existing M0 tests before implementation and record the result.
5. Summarize the current architecture and your M1 implementation plan before editing.

Do not replace working M0 APIs merely to match an example name in this prompt. Adapt the app layer to the real engine API. Do not silently change gameplay rules. If a material contradiction remains after reading the authoritative documents, stop and report it.

## M1 objective

Build a **gray-box, portrait-first Jetpack Compose Android prototype** in `:app` that makes all 12 solver-verified prototype levels playable on a real device or emulator.

M1 must prove the complete interaction loop:

- Load a level from the canonical JSON asset.
- Render its board and entities with Compose Canvas.
- Tap a remaining arrow using forgiving hit-testing.
- Ask the production `GameEngine` for one `ResolutionResult`.
- Animate only what that result describes.
- Commit the resulting immutable board state.
- Support undo and restart.
- Detect win and deadlock.
- Let the tester reach and replay every one of the 12 prototype levels.

This is a functional gray-box milestone, not final production art.

## Non-negotiable architecture

### Source of truth

- `:game-core` remains the only source of gameplay truth.
- The Compose UI, Canvas renderer, animation layer, and ViewModel must **never** recalculate line of sight, controlling magnets, effective direction, collision, success, polarity changes, win, or deadlock.
- On a tap, call the production engine exactly once for that action and retain the returned `ResolutionResult` as the complete animation script.
- Do not duplicate the rules in UI code, animation code, previews, or tests.
- Do not add Android or Compose dependencies to `:game-core`.

### State ownership

Create a screen-level state holder, preferably an Android `ViewModel` exposing immutable `StateFlow` UI state. Keep at least:

- Current level identity and definition
- Current committed `BoardState`
- Initial board state for restart
- Undo history of committed board states
- Current in-flight `ResolutionResult`, if any
- Animation phase/progress or an equivalent animation model
- Input-enabled flag
- Win/completion state
- Deadlock state

Compose renders state and emits user intents. The ViewModel/state holder calls the engine and coordinates state transitions. Avoid passing mutable collections into UI state.

### Turn transaction

Use this order for every arrow tap:

1. Ignore the tap if input is disabled, the board is complete, or an animation is already running.
2. Resolve the selected arrow through the production engine.
3. Store the returned `ResolutionResult` and disable board input.
4. Animate the selected arrow along `traversedCells` and toward the terminal event described by the result.
5. For a successful controlled move, animate the documented polarity change only after or as the route completes.
6. For a collision or invalid Pull exit, show impact/invalid feedback and return to the unchanged original state.
7. Commit `resultingState` only at the defined completion point; never partially mutate domain state during animation.
8. Clear the in-flight result, recompute screen flags only from the committed result/engine outputs, and re-enable input unless completion blocks it.

If lifecycle interruption or recomposition occurs, the board must never enter a state that the engine did not produce. Use stable keys and saveable screen identity where appropriate. Do not attempt to serialize animation objects.

## Required implementation

### 1. Android app shell

- Use Kotlin and Jetpack Compose with the repository's existing conventions and current compatible stable dependencies.
- Preserve the package/application ID unless the repository is clearly unfinished and documents specify another value.
- Keep M1 offline and permission-light. Do not add Internet permission.
- Support portrait layouts from approximately 360 dp to 430 dp wide.
- Edge-to-edge is acceptable, but system bars and bottom controls must remain safely inset.

### 2. Canonical level asset

- Place or expose `Magnetrail_Prototype_Levels_v1.json` at `app/src/main/assets/levels/magnetrail_prototype_levels_v1.json`.
- Keep one clearly canonical authored JSON file. If Gradle wiring can reuse the docs source without fragile absolute paths, do so; otherwise document the copy/update process and add a test that detects divergence.
- Load, parse, and validate levels through the M0 level contracts. Do not redefine DTOs in `:app`.
- Surface a clear developer-facing error if the asset cannot load or validate; do not silently substitute hardcoded boards.

### 3. Playable screen

Implement a simple Compose gameplay screen containing:

- Compact top bar with current level number and a back/level-selection affordance
- Optional short rule/status label for prototype testing
- Centered square board as the dominant element
- Arrow progress such as `Arrows 4/6` or equivalent
- Undo and Restart controls
- A restrained way to open any of the 12 prototype levels
- Win presentation with `Board cleared`, Replay, and Next level
- Deadlock presentation with a concise message plus Undo and Restart access

Do not implement stars, move grading, coins, hints, ads, daily challenge, streak, accounts, store, lives, settings, or onboarding in M1.

### 4. Compose Canvas board

Render the board with a custom Compose `Canvas` or an equally direct Compose drawing primitive. Do not build the grid from dozens of raised composable tiles.

Create a single board geometry/transform object used by both drawing and pointer hit-testing. It must calculate:

- Board bounds
- Cell size
- Cell center from a core position
- Core position from a pointer coordinate
- Route points for animation
- Exit point beyond each board edge

The renderer must support every entity present in the 12 levels:

- Empty cells and subtle grid
- Short, thick cardinal arrows with a clear triangular head
- Pull and Push magnets with different color **and** directional geometry/label
- Walls as dense dark blockers
- Selected/moving arrow
- Traversed path/trail
- Collision impact marker

Use the existing design tokens as a gray-box baseline:

- Background `#F4F7FB`
- Board surface `#FFFFFF`
- Arrow/navy `#183153`
- Pull cyan `#18A7B8`
- Push amber `#E79A2D`
- Wall `#39414D`
- Error `#B84343`

Use a system sans-serif fallback if Manrope is not already available. Do not add a network font dependency. Final Rail Dart artwork, refined magnetic fields, particles, audio, and haptics belong to M2.

### 5. Hit-testing and interaction

- Only remaining arrows are actionable.
- Derive the selected arrow from the tapped board cell using the shared board transform.
- Provide forgiving effective hit targets without making adjacent cells ambiguous.
- Ignore taps outside the board and on non-arrow entities.
- Prevent double-taps and concurrent turns while an animation is active.
- Provide visible pressed/selected acknowledgment quickly.
- Add accessibility semantics so arrows can be discovered and activated without relying solely on Canvas pixels. A transparent semantic overlay aligned from the same board geometry is acceptable.
- Give controls meaningful content descriptions and maintain approximately 48 dp control targets.

### 6. Result-driven animation

Animation timing may be approximate in M1, but behavior must be readable and deterministic:

- Traverse cells in the exact order returned by `ResolutionResult.traversedCells`.
- Use roughly 70–110 ms per traversed cell, with a short upper bound for long routes.
- Successful unaffected exit: move beyond the correct board edge and remove visually.
- Pull capture: finish at the controlling magnet and remove visually.
- Push exit: move beyond the correct edge and remove visually.
- Successful magnetic move: flip only the magnet identified by the result.
- Collision: reach the reported collision point/edge, show a compact impact indication, then rewind or snap back; domain state remains unchanged.
- Invalid Pull exit: communicate invalid movement briefly and restore the original visual state.
- Completion appears only after the final movement animation finishes.

The moving arrow may need to be drawn from the result's original state while the static layer still shows the committed state. Avoid a one-frame disappearance before travel begins.

Provide a reduced-motion-friendly internal timing switch or animation policy if straightforward, but do not build the M2 Settings UI yet.

### 7. Undo, restart, and level navigation

- Push the pre-turn committed state onto undo history only when a successful action is committed.
- Undo restores exactly the previous engine-produced state, including all magnet polarities.
- Failed actions do not create undo entries.
- Restart restores the validated initial state and clears history, in-flight animation, win, and deadlock presentation.
- Disable Undo when history is empty or a turn is animating.
- Level change resets state and history cleanly.
- Next level advances through the 12-level catalog; after level 12, offer replay or return to level selection rather than inventing more content.
- M1 progress does not need to survive process death. Local persistence is M2/M3 work.

## Testing requirements

Keep all M0 tests passing and add focused tests at the appropriate layer.

### Local unit tests

Test the ViewModel/state reducer with a fake animation-completion boundary where possible:

- Initial level loads correctly
- Valid tap creates one in-flight result and disables input
- Second tap during animation is ignored
- Successful completion commits exactly `resultingState`
- Failed action preserves original state and does not add undo history
- Successful controlled action exposes the correct polarity change
- Undo restores arrow and magnet polarity together
- Restart restores the exact initial state and clears history
- Level change resets state
- Win appears after, not before, final animation completion
- Deadlock state is surfaced without mutating the board

Use the production M0 engine for integration-style state-holder tests unless isolation genuinely requires a small interface fake. Never create a fake that reimplements game rules.

### Compose/UI tests

Add a small, stable set of UI tests for:

- Gameplay screen launches and shows Level 1
- An arrow exposes an accessible action/description
- Undo is disabled initially and enabled after a committed successful move
- Restart returns to the initial rendered/semantic arrow count
- Level selection can open at least Level 12
- Completion UI exposes Replay and Next level where applicable

Do not write fragile screenshot/pixel tests in M1. Prefer semantics and state assertions.

### Manual verification matrix

Document a short manual QA checklist covering at least:

- One unaffected exit
- One Pull capture
- One Push exit
- One collision/failure
- One polarity flip
- Undo after a polarity flip
- Restart after multiple moves
- Win flow
- Deadlock flow if a reachable prototype state demonstrates it
- All 12 levels open and accept input
- 360 dp and 430 dp portrait widths

## Suggested app-layer organization

Fit the repository's conventions; do not reorganize working M0 code unnecessarily. A reasonable shape is:

```text
app/src/main/kotlin/.../magnetrail/
├── MainActivity.kt
├── data/
│   └── AssetLevelCatalog.kt
├── game/
│   ├── GameScreen.kt
│   ├── GameUiState.kt
│   ├── GameViewModel.kt
│   ├── GameAction.kt
│   └── BoardGeometry.kt
├── game/render/
│   ├── MagnetrailBoard.kt
│   ├── BoardRenderer.kt
│   └── TurnAnimation.kt
├── levels/
│   └── LevelSelectionScreen.kt
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

Names may change to match the existing project. Favor small, cohesive files; avoid speculative frameworks, navigation libraries, dependency-injection frameworks, or a multi-module UI architecture unless already present.

## Explicitly out of scope

Do **not** add any of the following in M1:

- Changes to the frozen gameplay rules
- Procedural generation or new levels
- Production hint solver UI
- Coins, rewards, stars, move scoring, streaks, daily challenge
- DataStore, Room, cloud sync, account, backend, Firebase
- Ads, consent SDK, billing, analytics, crash reporting
- Sound, music, or haptics
- Final logo, final artwork, elaborate particles, or store assets
- Dark theme or complete Settings screen
- Internet access or remote content
- Release signing or Play Store submission work

Do not delete or weaken M0 tests to make M1 pass.

## Verification commands

Use the Gradle wrapper and the narrowest tasks supported by the actual project. At minimum, run the equivalents of:

```bash
./gradlew :game-core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Run connected Compose tests if an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

If no emulator is available, state that clearly; do not report connected tests as passed. Diagnose build or environment blockers precisely instead of fabricating results.

## M1 definition of done

M1 is complete only when all of the following are true:

- Existing M0 tests still pass.
- The debug app builds and launches.
- All 12 canonical prototype levels load from JSON and are reachable in the UI.
- Every remaining arrow is tappable through shared geometry-based hit-testing.
- Every turn is resolved by the production engine and animated from its `ResolutionResult`.
- UI code contains no duplicate magnet, route, collision, win, or deadlock rules.
- Success, failure, Pull capture, Push exit, unaffected exit, and polarity flip are visibly distinguishable.
- Input is locked during a turn and no overlapping actions can corrupt state.
- Undo restores the exact previous board including polarity.
- Restart restores the exact initial board.
- Win and deadlock states are usable and non-destructive.
- Core and app unit tests pass; the Android debug build succeeds.
- Manual QA notes and any unrun device tests are documented.

## Final response

Report:

1. What was implemented.
2. Key files created or changed.
3. How the app consumes `GameEngine` and `ResolutionResult` without duplicating rules.
4. Test/build commands run and exact results.
5. Manual checks completed and any device/emulator limitation.
6. Any ambiguity, compromise, or deferred issue.
7. The exact next safe milestone: **M2 interaction and presentation polish**—Rail Dart visual refinement, magnetic field motion, sound/haptics, accessibility settings, hints, and local progress—without ads or procedural generation yet.

Begin by inspecting the repository, reading the docs, running the M0 tests, and summarizing your implementation plan. Then proceed unless a material blocker is found.
