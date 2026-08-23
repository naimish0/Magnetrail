# Magnetrail Privacy Policy

Last updated: 23 August 2026

Status: deployable policy text with a configured public HTTPS URL. The publisher must still verify that the hosted page matches the exact release binary and complete the applicable legal and Google Play review before publication.

## Overview

Magnetrail is an offline-first puzzle game. It does not require an account. Numbered-campaign progress, Auto Journey boards and uniqueness history, settings, rewards, and local records are stored on the device.

The app includes third-party Google software for advertising and consent. A production build may also use Firebase Analytics and Firebase Crashlytics if the owner supplies a valid Firebase configuration and the player enables optional diagnostics under an applicable consent state. These services can transmit data even though puzzle gameplay itself works offline.

## Data stored locally

Magnetrail stores the following in private app storage:

- campaign, Daily, Infinite, and Auto Journey progress and records;
- settings, hint/reward balances, and ad-cadence state;
- consent-managed state maintained by Google's consent software;
- exact generated Auto Journey boards, identities, certification receipts, and semantic fingerprints so a board can be restored after process death and local duplicates can be avoided;
- debug-only blinded playtest sessions and optional comments when the debug playtest feature is used.

Magnetrail has no account or cloud-sync system. Android cloud backup and device-to-device transfer are disabled for app data. Clearing app storage or uninstalling deletes Magnetrail's local progress and resets local Auto Journey history. An upgrade install is designed to preserve compatible local progress.

## Advertising and consent

Magnetrail includes the Google Mobile Ads SDK and Google's User Messaging Platform (UMP). In a correctly configured production build, UMP requests current consent information at launch and presents a privacy message or privacy-options entry point when required. Ads are requested only when UMP reports that ads may be requested. When consent is denied, unavailable, unresolved, or an ad is unavailable, gameplay continues immediately.

Depending on region, consent, device settings, and Google configuration, Google and its advertising partners may process data such as:

- device or advertising identifiers and app-instance identifiers;
- IP-address-derived approximate location;
- app interactions, ad impressions, clicks, and performance information;
- device, diagnostic, security, and fraud-prevention information.

This processing may be used to deliver, limit, personalize where permitted, measure, and protect advertising. Non-personalized ads may still use identifiers or local storage for frequency capping and aggregated reporting. Google acts under its own terms and retention practices. See [Google's privacy policy](https://policies.google.com/privacy), [Google Mobile Ads data disclosures](https://developers.google.com/admob/android/privacy/play-data-disclosure), and [Google's ad-serving modes](https://developers.google.com/admob/android/privacy/ad-serving-modes).

Rewarded ads are optional and may grant one safe-move hint or an eligible Campaign or Infinite level skip only after Google's SDK confirms that the rewarded ad was completed. Magnetrail applies no daily limit to rewarded-ad use, although an earned hint must be used before another hint credit can be held.

Campaign Levels 1–10 are excluded from interstitial counting. Completing Campaign Level 11 creates the first Campaign interstitial opportunity. After that boundary, each five eligible first-time completions across Campaign, normal Infinite, and Auto Journey create another opportunity. Daily Challenges, human playtests, replays, failed attempts, hints, and rewarded-ad skips do not count.

An eligible interstitial is evaluated immediately after the solved board is safely recorded and may appear over the Celebration screen; it is not triggered by the Next button. Magnetrail applies no daily interstitial limit or general interstitial cooldown. An interstitial is suppressed for 60 seconds after—and only after—a rewarded ad is completed and its reward is earned. Missing consent, an unavailable ad, or another failed eligibility check never blocks gameplay, and a skipped opportunity creates no delayed “ad debt.”

## Optional usage and crash diagnostics

Firebase Analytics and Firebase Crashlytics libraries are included. In this repository, automatic collection is disabled in the Android manifest. Collection is enabled only when all of the following apply:

1. the production binary contains a valid Firebase configuration;
2. the player enables **Usage & crash diagnostics** in Settings; and
3. the applicable consent state permits collection.

When enabled, Firebase Analytics may process coarse app-use events and associated device/app identifiers and metadata. Firebase Crashlytics may process crash and ANR stack traces, relevant app state, device metadata, and a Crashlytics installation identifier. Magnetrail's event allow-list does not intentionally include canonical board state, free-text playtest comments, raw consent strings, or advertising identifiers. See [Firebase privacy and security](https://firebase.google.com/support/privacy) and [Firebase's Android data-disclosure guidance](https://firebase.google.com/docs/android/play-data-disclosure).

Optional diagnostics can be disabled in Settings. The app asks Crashlytics to delete unsent reports when collection is disabled. Data already received by Google is subject to Google's retention and deletion controls.

## User-initiated sharing

The Celebration screen includes a Share button. When selected, Magnetrail captures the visible game screen as a PNG in temporary app cache and opens the Android Sharesheet with that screenshot and the Magnetrail Google Play URL. Magnetrail does not select a recipient or upload the screenshot itself. The app or service selected by the player receives the shared content and handles it under its own privacy policy. The cached screenshot may remain until it is replaced, app cache is cleared, or the app is uninstalled.

## Sharing and purposes

Magnetrail does not sell an account profile because it does not operate an account system. Data may be shared with or processed by Google and its advertising partners through Mobile Ads and UMP for ad delivery, measurement, consent management, security, and fraud prevention. If optional diagnostics are configured and enabled, Google may process Analytics and Crashlytics data to understand app use and diagnose reliability problems.

No other application network client, backend API, social login, payment SDK, or cloud gameplay database was found in the audited source as of the last-updated date.

## Security

The app disables cleartext network traffic, stores progress in private app storage, excludes app data from Android backup and device transfer, limits diagnostic event fields, and requires release builds to fail closed when production advertising, signing, audience, Firebase, or privacy-URL configuration is incomplete. Celebration screenshots are shared through a read-only, temporary content URI rather than broad storage access. No security measure eliminates every risk.

## Children and target audience

The source requires an owner-reviewed `general` target-audience configuration before a production release can enable live services. The repository does not prove the actual Google Play target-audience declaration and does not contain completed age-treatment decisions. The publisher must verify the audience, content-rating, ad-treatment, and consent configuration before distribution. This policy does not claim that Magnetrail is directed to children.

## Your choices and deletion

- Use **Privacy options** in Settings when UMP indicates that the entry point is required.
- Disable **Usage & crash diagnostics** in Settings.
- Choose whether and where to share a Celebration screenshot.
- Clear app storage or uninstall Magnetrail to remove locally stored progress and Auto Journey history.
- Use Google's own privacy and ad controls for data processed by Google.
- Contact the publisher for a privacy request after the verified contact below is configured.

Because Magnetrail has no account, the publisher may not be able to associate a provider-held identifier with an individual without additional information. Provider requests are governed by the provider's processes.

## Retention

Local data remains until it is replaced by normal gameplay, cleared through app storage, or removed by uninstalling. Magnetrail does not currently synchronize it to a developer backend. Google and advertising partners determine retention for data they process; consult their linked policies and the production console configuration.

## Publisher and contact

- Developer/publisher: Naimish Gupta
- Privacy contact: [naimish.app@gmail.com](mailto:naimish.app@gmail.com)
- Postal address: Sandi, Hardoi, 241403, Uttar Pradesh
- Public HTTPS URL for this policy: [https://naimish0.github.io/Magnetrail/](https://naimish0.github.io/Magnetrail/)

Before publication, verify that the hosted page matches this reviewed policy and reconcile it with the exact release binary and Play declarations.
