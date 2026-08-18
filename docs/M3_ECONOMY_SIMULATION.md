# Magnetrail M3 economy simulation

The developer-only `EconomySimulation` test walks 100 first campaign clears and
same-result replays with the frozen M3 configuration. Hints are charged before
the simulated clear; rewards arrive only after completion.

| Scenario | Minimum | Median | Final | Unaffordable hints |
|---|---:|---:|---:|---:|
| Clean, three stars, no hints | 150 | 1,900 | 3,650 | 0 |
| Two stars, one hint every four levels | 150 | 1,290 | 2,400 | 0 |
| Two stars, one hint every level | 120 | 150 | 150 | 0 |

The simulation also replays every result and asserts that first-clear and
already-earned-star rewards are not repeated. It is a deterministic balance
check, not a player-behavior forecast; M4 should tune these values only after
observational data and privacy/consent review.
