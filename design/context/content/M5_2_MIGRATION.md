# M5.2 content/progress migration

The promoted catalog moves catalog content version 3 → 4 and generator version 1 → 2 after the completed approval gate. Level IDs and all content fingerprints for Levels 1–100 remain unchanged.

- Progress is keyed by stable level ID. Completion, stars, best actions, overloads, hints, first-clear rewards, coins, Daily history/settings, and ad caps are retained.
- Catalog-size clamping is dynamic. Completing `campaign-100` in the 150-level catalog raises `highestUnlockedLevel` to 101, making `campaign-101` the valid Continue destination.
- Players below Level 100 retain their current highest unlock and selected stable ID.
- New IDs `campaign-101`…`campaign-150` start absent from completion/record/reward sets.
- Completing Level 150 clamps the unlock to 150; `hasNextLevel` is false and no Level 151 is synthesized.
- Repeated migration is idempotent. Unknown/corrupt IDs are filtered while economy, Daily, settings, and monetization state use the existing conservative recovery path.
- Promotion completed only after all 50 approval rows and manual checklist fields were `APPROVED`.
