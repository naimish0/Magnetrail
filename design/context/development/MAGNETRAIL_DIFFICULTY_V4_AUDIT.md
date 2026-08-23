# Magnetrail Difficulty V4 Diagnostic Audit

> V4 is an experimental diagnostic model and is not yet a production difficulty authority.

Status: **Awaiting human calibration.** Scores are provisional calibration version 0.

## Executive summary

The analyzer evaluated 200 campaign levels without modifying campaign content. It separates successful-but-safe choices from consequence-bearing decisions, collapses commuting winning permutations, tests polarity actionability counterfactually, and penalizes forced runs and irrelevant structure.

- Scored levels: 200/200
- Truncated levels: 0
- Aggregate safe-choice ratio: 82.32%
- Aggregate meaningful-failure rate: 17.68%
- Harmful/meaningful decision-state density: 71.37%
- Polarity impact: 1671/4934 (33.87%)
- Greedy-solved levels: 99/200
- Mean random-success completion rate: 47.18%
- Raw winning sequences: 4186
- Canonical strategy representatives: 281
- Aggregate permutation redundancy: 93.29%
- Mean V3 score: 76.63
- Mean provisional V4 score: 37.12

## V3 vs V4

V3 and V4 are independent. V3 rewards several activity/branching signals; V4 makes consequence, mandatory order, polarity actionability, greedy/random resistance, and recovery primary while applying explicit penalties for safe choices, permutation redundancy, forced runs, and irrelevant walls/magnets.

### Largest provisional downward changes

| Level | V3 | V4 | Change | Safe | Redundancy | Harmful density | Ordering | Polarity | Greedy |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| 46 | 86 | 3 | -83 | 91.89% | 92.00% | 35.29% | 0.00% | 42.42% | solves |
| 38 | 79 | 2 | -77 | 94.12% | 96.67% | 28.57% | 0.00% | 57.14% | solves |
| 124 | 74 | 0 | -74 | 94.12% | 96.67% | 28.57% | 0.00% | 26.67% | solves |
| 28 | 77 | 6 | -71 | 91.89% | 92.00% | 35.29% | 0.00% | 58.33% | solves |
| 118 | 80 | 12 | -68 | 92.68% | 97.14% | 42.86% | 10.00% | 17.07% | solves |
| 22 | 76 | 9 | -67 | 90.16% | 92.50% | 37.50% | 0.00% | 48.89% | solves |
| 77 | 87 | 21 | -66 | 89.87% | 95.56% | 47.06% | 20.00% | 18.92% | solves |
| 70 | 73 | 9 | -64 | 90.91% | 88.00% | 36.36% | 0.00% | 61.54% | solves |
| 87 | 76 | 12 | -64 | 92.16% | 93.02% | 33.33% | 13.33% | 65.52% | solves |
| 178 | 83 | 20 | -63 | 89.13% | 92.11% | 41.67% | 10.00% | 15.62% | solves |
| 130 | 82 | 20 | -62 | 92.11% | 97.62% | 75.00% | 6.67% | 19.74% | solves |
| 4 | 61 | 0 | -61 | 100.00% | 83.33% | 0.00% | 0.00% | 0.00% | solves |
| 26 | 85 | 24 | -61 | 91.18% | 97.44% | 54.55% | 20.00% | 20.00% | solves |
| 176 | 84 | 25 | -59 | 86.67% | 95.00% | 72.73% | 10.00% | 28.57% | solves |
| 179 | 88 | 29 | -59 | 82.98% | 92.00% | 80.00% | 10.00% | 22.73% | solves |
| 138 | 79 | 21 | -58 | 85.71% | 97.78% | 72.73% | 10.00% | 7.14% | solves |
| 8 | 57 | 0 | -57 | 100.00% | 83.33% | 0.00% | 0.00% | 0.00% | solves |
| 49 | 82 | 25 | -57 | 85.29% | 88.24% | 55.56% | 30.00% | 8.00% | solves |
| 62 | 87 | 31 | -56 | 90.91% | 97.50% | 100.00% | 20.00% | 9.09% | fails |
| 104 | 84 | 28 | -56 | 86.67% | 95.00% | 72.73% | 10.00% | 38.10% | solves |

### Highest provisional V4 diagnostics

| Level | V3 | V4 | Change | Safe | Redundancy | Harmful density | Ordering | Polarity | Greedy |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| 112 | 88 | 67 | -21 | 61.90% | 80.00% | 100.00% | 60.00% | 50.00% | fails |
| 72 | 89 | 65 | -24 | 67.74% | 91.67% | 100.00% | 60.00% | 32.00% | fails |
| 147 | 88 | 65 | -23 | 53.33% | 83.33% | 100.00% | 66.67% | 41.67% | fails |
| 40 | 90 | 64 | -26 | 69.81% | 93.33% | 85.71% | 40.00% | 35.56% | fails |
| 14 | 67 | 63 | -4 | 75.00% | 0.00% | 100.00% | 33.33% | 57.14% | fails |
| 192 | 83 | 63 | -20 | 56.52% | 80.00% | 100.00% | 60.00% | 44.44% | fails |
| 19 | 86 | 62 | -24 | 61.90% | 80.00% | 100.00% | 60.00% | 38.10% | fails |
| 196 | 84 | 62 | -22 | 73.91% | 90.00% | 85.71% | 40.00% | 66.67% | fails |
| 27 | 80 | 61 | -19 | 70.83% | 71.43% | 85.71% | 53.33% | 70.83% | fails |
| 41 | 82 | 61 | -21 | 75.00% | 66.67% | 100.00% | 80.00% | 41.67% | fails |
| 83 | 90 | 61 | -29 | 73.08% | 90.00% | 70.00% | 71.43% | 75.00% | fails |
| 173 | 83 | 61 | -22 | 68.75% | 75.00% | 66.67% | 70.00% | 68.75% | fails |
| 188 | 86 | 61 | -25 | 72.22% | 94.44% | 80.00% | 53.33% | 50.00% | fails |
| 200 | 85 | 60 | -25 | 69.70% | 93.33% | 100.00% | 40.00% | 30.77% | fails |
| 144 | 88 | 59 | -29 | 66.67% | 83.33% | 100.00% | 66.67% | 14.29% | fails |
| 88 | 88 | 58 | -30 | 75.00% | 95.00% | 81.25% | 26.67% | 66.07% | fails |
| 199 | 86 | 58 | -28 | 70.00% | 83.33% | 100.00% | 73.33% | 33.33% | fails |
| 43 | 84 | 57 | -27 | 77.78% | 95.00% | 100.00% | 30.00% | 50.00% | fails |
| 32 | 89 | 56 | -33 | 61.90% | 80.00% | 100.00% | 60.00% | 38.10% | solves |
| 64 | 77 | 56 | -21 | 77.78% | 95.00% | 100.00% | 30.00% | 66.67% | fails |

### Required reference/control inspection

These groups are sampling groups, not objective hard/easy truth.

| Level | V3 | V4 | Positive driver | Largest penalty | Confidence |
|---:|---:|---:|---|---|---:|
| 97 | 79 | 36 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.666 | 1.00 |
| 100 | 64 | 39 | harmfulDecisionDensity:17.286 | safeChoiceRatio:17.5 | 1.00 |
| 122 | 80 | 31 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.666 | 1.00 |
| 140 | 79 | 26 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.666 | 1.00 |
| 145 | 75 | 39 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.666 | 1.00 |
| 152 | 85 | 50 | harmfulDecisionDensity:20.0 | permutationRedundancy:14.25 | 1.00 |
| 153 | 84 | 37 | harmfulDecisionDensity:20.0 | safeChoiceRatio:17.392 | 1.00 |
| 159 | 69 | 16 | harmfulDecisionDensity:13.398 | safeChoiceRatio:18.182 | 1.00 |
| 163 | 84 | 54 | harmfulDecisionDensity:20.0 | safeChoiceRatio:15.556 | 1.00 |
| 175 | 74 | 44 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.666 | 1.00 |
| 191 | 83 | 32 | harmfulDecisionDensity:20.0 | safeChoiceRatio:16.924 | 1.00 |
| 198 | 85 | 56 | harmfulDecisionDensity:20.0 | safeChoiceRatio:15.556 | 1.00 |

## Metric interpretation

A meaningful decision state has at least two successful choices whose outcomes differ by a proven dead end, a future capability signature, a reduction in winning continuations, or non-commutation. A harmful decision state specifically contains both a viable continuation and a successful action with a completely proven unsolvable descendant. Route-only differences are excluded from harm.

Greedy is stable authored-order among currently successful arrows. Random-success trials choose uniformly from successful actions using the 256 fixed serialized seeds. Neither policy models visual salience.

## Search safety and confidence

Bounds: states=100000, action resolutions=1000000, depth=32, winning sequences=100000, canonical representatives=100000, random trials=256. Any exhausted bound makes the affected metric incomplete and appears as null/status/truncation evidence.

## Limitations

- NOT MEASURABLE WITH CURRENT IMPLEMENTATION: human-perceived obviousness.
- NOT MEASURABLE WITH CURRENT IMPLEMENTATION: visual fairness and player attention.
- V4 is experimental; preliminary human evidence does not make it a production difficulty authority.

Automated analysis is not human validation or human approval. Awaiting human calibration.
