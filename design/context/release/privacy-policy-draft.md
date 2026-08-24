# Magnetrail privacy policy — owner/legal review draft

**Not ready to publish.** Audience decision, production advertising/consent configuration, verification of the hosted bytes, and legal review are still required. This draft is not legal advice. The canonical deployable text is `design/context/privacy-policy.md`.

Effective date: `[OWNER REQUIRED]`  
Developer/publisher: Naimish Gupta
Privacy contact: [naimish.app@gmail.com](mailto:naimish.app@gmail.com)
Postal address: Sandi, Hardoi, 241403, Uttar Pradesh
Public privacy-policy URL: [https://naimish0.github.io/Magnetrail/](https://naimish0.github.io/Magnetrail/)

## Scope and data stored on the device

Magnetrail does not provide an account, custom backend, cloud save, social profile, or user-generated content. Campaign progress, completed levels, coins, hints, daily challenge/streak state, and settings are stored locally on the device. The developer does not receive this local state through a Magnetrail server. It can be removed through the in-app reset controls where available, by clearing app storage, or by uninstalling the app. Android backup is disabled for this release.

## Advertising and consent

The planned production app contains Google AdMob rewarded and interstitial advertising and Google's User Messaging Platform (UMP). Rewarded ads are optional, have no app-defined daily limit, and grant a hint or eligible level skip only after Google's SDK confirms reward completion. Interstitial opportunities begin at the Campaign Level 11 boundary and then follow a shared five-first-completion cadence across Campaign, normal Infinite, and Auto Journey. An eligible interstitial is evaluated after the solved board is recorded and may appear over the Celebration screen. There is no app-defined daily interstitial limit or general cooldown, but interstitials are suppressed for 60 seconds after a rewarded ad is completed. UMP obtains current consent information and exposes privacy options when required. If consent is unavailable, denied, or unresolved, the app does not request ads.

Depending on region, consent, device, and Google configuration, the Mobile Ads SDK may collect or share approximate location derived from IP address, app interactions, diagnostic information, device or other identifiers, and advertising data for advertising, analytics, fraud prevention, security, and compliance. Google and an ad creative may process network data when an ad is requested or shown. The owner must review the final AdMob partners, Privacy & messaging configuration, and Play Data safety answers before publication.

## Developer diagnostics

No developer analytics or crash-reporting SDK is included in this release. The dormant typed event and reporter abstractions use no-op implementations and do not transmit data. Adding a diagnostics provider in a later release requires a new privacy, consent, retention, Data Safety, dependency, and binary review.

## Controls and deletion

- Use Privacy options in the app when UMP reports that privacy choices are available.
- Clear local gameplay information by clearing Magnetrail's storage or uninstalling it. Reinstalling starts fresh because Android backup is disabled.
- Contact [naimish.app@gmail.com](mailto:naimish.app@gmail.com) for privacy questions or requests concerning data controlled by the developer. The publisher must document what can be located or deleted without an account identifier and how processor requests are handled.

Data transmitted through advertising and consent services may remain for the periods selected in AdMob or required for security/legal purposes. Those periods and request procedures must be confirmed before this draft is published.

## Children and target audience

The production target age groups have not been selected. The current metadata is not directed to children, and live ads remain build-blocked until the owner selects the audience and aligns Play, UMP, AdMob, SDK request configuration, creative controls, and this policy. If any child or mixed audience is selected, publication must stop for a Families-policy and age-screening review.

## Security and transfers

The app disables cleartext network traffic. Google SDK traffic is documented by Google as protected in transit. Third-party processing may occur in countries outside the user's country under the provider's terms and safeguards; the owner must supply the jurisdiction-specific wording appropriate to the publisher.

## Providers and contact

- Google AdMob and UMP: [Google privacy policy](https://policies.google.com/privacy)
- Publisher: Naimish Gupta; privacy contact: [naimish.app@gmail.com](mailto:naimish.app@gmail.com); postal address: Sandi, Hardoi, 241403, Uttar Pradesh
