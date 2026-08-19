# Magnetrail post-launch decision dashboard

No new SDK, remote config, attribution, or A/B framework is authorized. Firebase metrics exist only for users whose diagnostics toggle and consent state permit collection; AdMob revenue/fill/eCPM comes only from AdMob; Play conversion/vitals/reviews come only from Play. Always label low-volume and consent bias.

| Decision area | Source / M4 event | Weekly question and provisional action |
|---|---|---|
| Store conversion | Play acquisition report | Does conversion materially drop by country/device/listing experiment? Check listing accuracy and technical availability before changing copy. |
| D1/D7 retention | Consented Firebase cohorts | Is a stable, sufficiently sized cohort declining vs prior comparable weeks? Inspect onboarding/completion and crashes; do not infer population retention from tiny consented samples. |
| Tutorial / L1 / L5 / L10 | `level_start`, `level_complete` | Where is the first sustained completion drop? Reproduce the level and clarity issue before proposing content changes. |
| Pack/level difficulty | starts/completes, duration/attempt buckets, restart/deadlock | Flag a sharp within-pack outlier with adequate starts; verify solvability and UX before tuning. |
| Hints/economy | `hint_choice_open`, `hint_coin_spend`, `hint_shown`, rewarded events | Investigate rising insufficient-coin/offer rates, any negative balance, double reward, or hint mismatch immediately. |
| Daily/streak | `daily_start`, `daily_complete` and streak bucket | Is participation/completion/streak continuation stable? Check date/offline/fallback correctness before messaging changes. |
| Rewarded funnel | offer → load → show → earned → dismiss | Any earned-without-callback, repeated callback, large load failure, or consent mismatch blocks rollout. |
| Interstitial quality | eligible/show/dismiss and reason/outcome | Compare show frequency with M4 caps and complaints. Any mid-puzzle display or cap breach blocks rollout. |
| Ad health | AdMob revenue, eCPM, match/fill, policy center | Use AdMob only; never derive revenue from client events or create test live impressions. Policy alert blocks advancement. |
| Stability | Play user-perceived crash/ANR; Crashlytics consented diagnostics | Review overall and device-cluster regressions daily during rollout. Provisional guardrails: Play bad-behavior thresholds currently document 1.09% overall user-perceived crash and 0.47% ANR; use stricter baseline-relative judgment. |
| Ratings/support | Play reviews and owner support channel | Cluster repeated gameplay, listing, accessibility, ad, consent, progress and device complaints; link each actionable cluster to triage. |

## Review rhythm

- During rollout: release owner checks stability/policy daily and records an advance/hold/halt decision.
- Weekly after 100%: product, QA, privacy/ads and engineering review the table, affected-device clusters, consent bias, open blockers and support trends.
- Monthly: verify SDK/Play policy changes, Data safety/policy accuracy, retention/deletion settings, app-ads.txt, device exclusions, and whether additional localization/device coverage is justified.

Every decision record contains date/time zone, app version, AAB hash, data window, sample size/coverage, comparison baseline, named reviewer, decision and follow-up. Do not create thresholds that encourage collecting more personal data; prefer coarse typed events already defined in M4.

Source: [Android vitals](https://developer.android.com/topic/performance/vitals/index.html).

