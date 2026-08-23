# Generator V6 baseline audit

Status: **BASELINE_VERIFIED**

## Protected catalogs

| Catalog | Levels | SHA-256 verified | Exact | D4 | Arrow | Interactive | Perceptual |
|---|---:|:---:|---:|---:|---:|---:|---:|
| Campaign V10 | 2205 | yes | 2205 | 2205 | 2205 | 2205 | 2205 |
| Infinite V1 | 624 | yes | 624 | 624 | 570 | 602 | 619 |
| Daily fallback V1 | 7 | yes | 7 | 7 | 3 | 3 | 7 |
| V11 pilot | 53 | yes | 53 | 53 | 53 | 53 | 53 |

## D4 contradiction audit

- idsAndMetadata: removed
- entityOrdering: canonical sort
- coordinates: all eight D4 transforms
- directions: transformed with geometry
- rectangles: 90-degree/diagonal transforms swap serialized width and height
- magnetPolarity: included
- walls: included in full-board D4

The pre-V6 rectangular implementation used only four dimension-preserving transforms. V6 serializes transformed dimensions and proves all eight transforms, including direction changes and width/height swaps.

## Clone cause

- canonicalJson: protected bytes match expected V10 and parse as 2,205 distinct levels
- runtimeBoardState: LevelParser creates independent immutable BoardState objects from the selected LevelDefinition
- renderedInteractableBoard: Compose consumes current BoardState and emits arrow IDs; it does not transform or cache level geometry
- catalogSelection: AssetLevelCatalog selects stable IDs from the canonical asset; no evidence of wrong-catalog substitution
- cause: literal shared V9 cores plus semantic equivalence beyond exact/D4/arrow/interactive fingerprints

- `D4 arrow+magnet interactive core` `sha256:0e4899da552670818db6bbb16db641e0423adc46cb3fae4efec2c4ae9d0d466a`: campaign-1103, campaign-1122, campaign-1182, campaign-1284, campaign-1350, campaign-1404, campaign-1427, campaign-1631, campaign-1781, campaign-1872, campaign-1997, campaign-257, campaign-269, campaign-426, campaign-497, campaign-551, campaign-629, campaign-744, campaign-755, campaign-894, campaign-924, campaign-984. V10 rebuilt density around a shared V9 interaction core; walls/cascades changed full layouts but not the underlying strategy template.
- `D4 arrow+magnet interactive core` `sha256:4b230c4a7c95a5243492886bbf89d9efe94bca8dbe9d9175fef35de17882ef5f`: campaign-1129, campaign-1249, campaign-1303, campaign-1435, campaign-1711, campaign-1933, campaign-1987, campaign-2101, campaign-349, campaign-619, campaign-817, campaign-937. V10 rebuilt density around a shared V9 interaction core; walls/cascades changed full layouts but not the underlying strategy template.

## Solver-derived representatives

- campaign-744: complete=true, states=576, decisions=5, traps=11, hardest winning share=0.5, policy classes=73, truncation=[]
- campaign-349: complete=true, states=340, decisions=8, traps=20, hardest winning share=0.4, policy classes=44, truncation=[]
- campaign-1997: complete=true, states=576, decisions=5, traps=11, hardest winning share=0.5, policy classes=73, truncation=[]

## Conclusion

Known high-band clones are content-generation/semantic-template clones, not parser, runtime-cache, catalog-selection or renderer defects.
