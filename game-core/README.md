# Magnetrail game core

`game-core` is the Android-free Kotlin/JVM implementation of the frozen
`magnetrail-core-1` rules. It owns immutable board models, deterministic route
tracing and turn resolution, strict JSON loading, catalog validation, and the
solver used to verify levels and produce clean hints.

Run its tests from the repository root:

```bash
./gradlew :game-core:test
```

The authoritative prototype JSON remains in `docs/`; the test source set reads
that directory directly, so the level catalog is not duplicated.

Future app code should parse the catalog with `LevelParser`, create a state with
`LevelDefinition.initialState()`, and send taps to
`GameEngine.resolve(state, PlayerAction(arrowId))`. Rendering and animation must
observe `ResolutionResult` and never independently mutate gameplay state.

`traversedCells` excludes the selected arrow's starting cell and an off-board
exit position. It includes an in-bounds terminal magnet or collision cell so a
renderer has the complete visual route; `terminalEvent` identifies how that
last cell should be animated.

M3 keeps the production engine unchanged and adds pure JVM content utilities:
seeded SplitMix64 generation, solver-backed certification, SHA-256 fingerprints,
difficulty metrics, action/star grading, economy policies, Daily seed identities,
and conservative streak transitions. Candidate generation always delegates route
and state outcomes to `DefaultGameEngine` and `Solver`.

Certify all checked-in M3 content from the repository root:

```bash
./gradlew certifyCampaignContent
```
