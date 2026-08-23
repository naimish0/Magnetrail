# Phase 0 per-level report

Status: **PROMOTED AND AUTOMATED-CERTIFIED — HUMAN PLAYTEST PENDING**

The canonical final 150-row report is `PHASE0_FINAL_DIAGNOSTICS.json`. For every stable campaign
ID it records board dimensions and entity counts, origin, approved decision, current and prior
fingerprints, target, production certification, complete Difficulty v3 metrics, target gate,
Quality result, structural near neighbors, Human Review Priority, owner approval, and playtest
status.

Summary:

- levels production-certified with complete Difficulty v3 searches: 150/150;
- target-gate accepted: 150/150;
- exact/symmetry-unique: 150/150;
- Quality: `ACCEPT=81`, `REVIEW=69`, `REJECT=0`;
- canonical guess-dependent choices: 0;
- stable ID and v4→v5 fingerprint mappings verified: 150/150;
- owner-approved assignments: 150/150;
- automated approvals represented as human approval: 0;
- human-playtested assignments: 0/150 (`PENDING`).

`PHASE0_FINAL_DISTRIBUTION_REPORT.md` reports total solution length, forced sequence length,
decision nodes, decision spacing, maximum forced runs, branching, dependencies, and player-choice
classes by campaign range. `PHASE0_FINAL_HUMAN_REVIEW_CHECKLIST.md` orders all levels by the
requested review-priority factors. Owner approval and automated certification are not presented
as substitutes for human playtesting.

The original diagnosis, 450-candidate pool, pre-promotion proposal, and proposed decisions remain
checked in as immutable process evidence. They intentionally retain their historical `PENDING`
labels.
