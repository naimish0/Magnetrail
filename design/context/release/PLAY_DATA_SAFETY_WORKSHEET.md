# Play Data safety worksheet

Binary basis: M5 source with Mobile Ads 25.4.0, UMP 4.0.0, Firebase Analytics/Crashlytics via BoM 34.17.0. **Do not paste this worksheet blindly into Play.** Verify the signed production binary, consoles, consent behavior, policies, and Google's current SDK disclosures.

Status labels: **verified** = established from repository/vendor docs; **owner decision** = business/legal/config choice missing; **console verification required** = must be checked in the production service or Play.

## Top-level answers

| Question | Working answer | Status / evidence |
|---|---|---|
| Does the app collect or share required data types? | Yes, when production AdMob/Firebase is enabled; exact sharing answers require processor/config review. | Console verification required |
| Is all collected data encrypted in transit? | App disallows cleartext; Google documents TLS in its SDK disclosure. | Verified in app; console/vendor re-check required |
| Can users request deletion? | Local data can be cleared/uninstalled. Processor/developer request route is not yet defined. | Owner decision |
| Account creation/deletion | No account is offered. | Verified |
| Optional collection | Diagnostics is controlled by local toggle plus consent. Ad/consent processing is conditional but advertising is part of the planned production service. | Verified in code; console verification required |

## Data inventory

| Data type | Collector | Collection/sharing and purpose | Status |
|---|---|---|---|
| Approximate location | Mobile Ads, inferred from IP | Advertising, analytics, fraud prevention/security; sharing depends on ad delivery/partners. | Vendor disclosure verified; console answer required |
| Device or other identifiers | Mobile Ads; Firebase installations/Analytics/Crashlytics | Advertising, analytics, app functionality, diagnostics, fraud/security. AD_ID permission is present transitively. | Vendor/binary verified; owner/console answer required |
| App interactions | Mobile Ads; Analytics typed events | Analytics, advertising, app functionality. App events contain coarse gameplay/ad outcomes. | Verified; console answer required |
| Crash logs | Crashlytics, when diagnostics permitted | Diagnostics and app stability. | Verified; retention/console required |
| Diagnostics/performance | Mobile Ads; Crashlytics | Diagnostics, security/fraud prevention, service operation. | Vendor disclosure verified; console required |
| Advertising data | Mobile Ads | Ad delivery, measurement, frequency, fraud prevention. | Console verification required |
| In-app messages/free text | None in the app | Feedback template is external/manual and not an in-app collector. | Verified |
| Local game progress/settings | Android DataStore on device | App functionality; not transmitted by Magnetrail code as a state record. | Verified |
| User IDs, email, contacts, photos, precise location, health, financial, messages, files | No first-party collection path found | Do not declare absent until the final SDK scanner/Play questionnaire is checked. | Repository verified; console verification required |

## Configuration evidence required before submission

- [ ] Record production Mobile Ads/UMP/Firebase SDK versions from the signed AAB dependency report.
- [ ] Export/review AdMob Privacy & messaging partners, purposes, consent mode defaults, restricted data processing, and serving regions.
- [ ] Decide target ages; record child-directed and under-age-of-consent request flags. Current SDK fields are unspecified and production remains blocked.
- [ ] Record Firebase Analytics retention, Google Signals, ads personalization, data sharing, linked products, and Crashlytics retention/deletion choices.
- [ ] Demonstrate diagnostics off/on and consent denied/obtained behavior on a test device without live-ad clicks.
- [ ] Reconcile every Play question with the hosted policy and final AAB SDK index.
- [ ] Owner/legal approver signs and dates the worksheet.

Sources: [Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [AdMob disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure), [Firebase disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

