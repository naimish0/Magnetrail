# Phase 0 proposed campaign remediation

Status: **PROPOSED — NOT HUMAN-APPROVED OR PROMOTED**

This is the required pre-promotion manifest. It maps every current stable level ID to one analyzed candidate. It does not modify the checked-in campaign, production metadata, fingerprints, or player data.

- Candidate pool analyzed: 450
- Proposed assignments: 150
- Decisions inherited from current-level diagnosis: {REPLACE=84, TUNE=66}
- Stable IDs/order preserved: 150/150
- Exact/symmetry fingerprints: 150/150
- Automated approvals: 0
- Human approvals: 0; every row remains `PENDING`.

| # | Stable ID | Decision | Candidate / origin | Target | Before→after v3 | Length / forced / decisions / spacing / max run | Choices P/I/V/F/G | Quality | Near | Review priority | Fingerprint change | Human |
|---:|---|---|---|---|---:|---|---|---|---:|---:|---|---|
| 1 | proto-001 | TUNE | phase0-tutorial-4750013 / GENERATOR_ASSISTED | phase0-tutorial | 3→10 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 68/REVIEW | 9 | 33 | `sha256:a4a91352…` → `sha256:b817f33d…` | PENDING |
| 2 | proto-002 | TUNE | phase0-tutorial-4750001 / GENERATOR_ASSISTED | phase0-tutorial | 17→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:6b97688e…` → `sha256:1555b7d1…` | PENDING |
| 3 | proto-003 | TUNE | phase0-tutorial-4750006 / GENERATOR_ASSISTED | phase0-tutorial | 11→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 68/REVIEW | 9 | 33 | `sha256:0c27c204…` → `sha256:aaf2b5d0…` | PENDING |
| 4 | proto-004 | TUNE | phase0-tutorial-4750004 / GENERATOR_ASSISTED | phase0-tutorial | 9→12 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:32ed043e…` → `sha256:55f09474…` | PENDING |
| 5 | proto-005 | TUNE | phase0-tutorial-4750015 / GENERATOR_ASSISTED | phase0-tutorial | 18→10 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 68/REVIEW | 9 | 33 | `sha256:9eb17aa1…` → `sha256:9efd985f…` | PENDING |
| 6 | proto-006 | TUNE | phase0-tutorial-4750003 / GENERATOR_ASSISTED | phase0-tutorial | 49→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:7c351916…` → `sha256:b44c7e05…` | PENDING |
| 7 | proto-007 | TUNE | phase0-tutorial-4750016 / GENERATOR_ASSISTED | phase0-tutorial | 17→12 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:924f1f2c…` → `sha256:167f55d7…` | PENDING |
| 8 | proto-008 | TUNE | phase0-tutorial-4750010 / GENERATOR_ASSISTED | phase0-tutorial | 14→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 68/REVIEW | 9 | 33 | `sha256:bc031205…` → `sha256:246e4474…` | PENDING |
| 9 | proto-009 | TUNE | phase0-tutorial-4750007 / GENERATOR_ASSISTED | phase0-tutorial | 49→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:293f0493…` → `sha256:6a7d39d0…` | PENDING |
| 10 | proto-010 | TUNE | phase0-tutorial-4750021 / GENERATOR_ASSISTED | phase0-tutorial | 40→11 | 1/1/0/1.0000/1 | 1/0/1/0/0 | 66/REVIEW | 9 | 33 | `sha256:d84fc050…` → `sha256:861f97e9…` | PENDING |
| 11 | proto-011 | REPLACE | phase0-easy-750102 / GENERATOR_ASSISTED | phase0-easy | 17→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:10e407e4…` → `sha256:7bef7f70…` | PENDING |
| 12 | proto-012 | TUNE | phase0-easy-750008 / GENERATOR_ASSISTED | phase0-easy | 67→21 | 3/2/0/3.0000/2 | 5/1/5/0/0 | 75/REVIEW | 3 | 27 | `sha256:85da95de…` → `sha256:108ed4ac…` | PENDING |
| 13 | campaign-013 | REPLACE | phase0-easy-750144 / GENERATOR_ASSISTED | phase0-easy | 11→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:19805bec…` → `sha256:572bb8b4…` | PENDING |
| 14 | campaign-014 | REPLACE | phase0-easy-750290 / GENERATOR_ASSISTED | phase0-easy | 9→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:a3f2ce27…` → `sha256:fd09f4e9…` | PENDING |
| 15 | campaign-015 | TUNE | phase0-easy-750340 / GENERATOR_ASSISTED | phase0-easy | 19→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:af67804c…` → `sha256:120b5080…` | PENDING |
| 16 | campaign-016 | REPLACE | phase0-easy-750009 / GENERATOR_ASSISTED | phase0-easy | 49→21 | 3/2/0/3.0000/2 | 5/1/5/0/0 | 75/REVIEW | 3 | 27 | `sha256:9bed21c8…` → `sha256:5e227f51…` | PENDING |
| 17 | campaign-017 | REPLACE | phase0-easy-750212 / GENERATOR_ASSISTED | phase0-easy | 17→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:91bf45c5…` → `sha256:4fd5fa1c…` | PENDING |
| 18 | campaign-018 | REPLACE | phase0-easy-750261 / GENERATOR_ASSISTED | phase0-easy | 14→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:b8833772…` → `sha256:2a1ef49b…` | PENDING |
| 19 | campaign-019 | TUNE | phase0-easy-750305 / GENERATOR_ASSISTED | phase0-easy | 49→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:dea80a33…` → `sha256:f71bc2f7…` | PENDING |
| 20 | campaign-020 | TUNE | phase0-easy-750385 / GENERATOR_ASSISTED | phase0-easy | 40→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:4257ba8c…` → `sha256:11d88d55…` | PENDING |
| 21 | campaign-021 | REPLACE | phase0-easy-750018 / GENERATOR_ASSISTED | phase0-easy | 17→21 | 3/2/0/3.0000/2 | 5/1/5/0/0 | 75/REVIEW | 3 | 27 | `sha256:b84de159…` → `sha256:55531533…` | PENDING |
| 22 | campaign-022 | TUNE | phase0-easy-750374 / GENERATOR_ASSISTED | phase0-easy | 67→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:bd5249b0…` → `sha256:c94c76b9…` | PENDING |
| 23 | campaign-023 | TUNE | phase0-easy-750414 / GENERATOR_ASSISTED | phase0-easy | 19→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:0c2bad71…` → `sha256:4440a18b…` | PENDING |
| 24 | campaign-024 | REPLACE | phase0-easy-750514 / GENERATOR_ASSISTED | phase0-easy | 50→19 | 2/1/0/2.0000/1 | 3/0/3/0/0 | 78/REVIEW | 10 | 30 | `sha256:473cabf0…` → `sha256:ecd706db…` | PENDING |
| 25 | campaign-025 | REPLACE | phase0-easy-750027 / GENERATOR_ASSISTED | phase0-easy | 17→21 | 3/2/0/3.0000/2 | 5/1/5/0/0 | 75/REVIEW | 3 | 27 | `sha256:da5100ab…` → `sha256:758edaf9…` | PENDING |
| 26 | campaign-026 | TUNE | phase0-planning-intro-3750184 / GENERATOR_ASSISTED | phase0-planning-intro | 72→43 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 85/REVIEW | 6 | 23 | `sha256:58a1bdf5…` → `sha256:178014dc…` | PENDING |
| 27 | campaign-027 | TUNE | phase0-planning-intro-3750014 / GENERATOR_ASSISTED | phase0-planning-intro | 72→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 85/REVIEW | 6 | 23 | `sha256:47c00fe9…` → `sha256:f401f503…` | PENDING |
| 28 | campaign-028 | TUNE | phase0-planning-intro-3751641 / GENERATOR_ASSISTED | phase0-planning-intro | 74→44 | 3/2/1/1.0000/2 | 4/2/4/0/0 | 79/REVIEW | 1 | 19 | `sha256:fc6d484b…` → `sha256:a32555b3…` | PENDING |
| 29 | campaign-029 | TUNE | phase0-planning-intro-3750304 / GENERATOR_ASSISTED | phase0-planning-intro | 74→43 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 85/REVIEW | 6 | 23 | `sha256:283d561e…` → `sha256:41535ff7…` | PENDING |
| 30 | campaign-030 | TUNE | phase0-planning-intro-3751362 / GENERATOR_ASSISTED | phase0-planning-intro | 63→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 85/REVIEW | 6 | 23 | `sha256:306df1ac…` → `sha256:ce984d65…` | PENDING |
| 31 | campaign-031 | TUNE | phase0-planning-intro-3750315 / GENERATOR_ASSISTED | phase0-planning-intro | 40→44 | 4/1/1/1.5000/1 | 10/0/10/0/0 | 78/REVIEW | 4 | 25 | `sha256:d7a102ba…` → `sha256:fd82d52e…` | PENDING |
| 32 | campaign-032 | TUNE | phase0-planning-intro-3750402 / GENERATOR_ASSISTED | phase0-planning-intro | 40→45 | 3/2/1/1.0000/2 | 5/1/5/0/0 | 79/REVIEW | 1 | 17 | `sha256:a7422438…` → `sha256:a77679c6…` | PENDING |
| 33 | campaign-033 | REPLACE | phase0-planning-intro-3751062 / GENERATOR_ASSISTED | phase0-planning-intro | 11→43 | 3/1/1/1.0000/1 | 5/1/5/0/0 | 85/REVIEW | 6 | 23 | `sha256:6ff39844…` → `sha256:590b3e81…` | PENDING |
| 34 | campaign-034 | REPLACE | phase0-planning-intro-3750233 / GENERATOR_ASSISTED | phase0-planning-intro | 11→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 77/REVIEW | 6 | 28 | `sha256:679786c1…` → `sha256:fb278c3e…` | PENDING |
| 35 | campaign-035 | REPLACE | phase0-planning-intro-3751331 / GENERATOR_ASSISTED | phase0-planning-intro | 17→44 | 4/1/1/1.5000/1 | 8/2/8/0/0 | 78/REVIEW | 4 | 25 | `sha256:09b0a746…` → `sha256:dc565105…` | PENDING |
| 36 | campaign-036 | REPLACE | phase0-planning-intro-3751403 / GENERATOR_ASSISTED | phase0-planning-intro | 19→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 77/REVIEW | 6 | 28 | `sha256:99b0066e…` → `sha256:3f2063f2…` | PENDING |
| 37 | campaign-037 | REPLACE | phase0-planning-intro-3750830 / GENERATOR_ASSISTED | phase0-planning-intro | 18→45 | 3/1/1/1.0000/1 | 5/1/5/0/0 | 86/REVIEW | 6 | 23 | `sha256:de7446eb…` → `sha256:8e89921e…` | PENDING |
| 38 | campaign-038 | REPLACE | phase0-planning-intro-3751025 / GENERATOR_ASSISTED | phase0-planning-intro | 17→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 79/REVIEW | 11 | 27 | `sha256:10b7b07d…` → `sha256:d005f5f4…` | PENDING |
| 39 | campaign-039 | TUNE | phase0-planning-intro-3750354 / GENERATOR_ASSISTED | phase0-planning-intro | 40→44 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 85/REVIEW | 11 | 23 | `sha256:82dea376…` → `sha256:16663a7c…` | PENDING |
| 40 | campaign-040 | TUNE | phase0-planning-intro-3750035 / GENERATOR_ASSISTED | phase0-planning-intro | 49→45 | 3/1/1/1.0000/1 | 6/0/6/0/0 | 81/REVIEW | 11 | 26 | `sha256:1bae7ab4…` → `sha256:fbe8723a…` | PENDING |
| 41 | campaign-041 | TUNE | phase0-medium-2750542 / GENERATOR_ASSISTED | phase0-medium | 40→45 | 4/1/1/1.5000/1 | 7/3/7/0/0 | 84/REVIEW | 3 | 19 | `sha256:c288970d…` → `sha256:60567eb8…` | PENDING |
| 42 | campaign-042 | REPLACE | phase0-medium-2751011 / GENERATOR_ASSISTED | phase0-medium | 50→45 | 4/2/1/1.5000/2 | 6/4/6/0/0 | 84/REVIEW | 2 | 17 | `sha256:4818842f…` → `sha256:989f4292…` | PENDING |
| 43 | campaign-043 | TUNE | phase0-medium-2750264 / GENERATOR_ASSISTED | phase0-medium | 40→58 | 4/2/2/0.6667/2 | 9/1/9/0/0 | 84/REVIEW | 5 | 21 | `sha256:063fd398…` → `sha256:fe1a3d68…` | PENDING |
| 44 | campaign-044 | REPLACE | phase0-medium-2751442 / GENERATOR_ASSISTED | phase0-medium | 18→58 | 4/1/2/0.6667/1 | 9/1/9/0/0 | 84/REVIEW | 5 | 22 | `sha256:2a8c4936…` → `sha256:8099722a…` | PENDING |
| 45 | campaign-045 | TUNE | phase0-medium-2751990 / GENERATOR_ASSISTED | phase0-medium | 63→59 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 84/REVIEW | 8 | 22 | `sha256:5c03744e…` → `sha256:515e9e3b…` | PENDING |
| 46 | campaign-046 | TUNE | phase0-medium-2750042 / GENERATOR_ASSISTED | phase0-medium | 49→48 | 4/1/1/1.5000/1 | 7/3/7/0/0 | 90/REVIEW | 3 | 16 | `sha256:45efad02…` → `sha256:758310ec…` | PENDING |
| 47 | campaign-047 | TUNE | phase0-medium-2751390 / GENERATOR_ASSISTED | phase0-medium | 49→59 | 4/2/2/0.6667/2 | 8/2/8/0/0 | 84/REVIEW | 3 | 17 | `sha256:ecd7a15a…` → `sha256:18579c07…` | PENDING |
| 48 | campaign-048 | REPLACE | phase0-medium-2751090 / GENERATOR_ASSISTED | phase0-medium | 49→59 | 4/1/2/0.6667/1 | 9/1/9/0/0 | 84/REVIEW | 3 | 18 | `sha256:92e32474…` → `sha256:d229d123…` | PENDING |
| 49 | campaign-049 | REPLACE | phase0-medium-2751152 / GENERATOR_ASSISTED | phase0-medium | 9→59 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 84/REVIEW | 2 | 15 | `sha256:e8fd3f4e…` → `sha256:e8681aa2…` | PENDING |
| 50 | campaign-050 | REPLACE | phase0-medium-2752753 / GENERATOR_ASSISTED | phase0-medium | 17→59 | 5/1/2/1.0000/1 | 11/4/11/0/0 | 84/REVIEW | 2 | 16 | `sha256:4cc80951…` → `sha256:79b08026…` | PENDING |
| 51 | campaign-051 | REPLACE | phase0-medium-2750304 / GENERATOR_ASSISTED | phase0-medium | 10→60 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 90/REVIEW | 6 | 18 | `sha256:bb4e2841…` → `sha256:30cb3b54…` | PENDING |
| 52 | campaign-052 | REPLACE | phase0-medium-2750504 / GENERATOR_ASSISTED | phase0-medium | 49→60 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 90/REVIEW | 6 | 18 | `sha256:113b5ce8…` → `sha256:182bdf09…` | PENDING |
| 53 | campaign-053 | TUNE | phase0-medium-2750550 / GENERATOR_ASSISTED | phase0-medium | 49→60 | 4/2/2/0.6667/2 | 8/2/8/0/0 | 88/REVIEW | 3 | 14 | `sha256:00f5e4d9…` → `sha256:34a0f0e4…` | PENDING |
| 54 | campaign-054 | REPLACE | phase0-medium-2750034 / GENERATOR_ASSISTED | phase0-medium | 17→60 | 4/1/2/0.6667/1 | 9/1/9/0/0 | 88/REVIEW | 3 | 16 | `sha256:7755ed5a…` → `sha256:0e220b9b…` | PENDING |
| 55 | campaign-055 | REPLACE | phase0-medium-2751164 / GENERATOR_ASSISTED | phase0-medium | 11→45 | 4/1/1/1.5000/1 | 7/3/7/0/0 | 85/REVIEW | 3 | 19 | `sha256:cfe70136…` → `sha256:daac44dd…` | PENDING |
| 56 | campaign-056 | TUNE | phase0-medium-2752342 / GENERATOR_ASSISTED | phase0-medium | 40→60 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 90/REVIEW | 6 | 18 | `sha256:54913bf9…` → `sha256:ffa15507…` | PENDING |
| 57 | campaign-057 | TUNE | phase0-medium-2751440 / GENERATOR_ASSISTED | phase0-medium | 67→60 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 84/REVIEW | 6 | 21 | `sha256:39b4d79d…` → `sha256:75f68155…` | PENDING |
| 58 | campaign-058 | TUNE | phase0-medium-2750664 / GENERATOR_ASSISTED | phase0-medium | 40→60 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 84/REVIEW | 5 | 22 | `sha256:fb793ace…` → `sha256:69be0a8a…` | PENDING |
| 59 | campaign-059 | REPLACE | phase0-medium-2752222 / GENERATOR_ASSISTED | phase0-medium | 49→59 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 84/REVIEW | 2 | 15 | `sha256:5515d7c7…` → `sha256:c883a2cb…` | PENDING |
| 60 | campaign-060 | TUNE | phase0-medium-2751072 / GENERATOR_ASSISTED | phase0-medium | 40→60 | 4/2/2/0.6667/2 | 8/2/8/0/0 | 88/REVIEW | 3 | 14 | `sha256:9640cf28…` → `sha256:465eb6c6…` | PENDING |
| 61 | campaign-061 | REPLACE | phase0-hard-1750272 / GENERATOR_ASSISTED | phase0-hard | 40→68 | 5/2/3/0.5000/2 | 11/4/11/0/0 | 94/ACCEPT | 0 | 14 | `sha256:4f131427…` → `sha256:dbd59160…` | PENDING |
| 62 | campaign-062 | REPLACE | phase0-hard-1752724 / GENERATOR_ASSISTED | phase0-hard | 49→69 | 5/1/3/0.5000/1 | 10/5/10/0/0 | 96/ACCEPT | 0 | 13 | `sha256:8ef891de…` → `sha256:d943ca58…` | PENDING |
| 63 | campaign-063 | REPLACE | phase0-hard-1750971 / GENERATOR_ASSISTED | phase0-hard | 40→71 | 5/1/3/0.5000/1 | 14/1/14/0/0 | 84/REVIEW | 1 | 12 | `sha256:85a9812a…` → `sha256:bc1e6d67…` | PENDING |
| 64 | campaign-064 | REPLACE | phase0-hard-1751660 / GENERATOR_ASSISTED | phase0-hard | 49→71 | 5/2/3/0.5000/2 | 14/1/14/0/0 | 94/ACCEPT | 0 | 20 | `sha256:f4e9d9be…` → `sha256:608d8698…` | PENDING |
| 65 | campaign-065 | REPLACE | phase0-hard-1750332 / GENERATOR_ASSISTED | phase0-hard | 50→71 | 5/2/3/0.5000/2 | 11/4/11/0/0 | 96/ACCEPT | 0 | 15 | `sha256:b3d56094…` → `sha256:3fe2bc07…` | PENDING |
| 66 | campaign-066 | REPLACE | phase0-hard-1750985 / GENERATOR_ASSISTED | phase0-hard | 17→71 | 5/2/3/0.5000/2 | 11/4/11/0/0 | 94/ACCEPT | 0 | 15 | `sha256:2016d48d…` → `sha256:37504d5a…` | PENDING |
| 67 | campaign-067 | REPLACE | phase0-hard-1751370 / GENERATOR_ASSISTED | phase0-hard | 9→71 | 5/1/3/0.5000/1 | 14/1/14/0/0 | 94/ACCEPT | 0 | 15 | `sha256:d1ab7666…` → `sha256:24a89d48…` | PENDING |
| 68 | campaign-068 | REPLACE | phase0-hard-1751141 / GENERATOR_ASSISTED | phase0-hard | 17→71 | 4/1/3/0.2500/1 | 9/1/9/0/0 | 96/ACCEPT | 0 | 16 | `sha256:10153d24…` → `sha256:1808bda6…` | PENDING |
| 69 | campaign-069 | REPLACE | phase0-hard-1751425 / GENERATOR_ASSISTED | phase0-hard | 19→71 | 5/1/3/0.5000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 16 | `sha256:e7f292ae…` → `sha256:4d601cf2…` | PENDING |
| 70 | campaign-070 | REPLACE | phase0-hard-1751685 / GENERATOR_ASSISTED | phase0-hard | 19→71 | 5/2/3/0.5000/2 | 11/4/11/0/0 | 94/ACCEPT | 0 | 16 | `sha256:cade45fb…` → `sha256:748f9944…` | PENDING |
| 71 | campaign-071 | REPLACE | phase0-hard-1752674 / GENERATOR_ASSISTED | phase0-hard | 49→71 | 4/1/3/0.2500/1 | 10/0/10/0/0 | 94/ACCEPT | 0 | 19 | `sha256:fee411ba…` → `sha256:6283c6e4…` | PENDING |
| 72 | campaign-072 | REPLACE | phase0-hard-1750934 / GENERATOR_ASSISTED | phase0-hard | 40→72 | 5/1/3/0.5000/1 | 15/0/15/0/0 | 97/ACCEPT | 0 | 14 | `sha256:438c53fd…` → `sha256:e4097895…` | PENDING |
| 73 | campaign-073 | REPLACE | phase0-hard-1751605 / GENERATOR_ASSISTED | phase0-hard | 40→73 | 5/2/3/0.5000/1 | 11/4/10/1/0 | 94/ACCEPT | 0 | 14 | `sha256:7c0c5bbe…` → `sha256:17a9571b…` | PENDING |
| 74 | campaign-074 | TUNE | phase0-hard-1750262 / GENERATOR_ASSISTED | phase0-hard | 72→73 | 5/1/3/0.5000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 16 | `sha256:05e03854…` → `sha256:3f170747…` | PENDING |
| 75 | campaign-075 | TUNE | phase0-hard-1751974 / GENERATOR_ASSISTED | phase0-hard | 72→74 | 5/1/3/0.5000/1 | 14/1/14/0/0 | 94/ACCEPT | 0 | 15 | `sha256:b7a560e3…` → `sha256:09a30518…` | PENDING |
| 76 | campaign-076 | TUNE | phase0-hard-1752570 / GENERATOR_ASSISTED | phase0-hard | 75→74 | 5/1/3/0.5000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 15 | `sha256:e0897825…` → `sha256:405e01cc…` | PENDING |
| 77 | campaign-077 | REPLACE | phase0-hard-1750914 / GENERATOR_ASSISTED | phase0-hard | 49→74 | 5/1/3/0.5000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 16 | `sha256:358be07d…` → `sha256:9dc3f1a1…` | PENDING |
| 78 | campaign-078 | TUNE | phase0-hard-1751573 / GENERATOR_ASSISTED | phase0-hard | 72→74 | 5/1/4/0.2000/1 | 11/4/11/0/0 | 94/ACCEPT | 0 | 16 | `sha256:8644e16e…` → `sha256:1886e334…` | PENDING |
| 79 | campaign-079 | TUNE | phase0-hard-1752362 / GENERATOR_ASSISTED | phase0-hard | 75→74 | 5/1/4/0.2000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 21 | `sha256:8a2d1edf…` → `sha256:64d2700d…` | PENDING |
| 80 | campaign-080 | TUNE | phase0-hard-1751703 / GENERATOR_ASSISTED | phase0-hard | 76→75 | 5/1/4/0.2000/1 | 12/3/12/0/0 | 94/ACCEPT | 0 | 15 | `sha256:ccad5783…` → `sha256:5486a104…` | PENDING |
| 81 | campaign-081 | REPLACE | phase0-very-hard-8750131 / GENERATOR_ASSISTED | phase0-very-hard | 63→77 | 6/1/4/0.4000/1 | 18/3/18/0/0 | 96/ACCEPT | 0 | 19 | `sha256:4042170f…` → `sha256:b5299c6c…` | PENDING |
| 82 | campaign-082 | REPLACE | phase0-very-hard-8750002 / GENERATOR_ASSISTED | phase0-very-hard | 75→77 | 6/1/5/0.1667/1 | 20/1/20/0/0 | 96/ACCEPT | 0 | 20 | `sha256:f0d47d29…` → `sha256:fc7153a4…` | PENDING |
| 83 | campaign-083 | REPLACE | phase0-very-hard-8750220 / GENERATOR_ASSISTED | phase0-very-hard | 75→78 | 6/2/4/0.4000/2 | 17/4/17/0/0 | 97/ACCEPT | 0 | 17 | `sha256:b6ce6640…` → `sha256:fd07f5e5…` | PENDING |
| 84 | campaign-084 | REPLACE | phase0-very-hard-8750122 / GENERATOR_ASSISTED | phase0-very-hard | 49→78 | 6/1/5/0.1667/1 | 17/4/17/0/0 | 94/ACCEPT | 0 | 21 | `sha256:5728d3bf…` → `sha256:643095a4…` | PENDING |
| 85 | campaign-085 | REPLACE | phase0-very-hard-8750184 / GENERATOR_ASSISTED | phase0-very-hard | 72→79 | 6/1/4/0.4000/1 | 21/0/21/0/0 | 97/ACCEPT | 0 | 15 | `sha256:19b2d83a…` → `sha256:c642525a…` | PENDING |
| 86 | campaign-086 | REPLACE | phase0-very-hard-8750073 / GENERATOR_ASSISTED | phase0-very-hard | 74→79 | 6/1/5/0.1667/1 | 21/0/21/0/0 | 97/ACCEPT | 0 | 18 | `sha256:3bd846b8…` → `sha256:6065afd5…` | PENDING |
| 87 | campaign-087 | REPLACE | phase0-very-hard-8750153 / GENERATOR_ASSISTED | phase0-very-hard | 74→80 | 7/1/6/0.1429/1 | 25/3/25/0/0 | 97/ACCEPT | 0 | 23 | `sha256:50416246…` → `sha256:e69ee781…` | PENDING |
| 88 | campaign-088 | REPLACE | phase0-very-hard-8750200 / GENERATOR_ASSISTED | phase0-very-hard | 75→81 | 7/1/6/0.1429/1 | 24/4/24/0/0 | 100/ACCEPT | 0 | 21 | `sha256:2956483f…` → `sha256:7342c4bb…` | PENDING |
| 89 | campaign-089 | REPLACE | phase0-very-hard-8750021 / GENERATOR_ASSISTED | phase0-very-hard | 40→81 | 7/1/6/0.1429/1 | 27/1/27/0/0 | 98/ACCEPT | 0 | 23 | `sha256:e956301b…` → `sha256:75dd3301…` | PENDING |
| 90 | campaign-090 | REPLACE | phase0-very-hard-8750150 / GENERATOR_ASSISTED | phase0-very-hard | 49→82 | 7/1/6/0.1429/1 | 21/7/21/0/0 | 97/ACCEPT | 0 | 20 | `sha256:aa8da70c…` → `sha256:d4f8b3bc…` | PENDING |
| 91 | campaign-091 | REPLACE | phase0-very-hard-8750143 / GENERATOR_ASSISTED | phase0-very-hard | 75→83 | 7/1/6/0.1429/1 | 27/1/27/0/0 | 98/ACCEPT | 0 | 21 | `sha256:8d25ab38…` → `sha256:6cf379e9…` | PENDING |
| 92 | campaign-092 | REPLACE | phase0-very-hard-8750044 / GENERATOR_ASSISTED | phase0-very-hard | 49→83 | 7/1/6/0.1429/1 | 27/1/27/0/0 | 96/ACCEPT | 0 | 24 | `sha256:e08a118e…` → `sha256:c04858c3…` | PENDING |
| 93 | campaign-093 | REPLACE | phase0-very-hard-8750101 / GENERATOR_ASSISTED | phase0-very-hard | 49→85 | 6/1/4/0.4000/1 | 19/2/18/1/0 | 97/ACCEPT | 0 | 20 | `sha256:9cda0426…` → `sha256:bc838817…` | PENDING |
| 94 | campaign-094 | REPLACE | phase0-very-hard-8750015 / GENERATOR_ASSISTED | phase0-very-hard | 72→86 | 6/1/5/0.1667/1 | 13/8/12/1/0 | 96/ACCEPT | 0 | 19 | `sha256:89445517…` → `sha256:2c955f51…` | PENDING |
| 95 | campaign-095 | REPLACE | phase0-very-hard-8750108 / GENERATOR_ASSISTED | phase0-very-hard | 49→86 | 7/1/6/0.1429/1 | 25/3/24/1/0 | 98/ACCEPT | 0 | 20 | `sha256:848ca089…` → `sha256:18fa97d1…` | PENDING |
| 96 | campaign-096 | REPLACE | phase0-very-hard-8750185 / GENERATOR_ASSISTED | phase0-very-hard | 63→86 | 7/1/6/0.1429/1 | 20/8/19/1/0 | 96/ACCEPT | 0 | 23 | `sha256:28e152c0…` → `sha256:9f9b80f9…` | PENDING |
| 97 | campaign-097 | REPLACE | phase0-very-hard-8750005 / GENERATOR_ASSISTED | phase0-very-hard | 63→88 | 6/2/5/0.1667/2 | 20/1/15/5/0 | 94/ACCEPT | 0 | 27 | `sha256:07f928ec…` → `sha256:29294678…` | PENDING |
| 98 | campaign-098 | REPLACE | phase0-very-hard-8750077 / GENERATOR_ASSISTED | phase0-very-hard | 75→88 | 6/1/5/0.1667/1 | 17/4/16/1/0 | 100/ACCEPT | 0 | 19 | `sha256:78c2eb43…` → `sha256:e529d9b6…` | PENDING |
| 99 | campaign-099 | REPLACE | phase0-very-hard-8750093 / GENERATOR_ASSISTED | phase0-very-hard | 40→90 | 7/1/5/0.3333/1 | 20/8/18/2/0 | 97/ACCEPT | 0 | 23 | `sha256:6fbf9e84…` → `sha256:3ce53084…` | PENDING |
| 100 | campaign-100 | REPLACE | phase0-very-hard-8750142 / GENERATOR_ASSISTED | phase0-very-hard | 49→90 | 7/1/6/0.1429/1 | 24/4/22/2/0 | 98/ACCEPT | 0 | 25 | `sha256:fec7f109…` → `sha256:910613e0…` | PENDING |
| 101 | campaign-101 | TUNE | phase0-upper-hard-5750742 / GENERATOR_ASSISTED | phase0-upper-hard | 63→71 | 6/1/3/0.7500/1 | 11/10/11/0/0 | 96/ACCEPT | 0 | 15 | `sha256:e7d54fb4…` → `sha256:c862fbea…` | PENDING |
| 102 | campaign-102 | TUNE | phase0-upper-hard-5750725 / GENERATOR_ASSISTED | phase0-upper-hard | 77→72 | 5/1/3/0.5000/1 | 14/1/14/0/0 | 94/ACCEPT | 0 | 18 | `sha256:35f345d4…` → `sha256:2c4a3cbb…` | PENDING |
| 103 | campaign-103 | TUNE | phase0-upper-hard-5750094 / GENERATOR_ASSISTED | phase0-upper-hard | 74→74 | 5/2/3/0.5000/2 | 13/2/13/0/0 | 94/ACCEPT | 0 | 17 | `sha256:64e5f5d8…` → `sha256:ab89b54f…` | PENDING |
| 104 | campaign-104 | TUNE | phase0-upper-hard-5750351 / GENERATOR_ASSISTED | phase0-upper-hard | 77→75 | 6/1/4/0.4000/1 | 15/6/15/0/0 | 94/ACCEPT | 0 | 18 | `sha256:d31387fb…` → `sha256:51febc53…` | PENDING |
| 105 | campaign-105 | REPLACE | phase0-upper-peak-6750045 / GENERATOR_ASSISTED | phase0-upper-peak | 67→77 | 7/1/5/0.3333/1 | 18/10/18/0/0 | 98/ACCEPT | 0 | 13 | `sha256:0371a296…` → `sha256:46bb3e8b…` | PENDING |
| 106 | campaign-106 | TUNE | phase0-upper-recovery-7750430 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→64 | 4/2/2/0.6667/2 | 9/1/9/0/0 | 90/REVIEW | 2 | 14 | `sha256:e93b1301…` → `sha256:d3a29583…` | PENDING |
| 107 | campaign-107 | TUNE | phase0-upper-hard-5750077 / GENERATOR_ASSISTED | phase0-upper-hard | 72→75 | 5/2/3/0.5000/1 | 9/6/8/1/0 | 96/ACCEPT | 0 | 13 | `sha256:c8d9a996…` → `sha256:10f0fa6d…` | PENDING |
| 108 | campaign-108 | TUNE | phase0-upper-hard-5750750 / GENERATOR_ASSISTED | phase0-upper-hard | 72→76 | 6/1/4/0.4000/1 | 18/3/18/0/0 | 94/ACCEPT | 0 | 17 | `sha256:1ff5c563…` → `sha256:3613fbb4…` | PENDING |
| 109 | campaign-109 | REPLACE | phase0-upper-hard-5750180 / GENERATOR_ASSISTED | phase0-upper-hard | 49→76 | 6/2/4/0.4000/2 | 16/5/16/0/0 | 96/ACCEPT | 0 | 18 | `sha256:01686557…` → `sha256:54c5a4fe…` | PENDING |
| 110 | campaign-110 | REPLACE | phase0-upper-peak-6750031 / GENERATOR_ASSISTED | phase0-upper-peak | 84→78 | 7/2/5/0.3333/2 | 23/5/23/0/0 | 98/ACCEPT | 0 | 16 | `sha256:05d8178f…` → `sha256:457b1cc6…` | PENDING |
| 111 | campaign-111 | REPLACE | phase0-upper-hard-5750353 / GENERATOR_ASSISTED | phase0-upper-hard | 49→76 | 5/1/4/0.2000/1 | 12/3/12/0/0 | 96/ACCEPT | 0 | 16 | `sha256:2d61f523…` → `sha256:8d4488a6…` | PENDING |
| 112 | campaign-112 | TUNE | phase0-upper-recovery-7750024 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→62 | 4/2/2/0.6667/1 | 7/3/6/1/0 | 88/REVIEW | 2 | 14 | `sha256:1317b9be…` → `sha256:e0a50872…` | PENDING |
| 113 | campaign-113 | REPLACE | phase0-upper-hard-5750553 / GENERATOR_ASSISTED | phase0-upper-hard | 49→76 | 6/1/5/0.1667/1 | 20/1/20/0/0 | 97/ACCEPT | 0 | 17 | `sha256:e384d1f0…` → `sha256:a40381f4…` | PENDING |
| 114 | campaign-114 | REPLACE | phase0-upper-hard-5750672 / GENERATOR_ASSISTED | phase0-upper-hard | 49→76 | 6/1/5/0.1667/1 | 21/0/21/0/0 | 97/ACCEPT | 0 | 18 | `sha256:ca4ae9ac…` → `sha256:12ea5315…` | PENDING |
| 115 | campaign-115 | REPLACE | phase0-upper-peak-6750101 / GENERATOR_ASSISTED | phase0-upper-peak | 67→79 | 6/2/4/0.4000/1 | 14/7/13/1/0 | 94/ACCEPT | 0 | 16 | `sha256:0041f25d…` → `sha256:e3da7aee…` | PENDING |
| 116 | campaign-116 | REPLACE | phase0-upper-hard-5750091 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 6/1/5/0.1667/1 | 17/4/17/0/0 | 97/ACCEPT | 0 | 18 | `sha256:f57b2a53…` → `sha256:32aeb6ff…` | PENDING |
| 117 | campaign-117 | REPLACE | phase0-upper-hard-5750583 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 6/1/5/0.1667/1 | 15/6/15/0/0 | 97/ACCEPT | 0 | 14 | `sha256:ab68018b…` → `sha256:18eecd9c…` | PENDING |
| 118 | campaign-118 | TUNE | phase0-upper-recovery-7750110 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→65 | 4/1/3/0.2500/1 | 9/1/9/0/0 | 84/REVIEW | 1 | 13 | `sha256:f8cc3e31…` → `sha256:b44710bf…` | PENDING |
| 119 | campaign-119 | REPLACE | phase0-upper-hard-5750120 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 6/2/4/0.4000/2 | 17/4/17/0/0 | 97/ACCEPT | 0 | 15 | `sha256:fe3a20d0…` → `sha256:f9f8a071…` | PENDING |
| 120 | campaign-120 | REPLACE | phase0-upper-peak-6750095 / GENERATOR_ASSISTED | phase0-upper-peak | 68→81 | 7/1/6/0.1429/1 | 22/6/22/0/0 | 96/ACCEPT | 0 | 18 | `sha256:bb54218b…` → `sha256:064905e1…` | PENDING |
| 121 | campaign-121 | REPLACE | phase0-upper-hard-5750083 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 5/1/4/0.2000/1 | 12/3/12/0/0 | 94/ACCEPT | 0 | 16 | `sha256:5cad8367…` → `sha256:318eb21a…` | PENDING |
| 122 | campaign-122 | REPLACE | phase0-upper-hard-5750692 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 5/1/4/0.2000/1 | 14/1/14/0/0 | 94/ACCEPT | 0 | 18 | `sha256:a03854b1…` → `sha256:477a0bec…` | PENDING |
| 123 | campaign-123 | TUNE | phase0-upper-hard-5750043 / GENERATOR_ASSISTED | phase0-upper-hard | 67→77 | 5/1/4/0.2000/1 | 14/1/14/0/0 | 94/ACCEPT | 0 | 20 | `sha256:420d1508…` → `sha256:01519605…` | PENDING |
| 124 | campaign-124 | TUNE | phase0-upper-recovery-7750224 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→65 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 90/REVIEW | 2 | 14 | `sha256:b47c7cf4…` → `sha256:55f26421…` | PENDING |
| 125 | campaign-125 | REPLACE | phase0-upper-peak-6750002 / GENERATOR_ASSISTED | phase0-upper-peak | 63→82 | 7/1/5/0.3333/1 | 21/7/21/0/0 | 96/ACCEPT | 0 | 21 | `sha256:0131a264…` → `sha256:ddbac337…` | PENDING |
| 126 | campaign-126 | REPLACE | phase0-upper-hard-5750700 / GENERATOR_ASSISTED | phase0-upper-hard | 49→77 | 6/2/4/0.4000/2 | 19/2/19/0/0 | 94/ACCEPT | 0 | 21 | `sha256:cf5bd327…` → `sha256:48649e50…` | PENDING |
| 127 | campaign-127 | TUNE | phase0-upper-hard-5750274 / GENERATOR_ASSISTED | phase0-upper-hard | 67→78 | 6/2/4/0.4000/2 | 17/4/17/0/0 | 94/ACCEPT | 0 | 17 | `sha256:343a5ac7…` → `sha256:667f5299…` | PENDING |
| 128 | campaign-128 | REPLACE | phase0-upper-hard-5750053 / GENERATOR_ASSISTED | phase0-upper-hard | 49→78 | 6/1/4/0.4000/1 | 18/3/18/0/0 | 96/ACCEPT | 0 | 15 | `sha256:dd9a41d9…` → `sha256:6910fc11…` | PENDING |
| 129 | campaign-129 | TUNE | phase0-upper-hard-5750452 / GENERATOR_ASSISTED | phase0-upper-hard | 67→78 | 6/1/4/0.4000/1 | 21/0/21/0/0 | 96/ACCEPT | 0 | 16 | `sha256:e9f9bbc0…` → `sha256:5d82d55f…` | PENDING |
| 130 | campaign-130 | REPLACE | phase0-upper-peak-6750044 / GENERATOR_ASSISTED | phase0-upper-peak | 67→84 | 7/1/6/0.1429/1 | 21/7/21/0/0 | 98/ACCEPT | 0 | 20 | `sha256:cd1670ce…` → `sha256:dff70638…` | PENDING |
| 131 | campaign-131 | TUNE | phase0-upper-recovery-7750182 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→62 | 4/2/2/0.6667/1 | 7/3/6/1/0 | 84/REVIEW | 2 | 16 | `sha256:0a11151f…` → `sha256:aecef39a…` | PENDING |
| 132 | campaign-132 | REPLACE | phase0-upper-hard-5750334 / GENERATOR_ASSISTED | phase0-upper-hard | 49→78 | 5/2/4/0.2000/1 | 11/4/10/1/0 | 94/ACCEPT | 0 | 15 | `sha256:a4654d5e…` → `sha256:af1467c0…` | PENDING |
| 133 | campaign-133 | TUNE | phase0-upper-hard-5750011 / GENERATOR_ASSISTED | phase0-upper-hard | 67→78 | 6/1/5/0.1667/1 | 17/4/17/0/0 | 97/ACCEPT | 0 | 17 | `sha256:cdf78017…` → `sha256:6c19e6b6…` | PENDING |
| 134 | campaign-134 | REPLACE | phase0-upper-hard-5750642 / GENERATOR_ASSISTED | phase0-upper-hard | 49→78 | 6/1/5/0.1667/1 | 17/4/17/0/0 | 97/ACCEPT | 0 | 17 | `sha256:777d0d16…` → `sha256:2aa0ff1c…` | PENDING |
| 135 | campaign-135 | REPLACE | phase0-upper-peak-6750077 / GENERATOR_ASSISTED | phase0-upper-peak | 69→86 | 6/1/5/0.1667/1 | 15/6/14/1/0 | 98/ACCEPT | 0 | 19 | `sha256:97d803e6…` → `sha256:7ff8eb0d…` | PENDING |
| 136 | campaign-136 | REPLACE | phase0-upper-hard-5750735 / GENERATOR_ASSISTED | phase0-upper-hard | 49→78 | 6/1/5/0.1667/1 | 17/4/17/0/0 | 94/ACCEPT | 0 | 19 | `sha256:e4961721…` → `sha256:973fa822…` | PENDING |
| 137 | campaign-137 | TUNE | phase0-upper-recovery-7750512 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→61 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 88/REVIEW | 6 | 18 | `sha256:f45358f5…` → `sha256:0e422782…` | PENDING |
| 138 | campaign-138 | REPLACE | phase0-upper-hard-5750402 / GENERATOR_ASSISTED | phase0-upper-hard | 49→78 | 6/2/4/0.4000/2 | 17/4/17/0/0 | 94/ACCEPT | 0 | 20 | `sha256:6fecd89c…` → `sha256:e0605ba7…` | PENDING |
| 139 | campaign-139 | TUNE | phase0-upper-hard-5750650 / GENERATOR_ASSISTED | phase0-upper-hard | 67→78 | 5/1/4/0.2000/1 | 15/0/15/0/0 | 94/ACCEPT | 0 | 18 | `sha256:db991bc0…` → `sha256:62bce7f2…` | PENDING |
| 140 | campaign-140 | REPLACE | phase0-upper-peak-6750066 / GENERATOR_ASSISTED | phase0-upper-peak | 84→88 | 6/1/4/0.4000/1 | 20/1/17/3/0 | 94/ACCEPT | 0 | 20 | `sha256:1e1fa20b…` → `sha256:9e7e2c8b…` | PENDING |
| 141 | campaign-141 | TUNE | phase0-upper-hard-5750555 / GENERATOR_ASSISTED | phase0-upper-hard | 72→78 | 6/2/4/0.4000/2 | 18/3/18/0/0 | 96/ACCEPT | 0 | 19 | `sha256:283299b8…` → `sha256:ce5ada4a…` | PENDING |
| 142 | campaign-142 | TUNE | phase0-upper-hard-5750480 / GENERATOR_ASSISTED | phase0-upper-hard | 72→78 | 6/1/5/0.1667/1 | 21/0/21/0/0 | 96/ACCEPT | 0 | 20 | `sha256:4a886263…` → `sha256:5932d7fb…` | PENDING |
| 143 | campaign-143 | TUNE | phase0-upper-recovery-7750282 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→62 | 4/1/2/0.6667/1 | 7/3/7/0/0 | 90/REVIEW | 8 | 18 | `sha256:d541e42b…` → `sha256:2f67878a…` | PENDING |
| 144 | campaign-144 | TUNE | phase0-upper-hard-5750381 / GENERATOR_ASSISTED | phase0-upper-hard | 72→79 | 6/2/4/0.4000/2 | 18/3/18/0/0 | 94/ACCEPT | 0 | 21 | `sha256:a1b36a42…` → `sha256:9e28215f…` | PENDING |
| 145 | campaign-145 | REPLACE | phase0-upper-peak-6750087 / GENERATOR_ASSISTED | phase0-upper-peak | 72→88 | 6/1/5/0.1667/1 | 20/1/17/3/0 | 97/ACCEPT | 0 | 23 | `sha256:e1867b66…` → `sha256:f5da2e95…` | PENDING |
| 146 | campaign-146 | TUNE | phase0-upper-hard-5750222 / GENERATOR_ASSISTED | phase0-upper-hard | 72→79 | 6/1/4/0.4000/1 | 16/5/16/0/0 | 94/ACCEPT | 0 | 18 | `sha256:25a1d464…` → `sha256:ae93cb17…` | PENDING |
| 147 | campaign-147 | TUNE | phase0-upper-hard-5750474 / GENERATOR_ASSISTED | phase0-upper-hard | 72→80 | 6/2/4/0.4000/1 | 14/7/13/1/0 | 94/ACCEPT | 0 | 15 | `sha256:81ff1d88…` → `sha256:1ec7bc62…` | PENDING |
| 148 | campaign-148 | TUNE | phase0-upper-recovery-7750392 / GENERATOR_ASSISTED | phase0-upper-recovery | 49→65 | 4/1/2/0.6667/1 | 10/0/10/0/0 | 90/REVIEW | 2 | 14 | `sha256:c068bb55…` → `sha256:f61b2d8d…` | PENDING |
| 149 | campaign-149 | TUNE | phase0-upper-hard-5750202 / GENERATOR_ASSISTED | phase0-upper-hard | 72→80 | 6/2/4/0.4000/2 | 15/6/15/0/0 | 94/ACCEPT | 0 | 18 | `sha256:562df2df…` → `sha256:ce40f91a…` | PENDING |
| 150 | campaign-150 | REPLACE | phase0-upper-peak-6750015 / GENERATOR_ASSISTED | phase0-upper-peak | 84→83 | 6/2/4/0.4000/2 | 13/8/12/1/0 | 96/ACCEPT | 0 | 16 | `sha256:a9f57511…` → `sha256:9116f497…` | PENDING |

Promotion remains blocked until the owner reviews this mapping, representative boards from every band are playtested, every higher-band near neighbor is justified or replaced, and the fingerprint-aware best-record migration is implemented and proven safe.
