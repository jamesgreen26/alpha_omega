# Cube World

**A cube planet made of six ordinary flat faces**

Target: Minecraft 1.21.1, NeoForge · Status: plan · Supersedes the torus documents in `archive/` (**Project Overview**, **Islands & Lift Tables**, **Two-Face Wrapping**, **Mod Compatibility**, **Implementation Status**). **Local Sky and Atmosphere** survives with changes (§7).

---

## 0. Summary

The world is a cube. Each of its six faces is a square of `W × W` chunks (default `W = 16`, so a face is 256 blocks across), facing outward from the cube's centre. Each face is stored as **ordinary, upright chunks** in its own area of the overworld. Gravity is vanilla `-Y`, and everything on a face behaves exactly like a normal world.

- **A position belongs to the face whose storage it is in.** Face membership is not stored anywhere: it is just where the thing is.
- **Crossing an edge is a transfer.** When something passes the diagonal plane between two faces, it moves to the neighbouring face's storage. Its cube-space position stays the same, and its velocity and orientation are carried over (§5).
- **The client treats its own face as the world.** The neighbouring faces' chunks and entities are drawn rotated into place (§6). You can see them but not touch them.
- **Edges cannot be built across.** A one-block diagonal barrier of bedrock and "edge air" separates the faces, so no block, multiblock, fluid or structure ever spans an edge (§3).
- **Terrain meets at the edges.** The ground on one face runs past the edge until it meets the next face's ground at the diagonal (§4).
- **Each face has one sun.** A face is flat, so its whole surface shares one sun elevation: one time zone per face, taken from the real geometry (§7).

Compared with the torus, a whole class of bugs disappears. There are no canonical-versus-lifted positions, packets need no normalisation, saves need no lap tags, and nothing has to work across a seam. Block positions, entity positions and client positions always agree, because each face is a normal patch of world. The hard parts move to three places: generating terrain that matches at the edges, transferring things between faces, and drawing the neighbouring faces on the client.

---

## 1. Geometry

### 1.1 Terms

| Term | Meaning |
|---|---|
| `W` | Face width in chunks (config, default 16). |
| `R` | Half-width of a face in blocks: `8·W` (128 by default). |
| `Y0` | The face plane: the storage y where the cube's faces lie. Sea level (63). |
| Face-local `(u, h, v)` | `u, v` are blocks from the face centre (the base square is `[-R, R)`). `h = y − Y0` is the height above the face plane. |
| `d` | Distance from the cube centre along the face normal: `R + h`. |
| Cube space | 3D coordinates centred on the cube. `P = Rot_f · (u, d, v)`, where `Rot_f` is one of six fixed 90°-multiple rotations taking local up to face `f`'s normal. |
| Owner | The face whose axis has the largest `|component|` of `P`. Seen from face `f`'s storage, `f` owns a position if `d > max(|u|, |v|)`. |
| Diagonal | The planes where two components tie (`d = |u|` or `d = |v|`). These are the planes through the cube centre and an edge. |

With the defaults, the cube centre is at `y = Y0 − R = −65`, just below the world floor. Each face's underground region therefore tapers to a point right at bedrock.

### 1.2 What each face owns

The six owned regions are the six pyramids from the cube centre through each face, extended upward without limit. Together they fill all of space. In face `f`'s storage column, `f` owns:

- **Below the face plane:** a tapering region bounded by the diagonals. Near an edge, the deep rock belongs to the neighbouring face.
- **Above the face plane:** a widening region. At height `h`, `f` owns `h` blocks past its base square on every side. This is the **overhang**. It is how "the horizontal extent on one side of the edge matches the vertical extent on the other side".

Cross-section through the edge between faces A and B, in A's frame:

```
          d (A's up)
     R+h  │━━━━━━━━━━━━━━━━━━━━━┓   ← A's ground at height h runs h blocks past the edge to the diagonal
          │                    ╱┃
          │                   ╱ ┃      A owns: d > |u|        B owns: u > d (right of ╱)
          │                  ╱  ┃
       R  │─ ─ ─ ─ ─ ─ ─ ─ ─●   ┃   ● the cube edge (u = d = R); ┆ is B's face plane
          │                ╱┆   ┃
          │               ╱ ┆   ┃   ┃ B's ground, h out from B's face plane, is a wall in A's frame
          │              ╱  ┆   ┃
          └─────────────────┴───┴────────────────→    u
                            R   R+h
```

Everything below `━` and left of `┃` is ground, split between A and B by the diagonal `╱`. The ridge `┓` is where the two faces' ground meets.

**Each column has exactly one barrier cell.** In any storage column of a face, the owned cells are exactly those above one barrier cell, and every cell below it belongs to other faces. So the barrier is a heightfield (`CubeGeometry.barrierY`): a pyramid under the base square that rises 1:1 through the overhang. In corner columns, the cells below include the barrier between two *other* faces; that is still foreign to this face.

**Discretisation.** The cube is centred on a lattice corner (`R` and `Y0 − R` are integers), so each block cell maps to an integer cell in cube space on every face. For a cell, compare `|a + ½|`, `|b + ½|` and `|c + ½|`:

- **Owned:** one component strictly dominates.
- **Barrier:** two or three components tie, i.e. `a = ±b`-style ties.

The barrier is a one-block diagonal staircase, and it is face-tight: an owned cell on one side never shares a face with an owned cell on the other side. Ownership is computed in cube space, so every face agrees on which cells are barrier.

### 1.3 Storage layout

Each face is stored around its own origin `O_f` in the overworld, as upright chunks. Its storage footprint is the base square plus the overhang at the build limit: half-width `R + (maxBuildY − Y0)`, which is 385 blocks by default (about 50 × 50 chunks). Most of that is empty sky.

The faces are laid out on a coarse grid. The spacing is at least the footprint plus twice the largest view distance, so vanilla's square view around a player never reaches into another face's storage. For example, `O_f = (f · 2048, 0)`. Face 0, the spawn face, sits at the storage origin, so vanilla's spawn search lands on it.

Chunks outside every footprint generate empty and are never needed. Sable's plot area (around chunk `10000 · plotSize`) is far outside the layout. The layout asserts this at load.

### 1.4 Transforms

- **`T_AB`** maps A's storage to B's storage through cube space: `S_B⁻¹ ∘ S_A`. It is a rigid motion with a 90° rotation, and exact on block cells. It moves positions on transfer (§5).
- **`T_BA`** maps B's storage into A's frame. The client draws B's chunks and entities with it (§6).
- **`U_AB`** (unfold) rotates about the shared edge so that B's face plane lies flat, continuing A's. It maps a position on A to a **virtual position** in B's storage, as though B's surface carried on past the edge. It is used for chunk tickets, chunk tracking and entity tracking (§6.1), because vanilla's square distance checks then work unchanged.

Every face has exactly four neighbours, and the opposite face is never visible from outside the cube. So a client never needs more than its own face plus four.

---

## 2. Settings

- **The overworld's chunk generator holds the settings.** `alpha_omega:cube` (`CubeChunkGenerator`) stores `face_chunks` and `sun_axis` in its codec, so they are saved in `level.dat`. A world is a cube world exactly when its overworld uses this generator. There is no `alpha_omega.json`.
- **A world preset `alpha_omega:cube`** is in the "normal" preset tag, so it shows under World Type. Its "Customize" screen (`CubePresetScreen`) sets the face width and sun axis.
- **Defaults** for presets that leave the settings out come from `alpha_omega-common.toml`. This covers dedicated servers with `level-type=alpha_omega:cube` and gametests. Once a world is saved, its settings are written explicitly.
- **The settings reach clients** in a configuration-phase payload (`CubePayload`).
- **A `CubeGeometry` value** per world owns every formula in §1. It is pure Java and unit tested. `Cube.of(level)` returns it for the overworld of a cube world and `null` everywhere else.
- **Gametests run in a cube world:** `GameTestServerMixin` swaps vanilla's flat test preset for the cube preset.

---

## 3. The barrier

| Block | Placed at | Properties |
|---|---|---|
| Bedrock | Barrier cells whose density is solid | Vanilla bedrock (mobs already cannot spawn on it). |
| **Edge air** (new) | Barrier cells whose density is not solid | Invisible, no collision, not selectable, not replaceable, `PushReaction.BLOCK`, unbreakable, lets light through. Waterloggable: where the cell is water on both sides it is waterlogged, so oceans meet without a visible wall (open decision 3). |
| **Filler** (new) | Every cell in a face's storage that another face owns | Invisible, no collision, not selectable, **occludes faces** (full occlusion shape), blocks light. Counts as solid (`forceSolidOn`), so fluids, rain and the motion-blocking heightmaps stop at it. |

Why each one is needed:

- **Edge air** blocks building in the client's own placement checks, before the server gets involved, so there are no ghost blocks. Players and mobs walk straight through it and transfer (§5).
- **Filler** stops barrier faces being drawn twice. Both faces store the barrier cells, so without filler both would draw the same quads, rotated, and they would z-fight. With filler, each face's mesh only draws barrier faces that point into its own region, and its foreign region renders as nothing.
- **A server-side guard** backs both up. `Level.setBlock`, and `WorldGenRegion` / `ProtoChunk` writes during generation, reject any write to a barrier or foreign cell. This stops pistons, explosions, fluids, Create contraptions, features and carvers from crossing, whatever path they take.

---

## 4. World generation

### 4.1 Generator

`CubeChunkGenerator` extends `NoiseBasedChunkGenerator` with the vanilla overworld noise settings and is registered as a chunk generator type. A custom generator, rather than mixins into the vanilla one, keeps the footprint small and lets C2ME's generation delegate call it like any other generator.

- Chunks outside the footprints are empty.
- Chunks inside a footprint generate normally through a cube-aware noise router (§4.2–4.3), then get the barrier pass (§4.4).

### 4.2 Continuous coordinates

After `RandomState` wires the router, `CubeNoise` rewrites it once per level (and rebuilds the climate sampler biomes use from it).

- **Flat noises** are sampled at the point on the cube's surface above the column (`CubeGeometry.surfacePoint`): the face coordinates clamped to the face's square, as a 3D point. "Flat" means the vanilla noise functions with `y` scale 0: continentalness, erosion, ridges, temperature, vegetation and jaggedness, plus the `shift_a` / `shift_b` offsets that warp them.
  - On a face this is a flat slice of the noise, so each face looks like vanilla.
  - Across an edge the slice bends 90° without a break, so continents, rivers, mountain ranges and biomes carry on over the edge.
  - In the overhang the clamp extends the edge's column outward. The ground on both sides of an edge therefore has the same height at the edge and meets at the 45° ridge.
  - The warp offsets are continuous; the warp is applied along each face's own axes, so its direction turns at an edge.
  - The surface point depends only on `x, z`, so vanilla's `flat_cache` / `cache_2d` stay valid.
- **3D noises** (caves, `base_3d_noise`, aquifers, veins) keep their storage coordinates. They are continuous on each face and disagree only at the diagonal, where they add surface detail.
- **`horizontal_scale`** (setting, default 1) multiplies the surface point, shrinking terrain and biomes sideways on small faces.

### 4.3 The barrier decides alike on both faces

Both faces store each barrier cell, so both must make it the same block. A barrier cell is bedrock where the **mean of the two faces' raw terrain densities** there is positive, and edge air otherwise. Each face evaluates the same two densities (its own at its cell, the partner's at the partner's copy of the cell, `CubeGeometry.barrierPartner`), so the result is identical. Two density evaluations per column, skipped where the answer is fixed:

- More than 24 blocks below the face plane, barrier cells are always bedrock.
- Above y 300, they are always edge air.
- Where three faces meet (the lines from the centre through the cube's corners), always bedrock.

Edge air is waterlogged where this face has water at the cell (cosmetic, and may differ between the two copies).

Measured over the 12 edges (`TerrainGameTests`): every sampled barrier cell agrees, and the ground on the two sides of an edge differs by about 3 blocks on average (the 3D noise detail). A wider blend of the densities near the diagonal would close that gap at many times the cost; not done.

### 4.4 Barrier pass, features and carvers

- **At the noise step:** foreign cells become filler, and barrier cells become bedrock or edge air (§4.3).
- **During generation:** the write guard (§3) drops any carver or feature write into barrier or foreign cells. Trees at the ridge are cut off, as they would be by any wall.
- **Surface rules** only replace the default block, so they leave bedrock and edge air alone.

### 4.5 Structures, biomes, surface

- **Structures.** A structure start is kept only if its whole bounding box is owned by the face, with a margin (for example 8 blocks) from the diagonal. Owned regions are convex, so checking the eight corners is enough. This check runs in `ChunkGenerator.tryGenerateStructure` after the start is generated and before it is stored. Strongholds and other grid-placed structures keep vanilla spacing in storage coordinates and are filtered the same way.
- **Biomes.** The biome source samples the same router, so climate is continuous across edges.
- **Surface noises** (`SurfaceSystem`: surface depth, clay bands, badlands pillars, icebergs) still sample storage coordinates, so those details do not line up across edges. Not done yet.

### 4.6 Scale

A default face is 256 blocks across, smaller than one vanilla continent. `horizontal_scale` (Customize screen, 0.25 to 4; config up to 16) shrinks terrain and biomes sideways; vertical terrain stays vanilla.

---

## 5. Faces and transfers

### 5.1 The rule

After any movement, if an entity's feet position is owned by a different face, by more than a hysteresis margin (½ block), it transfers to that face:

- **Position:** `T_AB`, the same cube point.
- **Velocity and orientation**, by kind:
  - **Players and mobs stay upright.** Yaw and pitch are rotated with the frame, and their hitbox simply re-axes.
  - **Mobs' velocity is rotated with the frame too**, so walking off an edge continues along the next face. **Players keep their velocity in world space:** speed toward an edge carries them up off the next face, and its gravity brings them round, a brief orbit. (Changed 2026-10-05.)
  - **Projectiles, items, falling blocks, TNT and Sable sub-levels keep their world-space velocity and orientation**, rotated into the new storage frame. Gravity simply changes direction at the diagonal, as it would on a real cube.
- **Riders and vehicles** transfer as one group, decided by the root vehicle.
- **Position memories** are cleared: navigation, brain memories, homes and goal targets. They make no sense in another face's storage. Mobs re-acquire their targets within a tick or two. (Open decision 7: transform them instead, adapting `BuiltinFrameTranslators` from laps to `T_AB`.)
- **Collision at the destination:** if the destination box collides with blocks, the transfer waits a tick, up to a limit, so nobody lands inside terrain.

### 5.2 Players

- **The local player transfers on the client**, which already decides its own movement. When it crosses, the client applies `T_AB` to its current state, adds a camera smoothing offset and sends `ServerboundFaceTransfer` with its pre-transfer position.
  - The server checks the claim: the player was within a few blocks of that diagonal, and the destination is owned by B. Then it applies the same transform without sending a position packet, so there is no teleport acknowledgement, rubber-banding or "moved too quickly" kick.
  - Movement packets sent before the transfer are recognised by their frame and ignored.
- **Server-driven transfers** (vehicles, pistons, knockback, `/tp` into foreign territory) send `ClientboundFaceTransfer`. It carries the transform, not an absolute position. The client applies it to its current state and acknowledges, and the server ignores movement until it gets the acknowledgement, as vanilla does around a teleport. The client keeps whatever it moved in the meantime, so there is no snap back.
- **Camera smoothing.** The camera's orientation and eye position ease from the old frame into the new one over about half a second, so the 90° change in "down" reads as rolling over the edge rather than a cut.

### 5.3 Other entities on the client

A remote entity that transfers arrives with a big jump in storage position. When the client sees that an entity's new position is on a different face from its old one, it re-expresses the old position through `T` and interpolates from there, instead of lerping across thousands of blocks.

---

## 6. Seeing and simulating the neighbours

### 6.1 Server: virtual positions

For each player, and for each neighbour face within view distance on the unfolded net, the server works out the player's **virtual position** `U_AB(pos)` in that face's storage. Then:

- **Tickets.** A player-style ticket at the virtual chunk, with level from the view and simulation distance, through the public ticket API. Chunks near the edge on the neighbour load and tick as if the player stood just past the edge.
- **Chunk tracking.** The player's chunk tracking view is the union of its real view and its virtual views. The neighbour chunks are sent with their own storage coordinates.
- **Entity tracking.** `TrackedEntity.updatePlayer` measures range from the nearest of the player's real and virtual positions.
- **Sable sub-level tracking:** the same (§8.1).

After a transfer, the old face becomes a neighbour and the new one becomes home. The tracked sets barely change, so a transfer streams almost nothing.

**No packet needs changing.** Every position on the wire is a plain storage position.

### 6.2 Client storage

- **`ClientChunkCache`:** chunks from the four neighbour faces go in a side map keyed by face, next to vanilla's ring buffer around the player. Lookups fall through to it, so neighbour chunks count as loaded and their entities tick and interpolate.
- **The client light engine** stores neighbour sections normally. Faces are never adjacent in storage, so light never crosses an edge.

### 6.3 Rendering

- **Terrain.** Each neighbour face gets its own `ViewArea`, centred on the camera's virtual position in that face's storage. Meshes are compiled by the vanilla section dispatcher in their own storage frame, so meshing is unchanged and a transfer needs no recompile.
  - For each chunk layer, the neighbour's visible sections draw with the vanilla call, using `ModelViewMat · Rot_BA` and `ChunkOffset` relative to the virtual camera. Rotation keeps lengths, so spherical fog stays right. Cylindrical terrain fog is slightly off on neighbours (it can be fixed later in the core shader).
  - Frustum culling uses the transformed section boxes. The occlusion graph runs per face from the virtual camera, or is skipped for neighbours at first.
- **Entities and block entities** on neighbour faces draw with the face rotation pushed onto the pose stack and the virtual camera position.
- **Sounds and particles** created at neighbour-face positions are moved into the home frame with `T_BA` when they are played or added.
- **Interaction:** the crosshair ray, attacks and block placement run in home storage only, so neighbour faces are never targeted.

### 6.4 Sodium, Embeddium and Iris

Neighbour rendering targets the vanilla section renderer. Sodium and Embeddium replace it, and Iris shader packs replace the terrain shaders, so with those mods the neighbour faces will not draw. Compat with them is out of scope for now (decided). The home face, transfers and gameplay are unaffected.

---

## 7. Sky and time

### 7.1 Per-face sun

The sun's direction `s(t)` turns in cube space about an **axis `a`** (config):

- **Body diagonal `(1, 1, 1)` (default, decided).** Every face's normal is 54.7° from the axis, so all six faces get a 12-hour day with the sun peaking at 54.7°. Noons are 4 hours apart around the cube: six time zones.
- **Polar (through two face centres).** The four equatorial faces have vanilla days with noon overhead, 6 hours apart. The two polar faces keep the sun on the horizon forever: permanent twilight, which counts as night for gameplay.

On face `f` the local sun is `Rot_fᵀ · s(t)`. A face is flat and the sun is at infinity, so every point on a face shares one elevation and one time zone. Local time jumps when you cross an edge, which is what the real geometry gives. There is no sphere projection.

### 7.2 Code

- **`LocalSky`** keeps its API: `sample`, `isDay`, `skyDarken`, `localDayTime`, `equivalentTimeOfDay`, `sleepTimeAddition`. Only the inside changes: `sample(level, x, z)` becomes a face lookup plus a dot product. The longitude and latitude triangle waves go.
- **The 26 `mixin/server/time` mixins** and the client sky mixins call `LocalSky`, so they stay as they are.
- **The night skip** goes to the next first light (sun above the daylight threshold) of the face where most sleepers are. A face that never gets that light (the polar faces' twilight) wakes at its noon.
- **Local clocks** (villager schedules, the clock item) read the face's own vanilla-eased time of day, so local noon is 6000. Vanilla's eased sun meets each time zone at a different phase, so a local clock runs a little faster or slower through the day, but gains exactly a day per day.
- **`LevelRendererSkyMixin`'s celestial rotation** becomes `Rot_fᵀ · Rot(a, ωt)`, which tilts the sun, moon and stars together.
- **Atmosphere, `StarField` and `SpaceFade`** (the fade to black above the build height) are kept unchanged.
- **`/cube time`** and F3's `Sun:` line show the face, local clock and sun altitude and azimuth.

---

## 8. Mod compatibility

### 8.1 Sable (keep, mostly rewritten)

A sub-level belongs to the face its logical pose is on, and collides with that face's terrain in storage, so physics is unchanged. New work:

- **Transfer:** when the pose crosses the diagonal (with hysteresis), move the pose with `T_AB`, keeping world orientation (§5.1). Rotate linear and angular velocity, and carry passengers with it.
- **Tracking:** `SubLevelTrackingSystem.shouldLoad` uses the player's virtual positions.
- **Client:** sub-levels on neighbour faces render with `T_BA` before their pose. Pose interpolation across a transfer re-expresses the old pose (§5.3).
- **Barrier:** sub-levels ignore edge air and filler, since both have no collision.

Dropped, because nothing has images any more: the distance bridges (`ActiveSableCompanionMixin`), canonical holding keys, physics block-change lifting, serializer lap moves, plot exclusion from the torus, and the 100,000-block recentre limit.

Kept: the companion jar, the `-PwithSable` dev runs, `SableTestOps` and the gametest scaffolding.

### 8.2 C2ME (keep, mostly removed)

Without canonicalisation there are no scheduler keys or neighbourhood images to fix, so the following go:

- `StatusAdvancingSchedulerMixin`
- `VanillaWorldGenerationDelegateMixin`
- `ServerAccessibleChunkSendingMixin`
- `ServerBlockTickingMixin`

Noise is no longer changed from inside `ImprovedNoise` / `PerlinNoise`, so C2ME's math optimisations can stay **on** and the MixinSquared canceller is probably unnecessary.

To verify:

- The density function compiler falls back cleanly on `cube_noise` and on the blend band's raw evaluations. The old `McToAstMixin` shows the fallback path exists.
- Virtual tickets and tracking still work under its rewritten chunk system and no-tick view distance (it replaces vanilla chunk sending).

### 8.3 Xaero's World Map and Minimap

**Removed:** compat classes, mixins, Modrinth dependencies and tests.

### 8.4 Everyone else

Each face is vanilla, so mods work on a face without help. Create contraptions, ZPS reactors and other multiblocks cannot span an edge (§3), so the whole of **Mod Compatibility** §1 goes away. What remains for other mods:

- **Mods that teleport or path between distant storage positions** see six separate areas.
- **Sky mods** see the vanilla global `isDay` at face 0.

---

## 9. Dimensions, spawn, portals, tooling

- **Nether and End are vanilla.**
  - Overworld → Nether scales storage coordinates by ⅛, as vanilla does, so each face maps to its own patch of the Nether.
  - Nether → Overworld destinations are clamped into the owned base square of the nearest face (adapting `NetherPortalBlockMixin`).
  - End return goes to world spawn on face 0.
- **Spawn and saves** are vanilla: players load where they saved, on their face. There are no lap tags.
- **Compasses and maps** pointing at another face aim along the unfolded net when the target is on a neighbour, and spin otherwise.
- **F3** shows the face, the local `(u, h, v)` and the cube-space position.
- **`/cube`** has `info`, `tp <face> [u v]`, `time` and `time set`.
- **`api/CubeWorld`** offers `faceOf`, `toCube`, `fromCube`, `transform(A, B)`, `owner`, `isBarrier` and transfer events.
- **The mixin audit** flag stays.

---

## 10. Keep, change or discard

Line counts are today's.

| Area | Lines | Verdict |
|---|---:|---|
| `sky/`, `client/sky/`, shaders, `mixin/server/time/` (26), sky client mixins | ~2,200 | **Keep.** Rework `LocalSky`'s position → sun mapping and the celestial rotation (§7). Keep `AtmosphereModelTest`, rewrite `LocalSkyTest`. |
| `AlphaOmegaMod`, `config/`, mixin audit, `CompatMixinPlugin`, `ReplacedMixinPlugin`, Gradle (Sable/C2ME dev runs, vendored libs) | | **Keep**, minus Xaero dependencies and the removed gametests. |
| `network/WrapSettingsPayload`, `WrapSettingsTask`, `WorldWrapStore` (file handling) | | **Change:** cube settings (§2). |
| `CreateWorldScreenWorldTabMixin` | | **Replace** with a world preset and preset editor. |
| `DebugScreenOverlayMixin`, `CompassItemPropertyFunctionMixin`, `ClockItemPropertyMixin`, `command/WrapCommand`, `api/WorldWrap` | | **Change** to faces (§9). |
| `NetherPortalBlockMixin` (server and Sable), `PlayerListLoginMixin` | | **Change** to portal clamping. Login needs nothing. |
| `compat/sable/`, `mixin/compat/sable/` | ~490 | **Keep the scaffolding, rewrite the mixins** (§8.1). |
| `compat/c2me/`, `mixin/compat/c2me/` | ~260 | **Keep the scaffolding and the equality/fallback ideas, drop the chunk-system mixins** (§8.2). |
| `gametest/` | ~1,800 | **Keep** `TestPlayers`, `ModLoadGameTests`, the platform structure, and the shapes of `LocalTimeGameTests` and `SableGameTests`. **Discard** the rest. |
| `wrap/` (`Wrap`, `WrapMath`, `Wraps`, `WrapContext`, `WrapHolder`), `wrap/poi/` | ~850 | **Discard.** Replaced by `CubeGeometry`. |
| `wrap/noise/`, `mixin/worldgen/` (noise, aquifer, surface, structure, biome mixins) | ~1,400 + | **Discard.** Replaced by the visitor, `cube_noise`, the generator and the structure filter (§4). |
| `island/`, `frame/` | ~1,400 | **Discard.** Possibly adapt `BuiltinFrameTranslators` (open decision 7). |
| `network/PacketNormalization`, `ServerboundNormalizer`, `client/ClientboundNormalizer`, `mixin/network/` (35 accessors) | ~950 | **Discard.** |
| `mixin/server/` storage canonicalisation (chunk map, tickets, light, POI, entity sections, ticks, game events, explosions, maps, raids, spawner) | ~1,900 | **Discard.** New server mixins are only transfer, virtual tickets and tracking, and the write guard. |
| `client/ClientImages`, `ViewCenter`, `SeamRenderer`, `ClientChunkCacheMixin`, `SoundEngineMixin`, particle mixins, `ChunkBorderRendererMixin` | | **Discard.** New ones are for neighbour storage and rendering (§6.2–6.3). |
| Xaero compat | ~810 | **Discard.** |
| `islands-and-lift-tables.md`, `two-face-wrapping.md`, `mod-compatibility.md`, `project-overview.md`, `implementation-status.md` | | **Archived** in `design_docs/archive/`. `implementation-status.md` describes LOD work that is not on this branch. |

The mixin count should drop from about 200 to about 40–50.

---

## 11. Phases

| # | Phase | Done when |
|---|---|---|
| 0 | **Strip.** Delete the discarded code; keep the sky, time and compat scaffolding compiling against a stub geometry. | `./gradlew build` passes; the game runs as vanilla with the atmosphere. |
| 1 | **Geometry.** `CubeGeometry`, settings, world preset, client sync, `/cube info`. | Unit tests: the six regions plus the barrier tile space exactly once; transforms invert and compose; barrier cells agree from both faces; the unfold keeps adjacency. |
| 2 | **Faces v1.** Generator with void outside the footprints, vanilla terrain per face (no continuity yet), barrier and filler pass, write guard, edge air. | Gametests: nothing can be placed, pushed or poured into barrier or foreign cells; structures stay inside. |
| 3 | **Sky per face.** `LocalSky` rework, celestial rotation, time zones per face. | `LocalSkyTest` and `LocalTimeGameTests` on two faces. |
| 4 | **Transfers.** Server rule, entity groups, memory clearing, client-driven player transfer, `ClientboundFaceTransfer`, camera smoothing. | Gametests: mobs, items and arrows cross every edge and corner type; mock players cross both ways; no ping-pong walking along an edge. |
| 5 | **Neighbours.** Virtual tickets and tracking, client side storage, neighbour rendering (terrain, then entities and block entities), sounds and particles. | Dev client: walk around the cube in all directions with no visible popping; two players on adjacent faces see each other. |
| 6 | **Continuous terrain.** `cube_noise` visitor, blend band, surface noises, noise scale. | Gametest: generate both sides of every edge type and compare barrier cells (identical) and density across the ridge (continuous). Screenshots at edges and corners. |
| 7 | **Sable.** Transfer, tracking, client render. | Sub-levels fly and slide across edges in gametests and the dev client. |
| 8 | **C2ME.** Remove the old mixins, verify the compiler and tickets. | Full gametest suite under `-PwithC2me`. |
| 9 | **Polish.** Portals, compass and maps, F3, API, docs. | |

Phases 3 and 4 can run in parallel after 2. Phase 6 is the riskiest algorithm, so a throwaway prototype of the blend band, rendering one edge to an image from both sides, is worth doing during phase 2.

---

## 12. Open decisions

| # | Question | Recommendation |
|---|---|---|
| 1 | Sun axis | **Decided:** body diagonal by default (all faces playable, 4-hour zones), polar as an option. |
| 2 | Velocity across an edge | **Decided:** upright for mobs; world-space for players (a brief orbit over the edge), projectiles, items and sub-levels (§5.1). |
| 3 | Oceans at the ridge | Waterlogged edge air, so there is no visible water wall. |
| 4 | Terrain scale on small faces | Horizontal noise scale setting. The default should give a few biomes per face at `W = 16`. |
| 5 | Overhang storage up to the build limit | Keep it (mostly empty sections). A lower overhang cap would leave the sky above the ridge outside every face. |
| 6 | Sodium, Embeddium and Iris | **Decided:** out of scope for now; vanilla renderer only. |
| 7 | Mob memories on transfer | Clear them at first, and transform them later if mobs visibly lose track at edges. |
| 8 | Caves through the diagonal | Edge air where both sides are open, as specified. Making it all bedrock below the face plane would avoid sideways transfers in caves, if that turns out too odd. |

---

## 13. Risks

- **Neighbour rendering** is the largest new client system, and it is where other renderer mods will conflict.
- **The blend band and the C2ME compiler:** raw single-point evaluations must fall back cleanly and stay fast.
- **Transfer edge cases:** riding, sleeping, elytra, a crossing at the moment of a dimension change, and a player disconnecting mid-transfer.
- **Chunk systems:** C2ME's rewritten chunk system and no-tick view distance may bypass the vanilla tracking hooks the virtual views rely on.
