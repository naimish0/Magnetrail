# Magnetrail M5 binary report

Verification date: **2026-08-19, Asia/Kolkata**  
Source base: `656688898cc5eecbfdaa7564868150dd80dc793b` plus the uncommitted M5 working-tree changes  
Status: **structurally verified, unsigned, not uploadable**

## Final local AAB

| Field | Result |
|---|---|
| Path | `app/build/outputs/bundle/release/app-release.aab` |
| Size | 9,056,349 bytes |
| SHA-256 | `febd18c94f78af6d3883cfee9e1e7b6848f7f21458c3dd943a38dea9cc311f69` |
| Package/version | `com.rameshta.magnetrail`, code 1, name 1.0 |
| SDK | min 24, target 36, compile 37 |
| Build | R8 optimized, resources shrunk, non-debuggable, no cleartext, no backup |
| Signing | `jarsigner` reports unsigned; no owner upload key was supplied |
| Services | Structural `release_blocked`; no production AdMob IDs, Firebase config, UMP request, or live ads |

Gradle 9.3.1, AGP 9.1.1, launcher JVM 26 and Java 21-compatible daemon were used. `validateReleaseConfiguration` correctly failed when production mode was requested without all eight owner inputs. The final AAB scan found zero Google sample-ID, debug-package, or Android test-package hits. The merged-manifest task checked the SDK, package, flags, allowed permissions, explicit exported state and protected exported library components.

## Bundletool and generated APKs

Official bundletool 1.18.3 (`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`) validated the final AAB.

| Local QA artifact | Size / SHA-256 | Result |
|---|---|---|
| `magnetrail-1-universal-local-debug.apks` | SHA-256 `14f234da0c9723781739a984d9109edc2f93d8c7fa0a1923a685f1095b037969` | Built; universal APK passed `zipalign -c -P 16 -v 4`. |
| `magnetrail-1-pixel7a-16kb-local-debug.apks` | SHA-256 `1e3e67a77eb2c5d88c707238f90fbd8adcf9509fdcc13909b5065caa4f2a75bf` | Built from recorded device spec, installed on Pixel 7a AVD, cold-launched successfully in 767 ms. |

These APK sets were signed with the standard local Android debug key only so bundletool could install them. They are QA artifacts, not Play artifacts. A state-preserving reinstall of the preceding candidate retained the expected 155-coin balance; a genuine upgrade from a previously uploaded version remains unavailable.

## R8, profile, native, and size evidence

- Mapping: `app/build/outputs/mapping/release/mapping.txt`, SHA-256 `83f3bfddd474961ab79c5551ad6a75f820ec77a7935aceabe5e723bdee9e4d91`.
- Compiled packaged Baseline Profile: 10,552 bytes, SHA-256 `f6c550868b9b924adcb70af279a48e32e652d6b2cb16fe55b830c9bc8420732`; profile metadata is also present in the bundle.
- Baseline/startup profile generation completed on the Pixel 7a API 36 emulator. A physical Samsung Android 16 Macrobenchmark completed five profile-enabled cold starts: time to initial display min 187.0 ms, median 204.6 ms, max 217.6 ms. This is one high-end device result, not a population performance claim.
- The benchmark initially exposed an optimized-startup crash in Mobile Ads' transitive WorkManager 2.7.0. Pinning stable WorkManager 2.11.2 fixed it; the same benchmark then passed.
- Native libraries: `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` for arm64-v8a, armeabi-v7a, x86 and x86_64. Apple LLVM `objdump -p` reported every ELF `LOAD` segment aligned to `2**14` (16 KB). The universal APK passed 16 KB ZIP alignment, and the device split ran on a confirmed 16,384-byte-page emulator.
- Bundletool estimated the Pixel arm64/xxhdpi/en/API 32+ compressed delivery at 3,831,386 bytes. Play Console remains authoritative.

## Verification results

| Check | Result |
|---|---|
| Core tests | 58/58 passed |
| Level tools | 2/2 passed |
| App JVM tests | 66/66 debug and 66/66 release passed |
| Content certification | 100 campaign levels + 7 Daily fallbacks passed with production engine/solver |
| Connected debug UI | 14/14 Pixel 7a AVD and 14/14 Samsung SM-S928B passed |
| Lint | Debug/release: 0 errors, 10 non-blocking target/tool/dependency-version warnings each |
| Release manifest/config/R8 bundle | Passed |
| Current bundletool validation/16 KB alignment/final split cold launch | Passed |

Not run or not proven: API 24, mid-range/API 35, tablet/foldable, full human TalkBack/Switch Access, production UMP/AdMob/Firebase flows, Play pre-launch/device catalog, true uploaded-version upgrade, closed test, vitals volume, upload signing, or console declarations. These remain release blockers.

