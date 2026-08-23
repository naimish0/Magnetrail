# Magnetrail rollout and hotfix runbook

## Promotion path

1. Freeze package/version and create one signed AAB from a clean, reviewed revision. Record source commit, inputs (names only), certificates, hash, mapping, dependency lock/report, tests, and approvals.
2. Install release-AAB-derived APKs locally, then upload only with explicit owner authorization to internal testing. Review generated APK/device support, signing, App Bundle Explorer, automated integrity, and pre-launch report.
3. Fix or explicitly disposition every pre-launch/device-catalog finding. Promote the same artifact to closed testing.
4. Meet the account-specific closed-test and production-access gates in `CLOSED_TEST_PLAN.md`. Submit production access where Play requires it.
5. Complete App content, pricing/countries, store assets, privacy/support URLs, review notes, developer/package verification, and managed-publishing choice. Record approvals.
6. For a **first production release**, Play does not offer a staged percentage rollout. Use closed testing as the controlled exposure, then make the approved first production release when gates are green.
7. For later updates, use a provisional 5% → 20% → 50% → 100% staged rollout. This is a human decision sequence, not automation.

## Observation gates for update rollouts

Minimum window: 24 hours and enough active-device/session volume at 5% and 20%; 48 hours at 50%, unless the release owner documents why a longer window is required. Never advance on elapsed time alone.

At each stage review user-perceived crash/ANR and device clusters, startup issues, reviews, consent/ad flow, reward ledger, progress migration, level completion/drop-off, pre-launch updates, support reports, and Data safety/listing consistency. Compare to the immediately previous healthy version and Play's current bad-behavior thresholds; low volume means wait or use qualitative evidence, not “zero defects.”

## Halt criteria

Halt further delivery for any widespread startup crash, progress loss, unsolvable shipped level, stuck consent/ad flow, reward duplication/negative balance, interstitial policy breach, privacy/Data safety discrepancy, material crash/ANR regression, or review pattern indicating misleading listing/intrusive ads. The release owner may also halt on a concentrated affected-device issue.

Halting a rollout prevents new delivery but does not uninstall from existing users. A completed rollout cannot restore the old binary for already-updated users, and the first release has special halt limitations. Prepare a higher-version-code hotfix; do not promise instant rollback.

## Hotfix path

- Branch from the exact released source, make the smallest safe change, and increment version code.
- Re-run unit/content/migration/release/lint/manifest/bundletool/16 KB checks and focused regression. Regenerate mapping/profile only through the build; retain the broken and fixed hashes.
- Use the same protected upload-key workflow and verify the signing certificate. Never reuse a previous version code or overwrite provenance files.
- Submit to the fastest eligible Play track/review path with accurate review notes. Do not weaken consent, ad policy, privacy, or progress safeguards to accelerate review.
- Communicate status through the owner-approved support channel; record impact, devices/versions, decision, reviewer, timestamps, and final resolution.

References: [staged rollouts](https://support.google.com/googleplay/android-developer/answer/6346149), [managed publishing](https://support.google.com/googleplay/android-developer/answer/9859654), [pre-launch reports](https://support.google.com/googleplay/android-developer/answer/9842757).

