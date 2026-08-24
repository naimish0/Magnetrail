# Generator V6 acceptance contract

Contract version: `generator-v6-acceptance-five-band-2`.

V6 has exactly five provisional/human bands: Easy, Medium, Hard, Super Hard, and Expert. Master is
out of scope by owner authorization and cannot appear in a V6 identity, pilot, model output, full
candidate, or promotion manifest.

## State machine

The only valid machine statuses are:

`BASELINE_VERIFIED`, `V6_TECHNICALLY_CERTIFIED`, `AWAITING_HUMAN_CALIBRATION`,
`HUMAN_MODEL_CERTIFIED`, `FULL_CAMPAIGN_STAGING_CERTIFIED`, `CAMPAIGN_CERTIFIED`,
`PROMOTED_V6_CAMPAIGN`, `FAIL_NO_PROMOTION`, and `BLOCKED_RULESET_CEILING`.

Automated evidence can advance only through `V6_TECHNICALLY_CERTIFIED`. Real fingerprint-bound
human data is mandatory for `HUMAN_MODEL_CERTIFIED`. Only `CAMPAIGN_CERTIFIED` enables promotion.

## Candidate invariants

A technically accepted candidate must satisfy every item:

- dimensions are within 8×8, entities are unique/in bounds and level serialization parses;
- the authored partial-order witness clears through `DefaultGameEngine`;
- the independent `Solver` is complete, solvable and its solution replays;
- every failed action at every reachable state preserves exact state;
- the complete decision DAG is non-truncated;
- every declared causal edge has a reachable production-engine `CausalWitness`;
- every trap succeeds, removes its arrow, persists state, is losing at the wrong point, meets its
  delayed-depth contract and belongs to a winning policy at the correct point;
- provisional bucket behavioural gates pass;
- occupancy is in range and relevance gates pass without filler;
- V4 and Quality complete without essential truncation or rejection;
- a simple/low-budget policy does not reliably solve bucket 5;
- no decision requires indistinguishable guessing;
- exact, D4, arrow-layout, interactive-layout, perceptual-template, relevance-pruned, causal,
  decision-DAG, solution-policy and synthesis-graph fingerprints are unique;
- near-semantic similarity does not exceed frozen threshold `0.92`;
- regeneration is byte-identical for identical inputs.

Immediate failures are recorded separately and provide no trap/lookahead credit. Object count,
occupancy, par, solution length, route length, raw solution count and forced-run length provide no
positive difficulty credit.

## Provisional behavioural and layout gates

The bucket gates and occupancy bands are frozen exactly as specified in the owner directive. They
are represented by `V6Profiles`. They are generation eligibility gates, never human labels.

## Pilot catalogs

Calibration and sealed validation each require exactly 30 technically certified boards, six per
bucket. They use disjoint seed schedules and graph-instance identities. Each has a frozen SHA-256.
No family may provide more than two boards per bucket or 25% of the catalog. Buckets 4–5 require at
least four families. Failure to fill within the attempt cap preserves partial audit output and sets
`FAIL_NO_PROMOTION`; no gate may change within the run.

## Full campaign

Full campaign generation is forbidden before a signed/hash-bound `HUMAN_MODEL_CERTIFIED` artifact
passes sealed validation. Every full candidate repeats all technical gates and additionally needs a
non-OOD human prediction with acceptable confidence. Archive exhaustion is `FAIL_NO_PROMOTION`.

Campaign ordering limits one family to 5% of a tier, one behavioural cluster to 2%, the same family
within eight positions and the same cluster within twenty positions. Exhaustion cannot be filled by
clones or weaker gates.

## Failure rules

Engine/solver disagreement, truncation, nondeterminism, undetected known V10 semantic clones,
filler-dependent density, simple-policy high buckets, guessing, semantic collapse, insufficient
human evidence, inseparable bands or unsafe migration fail closed. If Expert itself cannot be
separated after two bounded refinements, status becomes `BLOCKED_RULESET_CEILING` and the rules
remain unchanged.
