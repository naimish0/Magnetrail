# M5.3 proposed migration

Status: **NOT APPLIED — PROMOTION AWAITS OWNER APPROVAL**

Levels 1–150 and their fingerprints remain byte-for-board preserved in the proposal. New stable IDs are `campaign-151` through `campaign-200`; no old ID is renumbered or replaced. On approved promotion, content advances 5→6 and generator 3→4.

Required implementation after approval: completing Level 150 unlocks 151 exactly once; Continue/Next cross 150→151; Level 200 clamps cleanly; schema-6 records, rewards, economy, Daily state, settings, consent, and ad state remain intact. No production migration or campaign JSON is changed by this proposal.
