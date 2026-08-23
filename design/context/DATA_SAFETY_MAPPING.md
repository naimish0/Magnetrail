# Magnetrail Data Safety mapping

Audit date: 22 August 2026  
Status: repository-derived worksheet; the owner must validate the exact production binary, AdMob/Firebase console settings, regional consent messages, target audience, and Google Play answers before submission.

## Audited implementation

| Area | Repository evidence | Effect |
|---|---|---|
| Accounts | No authentication/account SDK or application account model found | No Magnetrail account data |
| Gameplay | `DataStoreProgressRepository`, `DataStoreAutoJourneyRepository` | Progress and generated board history stored locally |
| Backup | `allowBackup=false`, backup and data-extraction rules exclude all domains | No Android cloud backup or device transfer |
| Transport | `usesCleartextTraffic=false` | Cleartext network traffic disabled |
| Advertising | Google Mobile Ads SDK, rewarded and interstitial services | Provider processing must be declared when live ads are enabled |
| Consent | Google UMP, launch refresh, `canRequestAds`, privacy-options entry | Regional consent/configuration must be verified in AdMob |
| Analytics | Firebase Analytics dependency; manifest collection disabled; local opt-in plus consent gate | Declare only for a binary with real Firebase configuration, but SDK presence/configuration must be reviewed |
| Crashes | Firebase Crashlytics dependency; manifest collection disabled; same gate | Crash/ANR, app state, device metadata, and installation ID may be processed when enabled |
| Other network clients | None found in application source | No developer gameplay backend/API observed |

## Candidate Play Data Safety answers

These are mappings, not pre-approved console answers. Google defines “collected,” “shared,” optionality, purposes, and ephemeral processing; answer using the shipped SDK configuration and current Play guidance.

| Play category | Likely source | Collected/shared conditions | Purposes to review |
|---|---|---|---|
| Approximate location | Mobile Ads, derived from IP | When an ad/consent request is made, subject to provider/configuration | Advertising or marketing; fraud prevention/security/compliance |
| Device or other identifiers | Mobile Ads/UMP; Firebase installations/Analytics/Crashlytics | Ads when permitted; diagnostics only when configured, opted in, and consent-permitted | Advertising; analytics; fraud prevention; app functionality/reliability |
| App interactions | Mobile Ads ad events; optional Analytics coarse allow-listed events | Ads when used; Analytics only when diagnostics gate passes | Advertising/measurement; analytics |
| Crash logs | Optional Crashlytics | Only configured + player diagnostics opt-in + applicable consent | Analytics; app functionality/reliability |
| Diagnostics/performance | Mobile Ads; optional Crashlytics/Analytics | As above | Security/fraud prevention; analytics; app functionality |
| User-provided text | Debug-only local playtest comments | Not intentionally sent by app diagnostic events; debug feature excluded from release | Do not declare as production network collection unless the release changes |
| Gameplay progress | Local DataStore | Stored on device; not sent to a Magnetrail backend | Not collected under current developer-server implementation |

## Required owner/console verification

- Verify the legal publisher identity, privacy contact, active public HTTPS policy URL, and deletion/request process.
- Verify production AdMob app/unit IDs, mediation partners, publisher first-party-ID setting, privacy messages, consent vendors/purposes, limited/non-personalized/personalized modes, and `app-ads.txt`.
- Verify Play target audience, content rating, age treatment, Ads declaration, Advertising ID declaration, and regional requirements.
- Verify the real Firebase project, Analytics data-sharing and retention settings, Crashlytics retention/export configuration, and whether optional diagnostics will ship.
- Re-run the merged-manifest and dependency report for the signed app bundle and reconcile every transitive SDK/permission.
- Complete Play's Data Safety form using current definitions and the exact distributed artifact; do not copy this worksheet blindly.

Primary references: [Google Mobile Ads disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure), [UMP setup](https://developers.google.com/admob/android/privacy), [Firebase Android disclosure](https://firebase.google.com/docs/android/play-data-disclosure), and [Google Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469).
