# Magnetrail V11 Human Difficulty Pilot Protocol

## Purpose

V10 difficulty calibration is rejected. This blinded study evaluates new V11 puzzle cores before
any regeneration or promotion of the planned 2,000 campaign boards. Automated gates establish
solvability, restricted openings, dependencies, mechanic-family membership, and visual diversity;
only human results can approve the six difficulty labels.

Do not combine V9, V10, and V11 observations. V11 uses new profile IDs, fingerprints, content
version, study seed, and CSV schema.

## Current partial pilot

- 53 isolated V11 boards: 10 each for Easy, Medium, Hard, Super Hard, and Expert; 3 Master.
- Target remains 60 boards, with 10 per band. The present 53-board build is temporary because the
  owner requested an immediately playable pilot.
- Study seed: `11600001`; CSV schema: 3; content version: 11.
- Five topology families: blocker weave, polarity lock, cancellation release, wall occlusion, and
  mixed interlock.
- Difficulty, board ID, title, assigned band, and topology family stay hidden during play.
- The debug app schedules every available board once. The first three rounds contain all six bands;
  the remaining seven rounds contain the five complete bands.

## Running a session

1. Install a debug APK. Human Playtest and the V11 pilot asset are excluded from release assets.
2. Open **Human Playtest**, enter an anonymous code such as `PILOT-01`, and start a new session.
3. Play naturally. Avoid Hint on the first attempt; Hint and Restart remain available and recorded.
4. Use **Could not complete** instead of guessing indefinitely.
5. Rate perceived difficulty from 1 (Easy) through 6 (Master) before the next blind board.
6. Export the CSV after the session. Keep its answer-key columns hidden from the tester until play
   is complete.

Opening V11 Human Playtest clears an unfinished older-schema session. Export any older results
before installing this build when they must be retained.

## Approval gates

- Complete the 60-board set before final band approval; the current three-board Master sample is
  directional only.
- Obtain at least five independent observations per board.
- At least 70% of ratings are within one band of the assigned candidate difficulty.
- Median perceived difficulty is strictly ordered Easy < Medium < Hard < Super Hard < Expert < Master.
- Easy reaches at least 90% unassisted completion.
- Expert and Master show lower random/unassisted success plus higher time, restarts, and abandonment
  than Hard; arrow or occupancy count alone is never evidence of difficulty.
- Review any board whose median differs by more than one band or whose abandonment reaches 40%.
- Ask testers explicitly about repeated/reflected layouts, obvious opening moves, mechanic variety,
  and visual attractiveness.

These gates never mutate, relabel, regenerate, or promote production content automatically. Human
review decides whether a candidate is approved, relabeled, regenerated, or removed.

## Reproducibility

- Production campaign: V10, 2,205 boards, unchanged by this pilot.
- Pilot catalog: `content/v11_pilot/V11_PILOT_CATALOG.json`.
- Pilot audit: `content/v11_pilot/V11_PILOT_AUDIT.json`.
- Current sample: 53 total; target 60.
- Study seed: `11600001`.
- CSV schema: 3.
- Exact, D4, arrow-layout, interactive-layout, and perceptual-template uniqueness: 53/53 each.
- Production and Infinite exact/D4 collisions: zero.

If the catalog, study seed, schema, or any sampled fingerprint changes, start a new session rather
than mixing incomparable observations.
