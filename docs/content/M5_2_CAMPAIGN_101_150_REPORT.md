# Magnetrail M5.2 campaign 101–150 review report

All 50 levels received explicit project-owner approval on 2026-08-19 and are now promoted into the shipped catalog.

- Final count: 150 (100 unchanged + 50 promoted)
- Origin split for 101–150: {GENERATOR_ASSISTED=40, HANDCRAFTED=10}
- Board sizes: {7x7=42, 6x6=8}
- Difficulty V2: {Normal=3, Medium=32, Hard=15}
- Target-distribution note: Very Hard/Expert candidates were not manufactured by inflating density; the resulting target-band deviation was accepted during owner review.
- Quality: {ACCEPT=50}
- Sequence-aware Quality (M5.1 eight-level window): {ACCEPT=39, REVIEW=11}; local-structure REVIEW rows received explicit owner approval
- Mechanic tags: {PULL=50, POLARITY_FLIP=50, WALLS=50, CANCELLATION=2, MULTIPLE_MAGNETS=16, ORDER_DEPENDENCY=49, PUSH=48, OCCLUSION=4}
- Candidate pool: 200 viable from seed range 520001..520348 (5 viable candidates per generator-assisted slot)
- Candidate profiles: {m52-mastery-v1=87, m52-advanced-continuation-v1=113}
- Generator/content/analyzer versions: 2 / 4 / magnetrail-difficulty-v2.0
- Generation + certification time range: 0..11 ms
- Solver explored-state range: 7..64
- Runtime budgets: host catalog parsing <2,000 ms; connected 150-item lazy-grid discovery <10,000 ms; solver state caps 30,000/50,000 by profile
- Rejections: {quality-review:QUALITY_MECHANIC_CLAIM_UNSUPPORTED=64, required-magnetic-mechanic-unused=32, symmetry-duplicate-existing=83, symmetry-duplicate-pool=1}
- Remaining manual approvals: 0

Levels 1–100 are byte-for-byte domain copies with unchanged IDs, numbers, boards, metadata, and fingerprints. The promoted catalog uses stable IDs `campaign-101` through `campaign-150` and is consumed by normal app asset synchronization.
