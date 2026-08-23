# Magnetrail M5 pre-change release-gap report

Audit date: **2026-08-19 (Asia/Kolkata)**  
Audited revision: `6566888` (`main`)  
Scope: repository and locally available devices only. No Play Console, AdMob, Firebase, signing-key, or publishing action was performed.

## Frozen release identity observed before M5 edits

| Field | Pre-change value | Release assessment |
|---|---|---|
| Application ID / namespace | `com.rameshta.magnetrail` | Treat as permanent once the first Play artifact is created; owner must confirm it before upload. |
| Display name | `Magnetrail` | Matches the product specification. |
| Version | `versionCode 1`, `versionName 1.0` | Valid first candidate, but values were inline and no increment procedure existed. |
| SDKs | min 24, target 36, compile 37 | Meets the August 31, 2026 phone/tablet target-API requirement; compile 37 is required by current AndroidX dependencies. |
| Form factors | Android phone/tablet; portrait-first UI; no manifest orientation lock | Large-screen and foldable behavior still required manual QA. |
| Locale | en-US source strings only | Launch should remain en-US until another locale is translated and QA-tested. |
| Category / price / ads | Intended Game / Puzzle; free; rewarded and interstitial ads | Owner/console confirmation required. |

## Pre-change automated baseline

Command attempted first:

```bash
./gradlew clean :game-core:test :level-tools:test certifyCampaignContent \
  :app:testDebugUnitTest :app:testReleaseUnitTest lintDebug lintRelease \
  :app:assembleDebug :app:bundleRelease :app:connectedDebugAndroidTest --continue
```

AGP 9.1.1 does not register `:app:testReleaseUnitTest`, so Gradle rejected that task before executing work. The available-task equivalent was then run without that nonexistent task:

```bash
./gradlew clean :game-core:test :level-tools:test certifyCampaignContent \
  :app:testDebugUnitTest lintDebug lintRelease :app:assembleDebug \
  :app:bundleRelease :app:connectedDebugAndroidTest --continue
```

Results before M5 edits:

| Check | Result |
|---|---|
| `:game-core:test` | 58/58 passed |
| `:level-tools:test` | 2/2 passed |
| `:app:testDebugUnitTest` | 59/59 passed |
| Campaign certification | 100 campaign levels + 7 Daily fallbacks passed |
| Debug/release lint | Completed; 0 errors and 9 warnings per variant (target/tool/dependency update notices) |
| `:app:assembleDebug` | Passed |
| `:app:bundleRelease` | Passed; produced an **unsigned**, non-minified pre-M5 bundle |
| Pixel 7a AVD, API 36, 16 KB | 14/14 connected tests passed |
| Samsung SM-S928B, API 36 | 13/14 passed; level-selection semantics lookup failed |

The baseline aggregate command failed because of the single physical-device UI-test failure. It is not recorded as green.

## Code and build blockers found

- Release minification was disabled and resource shrinking was not configured.
- Release configuration substituted Google's sample AdMob App ID and test ad-unit IDs when production values were absent. Monetization stayed disabled, but the test identifiers were still present in the release binary.
- No production-release input validator rejected missing, malformed, placeholder, or Google sample IDs.
- No upload signing configuration, certificate verification task, or secret template existed. The generated AAB was unsigned.
- Firebase SDKs were present, but no genuine `google-services.json` or Google Services/Crashlytics build-plugin configuration existed; Analytics and Crashlytics therefore remained no-op.
- The manifest did not explicitly disable cleartext traffic or declare the app category. Backup/data-extraction resources were unchanged templates and did not express the app's local-only state policy.
- Launcher assets were Android Studio defaults rather than the approved Magnetrail split-ring/Rail Dart identity. Platform splash branding and maintainable release-art sources were absent.
- No Baseline Profile producer, Macrobenchmark coverage, generated app profile, or package-verification task existed.
- DataStore handled read `IOException`s but did not install an explicit corruption replacement handler; the schema was still M4 (`4`) and no M4-to-M5 migration test existed.
- Privacy-policy external intents relied on an injected string without an app-side HTTPS/host validation helper.
- No automated assertions covered release manifest exported/permission policy, sample-ID rejection, or store-listing limits.
- One physical-device connected test was nondeterministic/failing at baseline and needed investigation.

## Missing owner decisions

- Confirm `com.rameshta.magnetrail` as the permanent production package.
- Confirm `versionCode 1` / `versionName 1.0` for the first upload or provide the already-used Play version code.
- Confirm Game / Puzzle, free, and contains-ads listing choices.
- Select exact Play target age groups and state whether the game is general-audience, mixed-audience, or child-directed. The current ad request flags remain unspecified and live ads must stay blocked.
- Provide the legal developer/publisher identity, privacy contact, support email, public support URL, developer website, effective date, retention selections, and deletion-request process.
- Decide whether optional Firebase Analytics/Crashlytics will ship and approve the production Firebase project/data-retention/data-sharing configuration.
- Confirm closed-testing applicability based on developer account type/creation date and approve tester recruitment/feedback handling.
- Confirm store countries, first-release publishing method, rollout observation windows, and accountable release owner.

## Missing production IDs and configuration

- Production AdMob App ID.
- Exactly one rewarded and one interstitial AdMob ad-unit ID matching this package.
- AdMob Privacy & messaging configuration and reviewed consent-mode/default consent choices.
- Genuine production Firebase Android configuration and corresponding Firebase/Analytics/Crashlytics console settings, if those services will ship.
- Public HTTPS privacy-policy URL and support/developer-site URLs.
- Target-audience configuration matching Play, AdMob, UMP, and Mobile Ads request flags.
- Verified `app-ads.txt` publisher line and developer website.

## Missing signing and Play Console prerequisites

- Owner-authorized upload key and protected local/CI path, alias, and passwords.
- Play App Signing enrollment and trusted record of upload/app-signing certificate fingerprints.
- Play developer identity and 2026 Android developer/package registration confirmation.
- Play app creation for the permanent package and verification that version code `1` is unused.
- Completed Ads, Advertising ID, Data safety, target audience/content, content rating, app access, privacy policy, and other applicable App content declarations.
- Play device-catalog review, pre-launch report, closed-test evidence, production-access approval where applicable, and final review approval.

## Missing store, privacy, and test assets

- Hosted privacy policy and verified support contact.
- Play icon, feature graphic, and release-candidate phone/tablet screenshots with owner-reviewed captions/alt text.
- Final en-US listing review and owner-approved release notes.
- Representative min-SDK (API 24), mid-range, API 35, tablet/foldable, TalkBack/switch/keyboard, font/display scaling, process-death, low-memory, offline/consent/ad failure, and upgrade-install records.
- Real UMP/AdMob test-mode and Firebase DebugView/Crashlytics delivery evidence.
- Closed-test roster/duration evidence, feedback, fixes, vitals, and Play production-access questionnaire evidence.

## Environment limitations at baseline

- Available locally: Samsung SM-S928B/API 36 and Pixel 7a AVD/API 36 with 16 KB pages.
- Not available at baseline: API 24, a mid-range API, API 35, tablet/foldable, Play pre-launch devices, Play-generated device APKs, Play Console, AdMob console, Firebase console, and an owner-authorized upload key.
- Physical subjective audio/haptic tuning, full human TalkBack traversal, switch access, and production ad/consent flows cannot be proven by repository automation.

## Incremental M5 plan

1. Preserve the M0-M4 engine/content/monetization policies while hardening release configuration, secrets, manifest, backup policy, R8, versioning, and production validation.
2. Add M5 persistence/corruption and release-policy tests, fix the connected-test instability, and add a Baseline Profile/Macrobenchmark producer when the current toolchain supports it.
3. Build a minified unsigned release candidate, inspect its manifest/dependencies/native libraries/profile, validate it with current bundletool, generate local APK splits, and test them on the 16 KB environment without claiming upload readiness.
4. Create truthful, project-specific policy, listing, brand, QA, closed-test, rollout, rollback, and dashboard artifacts with every external dependency labeled.
5. End with `NO-GO` until signing, production IDs/configuration, hosted policy/support details, owner declarations, representative testing, and Play/closed-test evidence are supplied.
