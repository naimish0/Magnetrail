# Magnetrail closed-test plan

## Applicability gate

The account owner must first record account type and creation date from Play Console. New personal accounts created after 2023-11-13 may require **12 opted-in testers for 14 continuous days** before applying for production access. Play—not this document—determines applicability and approval. Joining without remaining opted in, merely receiving an invitation, or an elapsed calendar does not prove readiness.

Owner evidence: `[ACCOUNT TYPE/DATE/PLAY REQUIREMENT/SCREENSHOT LINK REQUIRED]`

## Build and tester handling

- Promote one SHA-256-recorded, upload-key-signed AAB through internal testing before closed testing; do not rebuild between tracks.
- Use a consented roster with only the minimum contact/account information needed to grant Play access. Store it outside this repository with restricted access and a deletion date.
- Give testers the matching `TESTER_GUIDE.md` and issue template. Never ask for advertising IDs, consent strings, logs containing personal data, or live-ad clicks.
- Testers must report app version, device model, Android version, network/consent state, and reproducible steps.

## Coverage target

At minimum cover one API 24 device, a representative mid-range device, API 35, API 36, a 16 KB device, phone and tablet/foldable. Include fresh and upgrade installs, offline launch/play, process death, background/rotation, large font/display, gesture/predictive back, TalkBack/switch/keyboard, and reduced motion. The same tester/device may cover several cells, but low/mid/high and accessibility evidence must be explicit.

Every participating tester should complete the tutorial/early campaign, verify Pull/Push and polarity flips, solve an advanced level, open/complete Daily, exercise a hint/coin flow, assess rewarded opt-in/interstitial frequency using test-approved traffic, retry offline, and review settings/accessibility.

## Triage cadence and exit gates

Review feedback and Play vitals at least twice weekly during the test. Link each actionable finding to a defect, disposition, build, and retest evidence. Restart the evidence window if Play indicates the tester continuity requirement was broken.

Exit requires all of the following:

- Applicable Play tester count/time condition is satisfied and console evidence is saved.
- No open release-blocking defect, progress loss, unsolvable content report, consent/ad-policy defect, duplicate reward, or negative coin balance.
- Crash/ANR/vitals evidence is acceptable for the available volume and affected-device clusters are reviewed.
- Representative low/mid/high devices and required accessibility/offline/upgrade paths passed.
- The tested binary, listing, screenshots, privacy policy, and declarations agree.
- Feedback/fixes are reviewed by the owner, and the production-access questionnaire is answered with actual evidence.

If any gate fails, update `RELEASE_BLOCKER_LOG.md`; do not apply for production access merely because 14 days elapsed.

