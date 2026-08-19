# Magnetrail privacy policy — owner/legal review draft

**Not ready to publish.** Effective date, developer legal identity, contact, hosted URL, audience decision, production SDK configuration, retention choices, and deletion process are owner-required. This draft is not legal advice.

Effective date: `[OWNER REQUIRED]`  
Developer/publisher: `[OWNER REQUIRED]`  
Privacy contact: `[OWNER REQUIRED]`

## Scope and data stored on the device

Magnetrail does not provide an account, custom backend, cloud save, social profile, or user-generated content. Campaign progress, completed levels, coins, hints, daily challenge/streak state, and settings are stored locally on the device. The developer does not receive this local state through a Magnetrail server. It can be removed through the in-app reset controls where available, by clearing app storage, or by uninstalling the app. Android backup is disabled for this release.

## Advertising and consent

The planned production app contains Google AdMob rewarded and interstitial advertising and Google's User Messaging Platform (UMP). Rewarded ads are optional. Interstitial display is restricted by the app's gameplay policy. UMP obtains current consent information and exposes privacy options when required. If consent is unavailable, denied, or unresolved, the app does not request ads.

Depending on region, consent, device, and Google configuration, the Mobile Ads SDK may collect or share approximate location derived from IP address, app interactions, diagnostic information, device or other identifiers, and advertising data for advertising, analytics, fraud prevention, security, and compliance. Google and an ad creative may process network data when an ad is requested or shown. The owner must review the final AdMob partners, Privacy & messaging configuration, and Play Data safety answers before publication.

## Optional diagnostics

The app includes Firebase Analytics and Firebase Crashlytics code. Collection is disabled by default in the manifest and is enabled by app logic only when both the local Diagnostics setting is enabled and the consent state permits it. Analytics events use coarse gameplay fields such as a level identifier, attempt/duration/count bucket, difficulty, ad-flow outcome, and settings state; app code rejects free text and known direct identifiers. Crash reports may include stack traces, app/device state, installation identifiers, and bounded diagnostic keys.

Google/Firebase processes this information to provide analytics, stability diagnostics, fraud/security, and service operation. Exact retention, deletion, Google Signals, data sharing, and linked-product choices depend on the production Firebase project and must be inserted here after owner/console verification: `[OWNER REQUIRED]`.

## Controls and deletion

- Use the in-app Diagnostics switch to stop or allow optional Analytics/Crashlytics collection, subject to consent.
- Use Privacy options in the app when UMP reports that privacy choices are available.
- Clear local gameplay information by clearing Magnetrail's storage or uninstalling it. Reinstalling starts fresh because Android backup is disabled.
- Contact `[OWNER REQUIRED CONTACT]` for privacy questions or requests concerning data controlled by the developer. The owner must document what can be located/deleted without an account identifier and how processor requests are handled.

Previously transmitted data may remain for the periods selected in Firebase/AdMob or required for security/legal purposes. Those periods and request procedures must be confirmed before this draft is published.

## Children and target audience

The production target age groups have not been selected. The current metadata is not directed to children, and live ads remain build-blocked until the owner selects the audience and aligns Play, UMP, AdMob, SDK request configuration, creative controls, and this policy. If any child or mixed audience is selected, publication must stop for a Families-policy and age-screening review.

## Security and transfers

The app disables cleartext network traffic. Google SDK traffic is documented by Google as protected in transit. Third-party processing may occur in countries outside the user's country under the provider's terms and safeguards; the owner must supply the jurisdiction-specific wording appropriate to the publisher.

## Providers and contact

- Google AdMob and UMP: [Google privacy policy](https://policies.google.com/privacy)
- Firebase Analytics and Crashlytics: [Firebase privacy and security](https://firebase.google.com/support/privacy)
- Publisher privacy contact and postal details: `[OWNER REQUIRED]`

