# Magnetrail M1 manual QA

Use a portrait emulator or device with the debug build. Repeat the layout pass
at approximately 360 dp and 430 dp widths.

## Gameplay matrix

- [ ] Level 1: launch A and confirm an unaffected east exit, then completion.
- [ ] Level 3: launch A and confirm Pull capture at M1 and a cyan-to-amber flip.
- [ ] Level 4: launch A and confirm Push sends it west beyond the board.
- [ ] Level 2: launch A first and confirm arrow collision, impact, rewind, and no state change.
- [ ] Level 6: launch B and confirm only M1 flips; Undo restores B and Pull together.
- [ ] Level 6: after Restart, launch A first and confirm the remaining Push-side B is reported as deadlocked; Undo and Restart remain available.
- [ ] Make multiple successful moves, then Restart and confirm the authored initial state.
- [ ] Complete a level and verify Replay and Next level.
- [ ] Open every level from 1 through 12 and confirm each board accepts arrow input.

## Interaction and layout

- [ ] Double-tap during motion and confirm only one turn runs.
- [ ] Use TalkBack to discover and activate every remaining arrow by ID and direction.
- [ ] Confirm Undo is disabled before a successful move and while motion is running.
- [ ] Confirm system bars and bottom controls remain clear at 360 dp and 430 dp portrait widths.
- [ ] Confirm Pull and Push differ by label, color, and inward/outward chevrons.

Automated unit and Compose semantics tests cover the corresponding state
transactions. Device-only visual, TalkBack, and size checks must be recorded
when an emulator or physical device is available.
