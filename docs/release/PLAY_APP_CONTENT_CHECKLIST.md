# Play App content checklist

No console answers have been submitted by M5. The accountable owner must preserve screenshots/exports of the final answers and compare them to the signed AAB.

| Declaration | Proposed answer / action | Gate |
|---|---|---|
| App access | All functionality is accessible without login; no credentials/instructions needed. | Verify installed production split and answer “all functionality available.” |
| Ads | **Contains ads**: optional rewarded ads and capped interstitials. | Required even if a consent state prevents some requests. Verify live units/package in AdMob. |
| Advertising ID | AD_ID permission is present through Mobile Ads. | Answer the Play purpose questionnaire consistently with advertising/analytics/fraud prevention. |
| Target audience/content | Unanswered. Owner selects exact age groups and general/mixed/children treatment. | Release blocker. If children/mixed is selected, stop for Families SDK/creative/age-screen review. |
| Content rating | Complete IARC questionnaire as a puzzle game; answer ads and all content questions factually. | Owner/console required; do not predict a rating. |
| Data safety | Use `PLAY_DATA_SAFETY_WORKSHEET.md`; reconcile final SDK/config. | Owner/legal/console required. |
| Privacy policy | Host the approved policy at a public, active, non-geofenced HTTPS page and link it in Play and the app. | Draft only; release blocker. |
| News / health / financial / government | Repository has no such function or claim. | Re-verify final listing and answer not applicable where Play asks. |
| App category | Game / Puzzle. | Owner confirmation and console selection required. |
| Data deletion | No account. Local storage can be cleared/uninstalled; controller request route is missing. | Policy/console answer must be finalized. |

## Ads and consent sign-off

- [ ] Production AdMob app ID is linked to `com.rameshta.magnetrail`; exactly one rewarded and one interstitial unit are approved.
- [ ] UMP message/partners are published for required regions; privacy-options entry is visible when required.
- [ ] Denied/unresolved/error/offline consent states do not request ads; gameplay remains usable.
- [ ] Reward is granted once only from the earned callback; dismiss/failure grants nothing.
- [ ] Interstitials never interrupt a puzzle and do not exceed M4 frequency/session/cooldown limits.
- [ ] Target-audience SDK flags match Play. Current production gate only allows an explicitly owner-reviewed `general` path; other choices require redesign/re-review.
- [ ] `app-ads.txt` developer website and seller line are verified in AdMob.
- [ ] Test traffic uses Google's test ads/test devices. Nobody clicks live ads for QA.

## Final consistency review

- [ ] Name, description, screenshots, ads claim, offline claim, levels, daily challenge, and accessibility claims match the installed release-derived APK.
- [ ] Support and privacy URLs load publicly over HTTPS and match the Play developer identity.
- [ ] Play pre-launch report and device-catalog exclusions are reviewed and documented.
- [ ] 2026 developer/package verification and Play App Signing status are recorded.
- [ ] Owner approves and dates every declaration export.

