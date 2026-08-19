# Magnetrail M5 release requirements

Research date: **2026-08-19**. Re-check every dated policy immediately before Play submission. This is an engineering checklist, not legal advice.

## Frozen repository identity

| Item | M5 candidate | Gate |
|---|---|---|
| Package / namespace | `com.rameshta.magnetrail` | Owner must confirm it is permanent and not already registered elsewhere. |
| Name / locale | Magnetrail / en-US | Owner approval required. Do not publish machine translations. |
| Version | code `1`, name `1.0` | Owner must confirm version code 1 has never been uploaded. |
| SDK | min 24, target 36, compile 37 | Repository-verified; device/Play checks remain. |
| Listing | Game / Puzzle; free; contains ads | Owner and Play Console confirmation required. |
| Audience | Undecided | Live ads and production upload remain blocked. |

The version is centralized in `gradle.properties`. Increment `magnetrail.versionCode` for every artifact ever uploaded to Play, including rejected/internal artifacts that Play has accepted. Do not change the application ID after app creation.

## Current platform and Play requirements

- Google Play requires new phone/tablet apps and updates to target Android 16/API 36 beginning **2026-08-31**; the documented extension deadline is **2026-11-01**. Magnetrail targets 36. [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- Apps targeting API 35 or later must support 16 KB page-size devices. Google's current page notes the latest enforcement timing and provides bundle/APK checks; verify it again at upload time. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes)
- Android 16 changes include edge-to-edge behavior, predictive back, and large-screen behavior. The app must be manually checked on phone, tablet/foldable, gesture navigation, and large font/display modes. [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- New Play apps use Android App Bundles and Play App Signing. An upload key still signs the uploaded bundle; Play protects the app-signing key. No key is created or enrolled by this repository work. [App signing](https://developer.android.com/studio/publish/app-signing) · [Prepare for release](https://developer.android.com/studio/publish/preparing)
- The compressed download limit for device-specific APKs generated from an app bundle is currently 200 MB. The final split sizes must be reviewed in Play before rollout. [Play app size limits](https://support.google.com/googleplay/android-developer/answer/9859152)
- Developer/package verification may require owner action during 2026. Play-distributed apps are generally registered through Play, but the owner must confirm the account/package status before the applicable deadline. [Android developer verification FAQ](https://developer.android.com/developer-verification/guides/faq)

## Store, privacy, and testing requirements

- Current main-store limits are 30 characters for the app name, 80 for the short description, and 4,000 for the full description. A Play icon is 512×512, 32-bit PNG, at most 1,024 KB; a feature graphic is 1,024×500 JPEG or 24-bit PNG without alpha. [Store listing setup](https://support.google.com/googleplay/android-developer/answer/9859152) · [Graphic assets](https://support.google.com/googleplay/android-developer/answer/9866151)
- Data safety must include collection/sharing performed by embedded SDKs. A public privacy policy is required and must match the binary and console declarations. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- App content requires accurate Ads, app access, target audience/content, content rating, and privacy declarations. [App content](https://support.google.com/googleplay/android-developer/answer/9859455) · [Ads](https://support.google.com/googleplay/android-developer/answer/9857753) · [Target audience](https://support.google.com/googleplay/android-developer/answer/9867159) · [Content rating](https://support.google.com/googleplay/android-developer/answer/9859655)
- A new personal developer account created after **2023-11-13** may need a closed test with at least 12 opted-in testers continuously for 14 days before applying for production access. Account applicability and approval are Play decisions; installs alone do not establish readiness. [Production access requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- Staged rollout applies to updates, not an app's first production release. Halting prevents additional delivery but does not uninstall from existing users. The owner must choose the first-release approach and a hotfix path. [Release rollout](https://support.google.com/googleplay/android-developer/answer/6346149)

## SDK disclosure sources

- Google Mobile Ads SDK 25.4.0 disclosures include approximate location derived from IP, product interactions, diagnostics, and identifiers for advertising, analytics, and fraud/security purposes; the exact console answers depend on configuration and use. [AdMob Play data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- UMP 4.0.0 must request updated consent information each launch where production monetization is enabled, honor `canRequestAds`, and expose privacy options when required. [UMP setup](https://developers.google.com/admob/android/privacy) · [Consent mode](https://developers.google.com/admob/android/privacy/consent-mode)
- Firebase disclosures vary by product. Crashlytics can process stack traces, application/device state and installation identifiers; Analytics processes event and device/identifier data. Console retention and data-sharing choices must be recorded. [Firebase Play disclosure](https://firebase.google.com/docs/android/play-data-disclosure) · [Firebase privacy](https://firebase.google.com/support/privacy)
- AdMob publishers may need an authorized seller entry at `app-ads.txt`; new-app verification rules also require an owner/console check. [app-ads.txt setup](https://support.google.com/admob/answer/9363762) · [app verification](https://support.google.com/admob/answer/14538460)

## Engineering choices and deliberate limits

- `compileSdk 37` resolves the AndroidX 1.19.0/2.11.0 min-compile requirement while `targetSdk 36` opts into the current intended runtime behavior.
- Mobile Ads 25.4.0 transitively requested obsolete WorkManager 2.7.0, which crashed the R8-optimized benchmark build on Android 16 during `WorkDatabase` initialization. The app pins current stable WorkManager 2.11.2 (min SDK 23, compatible with Magnetrail's min 24) and release-startup testing guards the fix. [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
- Release builds enable R8 optimization and resource shrinking, disable cleartext and backup, verify the merged manifest, reject sample IDs in the release artifact, and stay structurally non-monetized until all production inputs are valid.
- The Baseline Profile Gradle plugin is pinned to `1.5.0-rc01` because stable 1.4.1 rejects the AGP 9.1 application model. ProfileInstaller remains stable 1.4.1. This release-candidate dependency must be revisited before submission against [Google Maven](https://maven.google.com/web/index.html?q=baselineprofile) and [Baseline Profile guidance](https://developer.android.com/topic/performance/baselineprofiles/overview).
- No notification icon is added because the app has no notification feature.
- No upload, key generation, console mutation, live-ad impression, or publishing operation is authorized by M5.
