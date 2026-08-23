# Campaign V9 human playtest findings

## Evidence status

- Source: `docs/magnetrail-playtest-pet-cffc4bb6.csv`
- SHA-256: `59784c4ea8ae392779450ff2a2ed6e47463f55cd426d119717cd6ae8c467ad88`
- Participant: anonymous code `PET`
- Campaign/study/schema: V9 / `9202205` / 1
- Recorded observations: 157 of 180, covering 157 unique boards
- Decision: **V9 difficulty approval failed; do not pool this run with V10**

One participant is not enough for final inference, and Hint was used on 105 of 157 observations.
The run is nevertheless decisive as rejection evidence: only 46.5% of ratings were within one band
of the assigned label, against the 70% approval floor, and the six medians were not strictly ordered.

## Results by assigned band

| Candidate band | n | Completion | Unassisted completion | Median perceived | Within one band | Median time | Median actions | Median hints | Median restarts |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Easy | 26 | 100.0% | 34.6% | 1 | 96.2% | 5.9 s | 4.0 | 2.5 | 0 |
| Medium | 26 | 96.2% | 34.6% | 1 | 100.0% | 5.6 s | 4.0 | 3.0 | 0 |
| Hard | 26 | 100.0% | 34.6% | 1 | 30.8% | 6.7 s | 4.5 | 4.0 | 0 |
| Super Hard | 26 | 96.2% | 30.8% | 2 | 46.2% | 12.4 s | 10.0 | 10.0 | 0 |
| Expert | 26 | 100.0% | 34.6% | 2 | 7.7% | 15.8 s | 12.5 | 10.0 | 1 |
| Master | 27 | 100.0% | 25.9% | 3 | 0.0% | 14.1 s | 10.0 | 10.0 | 0 |

Perceived-rating totals were Easy 65, Medium 50, Hard 39, Super Hard 2, Expert 0, and Master 1.
The V9 labels therefore compressed into roughly three experienced bands rather than six.

## Structural finding

The qualitative report that ten-arrow boards looked reflected was confirmed against the full V9
catalog. The 999 Super Hard, Expert, and Master boards had 999 unique full-board D4 fingerprints,
but only two D4-normalized arrow-and-magnet skeletons. Wall placement had made full boards unique
while disguising repeated interactive layouts.

V10 consequently hard-rejects duplicates at four levels: exact board, D4-normalized full board,
D4-normalized arrow silhouette, and D4-normalized arrow-plus-magnet interactive layout. Density
also rises by band and the extra arrows are placed in ordered cascades rather than as visual-only
decoration.
