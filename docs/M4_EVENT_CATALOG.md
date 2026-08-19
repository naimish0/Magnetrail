# Magnetrail M4 event catalog

Status: implemented, collection-disabled by default  
Last reviewed: 2026-08-19

All events are constructed as typed `AnalyticsEvent` values in the Android `:app` layer. They are discarded by the no-op tracker when Firebase is not configured and by the Firebase tracker unless both the local diagnostics opt-in and the effective consent policy allow collection. No event changes gameplay or economy state.

## Product events

| Event | Stable trigger | Parameters | Product question | Privacy classification |
|---|---|---|---|---|
| `level_start` | ViewModel enters a campaign attempt from Home or level selection | `level_id`, `pack`, `difficulty`, `origin` | Where do campaign attempts begin? | Product interaction; shipped content identifiers only |
| `level_complete` | Result-driven completion commits once | `level_id`, `stars`, `actions`, `overloads`, `hints`, `duration_bucket` | Which difficulty/quality patterns lead to completion? | Product interaction; no board snapshot |
| `level_restart` | Accepted restart action | `level_id`, `attempt_bucket` | Where are players restarting? | Product interaction; count bucketed |
| `level_deadlock` | A committed result first enters deadlock | `level_id`, `actions_bucket` | Where do deadlocks occur? | Product interaction; count bucketed |
| `hint_choice_open` | Accepted opening of the explicit hint-choice dialog | `level_id` | How often is hint help considered? | Product interaction |
| `hint_coin_spend` | Atomic coin spend succeeds | `balance_bucket` | Are earned coins supporting hints? | Economy interaction; balance bucketed |
| `hint_shown` | A valid solver hint becomes visible | `source` = `coins` or `rewarded` | Which voluntary hint source is used? | Product/monetization interaction |
| `daily_start` | A Daily Challenge is ready and shown | `difficulty` | Is Daily Challenge being started? | Product interaction; no date, seed, or daily ID |
| `daily_complete` | Result-driven daily completion commits once | `difficulty`, `stars`, `streak_bucket` | Is daily difficulty/reward cadence healthy? | Product interaction; streak bucketed, no date |

## Monetization events

| Event | Stable trigger | Parameters | Product question | Privacy classification |
|---|---|---|---|---|
| `rewarded_offer` | Explicit rewarded choice is attempted | `outcome` | Was a credit, ready ad, cap, or unavailable state offered? | Monetization interaction; coarse state |
| `rewarded_load_result` | SDK load callback | `result` | Are test/production rewarded loads healthy? | Ad diagnostics; coarse result only |
| `rewarded_show` | SDK full-screen shown callback | none | Did an explicitly requested rewarded ad display? | Ad interaction |
| `rewarded_earned` | First SDK reward callback for the local transaction | none | Did the SDK signal the promised reward? | Ad interaction; transaction ID is never logged |
| `rewarded_dismiss` | SDK full-screen dismissal callback | `earned` | Was dismissal before or after reward? | Ad interaction |
| `interstitial_eligible` | Every accepted campaign completion `Next level` evaluation | `reason`, `outcome` | Which policy gate showed or skipped an ad? | Monetization policy; coarse reason |
| `interstitial_show` | SDK full-screen shown callback | none | Did an eligible boundary ad display? | Ad interaction |
| `interstitial_dismiss` | SDK dismissal callback | none | Was normal navigation resumed after display? | Ad interaction |
| `ad_show_failure` | SDK show-failure callback | `format`, `category` | Are full-screen displays failing? | Ad diagnostics; coarse category, no SDK payload |

Expected load no-fill, network loss, and offline conditions are Analytics result categories only. They are not Crashlytics non-fatals.

## Consent and settings events

| Event | Stable trigger | Parameters | Product question | Privacy classification |
|---|---|---|---|---|
| `consent_flow_result` | UMP refresh/form completion state boundary | `state` | Did the consent orchestration complete, fail, or use permitted cached state? | Consent category only; never a raw consent string |
| `privacy_options_open` | Player explicitly opens the UMP privacy-options entry point | none | Is the review/change entry point used? | Settings interaction |
| `diagnostics_setting_changed` | Local diagnostics switch changes | `enabled` | Is optional diagnostics enabled locally? | Settings interaction |

## Buckets and constraints

- Count buckets: `0`, `1_2`, `3_5`, `6_10`, `11_plus`.
- Duration buckets: `under_30s`, `30_59s`, `1_2m`, `3_9m`, `10m_plus`.
- Custom names are lowercase snake_case and at most 40 characters.
- Typed events reject known forbidden parameter keys and string values longer than 100 characters before Firebase mapping.
- No custom Firebase user ID is set.
- Never add name, email, phone, contacts, precise location, free text, advertising ID, Firebase installation ID, raw consent data, date of birth, exact local date, daily seed, user-generated identifier, or full board state.
- Completion and deadlock events originate from committed result boundaries; ad events originate from SDK callbacks/coordinator decisions; Compose recomposition emits none of them.

