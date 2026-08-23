# Magnetrail release blocker log

Updated: **2026-08-19**. `OPEN` items make the recommendation `NO-GO`.

| ID | Status | Blocker | Owner/evidence needed |
|---|---|---|---|
| M5-B01 | OPEN | Permanent package and version code 1 are not owner/Play confirmed. | Owner + Play app/version history |
| M5-B02 | OPEN | No owner-authorized upload key; no Play App Signing/certificate record. | Release owner/security custodian |
| M5-B03 | OPEN | Production AdMob app/unit IDs, UMP console choices, audience, and `app-ads.txt` are absent. | Product/privacy/ad owner + console evidence |
| M5-B04 | OPEN | Genuine production Firebase config and retention/data-sharing choices are absent. | Firebase/privacy owner; or approve SDK removal and update declarations |
| M5-B05 | OPEN | Privacy identity/contact/effective date/deletion process and public HTTPS URL are absent. | Publisher/legal/privacy owner |
| M5-B06 | OPEN | Play Data safety, Ads, Advertising ID, audience, content rating, app access and category declarations are not approved/submitted. | Play account owner |
| M5-B07 | OPEN | Representative API 24/mid/API 35/tablet/foldable/accessibility/upgrade/ad-consent QA is incomplete. | QA owner + evidence matrix |
| M5-B08 | OPEN | Closed-test applicability, roster/duration, feedback and production-access evidence are unavailable. | Play account/release owner |
| M5-B09 | OPEN | Pre-launch report, device catalog, review/managed publishing, countries and first-release plan are unavailable. | Play/release owner |
| M5-B10 | OPEN | Release screenshots need final capture/owner review against the final signed candidate. | QA/brand owner |

Repository defects discovered during M5 are tracked in the binary and QA reports; close an item only with a dated artifact/link, approver, and exact build SHA-256. Never paste credentials or tester personal data here.

