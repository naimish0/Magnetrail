# Magnetrail V10 Human Difficulty Playtest Protocol

## Purpose

This debug-only blinded study samples the promoted V10 campaign without changing, relabeling, or
promoting any production board. It is suitable for reproducing or extending V10 human evidence;
it does not overturn the existing V10 rejection without a new, independently reviewed dataset.

Do not pool this run with the earlier V10 CSV. The earlier study used schema 2 and study seed
`10202205`; the current app exports schema 5 with study seed `11600030`.

## Study design

- Source: `docs/Magnetrail_Campaign_Levels_v3.json`.
- Source identity: content version 10, generator version 5, 2,205 boards.
- Eligible pool: Levels 206–2,205. The 205 preserved V9/tutorial boards are excluded because their
  per-board metadata is not V10.
- Sample: exactly 30 unique boards, with 5 each from Easy, Medium, Hard, Super Hard, Expert, and Master.
- Every board size available within each candidate band is represented in that band's five-board sample.
- Assignment is deterministic for the catalog and study seed; participant codes change blind order,
  not the selected board set.
- Board ID, title, assigned band, and answer-key fields stay hidden during play.

## Build and run

```sh
./gradlew :app:installDebug -PhumanPlaytestCatalog=v10
```

Open **Human Playtest**, enter an anonymous participant code, and complete the 30 blind boards.
Play naturally and avoid Hint on the first attempt. For each board, record perceived difficulty,
fairness, whether guessing was required, repeated strategy, and an optional comment. Export the CSV
before clearing or replacing the session.

## Review gates

- Obtain at least five independent observations per sampled board.
- Require at least 70% of ratings within one band of the candidate label.
- Require strictly ordered median ratings: Easy < Medium < Hard < Super Hard < Expert < Master.
- Require at least 90% unassisted completion for Easy.
- Review any board whose median differs by more than one band or whose abandonment reaches 40%.
- Treat completion, time, restarts, wrong successful actions, overloads, fairness, and guessing as
  evidence together; arrow or occupancy count alone is not difficulty evidence.

The existing 68-board V10 run failed its calibration gates. A new run remains diagnostic until its
CSV is analyzed and a human reviewer explicitly approves any conclusion.
