# Campaign V10 density-remediation report

- Status: **MECHANICALLY_CERTIFIED_STAGING**
- Human difficulty status: **PENDING_BLINDED_PLAYTEST**
- Preserved/remediated/final levels: 205/2000/2205
- Remediated arrow count: 14250 → 25509 (79.0% increase; every remediated board increased)
- Mean occupied cells: 41.4% → 66.9% (V10 range 50.0%–81.3%)
- Profile distribution: {v5-campaign-v10-easy-dense=334, v5-campaign-v10-expert-dense=333, v5-campaign-v10-hard-dense=333, v5-campaign-v10-master-dense=333, v5-campaign-v10-medium-dense=334, v5-campaign-v10-super-hard-dense=333}
- Cascade topology distribution: {HORIZONTAL_CASCADES=592, MIXED_CASCADES=692, VERTICAL_CASCADES=716}
- Full-board exact uniqueness: 2205/2205
- Full-board rotation/reflection uniqueness: 2205/2205
- Remediated arrow-silhouette uniqueness: 2000/2000
- Remediated interactive-layout uniqueness: 2000/2000
- Infinite exact/symmetry/arrow/interactive collisions: 0/0/0/0
- Content/generator version: 10/5
- Workers/retries: 10/2048
- Seed: 10200001

Levels 206–2,205 were rebuilt from their V9 certified puzzle cores. Each received a deterministic,
magnet-aware ordered arrow cascade, enough solution-preserving wall structure to satisfy its
density floor, a fresh production-engine solution witness and replay certification, and a V9
fingerprint migration link. Automated difficulty metrics are diagnostic until human approval;
wall-only and rotation/reflection variants are hard-rejected.

The six labels are candidates, not approved difficulty claims. A new blinded human playtest is
required because the submitted V9 study rejected the previous calibration.
