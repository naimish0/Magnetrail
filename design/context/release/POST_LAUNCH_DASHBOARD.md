# Magnetrail post-launch decision dashboard

No developer analytics, remote config, attribution, or A/B framework is shipped in this release. AdMob revenue/fill/eCPM comes only from AdMob; Play conversion/vitals/reviews come only from Play. Always label low-volume and consent bias.

| Decision area | Source / M4 event | Weekly question and provisional action |
|---|---|---|
| Store conversion | Play acquisition report | Does conversion materially drop by country/device/listing experiment? Check listing accuracy and technical availability before changing copy. |
| D1/D7 retention | Not available this release | Do not infer retention without an approved measurement source. Use Play aggregate signals and qualitative closed-test feedback only for this cycle. |
| Tutorial / L1 / L5 / L10 | QA and support reports | Reproduce any reported level or clarity issue before proposing content changes. Dormant typed events are not transmitted. |
| Pack/level difficulty | QA, support, and review clusters | Verify solvability and UX before tuning any repeated level-specific complaint. |
| Hints/economy | QA and support reports | Investigate any negative balance, double reward, or hint mismatch immediately. |
| Daily/streak | QA and support reports | Check date, offline, and fallback correctness before changing messaging. |
| Rewarded funnel | AdMob aggregate reports plus QA | Any reward mismatch, repeated callback, large load failure, or consent mismatch blocks rollout. |
| Interstitial/app-open quality | AdMob aggregate reports plus QA | Compare frequency with policy and complaints. Any mid-puzzle display or cooldown breach blocks rollout. |
| Ad health | AdMob revenue, eCPM, match/fill, policy center | Use AdMob only; never derive revenue from client events or create test live impressions. Policy alert blocks advancement. |
| Stability | Play user-perceived crash/ANR | Review overall and device-cluster regressions daily during rollout. Provisional guardrails: Play bad-behavior thresholds currently document 1.09% overall user-perceived crash and 0.47% ANR; use stricter baseline-relative judgment. |
| Ratings/support | Play reviews and owner support channel | Cluster repeated gameplay, listing, accessibility, ad, consent, progress and device complaints; link each actionable cluster to triage. |

## Review rhythm

- During rollout: release owner checks stability/policy daily and records an advance/hold/halt decision.
- Weekly after 100%: product, QA, privacy/ads and engineering review the table, affected-device clusters, consent bias, open blockers and support trends.
- Monthly: verify SDK/Play policy changes, Data safety/policy accuracy, retention/deletion settings, app-ads.txt, device exclusions, and whether additional localization/device coverage is justified.

Every decision record contains date/time zone, app version, AAB hash, data window, sample size/coverage, comparison baseline, named reviewer, decision and follow-up. Do not create thresholds that encourage collecting more personal data. The dormant M4 event schema may be reviewed in a later release before any analytics provider is added.

Source: [Android vitals](https://developer.android.com/topic/performance/vitals/index.html).
