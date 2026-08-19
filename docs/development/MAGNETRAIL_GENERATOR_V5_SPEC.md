# Magnetrail Generator V5 — Solution-First Repair Specification

Status: implemented for staging diagnostics; production campaign promotion is prohibited.

## Scope and authority

Generator V5 constructs a solution/dependency contract before filling a board. The production
`DefaultGameEngine`, `Solver`, Difficulty V4, Quality V2, spatial gates, duplicate checks, and
campaign certification remain authoritative. The generator does not reproduce or override their
logic. An incomplete solver or V4 result is a certification failure.

The canonical campaign, stable level IDs, rules, Difficulty V3, and Difficulty V4 are unchanged.

## Pipeline

```text
Difficulty profile
  -> versioned solution contract
  -> cancellation / exposure / polarity scaffold
  -> full-occupancy board materialization (Medium+)
  -> production-engine canonical replay
  -> complete solver proof
  -> unchanged V4 and production quality gates
  -> bounded repair with rollback
  -> diversity/certification
  -> staging only
```

## Construction contract

`SolutionContractV5` contains unique object/action nodes, typed dependency edges, and one known
canonical action order. Contract edges include arrow blocking, magnet control, cancellation,
polarity dependency, wall route blocking, exposure, ordering, and state dependency. Cyclic
contracts are rejected before materialization.

The current connected scaffold intentionally creates:

- arrow blockers whose removal exposes the next route;
- two equal-distance magnetic controllers that produce cancellation transitions;
- successful magnet-controlled moves with persistent polarity state;
- polarity traps where a successful early move makes the remaining board unsolvable;
- walls shielding routes and magnetic visibility;
- one complete known replay constructed before certification.

Contract metadata never makes a board valid. The constructor replays every canonical action using
the production engine, then requires a complete solver proof.

## Profiles

Tutorial and Easy retain the established low-density path. D2.1 Medium, Hard, Very Hard, Expert,
and Master select `SOLUTION_FIRST`, use a deterministic 6x6–8x8 structural scaffold, and require
100% authored occupancy. Object-count ceilings were expanded only so arrows, magnets, and walls
can occupy every cell. After the owner explicitly approved a bounded reduction, staging-only
profile floors were calibrated to the measured full-occupancy scaffold. Difficulty V4, production
quality/campaign certification, solver completeness, and truncation handling were not changed.

Difficulty is still decided by V4 and the structural gates, never by grid size or occupancy.

## Bounded repair

Each solution-first profile has one repair attempt per candidate. Operators currently support:

- flipping a shielded filler magnet for polarity/safe-choice failures;
- swapping a shielded magnet and wall for wall/interaction/relevance failures.

Before a repair is committed, the original canonical solution must replay and the bounded solver
must remain complete and solvable. Otherwise the mutation is rolled back. Repair never changes a
gate, search cap, rule, or certification result.

## Determinism

The tuple `(generatorVersion, profile, seed, request)` reproduces the same contract and board.
Generation does not use time, device state, network state, Android lifecycle, advertising, or
analytics.

## Diagnostics

`GenerationTelemetryV5` records candidate attempts, successful constructions, repair attempts and
rollbacks, solver failures, V4 truncations, ordering failures, wall-participation failures,
relevance failures, safe-choice failures, commutation failures, consequence failures, duplicate
failures, difficulty failures, and certified candidates.

The `benchmarkGeneratorV5Repair` Gradle task writes the audit, rejection distribution, benchmark
CSV, and a staging-only candidate catalog. It hashes the production campaign before and after and
fails if the bytes change.

## Benchmark result and limitation

The bounded benchmark certified one Easy, Medium, Hard, Very Hard, Expert, and Master candidate
from one attempt per band. All six had complete solver and V4 analysis with zero truncation. This
proves deterministic bounded construction for the benchmark seeds, not broad seed-distribution or
human difficulty calibration. Staging candidates remain unpromoted.
