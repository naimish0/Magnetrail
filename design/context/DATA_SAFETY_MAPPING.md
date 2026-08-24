# Magnetrail Data Safety mapping

Audit date: 24 August 2026
Status: repository-derived worksheet; the owner must validate the exact production binary, AdMob console settings, regional consent messages, target audience, and Google Play answers before submission.

## Audited implementation

| Area | Repository evidence | Effect |
|---|---|---|
| Accounts | No authentication/account SDK or application account model found | No Magnetrail account data |
| Gameplay | `DataStoreProgressRepository`, `DataStoreAutoJourneyRepository` | Progress and generated board history stored locally |
| Backup | `allowBackup=false`, backup and data-extraction rules exclude all domains | No Android cloud backup or device transfer |
| Transport | `usesCleartextTraffic=false` | Cleartext network traffic disabled |
| Advertising | Google Mobile Ads SDK, rewarded and interstitial services | Provider processing must be declared when live ads are enabled |
| Consent | Google UMP, launch refresh, `canRequestAds`, privacy-options entry | Regional consent/configuration must be verified in AdMob |
| Analytics | No analytics SDK or developer analytics backend in this release cycle | No developer analytics collection |
| Crashes | No crash-reporting SDK or developer crash backend in this release cycle | No developer crash-report collection |
| Other network clients | None found in application source | No developer gameplay backend/API observed |

## Candidate Play Data Safety answers

These are mappings, not pre-approved console answers. Google defines “collected,” “shared,” optionality, purposes, and ephemeral processing; answer using the shipped SDK configuration and current Play guidance.

| Play category | Likely source | Collected/shared conditions | Purposes to review |
|---|---|---|---|
| Approximate location | Mobile Ads, derived from IP | When an ad/consent request is made, subject to provider/configuration | Advertising or marketing; fraud prevention/security/compliance |
| Device or other identifiers | Mobile Ads/UMP | Ads when permitted | Advertising; measurement; fraud prevention/security |
| App interactions | Mobile Ads ad events | Ads when used | Advertising/measurement |
| Crash logs | No developer collection path | Not collected by Magnetrail | Not applicable |
| Diagnostics/performance | Mobile Ads provider processing | When ads are used, subject to provider/configuration | Security/fraud prevention; advertising measurement |
| User-provided text | Debug-only local playtest comments | Not intentionally sent by app diagnostic events; debug feature excluded from release | Do not declare as production network collection unless the release changes |
| Gameplay progress | Local DataStore | Stored on device; not sent to a Magnetrail backend | Not collected under current developer-server implementation |

## Required owner/console verification

- Verify the legal publisher identity, privacy contact, active public HTTPS policy URL, and deletion/request process.
- Verify production AdMob app/unit IDs, mediation partners, publisher first-party-ID setting, privacy messages, consent vendors/purposes, limited/non-personalized/personalized modes, and `app-ads.txt`.
- Verify Play target audience, content rating, age treatment, Ads declaration, Advertising ID declaration, and regional requirements.
- Confirm the final dependency report remains free of Firebase and other diagnostics SDKs for this release cycle.
- Re-run the merged-manifest and dependency report for the signed app bundle and reconcile every transitive SDK/permission.
- Complete Play's Data Safety form using current definitions and the exact distributed artifact; do not copy this worksheet blindly.

Primary references: [Google Mobile Ads disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure), [UMP setup](https://developers.google.com/admob/android/privacy), and [Google Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469).
