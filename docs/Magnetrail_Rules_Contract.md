# Magnetrail Rules Contract

Rule version: `magnetrail-core-1`

This file is authoritative for M0. The engine must be deterministic and contain no Android framework dependencies.

## Coordinate system

- Board coordinates are one-based in authored JSON.
- Row 1 is the top edge.
- Column 1 is the left edge.
- North decreases row; east increases column; south increases row; west decreases column.
- The loader may convert coordinates to a zero-based internal representation, but serialization and tests must preserve the authored values.

## Entities

- Arrow: stable ID, cell position and printed cardinal direction.
- Magnet: stable ID, cell position and current polarity (`PULL` or `PUSH`).
- Wall: permanent occupied cell.
- At most one entity occupies a cell.

## Turn resolution

1. The player selects exactly one remaining arrow.
2. Capture the immutable board state at tap time.
3. Find magnets aligned with that arrow on the same row or column.
4. A candidate magnet is visible only if no arrow, wall or other magnet lies strictly between it and the selected arrow.
5. The nearest visible aligned magnet controls the arrow.
6. If two or more nearest visible aligned magnets are tied at the same distance, their influence cancels and the arrow follows its printed direction.
7. `PULL` sets the effective direction toward the controlling magnet.
8. `PUSH` sets the effective direction directly away from the controlling magnet.
9. With no controlling magnet, use the printed direction.
10. Trace one cell at a time until a terminal event occurs.

## Terminal events

### Exit

The arrow crosses the board boundary. This succeeds unless a controlling `PULL` magnet existed; a Pull-controlled arrow must reach that magnet rather than exit.

### Pull capture

The arrow reaches its controlling Pull magnet. The arrow is removed successfully.

### Collision

The arrow encounters another arrow, a wall or a non-controlling magnet. The launch fails.

## State mutation

On successful magnet-controlled movement:

- Remove the selected arrow.
- Flip only the controlling magnet: `PULL` becomes `PUSH`, or `PUSH` becomes `PULL`.

On successful unaffected movement:

- Remove the selected arrow.
- Do not change any magnet.

On collision or invalid Pull exit:

- Return the original board state unchanged.
- Do not remove the arrow.
- Do not flip a magnet.

## Win and deadlock

- Win immediately when no arrows remain.
- Deadlock exists when arrows remain but none can launch successfully.
- The engine reports deadlock; it never reshuffles or mutates the board automatically.

## Required output

Every resolution returns:

- Success or failure
- Original and resulting state
- Selected arrow ID
- Printed and effective direction
- Controlling magnet ID, if any
- Traversed cells in order
- Terminal event
- Collision target, if any
- Polarity change, if any
- Win and deadlock flags

## Determinism

The same `BoardState` and `PlayerAction` must always produce structurally equal `ResolutionResult` values. Animation, time, random numbers, device density and frame rate must never influence the rules result.
