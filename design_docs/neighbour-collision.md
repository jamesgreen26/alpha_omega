# Neighbour Collision

**Bumping into what you can see on the other side of an edge**

Target: Minecraft 1.21.1, NeoForge · Status: implemented (steps 1–4; see §10) · Extends **Cube World** §3, §5 and §6 (section numbers below refer to `cube-world.md`).

---

## 0. Summary

`cube-world.md` §0 says of the neighbouring faces: "You can see them but not touch them." Near an edge that is now the visible problem. The neighbour's terrain is drawn folded into place (§6.3), but in the home face's storage the cells it occupies are **filler**, which has no collision. So an entity walking up to an edge walks into the neighbour's rock, trees and walls for up to `WAIT_DEPTH` (2 blocks) before it crosses, and arrows fly into the neighbour's blocks and stick inside them once they transfer.

The fix: filler cells close to the diagonal take their collision shape from the neighbour.

1. **A band.** The 16 filler cells directly under each column's barrier cell. Each of them is the same physical cell as one of the neighbour's own cells within 16 blocks above its barrier: along the edge, that is the neighbour's first row of chunks (§2).
2. **Edge filler.** A new block, placed in the band instead of filler. It looks and occludes exactly like filler. Its collision shape is the neighbour's block at the same cube cell, rotated into this face's frame (§3).
3. **Nothing else hooks collision.** Vanilla asks the block for its collision shape everywhere that matters (movement, `noCollision`, projectile ray casts), on the client and on the server. So one method covers all of them, and both sides agree (§4).

Interaction is unchanged: the crosshair uses outline shapes, not collision shapes, so neighbour faces still can't be targeted (§6.3).

---

## 1. What happens today

A player on face A walks toward the edge with face B.

| Where | What is in A's storage | Effect |
|---|---|---|
| A's own cells | A's terrain | Collides normally. |
| The barrier staircase | Edge bedrock or edge air, decided alike on both faces (§4.3) | Collides correctly. Both faces store the same block. |
| Past the diagonal (B's cells) | **Filler**: no collision | B's terrain is drawn here, rotated, but the entity passes through it. |

Consequences:

- **Walking into the neighbour.** Where B's ground is higher at the ridge than A's (they differ by about 3 blocks on average), B's rock forms a 45° staircase past the diagonal in A's frame. The entity walks into it. `FaceTransfer.roomToCross` then holds it at the diagonal, because the destination box would be inside B's rock, until it is `WAIT_DEPTH` deep. After that it crosses and vanilla pushes it out. Players are teleported by the server at `FORCE_DEPTH` (3 blocks).
- **Trees and buildings on B near the edge** stick out sideways in A's frame. They can be walked into and through.
- **Projectiles** ray-cast through filler. An arrow aimed at B's block near the edge passes into it, transfers 0.5 blocks past the diagonal, and lands inside the block.
- **`roomToCross` is the only thing that makes the far side solid**, and it only works for upright entities and only at the moment of crossing.

---

## 2. The band

### 2.1 Which cells

In each storage column of face A, the barrier cell sits at `barrierY` and everything below it is foreign (filler). Take the top `BAND = 16` of those filler cells: `y ∈ [barrierY − 16, barrierY)`.

Work it through in A's local frame for the edge with B (`u` toward B, `d` A's up). A column at `u` has its barrier at `d = u`. The filler cell `k` below it (`d = u − k`, `1 ≤ k ≤ 16`) is owned by B. In B's frame the same cell is in column `u' = u − k`, at `d' = u`, so it is `k` cells **above B's barrier**. So the band is one-to-one with B's cells within 16 above its barrier:

- **At ground level** those are B's columns along the edge, out to 16 blocks: B's first row of chunks, plus the overhang above the edge.
- **Underground** they are B's rock next to the diagonal, which only matters to caves that reach the barrier.
- **In the sky** they are B's air: lookups that return nothing.

The band in B's storage is exactly the shared band the generator already blends (`SHARED + FADE = 16` cells above the barrier, `CubeChunkGenerator.blendTowardNeighbour`), so it is terrain both faces have already agreed on.

### 2.2 Corner columns

Near the lines where three faces meet, the cells under a column's barrier are owned by different neighbours, or they are the barrier between two *other* faces (§1.2). Edge filler handles each cell on its own:

- Owned by a neighbour: read that face's copy.
- A barrier cell of two other faces: read the copy on either of them (`barrierFaces`, first one that is not A). Both copies agree (§4.3).
- Owned by the opposite face: this can't happen within 16 of the barrier, but if it does, the shape is empty.

### 2.3 Why 16

Something can reach past the diagonal by its feet margin (`MARGIN` 0.5, `WAIT_DEPTH` 2 for upright entities, `FORCE_DEPTH` 3 for players), plus half its width (about 2 for a ghast), plus one tick of movement, because vanilla sweeps the box along the motion (`expandTowards`). That is under 10 blocks. 16 leaves a margin and matches the chunk size and the generator's shared band. Beyond the band, filler stays empty. Anything that far past the diagonal has already transferred.

---

## 3. Edge filler

### 3.1 The block

`EdgeFillerBlock extends FillerBlock`, registered as `alpha_omega:edge_filler` (`CubeBlocks.EDGE_FILLER`). It needs a blockstate JSON and a lang entry, like `filler`.

- **Same as filler:** invisible (`RenderShape.INVISIBLE`), no outline shape, full occlusion shape, blocks light, `forceSolidOn`, unbreakable, `PushReaction.BLOCK`, `isValidSpawn(never)`. Rendering and lighting don't change.
- **Collision:** `getCollisionShape(state, getter, pos, context)` returns the neighbour cell's shape (§3.2), rotated (§3.3).
- **`dynamicShape()`.** Two reasons:
  - Without it, `BlockState` caches the collision shape once, against an empty getter.
  - A rotated shape can leave its cell. A fence on B is 1.5 tall, so in A's frame it sticks out 0.5 sideways. `BlockCollisions` only looks at cells on the outer ring of its search box if `hasLargeCollisionShape()` is true, and that is always true for uncached states.
- **Every property that would otherwise be derived from the collision shape is made explicit,** so a dynamic shape does not leak into them: `isSuffocating(false)`, `isViewBlocking(false)`, `isRedstoneConductor(false)`. Filler already sets the first two.

`dynamicShape` disables the state cache for this block only. Plain filler, which is most of every face's storage, keeps its cache. That is the reason for a separate block rather than making filler itself dynamic.

### 3.2 Finding the neighbour's cell

`CubeGeometry.bandSource(face, x, y, z)` returns the face and storage cell to read, or `null`:

- `cellOwner` gives the owning face (or `BARRIER`, §2.2).
- `transformBlock` gives the exact cell in that face's storage.
- It is pure arithmetic, and is unit tested alongside the other geometry.

`EdgeFillerBlock.getCollisionShape`:

1. If the getter is not a `Level`, or `Cube.of(level)` is null, return empty. This covers the state cache, `PathNavigationRegion`, and other mods' getters (Sable, §6).
2. Get `bandSource`. If it is null, return empty.
3. Get the chunk with **`level.getChunkForCollisions(cx, cz)`** (`getChunk(…, FULL, false)`), never `getBlockState`. On the server, `getBlockState` would load or generate the neighbour's chunk synchronously from inside entity movement. On the client this returns the map entry from `ClientChunkCacheMixin`, or null.
4. If the chunk is missing, use the **missing-chunk shape** (§3.4).
5. Get the neighbour's state. Ask it for its collision shape with **`CollisionContext.empty()`** at its own position in its own storage. The entity's context can't be used: it describes "above" and "descending" in the home face's frame, which is a different "up" from the neighbour's. Blocks that depend on the entity (scaffolding, powder snow) therefore come out as they would for no entity. That is deterministic and the same on both sides.
6. Rotate the shape into this face's frame (§3.3).

The lookup can never recurse. The target cell is owned by the face it is read from (or is a barrier cell there), so it is never filler.

### 3.3 Rotating shapes

`NeighbourShapes.rotate(shape, from, to)`:

- For each box in `shape.toAabbs()`, rotate its corners about the cell centre `(½, ½, ½)` with `CubeGeometry.rotate(from, to, …)`, then rebuild the shape with `Shapes.or`. The rotations are 90° multiples, so boxes stay axis-aligned and exact.
- **Cache** results in an identity map per directed face pair, keyed by the source shape. Most blocks return shared constant shapes (`Shapes.block()`, the slab and stair constants), so the cache stays small. Weak keys keep it from growing unbounded with shapes that are built on demand.
- `Shapes.block()` and `Shapes.empty()` take a fast path, because they are most of the lookups.

### 3.4 Missing chunks

What should a band cell be when the neighbour's chunk isn't there?

- **Server:** this is rare. Entities only tick within simulation distance of a player, and that player's virtual position on the neighbour (`NeighbourViews`) loads the neighbour's edge chunks out to view distance. The exceptions are force-loaded chunks with no player nearby, and the first ticks after a join, before the neighbour gate opens.
- **Client:** the neighbour's chunks arrive after the home face's (the gate in `NeighbourViews.tick`). The transfer-retention plan also describes how they can briefly go missing after a crossing.

**Recommendation: solid (`Shapes.block()`).** You can't walk or shoot into a face you haven't received, just as vanilla won't move a player in an unloaded chunk. Shapes an entity already overlaps don't stop it moving out (vanilla's collision ignores them), so nobody gets trapped. The alternative is empty, which is today's behaviour; it would let a client predict free movement that the server then corrects. This is open decision 1.

### 3.5 Generation

In `CubeChunkGenerator.carveFaces`, the column loop that writes filler below `fillTop` writes edge filler for `y ≥ barrierY − BAND` and plain filler below that. The heightmap updates are unchanged. They only look at the top filler cell, which becomes edge filler, and edge filler is `forceSolidOn` like filler.

Chunks generated before this change keep plain filler in the band, so they have no neighbour collision until they are regenerated. Dev and gametest worlds are fresh each run, so no upgrade pass is planned.

---

## 4. What it covers

Vanilla asks `BlockState.getCollisionShape(getter, pos, context)` with the level as the getter, so edge filler takes part in all of the following without any further mixins. They run identically on the client and the server.

| Path | Used for | Covered |
|---|---|---|
| `Entity.collide` → `Level.getBlockCollisions` | All entity movement, including the local player's prediction | Yes |
| `CollisionGetter.noCollision` | `FaceTransfer.roomToCross`, spawning, pose changes (stand up from crouch or swim), `isPlayerCollidingWithAnythingNew` in the server's movement check | Yes |
| `Level.clip` with `ClipContext.Block.COLLIDER` | Arrows, tridents, thrown items, fireballs | Yes: projectiles hit the neighbour's blocks before they cross |
| `Level.clip` with `OUTLINE` / `VISUAL` | Crosshair, block placement and breaking, mob line of sight | **No**, by design: edge filler's outline is empty, so interaction stays home-only (§6.3) |
| `Level.findSupportingBlock` | `onGround` support, friction, step sounds, fall-damage block | Partly. `onGround` is set from the collision, so standing works. The supporting block found is the edge filler, so friction is the default 0.6 and step sounds are filler's. Mapping it to the neighbour's block (ice, slime, honey) is a follow-up. |
| `Entity.isInWall`, `LocalPlayer.suffocatesAt` | Suffocation, push-out | No: `isSuffocating(false)`. Collision now keeps entities out, so it isn't needed. |

**Not covered** (out of scope, listed in §8):

- Fluids across the edge: the band has no fluid state, so swimming toward an edge is dry past the diagonal until you cross.
- Entity-to-entity collision across faces: boats, shulkers, pushing.
- Effects of being inside a block (`checkInsideBlocks`): cobwebs, sweet berries, pressure plates, portals.
- Pathfinding: `PathNavigationRegion` isn't a `Level`, so mobs still plan paths through the band and are stopped by collision when they follow them.

---

## 5. Behaviour at the edge afterwards

- **What you see is what you hit.** The neighbour's blocks in the band are drawn by the neighbour renderer at the same place they now collide.
- **The ridge.** Where B's ground is higher than A's at the ridge, A sees a 45° staircase of full blocks (§1). Players and mobs have to jump each step, as with any staircase of full blocks, and cross once they are above B's surface. When the grounds meet flush, nothing changes. When B is lower, the entity steps off a convex ridge as today.
- **`roomToCross` mostly stops waiting.** An entity can no longer overlap B's solid cells, so its box at the destination is clear except for the re-axed hitbox of an upright entity. Keep the wait as a safety net, and count how often it still triggers (§7).
- **Symmetric.** After crossing to B, A's blocks near the diagonal are in B's band and collide the same way, so walking back is solid too.

---

## 6. Client and server agreement

The server replays the client's movement and corrects it when the result differs ("moved wrongly"), so both sides have to compute the same shapes.

- The shape depends only on the neighbour's block state and the fixed geometry, through an empty collision context. No entity state is involved.
- Both sides read the neighbour's chunk without loading it. Where the client hasn't received the chunk yet and the server has it, the client predicts solid (with the §3.4 recommendation). The client then moves *less*, which the server accepts. The reverse (the server missing a chunk the client has) only happens around force-loads and joins.
- Block changes in B's band reach the client through normal block updates in B's storage. A's edge filler has no state of its own to update, and the next collision query reads the new state. Vanilla's per-chunk collision caching only applies to cached states, and edge filler has none.

---

## 7. Tests

**Unit tests (`CubeGeometryTest`, new `NeighbourShapesTest`):**

- For every face and column near each of the 12 edges, every band cell's `bandSource` is a neighbour's owned cell, or a barrier cell of two other faces. Its height above that face's barrier is in `1..16`. Mapping back gives the original cell.
- Every one of the neighbour's owned cells within 16 above its barrier is the source of exactly one band cell on the other face (one-to-one).
- Shape rotation: rotating there and back is the identity for all 24 directed neighbour pairs; full and empty blocks are fixed points; a bottom slab on B becomes the half of the cell on A that faces B's down; a fence's 1.5-tall post becomes a 0.5 overhang on the correct side.

**Gametests (new `CollisionGameTests`, using `TestChunks` to pre-generate both faces):**

1. **Wall past the diagonal.** On UP near the EAST edge, place stone in EAST's first chunk row at the cell that maps just past the diagonal at the test height. A zombie walking toward it, and an item thrown toward it, stop short of the diagonal. Remove the stone, and both cross.
2. **`noCollision` both ways.** A box overlapping that cell from UP's side collides. The same box with the stone removed doesn't.
3. **Slab and fence orientation.** `level.getBlockCollisions` around the band cell returns the expected rotated boxes.
4. **Arrow.** An arrow fired from UP at a block in EAST's band sticks in it before transferring. With the block removed, it crosses with its world velocity (as in `TransferGameTests`).
5. **Crosshair stays home.** `level.clip` with `OUTLINE` through the band does not hit the neighbour's block.
6. **Corners.** A band cell near a cube corner reads the right face, including a barrier cell of two other faces.
7. **Missing chunk.** With the neighbour chunk not loaded, the band cell is solid (if open decision 1 goes that way).
8. **Layout.** Update `FaceGameTests` (lines 103, 142, 164): a foreign cell is filler *or* edge filler, and edge filler occupies exactly the 16 cells under each barrier cell. Add a `CubeBlocks.isFiller(state)` helper.
9. **Existing tests stay green**, especially `TransferGameTests`' upright waits and the neighbour tests.

**Dev client** (`DevScript`): walk into a tree and a ridge staircase on the next face, shoot an arrow at the neighbour's ground, and take screenshots. Check that there are no rubber-band corrections in the log while doing it.

**Counters:** `/cube scan` reports edge-filler lookups per tick, missing-chunk hits, and `roomToCross` waits, so the cost and the safety net's use are visible.

---

## 8. Order of work

| # | Step | Done when |
|---|---|---|
| 1 | `bandSource` and `NeighbourShapes` with unit tests (§3.2, §3.3) | Geometry and rotation tests pass |
| 2 | `EdgeFillerBlock`, registration, blockstate, lang (§3.1) | Builds; the mixin audit is unaffected (no new mixins) |
| 3 | Generator writes the band (§3.5); `FaceGameTests` accept edge filler | Layout gametest passes |
| 4 | Collision lookup with the missing-chunk policy (§3.4) | Gametests 1–7 pass |
| 5 | Counters, dev-client walk and screenshots | No corrections; `roomToCross` waits are near zero |
| 6 | Update `cube-world.md` §0, §3 and §6.3 ("see but not touch" becomes "touch within one chunk of the edge"), and `progress.md` | |

**Follow-ups, not in this plan:**

- Supporting block mapped to the neighbour, for friction, sounds and fall effects.
- Fluids in the band.
- Entity-to-entity collision across faces.
- `checkInsideBlocks` across the edge.
- Pathfinding through `PathNavigationRegion`.
- A step-assist up the ridge staircase, if jumping it feels bad.

---

## 9. Risks and open decisions

| # | Question | Recommendation |
|---|---|---|
| 1 | Missing neighbour chunk | Solid (§3.4). Revisit if it blocks players right after crossing before transfer retention lands. |
| 2 | Band depth | 16 (§2.3). Raise it only if something large (a sub-level, a dragon) reaches further. |
| 3 | Collision context for neighbour blocks | Empty (§3.2). Transforming the entity's context into the neighbour's frame is possible, but only scaffolding and powder snow care. |

- **Cost.** Each edge-filler cell in an entity's sweep costs a geometry transform, a chunk lookup and possibly a shape rotation (cached). It only happens next to edges. The counters in §7 measure it. If it shows up, cache the rotated shape per `(face, cell)` for one tick.
- **Mods that replace the collision loop** (Lithium-style ports that skip `getCollisionShape` for blocks they think are simple, or use the state cache). Edge filler is marked dynamic, which those mods generally respect, but this is unverified. It is the same class of risk as Sodium for rendering.
- **Sable (phase 7).** Sub-levels collide using their own getters, which get an empty shape from edge filler (§3.2 step 1). They keep ignoring the band unless phase 7 decides otherwise.
- **C2ME (phase 8).** Its chunk system has to return null from `getChunkForCollisions` for chunks that aren't loaded, rather than loading them. Phase 8 already verifies the related ticket paths.
- **Ridge feel.** Jumping up to about 3 one-block steps at many ridges may feel worse than today's invisible walk-through. Closing the ridge gap (`progress.md` phase 6 item) would reduce it.

---

## 10. Outcome

Steps 1–4 are in, as planned, except open decision 1. A missing neighbour chunk is **empty**, not solid: a band cell only collides when the neighbour's block is known to be there.

- **Geometry:** `CubeGeometry.bandSource` and `BAND`. A barrier cell between two other faces reads the first of them that is not this face's opposite.
- **Shapes:** `block.NeighbourShapes` turns shapes, with a weak identity cache per directed face pair. `block.EdgeFillerBlock` is registered as `alpha_omega:edge_filler`, with a dynamic shape. The generator writes it in the 16 cells under each barrier. `CubeBlocks.isFiller` covers both kinds of filler.
- **Unit tests:** every band cell reads a neighbour's cell within the band above that face's barrier, and maps back to the original cell. Every such neighbour cell is read once. Shapes come back unchanged after a round trip, and keep their own face's down.
- **Gametests:** the layout test covers the band. `CollisionGameTests` checks:
  - stone on the neighbour makes the band solid, and a slab stands upright;
  - items stop at the wall, and cross once it's removed;
  - arrows stick in it before crossing;
  - the crosshair passes through it;
  - a missing neighbour chunk is open.

**Not done:**
- Step 5: the counters, and a dev-client walk that actually reaches a ridge.
- Step 6: updating `cube-world.md`.

Worlds generated before this change keep plain filler in the band, so their edges don't collide.
