# Magnetrail Android Technical Brief

## Platform

- Android only
- Kotlin
- Jetpack Compose for application UI
- Compose Canvas for board rendering after M0
- Portrait-first prototype
- Offline-first

Use the current stable Android Studio template and dependency versions available when the project is created. Do not hardcode versions from this document.

## Recommended modules

```text
:app
:game-core
```

`:game-core` is a pure Kotlin/JVM module containing models, rules, route tracing, level parsing contracts, solver and tests. It must not depend on Android SDK classes, Compose, ViewModel, Context, resources or lifecycle APIs.

`:app` will later contain Compose UI, assets, ViewModels, local persistence, audio, haptics and monetization integrations.

If the repository begins as a single-module Android project, create `:game-core` during M0 rather than placing rules inside the app module.

## M0 package layout

```text
game-core/src/main/kotlin/.../magnetrail/core/
├── model/
│   ├── Position.kt
│   ├── Direction.kt
│   ├── Polarity.kt
│   ├── Arrow.kt
│   ├── Magnet.kt
│   ├── Wall.kt
│   ├── BoardState.kt
│   └── LevelDefinition.kt
├── engine/
│   ├── PlayerAction.kt
│   ├── TerminalEvent.kt
│   ├── ResolutionResult.kt
│   ├── RouteTracer.kt
│   └── GameEngine.kt
├── level/
│   ├── LevelCatalog.kt
│   ├── LevelParser.kt
│   └── LevelValidation.kt
└── solver/
    ├── Solver.kt
    ├── SolverResult.kt
    └── StateKey.kt
```

## Modeling rules

- Prefer immutable Kotlin data classes.
- Prefer sealed interfaces or enums for finite domain states.
- Keep authored IDs stable through parsing and resolution.
- Validate bounds, duplicate IDs, duplicate cells, invalid directions and invalid polarity during load.
- Make illegal authored levels fail fast with useful messages.
- Define structural equality for all state and result types.
- Keep rendering coordinates out of core models.

## Engine API

The public API should be close to:

```kotlin
interface GameEngine {
    fun resolve(state: BoardState, action: PlayerAction): ResolutionResult
    fun validActions(state: BoardState): List<PlayerAction>
    fun isDeadlocked(state: BoardState): Boolean
}
```

Do not mutate the input state.

## Solver

Implement breadth-first search or depth-first search with cycle protection. Because every successful action removes one arrow, the graph is acyclic by arrow count, but polarity creates distinct states that require a complete state key.

The state key must include:

- Remaining arrow IDs and their positions/directions
- Every magnet ID and current polarity
- Static board identity or static entity layout

The solver must use the production `GameEngine`; it must not reimplement gameplay rules.

Return at least:

- Solvable flag
- One clean solution
- Solution count up to a configurable cap
- Valid first actions
- Explored state count

## JSON loading

Use Kotlin serialization in the Android project. Keep DTOs separate from validated domain models. The JSON file should eventually live at:

```text
app/src/main/assets/levels/magnetrail_prototype_levels_v1.json
```

For M0 local JVM tests, load it as a test resource or copy it into `game-core/src/test/resources`.

## Required tests

- Coordinate and direction movement
- Line-of-sight blocking by arrow, wall and magnet
- Nearest magnet selection
- Equal-distance cancellation
- Pull capture
- Push exit
- Unaffected exit
- Collision with each entity type
- Invalid Pull exit
- Successful polarity flip
- No polarity flip on unaffected success
- No state change on failure
- Win detection
- Deadlock detection
- JSON validation failures
- Replay every documented designed solution
- Solver finds all 12 levels solvable

Use parameterized tests where they improve clarity. Tests should describe behavior, not implementation details.

## Not in M0

- Compose UI or Canvas
- ViewModel or StateFlow
- Animation
- Sound or haptics
- DataStore or Room
- Ads, billing or consent
- Analytics or crash reporting
- Daily challenge
- Generator
- Production solver hints in UI

## M1 preview

After M0 passes, M1 will add a gray-box Compose screen, Canvas board, tap hit-testing, route animation driven entirely by `ResolutionResult`, undo/restart, and the first 12 levels. Do not begin M1 in the M0 change unless explicitly instructed.
