# Rotated Seams

**Seamless joins between chunk regions that are turned relative to each other**

Target: Minecraft 1.21.1, NeoForge · Status: plan · Builds on **Cube World** (v2, this branch) and **Islands & Lift Tables** (main, in `archive/`). Rotations are about the vertical axis only (decided): gravity is `-Y` everywhere.

---

## 0. Summary

The world is an **atlas**: a set of regions, each a rectangle of chunks, glued edge to edge. Each glue (a **seam**) is a rigid motion: a turn of 0°, 90°, 180° or 270° about Y plus a chunk-aligned translation. A torus is one region glued to itself by translations (main's world). A pillowcase is two regions, glued by a translation on one pair of edges and a half-turn on the other.

The design keeps one idea from each branch:

- **From v2: every region is ordinary, upright chunks in its own storage area.** A region's blocks, block entities and entities share one coordinate frame, so modded blocks work exactly as in vanilla. No packet changes, no canonicalisation, no lap tags.
- **From main: near a seam, both sides are one world.** Main got this by storing each block once and reaching it through any image. Here each side's storage also holds a **band**: a copy of the neighbour's first few chunks past the seam, turned into this side's frame and kept identical to the original on every write. Redstone, pistons, fluids, portals and multiblocks reach across the seam by reading and writing ordinary cells in their own storage.

The rules that make two copies behave like one world:

1. **State is shared, reactions are owned.** Every band cell has exactly one **owner** copy. A write to either copy is applied to both. Neighbour updates, shape updates, scheduled ticks, random ticks and block entity ticks run only at the owner, so nothing happens twice (§3).
2. **Block entities live at the owner.** On the server, the other copy has no block entity of its own. Looking one up there returns the owner's object (§3.4).
3. **A placed block belongs to the frame that placed it.** Something built from A's side is entirely in A's frame, even where it reaches past the seam. A multiblock or kinetic network built from one side is therefore seamless for modded code as well (§3.3).
4. **Entities choose a frame, and interacting entities share one.** Inside a band an entity can live in either storage, because both hold the same blocks. Players keep their frame until they are well past the seam. Other entities near a player join the player's frame. A transfer is invisible, because both frames show the same world (§4).
5. **A few vanilla choke points look into the other frame:** entity box queries, POIs, player distance checks, game events and interaction range checks (§5).

Because the turn is about Y, `BlockState.rotate` gives an exact copy of every vanilla block (and every modded block that supports rotated structures). Collision, light, rendering and gravity all match across the seam, so v2's barrier, filler, edge air, gravity turn and camera ease are not needed.

---

## 1. What each branch gets right, and why

| | Main (torus, islands) | V2 (cube, faces) |
|---|---|---|
| Storage | One copy, canonical. Any image reaches it. | One area per face, ordinary chunks. |
| Simulation frame | Per island, unrolled. Entities lifted, blocks canonical. | Per face. Blocks, entities and the client agree. |
| Across the seam | Everything, because storage is periodic. | Nothing interactive. Barrier, then a transfer. |
| What broke | Block entities and mod data hold canonical positions, while entities near them are lifted. This happens wherever a lap is not 0, not only at the seam (`archive/mod-compatibility.md` §1). | The edge is a wall for building, redstone, AI and multiblocks. Terrain meets with a ~3-block ridge. |
| Root cause | Storage frame ≠ simulation frame. | Each place exists in only one frame. |

The combined design removes both root causes. Storage frame always equals simulation frame (v2), and every place near a seam exists in both frames (the band), so whichever frame something is in, its surroundings are there in that frame (main's "any image works", restricted to the band).

What is left over is narrower than either branch: code that compares positions of two things in **different** frames. That only happens inside a band, and only between things built or standing in different frames (§9).

---

## 2. Geometry

### 2.1 Terms

| Term | Meaning |
|---|---|
| Region | A rectangle of chunks with its own storage origin `O_r`. Its **own area** is the rectangle. |
| Seam | A glued pair of chunk-aligned edges, one on each region (possibly the same region). Glued edges have the same length. |
| `G_AB` | The seam's rigid motion from A's storage to B's storage: turn by `k·90°` about Y, then translate. Exact on block cells and on chunks. `G_BA = G_AB⁻¹`. |
| `Rot_AB` | The matching `Rotation` (`NONE`, `CLOCKWISE_90`, `CLOCKWISE_180`, `COUNTERCLOCKWISE_90`) for block states, shapes, directions and yaw. |
| Nominal seam | The glued edge itself. |
| `C` | Claim depth, past the nominal seam (default 32 blocks). |
| `H` | Band depth (default 64 blocks, 4 chunks). Must be at least `C + R`. |
| Skirt | One more chunk past the band (16 blocks). Mirrored for light and meshing only. |
| `R` | Interaction radius (default 32 blocks). |
| Copy | Each band cell exists twice: in its nominal region's own area, and in the other region's band. |
| Owner | The copy where the cell's reactions run and its block entity lives (§3.1). |
| Frame | A region's storage, used as a coordinate system. An entity's frame is the storage it is in. |
| Valid | A position is valid in frame A if A's storage holds it as a live cell: A's own area, or A's band out to `H`. |

### 2.2 Storage layout

Each region's storage holds its own area, a band of `H` and a skirt of 16 past every seam, and nothing else. Regions sit on a coarse grid with spacing of at least the footprint plus twice the largest view distance, as in v2 (`cube-world.md` §1.3), so vanilla's view square never reaches another region's storage.

Cross-section across seam AB, in A's storage:

```
   A's own area                          │ nominal seam
 ────────────────────────────────────────┼──────────────┬──────────────┬────────┐
                                         │  claim zone  │ entity-valid │ skirt  │
                                         │   0 … C      │   C … H      │ H…H+16 │
                                         │ players stay │ others stay  │ copy   │
                                         │ A may claim  │              │ only   │
 ────────────────────────────────────────┴──────────────┴──────────────┴────────┘
                                          ◄─────── A's band: B's cells, turned ───►
```

B's storage has the mirror image: A's cells next to the seam, turned into B's frame.

Band chunks map one to one onto chunks in the neighbour's own area (`G` is chunk-aligned). A band chunk and its **source** form a **pair**.

### 2.3 Corners and cone points

Where seams meet at a vertex, bands overlap. If the regions around the vertex add up to 360°, the corner is flat, and the corner square of A's storage is a band chunk like any other (its source is the diagonal region).

If they don't add up to 360° (a **cone point**, for example a pillowcase corner, which totals 180°), the corner square past both seams has no consistent source. Going round the vertex one way or the other gives different cells. That square stays empty in storage: no blocks, not valid for entities, not drawn. An entity that reaches it transfers by the seam of the band it came from. Seamlessness holds for everything that does not wrap round a cone point within `H` of it. This is the same kind of limit as main's cuts, and it is inherent to the topology.

### 2.4 Where v2's geometry goes

The cube's diagonal ownership, overhang, barrier heightfield and unfold `U_AB` all come from tilted faces. With turns about Y, ownership is just "which own area is the cell in", the band is a fixed-depth strip, and the virtual position of a player on a neighbour is exactly `G_AB(pos)`.

---

## 3. The band

### 3.1 Ownership

- **Nominal owner:** the region whose own area contains the cell.
- **Claimed:** a cell within `C` of the seam on B's side can be owned by A (§3.3), and the other way round.
- Stored as a per-section bitset of flipped cells in a chunk data attachment, written in both copies. Most sections have none, and an empty set costs nothing.
- Air is always owned nominally.

### 3.2 Writes: state is shared

All block writes in loaded chunks end at `LevelChunk.setBlockState`, which is the one choke point. A flag on each chunk says whether it is in a pair, so chunks away from seams pay a single field check.

When copy X of a band cell is written:

1. X's write runs as vanilla: section, heightmaps, light, `onPlace`/`onRemove`, block entity creation or removal (§3.4).
2. The **mirrored write** puts `state.rotate(Rot_XY)` into copy Y at `G_XY(pos)`. It writes the section, heightmaps and light, but creates no block entity and calls no `onPlace`/`onRemove`. A re-entry guard stops it mirroring back.
3. **The mirrored write sends no neighbour updates**, only block update packets. Vanilla often writes without updates and then notifies its own neighbours explicitly (redstone dust, diodes, observers, pistons), so updates are handled where they are sent: an update aimed at a non-owner copy is forwarded to the owner (§3.5). (Changed after the phase 4 spike, 2026-10-05.)
4. Block update packets go out for both copies, to whoever tracks each chunk. No packet type changes.

A write to a cell that is not owned in X is still applied in X first, then mirrored. Placing, breaking, pistons, explosions, fluids and mob griefing therefore all work through the band without being routed anywhere.

### 3.3 Claims: a placed block belongs to the frame that placed it

A write is a **placement** when the old state is air, a replaceable block or a bare fluid, and the new state is a different block. A placement in frame X claims the cell for X if the cell is within X's claim zone (own area, or the band out to `C`). Otherwise the cell keeps its owner.

- Breaking a block (to air) returns the cell to its nominal owner.
- A change of state of the same block (dust power, door open, crop age) never changes the owner.
- Moved blocks (pistons, Create contraption disassembly) are placements in the frame of the logic that moves them, so they stay with their builder's frame.

**As implemented (phase 8, `band/Claims`).**
- **When the rule applies:** to every original (not mirrored) write in a chunk with copies, at the copy where the write was made.
- **Moving pistons:** writing a moving piston is also a placement, whatever it replaces.
- **Placing in the tile:** gives the cell back to the tile copy.
- **Timing:** the claim happens straight after the section write: before the mirror, `onRemove`/`onPlace` and block entity creation. A removal returns the cell to nominal at the end of the write, so the old block's `onRemove` still reaches its owner's block entity.
- **Block entities:** a block entity that a write at a non-owner copy would create (beyond `C`, or a block-to-block change) is created at the owner instead. It is empty, so nothing frame-dependent is lost.
- **Explicit `setBlockEntity`:** a block entity set explicitly at a non-owner copy (pistons) claims the cell. Beyond `C` that claim ends when the block entity is removed. Inside `C`, ownership follows the block.
- **Recovery:** a claimed cell takes a newer source's block state; its block entity data is not refreshed.
- **Built from both sides (§9 limit 1):** a test multiblock whose controller stores absolute part positions still forms, because lookups reach the far parts through the owner redirect. Each far part reports itself unlinked, because its own position is in the other frame.

So a player standing in A's frame builds a tank, a gearbox chain or a reactor across the nominal seam, and all of it is owned by A. Its block entities are created in A's storage at A-frame positions, and every position they store, compare or send is in one frame. Players stay in their frame throughout the claim zone (§4.2), so a build made in one visit is always in one frame.

### 3.4 Block entities

- **The server keeps one block entity, at the owner.** The other copy has none in its chunk's map, so nothing ticks, saves or syncs twice.
- **`Level.getBlockEntity` and `LevelChunk.getBlockEntity` at a non-owner copy return the owner's object.** Hoppers, comparators, containers and capability users get the live object, and writes to it are real. Its `getBlockPos()` is in the owner's frame (§9).
- **Capabilities:** `Level.getCapability(cap, pos, side)` at a non-owner copy is redirected to the owner's position with `side` turned by `Rot`. This covers NeoForge's item, fluid and energy handlers, and with them vanilla hoppers and most modded pipes.
- **Client copies are replicas.** Chunk packets for a non-owner copy carry the owner's update tag at the turned position and state. `ClientboundBlockEntityDataPacket` is sent for both copies. A client block entity is already a read-only replica in vanilla, so renderers work unchanged.
- **Block events** (pistons, note blocks, chest lids) run once, in the copy that queued them. Their packets are sent for both copies.

### 3.5 Reactions are owned

These run only at the owner copy. At the other copy, they are skipped:

| Reaction | Hook |
|---|---|
| Neighbour updates (`neighborChanged`, NeoForge `onNeighborChange`): **forwarded** to the owner, with target, source and direction moved by the motion between the copies | `BlockStateBase.handleNeighborChanged`, `Level.updateNeighbourForOutputSignal` |
| Shape updates (`updateShape`): **forwarded** to the owner the same way | `Level.neighborShapeChanged` |
| Random ticks and precipitation | `ServerLevel.tickChunk`, per-section ownership mask |
| Block entity tickers | Not registered at non-owners (§3.4) |
| POI registration | `ServerLevel.onBlockStateChange` (§5.2) |

**Entity-driven callbacks run in the entity's own frame, on whichever copy it touches:** `entityInside`, `stepOn`, `fallOn`, `useItemOn`, `useWithoutItem`, `attack` and `onProjectileHit`. The entity exists in one frame only, so these never run twice.

**Scheduled ticks run in the copy that scheduled them.** Reactions schedule at the owner. An entity-driven callback schedules in the entity's frame. Scheduling is skipped if the other copy already has the same block or fluid tick pending at that cell.

Worked example: a redstone line crossing a 90° seam, with the dust owned by A and the repeater owned by B.

1. The dust powers up in A. The mirrored write sets the dust's copy in B's storage, with no updates.
2. In A, the dust's neighbour update reaches the repeater's copy. A doesn't own it, so the update is forwarded to the repeater in B, with its source and direction moved into B's frame.
3. The repeater in B reacts and schedules its tick in B, as vanilla would.
4. The tick powers the repeater in B. The mirrored write sets A's copy, and the repeater's own update to its output neighbour is delivered (or forwarded) to that neighbour's owner.

Each component reacts once, in its own frame, in the same tick order as vanilla. Across a fold, an owner reacts in its own frame, so vanilla's neighbour update order is turned for the part of a build on the other side: simple builds match vanilla tick for tick (spike gametests), but an order-sensitive build could differ.

**Block entities created explicitly claim their cell.** `PistonBaseBlock.moveBlocks` puts a moving-piston block entity at the destination with `setBlockEntity`, in the piston's frame. A block entity created at a copy owns that cell while it exists, so a push across a seam lands. This is the minimum of §3.3's claims, needed from phase 4 on. All reads see turned copies that are up to date, so connections, facing and power levels match.

### 3.6 Light, heightmaps, fluids

- **Light** is computed separately in each storage. The skirt makes the copy exact: a light source beyond the skirt reaches at most 14 blocks into it, so every cell in the band has the light it would have in the source. Skirt cells themselves may be slightly off, but nothing stands, spawns or is drawn there.
- **Heightmaps** are kept per copy by vanilla, from the mirrored sections.
- **Fluids** flow by owner reactions and scheduled ticks. A flow into a cell is a placement, so water poured from A's side is A's, and its ticks run in A.

### 3.7 Loading and saving pairs

- **Paired tickets.** A ticket on a band chunk adds the same ticket on its source, and the other way round. Both use the public ticket API with v2's neighbour ticket levels (`transfer-retention.md` §6).
- **A band chunk becomes full only once its source is full.** Before that, it does not exist as a loaded chunk, so nothing can write to it. The band chunk is filled from the source on promotion (§6.3). This is a cross-storage dependency of the same kind as v2's "home loads first" gate.
- **Saving.** Both copies are saved as ordinary chunks. Region files stay readable by external tools; band chunks look like a turned duplicate.
- **Recovery.** Each pair carries a stamp that is bumped on every mirrored write and saved with both chunks. On load, if the stamps differ (a crash between the two saves), the newer chunk's ownership masks win, and every non-owner cell is refreshed from its owner.
- **Invariant check.** `/atlas check` and a dev flag compare every loaded pair cell by cell, along with masks and block entity placement.

---

## 4. Entities and frames

### 4.1 Valid frames

An entity is in its frame's storage, and its position must be valid there (§2.1). In a band, a position is valid in both frames, and the entity may be in either. Everything around it is present in both, so its physics, collisions, AI and pathfinding are vanilla in whichever frame it is in.

A **transfer** moves an entity from A's storage to B's: position by `G_AB`, yaw and velocity turned by `Rot_AB`, passengers and vehicle as one group (v2 `FaceTransfers`). Unlike v2, transfers are invisible: both frames show the same world, so nothing visibly jumps.

### 4.2 Players

- A player keeps its frame until it is more than `C` past the nominal seam, plus ½ block of hysteresis. It then transfers to the region that owns its position. Walking back, it transfers at `C` on the other side, so there are `2C` blocks of hysteresis.
- Everything within `R` of a player is then valid in the player's frame (`H ≥ C + R`). Its crosshair, reach, attacks and builds all work in one frame.
- **Player groups.** Players in bands within `2R` of each other are grouped (union-find, recomputed every 20 ticks). A group takes one frame that is valid for every member, preferring the frame most members already have. If no frame is valid for all of them (a chain reaching from one side's own area to the other's), the group splits at the nominal seam. That needs at least three players strung across a band.
- **Interaction pull.** When a player uses or opens a block whose owner is in another frame, the player's group transfers to the owner's frame first, if it is valid there. GUIs, range checks and mod packets then see one frame. Claims stop at `C`, so the player is always valid in the claimer's frame.
- **Client side.** The local player transfers on its own client, as in v2 (`FaceTransferPayload`, server check, no teleport). There is no camera ease: the client re-expresses its position, yaw and velocity, swaps its home storage (§7), and the picture is unchanged.

### 4.3 Other entities

- **Forced transfer:** at `H` past the seam, to the frame that owns the position. Projectiles and items keep their world-space velocity, turned by `Rot`.
- **Followers.** A non-player entity in a band joins the frame of the nearest player within `R`, if its position is valid there. This is checked every 20 ticks, and at most once per 40 ticks per entity (v2 `TransferCooldown`). A zombie chasing a player across the seam is already in the player's frame before it gets close.
- **Self-moved entities stay put.** If an entity's own code moves it into another storage (for example Create re-attaching a contraption to its bearing with `setPos`), the entity is marked **anchored** for 200 ticks, and followers leave it alone. Code that puts an entity next to a block entity is expressing which frame it belongs to.
- **No players nearby:** entities keep whatever frame they are in. Chunks only tick near players anyway, and forced-loaded areas stay as they are.

### 4.4 Memories and held positions

A transfer translates held positions with `G` instead of clearing them. This is main's `FrameTranslators` / `BuiltinFrameTranslators`, generalised from lap offsets to rigid motions: brain memories, `WalkTarget`, homes, bee hives and flowers, leash anchors, and `xo/yo/zo`. A position that is not valid in the new frame (deep in the old region, outside the band) is cleared, as v2 does today. A path is recomputed.

Pathfinding sees the band, so a mob can path through the seam out to `H`. Beyond that, the path ends at the band and resumes after the forced transfer.

---

## 5. Bridges into the other frame

Most cross-seam behaviour needs no bridge, because the band puts the other side into each frame. These choke points are what remains: places where a lookup in one storage must also find things that are physically present but stored in the other frame. Each is a single vanilla method, following main's bridge list (`archive/islands-and-lift-tables.md` §7.2) with `G` in place of nearest image.

| Bridge | Hook | Behaviour |
|---|---|---|
| Entity box queries | `EntitySectionStorage.getEntities(AABB, …)` | A box that overlaps a band also queries the other storage with `G(box)`, and adds those entities. Presence is right: hoppers, pressure plates, tripwires, beacons, detector rails. Their positions are in their own frame (§9). |
| Player proximity | `getNearestPlayer`, `hasNearbyAlivePlayer`, `PlayerList.broadcast` | Distance to a player in the other frame is measured from `G(player pos)`. Covers spawners, trial spawners, vaults, sound and particle broadcast. |
| POIs | `PoiManager` find, take, range queries | Registered at the owner only (§3.5). A query that overlaps a band also searches the other storage, and returns positions mapped into the querier's frame. They are valid there, so villagers can claim and walk to a bed across the seam. Claims and occupancy hit the single owner record. |
| Game events | `GameEventDispatcher` | Events in a band are also dispatched to the other storage's listeners at `G(pos)`. Covers sculk sensors and wardens. |
| Interaction range | `Player.canInteractWithBlock`, `Container.stillValidBlockEntity` | The block entity's position is mapped into the player's frame before the distance check. A safety net behind the interaction pull (§4.2). |
| Structure lookups | `StructureManager.getStructureWithPieceAt` | Bands are queried through `G`, so raids, monuments and fortress spawns see across. Structures never straddle the nominal seam (§6.4), so this is rare. |

Explosions get no bridge: their block writes are shared (§3.2). Entities near a player are in the player's frame (§4.3), so knockback is right in the usual case.

---

## 6. World generation

### 6.1 Each cell is generated once, by its nominal owner

A region's generator produces its own area only. Band chunks are not generated; they are filled as copies (§6.3). Each storage therefore holds one generated copy of every cell, so the two sides can never disagree.

### 6.2 Continuous terrain without a global lattice

Main needed periodic noise and a world size that divides every grid. V2 sampled flat noise on the cube's surface. Here, regions can be glued in any way, so continuity comes from blending across each seam:

- Within `D` blocks of a seam (default 32), the owner evaluates both its own density and climate at the cell, and the neighbour's at `G(cell)`. It blends them with a weight that is ½ at the nominal seam and 0 at `D`. The other side does the mirror image, so the two meet with the same value at the seam.
- Both are pure functions of position, so there is no ordering between the two regions' generation.
- This replaces v2's mean-of-two-densities barrier rule, applied across a band instead of one cell. Terrain, caves, aquifers and biomes run continuously over the seam, with no ridge. It costs two density evaluations inside `D` only.
- Regions don't have to share a coordinate lattice or a noise period, and edges don't have to come from one parent plane. This is the sense in which seams don't have to line up: anything glued edge to edge meets smoothly.

### 6.3 Filling the band

When a pair is promoted (§3.7), the band chunk is filled from its source: sections turned by `Rot`, ownership masks copied, light computed locally. This is a section-level copy with a 16×16 index permutation per section.

### 6.4 Features, carvers and structures

- **Version 1:** a generation write that crosses the nominal seam is dropped (v2's `WorldGenRegion` guard). Trees within a few blocks of a seam are cut.
- **Later:** a write past the seam is recorded on the band proto-chunk, and applied as a claimed placement when the pair is promoted. Both halves of a pair are always generated before either is played in (§3.7), so this is deterministic, and no player edit can be overwritten. Where both sides wrote the same cell, the nominal owner wins.
- **Structures:** kept only if the whole box is inside one own area, with a margin (v2 §4.5).

---

## 7. Client

### 7.1 Storage and tracking

The client keeps v2's per-region chunk store (`FaceChunks`, generalised to regions). The server tracks for each player:

- the home storage's square, which includes its bands;
- each neighbour's square around the player's virtual position `G(pos)`, as in v2's `NeighbourViews` and `CubeTrackingView`.

Band cells are therefore sent twice, once in each copy. That keeps a transfer free (v2's transfer retention), and it is the only extra traffic.

### 7.2 Rendering

- **The home storage, bands included, is vanilla's world.** Everything within `H` of the seam is drawn by vanilla's renderer from real local chunks, with correct culling, AO and light (the skirt covers the outer edge).
- **Neighbours beyond the band** are drawn by v2's `NeighbourRenderer`, turned about Y. Neighbour sections that lie within `H` of the seam are skipped, because the home band already draws them. Neighbour band and skirt chunks are never drawn. The cut is on a chunk boundary, and the meshes meet exactly: the home side culls against the skirt copy, and the neighbour side against its own chunks.
- A turn about Y keeps up-vectors, so cylindrical fog and sky are right on neighbours. This fixes v2's known spherical-fog limitation.
- **Entities:** those in home storage are drawn natively. Those in a neighbour's storage (including its band) are drawn turned (v2). A remote entity that transfers interpolates from its re-expressed old position (`EntityLerpMixin`).
- **Sodium:** v2's `SodiumNeighbours` path, with the same section clipping.

### 7.3 Transfers on the client

Crossing `C` swaps the home storage with the neighbour, as v2's crossing does, minus `FaceCamera`'s ease and `EntityTurns`. The new home's band already holds what the player was standing in, so the first frame after the swap is the same picture, re-expressed.

---

## 8. Networking

There are no new packet types beyond v2's transfer payloads. Every position on the wire is a plain storage position, which the client resolves by region. Mirrored writes are ordinary block updates in the other copy. Main's 35 normalisers are not needed.

---

## 9. What works across a seam

| | Main | V2 | Rotated seams |
|---|---|---|---|
| Walk, see, fly across | Yes | Yes (turn, camera ease) | Yes, and the transfer is invisible |
| Terrain meets | Yes (periodic noise) | Ridge, ~3 blocks | Yes (blend) |
| Place, break, build straddling | Yes | No (barrier) | Yes |
| Redstone, pistons, fluids, explosions | Yes | No | Yes (§3.2, §3.5) |
| Vanilla multiblocks (portal, beacon, conduit, golem) | Yes | No | Yes: block state reads through turned copies |
| Hopper / pipe into a container across | Yes | No | Yes (§3.4) |
| Modded block entities away from seams | **Broken wherever lap ≠ 0** | Yes | Yes |
| Modded multiblock straddling, built from one side | Broken (lap ≠ 0, plus seam deltas) | Not possible | **Yes** (claims, §3.3) |
| Modded multiblock built from both sides | Broken | Not possible | Partial (below) |
| Mob chases player across, combat, pushing | Yes | No (memories cleared) | Yes (followers, §4.3) |
| Villagers, POIs across | Yes | No | Yes (§5) |
| Packet changes | ~35 normalisers | None | None |
| World-size and lattice constraints | Period a multiple of 3072, periodic noise | Cube only | Any chunk-aligned gluing |

**Remaining limits**, all inside bands:

1. **Block entity ↔ block entity across frames.** When two block entities owned by different frames talk to each other, a `getBlockPos()` delta is wrong: rotated as well as offset. Example: a Create shaft built from B pressed against a gearbox built from A. Vanilla doesn't do this (its multiblocks use block states). Mitigation: `api/Atlas.toFrame(level, pos, ofFrame)` for mods, and a later "a placement joins the structure it touches" claim rule (open decision 3).
2. **Block → entity geometry across frames.** A box query returns entities in the other frame at their own positions (§5). Presence logic is right; geometry such as a fan's push direction or a beam's grip is wrong. This happens only when the entity's nearest player is in a different frame from the block's owner, which is uncommon with followers and the interaction pull.
3. **Directional APIs outside capabilities.** A mod method that takes a `Direction` on another block entity gets an untransformed side.
4. **Blocks with an incomplete `rotate`.** These show the wrong facing in the turned copy. The same block would also place wrongly in a rotated structure.
5. **Direct section writes.** Mods that write `LevelChunkSection` without going through `LevelChunk.setBlockState` aren't mirrored. The invariant check (§3.7) finds them.
6. **Cone points** (§2.3).

---

## 10. Reuse

| From | What | Verdict |
|---|---|---|
| v2 | Per-face storage layout, `CubeGeometry` patterns, settings in the generator codec, world preset and payload | **Adapt** to `AtlasGeometry`: regions, seams, `G`, bands, skirts, cone points. Pure Java, unit tested. |
| v2 | `FaceTransfers`, `FaceTransferPayload`, `TransferCooldown`, client-driven crossing, server check | **Keep**, with new trigger depths (§4) and no camera ease. |
| v2 | `NeighbourViews`, `CubeTrackingView`, `ChunkTrackingViewMixin`, `TrackedEntityMixin`, transfer retention | **Keep**. The virtual position becomes `G(pos)`; add paired tickets (§3.7). |
| v2 | `NeighbourRenderer`, `FaceChunks`, `ClientChunkCacheMixin`, `EntityLerpMixin`, `SodiumNeighbours` | **Keep**. Turns about Y only; clip band sections (§7.2). |
| v2 | `LevelMixin` / `WorldGenRegionMixin` write guard | **Becomes** the mirror at `LevelChunk.setBlockState` (§3.2); keep the generation half for §6.4 version 1. |
| v2 | `CubeChunkGenerator`, `blendTowardNeighbour` | **Adapt** to the seam blend (§6.2). Drop `CubeNoise`'s surface sampling. |
| v2 | Barrier, filler, edge filler, edge air, edge bedrock, `NeighbourShapes`, `FaceCamera`, `EntityTurns`, per-face sun | **Drop.** The band holds real blocks, and gravity never turns. The sky follows whatever the atlas models. |
| main | `FrameTranslators`, `BuiltinFrameTranslators` | **Port** from lap offsets to rigid motions (§4.4). |
| main | Bridge list (§7.2 there), `IslandGraph` union-find | **Port** as §5 and player groups (§4.2). |
| main | `archive/mod-compatibility.md` findings | **Use as the test list** for §9: each lap≠0 finding should now pass; each seam finding should pass when built from one side. |
| main | Storage canonicalisation, packet normalisation, islands, lap tags, periodic noise | **Not needed.** |

---

## 11. Phases

| # | Phase | Done when |
|---|---|---|
| 0 | **Geometry.** `AtlasGeometry`, settings and presets for torus, pillowcase and a test atlas with all four turns. | Unit tests: `G` round trips and composes; band and source chunks are one to one; every own-area cell is in exactly one region; cone point squares are found; `Rot` on every vanilla block state round trips. |
| 1 | **Band, nominal ownership.** Pairs, paired tickets, band fill, shared writes, owned reactions, scheduled-tick dedup, server block entity lookup, capabilities, light and skirt, save and recovery, `/atlas check`. | Gametests, for each of the four turns: a redstone line, a repeater chain and a comparator across; a piston pushing across; water flowing across; a nether portal and a beacon pyramid straddling; a hopper into a chest across. `/atlas check` is clean after every test. |
| 2 | **Entities.** Valid frames, player and forced transfers, invisible client crossing, ported frame translators, player groups, followers, anchoring. | Gametests: a zombie chases a mock player across and back; items, arrows and minecarts cross; no ping-pong walking along a seam; a group of two players crossing toward each other ends in one frame. |
| 3 | **Bridges.** §5 table, interaction pull. | Gametests: an entity in the other frame holds a pressure plate down; a villager claims a bed across; a sculk sensor hears across; a chest across stays open. |
| 4 | **Claims.** Ownership masks, placement and removal rules. | Gametest: a test-only multiblock whose controller stores absolute part positions, built across a seam from one side, forms, ticks, saves and reloads. The same multiblock built from both sides is recorded as limit 1. |
| 5 | **Generation.** Seam blend, band fill on promotion, generation write drop, structure filter. | Gametest: no density break at any seam; screenshots show no visible seam. |
| 6 | **Client.** Band-native rendering, neighbour clipping, client block entity replicas, crossing without ease. | `DevScript`: walking across every turn type shows nothing changing in the frame; no corrections in the log. |
| 7 | **Compat.** Sable (sub-levels transfer like entities, and anchor their passengers), C2ME (paired tickets under its chunk system), Sodium. | Full suite with `-PwithSable`, `-PwithC2me`. |
| 8 | **Later.** Cross-seam generation writes (§6.4), structure-join claims, `api/Atlas`. | |

Phase 1 is the riskiest, so start it with a throwaway: two flat regions, one 90° seam, and only redstone dust, repeaters and pistons. It proves the owned-reaction rule before anything else is built on it.

---

## 12. Open decisions

| # | Question | Recommendation |
|---|---|---|
| 1 | `C`, `R`, `H` defaults | 32 / 32 / 64 (2, 2, 4 chunks), configurable. `H ≥ C + R` is checked at load. |
| 2 | Server block entity at the non-owner copy: owner's object, or a synced replica | **Owner's object** (§3.4). A replica loses writes and can duplicate items. |
| 3 | Should a placement join the frame of the structure it touches, rather than the placer's? | Not at first. Count cross-frame block entity neighbours with `/atlas scan`, and add it if limit 1 shows up in practice. |
| 4 | Followers and the interaction pull for players | Both on. They are what make combat and GUIs work across seams. |
| 5 | Cross-seam generation writes | Drop in version 1; merge at pair promotion later (§6.4). |
| 6 | Cone point squares | Empty and not valid (§2.3). A visible marker (a single bedrock column at the vertex) can be added if the gap is noticeable. |

---

## 13. Risks

- **Owned reactions are the heart of the design**, and any reaction path that bypasses the hooks in §3.5 runs twice. Examples are mod code that calls `neighborChanged` directly, or a custom `NeighborUpdater`. Doubled pistons or dispensers would duplicate items. Mitigations: the phase 1 prototype, `/atlas check`, and a debug counter for reactions at non-owner copies.
- **Hot paths.** `LevelChunk.setBlockState`, `getBlockEntity` and neighbour dispatch gain a check. Away from seams, it is one boolean on the chunk.
- **Double loading near seams.** Each band is loaded twice, and generation work rises near seams (v2 already saw five times the generation on a fresh world). The band is bounded at 5 chunks per side per seam.
- **C2ME** replaces the chunk system that paired tickets and the promotion gate depend on.
- **Mods that write sections directly, or keep chunk data outside sections**, aren't mirrored (limit 5).
- **Follower churn** when players in different frames hover near each other at `2R`. The cooldown bounds it, and a counter in `/atlas scan` makes it visible.
