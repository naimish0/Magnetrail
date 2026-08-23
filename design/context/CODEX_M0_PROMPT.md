# Codex Prompt — Magnetrail M0

Copy everything below into Codex from the root of a newly created Android Studio repository.

---

You are implementing M0 for **Magnetrail**, an Android-only deterministic arrow-and-magnet logic puzzle.

First inspect the repository and read every file under `docs/`. Treat these as authoritative in this order:

1. `docs/Magnetrail_Rules_Contract.md`
2. `docs/Magnetrail_Prototype_Levels_v1.json`
3. `docs/Magnetrail_Android_Technical_Brief.md`
4. `docs/Magnetrail_DESIGN.md`
5. `docs/Magnetrail_Game_Design_Spec_v0.1.docx`

If the repository contains `AGENTS.md`, read and follow it before making changes. Do not invent or silently change gameplay rules. If a material ambiguity remains after reading the documents, report it before implementation.

## Objective

Build a pure Kotlin/JVM `:game-core` module containing immutable domain models, deterministic turn resolution, JSON level loading contracts, validation, a solver, and comprehensive local unit tests for the 12 prototype levels.

## Required work

1. Inspect the existing Gradle structure and preserve the current Android Studio project conventions.
2. Create `:game-core` as a Kotlin/JVM module if it does not exist.
3. Implement immutable models for position, direction, polarity, arrows, magnets, walls, board state and level definition.
4. Implement `PlayerAction`, `TerminalEvent`, `ResolutionResult`, `RouteTracer` and `GameEngine` exactly according to the rules contract.
5. Ensure `GameEngine.resolve` is a pure deterministic state transition and never mutates its input.
6. Implement level DTOs, parsing and validation suitable for Kotlin serialization.
7. Copy or route `docs/Magnetrail_Prototype_Levels_v1.json` into an appropriate test-resource location without creating a second conflicting source of truth.
8. Implement a solver that calls the production `GameEngine` and returns solvability, one clean solution, capped solution count, valid first actions and explored state count.
9. Implement local JVM unit tests covering all rule branches listed in the technical brief.
10. Add parameterized tests that replay every `designedSolutions` path in the JSON.
11. Add a solver test proving all 12 prototype levels are solvable.
12. Add concise module documentation explaining how to run tests and how the app module should call the engine later.

## Constraints

- Kotlin only.
- No Android framework or Compose dependency in `:game-core`.
- No UI, Canvas, ViewModel, animation, audio, haptics, persistence, ads, billing, analytics, Firebase, daily challenge or procedural generation.
- No real-time physics or random behavior.
- Do not embed level definitions directly in Kotlin when JSON is available.
- Do not duplicate the gameplay rules inside the solver.
- Do not add speculative architecture or dependencies outside M0.
- Prefer straightforward, testable code over abstraction-heavy frameworks.

## Verification

Run the narrowest relevant tests while implementing, then run the complete project test suite using the Gradle wrapper. If the project has no wrapper or cannot execute, diagnose the exact blocker without fabricating a passing result.

Before finishing:

- Confirm every prototype level loads successfully.
- Confirm every documented designed solution succeeds.
- Confirm invalid launches leave board state unchanged.
- Confirm successful controlled launches flip only the controlling magnet.
- Confirm the solver finds at least one clean solution for all 12 levels.
- Confirm `:game-core` has no Android dependencies.
- Review the diff for accidental changes outside M0.

## Final response

Report:

- What was implemented
- Key files created or changed
- Test commands and results
- Any documented ambiguity or deferred decision
- The exact next safe milestone, which should be M1 gray-box Compose rendering—not monetization or production polish

Begin by inspecting the repository and summarizing the implementation plan, then proceed with M0 unless a material blocker is found.
