# Google Ads creative research

Accessed: 2026-08-26 (Asia/Kolkata)

## Scope and method

The repository and a locally built debug APK are the authority for product claims. Public material was used only to understand current Google Ads requirements and common creative presentation. No competitor artwork, wording, boards, layouts, or video footage is reused in the deliverables.

There was no `AGENTS.md` in the repository. The existing working tree was already modified before this work; those changes are being preserved. The capture entry point under `app/src/debug/` is temporary and must not remain in the final tree.

## Verified product truth

| Area | Repository-backed finding | Source of truth |
|---|---|---|
| Product | **Magnetrail**, package `com.rameshta.magnetrail` | `app/build.gradle.kts`, `app/src/main/res/values/strings.xml` |
| Objective | Clear every arrow from the board in a valid order | `GameScreen.kt`, `DefaultGameEngine.kt`, shipped campaign catalog |
| Control | Tap one remaining arrow to launch it | `GameAction.LaunchArrow`, `MagnetrailBoard` tap handling |
| Signature rule | The nearest visible aligned magnet controls an arrow. Pull draws it toward the magnet; push sends it away. A successful magnet-controlled move flips that controlling magnet's polarity, so one move changes later paths. | `game-core` engine/model and tutorial copy |
| Obstacles | Arrows, walls, and magnets affect visibility, routes, collisions, and action order | engine and level data |
| Failure behavior | A collision or invalid pull exit produces an overload animation and leaves the board state unchanged. Deadlock and restart are supported. | engine result handling and `GameViewModel` |
| Campaign | 2,205 deterministic shipped levels, from 3x3 to 8x8, with designed solutions | `design/context/Magnetrail_Campaign_Levels_v3.json` |
| Modes | Progressive Journey, campaign level selection, Daily Challenge, Infinite difficulty selections, and a debug-only human difficulty playtest | navigation, `GameViewModel`, daily/infinite/autojourney services |
| Difficulty evidence | Campaign metadata uses Intro, Developing, and Advanced. Infinite UI offers Easy, Medium, Hard, Super Hard, Expert, and Master selections. Automated labels are not treated as proof that any board is the "hardest" or human-mastered. | catalog metadata, infinite selection model, difficulty review documentation |
| Moves and progression | Action count, overload count, star grading, level unlocks, local records, completion rewards, coins, and daily/infinite streaks exist | `GameUiState`, progress repository, grading/economy policies |
| Hints | Solver-backed hints exist; a hint can use coins or an earned rewarded-ad credit | `GameViewModel`, solver, monetization code |
| Ads | Rewarded hints/skips plus constrained interstitial/app-open placements exist | monetization source and manifests |
| Not present | No lives system, countdown timer, boosters, multiplayer, account system, leaderboard, subscription, billing, or player-facing undo | repository-wide inspection |
| Offline behavior | The playable campaign is bundled, Daily has local generation/fallback/cache, and Infinite selects immutable local content. Core play has no account/backend dependency; ad loading and consent may require a connection. This is an offline-first core, not a promise that every service works offline. | asset catalog and daily/infinite implementations |
| Orientation | Production does not lock an orientation. The UI is responsive, but its visual hierarchy and supplied store material are portrait-first. Advertising source capture therefore uses portrait. | production manifest and Compose layout |
| Visual identity | Warm off-white surfaces, navy/ink UI, cyan pull fields, amber push fields, rounded Material 3 cards, fine grid lines, rail-dart arrows, and circular magnetic field effects | theme, board renderer, icon, existing store graphics |
| Audio | Short locally synthesized select, travel, exit, capture, push, polarity-flip, impact, restart, and completion cues; no licensed music asset was found | `feedback/AndroidFeedback.kt` |
| Store language | The checked-in listing source is English (US). The app resources currently include additional localized UI, but no localized store-listing copy was present in the inspected listing source. | `design/context/release/store-listing/en-US.md`, resource directories |
| Audience | General mobile logic-puzzle players who want short, readable strategic sessions; not specifically child-directed | product structure, copy, rating intent, session design |
| Determinism and testability | Campaign IDs and designed solutions are deterministic. Debug source has a capture entry point; tests and diagnostic tools cover board geometry, engine results, difficulty, and UI states. | catalog, debug source, test suites |

## Current Google Ads requirements

Official sources were checked live on 2026-08-26:

- [App campaign asset specifications](https://support.google.com/google-ads/answer/17091671?hl=en): up to five 30-character headlines and five 90-character descriptions are recommended. Accepted image shapes include landscape 1.91:1 (recommended 1200x628), square 1:1 (1200x1200), and vertical 4:5 (1200x1500), in JPG or PNG up to 5 MB. Video supports 16:9, 9:16, and 1:1 and must be hosted on YouTube for campaign use.
- [Performance Max asset specifications](https://support.google.com/google-ads/answer/17091269?hl=en): up to 15 short headlines, including at least one headline of 15 characters or fewer; up to five long headlines of 90 characters; and up to five descriptions of 90 characters. Google recommends at least four landscape, four square, and two vertical images. Custom videos in each orientation reduce reliance on automatically generated video.
- [Creative best practices for App campaigns](https://support.google.com/google-ads/answer/6167158?hl=en): text must work as a standalone, emphasize one selling point, and avoid clickbait, emojis, and poor capitalization. Imagery should use the frame, remain high-resolution, and generally avoid overlaid logos or text. Video should expose the app experience immediately, establish a hook in roughly the first two to three seconds, and include multiple early visual changes. It must remain understandable with sound off.
- [App campaign video review and gameplay context](https://support.google.com/google-ads/answer/13268252?hl=en): gameplay video means actual in-game footage rather than an unrelated promotional simulation. Video review can take time after submission.

The requested exports are stricter than Google's minimums: all final videos will be 15–25 seconds, 30 fps H.264/yuv420p with fast start, and all image sets will include four assets in every requested orientation.

## Market presentation review

### Direct mechanic or interaction references

1. [Arrow Puzzle: Tap Puzzle Games on Google Play](https://play.google.com/store/apps/details?id=com.easybrain.arrow.puzzle.game) — dense arrow grids are still legible because the board dominates the frame and the active paths have strong contrast. Difficulty is communicated through occupancy and overlap rather than explanatory copy. Magnetrail should keep its compact top status but avoid copying the blue grid treatment, arrow shapes, lives display, or hint layout.
2. [Railbound official page](https://afterburn.games/railbound/) and [developer press kit](https://afterburn.games/press/sheet.php?p=railbound) — a complete rail puzzle is usually visible at once, with generous environmental breathing room and little HUD. It proves that a transport-like logic premise can read quickly when the route is centered. Magnetrail can be visually distinct through its technical field grid, polarity colors, and mid-move magnetic flip.
3. [HOOK 2 official page](https://www.rainbowtrain.eu/hook-2) — minimal presentation makes each cause-and-effect interaction obvious without lengthy text. Magnetrail can borrow the discipline of one visible decision and one immediate reaction, but must retain its own board art, colors, magnet behavior, and wording.

### Highly installed puzzle references

4. [Candy Crush Saga on Google Play](https://play.google.com/store/apps/details?id=com.king.candycrushsaga) — the board occupies most of every store frame; goal/move information is compact; animation and disappearing pieces make success readable. Magnetrail should likewise let arrows disappearing and magnet polarity changing carry the visual story, without importing match-three effects or reward language.
5. [Royal Match on Google Play](https://play.google.com/store/apps/details?id=com.dreamgames.royalmatch) — saturated feedback and clearly staged jeopardy make a decision readable, but public discussion of puzzle advertising also highlights the trust cost of showing play unlike the installed product. The package therefore uses only replayable shipped Magnetrail states. A 2025 public creative-analysis report was reviewed as context: [SocialPeta mobile-game creative report](https://sp2cdn-idea-global.zingfront.com/report-preview/2025/Insight-into-Marketing-Insights-%26-Ad-Creatives-Analysis-of-Global-Mobile-Games-in-2025.pdf).

### Newer distinctive presentation

6. [Pup Champs on Google Play](https://play.google.com/store/apps/details?id=com.Afterburn.PupChamps) and [developer press kit](https://afterburn.games/press/sheet.php?p=pup_champs) — its store presentation combines a readable tactical setup with a specific expressive visual identity rather than a generic puzzle template. Magnetrail's corresponding opportunity is to make cyan pull, amber push, and the polarity flip unmistakably its own.

## Creative findings

### What reads in one second

- A single tapped arrow moving along a visible row or column.
- A cyan or amber magnet controlling the path, followed by a clearly visible color/polarity change.
- One arrow leaving the board and the remaining-arrow count decreasing.
- A dense board that visibly becomes cleaner over a few moves.
- An impact/rewind followed by a different successful choice.

### Showing difficulty without visual noise

- Use full-board square/vertical frames for small and medium layouts.
- Use a centered interaction crop for 8x8 landscape assets so arrows and magnet fields stay large.
- Let board density, blockers, multiple polarities, and a limited set of valid choices signal difficulty. Do not add invented timers, lives, warning meters, or red urgency overlays.
- Capture both anticipation and aftermath; a still of a completely dense board alone can look decorative rather than playable.

### Near-failure and satisfaction

- A real invalid launch is useful only in video, where impact and reset are understandable. A still of collision can imply broken controls.
- Follow the overload quickly with a valid move so the creative presents decision-making, not punishment.
- The most satisfying real outcomes are arrow exit/capture, a polarity flip that opens a future route, the last arrow clearing, and the genuine completion card/confetti.

### Framing, HUD, captions, and branding

- Keep the board centered and large. Preserve enough of the in-game top status to establish that this is an actual level, but crop out monetization surfaces and nonessential controls when the format would make the board too small.
- Use text-free final images. Google supplies separate text assets and may crop/recombine images.
- Video may use one short caption at a time only when the rule would otherwise be ambiguous. The app icon and name are reserved for a finish no longer than 1.5 seconds.
- No fake phone frame, cursor, touch ring, button, installer UI, or fabricated HUD should be added.

### First-three-second hooks

- Signature concept: open on an arrow already aligned with a pull magnet; tap immediately; show the field changing from pull to push.
- Challenge concept: open on a dense 8x8 board; show a real overload/rewind, then cut attention to the successful decision and rapid clearing.
- At least two genuine state changes occur in the first five seconds through launches, removals, field flips, or a crop change.

### Misleading patterns to avoid

- No fake rescue, pin-pull, match-three, merge, tower-defense, or runner sequence.
- No move that the engine would reject as a success and no edited board continuity.
- No unsupported lives, timer, streak urgency, multiplayer opponent, cash reward, daily reward calendar, or unlimited/offline-everything claim.
- No "best," "number one," "most addictive," "no ads," or "thousands of levels" claim. The catalog is large enough to support an exact level count, but durable copy focuses on the mechanic rather than a version-sensitive quantity.
- No competitor brand, art, board arrangement, copy, screenshot, or footage in an output asset.

## Magnetrail-specific recommendation

Lead with actual movement, not a title card. The strongest visual sequence is: readable alignment, tap, directional travel, removal/capture, controller polarity flips, and a later path becomes possible. Pair that signature concept with a second dense-board concept that proves strategic depth through one genuine overload and a satisfying resolution. Keep the cyan/amber field colors and fine technical grid visible in every crop; together they distinguish Magnetrail from generic arrow-unblock and rail-routing games.
