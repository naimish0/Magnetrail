# Magnetrail M4 manual QA

Use a non-production build, Google official test ad units, and UMP debug geography/test-device support configured locally. Never click production ads.

## Consent and privacy

- Verify consent required, not required, accepted, denied, update-error, and form-error cases.
- Relaunch and confirm one consent-information update per process launch and permitted cached-state behavior.
- When UMP requires it, open Settings → Privacy options and change the choice.
- Confirm denial never blocks Home, campaign, Daily Challenge, coin hints, or navigation.
- Toggle Usage & crash diagnostics and verify Analytics/Crashlytics collection follows both local opt-in and consent.
- Confirm the debug privacy-policy placeholder is non-clickable until a real URL is configured.

## Rewarded hint

- Open Hint and verify `Use 30 coins` remains the predictable first option.
- Verify `Watch an ad for one hint` is enabled only for a loaded, consent-permitted test ad.
- Earn the reward and confirm exactly one solver-backed hint, no coin charge, and no second pending credit.
- Dismiss before the reward callback and confirm no credit.
- Force solver failure/cancellation or kill/relaunch after earning; confirm the pending credit remains and can later be consumed.
- Exercise load/show failure, no-fill, airplane mode, background transition, five-grant daily cap, and local-date rollback.

## Interstitial

- Confirm none before ten lifetime campaign completions, in Daily Challenge, or on replay/deadlock/restart/undo/failure/home/back/launch/resume.
- Confirm the third eligible first-clear boundary is evaluated only after an explicit `Next level` tap.
- Confirm fewer than 120 foreground seconds, a rewarded ad in the last 120 foreground seconds, four ads on the local date, missing consent, no load, background, and overlap all skip immediately.
- Confirm dismissal navigates to the next level and show failure/no-fill/offline navigates without a spinner.
- Rotate, background, and kill around loading/dismissal; confirm no ad appears over the wrong screen.
- Open Google Mobile Ads Ad Inspector only in an authorized test build.

## Firebase and accessibility

- After the owner supplies a test Firebase project, enable diagnostics with valid consent and inspect typed events in DebugView. Confirm no local date, seed, board state, raw consent, ad response, or identifiers.
- Use the controlled non-production Crashlytics verification procedure from the Firebase console setup guide; never crash an automated release build.
- Check TalkBack focus after ad dismissal and navigation, 1.5×–2× font scale, reduced motion, and 360/390/430 dp portrait widths.
- Repeat airplane-mode gameplay, campaign completion, Daily Challenge fallback/cache, and coin hints.

The automated connected UI suite ran on a Pixel 7a AVD/API 17 image with M4 network SDKs forced to no-op (14/14 passed). That is not a substitute for this manual consent/ad/Firebase matrix. Record device, OS/API, consent debug geography, and observed result when running it.
