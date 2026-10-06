# Orbifold Implementation

**Building the hexagonal p2 world with rotated, seamless joins**

Target: Minecraft 1.21.1, NeoForge · Status: plan · Branch: `orbifold` (from `v2` at `b55c3be`) · Implements **Best Wrapping Plan** (`alpha-omega-best-wrapping-plan.md`, the world shape, projection and terrain requirement) with **Rotated Seams** (`rotated-seams.md`, how blocks and entities work across the joins). Section numbers prefixed `W` refer to the wrapping plan, and `RS` to rotated seams.

> `design_docs/` is in `.gitignore`, so this plan and its progress log live outside version control, as the earlier ones do.

---

## 0. Summary

The wrapping plan defines *what* the world is: the plane modulo the group `Γ` (p2, hexagonal lattice). One copy of the world is a 15360 × 6656 tile with three seams: one translation and two 180° folds (W §4). Rotated seams defines *how* things work across joins that turn: bands of mirrored cells, owned reactions, claims, entity frames and a few bridges (RS §3–5).

Put together, they become simpler than either alone. The orbifold has only one region:

- **One storage.** The tile plus a band and skirt on every side, all as ordinary upright chunks around spawn in the overworld. Every "pair" in RS is two places in the same storage. There is no storage grid, and the vanilla spawn search, region files, `/tp` and maps all see one ordinary area.
- **A frame is an element of `Γ`, not a region.** An entity in the band is just at a non-canonical position. A transfer applies a group element and stays in the same storage.
- **There are no holonomy gaps at cone points.** `Γ` acts on the whole plane, so every band cell has exactly one canonical source. Near a cone point, one tile cell can have several band copies. This is W §7's "some blocks appear twice", and RS §2.3's empty corner square is not needed.
- **Beyond the band, the client draws images of the tile.** v2's neighbour renderer becomes an **image renderer**: one view of the same storage per nearby element of `Γ`, drawn with that element's transform.

The work splits into four tracks that can run partly in parallel after the geometry lands:

| Track | Phases |
|---|---|
| Geometry and sky | 1 Geometry → 2 Sky |
| Terrain | 3 Generator skeleton → 9 Invariant terrain → 10 Nether |
| Band and entities | 4 Band copies → 5 Entities → 7 Bridges → 8 Claims |
| Client | 6 Image views (after 4) |

---

## 1. How the two plans fit

### 1.1 Decisions this plan makes where the two documents differ

| Topic | Wrapping plan | Rotated seams | This plan |
|---|---|---|---|
| Regions | One tile | Many regions, each with its own storage | **One region, one storage.** Pairs and copies are within the same storage. |
| Seam continuity | `Γ`-invariant noise (W §8) | Density blend across each seam (RS §6.2) | **Invariant noise first.** The blend covers what can't be made invariant (aquifer points, carver seeds). It is measured per seam, and only added where a mismatch is found. |
| Cone points | Blocks appear twice | Empty corner square | **Copies, not gaps.** A tile cell has a *copy set* of 1 to 3 band cells, not a single partner. |
| Frames | Each player's frame is the plane | One frame per region | **A frame is the element of `Γ` that maps a band cell to its source.** In the band, at most four elements apply (§2.4). |
| Rendering past the band | Not covered | v2 neighbour views of other storages | **Image views** of the same storage (§6). |
| Sky | Hexagonal Weierstrass projection | Out of scope | W §6, through main's `PlanetProjection` and `LocalSky` API (§2 phase 2). |
| Size | k = 2, 4 or 8 (open) | — | **Presets** small (3584 × 3072), medium (k = 2), normal (k = 4, default) and large (k = 8), in `OrbifoldSize`. Saves keep `size_factor` for k sizes and use `size: "small"` otherwise. Gametests use the default; `-PorbifoldSize=small` runs them at small. |

### 1.2 What carries over from each branch

| Source | Kept | Notes |
|---|---|---|
| v2 | Transfers (`FaceTransfers`, `FaceTransferPayload`, `TransferCooldown`, client-driven crossing) | Trigger depths change (RS §4). There is no camera ease or upright turn. |
| v2 | `NeighbourViews`, `CubeTrackingView`, `ChunkTrackingViewMixin`, `TrackedEntityMixin`, transfer retention | A virtual position is `g⁻¹(pos)` for each nearby image `g`. Same storage. |
| v2 | `NeighbourRenderer`, `FaceChunks`, `ClientChunkCacheMixin`, `EntityLerpMixin`, `SodiumNeighbours` | Become image views (§6). `FaceChunks` becomes one array over the storage footprint. |
| v2 | Settings in the generator codec, world preset, configuration payload, `GameTestServerMixin`, `TestChunks`, `TestPlayers`, `DevScript`, mixin audit, compat plugins | Renamed from cube to orbifold. |
| v2 | Sky shell: atmosphere, `StarField`, `SpaceFade`, the 26 local-time mixins | `LocalSky`'s inside goes back to a projection (phase 2). |
| main | `PlanetProjection` / `Position` record, projection-based `LocalSky` | Inputs become world coordinates instead of lap fractions. |
| main | `PeriodicLattice`, `ImprovedNoiseMixin`, `PerlinNoiseMixin`, `CanonicalPositionalRandomFactory`, shift mixins | Starting point for invariant noise (phase 9). The lattice becomes hexagonal, plus point symmetry. |
| main | `FrameTranslators`, `BuiltinFrameTranslators`, `IslandGraph` union-find | Translate by a rigid motion instead of a lap offset (phase 5); player groups (phase 5). |
| main | `archive/mod-compatibility.md` findings | The compat test list (phase 11). |

Dropped from v2: `CubeGeometry`, `CubeFace`, `CubeSun`, `CubeNoise`, `CubeChunkGenerator` (replaced), the barrier, `FillerBlock`, `EdgeFillerBlock`, `EdgeAirBlock`, `EdgeBedrockBlock`, `NeighbourShapes`, `FaceCamera`, `EntityTurns`, `SubLevelGravity` and the cube-only gametests (`FaceGameTests`, `CollisionGameTests`, cube parts of `TerrainGameTests`).

---

## 2. Geometry reference

All numbers are for size factor `k`, with the k = 4 values in brackets. The small preset (3584 × 3072, north row −1152, spawn (0, 49)) isn't a multiple of the base pair; `OrbifoldSize` holds `a`, `b`, the fold row and spawn for every preset. Coordinates are block `x` (east) and `z` (south).

### 2.1 The tile

| Quantity | Formula | k = 4 |
|---|---|---|
| `a` | `3840·k` | 15360 |
| `b` | `3328·k` | 13312 |
| Tile `x` | `[−a/2, a/2)` | `[−7680, 7680)` |
| North fold row `zN` | from W §4 "Sizes" table | −5248 |
| South fold row `zS` | `zN + b/2` | 1408 |
| Tile `z` | `[zN, zS)` | `[−5248, 1408)` |
| Cone points | `N (0, zN)`, `F (±a/2, zN)`, `E (a/4, zS)`, `W (−a/4, zS)` | |
| Spawn | from W §4 | (0, −44) |

The fold rows for k = 2 and 8 come from W §4's table. They are stored as data, not derived, because they are rounded to multiples of 128.

### 2.2 Generators of `Γ` on block cells

For cell `(x, z)` (the cell's minimum corner), the generators near the tile are:

| Name | Cell map | Turn |
|---|---|---|
| `T±` | `(x ± a, z)` | 0° |
| `R_N` (north fold) | `(−1 − x, 2·zN − 1 − z)` | 180° |
| `R_S` (south fold) | `(a/2 − 1 − x, 2·zS − 1 − z)` | 180° |

All of these map chunks to chunks, because `a/2`, `zN` and `zS` are multiples of 16 (of 128, in fact). A 180° turn uses `Rotation.CLOCKWISE_180` for states, directions and shapes, and adds 180° to yaw.

### 2.3 Canonicalisation

`canon(x, z) → (x', z', g)`, valid within `H + 16` of the tile:

1. If `z < zN`, apply `R_N`. If `z ≥ zS`, apply `R_S`.
2. Wrap `x` into `[−a/2, a/2)` with `T±`.
3. `g` is the composition applied. Its turn is 180° if a fold was applied.

The order is correct because a turn about `F` is `T ∘ R_N`, and so on: `Γ` is a group, so every path through the corner gives the same cell.

### 2.4 Storage footprint, band, skirt, copy sets

- **Footprint:** the tile grown by `H + 16` on every side. With the defaults (`H = 64`), that is 15520 × 6816 blocks at k = 4. Chunks outside it are void and never needed.
- **Band:** footprint cells outside the tile within `H`. **Skirt:** the next 16.
- **Source** of a band or skirt cell: `canon(cell)`. It is always in the tile.
- **Copy set** of a tile cell: every band or skirt cell whose source it is. Most tile cells have none. Cells near an edge have one. Cells near a tile corner or a cone point have up to three. Computed by applying the generators and their pairwise compositions, and keeping results inside the footprint.
- **Near cone points:** past the north fold near `N`, the band is the 180° turn of the tile cells just south of `N`, so the band can hold a copy of cells the player is standing next to. This is the visible "appears twice" (W §7), and it is correct: both are the same blocks.
- **Frame elements in the band:** an entity at a band position `p` is in frame `g`, where `canon(p) = g·p`. There are at most four distinct `g` values per neighbourhood: identity, `T`, a fold, and fold∘`T` near `F`.

### 2.5 Depths

`C = 32`, `R = 32`, `H = 64`, skirt 16 (RS §2.1, open decision 1 there). The setting `band_chunks` (default 4) sets `H`, with `C = H − R`.

---

## 3. Phases

Each phase lists its tasks, the files it touches, its tests and when it is done. Gametests run as subsets with `-Pgametests=<Class>[.method]`, and the full suite runs at the end of each phase.

### Phase 0: Branch and strip

**Tasks**

- [x] Create the `orbifold` branch from `v2`.
- [ ] Delete the dropped classes (§1.2), their mixins, registrations, assets, lang entries and tests.
- [ ] Replace `Cube.of(level)` call sites with a stub `Orbifold.of(level)` that returns `null`, so the game runs as vanilla.
- [ ] `LocalSky` returns the vanilla sample until phase 2.
- [ ] Keep the transfer, neighbour-view and renderer classes compiling against the stub. They will be adapted, not rewritten.

**Done when:** `./gradlew build` passes, the mixin audit passes (`-PauditMixins -PquitAfterAudit`), `ModLoadGameTests` passes, and a client starts a vanilla world with the atmosphere.

### Phase 1: Geometry and settings

**Tasks**

- [ ] `orbifold/OrbifoldGeometry`: tile, generators, `canon`, `isTile`, `isBand`, `isSkirt`, `copies(cell)`, chunk forms of each, frame elements, `transform(g)` for positions, vectors, yaw, `Direction`, `Rotation` and AABBs. Pure Java, no Minecraft types except in a thin adapter.
- [ ] `orbifold/Orbifold`: `of(level)`, the per-level geometry.
- [ ] `OrbifoldSettings` (`size_factor`, `band_chunks`) in the codec of a new `OrbifoldChunkGenerator` (phase 3 fills it). Defaults come from `alpha_omega-common.toml`.
- [ ] World preset `alpha_omega:orbifold` and a Customize screen (size factor 2, 4 or 8). Adapt `CubePresetScreen`.
- [ ] `OrbifoldPayload` to clients, replacing `CubePayload`.
- [ ] `/orbifold info`: tile, cone points, the current cell's source, copy set, frame and depth past the nearest seam.
- [ ] `GameTestServerMixin` swaps in the orbifold preset.

**Tests** (`OrbifoldGeometryTest`)

- Every cell in the footprint canonicalises to exactly one tile cell, and tile cells to themselves.
- Generators compose as the group: `R_N² = R_S² = id`, and `R_S ∘ R_N` is a lattice translation by `±(a/2, b)`.
- Every W §4 identification maps to the same canonical cell.
- No cell is its own image, and no chunk is its own image.
- Copy sets are complete: `c ∈ copies(s)` exactly when `canon(c) = s`.
- Every chunk maps to a whole chunk.
- `transform` round trips for positions, yaw, `Direction` and AABBs.
- `BlockState.rotate(CLOCKWISE_180)` applied twice is the identity for every registered vanilla block state. Run this as a gametest, because it needs the registry.

**Done when** the tests pass and `/orbifold info` reads correctly at spawn, `N`, `F`, `E` and `W`.

### Phase 2: Projection and sky

Can run in parallel with phases 3 to 8.

**Tasks**

- [ ] Restore main's `sky/PlanetProjection` (`Projection`, `Position`). Change the input to world block coordinates, because the orbifold is not a square torus.
- [ ] `sky/HexOrbifoldProjection`: W §6's reference implementation, parameterised by k, `X₀`, `Z₀`. Longitude in turns, matching `Position`. Includes `skySpeed`.
- [ ] Rebuild `LocalSky`'s inside on the projection, from main's version: `sample`, `isDay`, `skyDarken`, `localDayTime`, `equivalentTimeOfDay`, `sleepTimeAddition`. Keep the public API, so the 26 time mixins and the client sky mixins are unchanged.
- [ ] Celestial rotation from latitude, longitude and heading (main's `LevelRendererSkyMixin` form).
- [ ] Night skip: next first light where most sleepers are (main's rule). Sleeping at a cone point: a pole, so it uses main's polar handling.
- [ ] F3 `Sun:` line, `/orbifold time`.

**Tests** (`HexOrbifoldProjectionTest`, `LocalSkyTest` rewritten, `LocalTimeGameTests`)

- Spawn maps to 0° 0° with heading 0.
- The cone points form a regular tetrahedron (all six angles 109.47°).
- Every identification gives the same latitude and longitude, and the heading changes by exactly π under a fold.
- Sky speed is 0 at the cone points and within 1% of W §4's mean at k = 2, 4 and 8.
- A band position gives the same sky as its source.
- `LocalTimeGameTests` at spawn and 5,204 blocks north (the pole).

**Done when** the tests pass, and a dev-client screenshot at spawn and at `N` shows the expected sun path.

### Phase 3: Generator skeleton

**Tasks**

- [ ] `worldgen/OrbifoldChunkGenerator` extends `NoiseBasedChunkGenerator` (v2's pattern). Tile chunks generate vanilla terrain. Chunks outside the footprint are void. Band and skirt chunks generate empty, to be filled in phase 4.
- [ ] A generation write guard (adapted from v2's `WorldGenRegionMixin`): drop any write outside the tile during generation (RS §6.4, version 1).
- [ ] Structure filter: keep a start only if its box is in the tile with an 8-block margin from every seam (v2's `ChunkGeneratorMixin` check, with tile bounds).
- [ ] Spawn: the search starts at W §4's spawn point.

**Tests** (`OrbifoldGameTests`): void outside the footprint; no structure box crosses a seam; spawn is within 64 blocks of W's spawn point.

**Done when** a world generates the tile. Seams are visibly discontinuous until phase 9, which is expected.

### Phase 4: Band copies

This is the core of the work (RS §3), with nominal ownership only. **Start with a spike**: one seam (`T`, at `x = a/2`), only dust, repeaters and pistons, with the owned-reaction rule. Then repeat the spike across the north fold. Keep the spike branch only if it comes out clean; otherwise rewrite.

**Tasks**

- [ ] `band/CopyLinks`: per loaded chunk, whether it has a source or copies, and the list of linked chunks with their `g`. A field on `LevelChunk` (mixin), so chunks away from edges pay one check.
- [ ] Paired tickets: a ticket on a band or skirt chunk adds one on its source, and a ticket on a tile chunk with copies adds them on the copies. Use v2's neighbour ticket levels.
- [ ] Promotion gate: a band or skirt chunk does not become full until its source is full. On promotion, fill it from the source: sections turned by `g`, ownership masks, light recomputed locally. Copy a 16×16 index permutation per section for 180°, and use a straight copy for `T`.
- [ ] Mirrored writes at `LevelChunk.setBlockState` (RS §3.2): write the turned state into every linked copy, with sections, heightmaps and light updated; no block entity creation, `onPlace` or `onRemove`; a re-entry guard. The mirror runs right after the section write, before `onRemove`/`onPlace`. Mirrored writes send packets only, no neighbour updates.
- [ ] Ownership: per-section bitset attachment (empty at first: everything nominal). The owner of a band cell is its source unless claimed (phase 8).
- [ ] Owned reactions (RS §3.5, as revised by the spike): forward neighbour and shape updates aimed at a non-owner copy to the owner, at `BlockStateBase.handleNeighborChanged`, `Level.neighborShapeChanged` and `Level.updateNeighbourForOutputSignal`.
- [ ] Block entity claims: a block entity created at a copy (pistons' `setBlockEntity`) owns that cell while it exists. Skip random ticks and precipitation by mask in `ServerLevel.tickChunk`. Do not register block entity tickers at non-owner copies.
- [ ] Scheduled ticks run where scheduled. Skip scheduling if a linked copy already has the same tick pending.
- [ ] Block entities (RS §3.4): `Level.getBlockEntity` and `LevelChunk.getBlockEntity` at a non-owner copy return the owner's object. `Level.getCapability` redirects to the owner with `side` turned. Block events run once, and their packets are sent for every copy.
- [ ] Client block entity replicas: chunk packets for non-owner copies carry the owner's update tag. `ClientboundBlockEntityDataPacket` is sent for every copy.
- [ ] POIs: registered at the owner only (`ServerLevel.onBlockStateChange`).
- [ ] Saving and recovery: a per-link stamp bumped on mirrored writes and saved in both chunks. On load, the newer chunk's masks win, and non-owner cells refresh from their owners.
- [ ] `/orbifold check [radius]`: compare every loaded copy with its source (states turned, masks, block entity placement). Also a dev flag `-PcheckCopies` that runs it after every gametest.
- [ ] Counters for `/orbifold scan`: mirrored writes per tick, reactions skipped at non-owners, and reactions that ran at a non-owner (should be 0).

**Gametests** (`BandGameTests`), each run across `T`, `R_N`, `R_S` and at `F`:

1. A dust line, a repeater chain and a comparator reading a chest, all across the seam.
2. A piston pushes a block across; a sticky piston pulls it back.
3. Water poured on one side flows across; lava meets it and makes cobblestone at the seam.
4. A nether portal straddling the seam lights and teleports; a beacon pyramid straddling it activates.
5. A hopper on one side fills a chest on the other, through the capability path.
6. Breaking either copy breaks both, and drops once.
7. Pair consistency: after each test, `/orbifold check` is clean and the "ran at a non-owner" counter is 0.
8. Recovery: save, corrupt one chunk's stamp, reload, and the copy is restored.

**Done when** all of these pass for every seam type.

### Phase 5: Entities

**Tasks**

- [ ] Rework `FaceTransfers` into `transfer/FrameTransfers` (RS §4). Validity: tile and band out to `H`. Players transfer at `C + ½`, others at `H`. A transfer applies `g`: position, yaw and velocity turned, passengers and vehicle as a group. The storage doesn't change.
- [ ] Client-driven player transfer via `FaceTransferPayload` (rename it `FrameTransferPayload`). The server checks the claim as v2 does. No camera ease; the client re-expresses its own state.
- [ ] Port main's `FrameTranslators` and `BuiltinFrameTranslators` to rigid motions. Positions that are not valid after the transform are cleared, and paths are recomputed.
- [ ] Player groups (`IslandGraph`'s union-find, recomputed every 20 ticks), followers, the interaction pull and anchoring (RS §4.2–4.3), with `TransferCooldown`.
- [ ] Sable: sub-levels transfer like entities. Their passengers travel with them (v2's `SubLevelTransfers`, minus gravity). Drop `SubLevelGravity`.

**Tests** (`FrameTransferTest` unit; `TransferGameTests` rewritten)

- Items, arrows, minecarts and mobs cross every seam type and both directions at `F`, and keep their world velocity.
- A zombie chases a mock player across a fold and back without losing its target.
- A villager keeps its home and job site across a transfer.
- No ping-pong walking along a seam, or around `N` at 20 blocks.
- Two mock players walking toward each other across a seam end in one frame.
- A player standing still at a cone point does not transfer.

**Done when** these pass, and `RetentionGameTests` (adapted) still shows no chunk churn on a crossing.

### Phase 6: Image views

Players need the tile beyond the band in view. This adapts v2's neighbour views from other storages to images of the same storage.

**Tasks**

- [ ] Server: `NeighbourViews` becomes `ImageViews`. For each element `g` whose view square around `g⁻¹(pos)` reaches tile cells further than `H` from the player's own frame, add tickets and tracking at `g⁻¹(pos)`. There are at most four, by §2.4. Transfer retention keeps working as in v2.
- [ ] Entity tracking: range from the nearest of the player's real and image positions (`TrackedEntityMixin`).
- [ ] Client chunk store: `FaceChunks` becomes one array over the footprint.
- [ ] `NeighbourRenderer` becomes `ImageRenderer`: one `ViewArea` per active `g`, centred on `g⁻¹(camera)`, drawn with `g`'s transform. Skip any section whose image falls inside the home footprint, because vanilla already draws it from the band. A 180° turn about Y keeps cylindrical fog right.
- [ ] Entities and block entities in image views are drawn with `g`. This includes the duplicates near cone points: a player near `N` sees their own image at twice their distance, which W §7 calls the trace of curvature.
- [ ] Sounds and particles at an image's position play at the transformed position.
- [ ] Sodium: adapt `SodiumNeighbours` to image views with the same section clipping.

**Tests:** `ImageGameTests` (tracking sets include the image squares; a mob near the far side of a seam is sent to a player at the near side). `DevScript` walks across each seam type and around `N`, taking screenshots. Check that no seam is visible apart from terrain, and that there are no gaps or doubled faces at the band edge.

**Done when** screenshots show nothing changing across every seam type in the vanilla renderer and in Sodium.

### Phase 7: Bridges

RS §5, with `g` in place of another region.

**Tasks:** entity box queries (`EntitySectionStorage.getEntities`), player proximity (`getNearestPlayer`, `hasNearbyAlivePlayer`, `PlayerList.broadcast`), POI queries (owner record, results mapped into the querier's frame), game events, interaction range (`canInteractWithBlock`, `stillValidBlockEntity`), and structure lookups. Each bridge only runs when its query box reaches the band. Use the chunk link flag (phase 4) as the fast check.

**Tests** (`BridgeGameTests`): an entity in another frame holds a pressure plate down; a spawner across the seam sees a player; a villager claims a bed across; a sculk sensor hears across; a chest across stays open while a player stands back from it.

**Done when** these pass for every seam type.

### Phase 8: Claims

**Tasks:** placement claims, removal reverting to nominal ownership, and state changes keeping the owner (RS §3.3). Block entities are created only at the claiming copy, and moving the owner moves nothing because the block is new. Pistons and contraption disassembly count as placements in the mover's frame.

**Tests** (`ClaimGameTests`):

- A test-only multiblock block whose controller stores absolute part positions, built across a seam from one side, forms, ticks, saves, reloads and still works.
- Built from both sides, it is recorded as RS §9 limit 1.
- Breaking the build returns ownership to nominal.
- `/orbifold check` stays clean.

**Done when** these pass for every seam type.

### Phase 9: Invariant terrain

Can start after phase 3 and run alongside 4 to 8.

**Tasks**

- [ ] Port main's `PeriodicLattice` / `ImprovedNoiseMixin` into `worldgen/noise/OrbifoldLattice` (W §8). Hash gradients by lattice cell reduced modulo `L1` and `L2`. The cell at `2N − q` gets the 180°-turned gradient of `q`: `(−gx, gy, −gz)`. An octave applies when its cell size, in the octave's input units, divides `a/2`, `b` and both coordinates of `2N`. Stretch the input by at most a few percent to round to a whole number of cells, as main's lattice does. At k = 4, this holds for cells of up to 256 blocks.
- [ ] `worldgen/noise/SpectralNoise` for the coarser octaves (W §8): cosines on the dual lattice about `N`, with seeded amplitudes, matching each octave's amplitude and frequency band.
- [ ] Shift noises (`shift_a`, `shift_b`) through the same lattice. `ShiftB` samples `(z, x, 0)`, so its octaves need the swapped lattice (main's two-face note).
- [ ] Integer grids: aquifer cells, biome quarts and carver seeds hash their canonical cell (main's `CanonicalPositionalRandomFactory`, with `canon` from phase 1).
- [ ] Measure: for each seam type, the mean and maximum density step across the seam. Add RS §6.2's blend only where it isn't zero, and only in a narrow strip.
- [ ] Feature writes across seams, recorded and applied as claims at promotion (RS §6.4 "later"). Only once phases 4 and 8 are in.

**Tests** (`TerrainGameTests` rewritten)

- Density is identical across every seam at sampled columns (or within the blend tolerance where one was added).
- At `N`, `F`, `E` and `W`, the density gradient is flat.
- Biomes match across each seam.
- Screenshots at each seam and cone point.

**Done when** no seam is visible in screenshots, and the density step is zero, or covered by the blend.

### Phase 10: Nether

**Tasks:** the same layout at 1/8 scale (W §4), with its own `OrbifoldGeometry` and band. Portal linking scales storage coordinates by 8, then canonicalises, so both copies of a portal link the same way. End: vanilla.

**Tests:** an overworld portal at each seam type links to a Nether portal and back to the same overworld copy set.

### Phase 11: Compat and audit

**Tasks:**

- C2ME: paired tickets and the promotion gate under its chunk system; the density compiler with the orbifold lattice (main's `ImprovedNoiseEqualityMixin` pattern).
- Sable: full `SableGameTests`.
- Sodium: phase 6.
- Mod compatibility: run main's `mod-compatibility.md` findings with Create and ZPS. Each "lap≠0" finding should pass everywhere. Each "seam" finding should pass when the build is placed from one side.

**Done when** the full suite passes with `-PwithSable`, `-PwithC2me` and `-PwithSodium`, and the mod findings are recorded in `progress.md`.

### Phase 12: Polish

Compasses and maps (heading from the projection); F3 (canonical position, frame, depth past seam, latitude and longitude); `/orbifold tp <cone point|lat lon>`; `api/Orbifold` (`canon`, `copies`, `transform`, `toFrame`, transfer events); docs.

---

## 4. Order and parallelism

```
0 ─▶ 1 ─┬─▶ 2 (sky) ───────────────────────────────────────────┐
        ├─▶ 3 ─┬─▶ 9 (terrain) ──────────────────────▶ 10 ──────┤
        │      └─▶ 4 (spike, then full) ─▶ 5 ─▶ 7 ─▶ 8 ─────────┼─▶ 11 ─▶ 12
        │                                  └─▶ 6 (image views) ─┘
```

- Phase 4 is the largest risk; the spike decides whether RS's owned-reaction rule holds.
- Phase 6 is needed before anyone can walk the world in a dev client, so start it right after 4 if hands are free.
- Phases 2 and 9 don't depend on the band.

---

## 5. Testing and tooling

- **Unit tests** for everything pure: geometry, projection, lattice hashing and copy sets.
- **Gametests** by phase, run as subsets with `-Pgametests=` during work, and as a full suite at the end of each phase. Tests that need seams use `TestChunks` to generate both sides first.
- **The copy invariant** (`/orbifold check`, `-PcheckCopies`) after every band, entity, bridge and claim gametest.
- **Counters** in `/orbifold scan`: mirrored writes, skipped and executed non-owner reactions, transfers by kind, follower churn, bridge hits.
- **`DevScript`** walks for each seam type and cone point, with screenshots and a check that the log has no movement corrections.
- **Mixin audit** after every phase.

---

## 6. Progress log

Add a dated line here at the end of each phase, as in v2's `progress.md`: what landed, test counts, measurements, and anything deferred.

- 2026-10-05 Phases 0–1 merged: cube code stripped (kept systems stubbed inactive, see the stub table in the phase 0 report), `OrbifoldGeometry`/`Motion`/`Transform`, settings, preset, `/orbifold info`. 25 unit tests, 9 gametests.
- 2026-10-05 Phase 2 merged: `HexOrbifoldProjection` behind `PlanetProjection` (world coordinates), `LocalSky` rebuilt on it with its API unchanged, celestial rotation in double precision, night skip at the sleepers' mean position, F3 and `/orbifold time`. 47 unit tests, 19 gametests. Finding: W §5's "10,408 blocks north = 0° 180°" is wrong; walking straight through cone point N (a half turn) returns to spawn, turned round.
- 2026-10-05 Phase 4 spike (`orbifold-band-spike`): the rule holds for dust, repeaters, comparators, observers, pistons and breaking across a translation and a fold, with 0 reactions at non-owners. Two rule changes adopted: forward updates instead of skipping them, and block entity claims (RS §3.2, §3.5 updated).
- 2026-10-05 Phases 3 and 9 (noise) merged: tile terrain, empty band and skirt, void outside; write guard; structures kept 16 blocks inside the tile; spawn from the geometry. Noise invariant under Γ to ~1e-13 at every seam type and cone point (lattice octaves up to ~1000-block cells, spectral above; ≤5% stretch); climate and density sampled at cell centres, so everything moves by half a block. Residue: aquifers (and, unmeasured, carvers and biome jitter) on the folds, all below sea level. Not done: the RS §6.2 blend, cross-seam feature merge.
- 2026-10-05 Phase 4 merged: copy links, paired tickets, promotion gate at the FULL step (fill before block entities, ticks or sending), forwarded reactions, block entity claims, capability and block entity redirects, owner-only POIs, stamps and recovery, `/orbifold check`/`scan`, `-PcheckCopies`. 40 band gametests across east–west, both folds and a corner near F. Gaps: client replicas untested with a client, light work on the main thread (~1.8 ms per fill), simulation-distance changes not followed by paired tickets.
- 2026-10-05 Phase 6 merged: image views (server tickets, tracking, lingering; client per-image ViewAreas with the draw-once clip) in vanilla and Sodium. 6 unit + 15 gametests; screenshots at the east seam, north fold, F and N show correct placement and no doubled faces. Images start a band's depth earlier than planned. Gap: empty first frames after a crossing (phase 5). Combined `orbifold`: 86/86 gametests with copy checks, 62 unit tests, client audit clean.
- 2026-10-05 Small size merged: presets in `OrbifoldSize` (small 3584 × 3072 plus k = 2/4/8), fold row and spawn derived from the projection (re-derives the k table exactly), save-compatible codec, `-PorbifoldSize=small`, `TestPlaces` for size-independent test sites. 66 unit tests; suites pass at small. Small has 4.3× the default sky speed, sparser spectral octaves (4096-block climate octaves constant). Phase 10 note: a 1:8 Nether at small has a 12-chunk-tall tile and will need more images or a view limit.

---

## 7. Risks

| Risk | Where | Mitigation |
|---|---|---|
| A reaction path that skips the owned-reaction hooks runs twice and duplicates items | 4 | Spike first; the "ran at non-owner" counter; `/orbifold check` after every test |
| `Rotation.CLOCKWISE_180` is wrong for a modded block | 4, 11 | The same block also breaks in rotated structures; list them in compat notes |
| Invariant noise changes vanilla terrain character (stretched octaves, spectral low octaves) | 9 | Compare terrain statistics against vanilla in screenshots and a height histogram |
| Generation load: paired loading near seams, image views | 4, 6 | Seams are long but players rarely sit on them; v2's retention and gating carry over |
| C2ME bypasses the ticket or promotion hooks | 11 | Phase 11 tests, as v2 phase 8 planned |
| Cone point duplicates confuse players (seeing themselves) | 6 | It's the expected trace of curvature (W §7); add a cone point marker if needed |
| Interplay of followers, groups and pulls oscillates | 5 | Cooldowns, and the follower-churn counter |

---

## 8. Open questions carried forward

From the wrapping plan:

1. Default size: k = 4 (assumed here) or 8.
2. Should climate follow latitude?
3. Is the north-pole cone point acceptable for gameplay?

From rotated seams:

4. The `C`, `R`, `H` defaults.
5. Should a placement join the structure it touches?
6. When should cross-seam generation writes be merged?
