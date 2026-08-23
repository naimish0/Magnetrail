# Generator V6.1 — automated dual-engine architecture

Status: implemented; the 22 August 2026 full-campaign attempt is `AUTOMATED_CAMPAIGN_REJECTED`. Production remains V10.

## Contract

Generator V6.1 is **Semantic Novelty plus Cued Multi-Phase Adversarial Difficulty**. Uniqueness and difficulty are independent conjunctive gates. A board cannot compensate for failing either engine with object count, density, board size, authored tier, path length, or an additive score. Board dimensions are limited to 8×8.

The production `GameEngine` is the sole gameplay authority. Candidate analysis expands exact production states, annotates immutable failed moves, replays successful transitions, and rejects state/resolution/search truncation. The independent solver must return and replay a clean solution before certification.

## Engine A — semantic novelty

Accepted candidates persist exact, D4, relevance-pruned D4, state-conditioned causal-hypergraph, quotient decision-DAG, solution-policy, production-transition-trace, mechanic-rhythm, causal-family, strategy-cluster, and pre-geometry synthesis-graph fingerprints. IDs, metadata, insertion order, rotation, and reflection do not create novelty. Exact, D4, relevance, causal, decision-DAG, solution-policy, synthesis-graph, and frozen near-semantic collisions are rejection reasons.

The comparison corpus is the shipped V10 campaign, Infinite catalog, Daily fallbacks, V11 pilot, rejected V6 audit, the accepted Expert-capacity archive, the candidate archive, and—at runtime—durable Auto Journey history. “Unique” means no collision under these operational fingerprints in this checked corpus; it is not a claim of universal mathematical uniqueness.

## Engine B — cued adversarial difficulty

A `CriticalDecisionEpisodeV61` requires at least two successful, non-commuting production actions. One must preserve solvability and another must produce a delayed persistent losing consequence while also being valid at another reachable point. Immediate failed taps and forced-only runs receive no decision credit.

Each episode requires `VisibleDecisionProofV61`: exact state fingerprint, safe/trap cells, visible controller and polarity, routes, downstream divergence, delayed depth, production trace, bounded visible lookahead, and an explanation using only displayed cells and frozen rules. Any invalid proof or inferable ratio below 1.0 rejects the board as guess-dependent.

The policy ensemble attacks all D4 presentations with row/reverse scan, edge/center, exit/route/controller/polarity heuristics, stable order, random-successful choice, probe/restart memory, visible lookahead one through five, and limited polarity memory. It records cheap-policy success, blind/random completion, lookahead, restarts, decisions, traps, phases, cross-chain dependency evidence, forced runs, opening-decision position, and cleanup-tail ratio.

Band assignment is the minimum of independent length, critical-decision, interaction, delayed-trap, policy-resistance, visible-lookahead, and forced-run caps. Inferability, guess resistance, semantic novelty, purposeful occupancy, and complete analysis are hard gates. Easy through Expert are the five active automated classifications. Master remains parseable for historical artifacts but is rejected for synthesis under the owner-authorized ruleset-ceiling decision; Expert is not relabeled. These are automated classifications, not human validation.

## Purposeful occupancy

Occupancy is presentation/capacity only. Targets are 45–60% Easy, 50–65% Medium, 55–70% Hard, 60–75% Super Hard, and 65–80% Expert. Purposeful occupancy must be at least 85% below Super Hard and at least 90% for high tiers; inert occupancy must be at most 10%. Object witnesses are produced from reachable production states and counterfactual participation. Filler does not raise difficulty and is rejected.

## Deterministic generation and campaign pacing

Public salt, ordinal, band, family slot, and bounded attempt index derive a stable identity and seed. Candidate realization, DAG enumeration, solver replay, fingerprint canonicalization, rejection ordering, and archive comparison are bounded and stable. Counterexamples become deterministic seed/refinement inputs; exhausted bounds reject rather than imply difficulty.

Expert no longer selects one of three fixed authored challenge spines. `ExpertLogicalGraphSynthesizerV61` first creates an 18-action, three-chain state-conditioned causal specification with variable chain partition, phase boundaries, six non-commuting decision pairs, at least three delayed-trap contracts, and a deterministic subset of acyclic cross-phase dependencies. The exact coloured-incidence-graph canonicalizer proves the logical signature before any cells are chosen. That signature selects a spatial search kernel and independent refinements, but it is never gameplay evidence: the complete production decision DAG, extracted production causal graph, visible proofs, solver replay, and policy ensemble independently re-certify the realized board.

The high-band campaign phase is guarded by `verifyGeneratorV61ExpertCapacity` and
`verifyGeneratorV61SuperHardCapacity`. Each accepts only a fresh, hash-bound 24-board proof whose
catalog and audit hashes match and whose proof is newer than all V6 synthesis sources. The proof
commands load shipped, rejected, and prior-staging fingerprints and require all 24 logical,
production-causal, decision-DAG, and solution-policy signatures to be distinct.

The 2,193 non-tutorial schedule preserves Levels 1–12 and uses a progressive introduction through Level 60. Thereafter each 20-level cycle contains 2 Easy, 4 Medium, 6 Hard, 5 Super Hard, and 3 Expert slots. Twenty-four family identifiers permit an eight-position family gap. Strategy clusters require a twenty-position gap and are capped at 2% of a band. Both cluster constraints now run during candidate admission, not only as a fail-late final audit.

Production validation is split without changing that schedule:

- Phase 1 certifies 1,325 Easy, Medium, and Hard boards.
- Phase 2 certifies 868 Super Hard and Expert boards against the Phase 1 archive and neighboring
  pacing slots.
- Final certification merges both sets with the 12 preserved tutorials, recomputes global semantic
  uniqueness and pacing, and replays all 2,205 complete solution witnesses through the production
  engine.

The phase partition is by scheduled band, not by a contiguous level-number range. Both phases reuse
the family allocation calculated over the original unsplit 2,193-slot schedule. Cross-phase family
and strategy-cluster spacing is enforced when Phase 2 is admitted and again by the final full-sequence
pacing audit. No phase certificate is promotable by itself.

Strategy cluster schema `v61b2` uses only engine-derived behavior: decision/trap/lookahead/polarity/partial-order metrics plus reachable-state scale, policy-class scale, mandatory/transitive precedence, completion spread, forced-run shape, losing-state share, and mechanic-rhythm diversity. It does not use seed, ID, authored band, object count, board dimensions, or occupancy as difficulty evidence.

## Auto Journey

Auto Journey begins at player-facing Level 2206 with internal identity `auto-journey-v1-{ordinal}`. Pure Kotlin generation/certification runs on `Dispatchers.Default`, prepares four boards (allowed range three to five), and persists the exact canonical catalog JSON, hashes, semantic fingerprints, band, seed/attempt, certificate receipt, and completion state before presentation. Process restart restores the identical stored board.

Collision increments the deterministic attempt and re-runs all gates. Durable history excludes the previous eight causal families and previous nineteen strategy clusters during generation, fallback selection, and transactional persistence. A pre-certified unused fallback may be used only when its fingerprints and pacing remain valid. If strict generation and the certified pool are exhausted, the app shows a retryable “next level is being prepared” state; it never presents an uncertified or duplicate board. Clearing storage/uninstall resets local history because cloud sync does not exist.

The current production attempt did not complete a qualifying campaign, so no V6.1 fallback pool is
promotable. Auto Journey therefore remains fail-closed at the V10 boundary rather than showing
uncertified content. The refreshed host diagnostic certified one bounded candidate in each active
band, with wall times from 84 ms to 4,111 ms and heap deltas from 4.1 MB to 173.3 MB; these are
host-JVM allocation deltas, not Android peak RSS. Device thermal and rendered-UI measurements remain
unavailable, so runtime readiness is not certified.

## Certification and promotion

Phase 1 depends on core, tool, app unit, release lint, release compilation, Android-test compilation,
release-asset exclusion, and merged-manifest checks. Phase 2 depends on the Phase 1 certificate and
both high-band capacity proofs. Each phase persists a catalog, complete audit, manifest, and
hash-bound non-promotable certificate. The final merge certificate includes both phase hashes and
counts, global duplicate and pacing audits, 2,205 replay results, regression results, and the
explicit statement that the campaign has not been human-validated.

Promotion re-verifies production, candidate, certificate, and manifest hashes; every boolean/count gate; exact board count/version; and the no-human-validation statement. It first preserves a V10 rollback artifact, copies to a sibling staging file, verifies it, and atomically moves only the production campaign. A rejected or incomplete certificate cannot reach the mutation code.

## 22 August 2026 bounded result

The graph-first Expert capacity proof accepted 24/24 boards against a 3,000-fingerprint archive.
Every board had at least six visible critical decisions, six persistent delayed traps, three phases,
two cross-chain dependencies, inferability 1.0, cheap-policy solve rate 0.0, forced run at most three,
and complete independent replay. All 24 synthesis graphs, production causal graphs, decision DAGs,
and solution policies were distinct. The proof then authorized one official full run, which advanced
past the former Level 94 Expert blocker and stopped at Level 98:

- generated: 85/2,193 non-tutorial boards (Easy 12, Medium 22, Hard 27, Super Hard 16, Expert 8);
- final failing slot: Level 98 Super Hard, `SHARED_MAGNET_PARITY_BRAID-v3`;
- final-slot attempts: 49 near-semantic duplicates and 15 forbidden recent strategy clusters;
- V6 hard-negative regression: passed (12/12 former high assignments demoted; 23/23 reported-guess rows rejected or capped);
- campaign status: `AUTOMATED_CAMPAIGN_REJECTED`;
- promotion: not invoked; V10 remains byte-identical.

The former Expert archive ceiling is therefore resolved for the required substantial sample and the
observed campaign prefix. The new limiting gate is Super Hard archive/cluster capacity. This is not
an engine or solver disagreement. The accepted partial archive has zero exact, D4, causal-graph,
decision-DAG, solution-policy, synthesis-graph, or near-threshold duplicates, but partial output
cannot certify. No threshold was lowered and no human-data gate was substituted.
