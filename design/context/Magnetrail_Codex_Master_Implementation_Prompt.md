# Magnetrail — Codex Master Implementation Prompt
## 1,000+ Level Architecture + Procedural Generation + Controlled Empty-Grid Rule + Level Select + Hint/Coin UX

Date: 2026-08-20

---

# 0. Mission

Implement the next Magnetrail architectural milestone without destabilizing the existing production game.

The long-term goal is to move Magnetrail from a fixed, manually-authored 200-level campaign toward a deterministic, solver-validated, Difficulty-V4-evaluated procedural level supply system capable of supporting 1,000+ levels and eventually 5,000–10,000+ levels.

At the same time:

1. Keep the current production campaign safe and unchanged unless explicitly required.
2. Preserve Difficulty V4 and existing certification gates.
3. Preserve gameplay semantics.
4. Keep Easy → Very Hard behavior unchanged.
5. Retain the promoted Expert/Master V5.1 topology work as an architectural prototype, but do not block the project on certifying that single topology.
6. Introduce a bounded procedural-generation architecture based on causal topology first, physical board construction second, then solver/V4 evaluation.
7. Support a controlled "few empty grid cells" rule for future generated boards, but DO NOT repeat the previous failed occupancy experiment where empty space was treated as a substitute for meaningful gameplay structure.
8. Replace the 1,000+ level scrolling problem with scalable chapter/range navigation and direct level navigation.
9. Remove Undo.
10. Implement the Hint + Coins behavior exactly as specified below.
11. Make the implementation deterministic, testable, bounded, and safe to iterate.

Do not perform unrelated refactors.

---

# 1. Current Known State — Treat This as Baseline

The current Magnetrail project has:

- 200 certified campaign levels.
- 7 certified Daily fallback levels.
- Existing gameplay rules unchanged.
- Difficulty V4 unchanged.
- Existing Generator V4 unchanged.
- Existing certification gates unchanged.
- Campaign SHA-256 currently:
  8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5

Expert/Master Generator V5.1 has been developed and materially improved the topology:

Expert:
- V4 score: 61
- ordering depth: 3
- long-range relationships: 3
- safe-choice ratio: 0.5996
- relevant-object ratio: 0.2344
- average relevance: 0.0604
- wall participation: 3
- interaction density: 0.0303
- mandatory ordering: 0.2000
- V4 complete: Yes
- certified: No

Master:
- V4 score: 66
- ordering depth: 5
- long-range relationships: 1
- safe-choice ratio: 0.5154
- relevant-object ratio: 0.2500
- average relevance: 0.0615
- wall participation: 2
- interaction density: 0.0283
- mandatory ordering: 0.4444
- V4 complete: Yes
- certified: No

V5.1 therefore proved that meaningful causal ordering, polarity consequences, long-range relationships, and wall participation can be constructed.

However, the current deterministic topology still fails some unchanged Expert/Master certification gates.

DO NOT:
- lower certification thresholds;
- change Difficulty V4;
- change gameplay rules to make generated boards pass;
- change Easy/Medium/Hard/Very Hard;
- restore the failed empty-space experiment as a certification shortcut;
- add filler objects just to increase participation;
- spend the entire milestone trying to force the current V5.1 topology through certification.

The project must move forward toward a multi-topology procedural system.

---

# 2. Core Architectural Principle

The generator must stop thinking:

    "Place N objects on a board."

Instead it must think:

    "Construct a causal puzzle topology, realize that topology physically,
     verify it physically, solve it, measure it with Difficulty V4,
     then accept/reject it."

Required pipeline:

    Generation Profile
        ↓
    Topology Family Selection
        ↓
    Logical Causal Graph Construction
        ↓
    Physical Board Realization
        ↓
    Physical Semantic Verification
        ↓
    Canonical Solver
        ↓
    Difficulty V4
        ↓
    Structural Quality Gates
        ↓
    Diversity / Duplicate Filtering
        ↓
    Candidate Pool
        ↓
    Deterministic Selection
        ↓
    Level

V4 is the judge.
The topology generator creates candidates.
The solver proves solvability.
The physical verifier proves that declared relationships actually exist.
The quality system rejects boring/irrelevant boards.

---

# 3. D2 Procedural Generation Architecture

Create a clean, extensible procedural generation layer.

Do not hard-code one giant Expert/Master constructor.

Introduce conceptual components equivalent to:

    GenerationProfile
    TopologyFamily
    CausalGraph
    CausalNode
    CausalEdge
    BoardRealizer
    PhysicalSemanticVerifier
    CandidateGenerator
    CandidateEvaluator
    CandidatePool
    DiversityFilter
    DeterministicLevelFactory

Use the project's existing naming/package conventions where possible instead of blindly creating these exact names.

The architecture must allow multiple topology families.

At minimum design extension points for:

1. ORDERING_CHAIN
2. POLARITY_CHAIN
3. EXPOSURE_CHAIN
4. MAGNET_CONTROLLER_CHAIN
5. WALL_OCCLUSION
6. ARROW_MAGNET_INTERLOCK
7. BRANCHING_DEPENDENCY
8. MULTI_STAGE_DEPENDENCY

Do not necessarily implement all eight immediately if the repository architecture makes that unsafe.

At minimum implement enough topology families to prove that Expert/Master are no longer dependent on one deterministic topology.

---

# 4. Causal Graph First

Every generated non-trivial puzzle should begin with a logical dependency graph.

Example:

    A → B → C → D

or:

          A
          ↓
          B
         ↙ ↘
        C   D
             ↓
             E

or:

    A
    ↓
    Controller
    ↓
    Polarity Flip
    ↓
    B becomes mandatory
    ↓
    C
    ↓
    D

The graph must encode actual gameplay consequences, not decorative metadata.

A causal edge is valid only if removing/performing/changing the source can alter a meaningful future possibility involving the target.

Do not count:
- proximity alone;
- visual alignment without gameplay effect;
- arbitrary declared relationships;
- duplicate/symmetric relationships;
- relationships that disappear when transformed;
- relationships that never affect the actual solution space.

---

# 5. Physical Realization

After constructing the causal graph, map it onto the actual Magnetrail production board.

The generated board must physically realize the graph.

For every declared semantic relationship, verify on the actual board:

- exposure;
- controller relation;
- polarity relation;
- line-of-sight;
- wall occlusion;
- blocking;
- reveal;
- ordering consequence;
- long-range dependency;
- before/after state transition where applicable.

The physical verifier must remain mandatory.

Do not trust graph metadata without physical verification.

If a graph cannot be realized physically, reject the candidate rather than weakening the verifier.

---

# 6. Controlled Empty-Grid Rule

IMPORTANT:

Future procedural boards MAY contain a small, controlled number of empty grid cells.

However, empty cells are NOT a difficulty mechanism by themselves.

The previous experiment demonstrated:

    empty space → removed isolated walls
    but did not create meaningful gameplay structure.

Therefore:

## Empty cells must be subordinate to topology.

Use this rule:

    Empty cells may be introduced only where they improve/enable
    a real causal relationship, corridor, line-of-sight interaction,
    movement/exposure relationship, or spatial separation.

Do NOT add empty cells merely to:
- make a board look less full;
- reduce occupancy;
- pass a density threshold;
- create artificial difficulty;
- compensate for poor topology.

## Initial bounded rule

Use a configurable profile-level empty-cell budget.

Default initial target:

    0% to 10% empty cells

with the exact allowed percentage configurable per generation profile.

Do not exceed 10% in the initial D2 implementation unless an existing profile explicitly requires otherwise.

The generator must be able to produce:
- 0 empty cells;
- a few empty cells;
- the profile's maximum allowed empty cells.

The exact count must be deterministic from the generation seed.

## Critical requirement

If a candidate with empty cells does not have improved/measurable causal structure, reject the candidate.

Track:

    emptyCellCount
    emptyCellRatio
    emptyCellPurposeCount
    emptyCellPurposeTypes

A useful diagnostic should make it possible to answer:

    "Why is this empty cell here?"

Examples of valid purposes:
- creates a required corridor;
- prevents an unintended direct interaction;
- enables a specific exposure chain;
- preserves a long-range controller relationship;
- creates meaningful spatial separation between competing fields;
- allows a wall/arrow/magnet relationship to exist without an unintended shortcut.

Never use empty cells to fake object participation.

---

# 7. Occupancy vs Meaningful Interaction

Do NOT equate:

    occupancy = difficulty

and do NOT equate:

    object count = quality.

The generator should target:

    high meaningful interaction density
    + causal relevance
    + ordering
    + polarity consequences
    + physical dependencies
    + appropriate occupancy.

An occupied cell containing an irrelevant object is worse than a purposeful empty cell.

The quality system should prefer:

    meaningful sparse structure

over:

    dense but independent objects.

---

# 8. Every Object Must Have Purpose

Introduce/retain object relevance checks.

For each generated object, determine whether it contributes to at least one meaningful interaction.

Potential relevance categories:

Arrow:
- collision relevance
- blocking relevance
- line-of-sight relevance
- ordering relevance
- reveal/exposure relevance
- future-state relevance

Magnet:
- controller relevance
- polarity relevance
- cancellation relevance
- line-of-sight relevance
- exposure relevance
- ordering relevance

Wall:
- route relevance
- occlusion relevance
- field blocking relevance
- controller isolation/release relevance
- positioning relevance

Reject candidates with excessive irrelevant objects.

Do not add filler objects simply to increase:
- occupancy;
- interaction density;
- object participation;
- visual complexity.

---

# 9. Difficulty V4 Must Remain the Authority

Do not create a second competing difficulty system.

Existing Difficulty V4 remains authoritative.

The procedural generator should target a profile, but V4 determines what the candidate actually is.

Pipeline:

    generate candidate
        ↓
    solve
        ↓
    V4 analysis
        ↓
    compare against target profile
        ↓
    accept/reject

Do not modify V4 thresholds in this milestone.

Do not modify V4 internals unless an existing test proves an unrelated bug.

If a generated candidate fails V4, reject it and try another topology/parameter combination.

---

# 10. Expert/Master Strategy

Do not rely on:

    EXPERT_ORDERED_POLARITY_V1

as the only future Expert/Master construction.

Retain the V5.1 topology as a valid topology family/prototype where safe.

But create multiple alternatives.

Expert should be capable of obtaining difficulty through combinations such as:

- ordering depth;
- polarity dependencies;
- exposure chains;
- wall occlusion;
- long-range controller relationships;
- branching dependencies;
- arrow/magnet interlocks.

Master should differ primarily through deeper/more consequential causal structure.

Do NOT define Master as:

    Expert + more objects.

Prefer:

    Expert:
        meaningful causal depth ≈ 3+

    Master:
        meaningful causal depth ≈ 5+

with additional branching, polarity, or cross-dependency where the solver/V4 supports it.

The exact thresholds must come from the existing profiles, not invented replacements.

---

# 11. Candidate Generation Must Be Bounded

Do not initially generate 100,000 candidates.

Initial D2 benchmark:

    Easy       1,000 candidates
    Medium     1,000
    Hard       1,000
    Expert     1,000
    Master     1,000
    ------------------
    Total      5,000

Make candidate counts configurable.

The architecture must later support:

    10,000
    50,000
    100,000+

without redesign.

Do not store all candidates in the production APK.

This is primarily a development/offline generation benchmark.

---

# 12. Candidate Evaluation Order

Use cheap rejection before expensive V4 analysis.

Recommended order:

    1. Basic schema validity
    2. Grid validity
    3. Object placement validity
    4. Empty-cell rule
    5. Causal graph validity
    6. Physical semantic verification
    7. Basic interaction/relevance checks
    8. Canonical solver
    9. Difficulty V4
    10. Diversity/deduplication
    11. Candidate ranking

Do not run expensive counterfactual analysis on obviously invalid boards.

---

# 13. Avoid V4 Truncation

Previous Master experiments produced:

    COUNTERFACTUAL_OBJECT_SEQUENCE_ENUMERATION_CAP

This is unacceptable for a candidate intended for certification.

Do NOT simply increase the cap as a shortcut.

Instead:

- detect candidates whose topology explodes V4 counterfactual enumeration;
- reject or structurally mutate those candidates before complete V4;
- preserve V4 completeness as a certification requirement.

The generator must produce puzzles that are challenging because of meaningful structure, not because the evaluator is overwhelmed.

---

# 14. Candidate Diversity

Do not keep 500 copies of the same topology with minor rotations.

Diversity must consider:

- topology family;
- causal graph shape;
- dependency depth;
- branch count;
- polarity pattern;
- wall usage;
- spatial arrangement;
- interaction pattern;
- solution structure;
- symmetry.

At minimum, deterministic rotations/reflections of the same board should not count as sufficiently diverse candidates.

Use a structural fingerprint.

Potential fingerprint inputs:

    topologyFamily
    graph edge types
    graph depth
    graph branching signature
    object-type histogram
    interaction signature
    solution signature
    normalized spatial relationships

The fingerprint must be deterministic.

---

# 15. Deterministic Generation

Every generated level must be reproducible.

Use a deterministic seed derived from something equivalent to:

    generatorVersion
    + levelNumber
    + generationProfile
    + topologyFamily
    + variant/attempt seed

Do NOT depend on wall-clock time or nondeterministic iteration order.

If the same:
    generator version
    + profile
    + seed

is supplied, the same board must be generated.

This is required for debugging, crash reproduction, content verification, and future migration.

---

# 16. Future 1,000+ Level Supply Model

Do not require a 1,000-element campaign JSON.

Support a logical level identity:

    levelNumber
    profile
    generatorVersion
    seed
    topologyFamily
    variant

A level can therefore be reconstructed deterministically.

However, do NOT immediately remove the existing authored campaign.

Existing authored content remains the canonical safe fallback while procedural generation is validated.

Recommended future architecture:

    Canonical Authored Levels
            +
    Certified Procedural Pool
            +
    Deterministic Generator
            +
    Level Metadata/Seed

The system should be able to fall back to an authored/certified level if procedural generation is unavailable.

---

# 17. Runtime Generation Safety

Do NOT generate a level on the UI thread.

Do NOT make the player wait indefinitely after completing a level.

Future runtime flow should be:

    Player completes level
        ↓
    determine next target profile
        ↓
    check cached candidate
        ↓
    if available → present immediately
        ↓
    if unavailable → generate asynchronously
        ↓
    validate/solve/V4
        ↓
    cache
        ↓
    present

For the current milestone, it is acceptable to implement the architecture and offline candidate benchmark before enabling full runtime procedural generation.

Do not expose unstable generated levels to players merely to prove the system works.

---

# 18. Level Progression / Difficulty Rhythm

Do not force monotonic difficulty.

Use a future target-profile selector capable of sequences such as:

    Hard
    Hard
    Medium
    Expert
    Hard
    Medium
    Expert
    Easy
    Hard

The exact production progression algorithm should NOT be invented in this milestone unless an existing system already exists.

For now, make generation profiles explicit and deterministic.

Future player skill adaptation can use:

- completion success;
- retries;
- completion time;
- hint usage;
- failure patterns;
- recent V4 difficulty;
- streaks.

Do not build an overly complex player model until the candidate generator is proven.

---

# 19. Level Select UX for 1,000+ Levels

Do NOT implement a single vertically scrolling list from Level 1 to Level 1,000+.

The UI must scale to at least 10,000 logical levels.

Use:

    Level Select
        ↓
    Chapter / Range Selector
        ↓
    50-level chunk
        ↓
    Level Grid

Example:

    Chapter 01 — Levels 1–50
    Chapter 02 — Levels 51–100
    Chapter 03 — Levels 101–150
    ...
    Chapter 20 — Levels 951–1000

Do not hard-code only 20 chapters. The UI must calculate ranges dynamically.

Recommended chunk size:

    50 levels

Make it configurable if the existing design system benefits from another value.

---

# 20. Level Select Required Features

Implement:

### A. Chapter/Range selector

Show ranges such as:

    1–50
    51–100
    101–150
    ...

The user taps a range.

### B. 50-level page/grid

Only render the current range.

Do not instantiate/render 1,000+ level cells simultaneously.

### C. Current progress positioning

When opening Level Select, automatically open the range containing the player's current/next level.

Example:

    Current level = 437

Open:

    401–450

not:

    1–50.

### D. Previous/Next range navigation

Allow:

    ← 351–400
       401–450
    451–500 →

without returning to a giant list.

### E. Go To Level

Provide a direct navigation control.

Flow:

    Go to Level
        ↓
    numeric input
        ↓
    validate range
        ↓
    open corresponding 50-level chunk
        ↓
    focus requested level

If the level is locked, show its locked state rather than silently opening it.

### F. Level states

Use at minimum:

    Completed
    Current / Available
    Locked

Use existing visual language where available.

Do not invent a new visual style if the project already has a design system.

---

# 21. Level Select Must Support Procedural Levels

Do not assume every level has a stored JSON object.

A level cell should be able to represent:

    levelNumber
    unlockState
    completionState
    generated/authored status
    profile metadata if needed

The actual puzzle can be resolved only when selected.

Do not generate every level merely because the Level Select is opened.

---

# 22. Level Select Performance Requirements

The Level Select must remain fast with:

    1,000 levels
    5,000 levels
    10,000+ levels

Avoid:
- giant scroll lists;
- generating boards for every level cell;
- loading all puzzle JSON;
- running solver/V4 when rendering the selector.

The selector should be metadata-driven.

Only generate/load the actual board when the player selects a level.

---

# 23. Undo Button

Remove the Undo button completely.

Requirements:

- Remove it from the gameplay UI.
- Remove its click handling.
- Remove unused layout/Compose state associated only with Undo.
- Do not leave a dead/hidden button.
- Do not break existing gameplay state management.
- If Undo logic is used by internal debug tooling or tests, preserve only what is required by those tools, but it must not be exposed to players.

Update relevant UI tests.

---

# 24. Hint + Coins System

Implement the following exact economy.

### Rewards

Every successfully completed level:

    +10 coins

A replay/restart of an already completed level must NOT repeatedly award +10 unless the existing product requirements explicitly define replay rewards.

Default assumption:

    first completion only → +10

Persist the balance.

### Hint cost

One hint:

    -30 coins

### Hint behavior

If:

    coinBalance >= 30

then on Hint click:

    immediately deduct 30 coins
    immediately show hint
    DO NOT open a popup
    DO NOT ask for confirmation
    DO NOT offer a coin/ad choice

Example:

    60 coins
       ↓
    Hint tap
       ↓
    30 coins
       ↓
    show hint

---

# 25. Hint Behavior When Coins Are Insufficient

If:

    coinBalance < 30

then:

    do not deduct coins
    do not show a coin purchase popup
    trigger the rewarded-ad flow

The Hint button should visually replace the coin indicator with an ad/reward icon when the player does not have enough coins.

Example with enough coins:

    ┌───────────────┐
    │   💡  60      │
    │     HINT      │
    └───────────────┘

Example with insufficient coins:

    ┌───────────────┐
    │   💡  ▶       │
    │     HINT      │
    └───────────────┘

Use the project's existing icon system instead of literal emoji if one exists.

---

# 26. Rewarded Ad Rules

When coins are insufficient:

    Hint tap
        ↓
    Rewarded ad

If the rewarded ad completes successfully:

    grant the hint according to the existing hint implementation.

Do NOT automatically grant coins unless the current monetization design already specifies a rewarded-coin reward.

The explicit requirement is:

    insufficient coins → ad is shown in place of coin indicator

The implementation must gracefully handle:

    ad unavailable
    ad load failure
    user closes ad early
    reward callback not received

Never deduct 30 coins when the player has fewer than 30.

Never deduct coins before the coin-eligible path is confirmed.

Never grant a rewarded benefit twice for one ad completion.

---

# 27. Hint UI State

The top-of-Hint-button indicator must dynamically reflect affordability.

If:

    coins >= 30

show:

    coin icon + current coin balance

If:

    coins < 30

show:

    rewarded-ad icon

The Hint button itself remains the same action.

The player should NOT have to decide:

    "Use 30 coins?"
    "Watch ad?"

The app decides automatically.

---

# 28. Coin Persistence

Use the project's existing persistence layer if available.

Requirements:

- balance survives app restart;
- balance survives process death;
- level-completion reward is idempotent;
- hint deduction is atomic;
- concurrent taps cannot deduct multiple times accidentally;
- negative balance is impossible.

Create a single source of truth for the coin balance.

Do not scatter coin mutations across UI screens.

Prefer operations conceptually equivalent to:

    getBalance()
    addCoins(amount)
    trySpendCoins(amount): Boolean

`trySpendCoins(30)` should be atomic from the application's perspective.

---

# 29. Completion Reward Safety

A level completion event can be triggered more than once due to lifecycle/recomposition/replay.

Protect the +10 reward.

Use a completion identity equivalent to:

    campaign/generated level identity
    + completion state

The same completion should not credit multiple times.

Do not award coins merely because the completion screen recomposes.

---

# 30. Hint Deduction Safety

The hint cost operation must be atomic.

Bad:

    if (coins >= 30)
        UI later subtracts 30

Good:

    if (trySpendCoins(30))
        showHint()
    else
        showRewardedAd()

This prevents rapid repeated taps from spending incorrectly.

Disable/debounce Hint while a hint/ad transaction is already in progress.

---

# 31. Do Not Create a Coin Shop Yet

Do not implement:
- coin purchase store;
- IAP;
- coin bundles;
- coin popup;
- coin purchase screen.

Those are outside this milestone.

The only coin sources/actions required now are:

    Level completion → +10
    Hint → -30
    Insufficient balance → rewarded ad

---

# 32. Existing Hint Logic

Do not rewrite the underlying hint algorithm unless necessary.

Keep the existing hint generation/selection logic.

Only introduce the economy gate:

    Hint requested
        ↓
    Can spend 30?
      /     \
    yes      no
     ↓        ↓
   spend    rewarded ad
     ↓        ↓
   hint     hint/reward

If the current project has no stable hint implementation, stop and report that dependency rather than inventing a gameplay hint system unrelated to this task.

---

# 33. Data Model Compatibility

Do not make the campaign JSON responsible for:

- coin balance;
- UI state;
- player progression;
- procedural generation runtime state.

Separate:

    Level content
    Level metadata
    Player progression
    Economy state
    Generator metadata

If an existing schema already separates these, preserve it.

---

# 34. Testing Requirements

Add focused unit/integration tests.

## Generation

Test:

- deterministic seed produces identical board;
- different seeds can produce different candidates;
- profile restrictions are respected;
- empty-cell count is within profile budget;
- empty cells have measurable structural purpose;
- no filler occupancy is introduced;
- causal graph is physically realized;
- physical verifier rejects invalid mappings;
- solver proves accepted candidates solvable;
- V4 completes for accepted certification candidates;
- V4-truncated candidates are rejected;
- diversity fingerprint is deterministic;
- duplicate topology variants are filtered.

## Expert/Master

Test:

- multiple topology families can be selected;
- Expert is not hard-wired to one topology;
- Master is not simply Expert + object count;
- Expert/Master profile IDs select only intended generation paths;
- Easy/Medium/Hard/Very Hard remain unchanged.

## Level Select

Test:

- 1,000 levels do not create a giant scroll list;
- 10,000 logical levels remain navigable;
- 50-level chunk calculation is correct;
- current level opens the correct chunk;
- first/last chunk boundaries work;
- Go To Level works;
- locked levels remain locked;
- completed levels display completed state;
- previous/next chunk works;
- no puzzle generation occurs merely while rendering the selector.

## Coins

Test:

    0 coins + Hint → rewarded ad path
    1–29 coins + Hint → rewarded ad path
    30 coins + Hint → balance 0, hint shown
    60 coins + Hint → balance 30, hint shown
    29 coins + Hint → balance remains 29
    30 coins + Hint → no popup
    30 coins + rapid double tap → only one deduction
    level first completion → +10
    repeated completion event → no duplicate reward
    app restart → balance persists
    negative balance → impossible
    ad unavailable → no coin deduction

---

# 35. Regression Requirements

Run at minimum:

1. existing unit tests;
2. generation tests;
3. gameplay tests;
4. Level Select tests;
5. economy tests;
6. certification tests;
7. `certifyCampaignContent` or the repository's equivalent full campaign certification task;
8. build.

The existing 200 campaign levels and 7 Daily fallbacks must remain certified.

If campaign SHA changes unexpectedly, investigate before accepting.

Do not regenerate or rewrite the canonical campaign merely because D2 exists.

---

# 36. Performance Requirements

Do not make certification/generation unnecessarily slow.

Use:

- deterministic bounded attempts;
- cheap filters before V4;
- caching where safe;
- existing configuration cache;
- no UI-thread solver work;
- no unnecessary repeated V4 analysis.

For the first 5,000-candidate benchmark, record:

    candidates requested
    constructed
    physically valid
    solved
    V4 complete
    V4 accepted
    rejected by each major gate
    duplicate count
    diversity count
    generation time
    average evaluation time

This data is important for deciding whether to scale to 10K/50K/100K.

---

# 37. Diagnostics

Add structured diagnostics for generated candidates.

At minimum expose:

    seed
    profile
    topologyFamily
    gridWidth
    gridHeight
    emptyCellCount
    emptyCellRatio
    emptyCellPurposeCount
    objectCount
    interactionCount
    relevantObjectRatio
    averageRelevance
    orderingDepth
    mandatoryOrderingRatio
    safeChoiceRatio
    wallParticipation
    longRangeRelationships
    V4Score
    V4Complete
    solverResult
    rejectionReasons
    structuralFingerprint

Diagnostics should make failures actionable.

Example:

    REJECTED
    profile=EXPERT
    topology=POLARITY_CHAIN
    seed=12345

    emptyCells=4/64
    emptyPurpose=4

    orderingDepth=3
    longRange=3
    wallParticipation=3

    V4=61
    V4Complete=true

    rejectionReasons:
      object-participation-below-profile
      interacting-object-ratio-below-profile

Do not hide failures behind a generic "generation failed."

---

# 38. What NOT To Do

Do NOT:

- manually author another 200 levels as the primary solution;
- create one giant 1,000-level JSON solely to solve navigation;
- use random object placement as the procedural generator;
- use empty cells as a substitute for topology;
- lower certification thresholds;
- modify Difficulty V4 just to accept generated levels;
- modify gameplay rules to make generator metrics pass;
- add filler objects;
- increase V4 counterfactual caps simply to force Master candidates through;
- make Master merely "Expert with more objects";
- generate all 1,000+ boards when opening Level Select;
- generate puzzle boards on the UI thread;
- show a coin-choice popup when Hint is tapped;
- deduct coins when balance is below 30;
- expose Undo;
- add a coin shop/IAP in this milestone;
- destabilize the canonical campaign.

---

# 39. Implementation Order

Work in this order.

## Step 1 — Inspect existing architecture

Before changing code, inspect:

- current Generator V4;
- V5/V5.1 implementation;
- Difficulty V4;
- solver;
- physical semantic verifier;
- certification gates;
- campaign loader;
- level progression;
- Level Select UI;
- Hint UI and logic;
- persistence;
- rewarded-ad integration;
- current tests.

Do not assume names/paths.

## Step 2 — Lock regression baseline

Run the smallest useful existing tests and record baseline.

## Step 3 — Remove Undo

Make the UI change independently and test it.

## Step 4 — Implement Coins/Hint economy

Centralize coin state and implement exact behavior.

## Step 5 — Implement scalable Level Select

Build chapter/range + 50-level chunks + Go To Level.

Do this independently of procedural generation.

## Step 6 — Implement D2 topology interfaces

Create the extension architecture without replacing the production campaign.

## Step 7 — Implement multiple topology families

Reuse V5.1 where useful, but introduce at least one additional materially different topology family.

## Step 8 — Implement causal graph → physical board realization

Make physical verification mandatory.

## Step 9 — Implement controlled empty-cell support

0–10% initial configurable budget.

Empty cells must have measurable structural purpose.

## Step 10 — Candidate evaluator pipeline

Cheap filters → physical verification → solver → V4 → quality → diversity.

## Step 11 — Run bounded 5,000 candidate benchmark

1,000 per profile.

## Step 12 — Analyze survival rate

Do not automatically scale to 100K.

## Step 13 — Full regression/certification

Verify canonical campaign remains unchanged and certified.

---

# 40. Definition of Done

This milestone is DONE only when:

### Production safety

- [ ] Existing 200 campaign levels remain certified.
- [ ] 7 Daily fallbacks remain certified.
- [ ] Difficulty V4 unchanged.
- [ ] Existing gameplay semantics unchanged.
- [ ] Easy/Medium/Hard/Very Hard unchanged.
- [ ] Campaign hash unchanged unless a deliberate content change is explicitly approved.

### UI

- [ ] Undo removed.
- [ ] Hint shows coin balance when balance >= 30.
- [ ] Hint shows rewarded-ad icon when balance < 30.
- [ ] No hint popup when coins are available.
- [ ] Hint spends exactly 30 coins.
- [ ] Level completion grants exactly +10 coins on first completion.
- [ ] Coin balance persists.
- [ ] Level Select does not require scrolling through 1,000+ levels.
- [ ] Chapter/range navigation works.
- [ ] 50-level chunks work.
- [ ] Go To Level works.
- [ ] Current-progress auto-positioning works.
- [ ] 10,000+ logical levels are supported by the selector architecture.

### Procedural generation

- [ ] Multiple topology families supported.
- [ ] Causal graph is created before physical realization.
- [ ] Physical semantic verification is mandatory.
- [ ] Solver validation is mandatory.
- [ ] Difficulty V4 remains authoritative.
- [ ] Empty cells are controlled and purposeful.
- [ ] Empty-cell budget is configurable.
- [ ] No filler-object strategy.
- [ ] Candidate generation is deterministic.
- [ ] Candidate diversity is measured.
- [ ] V4-truncated candidates are rejected.
- [ ] 5,000-candidate benchmark is supported.
- [ ] Metrics/rejection reasons are reported.

### Testing

- [ ] Focused tests pass.
- [ ] Existing tests pass.
- [ ] Full campaign certification passes.
- [ ] Build succeeds.
- [ ] No unrelated regressions.

---

# 41. Final Strategic Rule

Do not let one difficult Expert/Master topology block Magnetrail's entire roadmap.

The current V5.1 topology has already demonstrated that the architecture can create deeper ordering, polarity, long-range, and wall relationships.

The next objective is not:

    "force V5.1 topology to pass every gate."

The next objective is:

    "build a generator capable of producing many different causal topologies,
     evaluate them with the existing solver and Difficulty V4,
     reject weak candidates,
     retain deterministic high-quality candidates,
     and eventually supply 1,000+ levels."

The controlled empty-grid rule is part of that system, but it is a spatial design tool—not a replacement for meaningful topology.

The final architecture should be:

    Causal Topology
          ↓
    Physical Realization
          ↓
    Controlled Empty Space
          ↓
    Physical Verification
          ↓
    Solver
          ↓
    Difficulty V4
          ↓
    Quality + Diversity
          ↓
    Certified Candidate Pool
          ↓
    Deterministic Level Identity
          ↓
    1,000+ Level Campaign
          ↓
    Scalable Chapter/Range Level Select

Implement carefully, keep changes bounded, run tests after each major step, and report exact files changed, tests run, generation statistics, rejection reasons, and any blockers at the end.
