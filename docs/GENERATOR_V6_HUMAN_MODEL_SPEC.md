# HumanLikeDifficultyV1 specification

Model/version: `human-like-difficulty-v1-five-band-v2`.

The ordered labels are Easy (1), Medium (2), Hard (3), Super Hard (4), and Expert (5). Master is
not collected, fitted, predicted, validated, or promoted by V6.

This is an interpretable, human-calibrated predictive model. It is not a claim that software thinks
like a human.

## Objective feature schema

The frozen feature order is:

1. meaningful decision count;
2. non-commuting forks;
3. successful persistent traps;
4. minimum lookahead/proof depth;
5. delayed-consequence depth;
6. polarity-memory depth/span;
7. controller/visibility transitions;
8. interacting causal chains;
9. hardest-state winning-choice ratio;
10. solution-policy classes;
11. longest forced run (diagnostic/penalty context only);
12. meaningful-decision density;
13. guess dependence;
14. recovery/restart cost;
15. route/LOS reasoning cost.

Features come only from visible board state and complete production-engine behavior. Bucket,
profile, grammar, seed, intended tier, authored solution, object count, occupancy and par are absent.

## Policy ensemble

Deterministic policies cover immediate route safety, unaffected exit, shortest route, controller
stability, polarity preservation, one- through seven-step bounded lookahead, greedy actionability
and fixed-seed plausible choice. Each reports lookahead, alternatives examined, remembered magnet
states, tracked controller transitions, trap avoidance and restarts. Reliability is at least 80%
across configured runs. A reliable low-budget solve rejects bucket 5 eligibility.

## Ordinal model

`CumulativeLinkOrdinalCalibratorV1` fits an L2-regularized proportional-odds cumulative-link
logistic model with four ordered cut points. Inputs are standardized from calibration-only data.
When sufficient repeated-participant data exists, a regularized participant intercept is fitted and
centred. Gradient descent has fixed initialization, iteration count, learning-rate schedule,
projection and ordering; no opaque learner is used.

Predictions contain latent difficulty, five probabilities, a confidence interval, calibration
confidence, OOD flag, the five largest signed feature contributions and an explicit rejection
reason. OOD is triggered by any feature outside calibration support or excessive standardized
distance. Confidence below 0.70 or maximum band probability below 0.40 is rejected.

`BradleyTerryCalibratorV1` deterministically fits optional blinded pairwise comparisons as
secondary evidence. It emits centred per-board abilities and pairwise probabilities, but cannot
independently create `HUMAN_MODEL_CERTIFIED` or replace the five-band ordinal observations.

## Evidence contract

Calibration and sealed validation require at least ten participants, at least five ratings per
board and no participant above 20% of observations. Hinted sessions remain in the audit but are
excluded from primary no-hint completion/time metrics.

Board fingerprint, graph instance/family, semantic cluster and participant grouping are protected
against train/validation leakage. Grouped-family cross-validation occurs before model freeze.
Feature schema, source dataset, standardized parameters, cut points and model receive independent
SHA-256 values. Sealed results are not opened until those hashes are frozen.

Historical D1/V10 rows are controls only because their fingerprints differ from V6. They cannot
certify V6.

## Sealed gates

`HumanModelValidatorV1` applies the correlation, clustered-bootstrap, all adjacent-band
superiority, within-band, median, severe-underrating, fairness, guessing, repetition and completion
thresholds to the five-band contract. Bucket 3 must reach Hard, Bucket 4 Super Hard, and Bucket 5
Expert. Bootstrap sampling is deterministic and independently resamples participant and board
clusters. Per-family severe-underrating, fairness, and guessing gates are also fail-closed. The former
Master-versus-Expert gate is removed because Master itself is removed; no remaining threshold is
lowered. Failure requires a new versioned generator/model/sealed set; thresholds are not retuned on
the sealed set.

## Required CSV bindings

Every observation binds anonymous participant, catalog hash, exact board fingerprint, causal
fingerprint, decision-DAG fingerprint, semantic cluster, presentation order, completion/abandonment,
perceived band, active time, actions, failed actions, successful wrong actions, restarts, hints,
deadlocks, fairness/predictability, guessing, repeated-strategy response and optional comment.
