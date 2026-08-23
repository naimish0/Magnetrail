# Generator V6 guarded promotion contract

Contract version: `generator-v6-promotion-five-band-2`.

Promotion accepts only the five-band V6 model and catalogs. A V6 Master label or bucket is a
contract violation. Existing V10/V11 metadata is historical input and is not rewritten before a
certified promotion.

`promoteGeneratorV6Campaign` is a non-default task and is never a dependency of build, test, lint,
assemble or certification. It refuses unless all of these exact inputs are supplied:

- `confirmGeneratorV6Promotion=true`;
- expected current production SHA-256;
- expected current content version;
- exact staged candidate SHA-256;
- technical-certificate SHA-256;
- human-model certificate SHA-256;
- final production-sample certificate SHA-256;
- promotion-manifest SHA-256.

The manifest and all three certificates must independently state `CAMPAIGN_CERTIFIED`. Hashes are
recomputed from bytes immediately before mutation. The current production catalog must still equal
the expected V10 bytes and content version 10.

Before replacement the task creates immutable V10 source and rollback artifacts, validates the
entire staged schema, enumerates every `KEEP`, `REPLACE` and `APPEND`, verifies stable ID/order rules,
and proves migration instructions. Target content version is 12 after source verification.
Generator version is 6 only on V6 boards. Every changed board records
`previousContentFingerprint`.

Migration preserves completion, stars, rewards, unlocks and settings. Fingerprint-bound records
for changed boards move to the existing legacy-record representation. An in-progress changed board
restarts safely without deleting earned value. Infinite, Daily and V11 hashes must remain unchanged.
Level 205's waiver remains explicit if that exact board is kept.

The candidate is copied to a same-filesystem temporary path, reparsed and rehashed, then moved over
the campaign with `ATOMIC_MOVE`. Any pre-move failure leaves V10 untouched. A post-move regression
failure restores the exact rollback bytes and records failure; no partially written campaign is a
valid outcome.

Promotion never commits, pushes, creates a branch/PR, signs, uploads or publishes. The complete game
context may be updated only after the promoted bytes pass the full regression and all protected
non-campaign hashes are reverified.
