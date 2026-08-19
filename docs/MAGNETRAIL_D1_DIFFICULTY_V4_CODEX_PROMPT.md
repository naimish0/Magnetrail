# Magnetrail — D1 Difficulty V4 Diagnostic & Human Calibration
## Codex Implementation Prompt

You are working on the Android project:

    Magnetrail

Repository package:

    com.rameshta.magnetrail

Current campaign:

    200 levels
    contentVersion = 6
    generatorVersion = 4

Core rule version:

    magnetrail-core-1

---

# 0. MISSION

Implement **D1 — Difficulty V4 Diagnostic Prototype and Human Calibration Tooling**.

This is a DIAGNOSTICS-ONLY phase.

The objective is to determine whether a new Difficulty V4 model can measure
actual player-facing cognitive difficulty better than Difficulty V3.

The current campaign has already been audited.

The audit established that the current difficulty model substantially overestimates
difficulty because many apparently different solution orders are strategically
interchangeable.

The most important existing findings are:

- 20,901 / 21,196 successful choices remain solvable: 98.61%
- Meaningful failure rate: 1.39%
- 167 / 200 levels have no fatal successful choice anywhere
- 70,675 winning sequences collapse to at most 633 strategies
- Sequence permutation redundancy: 99.10%
- Viable action-pair commutation: 91.25%
- Only 43 canonical harmful decision nodes versus 655 V3 "meaningful" nodes
- Strategically impactful polarity flips: 165 / 767 = 21.51%
- Walls changing winning orders: 89 / 282 = 31.56%
- Stable-order greedy policy solves 183 / 200
- Order-tagged levels with a fatal ordering choice: 33 / 115
- Quality:
  84 ACCEPT
  116 REVIEW
  0 REJECT

The conclusion is:

> Magnetrail currently contains many legal choices, but very few consequential choices.

Difficulty V4 must therefore measure **consequence-bearing decisions**, not merely
branching, board size, solution count, or different-looking permutations.

---

# 1. ABSOLUTE SCOPE RULE

This phase MUST NOT modify gameplay behavior or campaign content.

You are implementing:

    ANALYSIS ONLY

Allowed:

- New Difficulty V4 analysis code.
- New pure diagnostic models.
- New offline analysis commands/tasks.
- New diagnostic JSON/CSV/Markdown reports.
- Human-rating input schema.
- Human calibration tooling.
- V3-vs-V4 comparison tooling.
- Tests for V4.
- Documentation for V4.
- Diagnostic-only helper utilities.

Forbidden:

- Modifying production gameplay rules.
- Modifying ResolutionResult semantics.
- Modifying BoardState semantics.
- Modifying the solver's gameplay semantics.
- Modifying generator v4.
- Modifying campaign JSON.
- Replacing campaign levels.
- Tuning campaign levels.
- Adding new levels.
- Deleting levels.
- Changing level IDs.
- Changing level metadata in the canonical campaign.
- Changing Quality gates.
- Changing grading.
- Changing economy.
- Changing Daily Challenge.
- Changing ads.
- Changing UI.
- Adding new gameplay mechanics.
- Adding Insulator.
- Adding Infinite Mode.
- Adding Adaptive Infinite.
- Adding purchases/Billing.
- Changing Difficulty V3 behavior.
- Making V4 replace V3.
- Making normal builds depend on candidate generation.
- Making normal builds rewrite content.

Do not "improve" unrelated code.

If implementation appears to require production changes, STOP and report:

    BLOCKED — production change required outside D1 scope.

Do not guess.

---

# 2. AUTHORITATIVE DOCUMENTS

Read these before modifying anything:

1. docs/MAGNETRAIL_COMPLETE_GAME_CONTEXT.md
2. docs/Magnetrail_Rules_Contract.md
3. docs/Magnetrail_Android_Technical_Brief.md
4. docs/development/DIFFICULTY_V3_SPEC.md
5. docs/development/MAGNETRAIL_DIFFICULTY_AUDIT.md
6. docs/development/MAGNETRAIL_DIFFICULTY_AUDIT.json
7. docs/development/MAGNETRAIL_LEVEL_DIAGNOSTICS.csv
8. docs/development/MASTER_DEVELOPMENT_STATUS.md

Also inspect:

- current game-core difficulty implementation;
- current solver;
- current state-key implementation;
- current level parser;
- current campaign JSON;
- current level-tools analysis tasks;
- existing difficulty tests.

Do not assume historical documentation is newer than the current source.

Use the authority order documented in:

    docs/MAGNETRAIL_COMPLETE_GAME_CONTEXT.md

---

# 3. CURRENT PRODUCT REALITY

The project owner has manually completed all 200 campaign levels.

The owner reported:

> "the difficulty level is very poor."

This is the most important human evidence currently available.

The campaign is technically certified, but its perceived difficulty is NOT accepted.

Do not treat automated certification as evidence that the levels are hard.

Do not try to defend the existing difficulty scores.

The purpose of V4 is to determine whether a better diagnostic model can explain
the owner's actual experience.

---

# 4. CORE DESIGN PRINCIPLE

Difficulty V4 must answer:

> "How much does the player need to reason about consequences before choosing an action?"

It must NOT primarily answer:

> "How many legal actions or solver paths exist?"

A level should become harder because a wrong or premature choice can meaningfully
damage the player's future options.

A level should NOT become harder merely because:

- more arrows exist;
- the board is larger;
- routes are longer;
- more winning permutations exist;
- several harmless arrows can be completed in different orders;
- polarity changes visually;
- there are more walls that do not affect strategic ordering.

---

# 5. V4 MUST BE ISOLATED

Implement V4 as a separate diagnostic model.

Prefer a structure such as:

    difficulty/v4/

or the repository's existing equivalent.

Do NOT overwrite V3.

V3 and V4 must be runnable independently.

The analyzer must support:

    --difficultyVersion=v3
    --difficultyVersion=v4

or equivalent explicit selection.

If the existing tooling architecture uses another mechanism, follow that architecture
without modifying V3 semantics.

---

# 6. REQUIRED V4 METRICS

Implement the following diagnostic families.

## 6.1 Meaningful Failure Rate

Measure the proportion of strategically plausible successful actions that can lead
to a meaningful future consequence.

Distinguish:

- immediately invalid action;
- successful but harmless action;
- successful action that changes future strategic capability;
- successful action that causes future dead-end;
- successful action that reduces the set of viable solutions;
- successful action that forces recovery/backtracking.

Do not count an action as harmful merely because it produces a different route.

---

## 6.2 Harmful Decision Density

Measure:

    harmful decision nodes / total meaningful decision nodes

A harmful node is a state where at least two plausible successful actions differ
meaningfully in their future consequences.

The implementation must document exactly what qualifies as meaningful.

Avoid using raw state-signature difference alone.

---

## 6.3 Consequence Persistence

Measure how long a decision's strategic effect persists.

For a candidate action A:

1. Resolve A.
2. Compare the resulting state with alternative plausible actions.
3. Explore future states.
4. Determine whether the strategic difference disappears immediately
   or persists for multiple decisions.

Report:

- minimum consequence depth;
- maximum consequence depth;
- average consequence depth;
- median consequence depth.

A polarity flip that merely changes the next route but leaves all future choices
equally solvable should have low consequence persistence.

---

## 6.4 Consequence Depth

Measure the number of future meaningful decisions affected by an earlier decision.

Do not simply use search depth.

Example:

    Move A
       ↓
    polarity changes
       ↓
    affects B
       ↓
    affects C
       ↓
    forces D

This represents deeper consequence structure than:

    Move A
       ↓
    different animation
       ↓
    otherwise identical future

---

## 6.5 Mandatory Ordering Constraints

Measure genuine ordering requirements.

For actions A and B:

Determine whether:

    A → B

works but:

    B → A

does not.

Report:

- mandatory ordering pairs;
- ordering ratio;
- longest mandatory ordering chain;
- dependency graph depth;
- number of independent/interchangeable actions.

Do NOT count A/B as meaningful ordering merely because both orders produce
different but equally valid solutions.

---

# 7. POLARITY-DEPENDENT ACTIONABILITY

This is a critical V4 metric.

A polarity flip matters only if it changes future strategic capability.

For every successful magnet-controlled action:

1. Record the magnet before the action.
2. Record the magnet after the action.
3. Compare the set of future successful/plausible actions.
4. Determine whether the flip:
    - changes nothing important;
    - changes route only;
    - removes/adds future choices;
    - creates/destroys a successful solution;
    - creates a mandatory ordering dependency.

Report:

    polarityFlips
    strategicallyImpactfulPolarityFlips
    polarityImpactRatio

Do not treat every polarity flip as difficulty.

---

# 8. COMMUTATION-QUOTIENTED STRATEGY ANALYSIS

This is one of the most important V4 changes.

Two actions are interchangeable when executing them in either order does not
meaningfully change strategic solvability.

For example:

    A → B → C
    B → A → C

should not be counted as two substantially different strategies if A and B commute
with no meaningful consequence.

V4 must estimate strategy diversity after collapsing interchangeable permutations.

Report:

- raw winning sequences;
- canonical strategy count;
- commutation ratio;
- permutation redundancy;
- non-commuting action pairs;
- meaningful strategy families.

The implementation must avoid factorial explosion.

Use bounded canonicalization/search where required.

Any truncation must be reported.

Never silently treat truncated analysis as complete.

---

# 9. GREEDY RESISTANCE

Implement at least one deterministic greedy policy.

Examples of policy candidates:

- stable authored-order successful action;
- lexicographically first successful action;
- shortest immediate route;
- action with lowest immediate risk.

The policy must be clearly documented.

Measure:

- whether greedy solves the level;
- actions before failure;
- first point of divergence from an optimal solution;
- recovery required;
- whether the greedy mistake is recoverable.

A level solved by a simple stable greedy policy should generally receive lower
difficulty than one requiring deliberate planning.

Do not make this the only difficulty metric.

---

# 10. RANDOM SUCCESS RESISTANCE

Implement a bounded diagnostic experiment over successful plausible actions.

This is NOT gameplay randomness.

It is offline analysis only.

For a fixed deterministic seed set, repeatedly select among currently successful
plausible actions.

Measure:

- completion rate;
- deadlock rate;
- average actions;
- average failures;
- average recovery depth;
- variance.

Use deterministic seeds.

Never use runtime randomness.

The report must include:

    seedCount
    randomPolicyCompletionRate
    randomPolicyDeadlockRate

This metric must be treated as diagnostic evidence, not absolute difficulty.

---

# 11. RECOVERY / UNDO PRESSURE

Measure whether incorrect successful choices force recovery.

A level should have higher recovery pressure when:

1. a successful but strategically inferior choice is available;
2. it later leads to failure/deadlock;
3. the player must Undo or restart to recover.

Report:

- recoverable bad decisions;
- irreversible bad decisions;
- average recovery depth;
- maximum recovery depth;
- dead-end depth;
- restart pressure.

Do not assume every failure is a design problem.

---

# 12. SAFE-CHOICE RATIO

Explicitly penalize levels where almost every successful action is safe.

Calculate:

    safeSuccessfulChoices / totalSuccessfulChoices

Also report:

    meaningfulSuccessfulChoices / totalSuccessfulChoices

A level with:

    98% safe choices

should not receive a high difficulty score simply because it has many possible
solution sequences.

---

# 13. PERMUTATION REDUNDANCY

Measure the percentage of winning sequences that differ only because interchangeable
actions are reordered.

This should become a penalty.

High permutation redundancy:

    lower difficulty

Low permutation redundancy:

    potentially higher difficulty

Do not use raw solution count as a positive difficulty signal.

---

# 14. FORCED-SEQUENCE PENALTY

A long sequence of obvious forced actions is not equivalent to difficult reasoning.

Report:

- total solution length;
- forced sequence length;
- longest forced run;
- meaningful decision count;
- decision density.

Use forced-run excess as a penalty.

Do not increase difficulty simply because a solution contains many forced moves.

---

# 15. WALL RELEVANCE

Do not count walls merely because they exist.

Determine whether a wall changes:

- controller visibility;
- polarity outcome;
- successful-action availability;
- ordering constraints;
- solution strategy.

Report:

    totalWalls
    strategicallyRelevantWalls
    irrelevantWalls
    wallStrategicRelevanceRatio

---

# 16. MAGNET RELEVANCE

Likewise, do not count magnets simply because they are present.

Measure:

- magnets that affect at least one successful solution;
- magnets that create a genuine ordering dependency;
- magnets whose polarity changes future actionability;
- magnets whose presence is strategically irrelevant.

---

# 17. DECISION CONCENTRATION

Determine where meaningful decisions occur.

Report:

- number of meaningful decision states;
- decision spacing;
- maximum gap between meaningful decisions;
- average gap;
- first meaningful decision depth;
- last meaningful decision depth.

A level with one difficult choice and 12 trivial moves should not be treated
the same as a level with meaningful decisions throughout.

---

# 18. V4 SCORE

Create a transparent V4 score.

Do NOT hide the formula.

The report must contain:

- every metric;
- normalized metric;
- positive contribution;
- negative contribution;
- final score;
- confidence;
- truncation state.

Suggested conceptual structure:

    V4 difficulty =
        consequence density
      + consequence persistence
      + ordering pressure
      + polarity actionability
      + greedy resistance
      + recovery pressure
      + decision density
      - safe-choice ratio
      - permutation redundancy
      - forced-run excess
      - irrelevant-structure penalty

The exact weights MUST NOT be invented casually.

Start with documented provisional weights.

Clearly mark them:

    CALIBRATION_VERSION = 0

The weights will later be calibrated using human ratings.

Do not claim they are validated.

---

# 19. HUMAN CALIBRATION DATASET

Create a diagnostic human-rating dataset.

Do not modify campaign JSON.

Create something like:

    docs/development/MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json

or an equivalent diagnostic-only file.

Initial reference levels:

## Strong reference group

    97
    140
    145
    100
    175
    198
    99
    199
    200
    93
    96
    95
    195
    147
    184
    185
    197
    98
    135
    173

## Weak/control group

Use these audit-identified weak/late/problematic levels:

    153
    71
    68
    40
    28
    32
    163
    152
    159
    191
    122
    31
    35
    26
    27

## Easy controls

    1
    2
    3
    4
    5

The dataset should identify the groups explicitly.

Do NOT label strong levels as objectively Hard.

Use:

    referenceStrong

rather than:

    hardTruth

The current audit itself says the existing "best" levels have not been human-validated
as truly Hard/Expert.

---

# 20. HUMAN RATING SCHEMA

Create a simple rating format.

Recommended scale:

    1 = Trivial
    2 = Very Easy
    3 = Easy
    4 = Moderate
    5 = Challenging
    6 = Hard
    7 = Very Hard
    8 = Expert

Each level should support:

    difficultyRating

and optionally:

    firstMoveObvious
    meaningfulMistakeMade
    requiredOrderingReasoning
    requiredPolarityReasoning
    interchangeableMovesObserved
    neededUndo
    neededRestart
    perceivedFairness
    comments

Boolean values should use:

    true / false / null

where null means not rated.

Do not require every optional field.

The human calibration file must support multiple raters in the future.

---

# 21. CALIBRATION ANALYSIS

Create tooling that compares:

    human rating
    V3 score
    V4 score

For each rated level.

Report:

- Pearson correlation if statistically appropriate;
- Spearman rank correlation;
- mean absolute error;
- rank disagreement;
- strongest overestimates;
- strongest underestimates;
- V3 vs V4 comparison;
- metric-level correlations;
- confidence/sample limitations.

With the initial dataset, do NOT make statistically strong claims.

Clearly state:

    SAMPLE SIZE LIMITED
    CALIBRATION PRELIMINARY

The objective is directional calibration.

---

# 22. DO NOT OVERFIT

Do not tune V4 to reproduce a handful of ratings exactly.

Do not create per-level exceptions.

Do not hardcode:

    level 153 = easy
    level 97 = hard

The model must remain level-independent.

Human labels calibrate weights/thresholds, not individual level overrides.

---

# 23. DIAGNOSTIC OUTPUTS

Produce at least:

    docs/development/MAGNETRAIL_DIFFICULTY_V4_SPEC.md

    docs/development/MAGNETRAIL_DIFFICULTY_V4_AUDIT.json

    docs/development/MAGNETRAIL_DIFFICULTY_V4_AUDIT.md

    docs/development/MAGNETRAIL_DIFFICULTY_V4_LEVEL_DIAGNOSTICS.csv

    docs/development/MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json

    docs/development/MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md

Use existing repository conventions if equivalent filenames already exist.

Do not duplicate canonical campaign content.

---

# 24. REQUIRED PER-LEVEL OUTPUT

For every campaign level, report at minimum:

    levelId
    levelNumber
    boardSize
    arrowCount
    magnetCount
    wallCount

    plausibleChoiceCount
    successfulChoiceCount
    safeSuccessfulChoiceCount
    meaningfulSuccessfulChoiceCount
    harmfulDecisionCount

    meaningfulFailureRate
    harmfulDecisionDensity

    mandatoryOrderingPairCount
    mandatoryOrderingRatio
    mandatoryOrderingChainDepth

    polarityFlipCount
    strategicallyImpactfulPolarityFlipCount
    polarityImpactRatio

    rawWinningSequenceCount
    canonicalStrategyCount
    permutationRedundancy
    commutationRatio

    greedySolved
    greedyFailureDepth

    randomPolicyCompletionRate
    randomPolicyDeadlockRate

    recoveryPressure
    maxRecoveryDepth
    deadEndDepth

    longestForcedRun
    meaningfulDecisionCount
    decisionDensity
    maxDecisionGap

    relevantWallCount
    irrelevantWallCount
    relevantMagnetCount
    irrelevantMagnetCount

    v3Score
    v4Score
    v4Confidence

    searchComplete
    searchStateCount
    searchTruncated
    truncationReason

Do not fabricate unavailable metrics.

If a metric cannot be computed, use an explicit state such as:

    null

with:

    metricStatus

or an equivalent explanation.

---

# 25. SEARCH / PERFORMANCE SAFETY

The current campaign has 200 levels.

V4 must remain bounded.

Every expensive analysis must have explicit limits:

- max states;
- max depth;
- max sequences;
- max random trials;
- max canonical strategy representatives.

All limits must be configurable.

All truncation must be visible in reports.

Never silently treat incomplete exploration as complete.

Do not make runtime gameplay slower.

V4 is offline tooling only.

---

# 26. DETERMINISM

All analysis must be deterministic.

Same:

    campaign
    analyzer version
    configuration
    seed set

must produce the same diagnostic results.

Do not use:

- current time;
- device randomness;
- UI randomness;
- network;
- live services.

If random-policy analysis is used, use fixed explicit seeds.

---

# 27. TESTING REQUIREMENTS

Add unit tests for:

1. Safe action classification.
2. Harmful action classification.
3. Consequence persistence.
4. Mandatory ordering.
5. Polarity actionability.
6. Commutation detection.
7. Canonical strategy reduction.
8. Greedy policy.
9. Random-policy deterministic behavior.
10. Recovery pressure.
11. Forced-sequence detection.
12. Wall relevance.
13. Magnet relevance.
14. V4 score calculation.
15. Truncation reporting.
16. Deterministic repeated analysis.
17. Human calibration parsing.
18. V3/V4 comparison.

Use small synthetic boards wherever possible.

Do not weaken existing tests.

---

# 28. BACKWARD COMPATIBILITY

All existing tests must remain valid.

Especially preserve:

- magnetrail-core-1 semantics;
- production engine;
- solver;
- campaign parsing;
- campaign certification;
- grading;
- economy;
- Daily Challenge;
- existing V3 analysis;
- app tests.

Do not modify their behavior to make V4 easier to implement.

---

# 29. REQUIRED COMMANDS

Inspect existing Gradle tasks first.

Then add only the necessary offline diagnostic tasks.

Preferred conceptual commands:

    ./gradlew analyzeCampaignDifficultyV4

and:

    ./gradlew analyzeCampaignDifficultyV4 \
      -PdifficultyV4Config=<optional-config>

If calibration analysis is separated:

    ./gradlew calibrateDifficultyV4

Do not make these tasks dependencies of:

    assembleDebug
    assembleRelease
    test
    normal builds

unless an existing project convention explicitly requires it.

---

# 30. V3 VS V4 REPORT

Generate a clear comparison.

The report must answer:

1. Which levels V3 considered difficult but V4 considers easy?
2. Which levels V4 considers difficult?
3. Why did their scores differ?
4. How much safe-choice ratio changed the ranking?
5. How much permutation redundancy changed the ranking?
6. How much harmful decision density changed the ranking?
7. How much ordering pressure changed the ranking?
8. How much polarity actionability changed the ranking?
9. How much greedy resistance changed the ranking?

Specifically inspect:

    97
    140
    145
    100
    175
    198
    153
    163
    152
    159
    191
    122

Do not claim these are objectively hard/easy.

Explain the structural reasons for the ranking.

---

# 31. HUMAN CALIBRATION WORKFLOW

Provide a simple workflow:

### Step A

Run:

    ./gradlew analyzeCampaignDifficultyV4

### Step B

Open:

    MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json

### Step C

Human fills ratings for the selected reference/control levels.

### Step D

Run:

    ./gradlew calibrateDifficultyV4

### Step E

Review:

    MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md

The report must clearly say whether:

    V4 appears better aligned
    V4 appears worse aligned
    evidence is inconclusive

Do not automatically change production thresholds from calibration.

---

# 32. NO CAMPAIGN PROMOTION

This phase ends before campaign redesign.

Do NOT create:

    KEEP/TUNE/REPLACE

promotion manifests yet.

Do NOT change campaign content.

The next phase will use the calibrated V4 model to generate/review candidates.

This prompt is intentionally stopping before candidate generation.

---

# 33. NO HUMAN-APPROVAL CLAIMS

Do not write:

    "human validated"

unless actual human ratings have been supplied.

Initially write:

    "Awaiting human calibration."

Automated analysis is not human validation.

---

# 34. NO DIFFICULTY CLAIMS

Avoid language such as:

    "Level 200 is Expert."

Instead use:

    "V4 diagnostic score = X."

After calibration, it may say:

    "V4 predicts higher difficulty relative to the calibrated reference set."

Still do not call it human-validated unless evidence exists.

---

# 35. DOCUMENTATION REQUIREMENTS

Write:

## MAGNETRAIL_DIFFICULTY_V4_SPEC.md

Include:

- motivation;
- V3 failure;
- V4 goals;
- definitions;
- formulas;
- search bounds;
- canonicalization;
- scoring;
- confidence;
- truncation;
- calibration methodology;
- limitations;
- examples.

Also explicitly document:

> V4 is an experimental diagnostic model and is not yet a production difficulty
> authority.

---

# 36. IMPORTANT LIMITATIONS

The current implementation cannot reliably measure:

- human-perceived obviousness;
- visual fairness;
- exact player attention;
- whether a player notices an occlusion;
- whether a route is visually confusing;
- emotional difficulty;
- subjective satisfaction.

Do not invent proxies and present them as equivalent human perception.

The human calibration step exists specifically because these cannot be derived
reliably from the solver alone.

---

# 37. FINAL ACCEPTANCE CRITERIA

D1 is complete only if:

### Architecture

- [ ] V4 is isolated from V3.
- [ ] No production gameplay semantics changed.
- [ ] No campaign content changed.
- [ ] No generator behavior changed.
- [ ] No UI changed.
- [ ] No monetization changed.

### Diagnostics

- [ ] Meaningful failure rate implemented.
- [ ] Harmful decision density implemented.
- [ ] Consequence persistence implemented.
- [ ] Consequence depth implemented.
- [ ] Mandatory ordering implemented.
- [ ] Polarity actionability implemented.
- [ ] Commutation-quotiented strategy analysis implemented.
- [ ] Greedy resistance implemented.
- [ ] Random-success resistance implemented.
- [ ] Recovery pressure implemented.
- [ ] Safe-choice ratio implemented.
- [ ] Permutation redundancy implemented.
- [ ] Forced-run penalty implemented.
- [ ] Wall relevance implemented.
- [ ] Magnet relevance implemented.
- [ ] Decision concentration implemented.

### Safety

- [ ] Explicit search bounds.
- [ ] Deterministic results.
- [ ] Truncation visible.
- [ ] No silent approximation presented as complete.
- [ ] No network dependency.

### Calibration

- [ ] Strong reference group configured.
- [ ] Weak control group configured.
- [ ] Easy control group configured.
- [ ] Human rating schema implemented.
- [ ] V3 vs V4 comparison implemented.
- [ ] Calibration analysis implemented.
- [ ] No per-level hardcoding.
- [ ] No automatic production threshold changes.

### Testing

- [ ] V4 unit tests pass.
- [ ] Existing game-core tests pass.
- [ ] Existing level-tools tests pass.
- [ ] Existing V3 tests pass.
- [ ] Campaign certification still passes.
- [ ] Deterministic repeated analysis produces identical results.

### Documentation

- [ ] V4 specification written.
- [ ] Audit report generated.
- [ ] Per-level CSV generated.
- [ ] Human calibration file generated.
- [ ] Calibration report generated.
- [ ] Limitations documented.

---

# 38. FINAL REPORT FORMAT

When finished, do NOT simply say:

    Done.

Return a structured report:

## D1 STATUS

    PASS
    PASS WITH LIMITATIONS
    BLOCKED
    FAIL

## IMPLEMENTED

List every V4 component implemented.

## FILES CHANGED

List every source/test/documentation file changed.

## FILES CREATED

List all diagnostic artifacts.

## CAMPAIGN SAFETY

Explicitly state:

    Campaign JSON changed: YES/NO
    Campaign level count changed: YES/NO
    Level IDs changed: YES/NO
    Gameplay semantics changed: YES/NO
    Generator changed: YES/NO
    Difficulty V3 changed: YES/NO

Expected:

    ALL = NO

## TEST RESULTS

Provide exact commands and results.

## V4 SUMMARY

Provide aggregate metrics for all 200 levels.

## V3 VS V4

Show the most important ranking changes.

## CALIBRATION STATUS

State:

    Human ratings supplied: YES/NO
    Number of rated levels:
    V4 calibrated: YES/NO
    Evidence strength:

## LIMITATIONS

List all incomplete/truncated/experimental areas.

## NEXT RECOMMENDED ACTION

The only valid next action after successful D1 is:

    Human calibration of the selected reference/control levels.

Do NOT recommend automatic campaign redesign yet.

---

# 39. FINAL COMMANDMENT

The purpose of this phase is NOT to make the game harder.

The purpose is to build a measurement system capable of distinguishing:

    "many possible moves"

from:

    "many meaningful decisions."

Do not optimize for impressive metrics.

Do not optimize for high difficulty scores.

Do not optimize for making existing levels appear harder.

Optimize for correlation with actual human experience.

The owner has already completed all 200 levels and found them very easy.

Treat that observation as the primary product signal.

Build the diagnostic system that can explain WHY.

Only after that model has been calibrated should Magnetrail begin campaign redesign.