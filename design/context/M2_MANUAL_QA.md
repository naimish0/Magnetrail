# Magnetrail M2 manual QA

Use a debug build. Debug level selection deliberately leaves all 12 canonical
boards available while still showing their persisted progression state.

## Functional and persistence pass

- [ ] Fresh app data opens Home at Level 1 with Sound/Haptics on and assistance off.
- [ ] Complete Level 1, return Home, and confirm Level 2 is available and Level 1 is completed.
- [ ] Kill and relaunch the process; confirm settings/progress persist and the selected level restarts cleanly.
- [ ] Replay a completed level and confirm Level 12 never unlocks a Level 13.
- [ ] In Level 6, request a hint before and after the Pull-to-Push flip; confirm B then A is suggested.
- [ ] Request a hint, then move/undo/restart/change levels; confirm no stale highlight appears.
- [ ] Confirm hints do not launch a dart and only shown hints increment the completion metric.
- [ ] Verify Pull capture, Push exit, unaffected exit, collision/rewind, flip, undo, restart, win, and deadlock.
- [ ] Launch and complete gameplay in airplane mode; the app must request no network access.

## Presentation, feedback, and accessibility

- [ ] Confirm Rail Darts remain readable on 4x4 through 7x7 boards at 360/390/430 dp widths.
- [ ] Confirm Pull and Push differ by label, color, chevron direction, and ring motion.
- [ ] Toggle Sound and Haptics independently and confirm each semantic channel is suppressed.
- [ ] On a physical device, check select, capture/exit, collision, flip, and completion timing.
- [ ] Toggle Reduced motion and confirm direct travel, instant polarity swap, and no particles.
- [ ] Toggle High-contrast fields and confirm stronger fields without rule or board changes.
- [ ] Toggle Path-preview assistance, request a hint, and confirm only the first engine-derived segment appears.
- [ ] Traverse Home, Levels, Settings, controls, magnets, and darts with TalkBack.
- [ ] Confirm the suggested dart is announced without unexpected focus movement.
- [ ] Check 1.0x, 1.3x, and 1.5x font scale; primary actions must remain reachable.
- [ ] Confirm system bars and all 48 dp targets remain usable in portrait.

Record device model/API, build SHA or timestamp, and any failed item alongside
the playtest session. Automated tests intentionally suppress audio and haptics.

## Execution record — 2026-08-19

- Pixel 7a AVD, API 37: Home, all 12 debug tiles, Level 6 Pull/hint/Push,
  completion metrics, and process force-stop/relaunch restoration were visually
  inspected. Rail Dart, split-ring fields, controls, and system insets rendered
  without clipping. The 10-test connected suite passed.
- Samsung SM-S928B, API 36: Home and gameplay were visually inspected. A
  device-specific light-status-icon issue was found and fixed. The production
  sound/haptic path executed through a Level 1 completion with no AudioTrack or
  runtime errors; subjective loudness and haptic timing still require a human
  listener. The 10-test connected suite passed.
- Automated coverage exercised settings recreation, reduced-motion completion,
  1.5x font action reachability, hint semantics, progression states, and all M0
  solver/designed-solution regressions.
- Not claimed as manually complete: human TalkBack traversal, airplane-mode
  observation, subjective audio/haptic tuning, and exhaustive manual completion
  of all 12 levels. Those remain playtest-session checks above.
