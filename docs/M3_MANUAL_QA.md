# Magnetrail M3 manual QA

Use a debug build. Debug level selection keeps all 100 campaign boards open
while showing the real persisted lock/completion/star state.

## Campaign, grading, and economy

- [ ] Scroll all six packs and open representative Intro, Developing, and Advanced boards.
- [ ] Complete with par/no hint, par/one hint, two-star maximum, and above maximum; verify 3/2/2/1 stars.
- [ ] Make a failed launch; verify Actions and Overloads both increment and the board does not mutate.
- [ ] Undo after a success; verify Actions does not decrease. Restart; verify attempt counters reset.
- [ ] Replay to improve stars; verify only incremental stars pay and first-clear reward never repeats.
- [ ] Confirm completion itemizes first-clear, new-star, Daily, and resulting balance values.
- [ ] Spend hints down to an insufficient balance; verify no solver failure/stale request charges coins.
- [ ] Confirm Undo, Restart, completion, and all campaign progression remain usable at zero coins.

## Daily and local clock

- [ ] Launch and complete today’s Daily in airplane mode; relaunch and verify cached identity/content.
- [ ] Replay today; verify no second Daily reward or streak increase.
- [ ] Simulate next day, a missed day, and a backward clock; verify 2 / reset-to-1 / unchanged streak behavior.
- [ ] Change time zone across a local-date boundary and verify the card clearly follows device local date.
- [ ] Force invalid Daily cache in a debug/test environment and verify regeneration, then bundled fallback.

Offline Daily cannot provide server-clock anti-cheat or a globally identical day
across time zones. Backward dates are handled conservatively and never increase
the last trustworthy streak state.

## Accessibility and performance

- [ ] Traverse Home coins/Daily, pack headings, tile lock/star states, paid Hint, and rewards with TalkBack.
- [ ] Verify 48 dp targets and reachable primary actions at 1.0x, 1.3x, and 1.5x font scales.
- [ ] Check 360/390/430 dp portrait widths, Reduced Motion, and High-Contrast Fields.
- [ ] Check cold-start catalog load and fast scrolling through Level 100.
- [ ] Run `benchmarkDailyChallenge`; record a lower-spec emulator/device result when available.
- [ ] Confirm no Internet permission, network dependency, ads, billing, analytics, or account UI.

## 2026-08-19 execution record

- Automated instrumentation passed on a Pixel 7a emulator (API 37) and a Samsung
  SM-S928B (API 36): 12/12 tests on each device, 24/24 total.
- Home, Daily gameplay, and campaign completion/reward layouts were visually
  inspected on the Pixel 7a emulator at the default font scale.
- The host benchmark is recorded in `M3_DAILY_BENCHMARK.md`; it is not presented
  as a lower-spec-device result.
- Human TalkBack traversal, 1.3x/1.5x font-scale inspection, subjective
  audio/haptic quality, and lower-spec-device timing remain manual release checks.
- The manifest/build configuration has no Internet permission, ads, billing,
  analytics, account UI, or runtime network dependency.
