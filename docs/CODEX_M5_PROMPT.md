# Codex Prompt — Magnetrail M5

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0 through M4 are complete.

---

You are implementing **M5 for Magnetrail**, an Android-only, offline-first deterministic arrow-and-magnet puzzle.

Completed foundations:

- **M0:** pure Kotlin/JVM engine, rules, solver, loader, validation, and tests
- **M1:** playable Compose/Canvas game loop and result-driven animation
- **M2:** original visual identity, accessibility, sound/haptics, hints, and local progress
- **M3:** 100+ certified levels, content tooling, daily challenge, streak, stars, coins, and migration
- **M4:** consent-aware AdMob integration, capped rewarded/interstitial ads, Firebase Analytics/Crashlytics, and compliance inventory

M5 is the **release-readiness and Google Play launch milestone**. It hardens the production build, produces a verified Android App Bundle, prepares truthful store/compliance artifacts, runs closed testing, and defines a safe staged rollout.

## Authorization boundary

You may inspect and modify the repository, generate local release artifacts, and prepare console-ready documentation. Do **not**:

- Create, rotate, expose, overwrite, or commit a production signing key without explicit owner authorization.
- Invent passwords, aliases, AdMob/Firebase IDs, privacy-policy URLs, contact details, company names, or Play Console answers.
- Upload an AAB, publish a release, modify Play Console, enable live ads, or start a rollout unless the owner explicitly authorizes that external action.
- Claim that a console declaration, closed-test requirement, review, or production launch is complete without direct evidence.

If credentials, console access, or owner decisions are missing, complete all safe repository work and deliver a precise blocker/checklist.

## Current-source requirement

Google Play, Android, AdMob, Firebase, target API, asset, testing, and policy requirements change. Before implementation, verify current official documentation. Use only first-party Google/Android/Firebase sources for requirements and record URLs plus access dates in `docs/release/M5_RELEASE_REQUIREMENTS.md`.

At minimum verify:

- Current target API deadline for new apps and updates
- Android 16/API 36 behavior changes relevant to the app
- 16 KB memory page compatibility requirement
- Android App Bundle and Play App Signing requirements
- Current Play maximum download size and device compatibility checks
- Current store-listing text and graphic-asset requirements
- Data safety, Ads, target audience, content rating, app access, and privacy-policy declarations
- New personal-account closed-testing requirements, if applicable to the owner’s account
- Android vitals/core technical quality metrics
- Current AdMob, UMP, Firebase Analytics, and Crashlytics data disclosures

As of this prompt’s preparation date, August 19, 2026, official guidance says new phone/tablet apps and updates submitted from August 31, 2026 must target Android 16/API 36, with a possible account-specific extension to November 1, 2026. Do not rely on the extension; target API 36 unless the current official requirement is newer. Apps submitted to Play targeting Android 15/API 35 or higher must support 16 KB memory pages. Re-verify both facts before editing.

## Inspect and baseline first

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect the repository, current Git status/diff, Gradle wrapper/AGP/Kotlin/JDK versions, version catalog, modules, manifests, build types/flavors, dependency graph, native libraries, assets, and current release configuration. Preserve unrelated user changes.
3. Read every file under `docs/`, especially rules/design specs, content certification report, M4 event/compliance inventory, prompts, and milestone completion notes.
4. Inspect actual package/application ID, `versionCode`, `versionName`, min/compile/target SDK, app label, adaptive icon, splash, backup configuration, network security, R8 rules, AdMob/Firebase configuration, and signing setup.
5. Run all existing unit/instrumentation-available tests, content certification, lint, and debug build. Record baseline results.
6. Produce a pre-change release-gap report listing:
   - Code/build blockers
   - Missing owner decisions
   - Missing production IDs/configuration
   - Missing signing/Play Console prerequisites
   - Missing store/privacy assets
   - Device/emulator checks unavailable in the environment
7. Summarize the incremental M5 plan before editing.

Do not rewrite stable M0–M4 systems merely to “clean up” the repository during release work.

## M5 objective

Deliver:

- API 36-compatible, 16 KB-compatible hardened release build
- Stable package/versioning and secure signing configuration
- R8-minified, resource-shrunk, tested release AAB
- Bundle/device/manifest/dependency verification report
- Baseline Profile and measured critical-user-journey performance where feasible
- Final accessibility, offline, battery, memory, startup, and ad-policy QA
- Production configuration validation without committed secrets
- Privacy policy draft and Play Data safety/Ads/target-audience declaration worksheet
- Store-listing/ASO copy and accurate visual assets
- Closed-testing plan, tester instructions, feedback log, and exit criteria
- Staged-rollout plan and post-launch dashboard/runbook

## Required work

### 1. Freeze release identity and versioning

Confirm and document before changing:

- Application ID/package name
- Display name: `Magnetrail`
- Version name and monotonically increasing version code
- Minimum supported Android version
- Compile/target SDK
- Supported phone/tablet form factors and orientations
- Default locale and supported translations
- Game category and free/contains-ads status

Treat the production application ID as permanent once the first Play artifact is created. Do not rename it casually. Centralize version values through the existing build convention. Add a documented release-version increment process.

For the 2026 release, compile and target API 36 or the newer current requirement. Resolve behavior changes through testing; do not suppress warnings or lower target SDK to avoid migration work.

### 2. Production build configuration

Create a clear variant strategy:

- `debug`/QA uses test AdMob IDs and safe diagnostics
- `release` uses production configuration only when explicitly supplied
- Release is non-debuggable, non-testOnly, minified, optimized, and resource-shrunk where compatible
- Debug/test menus, fake providers, test crash controls, level cheats, UMP debug geography, and test-device IDs cannot appear in release
- Release logs do not expose consent, ad callbacks, device IDs, preferences, signing details, or internal board state

Use environment variables, untracked `local.properties`, CI secrets, or the project’s established secure mechanism for:

- Upload signing path/alias/passwords
- Production AdMob App ID and ad unit IDs
- Firebase configuration where applicable
- Privacy policy/support URLs if injected into the app

Check in a `.example`/documented template containing placeholders only. Verify `.gitignore`. Add a build-time validation that fails a production release when required values are absent, malformed, or still equal to Google sample/test IDs.

Do not mistake `google-services.json` or AdMob unit IDs for passwords, but still follow the project’s chosen environment/configuration policy and never fabricate them.

### 3. Signing and Play App Signing

Prepare a safe signing workflow:

- Use Play App Signing for the new app as currently required.
- Distinguish the Play-managed app signing key from the developer-controlled upload key.
- Never store keystore bytes or passwords in Git.
- Document secure backup and recovery responsibilities for the upload key.
- Print/record certificate fingerprints only in an owner-controlled release record; do not publish secrets.
- Verify the final bundle is signed with the expected upload certificate before upload.

If no upload key exists, do not generate one silently. Provide exact Android Studio/`keytool` steps and wait for explicit owner authorization. An unsigned release AAB may be produced for structural verification, but label it clearly as not uploadable.

### 4. API 36 migration and compatibility

Review current Android 16 behavior changes and test only those relevant to Magnetrail, including:

- Edge-to-edge/system-bar/inset behavior
- Back navigation/predictive back
- Foreground/background lifecycle around consent and full-screen ads
- Notification changes only if notifications exist; do not add them in M5
- Large-screen/resizable behavior even though portrait phone is primary
- Deprecated APIs and permission changes
- Text scaling, accessibility, and animation settings

Run on representative APIs including the minimum supported SDK, one mid-range version, API 35, and API 36 where available. Do not add broad permissions or compatibility flags without a demonstrated need.

### 5. 16 KB page-size and native-library audit

Inspect the final release dependency graph and AAB/APKs for native `.so` libraries, including those contributed by Ads/Firebase or transitive SDKs.

- Verify 16 KB page compatibility using current Android/Play tooling.
- Confirm every packaged ABI/native library is aligned and compatible.
- Upgrade or remove incompatible dependencies rather than excluding required ABIs blindly.
- Test the generated APK set on a 16 KB page-size emulator/device when available.
- If the app packages no native code, document the evidence from the final AAB/APK set rather than assuming compatibility.

Add the commands/results to `docs/release/M5_BINARY_REPORT.md`.

### 6. Release hardening and security review

Review the merged release manifest and binary:

- Every component has intentional `exported` behavior.
- No debug activity/provider/receiver/service is exported.
- Deep links, if any, validate input and open only expected destinations.
- Cleartext network traffic is disabled unless an explicitly justified endpoint requires it.
- No unused dangerous permissions.
- No hardcoded secrets, keystores, passwords, personal test IDs, or production credentials in source/history-visible files.
- Backup/data extraction rules match local progress/privacy expectations.
- Logs and Crashlytics keys contain no PII, raw consent strings, ad payloads, or full board snapshots.
- Web/privacy-policy links use HTTPS and safe external intents.
- Dependency versions have no known critical issue identified by current official/vendor release notes or available dependency scanning.

Exercise DataStore corruption recovery, M2→M3→M4→M5 migration, clock rollback, and upgrade install without clearing player progress.

### 7. R8, shrinking, and release behavior

Enable R8 full optimization/minification and resource shrinking for release unless a measured blocker exists. Use the smallest necessary keep rules based on SDK documentation and release failures; do not blanket-keep the entire app or all dependencies.

Verify in a minified release-derived build:

- JSON serialization/level loading
- All 100+ campaign levels and content certification
- Solver/hints and deterministic daily generation
- DataStore migrations
- Canvas rendering and accessibility semantics
- Sound/haptics
- UMP consent/privacy options
- Rewarded callback/credit persistence
- Interstitial eligibility/navigation
- Analytics event names and Crashlytics mapping

Archive release mapping/native symbols as applicable in the secure release record. Do not commit them publicly if project policy treats them as sensitive.

### 8. Performance, startup, jank, memory, and battery

Add or update a Baseline Profile module using current official Android guidance when compatible. Cover critical user journeys:

- Cold app start to Home
- Continue into campaign gameplay
- Scroll the 100+ level catalog
- Open a representative level and complete one move
- Open Daily Challenge

Generate profiles from a non-minified profile variant and consume them in the optimized release variant. Verify the final AAB includes the profile.

Use Macrobenchmark on a physical device where available to measure:

- Cold/warm startup
- Time to initial/full display
- Frame timing while scrolling level selection
- Representative arrow/magnet animation jank

Also inspect:

- Memory during repeated level transitions and ad load/dismiss cycles
- Leaked Activity/ad/audio references
- Solver/daily generation CPU time and cancellation
- Network activity when consent/diagnostics/ads are disabled
- Wake locks/background work/battery use
- APK/AAB download/install size

Do not invent universal performance numbers. Record measured device/build/iterations and before/after results. If physical hardware is unavailable, keep benchmarks configured and report them unexecuted.

### 9. Final functional and accessibility regression

Create `docs/release/M5_QA_MATRIX.md` and verify:

- Fresh install, update install, process death, device restart
- Offline first launch and offline returning launch
- Campaign progression, stars, coins, hint spending, replay, Level 100+
- Daily challenge, cache fallback, streak, timezone/clock rollback
- Undo/restart/deadlock/completion
- Sound/haptics/reduced motion/high contrast/path assistance
- UMP accepted/denied/not-required/error/privacy-options flows
- Rewarded earned/dismissed/no-fill/offline/cap/pending-credit cases
- Interstitial boundaries, cooldowns, daily caps, suppression, no-fill
- Analytics/Crashlytics enabled and disabled states
- TalkBack full critical path, switch/keyboard activation where applicable
- Font scale, display size, contrast, touch targets
- 360/390/430 dp portrait and representative tablet/foldable window
- Minimum SDK, API 35, API 36, low-memory/background lifecycle

No release-blocking crash, ANR, inaccessible primary action, progress loss, stuck navigation, overlapping full-screen UI, or unsolvable shipped level may remain.

### 10. App icon, splash, and brand assets

Finalize an original Magnetrail brand system consistent with the M2 design:

- Adaptive launcher icon with foreground/background layers and monochrome icon where current Android guidance supports it
- High-resolution Play icon
- Branded Android splash using the platform SplashScreen API
- Feature graphic
- Notification icon only if notifications actually exist

Do not copy competitor arrow shapes, store artwork, color composition, screenshots, logos, or typography. Avoid emoji magnets and literal red/blue horseshoes. Keep the split-ring magnet/rail identity and Rail Dart silhouette.

Validate icon masks, light/dark launch surfaces, small-size legibility, transparency, and safe zones. Store source files and export instructions in a maintainable design-assets directory.

### 11. Accurate screenshots and store graphics

Capture screenshots from the actual release-candidate app, not fabricated UI. Use seeded/demo states that are valid under the production engine and contain no test/debug labels, device IDs, or personal data.

Prepare current Play-compliant assets and document their dimensions. A recommended phone screenshot story:

1. `Clear every arrow`
2. `Magnets bend the path`
3. `Every magnetic move flips the field`
4. `Find the perfect sequence`
5. `Solve 100+ handcrafted challenges`
6. `Take on a new daily puzzle`
7. `Play offline, wherever you are`
8. `Built for comfortable, accessible play`

Only claim features actually present and functioning in the release candidate. Keep captions concise, readable, and outside critical UI. Use real in-app captures with consistent framing; do not misrepresent gameplay or show competitor names.

If automated screenshot capture is practical, create a deterministic screenshot test/task with animations, dates, coins, and ad/consent UI controlled for reproducibility. Never let it request live ads.

### 12. ASO and store listing

Create `docs/release/store-listing/en-US.md` containing current allowed lengths and ready-to-paste fields:

- App name: `Magnetrail`
- Category: Game / Puzzle, subject to owner confirmation
- Short description
- Full description
- Release notes
- Feature bullets
- Search-term rationale
- Support contact placeholders
- Privacy-policy URL placeholder until supplied

Recommended short description:

> Bend magnetic paths and clear every arrow in the right order.

Write the full description in concise natural language around real differentiators:

- Directional arrow puzzle
- Pull/Push magnets and automatic polarity flips
- Sequence/order reasoning
- 100+ certified levels
- Daily challenge and streak
- Offline campaign play
- Hints and accessibility options

Use relevant phrases naturally, such as `arrow puzzle`, `logic puzzle`, `magnetic puzzle`, `offline puzzle game`, and `brain teaser`. Do not keyword-stuff, make unverifiable claims, promise health/cognitive benefits, use competitor/trademark names, call the app “#1,” or mention download counts/reviews that do not exist.

Default to en-US for launch unless the app is genuinely localized and QA-tested. Do not auto-publish machine translations. Create a localization backlog for likely markets after launch data exists.

### 13. Privacy policy and Play declarations

Using the actual final binary, SDK versions, M4 inventory, effective consent behavior, and vendor disclosures, create:

- `docs/release/privacy-policy-draft.md`
- `docs/release/PLAY_DATA_SAFETY_WORKSHEET.md`
- `docs/release/PLAY_APP_CONTENT_CHECKLIST.md`

Privacy policy draft must accurately cover:

- No account/custom backend
- Local gameplay/progress data
- AdMob/UMP
- Firebase Analytics/Crashlytics and diagnostics toggle
- Data categories/purposes based on current vendor disclosures
- Consent/privacy controls
- Children/target-audience decision
- Retention/deletion controls known to the developer
- Clearing local app data
- Contact and effective-date placeholders

The policy must be hosted at a public HTTPS URL and linked in the app/Play listing before production; Codex must not invent or publish the URL without authorization.

The Data safety worksheet must account for data collected or shared by every third-party SDK, not only the app’s own code. Do not copy generic declarations blindly. Mark each answer as `verified`, `owner decision`, or `console verification required`.

The App content checklist must include:

- Contains ads declaration
- Target audience and content
- Content rating questionnaire
- Data safety
- Privacy policy
- App access: no login required
- Ads/UMP behavior
- News/health/financial/government flags as not applicable only after verification
- Families-policy re-review if children are selected

This documentation supports the owner; it is not legal advice and must not claim automatic compliance.

### 14. Production AdMob/Firebase validation

Before a production-ready AAB:

- Replace test IDs through secure release configuration only.
- Build must fail if release contains Google sample/test ad IDs.
- Confirm AdMob app and units exist and match the package/application ID.
- Confirm Privacy & messaging configuration and privacy-options behavior.
- Confirm target-audience request flags match the owner’s Play declaration.
- Confirm Firebase app/package and SHA/certificate configuration where needed.
- Confirm Analytics and Crashlytics collection follow consent/diagnostics settings.
- Verify ads.txt/app-ads.txt requirements and publisher setup from current AdMob guidance, if applicable.

Use test ads for QA. Never click live ads. Live monetization should not be validated by generating self-impressions/clicks.

### 15. AAB generation and binary verification

Build the release bundle with the Gradle wrapper. Validate:

- Expected package, version, min/target SDK
- Expected signing certificate/upload key
- No debug/test flags or sample ad IDs
- R8/minification/resource shrinking
- Baseline Profile inclusion
- Asset/catalog integrity
- Native ABIs and 16 KB page compatibility
- Manifest permissions/components
- Bundle size and generated split APK behavior

Use current `bundletool` to validate the AAB, generate a universal/device APK set, install it on available devices, and smoke-test the installed artifact—not only an Android Studio debug APK.

Record SHA-256 hashes for the final AAB and relevant mapping/symbol files in a local release manifest. Do not expose signing secrets. If the bundle is unsigned because authorization/key material is missing, name and label it clearly; do not present it as Play-ready.

### 16. Closed testing

Create:

- `docs/release/CLOSED_TEST_PLAN.md`
- `docs/release/TESTER_GUIDE.md`
- `docs/release/FEEDBACK_TEMPLATE.md`
- `docs/release/RELEASE_BLOCKER_LOG.md`

The plan must verify the current Play requirement for the owner’s account. New personal developer accounts may require at least 12 opted-in testers for 14 continuous days before applying for production access; do not assume it applies to every account or that installation alone guarantees approval.

Tester guide should request realistic use across:

- Tutorial/early campaign
- Pull/Push/polarity understanding
- One advanced level
- Daily challenge
- Hint/coin flow
- Rewarded ad opt-in and interstitial frequency perception
- Offline mode
- Accessibility/settings
- Device model, Android version, issue reproduction steps

Collect no unnecessary personal data. Track consented tester feedback and fixes. A calendar countdown alone is not an exit criterion.

Closed-test exit gates:

- Required Play tester/time condition satisfied where applicable
- No open release-blocking defects
- No progress loss or unsolvable content reports
- No consent/ad-policy defects
- Crash/ANR/vitals evidence acceptable for available volume
- At least representative low/mid/high Android devices tested
- Store listing and privacy declarations match the tested binary
- Owner reviews tester feedback and production-access questionnaire

### 17. Rollout and rollback plan

Create `docs/release/ROLLOUT_RUNBOOK.md` with:

- Internal test → closed test → production-access application where required
- Pre-launch report review
- Managed publishing/review timing
- Initial staged production rollout, for example 5% → 20% → 50% → 100%
- Minimum observation window and evidence required at each stage
- Halt/rollback criteria
- Hotfix versioning and artifact provenance

Use current Play capabilities; percentages are a plan, not an automatic command.

Provisional halt criteria should include:

- Any widespread startup crash, progress loss, unsolvable shipped level, or stuck consent/ad flow
- Material regression in user-perceived crash or ANR rate
- Rating/review pattern indicating misleading listing or intrusive ads
- Reward duplication/negative economy balance
- Interstitial policy violation
- Data safety/privacy discrepancy

Do not promise instant rollback: document Play’s actual halt/update behavior and prepare a safe hotfix path.

### 18. Post-launch observability and decision dashboard

Create `docs/release/POST_LAUNCH_DASHBOARD.md` mapping metrics to the typed M4 events and Play/Firebase/AdMob consoles:

- Install/store conversion where available
- D1/D7 retention when enough consented data exists
- Tutorial/Level 1/5/10 completion
- Difficulty drop-off by pack/level
- Hint usage and insufficient-coin rate
- Daily participation and streak continuation
- Rewarded offer/show/earn completion
- Interstitial eligible/show/dismiss frequency
- Ad revenue/eCPM/fill only from AdMob reporting
- User-perceived crash/ANR and affected devices
- Ratings/reviews and recurring complaints

Define weekly review questions and action thresholds without collecting more data than necessary. Do not add new SDKs, remote config, or A/B testing in M5.

## Testing and verification requirements

Keep all M0–M4 tests and content certification passing.

### Automated

- Core, generator/tool, app unit, migration, ad policy, analytics privacy, and UI tests
- Campaign/daily certification
- Release lint and dependency resolution
- Release compile/bundle with R8
- Debug/test-code absence checks for release
- Sample/test ad ID release failure test
- Manifest permission/exported-component assertions
- Store listing character-limit check
- Screenshot state determinism if capture automation is added
- Baseline Profile generation/package verification where environment permits
- AAB/bundletool validation

### Device/release artifact

- Install APKs generated from the release AAB
- Fresh and upgrade install
- Minimum SDK, API 35, API 36
- 16 KB page-size environment
- Offline/no-consent/consent/ad failure
- TalkBack, font/display scaling, reduced motion
- Startup/frame/memory checks
- Rotation/background/process death around ads and DataStore writes

Never report an unrun physical-device, Play pre-launch, closed-test, console, or production check as passed.

## Suggested release artifact structure

```text
docs/release/
├── M5_RELEASE_REQUIREMENTS.md
├── M5_RELEASE_GAP_REPORT.md
├── M5_BINARY_REPORT.md
├── M5_QA_MATRIX.md
├── RELEASE_CHECKLIST.md
├── RELEASE_MANIFEST_TEMPLATE.md
├── privacy-policy-draft.md
├── PLAY_DATA_SAFETY_WORKSHEET.md
├── PLAY_APP_CONTENT_CHECKLIST.md
├── CLOSED_TEST_PLAN.md
├── TESTER_GUIDE.md
├── FEEDBACK_TEMPLATE.md
├── RELEASE_BLOCKER_LOG.md
├── ROLLOUT_RUNBOOK.md
├── POST_LAUNCH_DASHBOARD.md
└── store-listing/
    └── en-US.md

design/release/
├── icon-source/
├── feature-graphic/
└── screenshots/
```

Fit the existing repository; do not create empty ceremonial files. Every artifact must contain actionable, project-specific information.

## Explicitly out of scope

Do not add:

- New gameplay rules/entities or unvalidated levels
- Accounts, backend, cloud save, leaderboard, social features
- Billing, purchases, subscriptions, ad-removal purchase
- New ad formats, networks, mediation, or higher M4 frequency caps
- Remote config, attribution, A/B testing, or additional tracking SDKs
- iOS, web, Windows, or non-Android builds
- Unauthorized console uploads, publishing, production rollout, or credential creation

Do not delete or weaken passing tests to obtain a release build.

## Verification commands

Use actual repository task names. At minimum run the equivalents of:

```bash
./gradlew clean
./gradlew :game-core:test
./gradlew :level-tools:test
./gradlew certifyCampaignContent
./gradlew :app:testDebugUnitTest
./gradlew :app:testReleaseUnitTest
./gradlew lintDebug lintRelease
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

Run connected/release-derived tests and Baseline Profile generation where supported:

```bash
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:generateBaselineProfile
```

Use current bundletool commands to validate, build APK sets, and install the release AAB. Record exact tool versions and commands in the binary report rather than assuming commands shown here remain current.

## M5 definition of done

Repository-level M5 is complete only when:

- All M0–M4 tests, certification, lint, and release build tasks pass.
- Target/compile SDK meet current Play rules, including API 36 for the planned 2026 submission.
- Final dependencies/native libraries pass 16 KB compatibility verification.
- Release build is minified/shrunk, contains no debug tools/sample ad IDs/secrets, and has verified manifest behavior.
- A release AAB is validated and tested through generated APK splits.
- A signed uploadable AAB exists only if owner-authorized upload-key configuration was supplied and verified.
- Baseline Profile/performance work is measured or honestly marked blocked by hardware.
- Offline, accessibility, consent, ads, progress migration, and 100+ level integrity are release-tested.
- Store copy and screenshots truthfully match the release candidate.
- Privacy/Data safety/App content worksheets reflect every final SDK and owner decision.
- Closed-test, rollout, rollback, and post-launch plans are actionable.
- Every external console/owner blocker is explicit.

Actual launch is complete only after the owner separately confirms:

- Play developer identity/account setup
- App creation and permanent package ID
- Play App Signing enrollment/upload-key handling
- Production AdMob/Firebase/UMP configuration
- Public privacy-policy URL and support contact
- Accurate Data safety/Ads/target-audience/content-rating declarations
- Required testing/review/production access
- Explicit approval of the production rollout

## Final response

Report:

1. Release hardening and key code/build changes.
2. Package/version/min/compile/target SDK and current policy sources.
3. 16 KB/native dependency verification.
4. Signing status without exposing secrets.
5. Exact AAB path, size, SHA-256, signing state, and bundletool verification.
6. Test/lint/certification/benchmark/device results and unrun limitations.
7. Privacy/Data safety/Ads/target-audience decisions and remaining owner actions.
8. Store listing and asset deliverables.
9. Closed-test readiness, blockers, and exit gates.
10. Staged rollout and post-launch monitoring plan.
11. A final `GO` or `NO-GO` recommendation. Use `NO-GO` if any required signing, policy, privacy, production-ID, testing, crash, content, or console evidence is missing.

Begin by verifying current official requirements, inspecting the repository, running the M0–M4 baseline, and producing the release-gap report. Then implement all safe repository-level M5 work. Do not upload or publish without explicit owner authorization.

## Official reference starting points

Re-check these at implementation time:

- Target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- 16 KB page sizes: https://developer.android.com/guide/practices/page-sizes
- Release builds/signing: https://developer.android.com/build/build-for-release
- Upload App Bundle/Play App Signing: https://developer.android.com/studio/publish/upload-bundle
- Android App Bundles: https://developer.android.com/guide/app-bundle
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- Android vitals: https://developer.android.com/topic/performance/vitals
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Play developer policies: https://play.google.com/about/developer-content-policy/
- Store listings: https://play.google.com/console/about/storelistings/
- AdMob data disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure
- Firebase privacy: https://firebase.google.com/support/privacy
