# Play Data safety worksheet

Binary basis: current source with Mobile Ads 25.4.0 and UMP 4.0.0. No developer analytics or crash-reporting SDK is packaged in this release. **Do not paste this worksheet blindly into Play.** Verify the signed production binary, consoles, consent behavior, policies, and Google's current SDK disclosures.

Status labels: **verified** = established from repository/vendor docs; **owner decision** = business/legal/config choice missing; **console verification required** = must be checked in the production service or Play.

## Top-level answers

| Question | Working answer | Status / evidence |
|---|---|---|
| Does the app collect or share required data types? | Yes, when production AdMob is enabled; exact sharing answers require processor/config review. | Console verification required |
| Is all collected data encrypted in transit? | App disallows cleartext; Google documents TLS in its SDK disclosure. | Verified in app; console/vendor re-check required |
| Can users request deletion? | Local data can be cleared/uninstalled. Processor/developer request route is not yet defined. | Owner decision |
| Account creation/deletion | No account is offered. | Verified |
| Optional collection | Ad/consent processing is conditional, but advertising is part of the planned production service. No developer diagnostics collection is shipped. | Verified in code; console verification required |

## Data inventory

| Data type | Collector | Collection/sharing and purpose | Status |
|---|---|---|---|
| Approximate location | Mobile Ads, inferred from IP | Advertising, analytics, fraud prevention/security; sharing depends on ad delivery/partners. | Vendor disclosure verified; console answer required |
| Device or other identifiers | Mobile Ads | Advertising, analytics, app functionality, diagnostics, fraud/security. AD_ID permission is present transitively. | Vendor/binary verified; owner/console answer required |
| App interactions | Mobile Ads | Analytics, advertising, and app functionality as documented by the provider. The app's dormant typed product events are not transmitted. | Vendor disclosure verified; console answer required |
| Crash logs | No developer collector | The app does not transmit crash logs to a developer diagnostics service in this release. Mobile Ads provider disclosures still require final review. | Repository verified; vendor/console re-check required |
| Diagnostics/performance | Mobile Ads | Diagnostics, security/fraud prevention, and service operation as documented by the provider. | Vendor disclosure verified; console required |
| Advertising data | Mobile Ads | Ad delivery, measurement, frequency, fraud prevention. | Console verification required |
| In-app messages/free text | None in the app | Feedback template is external/manual and not an in-app collector. | Verified |
| Local game progress/settings | Android DataStore on device | App functionality; not transmitted by Magnetrail code as a state record. | Verified |
| User IDs, email, contacts, photos, precise location, health, financial, messages, files | No first-party collection path found | Do not declare absent until the final SDK scanner/Play questionnaire is checked. | Repository verified; console verification required |

## Configuration evidence required before submission

- [ ] Record production Mobile Ads/UMP SDK versions and confirm no developer analytics/crash SDK from the signed AAB dependency report.
- [ ] Export/review AdMob Privacy & messaging partners, purposes, consent mode defaults, restricted data processing, and serving regions.
- [ ] Decide target ages; record child-directed and under-age-of-consent request flags. Current SDK fields are unspecified and production remains blocked.
- [ ] Confirm no developer analytics/crash-reporting provider is configured for this release.
- [ ] Demonstrate consent denied/obtained behavior on a test device without live-ad clicks.
- [ ] Reconcile every Play question with the hosted policy and final AAB SDK index.
- [ ] Owner/legal approver signs and dates the worksheet.

Sources: [Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [AdMob disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure).
