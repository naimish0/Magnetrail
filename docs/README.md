# Magnetrail Android Project Context

This folder is the source of truth for the Android implementation of Magnetrail.

## Read first

1. `Magnetrail_Rules_Contract.md` — authoritative deterministic gameplay rules.
2. `Magnetrail_Prototype_Levels_v1.json` — the 12 solver-verified prototype levels.
3. `Magnetrail_Android_Technical_Brief.md` — Android/Kotlin architecture and milestone boundaries.
4. `Magnetrail_DESIGN.md` — visual design tokens and screen requirements.
5. `Magnetrail_Game_Design_Spec_v0.1.docx` — complete product and gameplay specification.
6. `CODEX_M0_PROMPT.md` — paste this into Codex to begin implementation.

## Authority order

When files appear to conflict, follow this order:

1. `Magnetrail_Rules_Contract.md`
2. `Magnetrail_Prototype_Levels_v1.json`
3. `Magnetrail_Android_Technical_Brief.md`
4. `Magnetrail_DESIGN.md`
5. `Magnetrail_Game_Design_Spec_v0.1.docx`

Do not silently invent or change game rules. Record proposed rule changes in a decision note before implementation.

## M0 objective

M0 creates a pure Kotlin rules engine, route tracer, JSON level loader, solver and local JVM tests. It intentionally excludes Android UI, animations, audio, ads, analytics, billing, persistence and production artwork.

## M0 completion command

Use the project's Gradle wrapper:

```bash
./gradlew test
```

M0 is complete only when every prototype level loads, every documented solution replays successfully, invalid launches leave state unchanged and the solver finds at least one clean solution for all 12 levels.
