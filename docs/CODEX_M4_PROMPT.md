# Codex Prompt — Magnetrail M4

Copy everything below into Codex from the root of the Android Studio repository in which Magnetrail M0, M1, M2, and M3 are complete.

---

You are implementing **M4 for Magnetrail**, an Android-only, offline-first deterministic arrow-and-magnet logic puzzle.

Completed foundations:

- **M0:** pure Kotlin/JVM engine, immutable rules, loader/validator, solver, tests
- **M1:** playable Compose/Canvas app with result-driven turns
- **M2:** original visual identity, accessibility, audio/haptics, hints, and local progress
- **M3:** 100+ certified campaign levels, deterministic generation tooling, daily challenge, streak, stars, coins, economy, and migration

M4 adds **consent-aware monetization and privacy-conscious observability**. It must not change core gameplay, make ads necessary to play, or compromise the offline experience.

## Current-source requirement

Advertising, privacy, Firebase, and Google Play requirements change frequently. Before changing dependencies or manifests, use the current official Google/Android/Firebase documentation available at implementation time. Do not rely solely on version numbers in an old prompt or blog post.

At minimum verify the current official guidance for:

- Google Mobile Ads SDK Android setup and prerequisites
- User Messaging Platform (UMP) consent flow
- `requestConsentInfoUpdate`, required consent forms, privacy-options entry point, and `canRequestAds`
- Rewarded and interstitial lifecycle/callback APIs
- Google test ad unit IDs and test-device setup
- Firebase Analytics consent/collection controls
- Firebase Crashlytics opt-in/automatic collection controls
- Google Play ads, target-audience, Data safety, advertising ID, and Families policies applicable to this app

Record the official URLs and access date in `docs/M4_COMPLIANCE_NOTES.md`. Use only official primary sources for implementation decisions.

## Inspect and verify before editing

1. Read `AGENTS.md` and repository instructions, if present.
2. Inspect the repository, current branch/diff, Gradle/version catalog, flavors/build types, manifest, navigation, M3 economy, DataStore schema, and existing analytics/ad abstractions. Preserve unrelated user changes.
3. Read all files under `docs/`, including the rules contract, design specification, content reports, M0–M3 prompts, and completion notes.
4. Inspect the actual production APIs rather than assuming example class names here.
5. Run the complete existing core/tool/app tests, content certification, lint, and debug build. Record baseline results.
6. Report whether these external inputs are already present:
   - AdMob App ID
   - Rewarded and interstitial ad unit IDs
   - Firebase Android configuration
   - Published privacy-policy URL
   - Documented target audience/age classification
7. Summarize the existing architecture and a minimal incremental M4 plan before editing.

Never invent production IDs, configuration files, privacy claims, consent choices, or target-audience answers. Missing external account configuration is not permission to use guessed values. Complete the code using debug/test configuration and document the exact production setup blocker.

## M4 objective

Deliver a testable monetization and observability layer with:

- UMP-based consent orchestration
- A visible privacy-options entry point when required
- Optional rewarded ads for a solver-backed hint credit
- Carefully capped interstitials only at campaign level boundaries
- No banners, app-open ads, rewarded interstitials, or gameplay-obscuring formats
- Firebase Analytics events with no PII and consent-aware collection
- Firebase Crashlytics with controlled collection and useful non-sensitive context
- Centralized, testable ad eligibility/frequency policies
- Debug-safe test ads and no accidental production traffic during development
- Offline/no-consent/no-fill behavior identical to an ad-free app
- A compliance/data inventory for M5 Play Console declarations

## Preserve these invariants

- `:game-core`, generator, solver, certification, campaign, and daily rules remain unchanged.
- Ads and telemetry stay in the Android `:app` layer behind interfaces.
- Game completion, rewards already earned, navigation, undo, restart, hints paid with coins, and daily play never depend on ad availability.
- No ad or analytics SDK initialization may block first render or core gameplay.
- No ad is requested until the current consent flow says ads may be requested.
- Denying consent must not block the game or repeatedly nag the player.
- Rewarded value is granted only from the SDK reward callback, exactly once.
- Interstitial failure, no-fill, offline state, dismissal, lifecycle loss, or consent denial immediately follows the normal navigation path.
- UI/analytics/ad code never duplicates gameplay rules or mutates engine state.
- Production behavior is configured centrally and is independently testable with fake providers and clocks.

## Required work

### 1. Build configuration and dependency hygiene

Use the project’s version catalog/current conventions and current stable compatible SDKs from official docs. Do not blindly hardcode versions from this prompt.

Add only the dependencies required for:

- Google Mobile Ads SDK
- Google UMP SDK
- Firebase Analytics
- Firebase Crashlytics

Do not add mediation adapters, multiple ad networks, remote config, performance monitoring, attribution SDKs, or billing in M4.

Separate debug/test and production configuration:

- Debug/QA builds use Google’s official sample App ID and test ad unit IDs, or explicitly configured test devices.
- Release builds must obtain real IDs from non-source configuration/resources appropriate to the existing build system.
- Never send live ad traffic from automated tests or routine debug builds.
- Never commit personal test-device identifiers, service-account keys, signing keys, or secrets.
- Do not log ad IDs, consent strings, Firebase installation IDs, or full SDK responses.
- If Firebase configuration is absent, keep a working no-op observability implementation and document the exact setup needed; do not fabricate `google-services.json`.

Review the merged manifest. Declare only permissions actually required by chosen SDKs. Document whether `INTERNET`, network state, and advertising-ID permissions are introduced transitively or directly, why they exist, and what must be declared in Play Console. Do not manually duplicate an SDK-provided permission without a verified reason.

### 2. Consent and privacy orchestration

Create one application-scoped `ConsentManager`/`PrivacyManager` abstraction around UMP.

Required launch flow:

1. Initialize Firebase in a collection-disabled or consent-safe state according to current official guidance.
2. Obtain the UMP `ConsentInformation` instance.
3. Request fresh consent information on **every app launch**.
4. Load and show the consent form if required.
5. Expose whether a privacy-options entry point is required.
6. Check UMP’s current `canRequestAds()` result before initializing/requesting ads.
7. Prevent duplicate SDK/ad initialization when both cached and refreshed consent paths complete.
8. If consent update/form fails, use only the state UMP officially permits; otherwise continue without ads.

Do not parse or persist raw TCF/consent strings yourself. Do not infer consent from geography. Do not show a custom pre-consent screen that manipulates the user toward acceptance. Do not repeatedly reopen a dismissed form.

Settings/Privacy UI must include:

- `Privacy options` only when UMP reports that an entry point is required
- `Usage & crash diagnostics` user control if collection is optional under the chosen policy
- Link/action for the app privacy policy when a real URL exists; show a clear debug placeholder only in non-release builds
- Existing sound, haptics, reduced-motion, contrast, and assistance settings unchanged

If the user disables optional diagnostics, analytics and Crashlytics collection must be disabled using official APIs. Consent denial always takes precedence over a local enable toggle.

### 3. Target audience and ad request configuration

Do not guess whether Magnetrail is child-directed, mixed-audience, or under-age-of-consent. Add a documented production checklist requiring the owner to decide the Google Play target age groups and configure matching SDK request flags.

Until the decision is explicitly configured:

- Use `UNSPECIFIED` treatment flags where officially supported.
- Use test ads only.
- Mark production monetization as not release-ready.
- Do not add language such as “for kids” or target children in metadata.

If the owner later selects children or mixed audience, stop and re-evaluate ad formats, SDKs, creatives, age screening, Families policy, and data collection before enabling live ads. Do not silently reuse the general-audience configuration.

### 4. Ad service abstraction

Keep SDK types out of ViewModels and gameplay state. Create interfaces close to:

```kotlin
interface RewardedAdService {
    val state: StateFlow<RewardedAdState>
    fun preloadIfAllowed()
    suspend fun showForHint(activity: Activity): RewardedOutcome
    fun clear()
}

interface InterstitialAdService {
    val state: StateFlow<InterstitialAdState>
    fun preloadIfAllowed()
    suspend fun showAtBoundary(activity: Activity): InterstitialOutcome
    fun clear()
}
```

Adapt names to the project. Requirements:

- One loaded ad instance is consumed once.
- Full-screen content callbacks clear references on show/dismiss/failure.
- Preload only after consent permits ad requests.
- Main-thread SDK requirements are respected.
- Activity references are never retained beyond display.
- Application context is used where appropriate.
- Lifecycle/background changes cannot show an ad over the wrong screen.
- A process-wide full-screen-ad coordinator prevents rewarded and interstitial overlap.
- No-op and fake implementations support offline mode, previews, unit tests, and missing SDK configuration.
- Load/show errors are modeled as recoverable outcomes, not exceptions that break navigation.

Do not create ad objects during Compose recomposition.

### 5. Rewarded ad placement: one hint credit

M4 implements exactly one rewarded placement:

> **Watch an ad for one hint**

The existing `30 coins` hint remains the primary predictable option. The rewarded option is a voluntary alternative, never a forced gate.

Flow:

1. User opens the Hint choice.
2. UI shows `Use 30 coins` and, when available/eligible, `Watch an ad`.
3. Explain the reward before display: `Watch an ad to reveal one safe move.`
4. User explicitly taps the rewarded option.
5. Show only if foreground Activity, consent, eligibility, and loaded-ad conditions hold.
6. On the SDK reward callback, atomically grant one pending `adHintCredit` identified by a unique local transaction ID.
7. Run the existing solver-backed hint flow.
8. Consume the credit only when a usable hint is actually shown.
9. If solver cancellation/failure/stale state prevents the hint, retain the credit for a later hint request.
10. Dismissal before the reward callback grants nothing.

Rules:

- Maximum **5 rewarded hint credits granted per local calendar day**.
- Keep at most **1 unconsumed ad hint credit** to prevent farming/stockpiling.
- A granted/consumed transaction is idempotent across callback duplication and process-state races.
- No coins are charged when using a credit.
- Rewarded availability is hidden or disabled gracefully when offline, not loaded, capped, or consent-disallowed.
- Do not promise an ad will be available.
- Do not auto-play a rewarded ad after an insufficient-coin message.
- Do not reward based on dismissal alone.

Persist only the minimal local cap/credit/transaction state required. Client-side rewards are acceptable for this no-backend casual game, but document that they are not fraud-proof and do not pretend server-side verification exists.

### 6. Interstitial placement and frequency policy

Interstitials may appear only at a natural campaign boundary when the player taps `Next level` after a completed level.

All conditions must be true:

- Current content is campaign, not Daily Challenge.
- The player has completed at least the first **10 campaign levels lifetime**.
- This is a first clear or normal forward progression, not a replay completion.
- At least **3 eligible campaign completions** occurred since the last shown interstitial.
- At least **120 seconds of foreground time** passed since the last full-screen ad dismissal.
- No rewarded ad was shown/dismissed within the last **120 seconds**.
- Fewer than **4 interstitials** were shown on the current local date.
- Consent permits ad requests.
- An interstitial is already loaded.
- The app is foregrounded with the expected completion screen/activity.
- No other full-screen ad or consent form is showing.

Never show an interstitial:

- At app launch, resume, home arrival, or level start
- During gameplay or animation
- After failure, collision, deadlock, restart, undo, or hint
- Before Level 11
- In Daily Challenge
- On back navigation or app exit
- Merely because an ad finished loading
- As a replacement for a rewarded ad

When eligible, the `Next level` action may show the loaded interstitial and then navigate after dismissal. If show/load fails or no ad is ready, navigate immediately. Never display a spinner waiting for an ad and never delay navigation to fetch one.

Implement `InterstitialPolicy` as a pure, clock-injected unit with explicit reason codes such as `FIRST_LEVELS`, `COMPLETION_GAP`, `COOLDOWN`, `DAILY_CAP`, `RECENT_REWARDED`, `NOT_LOADED`, and `CONSENT_BLOCKED`. Persist only state needed across process restart and handle wall-clock rollback conservatively. Use monotonic elapsed time for in-session cooldowns.

### 7. Formats intentionally excluded

Do not implement:

- Banner ads
- Native ads
- App-open ads
- Rewarded interstitials
- Splash-screen ads
- Ads on pause/deadlock/failure screens
- Cross-promotion SDKs
- More than one rewarded placement

These exclusions are part of the M4 design, not unfinished TODOs.

### 8. Analytics abstraction and event plan

Create a small `AnalyticsTracker` interface with Firebase and no-op/fake implementations. ViewModels/domain-facing coordinators emit typed product events; they do not construct arbitrary string bundles throughout the UI.

Use official recommended Firebase event names/parameters where they semantically fit. Keep custom names lowercase snake_case and below documented limits.

Track only events needed to answer product questions:

#### Core funnel

- `level_start`: level ID, pack, difficulty, origin
- `level_complete`: level ID, stars, actions, overloads, hints, duration bucket
- `level_restart`: level ID, attempt bucket
- `level_deadlock`: level ID, actions bucket
- `hint_choice_open`
- `hint_coin_spend`
- `hint_shown`: source `coins` or `rewarded`
- `daily_start`
- `daily_complete`: difficulty, stars, streak bucket

#### Monetization

- `rewarded_offer`
- `rewarded_load_result`: coarse result code
- `rewarded_show`
- `rewarded_earned`
- `rewarded_dismiss`
- `interstitial_eligible`: policy reason/outcome
- `interstitial_show`
- `interstitial_dismiss`
- `ad_show_failure`: format and coarse error category

#### Consent/settings

- `consent_flow_result`: coarse state only, never raw consent data
- `privacy_options_open`
- `diagnostics_setting_changed`

Privacy rules:

- Never log name, email, phone, precise location, contacts, free text, device advertising ID, Firebase installation ID, raw consent string, IP address, date of birth, or user-generated identifier.
- Do not set a custom user ID.
- Do not log exact local dates, daily seeds, or full board state.
- Bucket duration, balance, streak, and attempt values where exact values are unnecessary.
- Do not send expected ad no-fill/network errors as Crashlytics non-fatals.
- Event emission must not change gameplay/economy behavior.
- Collection follows consent and the local diagnostics toggle.

Create `docs/M4_EVENT_CATALOG.md` listing event, trigger, parameters, purpose, and privacy classification. Prevent duplicate completion/reward/ad events through stable event boundaries rather than recomposition.

### 9. Crashlytics

Integrate Crashlytics behind a `CrashReporter` interface with no-op/fake implementations.

Default automatic collection must match the chosen consent/privacy policy. If using opt-in/consent-gated reporting, disable automatic collection in configuration and enable it only after the effective policy permits it.

Allowed non-sensitive keys include:

- App/content/engine/generator/economy versions
- Current screen
- Stable campaign level ID or daily profile—not daily seed/date
- Animation phase
- Consent/ad state category
- Last interstitial/rewarded policy reason

Record unexpected non-fatals such as corrupted persisted data after safe recovery, impossible state invariant violations, and generator fallback activation. Do not report routine offline/no-fill/timeouts as crashes. Never attach PII, raw preferences, consent strings, ad payloads, or complete board snapshots.

Add a debug-only controlled test action or test that verifies integration without exposing a crash button in release UI. Do not intentionally crash automated release builds.

### 10. Persistence and migration

Extend the versioned M3 local schema without resetting campaign, stars, coins, daily history, streak, or settings.

Persist only:

- Interstitial eligible-completion counter
- Last full-screen-ad wall time/date and daily interstitial count
- Rewarded grants for the current local date
- At most one pending ad hint credit and its transaction identity
- Diagnostics preference if separate from UMP state
- Schema version

Do not persist SDK ad objects, load callbacks, Activity references, raw consent strings, ad IDs, or analytics identifiers.

Migration must be idempotent and tested from fresh install, valid M3 data, corrupt/partial data, and already-migrated M4 data. Date rollback must not create extra ad/reward allowance; recover conservatively.

### 11. UI and copy

Keep the calm Magnetrail visual hierarchy. Ads are never styled as primary gameplay.

Required copy examples:

- Rewarded choice: `Watch an ad for one hint`
- Ad unavailable: `No ad available right now`
- Cap reached: `More ad hints available tomorrow`
- Coin alternative: `Use 30 coins`

Do not use deceptive close buttons, fake system UI, countdown pressure, “free” when an ad is required, guilt, flashing badges, or ads visually disguised as game content. Do not place monetization controls where accidental taps are likely.

The completion screen should render normally before any eligible interstitial; the ad occurs only after the explicit `Next level` action. Accessibility focus must return to the correct destination after dismissal. Reduced-motion and TalkBack behavior must remain correct.

### 12. Compliance and data inventory

Create `docs/M4_COMPLIANCE_NOTES.md` containing:

- SDKs and exact resolved versions
- Official documentation links and access date
- Manifest permissions and merged-manifest source
- Data categories collected/shared by each SDK based on current vendor disclosures
- Retention/deletion controls known from vendor configuration
- Consent flow and privacy-options behavior
- Target-audience decision status and blocking release questions
- Ad formats/placements/frequency caps
- Test vs production ID strategy
- Privacy-policy content requirements
- Draft inputs for Play Data safety and Ads declarations
- Manual console steps still required in AdMob, Privacy & messaging, Firebase, and Play Console

This is an implementation inventory, not legal advice. Do not claim legal compliance merely because UMP is integrated. Mark every owner decision or console action that Codex cannot complete.

## Testing requirements

Keep all M0–M3 tests passing and add deterministic tests with fake SDK adapters, fake clocks, and fake lifecycle state. Unit/UI tests must never request live ads or transmit analytics.

### Consent tests

- Consent update requested once per launch orchestration
- Ads never requested before `canRequestAds`
- Required form, not-required, denied, update-error, form-error, and previous-session cases
- Duplicate callbacks initialize ads at most once
- Privacy-options entry point visibility and presentation
- Diagnostics toggle and consent precedence

### Rewarded tests

- Explicit opt-in required
- Reward callback grants exactly one credit
- Dismiss-before-reward grants nothing
- Duplicate reward callback is idempotent
- Solver failure preserves credit
- Successful hint consumes credit and charges no coins
- Coin hint path remains unchanged
- One-credit inventory and five-per-day cap
- Offline/not-loaded/consent-blocked paths remain playable
- Process recreation does not duplicate or lose a granted pending credit

### Interstitial policy tests

- No ads during first 10 lifetime campaign levels
- Third eligible completion boundary
- 120-second cooldown edge cases
- Recent rewarded suppression
- Four-per-local-day cap
- Daily, replay, failure, restart, undo, deadlock, launch/resume exclusions
- Not-loaded/consent-denied/offline path navigates immediately
- Wall-clock rollback conservative behavior
- Exactly one full-screen ad at a time

### Analytics/Crashlytics tests

- Typed event-to-Firebase mapping
- Required parameters and bucketing
- No forbidden PII/raw consent fields
- No duplicate events from recomposition
- Collection disabled when effective policy says disabled
- Expected ad failures are not reported as non-fatals
- Approved crash keys contain no board snapshot or sensitive identifiers

### Migration/UI tests

- M3-to-M4 migration and idempotence
- Rewarded choice semantics and availability states
- Completion `Next level` remains usable if interstitial is unavailable/fails
- Privacy-options and diagnostics controls
- TalkBack focus restoration after fake ad dismissal
- Large fonts and reduced motion do not break monetization dialogs

### Manual QA

Use official test ads and UMP debug geography/test-device support only:

- Consent required, not required, denied, accepted, changed through privacy options
- Debug ad inspector where officially supported
- Reward earned, dismissed early, show failure, no fill, offline, background transition
- Interstitial eligible/ineligible boundaries and cap/cooldown behavior
- Rotate/background/kill process around load and dismissal
- Airplane mode: all gameplay and coin hints still work
- Firebase DebugView/event verification only with test consent/configuration
- Controlled Crashlytics test in a non-production build
- TalkBack, font scaling, 360/390/430 dp portrait widths

Never click production ads during testing.

## Suggested organization

Fit existing conventions and avoid rewriting stable code:

```text
app/src/main/kotlin/.../magnetrail/
├── ads/
│   ├── AdConsentManager.kt
│   ├── AdConfiguration.kt
│   ├── FullScreenAdCoordinator.kt
│   ├── RewardedAdService.kt
│   ├── GoogleRewardedAdService.kt
│   ├── InterstitialAdService.kt
│   ├── GoogleInterstitialAdService.kt
│   ├── InterstitialPolicy.kt
│   └── NoOpAdServices.kt
├── analytics/
│   ├── AnalyticsEvent.kt
│   ├── AnalyticsTracker.kt
│   └── FirebaseAnalyticsTracker.kt
├── crash/
│   ├── CrashReporter.kt
│   └── FirebaseCrashReporter.kt
└── privacy/
    ├── PrivacyState.kt
    └── DiagnosticsPolicy.kt
```

Manual constructor injection is sufficient unless the app already uses a DI framework. Do not introduce a framework solely for M4.

## Explicitly out of scope

Do not add:

- Changes to engine, solver, level-generation, certification, grading, or campaign rules
- Banner, native, app-open, rewarded-interstitial, splash, failure, or pause ads
- More rewarded placements
- Ad mediation or another ad network
- Billing, purchases, subscriptions, ad-removal purchase, or shop
- Remote config, A/B testing, attribution, performance-monitoring SDKs
- Account, login, backend, server-side reward verification, cloud save, leaderboard
- Production signing, App Bundle publishing, Play Console submission, store listing, ASO assets
- Claims of legal compliance or guessed Play Console declarations

Do not delete or weaken existing tests to make M4 pass.

## Verification commands

Use actual repository task names. At minimum run the equivalents of:

```bash
./gradlew :game-core:test
./gradlew :level-tools:test
./gradlew certifyCampaignContent
./gradlew :app:testDebugUnitTest
./gradlew lintDebug
./gradlew :app:assembleDebug
```

Also inspect dependency and manifest results with suitable tasks, for example:

```bash
./gradlew :app:dependencies
./gradlew :app:processDebugMainManifest
```

Run connected tests when an emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

If account configuration, network access, emulator, or hardware is unavailable, distinguish code/test completion from console/device verification. Never fabricate ad impressions, consent results, Firebase events, or passing connected tests.

## M4 definition of done

M4 is complete only when:

- All M0–M3 tests/content certification still pass; debug app builds and lints.
- UMP refreshes consent information each launch and ads are requested only when officially permitted.
- Required privacy options are reachable.
- Test and production ad configuration cannot be accidentally confused.
- Rewarded ads are explicit, optional, capped, idempotent, and grant exactly one durable hint credit only after reward callback.
- Coin hints and all gameplay remain available without ads/network.
- Interstitials obey every placement, cooldown, gap, daily-cap, replay, daily, and recent-rewarded rule.
- Missing/no-fill/failed ads never delay progression.
- Analytics and crash collection follow effective consent/diagnostics policy and contain no PII/raw consent/board snapshots.
- M3 progress/economy/daily data migrate without reset.
- Compliance, event, permission, and data inventories exist with owner/console blockers clearly marked.
- No excluded ad format, billing, backend, remote config, or release publishing work was introduced.

## Final response

Report:

1. What was implemented.
2. Exact SDK/resolved dependency versions and official sources consulted.
3. Consent initialization and privacy-options behavior.
4. Rewarded hint-credit and interstitial policies, including all caps.
5. Analytics event catalog and Crashlytics data controls.
6. Manifest permissions, data inventory, and target-audience/console decisions still required.
7. Persistence migration and offline/no-consent behavior.
8. Exact test/content/lint/build commands and results.
9. Manual AdMob/UMP/Firebase checks completed and any unavailable verification.
10. The next safe milestone: **M5 release readiness**—performance/battery polish, production IDs and console configuration, signed Android App Bundle, Play integrity checks, privacy policy/Data safety/ads declarations, closed testing, store listing, screenshots, ASO, staged rollout, and post-launch dashboards.

Begin by inspecting the repository, verifying current official Google documentation, running the M0–M3 baseline, and reporting missing production account/configuration decisions. Then proceed with test-safe M4 implementation unless a material blocker is found.

## Official reference starting points

Verify these pages again at implementation time because requirements and versions change:

- Google Mobile Ads SDK for Android: https://developers.google.com/admob/android/quick-start
- UMP privacy setup: https://developers.google.com/admob/android/privacy
- UMP consent mode: https://developers.google.com/admob/android/privacy/consent-mode
- Firebase Analytics Android: https://firebase.google.com/docs/analytics/android/start
- Firebase Analytics events: https://firebase.google.com/docs/analytics/android/events
- Firebase Crashlytics Android: https://firebase.google.com/docs/crashlytics/get-started?platform=android
- Crashlytics collection/customization: https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android
- Google Play Developer policies: https://play.google.com/about/developer-content-policy/
