# Magnetrail release checklist

Use this list for one exact AAB. A box is evidence, not intent. Current M5 state is `NO-GO` while owner/external entries are open.

## Identity, source, and build

- [ ] Owner confirms permanent `com.rameshta.magnetrail`, `versionCode`, `versionName`, name, category, price, locale, countries and release owner.
- [ ] Clean reviewed source revision recorded; working tree and dependency changes approved.
- [ ] API target policy rechecked; min/target/compile values match manifest and bundle.
- [ ] Unit, migration, analytics privacy, ad policy, UI, campaign/daily certification, lint, R8 bundle, manifest and listing checks pass.
- [ ] Baseline Profile generated from the frozen candidate and packaged; startup benchmark/device limitations recorded.
- [ ] Final native libraries/ABIs and 16 KB ZIP/ELF alignment verified.
- [ ] Current bundletool validates AAB; universal/device APK sets install and pass smoke tests, fresh and upgrade where available.
- [ ] Mapping/profile/native debug-symbol artifacts and SHA-256 values recorded; no `.class`, `.tab`, `.keystream`, `.len`, secrets, debug tools or sample IDs ship unintentionally.

## Signing and services

- [ ] Owner-authorized upload key is supplied through protected local/CI variables; no key/password is committed or logged.
- [ ] Upload certificate fingerprint matches the Play record; Play App Signing/app-signing certificate status recorded.
- [ ] Production AdMob app/rewarded/interstitial IDs match package; UMP/partners/audience flags/privacy options/app-ads.txt verified.
- [ ] Production Firebase app/package/certificates, Analytics/Crashlytics toggles, retention and data sharing verified—or SDK removal is approved and declarations updated.
- [ ] Live traffic is never clicked for QA; test-device/test-ad evidence is retained.

## QA, listing, privacy, and Play

- [ ] QA matrix is green on API 24, mid, API 35, API 36/16 KB, phone/tablet/foldable and accessibility configurations, including upgrade/offline/process death/ad-consent failures.
- [ ] Actual candidate screenshots, icon, feature graphic, copy, support contact and public privacy URL are owner approved.
- [ ] Privacy policy, Data safety, Ads, Advertising ID, target audience/content, content rating and app-access answers match the binary.
- [ ] Developer/package verification, app creation, device catalog, pre-launch report, closed-test applicability/evidence and production access are green.
- [ ] Rollout/hotfix decision owner and observation channel are staffed; first-release limitation is understood.
- [ ] Owner gives explicit final upload and production-release approval for the recorded hash.

