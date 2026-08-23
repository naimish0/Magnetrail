# Phase 0 migration report

Status: **IMPLEMENTED AND TESTED**

The approved campaign migration advances content version 4→5 and generator version 2→3 while
preserving all 150 stable level IDs and campaign numbers. The exact pre-migration catalog is
retained in `PHASE0_SOURCE_CONTENT_V4.json`; the machine-readable 150-row old/new fingerprint
map is `PHASE0_CONTENT_MIGRATION.json`.

## Player-data policy

Player preference schema 6 separates earned progress from board-specific performance:

1. completion, best stars, first-clear rewards, unlock position, coins, settings, Daily state,
   consent state, and ad state remain attached to the stable level ID;
2. old move, overload, and hint minima are archived as a `LegacyLevelRecord` under the v4
   board fingerprint and source content version;
3. the active `LevelRecord` receives the v5 fingerprint and starts with empty, comparable
   move/overload/hint minima;
4. replaying a previously completed stable ID never grants the first-clear reward again;
5. repeated preference reads and process restart do not add duplicate legacy records.

Each promoted level embeds its exact v4 fingerprint as `previousContentFingerprint`. Final
certification verified all 150 embedded mappings against the immutable v4 snapshot and the
owner-approved remediation report; no fingerprint was inferred.

## Verification evidence

The focused migration test starts from schema 5/content 4 data containing completion, stars,
best moves, first-clear state, coins, and settings. It verifies schema 6/content 5 output,
preservation of earned values, archival of the v4 best record, fresh v5 board metrics, no replay
reward, idempotent reads, and restart persistence. Existing migration tests continue to cover
older supported preference schemas.

Automated migration evidence proves persistence invariants. It does not claim that a real
player account was manually upgraded or that any level was human-playtested.
