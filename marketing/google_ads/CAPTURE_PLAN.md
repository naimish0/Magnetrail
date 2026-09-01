# Google Ads capture plan

## Capture principles

- Source every frame from the installed debug build and a shipped deterministic campaign level.
- Use the temporary debug-only capture activity to select a level, replay a prefix of its checked-in designed solution, and then hand all visible actions to the production engine and renderer.
- Keep production rules, visuals, timing, feedback, monetization, and release manifests unchanged.
- Hide emulator system bars, notifications, touch diagnostics, debug labels, consent, and ads. Capture at 1080x1920 or higher in portrait.
- Keep untouched `screencap` and `screenrecord` results under `images/raw/` and `videos/raw/`. Crop/reframe only in final derivatives.
- Remove the temporary debug capture source before final build validation.

Difficulty labels below are catalog metadata, not claims of human-tested difficulty. "Dense challenge" describes visual occupancy only.

## Creative angles

| ID | Angle | Proof shown on screen |
|---|---|---|
| A1 | Understand the objective instantly | One arrow leaves the board and the remaining count reaches zero |
| A2 | Showcase the signature mechanic | A controlling magnet redirects an arrow and flips between cyan pull and amber push |
| A3 | Present a difficult but readable puzzle | A centered 8x8 board shows multiple arrows, magnets, and blockers at usable scale |
| A4 | Create a near-miss or high-tension decision | A genuine invalid launch produces an impact/rewind; the next decision succeeds |
| A5 | Show satisfying cause and effect | Tap, travel path, exit/capture, polarity change, and revealed future route stay visually continuous |
| A6 | Show completion and mastery | The final valid arrow clears and the real completion card/confetti appears |
| A7 | Show order-based strategy | A small group of arrows has only one certified opening despite several visible choices |
| A8 | Show the board simplify | A dense state transitions into a cleaner state over several valid actions |

## Selected deterministic states

| Capture ID | Level/seed | Catalog difficulty | Setup | Intended action sequence | Best capture moment | Angle | Acquisition value | Cropping risks |
|---|---|---|---|---|---|---|---|---|
| S01 | Level 3 / `proto-003` | Intro; 3x3; 1 arrow, 1 magnet, 1 wall | Fresh state, zero prior actions | `a1` | Before tap and immediately after the sole arrow clears | A1 | The simplest possible before/after makes the objective readable without copy | The 3x3 board can look too sparse in landscape; use square/vertical or a tight interaction crop only |
| S02 | Level 6 / `proto-006` | Intro; 4x4; 3 arrows, 1 magnet, 2 walls; one valid first action | Fresh state; optional state after one successful action | `a3 > a2 > a1` | Anticipation before `a3`, then the field flip after the launch | A2, A7 | Several tappable pieces are visible, but a unique opening and polarity dependency demonstrate strategy quickly | Production tutorial guidance may appear on early levels; final crops must not make it look like debug text or hide the controlling magnet |
| S03 | Level 13 / `campaign-013` | Advanced; 5x5; 4 arrows, 5 magnets, 1 wall | Fresh state and solution-prefix state after `a1` | `a1 > a3 > a2 > a4` | During `a3`, with its route and a controller flip visible | A2, A5 | Cyan and amber fields fill a balanced board, giving the signature mechanic strong visual ownership | A wide crop can remove edge magnets; landscape crop must track the active row/column rather than center blindly |
| S04 | Level 45 / `campaign-045` | Advanced; 6x6; 5 arrows, 2 magnets, 1 wall | Fresh state and prefix after `a1 > a3` | `a1 > a3 > a4 > a2 > a5` | Before the meaningful middle move `a4`, and on final clear | A5, A6 | A medium board is complex enough to feel strategic while every piece stays large | Do not crop the final route or the completion card; use vertical/square for full-board completion |
| S05 | Level 75 / `campaign-075` | Advanced; 8x8; 5 arrows, 4 magnets, 2 walls; cancellation-tagged | Fresh state and prefix after `a3 > a4` | `a3 > a4 > a5 > a1 > a2` | Before `a5`, when the field relationship changes the next safe order | A2, A7 | An 8x8 board communicates more advanced planning without becoming densely packed | The sparse 8x8 geometry becomes small in full-screen vertical; use a tighter crop while retaining all controlling pieces |
| S06 | Level 151 / `campaign-151` | Developing; 5x5; 5 arrows, 3 magnets, 1 wall; cancellation-tagged | Fresh state and prefix after `a1 > a2` | `a1 > a2 > a4 > a3 > a5` | Before `a4`, with a balanced distribution of remaining pieces | A3, A5 | This is the strongest medium-density still: enough choice to invite inspection and enough whitespace to understand it | Ensure actual in-game status remains legible if retained; do not crop away both side controllers |
| S07 | Level 300 / `campaign-300` | Advanced; 8x8; 16 arrows, 11 magnets, 21 walls; dense profile | Fresh state for density; also prefix after first nine designed actions for a consequential endgame | Full solution: `must0 > reveal0 > long0 > mustA > revealA > revealB > long1 > gateA > eastGate > v10a01 > v10a02 > v10a03 > v10a04 > v10a06 > v10a05 > mustB` | Opening board, first real invalid launch/rewind, first correct launch, state after `eastGate`, and final clear | A3, A4, A8, A6 | It provides the strongest challenge hook and a visible transformation from crowded to solved | Small pieces are unreadable if the whole phone UI is retained. Landscape must tightly frame the board; captions must never cover the grid |
| S08 | Level 13 / `campaign-013`, completed | Advanced; completion state | Replay all four designed actions with animation enabled | `a1 > a3 > a2 > a4` | Real completion card/confetti after the final arrow | A6 | A short, legible sequence reaches a truthful reward moment quickly | Capture before confetti obscures the title; branded video finish must remain separate from the real result and under 1.5 seconds |

## Planned final still inventory

The twelve final images use at least seven genuinely different level/state combinations:

| Orientation | Planned situations |
|---|---|
| Landscape 1200x628 | S02 unique opening; S03 polarity state; S05 cancellation/order state; S07 dense opening |
| Square 1200x1200 | S01 objective; S03 mid-sequence; S06 balanced choice; S07 endgame decision |
| Vertical 1200x1500 | S06 balanced mid-state; S04 medium challenge; S05 meaningful move; S07 dense challenge |

Every final still is text-free apart from genuine in-app UI. No logo overlay is planned.

## Planned video concepts

### Concept A — Signature mechanic

- Source: Level 13 (`campaign-013`).
- Runtime target: 17–20 seconds.
- Hook: begin on the live board and launch `a1` within the first two seconds.
- Sequence: replay `a1 > a3 > a2 > a4` through the production engine and renderer.
- Story: action, travel/capture, controller flip, changed board, additional decisions, final clear, real completion response.
- Audio: use the app's actual locally generated cues if device recording exposes internal audio; otherwise export silent rather than introduce unverified music or fabricated effects.
- Final branding: official app icon and name only, maximum 1.5 seconds, no call-to-action button.

### Concept B — Challenge and satisfaction

- Source: Level 300 (`campaign-300`).
- Runtime target: 21–25 seconds.
- Hook: dense live 8x8 board immediately; a real invalid arrow is selected by comparing remaining arrows with the engine's valid actions.
- Sequence: impact/rewind, then the exact checked-in 16-action designed solution.
- Story: near-miss, corrected decision, multiple early state changes, rapid board simplification, last-arrow clear, real completion response.
- Continuity: the board is never reconstructed or rearranged in editing. Formatting variants may reframe the same truthful recording but cannot reorder moves.
- Final branding: official app icon and name only, maximum 1.5 seconds, no fake install control.

## Reproduction commands and metadata

The raw filenames encode level and state, and the manifest records exact prefix/action data. Still-state parameters are `level_number` plus `pre_steps`; video parameters are `level_number`, `autoplay=true`, `near_miss`, and a start delay that allows screen recording to begin after the activity is drawn. All action IDs come from the catalog's `designedSolutions[0]` array.
