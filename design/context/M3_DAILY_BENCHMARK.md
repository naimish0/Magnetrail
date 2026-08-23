# Magnetrail M3 Daily benchmark

Host JVM run on 2026-08-19 using Java 21.0.9:

- Sample: 31 fixed local dates beginning 2026-01-01
- Generator/profile: version 1 / `daily-v1`
- Minimum: 0 ms
- Median: 0 ms
- p95: 2 ms
- Maximum: 14 ms
- Solver explored states: 5–7

Command: `./gradlew benchmarkDailyChallenge`.

This is a repeatable workstation JVM measurement, not a lower-end Android
device claim. Daily work runs on `Dispatchers.Default`, is bounded at 48
attempts and 8,000 explored states, caches certified JSON, and falls back to a
bundled seven-board bank. Lower-spec device timing remains a manual QA item.
