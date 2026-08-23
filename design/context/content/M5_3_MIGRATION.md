# M5.3 final content and progress migration

Status: **APPLIED AND AUTOMATED-VERIFIED**

- Campaign content 5→6; generator 3→4
- Levels 1–150 stable IDs preserved: 150/150
- Levels 1–150 fingerprints preserved: 150/150
- Added stable IDs: `campaign-151` through `campaign-200` (50)
- Preference schema remains 6; no destructive record migration is required because existing boards did not change
- A completed Level 150 unlocks and selects 151 exactly once; an incomplete Level 150 does not; Level 200 clamps without inventing 201
- Completion, stars, first-clear rewards, currency, Daily state, settings, consent, and ad-frequency state are preserved
