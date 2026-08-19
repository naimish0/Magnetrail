# Magnetrail Difficulty and Campaign Diagnostic Audit

**Audit status:** PASS  
**Mode:** diagnosis only; no fixes or campaign promotion  
**Audit date:** 2026-08-19  
**Campaign audited:** `magnetrail-campaign-v4`, content version 6, generator version 4, rule version `magnetrail-core-1`, 200 levels

The exact per-level evidence is in [MAGNETRAIL_LEVEL_DIAGNOSTICS.csv](MAGNETRAIL_LEVEL_DIAGNOSTICS.csv). Machine-readable aggregates and rankings are in [MAGNETRAIL_DIFFICULTY_AUDIT.json](MAGNETRAIL_DIFFICULTY_AUDIT.json).

## 1. Executive Summary

The campaign is easy because most successful taps are safe and most winning sequences are reorderings of the same independent work. The automated stack proves that boards are valid, solvable, deterministic, non-guessing under its one-ply classifier, and numerically inside configured score bands. It does not prove that the player must choose correctly.

The strongest evidence is:

- On the production analyzer's canonical paths, 2,620 of 2,665 successful choices are viable: **98.31%**.
- Across every reachable solvable state, 20,901 of 21,196 successful choices are viable: **98.61%**. Only 295, or **1.39%**, create an unsolvable future.
- **167/200 levels have no fatal successful choice anywhere** in their reachable solvable state graph.
- Only **43 canonical states** contain both a winning and a fatal successful choice. Difficulty v3 reports 655 “meaningful decision points”; only 6.6% as many canonical nodes have an actual wrong-choice consequence.
- Of 21,529 viable action pairs, 19,645 (**91.25%**) commute to the identical two-action state.
- The campaign has 70,675 exact winning action sequences. Collapsing sequences connected by adjacent, state-identical commuting swaps leaves at most **633 strategy classes**, a weighted permutation redundancy of **99.10%**. This is still an upper bound on human-perceived strategies because non-commuting sequences can also feel equivalent.
- A random successful-move policy solves a level on its first attempt with mean probability **92.65%**; it is guaranteed to solve **167/200** levels. A deterministic stable-order greedy policy solves **183/200**.
- The production engine makes every successful action remove exactly one arrow. Therefore every winning sequence has length equal to the authored arrow count. “Longer solution” currently means “more objects,” not deeper reasoning, and there is no successful-but-longer recovery path.
- Only 165 of 767 canonical polarity flips (**21.51%**) change the future successful/viable action set or solvability. **101/200 levels have no strategically impactful canonical flip** even though all 200 claim `POLARITY_FLIP`.
- Only 89 of 282 walls (**31.56%**) change the exact winning-order set; 193 (**68.44%**) are decorative with respect to winning order. No wall is required for root solvability.
- Only **33/115** levels tagged `ORDER_DEPENDENCY` contain any fatal successful ordering choice. The measured distribution is 132 no meaningful ordering, 35 weak, 28 moderate, 5 strong, and 0 critical.

Human evidence is decisive here: the owner completed all 200 and found all easy. The structural measurements explain that observation. Automated certification must not be treated as human approval.

## 2. Repository/Code Audit

### Scope inspected

- Frozen rules: `docs/Magnetrail_Rules_Contract.md`
- Canonical content: `docs/Magnetrail_Campaign_Levels_v3.json`
- Board model, state identity, route tracing, controller selection, resolution, collision, deadlock, undo/restart integration
- Production `Solver`, legacy `DifficultyAnalyzer`, `PuzzleSearchAnalyzer`, Difficulty v3 scorer/gates, Quality v1/v2, certification
- Phase 0 candidate construction, Generator v4 mutation/certification, Phase 1 candidate selection and purposeful-space tuning
- Current Phase 0 and M5.3 diagnostic artifacts

### Commands run

```text
./gradlew :game-core:test :level-tools:test analyzeCampaignDifficulty \
  analyzeCampaignQuality checkCampaignSymmetryDuplicates \
  auditCampaignPacing certifyCampaignContent --continue
```

Result: **BUILD SUCCESSFUL**; 16 tasks, 6 executed and 10 up-to-date. Certification reported 200 campaign levels and 7 Daily fallback levels valid. No generation, promotion, finalization, or canonical-content task was run.

### Diagnostic method

A temporary, non-integrated Java audit under `/tmp/magnetrail-difficulty-audit` called the production engine for every action and exhaustively enumerated every reachable state. The largest authored level has seven arrows, so exact winning sequences are bounded by 7! = 5,040 per level and were measured without a sequence cap. It also performed isolated counterfactuals for polarity, magnet removal, and wall removal. The temporary code is not part of the repository.

The audit recomputed the current Difficulty v3 score for every level and asserted equality with the stored score. No production or canonical file was modified. Only this report and its diagnostic CSV/JSON were added.

## 3. Gameplay Engine Findings

The production engine matches the frozen gameplay contract in all material rule semantics inspected:

- Controller selection uses the nearest visible aligned magnet.
- Equal-nearest visible magnets cancel control.
- Arrows, walls, and magnets block magnetic line of sight.
- Pull points toward the controller; Push points away.
- Pull capture and ordinary/Push exit succeed; collision and Pull exit fail.
- A failure returns the exact prior `BoardState`, with no flip or removal.
- A success removes the selected arrow and flips only its controlling magnet.
- Deadlock means arrows remain and there is no successful action.
- UI undo stores the original state only after a success; restart restores `initialState`.

No ambiguity or engine/rules contradiction triggered a mandatory stop condition.

### Structural consequence of the engine

Each success removes exactly one arrow and arrows never move to a new persistent board position. Consequently:

```text
successful solution length = authored arrow count
```

for every winning path. A successful choice has only two strategic outcomes: the remaining state is still solvable in exactly the remaining arrow count, or it eventually dead-ends. There is no “slightly longer but recoverable” successful route. Failed taps add a move/overload in UI but preserve the board completely.

This makes solution length, action count, and many apparent recovery metrics intrinsically weak proxies for cognition in the current ruleset.

### Discrepancies found

1. `PuzzleSearchAnalysis.kt` increments `polarityFlipCount` twice for the same non-null `polarityChange`. Stored reports and direct engine measurement show 767 canonical flips; the current source would report 1,534 in a fresh raw-metric calculation. The v3 score formula does not directly use `polarityFlipCount`, so this does not explain the score mismatch, but the diagnostic field is incorrect.
2. `DIFFICULTY_V3_SPEC.md` describes mechanic relevance as a mechanic materially changing route, control, or future choices. Implementation counts any canonical action with a controlling magnet as mechanic-relevant, even if its flip changes nothing later.
3. The spec says commuting actions should collapse to the same strategy. The implementation stores families of coarse mechanic tokens and hashes child family sets; it does not perform exact state-commutation quotienting.

No discrepancy was found between the frozen rules contract and production resolution itself.

## 4. Solver Findings

### Representation and completeness

`StateKey` includes level ID, dimensions, every remaining arrow's ID/position/printed direction, every magnet's ID/position/current polarity, and every wall position, with deterministic sorting. It is sufficient for exact state memoization under current rules.

The production `Solver` enumerates `GameEngine.validActions`, which contains only immediately successful taps. It recursively explores every such action, collapses identical complete states by `StateKey`, counts solutions up to a caller cap, records the first winning solution in stable action order, and reports root successful actions whose child remains solvable.

Failed taps are intentionally absent from recursive search because they return the same state. This is correct for solvability but means the production solver cannot represent a player's failed attempts or distinguish visible-but-invalid taps from actions the player should reasonably consider.

### Limits and bias

- If the production solver reaches `maxExploredStates`, it returns `solvable=false`, zero solutions, and `searchComplete=false`; it does not return trustworthy partial viability.
- The v3 search separately allows 100,000 states, 1,000,000 resolutions, 100,000 shortest sequences, 512 mechanic-token families, and 10,000 counterfactuals.
- No campaign level hit the state or action cap. Four levels—87, 157, 169, and 183—hit the 512 family cap and have confidence 0.95; all other levels have confidence 1.0. Exact offline sequence enumeration was not capped.
- Canonical-path metrics are biased by lexicographically first viable shortest action. They can miss a harder or easier experience on another valid path.
- IDs prevent collapsing geometrically symmetric arrows, appropriately preserving authored action identity, but the solver has no concept of perceptual equivalence.

### What “viable” means

“Strategically viable” means only: the action succeeds immediately and its child state has at least one winning path. It does not mean necessary, insightful, non-obvious, strategically distinct, or non-commutative. The solver distinguishes legal/successful/solvable-child; it does not natively distinguish useful, necessary, interchangeable, recovery-producing, or psychologically plausible choices.

## 5. Difficulty v3 Audit

Let `curve(x,t) = clamp(x/t, 0, 1)^0.80`. Difficulty v3 computes eight normalized components and rounds their weighted sum:

|Component|Weight|Implemented logic|
|:-|--:|:-|
|Solution complexity|10|`curve(length,7) × (0.25 + 0.75 × decisionActivity)`|
|Meaningful decisions|25|65% `curve(decisionPoints,4)` + 35% decision density, where density target is 0.55|
|Effective branching|15|70% curved average distinct signature count above 1 + 30% curved maximum above 1|
|Dependency depth|15|70% curved dependency DAG depth + 30% longest run of “mechanic-relevant” actions; both target 4|
|Fair dead ends|10|65% curved deceptive-fair choices per solution step + 35% curved average proof depth|
|Solution constraint|10|65% non-forced portion + 35% inverse log mechanic-family count, multiplied by a decision-count factor|
|Mechanic interaction|10|60% curved mechanic relevance + 40% curved sum of occlusion/cancellation/wall/controller-change events|
|Spatial routing|5|55% curved average route length + 45% curved purposeful authored-empty-cell ratio|

### Raw-metric behavior

- **Plausible choice:** actually any immediately successful action. Immediately invalid taps are excluded from plausible choices, despite being visible to the player before tapping.
- **Viable choice:** successful child is solvable.
- **Deceptive/fair versus guess:** a fatal action is “fair” if one-ply outcome/profile or a magnetic counterfactual differs from some viable action. This is not a player-study fairness test.
- **Meaningful decision:** at least two distinct strategic signatures among viable or fair-fatal choices. A signature hashes the action's terminal/flip/route token, child minimum depth, and child mechanic-token family set. It does not require a losing alternative.
- **Effective branching:** count of those distinct signatures, not count of consequential alternatives.
- **Forced sequence:** canonical states with exactly one viable choice; maximum run and spacing are calculated only along the chosen canonical path.
- **Solution family:** a sequence of coarse terminal/flip/route tokens, capped at 512. It neither equals exact action sequences nor exact human strategies.
- **Dependency edge:** canonical action `i` changes a later canonical arrow's resolution signature (success, direction, controller, terminal family, flip-from polarity, route bucket). The change need not affect solvability.
- **Dead-end/backtracking:** exact fatal successful edges in explored solvable states and their proof depths. This is the closest current metric to meaningful consequence.
- **Mechanic relevance:** controller present, detected occlusion/cancellation, or outgoing dependency. Controller presence alone qualifies.
- **Purposeful space:** arrows plus cells/entities touched by any expanded route/control/collision diagnostic. A traversed empty cell is “purposeful” even if it adds no choice.
- **Confidence:** search reliability only, not difficulty.

### Human-decision proxy classification

|Metric|Proxy class|Reason|
|:-|:-|:-|
|Fatal-choice/backtracking pressure|Strong|Directly measures a successful choice that destroys solvability, though fairness remains heuristic.|
|Guess-dependent ratio|Strong negative gate|Guess-driven failure should reduce quality; current one-ply detection may miss perceptual ambiguity.|
|Exact harmful decision nodes|Strong|Requires viable and fatal successful alternatives in the same state. This metric is absent from v3 scoring.|
|Dependency depth/edges|Moderate at best|Potential causal depth, but many resolution changes are harmless.|
|Polarity/controller/occlusion counterfactuals|Moderate|Useful only when tied to future actionability, order, or solvability. Event presence alone is weak.|
|Forced-run length/decision spacing|Moderate penalty|Good for detecting tedium; a long forced run must not add positive difficulty.|
|Meaningful decision points|Misleading|Distinct winning continuation signatures are counted as decisions without requiring a wrong alternative.|
|Effective branching|Misleading|Rewards many safe alternatives; V3 score correlates +0.880 with permutation redundancy.|
|Solution-family count|Misleading|Mechanic-token sequences retain many permutations and can cap; family count is not strategy count.|
|Solution length|Weak/misleading|Exactly arrow count under current semantics.|
|Mechanic relevance ratio|Misleading|Any controlled action qualifies even when its flip has no future effect.|
|Route length, board occupancy, purposeful space|Weak|Useful presentation/quality diagnostics, not evidence of choosing correctly.|
|Raw board size/object count|Not useful alone|Larger boards mostly stretch the same dependency template.|
|Confidence/truncation|Not a difficulty metric|Only states whether other measurements are trustworthy.|

The requested sequence/decision separation confirms that long forced play is not the campaign's main issue: total canonical solution length is 982 (mean 4.91, maximum 7), forced-sequence length totals 245 (mean 1.225), v3 reports 655 decision nodes (mean 3.275), mean decision spacing is 0.598 actions, and maximum forced-run length is only 2 (mean 1.175). The problem is that most reported decision nodes are safe, not that the campaign is dominated by long forced runs.

The biggest flaw is not an unfortunate weight. It is the definition of a decision: v3 rewards **different-looking future traces** rather than **alternatives with materially different strategic consequences**.

## 6. Quality Gate Audit

Current Quality v2 produced 84 `ACCEPT`, 116 `REVIEW`, and 0 `REJECT`, with mean score 88.6. Certification permits `REVIEW`; it blocks only `REJECT` and hard correctness failures. Therefore “certified” never meant “human-approved.”

Quality v2 can accept an easy level because it checks solvability, complete/confident search, guesses, excessive forced runs, zero v3 decisions, empty/irrelevant space, coarse mechanic relevance, similarity, and gate fit. It does **not** gate:

- harmful/consequential choice count;
- safe-choice ratio;
- commutative-choice ratio;
- exact strategy redundancy;
- greedy or random-success solvability;
- polarity actionability impact;
- exact ordering strength;
- wall/magnet removal impact.

Its `NO_MEANINGFUL_DECISIONS` reason applies only when v3's already-overbroad decision count is zero. One harmless signature difference is enough to avoid it. Its mechanic test inherits the broad v3 relevance definition.

Legacy Quality v1 is even less aligned: its “meaningful” count adds critical order constraints, recovery windows, and `(maximumSuccessfulBranching - 1)`. A recovery window is another viable action, so safe alternatives can increase the reported quality. Tag validation also accepts `WALLS` from wall presence, `MULTIPLE_MAGNETS` from count, Pull/Push from initial polarity, and `ORDER_DEPENDENCY` from any immediately failing tap.

Answer to the gate question: **yes, every major gate can pass while a level remains psychologically trivial**, except direct solvability/correctness gates, which are necessary but do not measure difficulty.

## 7. Consequential Decision Analysis

This audit defines a consequential decision node as a solvable state with at least two successful plausible actions where the alternatives lead to materially different future classes. It separately defines a **harmful decision node**, the most important subset, as a state containing at least one viable and at least one fatal successful action.

|Scope|Plausible successful|Immediately invalid|Viable|Fatal / deceptive-fair|Guess|Viability|
|:-|--:|--:|--:|--:|--:|--:|
|Opening states|783|199|772|11|0|98.60%|
|Canonical paths|2,665|510|2,620|45|0|98.31%|
|All reachable solvable states|21,196|2,464|20,901|295|0|98.61%|

The exact audit found 614 canonical nodes where choices produce distinct future-resolution profiles, but only **43 canonical harmful nodes** in 31 levels. V3 reports 655 meaningful nodes because its signature is even broader. Thus most “decisions” change some trace/controller/family detail while leaving every successful choice winning.

Across canonical paths there are 1,931 future-profile classes, 366 immediately equivalent successful choices under that profile, and 2,291 actions participating in multi-action commutation groups. `future_impact_edges=12,640` across the state graph shows that actions frequently change a later resolution signature, but the 1.39% fatal rate shows those changes are usually harmless.

Under current semantics:

- **Future dead end:** 295 successful edges.
- **Future recovery or extra steps:** zero successful edges. A viable edge still completes in the fixed remaining arrow count; a fatal edge requires undo/restart.
- **Immediate invalid:** 2,464 explored visible taps; board state is unchanged.
- **State-changing but harmless:** the overwhelming majority of the 20,901 viable edges.

## 8. Interchangeability Analysis

Exact winning sequences were quotient-collapsed by connecting sequences whenever an adjacent pair can be swapped and both orders reach the identical production `StateKey` after the pair. This is stricter and more defensible than comparing mechanic-token strings.

|Measure|Result|
|:-|--:|
|Exact winning sequences|70,675|
|V3 mechanic-token families|15,100|
|Commutation-quotiented strategy classes|633|
|Sequence-weighted permutation redundancy|99.10%|
|Mean per-level permutation redundancy|83.43%|
|Commutative viable pairs / tested viable pairs|19,645 / 21,529 (91.25%)|
|Levels with one strategy class|74|
|Levels with at most two strategy classes|118|
|Levels with redundancy ≥90%|125|
|Levels with redundancy ≥99%|72|

The 633 classes are an **upper bound** on human-perceived strategies: two sequences can be cognitively identical even when their adjacent actions do not commute to the exact same intermediate state. Therefore at least 99.10% of the aggregate sequence count is ordering permutation, not strategic diversity.

The worst fake-difficulty examples are listed in section 22. Levels 87, 88, 89, 91, 92, and 190 each expose hundreds or thousands of sequences but only 2–6 commutation classes and no fatal successful choice.

## 9. Polarity Impact Analysis

For each canonical controlled success, the audit reconstructed the same post-removal board with the controller left unflipped, then compared routes, controllers, successful/viable action sets, solution counts, and solvability.

|Polarity measure|Result|
|:-|--:|
|Real canonical flips|767|
|Route or solution-structure changed|463 (60.37%)|
|Successful/viable set or solvability changed|165 (21.51%)|
|Solution-count/solvability structure changed|146 (19.04%)|
|Magnets with any strategic flip impact|105 / 310 (33.87%)|
|Levels with at least one strategic canonical flip|99 / 200 (49.5%)|
|Levels with none|101 / 200 (50.5%)|

Polarity is visually active but strategically weak. Many flips alter a route signature or count of harmless permutations without changing which future choice is winning. Because all 200 levels claim `POLARITY_FLIP`, the 49.5% level interaction rate is a major mechanic-presence versus mechanic-consequence gap.

## 10. Magnet Relevance Analysis

Each magnet was removed from the authored level and the complete winning-order set was recomputed.

|Magnet class|Count|Share|
|:-|--:|--:|
|Total|310|100%|
|Changes exact winning-order structure|246|79.35%|
|Does not change winning-order structure|64|20.65%|
|Required for root solvability|94|30.32%|
|Has any strategically impactful canonical flip|105|33.87%|

The high 17,075 controlled-success interactions across repeated reachable states explain why v3 sees strong mechanic activity. The counterfactuals show that interaction frequency is not the same as dependence: only about one third of magnets make their signature flip matter on the canonical path, and fewer than one third are required for solvability.

“Decorative” here has a precise limited meaning: removing the magnet leaves the exact winning arrow-order set unchanged. It can still change animation, route shape, or failed taps.

## 11. Wall/Occlusion Analysis

|Wall measure|Result|
|:-|--:|
|Levels tagged `WALLS`|147|
|Tagged levels where at least one wall changes winning orders|68 (46.26%)|
|Total walls|282|
|Walls changing exact winning orders|89 (31.56%)|
|Walls not changing exact winning orders|193 (68.44%)|
|Movement-collision relevant walls|49 (17.38%)|
|Magnetic-visibility relevant walls|73 (25.89%)|
|Union route/control relevant|110 (39.01%)|
|Walls required for root solvability|0|

For occlusion, 164 levels claim the tag and 155 have at least one reachable wall/arrow blocker whose removal changes controller selection: 94.5% physical relevance. Nine tagged levels have no such reachable controller counterfactual. There are 2,322 arrow-occlusion events across repeated states.

This high physical-occlusion rate does **not** show high strategic consequence. A blocker can change the controller while both resulting orders remain winning. Exact per-arrow strategic occlusion removal is **NOT MEASURABLE WITH CURRENT IMPLEMENTATION** without defining a valid counterfactual that removes an arrow as an occluder without also changing the goal. Wall strategic relevance is measurable and low, as shown above.

## 12. Cancellation Analysis

Cancellation is tagged in 13/200 levels (6.5%) and is reachable in all 13. The exhaustive graph contains 322 cancellation-involved action instances, all physically counterfactual: removing either tied magnet changes the resolution. Sixteen lie on canonical winning paths.

None of the cancellation-involved successful actions leads to a fatal child. Cancellation currently functions as a route/availability device, not a deep trap or sustained ordering system. It is both underused across the curriculum and, where present, non-punitive. Because a cancellation can leave an arrow uncontrolled and still let it exit, raw cancellation-event count must not be interpreted as difficulty.

A cancelled action has no controlling magnet, so it cannot itself flip polarity. Its only strategic role is the uncontrolled route and any ordering needed to create or remove the equal-nearest tie.

## 13. Order Dependency Analysis

Exact winning sequences were tested pairwise for whether arrow A can precede B, B can precede A, or only one order appears.

|Measure|Result|
|:-|--:|
|Mandatory arrow-order pairs|90 (4.10%)|
|Flexible pairs|2,103 (95.90%)|
|Tagged `ORDER_DEPENDENCY` levels|115|
|Tagged levels with a fatal successful order choice|33 (28.70%)|
|No meaningful ordering|132 levels|
|Weak ordering|35|
|Moderate ordering|28|
|Strong ordering|5|
|Critical ordering|0|

The tag is inflated because current metadata logic can infer order dependency from an immediately invalid opening tap. A visible arrow that cannot move yet may teach an order, but it does not create a state-changing wrong decision: the player can simply tap another arrow.

Reversing a viable pair usually works and reaches the same state: 91.25% commute. When a successful wrong order is truly harmful, it cannot create a longer recovery path; it eventually forces undo/restart.

## 14. Dead-End/Failure Analysis

The meaningful failure rate is:

```text
successful edges from solvable states whose child is unsolvable
---------------------------------------------------------------- = 295 / 21,196 = 1.3918%
all successful edges from solvable states
```

There are 167 levels with no such edge anywhere and 169 with no harmful decision on the canonical path. Canonical fatal choices are 45/2,665 (1.69%); opening fatal choices are 11/783 (1.40%).

Classification under current engine semantics:

- Immediate failure: board unchanged; informative at only a move/overload cost.
- Successful and viable: harmless in action length; always finishes in the fixed remaining count.
- Successful and fatal: eventual dead end, then undo/restart.
- Extra-step-only and successful recovery states: impossible under current state transition model.

This bimodality helps explain the feel: almost everything works cleanly, and the rare wrong success is a full dead end rather than a layered recoverable consequence.

## 15. Human-Like Simulation

These are deterministic engine simulations, not human playtests. “Obvious” is approximated from immediate visible outcome; actual visual salience and player reasoning are **NOT MEASURABLE WITH CURRENT IMPLEMENTATION**.

|Policy|Definition|Mean first-attempt solve|Guaranteed levels|Mean actions/attempt|Mean wrong actions/attempt|Mean harmful decisions encountered|
|:-|:-|--:|--:|--:|--:|--:|
|A: first obvious|Prefer uncontrolled exit/no flip, then shortest route; random tie|91.83%|169|4.747|0.082|0.277|
|B: greedy|First immediately successful stable-ID action; no solver look-ahead|91.50%|183|4.740|0.085|0.200|
|C: random successful|Uniform among immediate successes|92.65%|167|4.763|0.074|0.225|
|D: random plausible tap|Random remaining taps without replacement until a success|92.65%|167|5.696|1.006|0.225|
|E: shortest apparent progress|Uniform among shortest immediate routes|91.76%|167|4.745|0.082|0.301|

Every successful action reduces arrow count, so a conventional greedy “maximize immediate progress” heuristic cannot discriminate among successful choices. Policy B uses stable ID only to make that tie deterministic. Its 183/200 result means a simple arbitrary order completes 91.5% of the campaign.

For random policies, mean expected restarts under independent replay are about 0.154. Deterministic policies that choose a fatal branch can repeat it forever, so a finite campaign-wide expected restart count is not meaningful.

Mean harmful-setback/polarity-mistake proxies per first attempt are 0.082 (A), 0.085 (B), 0.074 (C/D), and 0.082 (E). For A and E, four levels have zero probability under the heuristic and therefore infinite repeated restarts; among nonzero cases, mean geometric restarts are about 0.145. Policy B deterministically fails 17 levels, so restart repetition does not repair it.

## 16. Triviality Analysis

The diagnostic classification is based on exact harmful branching, random-success probability, future-profile distinction, polarity impact, and redundancy—not the v3 score.

|Structural class|Levels|
|:-|--:|
|Trivial|10|
|Very Easy|23|
|Easy|36|
|Moderate|113|
|Hard|16|
|Very Hard|2|
|Expert|0|

These are relative structural tiers, not validated player ratings. Owner play evidence says even the relative “Hard” set feels easy in the shipped context. The important conclusion is that the campaign contains **no structurally Expert level**, and only levels 140 and 145 reach the relative Very Hard tier.

The first ten are single-action tutorials. Beyond tutorials, the main triviality signatures are: every successful choice remains solvable, a single commutation class despite many sequences, zero strategic polarity impact, and no harmful canonical decision.

## 17. Campaign Progression Analysis

|Range|V3 avg|Random-success solve|Greedy solved|Failure rate|Harmful nodes/level|Permutation redundancy|Polarity impact|Structural conclusion|
|:-|--:|--:|--:|--:|--:|--:|--:|:-|
|001-012|12.5|100.0%|12/12|0.00%|0.00|8.3%|5.6%|Tutorial/intro; no successful wrong choice|
|013-030|26.2|100.0%|18/18|0.00%|0.00|58.3%|11.1%|No successful wrong choice|
|031-050|49.5|100.0%|20/20|0.00%|0.00|75.6%|11.8%|No successful wrong choice|
|051-070|64.5|100.0%|20/20|0.00%|0.00|84.4%|13.6%|No successful wrong choice|
|071-085|74.9|97.5%|15/15|0.42%|0.07|96.3%|14.4%|Near-flat consequence|
|086-100|84.5|75.6%|11/15|4.63%|0.93|99.1%|18.5%|First material consequence spike|
|101-110|73.8|96.7%|10/10|1.11%|0.10|91.3%|39.0%|Difficulty drops|
|111-125|74.9|96.1%|13/15|1.24%|0.13|91.0%|14.7%|Near-flat consequence|
|126-140|77.3|85.3%|12/15|3.54%|0.33|92.7%|33.2%|Weak consequence rise|
|141-150|77.2|81.7%|8/10|4.21%|0.40|90.4%|32.5%|Relative local peak|
|151-160|75.8|100.0%|10/10|0.00%|0.00|98.1%|12.5%|Late-game regression: completely safe|
|161-175|80.4|86.7%|13/15|2.47%|0.33|93.7%|18.1%|Weak consequence|
|176-190|79.9|93.3%|13/15|0.93%|0.13|97.5%|23.6%|Near-flat consequence|
|191-200|84.4|72.9%|8/10|4.29%|0.90|98.3%|29.5%|Some consequence; still broad safe ordering|

V3 rises strongly with campaign number (Pearson `r=+0.801`), but meaningful failure (`r=+0.261`) and harmful canonical nodes (`r=+0.253`) rise only weakly. V3 correlates strongly with permutation redundancy (`r=+0.880`): the analyzer often scores the property that makes levels easy.

Difficulty does not increase monotonically. It peaks structurally around 86–100, drops at 101, improves unevenly near 140–150, fully resets at 151–160, and only partially recovers in the finale. The final ten are relatively better but still have 98.3% permutation redundancy and only a 4.29% fatal-edge rate.

## 18. Board Size Analysis

|Board|Levels|Mean V3|Random-success solve|Greedy solved|Failure rate|Harmful nodes/level|Permutation redundancy|Polarity impact|
|:-|--:|--:|--:|--:|--:|--:|--:|--:|
|3×3|38|50.7|97.4%|36/38|0.81%|0.05|63.1%|14.9%|
|4×4|112|63.9|92.9%|103/112|1.52%|0.22|84.4%|19.1%|
|6×6|34|79.4|89.1%|30/34|1.98%|0.32|95.6%|21.7%|
|7×7|16|81.6|87.5%|14/16|1.66%|0.31|98.8%|19.3%|

The dominance of 3×3/4×4 boards contributes to short visual scan time, but larger boards do not fix the central problem. From 4×4 to 7×7, mean V3 rises by 17.7 points while failure rate barely changes and redundancy worsens to 98.8%. Empty distance and longer routes make boards look larger without adding dependencies.

## 19. Mechanic Interaction Analysis

Mechanic presence was separated from measured interaction:

|Claimed mechanic|Claimed levels|Measured interaction levels|Rate|
|:-|--:|--:|--:|
|Pull|192|192|100%|
|Push|195|194|99.5%|
|Polarity flip|200|99|49.5%|
|Walls|147|68|46.3%|
|Occlusion|164|155 physical controller effects|94.5%|
|Multiple magnets|110|104 physical multi-controller/cancellation effects|94.5%|
|Cancellation|13|13 physical effects|100%|
|Order dependency|115|33 fatal-order effects|28.7%|

The criteria differ intentionally by mechanic: polarity requires a changed future action/viability set, walls require changed winning orders, and order requires an actual fatal successful choice. Physical occlusion/multiple-magnet rates remain upper bounds on strategic value.

The common pattern is simultaneous **presence without interaction**: a magnet controls and flips, a wall blocks a ray, or a controller changes, but the player may still remove arrows in almost any order. Metadata therefore overstates the decision load.

## 20. Generator v4 Bias Analysis

The generator structurally favors easy puzzles for five code-level reasons:

1. **Independent exit arrows are added deliberately.** `Phase0CandidateFactory.addIndependentExitArrow` places a new boundary arrow outside every magnet row/column, points it outward, and appends it to a known solution. This raises arrow count, solution length, branching, and sequence permutations while adding a deliberately safe independent move.
2. **Known-solvable templates dominate.** Candidate construction transforms, compacts, and reuses ranked templates. Correctness is preserved, but existing weak dependencies are inherited.
3. **Selection targets the v3 number.** Phase profiles require v3 score, v3 decision count, dependency depth, branching, non-forced portion, and broad mechanic relevance—the same metrics shown here to reward safe variety.
4. **Phase 1 stretches rather than deepens.** Generator v4 maps Phase 0 templates by symmetry and spreads their coordinates to 6×6/7×7 while retaining arrow/magnet identities and the designed solution. It introduces no new dependency chain by itself.
5. **Purposeful-space tuning optimizes geometry.** Phase 1 initially adds zero extra walls, then greedily adds walls that reduce unused-space ratio while preserving replay, v3 gates, no guesses, and non-reject Quality. This can improve spatial/quality scores without creating a harmful decision.

The output evidence matches these biases: larger boards receive higher V3 while commutation redundancy grows; 151–160 inherit late-game scores but have zero fatal edges; and many levels contain one independent strategy expressed as hundreds of orders.

## 21. Top 50 Easiest Levels

Ranking prioritizes single-action structure, guaranteed random-success completion, absence of harmful choices, low future distinction, high permutation redundancy, and low polarity consequence. The first 12 are intentionally introductory, so their presence is not itself a defect; they are included because the requested ranking covers the complete campaign.

`Seq/Fam` is exact winning sequences/current v3 families. `P/V` is canonical plausible successful/viable choices. `Distinct/Harmful` is future-profile classes/canonical states containing a viable and fatal successful alternative. `Mechanics` is measured interactions/claimed presence.

|#|Level|Title|Board|V3/Q|Len|Seq/Fam|P/V|Distinct/Harmful|Fail|Order|Polarity|Mechanics|Class|Primary reason|
|-:|:-|:-|:-:|:-:|-:|:-:|:-:|:-:|:-:|:-|:-:|:-:|:-|:-|
|1|proto-001 (1)|First release|3x3|10/68|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|2|proto-002 (2)|Clear the blocker|4x4|11/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|3|proto-003 (3)|Pull|3x3|11/68|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|4|proto-004 (4)|Push|4x4|12/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|5|proto-005 (5)|Automatic flip|3x3|10/68|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|6|proto-006 (6)|Order matters|4x4|11/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|7|proto-007 (7)|Field occlusion|4x4|12/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|8|proto-008 (8)|Shielded field|3x3|11/68|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|9|proto-009 (9)|Alternating gates|4x4|11/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|10|proto-010 (10)|Reveal the relay|4x4|11/66|1|1/1|1/1|0/0|0.0%|None|0%|1/2|Trivial|Single-action puzzle|
|11|campaign-153 (153)|Advanced Recall 3|6x6|76/89|5|120/40|15/15|3/0|0.0%|None|0%|5/6|Very Easy|All successes remain solvable; orders are interchangeable|
|12|campaign-071 (71)|Rail study 071|4x4|71/94|4|24/20|10/10|3/0|0.0%|None|0%|4/6|Very Easy|All successes remain solvable; orders are interchangeable|
|13|campaign-068 (68)|Cadence study 068|4x4|71/96|4|12/12|9/9|2/0|0.0%|Weak|0%|4/8|Very Easy|All successes remain solvable; orders are interchangeable|
|14|campaign-040 (40)|Field study 040|4x4|45/81|3|6/2|6/6|3/0|0.0%|None|0%|3/4|Very Easy|All successes remain solvable; orders are interchangeable|
|15|campaign-028 (28)|Left rail duet|4x4|44/79|3|3/3|4/4|2/0|0.0%|Weak|0%|4/7|Very Easy|No successful choice creates an unsolvable future|
|16|campaign-032 (32)|Field study 032|3x3|45/79|3|5/5|5/5|3/0|0.0%|None|50%|4/4|Very Easy|No successful choice creates an unsolvable future|
|17|proto-011 (11)|Reverse gate|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|18|proto-012 (12)|Prototype capstone|4x4|21/75|3|4/1|5/5|3/0|0.0%|None|67%|4/4|Very Easy|No successful choice creates an unsolvable future|
|19|campaign-013 (13)|Quiet attraction|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|20|campaign-014 (14)|Amber release|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|21|campaign-015 (15)|Offset field|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|22|campaign-016 (16)|Side gate|4x4|21/75|3|4/1|5/5|3/0|0.0%|None|67%|4/4|Very Easy|No successful choice creates an unsolvable future|
|23|campaign-017 (17)|Hidden alignment|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|24|campaign-018 (18)|Long shield|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|25|campaign-019 (19)|Three-beat gate|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|26|campaign-020 (20)|Raised relay|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|27|campaign-021 (21)|Push opening|4x4|21/75|3|4/1|5/5|3/0|0.0%|None|67%|4/4|Very Easy|No successful choice creates an unsolvable future|
|28|campaign-022 (22)|Corner rhythm|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|29|campaign-023 (23)|Wide alternation|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|30|campaign-024 (24)|Deep gate|4x4|19/78|2|2/1|3/3|2/0|0.0%|None|0%|2/3|Very Easy|No successful choice creates an unsolvable future|
|31|campaign-025 (25)|Layered reveal|4x4|21/75|3|4/1|5/5|3/0|0.0%|None|67%|4/4|Very Easy|No successful choice creates an unsolvable future|
|32|campaign-033 (33)|Relay study 033|3x3|43/85|3|4/3|5/5|2/0|0.0%|None|0%|3/5|Very Easy|No successful choice creates an unsolvable future|
|33|campaign-037 (37)|Circuit study 037|3x3|45/86|3|4/3|5/5|2/0|0.0%|None|0%|3/6|Very Easy|No successful choice creates an unsolvable future|
|34|campaign-163 (163)|Dependency Lattice 3|6x6|79/91|6|360/49|20/20|7/0|0.0%|Weak|0%|5/7|Easy|All successes remain solvable; orders are interchangeable|
|35|campaign-152 (152)|Advanced Recall 2|7x7|74/89|5|120/100|15/15|6/0|0.0%|None|0%|5/6|Easy|All successes remain solvable; orders are interchangeable|
|36|campaign-159 (159)|Advanced Recall 9|6x6|77/90|5|120/105|15/15|8/0|0.0%|None|0%|4/6|Easy|All successes remain solvable; orders are interchangeable|
|37|campaign-191 (191)|Expert Circuit 1|7x7|79/87|6|180/138|18/18|4/0|0.0%|Weak|25%|6/7|Easy|All successes remain solvable; orders are interchangeable|
|38|campaign-122 (122)|Vector 122|4x4|77/94|5|60/38|14/14|7/0|0.0%|None|0%|4/7|Easy|All successes remain solvable; orders are interchangeable|
|39|campaign-031 (31)|Rail study 031|4x4|44/78|4|24/2|10/10|7/0|0.0%|None|0%|3/6|Easy|All successes remain solvable; orders are interchangeable|
|40|campaign-035 (35)|Polarity study 035|4x4|44/78|4|12/2|8/8|5/0|0.0%|Weak|0%|4/7|Easy|All successes remain solvable; orders are interchangeable|
|41|campaign-026 (26)|Twin cadence|3x3|43/85|3|6/3|6/6|5/0|0.0%|None|0%|3/5|Easy|All successes remain solvable; orders are interchangeable|
|42|campaign-027 (27)|Crossed cadence|3x3|44/85|3|6/2|6/6|5/0|0.0%|None|0%|2/3|Easy|All successes remain solvable; orders are interchangeable|
|43|campaign-029 (29)|Right rail duet|3x3|43/85|3|6/3|6/6|5/0|0.0%|None|0%|3/5|Easy|All successes remain solvable; orders are interchangeable|
|44|campaign-030 (30)|Balanced fields|3x3|44/85|3|6/2|6/6|5/0|0.0%|None|0%|2/3|Easy|All successes remain solvable; orders are interchangeable|
|45|campaign-034 (34)|Vector study 034|4x4|44/77|3|6/2|6/6|5/0|0.0%|None|0%|2/4|Easy|All successes remain solvable; orders are interchangeable|
|46|campaign-036 (36)|Cadence study 036|4x4|44/77|3|6/2|6/6|5/0|0.0%|None|0%|2/4|Easy|All successes remain solvable; orders are interchangeable|
|47|campaign-038 (38)|Alignment study 038|4x4|44/79|3|6/2|6/6|5/0|0.0%|None|0%|3/5|Easy|All successes remain solvable; orders are interchangeable|
|48|campaign-039 (39)|Rail study 039|3x3|44/85|3|6/3|6/6|5/0|0.0%|None|0%|2/3|Easy|All successes remain solvable; orders are interchangeable|
|49|campaign-042 (42)|Vector study 042|4x4|45/84|4|6/2|6/6|4/0|0.0%|Weak|0%|4/6|Easy|All successes remain solvable; orders are interchangeable|
|50|campaign-047 (47)|Rail study 047|3x3|59/84|4|16/8|8/8|6/0|0.0%|None|67%|4/6|Easy|All successes remain solvable; orders are interchangeable|

## 22. Top 30 Fake-Difficulty Levels

These are the highest v3-scored levels with at least 90% random-success completion and at most 3% meaningful failure. Every listed level has a 100% random-success solve probability and zero fatal successful edge. V3 sees different route/family signatures, dependency depth, and broad mechanic relevance; the human sees safe orderings.

|#|Level|V3/Q|Exact sequences → strategies|Redundancy|V3 sees|Human sees|
|-:|:-|:-:|:-:|--:|:-|:-|
|1|campaign-171 (171)|83/91|224 → 3|98.7%|6 decisions, depth 6, relevance 100%|0 harmful nodes; no meaningful ordering|
|2|campaign-183 (183)|83/93|1,680 → 2|99.9%|6 decisions, depth 4, relevance 100%|0 harmful nodes; weak ordering|
|3|campaign-194 (194)|83/88|1,400 → 4|99.7%|5 decisions, depth 4, relevance 100%|0 harmful nodes; weak ordering|
|4|campaign-091 (91)|83/98|3,360 → 3|99.9%|6 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|5|campaign-092 (92)|83/96|2,800 → 6|99.8%|6 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|6|campaign-090 (90)|82/97|1,792 → 6|99.7%|6 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|7|campaign-186 (186)|82/89|480 → 3|99.4%|5 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|8|campaign-193 (193)|82/86|480 → 3|99.4%|5 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|9|campaign-125 (125)|82/96|1,400 → 4|99.7%|5 decisions, depth 4, relevance 100%|0 harmful nodes; weak ordering|
|10|campaign-170 (170)|82/87|480 → 9|98.1%|5 decisions, depth 4, relevance 100%|0 harmful nodes; no meaningful ordering|
|11|campaign-189 (189)|81/92|840 → 1|99.9%|5 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|
|12|campaign-187 (187)|81/90|756 → 2|99.7%|6 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|
|13|campaign-182 (182)|81/89|504 → 3|99.4%|5 decisions, depth 6, relevance 100%|0 harmful nodes; weak ordering|
|14|campaign-088 (88)|81/100|3,360 → 2|99.9%|6 decisions, depth 3, relevance 100%|0 harmful nodes; no meaningful ordering|
|15|campaign-169 (169)|81/88|2,100 → 2|99.9%|6 decisions, depth 3, relevance 86%|0 harmful nodes; weak ordering|
|16|campaign-120 (120)|81/96|1,120 → 4|99.6%|6 decisions, depth 4, relevance 100%|0 harmful nodes; weak ordering|
|17|campaign-089 (89)|81/98|3,360 → 3|99.9%|6 decisions, depth 3, relevance 100%|0 harmful nodes; no meaningful ordering|
|18|campaign-190 (190)|81/90|3,360 → 4|99.9%|6 decisions, depth 3, relevance 100%|0 harmful nodes; no meaningful ordering|
|19|campaign-192 (192)|80/92|756 → 2|99.7%|6 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|
|20|campaign-168 (168)|80/92|360 → 1|99.7%|5 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|
|21|campaign-149 (149)|80/94|240 → 2|99.2%|4 decisions, depth 4, relevance 100%|0 harmful nodes; weak ordering|
|22|campaign-165 (165)|80/91|720 → 1|99.9%|5 decisions, depth 3, relevance 83%|0 harmful nodes; no meaningful ordering|
|23|campaign-167 (167)|80/89|720 → 1|99.9%|5 decisions, depth 3, relevance 83%|0 harmful nodes; no meaningful ordering|
|24|campaign-160 (160)|80/90|600 → 3|99.5%|5 decisions, depth 4, relevance 83%|0 harmful nodes; no meaningful ordering|
|25|campaign-181 (181)|80/91|600 → 3|99.5%|5 decisions, depth 4, relevance 83%|0 harmful nodes; no meaningful ordering|
|26|campaign-087 (87)|80/97|3,500 → 6|99.8%|6 decisions, depth 3, relevance 100%|0 harmful nodes; no meaningful ordering|
|27|campaign-164 (164)|80/89|2,240 → 4|99.8%|5 decisions, depth 3, relevance 100%|0 harmful nodes; no meaningful ordering|
|28|campaign-163 (163)|79/91|360 → 1|99.7%|5 decisions, depth 3, relevance 83%|0 harmful nodes; weak ordering|
|29|campaign-162 (162)|79/88|120 → 1|99.2%|5 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|
|30|campaign-191 (191)|79/87|180 → 2|98.9%|5 decisions, depth 3, relevance 100%|0 harmful nodes; weak ordering|

## 23. Top 20 Best Existing Levels

These are the best **relative references**, not proof of product-ready Hard/Expert quality. They rank highest for harmful choice nodes, fatal-edge rate, reduced random-success probability, order strength, strategic polarity impact, and multiple quotient strategies. All still retain substantial safe commutation, consistent with the owner's “all easy” assessment.

For every row, “harmful node” means a locally plausible successful alternative eventually loses while another wins; that is the meaningful consequence. Ordering is moderate except for the strong-order levels 140 and 145. The polarity column shows how often the signature flip changes later actionability. Current v3 labels all fatal choices fair and none guess-dependent because their one-step observable profiles differ from a winning choice; this makes a wrong choice potentially informative, but human readability of that clue remains unverified.

|#|Level|Title|Structural tier|Random success|Failure|Harmful nodes|Strategies|Polarity impact|Why it is relatively better|
|-:|:-|:-|:-|--:|--:|--:|--:|--:|:-|
|1|campaign-097 (97)|Relay study 097|Hard|50%|12.8%|5|3|17%|Five canonical states mix winning/fatal moves; moderate ordering|
|2|campaign-140 (140)|Alignment 140|Very Hard|25%|21.4%|2|4|50%|Strong ordering and unusually consequential flips|
|3|campaign-145 (145)|Polarity 145|Very Hard|25%|21.4%|2|4|40%|Strong ordering and consequential polarity|
|4|campaign-100 (100)|Cadence study 100|Hard|50%|7.5%|2|2|14%|Two canonical harmful nodes; moderate ordering|
|5|campaign-175 (175)|Dependency Lattice 15|Hard|50%|6.8%|2|2|33%|Two harmful nodes plus moderate order/polarity interaction|
|6|campaign-198 (198)|Expert Circuit 8|Hard|50%|13.0%|2|2|33%|High relative failure rate and moderate ordering|
|7|campaign-099 (99)|Polarity study 099|Hard|50%|5.9%|2|3|17%|Two harmful nodes and three strategy classes|
|8|campaign-199 (199)|Expert Circuit 9|Hard|50%|5.9%|2|3|17%|Two harmful nodes and three strategy classes|
|9|campaign-200 (200)|Expert Circuit Finale|Hard|50%|5.9%|2|3|17%|Two harmful nodes and three strategy classes|
|10|campaign-093 (93)|Circuit study 093|Hard|50%|11.8%|1|2|17%|One high-signal harmful node; moderate ordering|
|11|campaign-096 (96)|Field study 096|Hard|50%|8.6%|1|4|17%|Four strategies with a real wrong branch|
|12|campaign-095 (95)|Rail study 095|Moderate|67%|6.9%|1|4|60%|Strong relative polarity consequence and four strategies|
|13|campaign-195 (195)|Expert Circuit 5|Moderate|67%|6.9%|1|4|60%|Strong relative polarity consequence and four strategies|
|14|campaign-147 (147)|Cadence 147|Hard|50%|9.1%|1|2|17%|One real wrong branch; moderate ordering|
|15|campaign-184 (184)|Fair False Path 9|Hard|50%|9.1%|1|2|17%|One real wrong branch; moderate ordering|
|16|campaign-185 (185)|Fair False Path 10|Hard|50%|4.9%|1|2|25%|One harmful node and moderate ordering|
|17|campaign-197 (197)|Expert Circuit 7|Hard|50%|4.9%|1|2|20%|One harmful node and moderate ordering|
|18|campaign-098 (98)|Vector study 098|Moderate|67%|8.6%|1|4|60%|Consequential polarity plus a real wrong branch|
|19|campaign-135 (135)|Relay 135|Moderate|67%|8.6%|1|4|75%|Highest relative polarity impact with a wrong branch|
|20|campaign-173 (173)|Dependency Lattice 13|Moderate|50%|11.4%|1|2|25%|High relative fatal rate and moderate ordering|

## 24. Root Causes

Ranked by explanatory power:

1. **Successful choices are almost always safe.** The explored viability rate is 98.61%; only 1.39% of successful choices have a meaningful negative consequence.
2. **Branching is mostly permutation, not strategy.** Viable pairs commute 91.25% of the time and exact sequences collapse by at least 99.10% after state-identical reordering.
3. **Difficulty v3 defines difference as decision.** It rewards different child trace/family signatures even when every branch wins.
4. **The state transition is too binary for length/recovery metrics.** Every success removes one arrow, every win has the same length, viable choices have no extra cost, and fatal choices require undo/restart.
5. **Polarity is often decorative at decision level.** Only 21.51% of flips alter future actionability/viability and half the levels have no such canonical flip.
6. **Ordering claims are much stronger than ordering consequences.** Only 33 of 115 tagged levels contain a fatal successful order choice; 95.9% of arrow pairs are flexible.
7. **Walls frequently decorate routes instead of choices.** 68.4% do not change the exact winning-order set and none is necessary for root solvability.
8. **The generator deliberately adds independent safe exits.** This raises exactly the length, branch, non-forced, and family metrics rewarded by v3.
9. **Late generation stretches inherited templates.** Larger boards and purposeful-space walls inflate route/space scores without adding a new dependency graph.
10. **Quality gates certify correctness and surface structure, not cognitive demand.** Zero harmful decisions, full greedy solvability, and extreme commutation are not rejection reasons.

Concise root cause: **the campaign rewards and certifies many different ways to make progress, but almost never requires the player to distinguish a good move from a bad one.**

## 25. Proposed Difficulty v4 — Design Only

Difficulty v4 should not begin with a weighted sum. It should first construct a consequence-aware quotient graph, apply hard validity/fairness gates, and only then estimate a calibrated difficulty band.

### Required graph model

1. Build the exact successful-action DAG using production `StateKey` and engine semantics.
2. Mark every action as invalid, viable, or fatal; retain visible invalid actions only as feedback/friction diagnostics.
3. Quotient winning sequences by exact commutation and, where defensible, by equivalent future capability vectors.
4. For every choice, compare future actionability, controller availability, polarity, occlusion, mandatory-order relations, solvability, and undo requirement.
5. Measure along more than the lexicographically first path: report best, worst, and policy-weighted player paths.

### Metric roles

|Role|Metrics|
|:-|:-|
|Primary|Harmful decision count/density; meaningful failure rate; consequence persistence/depth; mandatory-order constraints; strategic-distinctness quotient; polarity actionability/solvability impact|
|Secondary|Fair clue strength; recovery/undo pressure; interacting-mechanic dependency graph; decision spacing; controller/visibility changes that affect viability; decision density per relevant entity|
|Penalties|Safe-choice ratio; commutative-pair ratio; permutation redundancy; greedy/random-success completion; excessive forced runs; independent-exit count; irrelevant entities/space; board expansion without dependency expansion|
|Hard gates|Production solvability/replay; complete/confident search; no guess-dependent fatal branch; claimed advanced mechanic has measured consequence; band-specific minimum harmful decisions; maximum trivial-policy solve rate; human review required for Hard/Expert|
|Diagnostic only|Raw solution length, raw sequence count, board dimensions, route length, occupancy, mechanic/tag presence, solver cost, confidence|

### Recommended core measures

- **Meaningful decision density:** harmful or cost-divergent choice nodes divided by successful path length. Do not count all-winning signature differences.
- **Consequence depth:** number of later decision opportunities whose capability vector remains changed, plus whether undo/restart becomes necessary.
- **Wrong-choice penalty:** separate harmless, recoverable-cost, and fatal. Current rules support harmless/fatal but not successful extra-cost recovery; report that limitation rather than fabricating a middle class.
- **Ordering dependency:** proportion and depth of mandatory pair/chain constraints after commutative collapse.
- **Polarity-state dependency:** fraction of flips that alter future successful/viable actions or solvability, not merely route animation.
- **Strategic distinctness:** number of quotient strategy classes, with controlled diversity preferred over one forced path or thousands of permutations.
- **Human-like robustness:** probability that simple no-lookahead policies solve; high values are an explicit difficulty penalty.
- **Fairness:** a consequence adds difficulty only if the pre-action board provides a readable reason. Solver-visible difference is necessary but not sufficient; human review remains mandatory.

Weights should be learned only after human labels exist. Until then, band gates and a multi-axis diagnostic profile are more honest than a single score.

## 26. Proposed Quality v2 Replacement — Design Only

The next quality model should retain correctness/identity/similarity gates and add consequence gates:

- minimum harmful decision count and density by intended band;
- maximum safe-success ratio;
- maximum commutative viable-pair ratio;
- maximum exact permutation redundancy;
- maximum greedy and random-success solve probability;
- minimum polarity actionability impact when `POLARITY_FLIP` is claimed as an advanced mechanic;
- minimum counterfactual relevance for walls, magnets, occlusion, and cancellation;
- minimum mandatory-order chain for advanced `ORDER_DEPENDENCY` levels;
- maximum forced-run length and minimum decision spacing quality;
- explicit rejection of board enlargement that does not increase dependency/decision density;
- human-review status that can never be set by automation.

Provisional band policy—not yet a certification threshold—should require progressively lower safe/commutative/greedy rates and progressively higher harmful-decision, consequence-depth, and polarity/order interaction. Thresholds must be calibrated against observed player solve behavior, not chosen to make the present campaign pass.

Suggested gate shape:

|Band|Expected structure|
|:-|:-|
|Tutorial/Very Easy|A forced or safely exploratory lesson is acceptable; one mechanic taught clearly.|
|Easy|At least one readable consequence or genuine order constraint; simple greedy may still often work.|
|Moderate|Multiple consequence-bearing decisions; at least one mechanic affects a later choice; safe permutations no longer dominate.|
|Hard|Several fair wrong branches or recoverable pressures, a multi-step dependency, and materially consequential polarity/order interaction. Casual successful tapping should not be reliable.|
|Expert|Layered dependencies across multiple decision nodes; controlled strategy diversity; wrong choices alter later capabilities; low greedy/random success; no guessing.|

## 27. Recommended Campaign Redesign

### Retain, tune, replace

- **Levels 1–12:** retain only as explicitly labeled teaching content. Their triviality is appropriate if pacing is short; do not use them as difficulty evidence.
- **Top 20 in section 23:** retain as reference kernels, then tune and human-playtest. They are the best current examples, not finished Hard/Expert levels.
- **The 167 levels with no fatal successful choice:** redesign or replace unless their curriculum role explicitly requires safe exploration.
- **The 30 fake-difficulty levels in section 22:** priority replacement/tuning candidates because high automated scores actively conceal trivial decision structure.
- **The 101 levels with no strategic canonical polarity impact:** rework if polarity is presented as part of the puzzle rather than animation.
- **Wall-heavy levels with no winning-order wall impact:** remove decorative walls or redesign them to alter a fair decision; do not add more clutter.

No content should be changed in bulk from this audit. A later remediation phase must first produce candidate diagnostics and a KEEP/TUNE/REPLACE manifest, obtain approval, and only then promote content.

### Proposed 200-level curriculum allocation

|Band|Count|Campaign role|
|:-|--:|:-|
|Tutorial|12|Rules and isolated mechanics|
|Very Easy|28|One readable dependency|
|Easy|35|One or two genuine consequences|
|Moderate|40|Multiple decisions and first mechanic chains|
|Hard|40|Sustained order/polarity/occlusion dependencies|
|Very Hard|30|Layered interactions and meaningful recovery pressure|
|Expert|15|Dense but readable multi-stage consequence networks|

This allocation totals 200 and is a redesign target, not a promotion decision.

### Mechanic progression

- Introduce polarity as a prediction requirement: a flip must change a later arrow's controller or viability.
- Use occlusion to open/close control relationships after removals, not merely to alter a ray.
- Increase cancellation beyond 13 levels only through readable, consequential sequences.
- Use walls sparingly as branch-defining constraints; avoid space-filling.
- Combine mechanics only when the interaction changes a decision. “Pull + wall + two magnets present” is not a combination by itself.
- Build advanced levels from dependency graphs first, then lay them out spatially. Do not begin from target board size or target score.

“Hard” should mean that the player must predict at least several future capability changes and a plausible wrong move has a fair, persistent cost. “Expert” should mean layered dependencies, multiple consequential decisions, controlled alternative strategies, and low trivial-policy success—not a larger or more crowded board.

## 28. Risks and Unknowns

- Actual visual obviousness, attention, learning, animation readability, and perceived fairness are **NOT MEASURABLE WITH CURRENT IMPLEMENTATION**. They require recorded human playtests.
- The owner supplied the decisive aggregate judgment (“all easy”) but no per-level time, undo, restart, or mistake log. Correlating v4 with human performance is not yet possible.
- The 633 strategy classes collapse only exact adjacent commutations. It is an upper bound on cognitive strategies; actual redundancy may be higher.
- Arrow-occluder removal also removes a goal object, so exact strategic arrow-occlusion relevance is **NOT MEASURABLE WITH CURRENT IMPLEMENTATION** without a formally approved transparency counterfactual.
- Fatal-choice fairness is based on existing one-ply signatures. Whether a player can foresee the clue is **NOT MEASURABLE WITH CURRENT IMPLEMENTATION**.
- Four family counts are capped at 512, but exact offline sequences and all viability/dead-end results are complete. No state/action search truncation affected the central findings.
- The audit ran against the current dirty working tree, which contains pre-existing project changes. Findings apply to that exact working state and content version 6.
- The duplicate `polarityFlipCount` increment makes fresh raw v3 diagnostics disagree with checked-in reports for that field. Direct engine counts were used here.

## 29. Recommended Next Steps and Direct Answers

### Direct answers

|Question|Answer|
|:-|:-|
|Q1. Why can 200 certified levels be easy?|Certification proves correctness and broad structural targets, while 98.61% of successful actions remain winning and 91.25% of viable pairs commute.|
|Q2. Is 98.3% viability the main problem?|Yes. It is the clearest direct symptom. The underlying causes are generator-produced independent actions and an analyzer that rewards safe branching.|
|Q3. How much branching is fake?|At least 99.10% of the aggregate exact sequence count collapses under exact commuting swaps; 19,645/21,529 viable pairs commute.|
|Q4. How often does polarity matter?|165/767 canonical flips (21.51%) strategically change actionability/viability or solvability; 99/200 levels have at least one such flip.|
|Q5. How often do walls/occlusion matter?|89/282 walls change winning orders; 155/164 occlusion-tagged levels have a physical controller effect. Exact strategic arrow-occlusion rate is not measurable safely.|
|Q6. How often does order matter?|Only 33/115 order-tagged levels have a fatal successful order choice; 132/200 have no meaningful ordering and 0 are critical.|
|Q7. How often does a wrong choice matter?|295/21,196 successful choices, or 1.39%, create an unsolvable future.|
|Q8. How many solve greedily?|183/200 with a stable-order no-lookahead successful-action policy; random successful play is guaranteed on 167/200.|
|Q9. Does difficulty increase 1→200?|The v3 number does; consequence-based difficulty rises weakly and non-monotonically, with major regressions at 101 and 151–160.|
|Q10. Why are later levels easy?|They stretch/re-theme existing templates, preserve independent moves, add space-tuning walls, and target v3 features that reward harmless variety.|
|Q11. Best 20?|97, 140, 145, 100, 175, 198, 99, 199, 200, 93, 96, 95, 195, 147, 184, 185, 197, 98, 135, 173.|
|Q12. Weakest 50?|Listed in section 21 and the structured JSON; includes tutorial 1–12 plus severe late fake-difficulty offenders such as 153, 163, 152, 159, 191, and 122.|
|Q13. Biggest flaw in v3?|It treats strategically harmless future-signature differences as meaningful decisions and then rewards their branching.|
|Q14. What should v4 measure?|Whether alternatives change future capability: harmful decision density, consequence depth, order/polarity dependency, strategy quotient, recovery pressure, and trivial-policy robustness.|
|Q15. What next?|Build and calibrate an isolated consequence-aware v4 diagnostic harness before changing any generator or campaign content.|

### Exactly one next development action

**Implement an offline Difficulty v4 diagnostic prototype—without changing production gameplay or campaign content—that computes harmful decision nodes, commutation-quotiented strategies, consequence depth, exact order constraints, polarity actionability impact, and trivial-policy success; then calibrate its gates using owner/human labels on the 20 best references and a deliberately selected weak control set.**

Only after that calibration should a separate phase design candidate levels and produce a reviewed KEEP/TUNE/REPLACE remediation manifest. No automated score should promote content or substitute for human playtest approval.
