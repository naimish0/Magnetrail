# Magnetrail Google Ads creative package

Prepared: 2026-08-26

## Product and positioning

- **Game:** Magnetrail
- **Production package:** `com.rameshta.magnetrail`
- **Capture package:** `com.rameshta.magnetrail.debug`
- **Positioning:** a single-player magnetic logic puzzle where the player taps arrows in a safe order. The nearest visible aligned magnet can pull or push an arrow, and a successful magnet-controlled move flips that controller's polarity, changing later routes.
- **Verified modes/features used in copy:** deterministic campaign puzzles, pull/push polarity, order-based strategy, single-tap launch, stars, completion, and progression.
- **Claims deliberately excluded:** lives, timer, boosters, multiplayer, account/leaderboard, licensed music, no ads, unlimited play, and broad offline-everything promises.

The audience is general mobile logic-puzzle players who value quick controls, readable cause and effect, and increasing strategic depth.

## Research summary

Official Google documentation was checked live on 2026-08-26. The package follows current App campaign and Performance Max text limits; 1200x628, 1200x1200, and 1200x1500 image specifications; and 16:9, 1:1, and 9:16 video coverage. Creative best practices led to immediate gameplay, a visual hook inside two seconds, early state changes, minimal added branding, text-free stills, and a truthful installed-game outcome.

Market review covered Arrow Puzzle, Railbound, HOOK 2, Candy Crush Saga, Royal Match, and Pup Champs. The distinct opportunity is Magnetrail's cyan pull/amber push identity and the visible polarity flip. No competitor media appears in an output.

Full findings and source URLs are in [RESEARCH.md](RESEARCH.md).

## Creative angles

1. Understand the clear-every-arrow objective instantly.
2. Show a magnet controlling a route and changing polarity.
3. Present a dense but readable strategic board.
4. Show a genuine overload followed by a corrected choice.
5. Preserve tap, travel, removal/capture, flip, and revealed route as one cause-and-effect sequence.
6. Finish on the genuine completion card and confetti.
7. Demonstrate that arrow order matters.
8. Show a crowded board simplifying over several successful moves.

Exact level setup and designed-solution sequences are in [CAPTURE_PLAN.md](CAPTURE_PLAN.md).

## Text assets

### Short headlines

| ID | Text | Characters |
|---|---|---:|
| SH01 | Bend the path | 13 |
| SH02 | Clear every arrow | 17 |
| SH03 | Plan each magnetic move | 23 |
| SH04 | Pull, push, then flip | 21 |
| SH05 | Master the changing field | 25 |
| SH06 | Tap the right arrow first | 25 |
| SH07 | Solve with magnetic logic | 25 |
| SH08 | Every move changes the board | 28 |
| SH09 | Can you read the field? | 23 |
| SH10 | Outsmart each magnet | 20 |
| SH11 | Find the safe launch | 20 |
| SH12 | Clear Magnetrail puzzles | 24 |
| SH13 | Choose. Launch. Adapt. | 22 |
| SH14 | One move flips the field | 24 |
| SH15 | Turn polarity into progress | 27 |

### Long headlines

| ID | Text | Characters |
|---|---|---:|
| LH01 | Clear every arrow by reading magnets that pull, push, and change polarity | 73 |
| LH02 | Plan the right launch as every successful magnetic move changes the board | 73 |
| LH03 | Magnetrail turns one simple tap into a shifting puzzle of order and polarity | 76 |
| LH04 | Start with a readable board, then solve the chain reaction one arrow at a time | 78 |
| LH05 | Find the safe order, flip magnetic fields, and finish each deterministic puzzle | 79 |

### Descriptions

| ID | Text | Characters |
|---|---|---:|
| DS01 | Tap an arrow, read the nearest magnet, and plan how its polarity will change. | 77 |
| DS02 | Clear the board in the right order as pull and push fields reshape each move. | 77 |
| DS03 | Magnetrail combines quick controls with deep, deterministic magnetic puzzles. | 77 |
| DS04 | Choose carefully: a safe launch can open the route for every arrow that follows. | 80 |
| DS05 | Complete levels, earn stars, and sharpen your plan across changing boards. | 74 |

CSV source files are under [text](text/), and the recommended five-by-five App campaign subset is in [APP_CAMPAIGN_SELECTION.md](text/APP_CAMPAIGN_SELECTION.md).

## Image inventory

All final images are PNG, under 5 MB, derived only by cropping and uniformly scaling untouched real-game screenshots.

| Orientation | File | Level/state | Angle |
|---|---|---|---|
| Landscape | `dense_challenge_level300_landscape_1200x628.png` | `campaign-300`, fresh | Dense readable challenge |
| Landscape | `meaningful_middle_level045_landscape_1200x628.png` | `campaign-045`, after two actions | One meaningful move |
| Landscape | `order_cancellation_level075_landscape_1200x628.png` | `campaign-075`, after two actions | Order/cancellation |
| Landscape | `signature_polarity_level013_landscape_1200x628.png` | `campaign-013`, fresh | Pull/push signature |
| Square | `balanced_choice_level151_square_1200x1200.png` | `campaign-151`, fresh | Balanced strategic choice |
| Square | `dense_endgame_level300_square_1200x1200.png` | `campaign-300`, after nine actions | Dense endgame decision |
| Square | `objective_complete_level003_square_1200x1200.png` | `proto-003`, complete | Objective and completion |
| Square | `signature_after_flip_level013_square_1200x1200.png` | `campaign-013`, after one action | Cause and effect |
| Vertical | `balanced_after_two_level151_vertical_1200x1500.png` | `campaign-151`, after two actions | Board simplification |
| Vertical | `balanced_board_level045_vertical_1200x1500.png` | `campaign-045`, fresh | Readable medium board |
| Vertical | `cancellation_state_level075_vertical_1200x1500.png` | `campaign-075`, after two actions | Meaningful order decision |
| Vertical | `dense_start_level300_vertical_1200x1500.png` | `campaign-300`, fresh | Dense opening |

Final image directories:

- `images/final/landscape/`
- `images/final/square/`
- `images/final/vertical/`

Fifteen untouched 1080x1920 sources are retained under `images/raw/`. The complete final contact sheet is [IMAGE_CONTACT_SHEET.png](qa/IMAGE_CONTACT_SHEET.png).

## Video inventory

Concept A uses Level 13 and communicates the signature mechanic through four real actions, controller changes, and completion. Concept B uses Level 300, starts with a genuine invalid launch/overload, follows the checked-in 16-action designed solution, and finishes with the real clear.

| Concept | Orientation | File | Technical result |
|---|---|---|---|
| A — Signature mechanic | Vertical | `concept_a_signature_mechanic_vertical_1080x1920.mp4` | 1080x1920, 16.0s |
| A — Signature mechanic | Square | `concept_a_signature_mechanic_square_1080x1080.mp4` | 1080x1080, 16.0s |
| A — Signature mechanic | Horizontal | `concept_a_signature_mechanic_horizontal_1920x1080.mp4` | 1920x1080, 16.0s |
| B — Challenge and satisfaction | Vertical | `concept_b_challenge_satisfaction_vertical_1080x1920.mp4` | 1080x1920, 20.0s |
| B — Challenge and satisfaction | Square | `concept_b_challenge_satisfaction_square_1080x1080.mp4` | 1080x1080, 20.0s |
| B — Challenge and satisfaction | Horizontal | `concept_b_challenge_satisfaction_horizontal_1920x1080.mp4` | 1920x1080, 20.0s |

Every final video is MP4 with H.264 video, AAC mono audio, 30 fps, `yuv420p`, and fast-start metadata. The final 1.2 seconds use the official app icon and product name with no call-to-action. Audio uses only exact repository-defined synthesized select, travel, and completion tones; there is no music.

Final video directories:

- `videos/final/vertical/`
- `videos/final/square/`
- `videos/final/horizontal/`

Two untouched silent Android `screenrecord` files and their deterministic action logs are retained under `videos/raw/`. Six required timeline storyboards are under `qa/video_storyboards/`.

Google Ads requires campaign video to be hosted on YouTube. These files were **not uploaded**; upload/publishing remains an owner action.

## Validation

Automated result: **PASS**.

- 15 short headlines: all 30 characters or fewer; one is 13 characters.
- Five long headlines: all 90 characters or fewer and all at least 30 characters.
- Five descriptions: all 90 characters or fewer.
- Twelve images: four per orientation, exact dimensions, supported format, each under 5 MB.
- Six videos: exact dimensions, H.264/AAC, 30 fps, `yuv420p`, 16.0s or 20.0s, fast start.
- Eighteen unique final media rows in [ASSET_MANIFEST.csv](ASSET_MANIFEST.csv).
- All 12 images, the contact sheet, and all six storyboards were opened and visually inspected after final regeneration.
- Final assets contain no system UI, personal data, ads, consent, debug/test UI, fake buttons, or fabricated game state.

Full output is in [VALIDATION_REPORT.md](VALIDATION_REPORT.md). Reproducible image, video, storyboard, contact-sheet, and validation tools are under `tools/`.

## Build and capture environment

- Normal debug workflow: `./gradlew :app:assembleDebug`
- Installed APK: `app/build/outputs/apk/debug/app-debug.apk`
- Emulator: temporary isolated Pixel 7a AVD, Android 37 Google APIs Play Store arm64-v8a
- Emulator binary: Android Emulator 36.6.11.0
- Capture display: 1080x1920 at 420 dpi, portrait, system bars hidden
- App locale: English (US)
- Touch indicators and heads-up notifications: disabled
- Screenshot: Android `screencap`
- Video source: Android `screenrecord` at 24 Mbps
- Final encoding: FFmpeg/libx264 at CRF 17, AAC 128 kbps, fast start

The temporary debug capture entry point is not part of release behavior and is removed before final repository handoff.

## Recommended launch assets

### First five for an App campaign

1. `concept_a_signature_mechanic_vertical_1080x1920.mp4`
2. `concept_b_challenge_satisfaction_vertical_1080x1920.mp4`
3. `signature_after_flip_level013_square_1200x1200.png`
4. `dense_challenge_level300_landscape_1200x628.png`
5. `objective_complete_level003_square_1200x1200.png`

Use the five headlines and five descriptions in [APP_CAMPAIGN_SELECTION.md](text/APP_CAMPAIGN_SELECTION.md) as the initial text set.

### First Performance Max asset group

Theme: **Every magnetic move changes the board**.

- Use all 15 short headlines, all five long headlines, and all five descriptions so Google can learn across objective, mechanic, strategy, and completion angles.
- Add all four landscape, all four square, and all four vertical images.
- Add Concept A and Concept B in all three video orientations to avoid relying on auto-generated orientation variants.
- Lead with LH02, SH02, SH04, SH08, DS01, and DS02 when an interface requests priority choices.

Campaign performance cannot be predicted from static QA. Evaluate combinations, low-performing assets, and audience differences through Google Ads asset reporting, then replace weak angles rather than drawing conclusions from a single creative.

## Limitations

- No campaign assets were uploaded or published.
- Android's native recorder on this emulator did not expose internal audio, so the raw recordings are silent; final audio is regenerated from the exact local tone formulas in `AndroidFeedback.kt` and contains no music.
- Automated difficulty labels are useful for selecting visual density but are not presented as human-proven superiority claims.
- Landscape stills and videos deliberately reframe the portrait board around the active puzzle area; they do not represent a separate landscape app layout.
- Ad performance, policy review outcome, and YouTube processing remain external to local validation.
