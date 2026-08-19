# Magnetrail M5 QA matrix

Updated: **2026-08-19**. `PASS` means an identified artifact/device was actually run; `OPEN` and `BLOCKED` are release blockers where required. Final command counts and artifact hashes belong in `M5_BINARY_REPORT.md`.

## Current environment results

| Area | Environment | Status | Evidence / limitation |
|---|---|---|---|
| M0-M4 baseline | Local Gradle, pre-M5 revision | PARTIAL | Core 58, tools 2, app 59, content 100+7 passed; lint/build passed; physical UI was 13/14 due a persisted-state selector. Gap report records exact result. |
| Updated unit/release policy | Local JVM | PASS | 66/66 debug and 66/66 release tests passed. |
| Baseline profile/performance | Pixel 7a AVD + Samsung SM-S928B | PASS (limited hardware) | Profile generated; five physical cold starts min 187.0 ms, median 204.6 ms, max 217.6 ms. |
| API 36 UI | Pixel 7a 16 KB AVD / Samsung SM-S928B | PASS | 14/14 on each device after stable-selector fix. |

## Required final matrix

| Configuration / journey | Status | Exit evidence |
|---|---|---|
| API 24 minimum, fresh install, campaign/offline/persistence | BLOCKED | No API 24 device available |
| Representative mid-range Android/device | BLOCKED | No qualifying device evidence |
| API 35 | BLOCKED | No API 35 device available |
| API 36 16 KB, release-derived split, fresh install | PASS | Confirmed 16,384-byte page size; final device split installed and cold-launched |
| API 36 physical phone, release/debug regression | PASS | 14/14 connected tests; optimized physical startup benchmark passed |
| Tablet/foldable, resize/multi-window/large screen | BLOCKED | No representative environment |
| Upgrade install from last shipped/tested build | BLOCKED | No prior owner-approved uploaded artifact |
| TalkBack traversal and announcements | BLOCKED | Human accessibility review required |
| Switch Access / keyboard | BLOCKED | Human/device review required |
| 200% font / large display / contrast / touch targets | BLOCKED | Human visual/interaction review required |
| Reduced motion, sound, haptic | PENDING | Functional automation exists; subjective physical review required |
| Gesture/predictive back, rotation/resize, background/process death | PENDING | Targeted manual run |
| Offline/no consent/denied/error/ad-load failure | BLOCKED | Production-like UMP test configuration absent; structural release makes no requests |
| Reward once, dismiss/failure none; interstitial caps/no mid-level | PASS (unit policy) / BLOCKED (SDK E2E) | M4 tests pass; production/test-device end-to-end evidence missing |
| DataStore M4→M5, corruption recovery, nonnegative economy | PASS (interim) | New M5 unit tests; clean rerun pending |
| 100 campaign + 7 Daily fallback integrity | PASS | Final certification passed |
| Startup/frame/memory/battery | PARTIAL | Startup measured on high-end physical device; frame/memory/battery and representative low/mid hardware remain open |
| Store screenshots vs final signed candidate | BLOCKED | Final signed candidate and owner review absent |

## Manual regression script

Fresh launch → tutorial → complete early level → reopen/select next → undo/restart/back → advanced level → Daily → hint with/without coins → optional rewarded test flow → interstitial eligibility boundary → offline relaunch → settings/diagnostics/privacy options → large font/reduced motion → background/process death during state write. Record device, build hash, starting state, steps, expected/actual, screenshot/video, tester and timestamp.
