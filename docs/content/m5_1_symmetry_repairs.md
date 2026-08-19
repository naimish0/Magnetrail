# M5.1 staged symmetry repairs

Generated only after the first M5.1 audit identified hard D4-equivalent duplicates. The earliest campaign member of each group was preserved. Later members received the first deterministic wall-only adjustment that passed the production certification pipeline and remained unique under symmetry.

- Stable IDs changed: 0
- Campaign numbers/order changed: 0
- Boards tuned: 11

| # | ID | Walls before | Walls after | Symmetry fingerprint before | Symmetry fingerprint after |
|---:|---|---|---|---|---|
| 36 | campaign-036 | Position(row=1, column=1) | Position(row=1, column=1), Position(row=1, column=2) | `sha256:a9bbbfc920e02bde5e6937c5ed7a9bcc229021c0fbf2b90c359ca6988d9d408e` | `sha256:3dc56434275375729b637a227cec86bf4aa5abe2fa8f8169efb1afa6f83b990e` |
| 43 | campaign-043 |  | Position(row=1, column=1) | `sha256:64c0d0d26e807a99df23e46044dd72c317e9a061d5d6d36c8c2591925b73990f` | `sha256:42f9e84ce5ba5829dd1ce82fe95e392caaf4c5e4b06e45f18eef93a513815280` |
| 46 | campaign-046 | Position(row=2, column=3), Position(row=4, column=1), Position(row=1, column=6) | Position(row=2, column=3), Position(row=4, column=1), Position(row=1, column=6), Position(row=1, column=1) | `sha256:1a760caa9b0cbd1fce931dd78355aecad62fb46f11f133e75393f1d3d486455b` | `sha256:8f80f09fe35d652f41a30401da81831a60fcdf109e964b40406b49a92c0781e4` |
| 54 | campaign-054 |  | Position(row=1, column=1) | `sha256:40c880fe3a95ede89e78273a07b0af40520f236b53e27c775582631e1c9cf083` | `sha256:88880206cd5578c634ec8900ab28e2953f378b7fd5e8cf48d98d76e9fa68ba7c` |
| 57 | campaign-057 | Position(row=4, column=7), Position(row=7, column=4) | Position(row=4, column=7), Position(row=7, column=4), Position(row=1, column=1) | `sha256:86882c9110892e58b767e3d39a6980b15bec70867ecd69a1977629ef691fc445` | `sha256:826c231d65df6cef5a9cf08fb9e6465004730e792a23264c390239e9bbc32f59` |
| 70 | campaign-070 | Position(row=1, column=7) | Position(row=1, column=7), Position(row=1, column=1) | `sha256:a9bbbfc920e02bde5e6937c5ed7a9bcc229021c0fbf2b90c359ca6988d9d408e` | `sha256:176271e9d2e7442522fc126a8aebf8db59f481d949fc654f7140ee0abb41ccd3` |
| 73 | campaign-073 |  | Position(row=1, column=1) | `sha256:64c0d0d26e807a99df23e46044dd72c317e9a061d5d6d36c8c2591925b73990f` | `sha256:962a5e8264ff2d957a31be69dec7c01d87d77d6fcb0e345478f254fe4fb38183` |
| 78 | campaign-078 | Position(row=4, column=1) | Position(row=4, column=1), Position(row=1, column=3) | `sha256:a641c7061e71f3a83c68ebb1e0ee3769e36f0a63a06ac186bf5bb47dac1eec5e` | `sha256:1702e1f8aac73b9e619c853c3ebde2f3ab7663d3142b77e7595e820988c85d67` |
| 84 | campaign-084 | Position(row=5, column=3), Position(row=3, column=1), Position(row=6, column=6) | Position(row=5, column=3), Position(row=3, column=1), Position(row=6, column=6), Position(row=1, column=1) | `sha256:1a760caa9b0cbd1fce931dd78355aecad62fb46f11f133e75393f1d3d486455b` | `sha256:a814da849abffedb319c29364d201d42f3e3e991ef1f2479f2e757f05abda531` |
| 86 | campaign-086 | Position(row=7, column=4) | Position(row=7, column=4), Position(row=1, column=2) | `sha256:65dc6b8dd04f2193ce72357d77dc94b2f1a56c17845de4a85b6684a86ed6a36c` | `sha256:7a38d9c7540c0155b8c0feb49fe84be5449b422930e81790e51906036dc06971` |
| 92 | campaign-092 | Position(row=5, column=4), Position(row=3, column=6), Position(row=6, column=1) | Position(row=5, column=4), Position(row=3, column=6), Position(row=6, column=1), Position(row=1, column=1) | `sha256:1a760caa9b0cbd1fce931dd78355aecad62fb46f11f133e75393f1d3d486455b` | `sha256:7b7621c52519c5f4d79b7c228143b27a154693a9354203e01914817faf113934` |

## Manual review record

On 2026-08-19, all 11 changed boards were inspected as complete board grids after staging. Each added wall is in bounds, non-overlapping, visually legible, and does not create an accidental enclosure or misleading entity overlap. Production solver certification and clean-solution replay then passed for every changed board. The other 89 boards are content-identical to the prior release candidate and were reviewed through the refreshed per-level audit.
