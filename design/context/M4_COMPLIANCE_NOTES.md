# Magnetrail M4 implementation and compliance inventory

Access/review date: **2026-08-19**  
Scope: implementation inventory for M5 decisions; **not legal advice and not a claim of legal compliance**.

## Implementation status

M4 adds app-layer UMP consent orchestration, one voluntary rewarded hint placement, capped campaign-boundary interstitials, consent-aware Firebase interfaces, schema-v4 persistence, typed events, and safe no-op behavior. `:game-core`, solver, generator, certification, campaign JSON, and daily fallback JSON were not changed.

External/account inputs found at implementation time:

| Input | Status |
|---|---|
| AdMob App ID | Missing; debug uses Google's official sample App ID |
| Rewarded ad unit ID | Missing; debug uses Google's rewarded test unit |
| Interstitial ad unit ID | Missing; debug uses Google's interstitial test unit |
| Firebase Android configuration | Missing; no `google-services.json`, so observability factories remain no-op |
| Published privacy-policy URL | Missing; debug shows a clearly labeled disabled placeholder |
| Target audience/age classification | Missing; age-treatment request fields remain SDK-default `UNSPECIFIED` and live ads are blocked |

Release is **not monetization-ready**. No production identifier, Firebase file, privacy claim, target-audience choice, or console state was invented.

## SDKs and resolved versions

Resolved from `debugRuntimeClasspath`:

| Component | Declared/resolved version |
|---|---|
| Google Mobile Ads | `com.google.android.gms:play-services-ads:25.4.0` |
| Google UMP | `com.google.android.ump:user-messaging-platform:4.0.0` |
| Firebase Android BoM | `com.google.firebase:firebase-bom:34.17.0` |
| Firebase Analytics | `com.google.firebase:firebase-analytics:23.2.0` |
| Firebase Crashlytics | `com.google.firebase:firebase-crashlytics:20.1.0` |

Firebase main modules are used, not discontinued KTX artifacts. A future configured Firebase build should use the then-current official Google services and Crashlytics plugins. On the access date the official setup showed `com.google.gms.google-services:4.5.0` and `com.google.firebase.crashlytics:3.0.7`; they are intentionally not applied while the project-specific Firebase configuration is absent.

## Official primary sources consulted

All pages were accessed 2026-08-19.

### Ads and consent

- Google Mobile Ads Android quick start and prerequisites: https://developers.google.com/admob/android/quick-start
- Google Mobile Ads release notes: https://developers.google.com/admob/android/rel-notes
- UMP privacy setup, launch refresh, forms, `canRequestAds`, and duplicate-init pattern: https://developers.google.com/admob/android/privacy
- UMP and consent mode: https://developers.google.com/admob/android/privacy/consent-mode
- Rewarded lifecycle and official test unit: https://developers.google.com/admob/android/rewarded
- Interstitial lifecycle, natural boundaries, and official test unit: https://developers.google.com/admob/android/interstitial
- Test ads and authorized test devices: https://developers.google.com/admob/android/test-ads
- Google Mobile Ads Play Data safety disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure

### Firebase

- Add Firebase to Android, BoM, main-module policy, and plugin setup: https://firebase.google.com/docs/android/setup
- Analytics Android setup: https://firebase.google.com/docs/analytics/android/get-started
- Analytics events: https://firebase.google.com/docs/analytics/events
- Analytics collection configuration: https://firebase.google.com/docs/analytics/configure-data-collection
- Crashlytics Android setup: https://firebase.google.com/docs/crashlytics/android/get-started
- Crashlytics collection, custom keys, logs, and non-fatals: https://firebase.google.com/docs/crashlytics/android/customize-crash-reports
- Firebase Android Play Data safety disclosure: https://firebase.google.com/docs/android/play-data-disclosure
- Firebase privacy, processing, retention, and deletion information: https://firebase.google.com/support/privacy

### Google Play policy and declarations

- Developer Program Policies: https://play.google.com/about/developer-content-policy/
- Ads policy/declaration guidance: https://support.google.com/googleplay/android-developer/answer/9857753
- Data safety form guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Advertising ID policy: https://support.google.com/googleplay/android-developer/answer/6048248
- Target audience and content: https://support.google.com/googleplay/android-developer/answer/9859655
- Families data practices: https://support.google.com/googleplay/android-developer/answer/11043825

These sources and final console configuration must be rechecked during M5 because SDK and policy requirements change.

## Build and ID separation

- Debug/QA: official sample App ID `ca-app-pub-3940256099942544~3347511713`, rewarded test ID `ca-app-pub-3940256099942544/5224354917`, and interstitial test ID `ca-app-pub-3940256099942544/1033173712`.
- No personal test-device IDs are committed.
- Release reads non-source Gradle properties: `MAGNETRAIL_ADMOB_APP_ID`, `MAGNETRAIL_REWARDED_AD_UNIT_ID`, `MAGNETRAIL_INTERSTITIAL_AD_UNIT_ID`, `MAGNETRAIL_PRIVACY_POLICY_URL`, `MAGNETRAIL_TARGET_AUDIENCE`, and explicit `MAGNETRAIL_ENABLE_LIVE_ADS=true`.
- Release live ads enable only when all values are present and the explicitly reviewed target audience is `general`. `mixed` and `children` deliberately remain build-blocked pending the required Families/age-treatment redesign. Otherwise services are no-op and the sample App ID is only a safe manifest fallback; no ad request is made.
- Do not put real IDs or the privacy URL into committed source. Supply them through the release environment's protected Gradle properties/resource mechanism.
- Automated unit tests use pure fakes/no-op interfaces and make no SDK/network calls.

## Consent, privacy options, and diagnostics

1. Firebase SDK configuration defaults Analytics and Crashlytics automatic collection to `false`; Analytics advertising-ID collection and default ad-personalization signals are also disabled in app metadata.
2. The first rendered Compose frame is not blocked. A process-scoped privacy manager then requests UMP consent information once for the launch.
3. UMP loads/shows a required form. Its own `canRequestAds()` is the sole ad-permission signal; geography and raw TCF strings are never read or stored.
4. Permitted previous-session state can initialize ads while refresh is pending or failed. An atomic gate ensures Mobile Ads initializes once even if cached and refreshed callbacks both qualify.
5. UMP-required privacy options appear in Settings and reopen UMP's form. A process-wide full-screen coordinator prevents consent/ad overlap.
6. Usage & crash diagnostics defaults off. Effective collection requires local opt-in and the consent gate; Firebase consent mode remains authoritative for its granular consent signals. Turning the local switch off calls both SDK collection-disable APIs and deletes unsent Crashlytics reports.
7. Failure or denial never blocks the game, reopens a dismissed form automatically, or enables ads from an inferred geography.

## Ad formats, placements, and caps

Implemented formats only:

- Rewarded: explicit `Watch an ad for one hint` alternative to `Use 30 coins`.
- Interstitial: only after the user taps `Next level` on a completed campaign level.

Intentionally excluded: banners, native, app-open, rewarded interstitial, splash/failure/pause/deadlock ads, cross-promotion, mediation, billing, and all other rewarded placements.

Rewarded rules:

- Loaded + consent-permitted + foreground + expected gameplay screen + explicit tap required.
- SDK reward callback creates one durable local UUID transaction; dismissal alone creates none.
- At most one pending credit and five grants per local date. Duplicate callbacks/transactions are idempotent.
- The existing solver runs; the credit is consumed only when a valid safe move is actually shown. Cancellation, timeout, stale board, or no solution retains it. Coins are not charged.
- Date rollback does not reset allowance. Client-side credit is appropriate for this offline casual game but is not fraud-proof and has no server-side verification.

Interstitial rules:

- Campaign only; at least 10 lifetime campaign completions; first-clear/normal forward progression only; never replay or Daily Challenge.
- Three eligible completions since the last shown interstitial.
- 120 seconds of foreground time since any full-screen ad dismissal and since a rewarded ad. In-session timing is monotonic. After process restart, both a non-rollback wall interval and 120 seconds in the new foreground session are required.
- Fewer than four shown interstitials on the local date; rollback is fail-closed.
- Consent, preloaded ad, resumed foreground Activity, expected rendered completion screen, and idle full-screen coordinator required.
- The app never waits for a load or shows a spinner. Missing/no-fill/offline/show failure immediately runs normal navigation.

## Manifest permissions and provenance

No permission was manually added to the app manifest. The debug merged-manifest report (`app/build/outputs/logs/manifest-merger-debug-report.txt`) attributes:

| Merged permission | Primary manifest source/reason |
|---|---|
| `android.permission.INTERNET` | Mobile Ads API; also Firebase measurement/transport/Crashlytics for network delivery |
| `android.permission.ACCESS_NETWORK_STATE` | Mobile Ads API; also Firebase measurement/installations/transport for connectivity-aware delivery |
| `com.google.android.gms.permission.AD_ID` | Mobile Ads API, measurement API, and ads-identifier |
| `android.permission.ACCESS_ADSERVICES_AD_ID` | Mobile Ads API and measurement API |
| `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` | Mobile Ads API and measurement API |
| `android.permission.ACCESS_ADSERVICES_TOPICS` | Mobile Ads API |
| `android.permission.WAKE_LOCK` | Firebase/Google measurement and transitive WorkManager |
| `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` | Google measurement |
| `android.permission.FOREGROUND_SERVICE` | Transitive WorkManager manifest |

The generated package-specific dynamic-receiver permission is Android build/runtime infrastructure, not an M4 source declaration. Before release, the owner must review every merged permission again, declare advertising-ID/Ads SDK use accurately in Play Console, and decide whether removing any optional ad-services permission is compatible with the final AdMob configuration. App metadata disables Analytics advertising-ID collection; this does not mean the Mobile Ads SDK cannot use identifiers under its own consent/configuration.

## Vendor data inventory for Play Data safety drafting

This section paraphrases vendor disclosures; the owner must answer from actual production configuration and observed behavior.

| SDK | Potential data disclosed by vendor | Typical stated purposes / sharing | App-side controls in M4 |
|---|---|---|---|
| Google Mobile Ads | IP address, device/account or advertising identifiers where available, ad/app interactions, diagnostics, and device signals | Ads delivery/personalization as consented, measurement, analytics, fraud/abuse prevention; data may be shared with Google/advertising ecosystem per configuration | No request before UMP `canRequestAds`; test-only until owner decision; age treatment unspecified; Analytics ad-ID metadata does not govern AdMob |
| UMP | Consent/message interaction and device/request context needed to determine/show configured messages | Consent management and regulatory-message operation | SDK state only; no raw consent string or geography persisted/logged |
| Firebase Analytics | App/device identifiers including Firebase installation-related identifiers, app interactions/events, app/device metadata, diagnostics, and coarse location signals such as IP-derived context per vendor disclosure | Analytics and product measurement; Google service processing governed by project settings/consent | Default collection off, local opt-in + consent gate, no user ID, typed minimal events, ad-ID collection disabled |
| Firebase Crashlytics | Crash/ANR stack traces, app/device/OS metadata, Firebase installation identifier, logs, and approved custom keys | App stability diagnostics | Default collection off, local opt-in + consent gate, unsent reports deleted on disable, strict key allowlist, expected ad/offline failures excluded |

Retention/deletion notes:

- Firebase Analytics user-level retention is configured in the Firebase/Google Analytics property (commonly 2 or 14 months for eligible data); aggregation and other service retention can differ. The owner must record the selected production setting.
- Firebase's privacy documentation describes product-specific retention/deletion; Crashlytics event data is generally retained for a limited period (vendor documentation currently describes 90 days). Verify the current console/documentation before publishing the policy.
- Crashlytics collection can be disabled and unsent local reports deleted by the app. Deletion of already-uploaded service data follows Firebase project/account controls, not the local switch alone.
- AdMob/UMP retention and user-choice behavior depend on AdMob, Privacy & messaging, consent provider, region, and account settings. Record the actual production configuration; do not claim an app-only deletion mechanism.

## Target audience blocker

Owner decision required before live ads:

1. Select and document Google Play target age groups and intended audience.
2. Decide whether the app is general audience, mixed audience, or child-directed/under age of consent.
3. Re-evaluate SDKs, ad formats, creatives, age screen, maximum ad content rating, data collection, and Families requirements for that decision.
4. Configure matching current Mobile Ads request flags and AdMob/Play settings, then add tests for that exact configuration.

Until then request age-treatment fields are left at SDK-default `UNSPECIFIED`, test ads only are used, and `MAGNETRAIL_TARGET_AUDIENCE=general` plus explicit live enablement are required. `mixed` and `children` cannot enable M4 live ads without a code/policy re-review. No “for kids” metadata was added.

## Privacy-policy content requirements

The owner-authored, published policy should accurately cover at least:

- app identity/contact and effective date;
- AdMob, UMP, Firebase Analytics, and Crashlytics use and links to relevant Google disclosures;
- data categories, purposes, sharing/processing, identifiers, consent/legal basis as applicable, retention/deletion, security, international processing, and user/guardian rights;
- how to reopen Privacy options and disable Usage & crash diagnostics;
- target audience/children treatment and region-specific choices after those decisions are made;
- offline local progress (campaign, stars, coins, daily history/streak, ad caps/pending credit, settings) and uninstall/reset behavior;
- an accurate statement that rewarded ads are optional and interstitial placement/caps;
- owner contact and a process for policy updates/deletion requests.

## Draft Play Console inputs requiring owner verification

- Ads declaration: **Yes, contains ads** (rewarded and interstitial only), once production monetization is enabled.
- Target audience/content: **unanswered/blocking**.
- Data safety: review vendor categories above against the final AdMob personalization, Firebase project, consent messages, regions, and release manifest. Do not copy this draft as final declarations without verification.
- Advertising ID: merged permission is present transitively; answer the Play declaration based on final Mobile Ads use and current policy.
- Families: if any selected age includes children, stop release enablement and complete the Families-specific review before using the current setup.
- App access/account: no login/account was introduced.
- Data deletion: local state is removed by uninstall/clear storage; server-side deletion/retention must be described from the final Google account/project controls.

## Manual console work still required

### AdMob and Privacy & messaging

1. Create/verify the Android app for `com.rameshta.magnetrail`, obtain the real App ID and exactly one rewarded + one interstitial unit.
2. Complete app verification/payment/account requirements and review serving restrictions.
3. Configure UMP messages for every applicable region, purposes/vendors, privacy-options entry point, and the published privacy-policy URL.
4. Record target-audience flags, content rating, personalization mode, and any test devices locally; never commit personal device IDs.
5. Validate official test ads and Ad Inspector before any limited live test. Never click live ads.

### Firebase

1. Create/select the production and preferably separate test Firebase projects; register the exact application ID.
2. Download the genuine `google-services.json` into the protected build process/app module as intended by the team.
3. Add then-current official Google services and Crashlytics Gradle plugins (current reviewed examples: 4.5.0 and 3.0.7), sync, and verify variant separation.
4. Configure Analytics retention/data sharing/Google signals and Crashlytics collection to match the published policy and consent mode.
5. With test consent, validate DebugView and send one controlled non-production Crashlytics test report; verify mapping upload for release builds.

### Play Console and release

1. Publish the privacy policy and add its URL to the release property and Play listing.
2. Complete target audience/content, Data safety, Ads, advertising ID, content rating, and—if applicable—Families declarations.
3. Upload a closed-test signed AAB only after reviewing the release merged manifest and production configuration.
4. Run the M4 manual QA matrix and retain evidence for consent variants, offline/no-fill, caps, accessibility, and console events.

## Verification snapshot

Pre-edit baseline: core/tool/app tests, campaign certification, lint, debug assembly, and manifest processing succeeded; campaign certification reported 100 campaign levels plus 7 daily fallbacks. No production account inputs listed above were present.

Final repository verification:

- `./gradlew :game-core:test` — 58/58 passed.
- `./gradlew :level-tools:test` — 2/2 passed.
- `./gradlew certifyCampaignContent` — 100 campaign levels and 7 daily fallbacks certified.
- `./gradlew :app:testDebugUnitTest` — 59/59 passed.
- `./gradlew :app:lintDebug` — success, 0 errors and 10 pre-existing/toolchain-update warnings.
- `./gradlew :app:assembleDebug :app:assembleRelease :app:processDebugMainManifest` — success; generated release configuration reports `MONETIZATION_ENABLED=false` and `AD_CONFIGURATION_MODE=release_blocked` with missing owner inputs.
- `./gradlew :app:compileDebugAndroidTestKotlin` — success.
- `./gradlew :app:connectedDebugAndroidTest` — 14/14 passed on the available Pixel 7a AVD; the instrumentation environment forces consent, ads, Analytics, and Crashlytics to no-op.

See `M4_MANUAL_QA.md` for device/console checks and `M4_EVENT_CATALOG.md` for the full typed telemetry inventory. Real AdMob/UMP console flows, UMP debug geography, Firebase DebugView, and Crashlytics delivery were unavailable during repository-only implementation and are not claimed as verified.
