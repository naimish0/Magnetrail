# Phase 6 Infinite Mode QA

Status: PASS

## Catalog evidence

| Check | Result |
|---|---:|
| Requested candidates | 624 |
| Packaged certified candidates | 624 |
| Easy | 200 |
| Medium | 270 |
| Hard | 130 |
| Expert | 12 |
| Master | 12 |
| Campaign exact duplicates | 0 |
| Campaign symmetry duplicates | 0 |
| Pool exact duplicates | 0 |
| Pool symmetry duplicates | 0 |
| Generator candidate attempts for packaged rows | 2,606 |

Seventy logical Hard slots exhausted Hard generation and used certified Medium fallback boards.
This is recorded in the benchmark instead of silently relabeling the board metadata.

Aggregate packaged diagnostics: mean V4 score 38.13, safe-choice ratio 0.8164, ordering depth 2.34,
dependency depth 4.22, interaction density 0.3280, and relevant-object ratio 0.7233. These numbers
describe the mixed pool; certification is evaluated against each board's actual generation profile.

## Functional and safety checks

- Catalog parsing and catalog-policy validation pass.
- Same catalog/difficulty/ordinal selects the same stable ID.
- Selection version 3 deliberately chooses compact, single-solution certified Easy boards for the
  first five ordinals: arrow counts `2, 2, 2, 2, 3`, one valid opening action each, and no duplicate
  fingerprints. Guided Easy onboarding continues through Level 10. Animated lesson cards, a board-level
  fingertip, a printed-direction cue, and board focus appear for Progressive Levels 1–10 and numbered
  campaign Levels 1–10. A successful removal advances the pointer along a compatible authored solution;
  a failed tap does not. All coaching animations respect Reduced Motion and never consume board input.
- Progressive Journey requests Easy for 1–10, Medium for 11–20, Hard for 21–30, then enters the
  deterministic all-band mixed rhythm.
- Recent fingerprint avoidance is deterministic and bounded.
- Expert and Master requests select their certified bands; nearest-band fallback remains bounded.
- Super Hard is a visible choice and uses certified Expert when Hard and Expert are equally near;
  its slow exact constructor is not packaged or mislabeled.
- Selection and completion survive repository recreation through DataStore.
- Duplicate/replay completion for the same journey ordinal is idempotent and grants zero additional coins.
- First completion of each journey ordinal grants exactly 10 coins; a later ordinal remains independently
  rewardable even if the bounded selector eventually reuses the same certified board identity.
- Infinite completion does not change campaign completed IDs or campaign unlock.
- Home has one centered content stack: app name/tagline, `Play · Level N` with its actual selected
  difficulty on a second line, then Daily Challenge.
  A compact coin chip and icon-only Settings action occupy the top-left and top-right. Play immediately launches/resumes the
  Progressive selector; Continue, Level Select, and Progressive Journey cards are absent.
- Campaign and Daily modes retain their existing state paths.
- The app packages the separate Infinite asset; normal build does not generate content.
- Runtime contains no solver, generator, network, account, Billing, or ad requirement for selection.

## Commands

```text
./gradlew generateInfiniteCertifiedCatalog --configuration-cache
  BUILD SUCCESSFUL in 12m 22s (624/624 certified)

./gradlew :app:testDebugUnitTest --tests com.rameshta.magnetrail.GameViewModelTest \
  --tests com.rameshta.magnetrail.M3ProgressRepositoryTest \
  :game-core:test --tests com.rameshta.magnetrail.core.infinite.InfiniteModeTest \
  :level-tools:test --tests com.rameshta.magnetrail.tools.ContentArtifactsTest \
  --configuration-cache
  BUILD SUCCESSFUL
```

Full final build/test and campaign certification results are recorded in the Phase 6 handoff after
the final validation run:

```text
./gradlew :app:testDebugUnitTest :level-tools:test :game-core:test --configuration-cache
  BUILD SUCCESSFUL after correcting one obsolete rejection-stage assertion

./gradlew :app:assembleDebug --configuration-cache
  BUILD SUCCESSFUL in 2s

./gradlew certifyCampaignContent --configuration-cache
  Certified 205 campaign levels and 7 Daily fallbacks
  BUILD SUCCESSFUL in 2m 41s
```

The complete 142-test `:game-core:test` suite passed in 2m 11s after the final source edit. Focused
app persistence/ViewModel tests and the checked-in catalog artifact test also passed. The packaged
debug APK contains all 624 catalog entries, including 12 Expert and 12 Master rows.

## Campaign safety baseline

At Phase 6 validation the numbered campaign contains 205 levels and SHA-256:

```text
6416c0a5677e66cba169cf9caaa9d7d7e6e70bc6e4e3e69b36277e3c69e78128
```

The Infinite catalog is a separate pack and does not change campaign IDs or content.
