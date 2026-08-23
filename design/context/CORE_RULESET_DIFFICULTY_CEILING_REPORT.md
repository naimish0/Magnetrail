# Magnetrail core-1 Master difficulty ceiling

Date: 22 August 2026  
Scope: Generator V6.1 automated campaign and Auto Journey  
Finding: `BLOCKED_RULESET_CEILING` for the Master band only

## Decision

Master is removed from the active V6.1 campaign and Auto Journey schedules under the owner's prior authorization. The enum remains readable for historical V6/V10/V11 artifacts, but a new Master request fails immediately with `REJECT_MASTER_BAND_OWNER_REMOVED`. Expert is the highest band that V6.1 may synthesize or assign. This does not rename Expert as Master and does not lower any Master gate.

## Bounded evidence

Two bounded Master refinement directions were investigated through the unchanged production engine:

1. The ordinary Master search used 64 deterministic attempts. Sixty-two proposals were unsolvable and two hit the complete-analysis cap. None reached technical admission.
2. A 6x6 expansion of the strongest cancellation braid reached 22 successful solution actions, eight critical episodes, eight successful traps, no cheap-policy completion, visible lookahead nine, maximum forced run two, cleanup ratio 0.1818, and inferability 1.0. It still had only three causal phases and its first critical decisions occurred too late. Variants intended to add a fourth phase either hit `DECISION_DAG_RESOLUTION_CAP:750000` or degraded to four decisions, one phase, cheap-policy solve rate 0.25, and lookahead five. A retained full-generator diagnostic exhausted the 512 MiB test worker with `OutOfMemoryError`; that construction was removed.

Search exhaustion, resolution caps, memory pressure, board size, action count, and solution length are rejection evidence—not difficulty evidence. The numerical Master contract therefore remains unproven under `magnetrail-core-1`.

## Frozen-rule constraints observed

- Failed taps do not mutate state, so probing cheaply eliminates many apparent choices.
- Every successful action removes an arrow, making the state graph finite but strongly permutation-shaped.
- Only the controlling magnet flips, which limits persistent memory channels without introducing visually ambiguous controller ties.
- Adding independent chains increases actions and state-space cost faster than it creates opening/middle/late cross-chain decisions.
- Adding enough interacting actions for four phases causes complete DAG enumeration to approach the frozen 750,000-resolution budget; the same additions often create forced cleanup or cheap scan policies.
- The 8x8 maximum and purposeful-object requirements prohibit padding the board to manufacture more apparent complexity.

## Recommendation

Keep the production rules unchanged for V6.1 and ship no Master label. If a future owner wants a sixth empirically separable tier, authorize a separate mechanic/ruleset prototype and human study. Candidate ideas must be evaluated outside this task and must not be silently added to `magnetrail-core-1`.

