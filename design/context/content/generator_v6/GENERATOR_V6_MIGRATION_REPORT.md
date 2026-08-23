# Generator V6 migration report

Status: **NOT_RUN — AWAITING_HUMAN_CALIBRATION**

No production migration is authorized until the five-band human model and final production sample
are certified. Campaign V10 remains byte-identical.

The implemented migration assessor enumerates `KEEP`, `REPLACE`, and `APPEND`; requires stable IDs,
ordering, and `previousContentFingerprint`; preserves completion, stars, rewards, unlocks, settings,
and balance; moves changed-board performance into fingerprint-bound legacy records; and restarts a
changed in-progress board without deleting earned value. Existing persistence uses the same
fingerprint-aware record archive.

Automated coverage includes fresh install, partial and complete progress, current-board
replacement, changed fingerprints, stable-ID removal/downgrade rejection, legacy record
preservation, interrupted promotion, and rollback verification. These tests validate the
infrastructure, not a not-yet-generated production candidate.
