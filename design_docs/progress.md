# Progress

Design: `cube-world.md` (section numbers below refer to it). Torus-era docs are in `archive/`.

## Decided

- Sun axis: body diagonal by default, polar as an option.
- Velocity across edges: upright for mobs, world-space for players (orbit over the edge), projectiles, items and sub-levels.
- Sodium, Embeddium, Iris: out of scope for now.

## Phases

### 0 Strip ✅
- [x] Delete torus code (wrap, islands, frames, packet normalisation, storage and worldgen mixins, Xaero)
- [x] Keep sky, atmosphere, stars, space fade, local-time mixins, F3 sun line, mixin audit, compat plugin
- [x] Done when: build and gametests pass

### 1 Geometry ✅
- [x] `CubeGeometry`: face bases, ownership, barrier cells, `T_AB`, unfold `U_AB`, storage layout (§1)
- [x] Settings in the generator codec (no `alpha_omega.json`), common config defaults, `CubePayload` to clients (§2)
- [x] World preset `alpha_omega:cube` with Customize screen; gametests run in a cube world
- [x] `/cube info`
- [x] Done when: unit tests show regions tile space once, transforms invert and compose, barrier cells agree from both faces
- [ ] Check the Customize screen by hand (not exercised by tests)

### 2 Faces v1 ✅
- [x] `CubeChunkGenerator`: void outside footprints, vanilla terrain per face (§4.1)
- [x] Edge air (waterloggable, fixed) and filler blocks; barrier pass (§3, §4.4)
- [x] Write guard on `Level.setBlock` and `WorldGenRegion.setBlock`
- [x] Structure filter: whole box owned, 8-block margin (§4.5)
- [x] Done when: gametests show nothing can be placed, pushed or poured across; structures stay inside
- Moved to phase 6: the blend-band prototype. Until then each face generates its own barrier, so the two copies of a barrier cell can disagree (bedrock on one side, edge air on the other).

### 3 Sky per face ✅
- [x] `CubeSun`: per-face sun from geometry; `LocalSky` API kept, torus math removed (§7)
- [x] Celestial rotation `Ry(90)·Mᵀ·C·Rx(H)`; night skip to the next first light of the face with most sleepers
- [x] `LocalSkyTest` rewritten; `LocalTimeGameTests` on DOWN (12 h from UP)
- [x] Done when: tests pass on two faces with different time zones
- [ ] Look at the sky in a client (sun path, stars about the pole) — needs the screenshot harness from phase 5

### 4 Transfers ✅
- [x] Server check after each root entity ticks, 0.5-block hysteresis, vehicle stacks as a group, position memories cleared (§5.1)
- [x] Client-driven player crossing with `FaceTransferPayload`, checked by the server; vanilla teleport when a player is 3+ blocks past (§5.2)
- [x] Camera eases from the old view to the new upright one over 10 ticks
- [x] Done when: items cross all 24 directed edges, arrows keep world velocity, mobs stay upright, no flip-flop, corners, player claims
- [ ] Try crossing in a client (needs phase 5: until then the new face's chunks stream in only after crossing)
- [ ] Remote entities crossing faces: interpolate from the transformed old position (§5.3) — with phase 5's neighbour rendering
- Players riding a vehicle the server moves are teleported (no camera easing beyond the face-change detection)

### 5 Neighbours ✅
- [x] Virtual positions: loading and ticking tickets, `CubeTrackingView` (diffed as sets), entity tracking (§6.1)
- [x] Client chunk cache backed by a map in cube worlds (§6.2)
- [x] Rendering: per-face `ViewArea`s drawn in vanilla's terrain pass with a rotated model-view; entities, block entities; particles and sounds moved to the camera's face (§6.3)
- [x] Crossing swaps the neighbour's area into vanilla, and neighbour sections compile ahead out of view, so the new face is ready
- [x] Remote entities crossing faces interpolate from their transformed old position (§5.3)
- [x] Done when: walking across shows no gap; checked with scripted screenshots (`client.DevScript`)
- Known: neighbours use spherical fog; translucent sorting of neighbour sections is relative to the wrong camera; entity-bound sounds on neighbours stay silent
- Known: a fresh world generates ~5× the chunks at first (four neighbour views), so the server lags; once a client quit hung during that backlog
- [x] Upright entities wait at the diagonal (up to 2 blocks) while the other side is solid

### 6 Continuous terrain ✅
- [x] `CubeNoise`: flat noises (and their warps) sampled on the cube surface; 3D noises left on storage coordinates (§4.2)
- [x] Barrier cells decided by the mean of both faces' densities, so both copies agree (§4.3) — instead of the blend band
- [x] `horizontal_scale` setting (codec, config, Customize slider) (§4.6)
- [x] Done when: barrier cells identical across all 12 edges (1260 sampled); ground meets at the ridge (mean 3 blocks apart)
- [ ] Surface noises (`SurfaceSystem`) on the cube surface
- [ ] Closer match at the ridge (3D noise detail differs by ~3 blocks)

### 7 Sable
- [ ] Sub-level transfer, tracking via virtual positions, client render on neighbour faces (§8.1)
- [ ] Decide whether Mixin Squared is still needed; drop it if not
- [ ] Done when: sub-levels cross edges in gametests and the dev client

### 8 C2ME
- [ ] Verify density function compiler fallback and virtual tickets/tracking under its chunk system (§8.2)
- [ ] Done when: full gametest suite passes with `-PwithC2me`

### 9 Polish
- [ ] Nether return portals clamped to a face; compass and maps; F3 face line; `/cube tp`, `/cube time`; `api/CubeWorld` (§9)

## Log

- 2026-10-05 Players keep their momentum in world space across an edge, so they arc over it; they still land upright. Dev client: a jump across UP's east edge left EAST moving 0.11 up and 0.36 along it, with no server corrections.
- 2026-10-05 Neighbour collision (`neighbour-collision.md` steps 1–4). Edge filler in the 16 cells under each barrier collides like the neighbour's block at the same cell, turned into this face's frame. A missing neighbour chunk is empty. Added 3 unit tests and 5 collision gametests (42/42 gametests in total).
- 2026-10-04 Transfer retention (`transfer-retention.md`): crossing an edge now keeps everything still in view, with nothing forgotten, re-sent or unloaded. Squares that drop out linger for 200 ticks (`transferLingerTicks`). Neighbour tickets match vanilla's levels. 2 retention gametests. Dev tool: `-PtransferStats`.
- 2026-10-04 Phase 6: 2 terrain gametests, 2 more geometry unit tests (31/31 gametests); dev-client screenshot shows hills and biomes continuing over an edge. Dev client render distance set to 8 (run/options.txt) to cut generation lag.
- 2026-10-04 Phase 5: 3 neighbour gametests (28/28 total); scripted dev-client screenshots show neighbours folded in place and a smooth crossing. Dev tools: `-PquickPlay=cube -PdevScript=...` (see `DevScript`), `-PonlyFace=EAST`, `/cube scan`.
- 2026-10-04 Phase 4: 4 transfer unit tests, 7 transfer gametests (25/25 total), client audit passes.
- 2026-10-04 Phase 3: 11 sun unit tests, 6 local-time gametests (18/18 total). Local clocks run at a varying rate (vanilla's eased sun meets each zone at a different phase) but gain a day per day. Gametests now pre-generate chunks they force: ticks run back to back, faster than generation.
- 2026-10-04 Phase 2: 12/12 gametests (barrier/filler layout, void between faces, write guard, fluids, pistons, placement, structures: 26 rejected, 0 kept on 16-chunk faces). Gametests now run at y 100 over UP with structures on, in a fresh world each run.
- 2026-10-04 Phase 1: 10 geometry unit tests, 3 cube gametests (5/5 total), client mixin audit passes (`-PauditMixins -PquitAfterAudit`).
- 2026-10-04 Phase 0: ~200 → 31 mixins. `LocalSky` treats every position as the prime meridian until phase 3. Build and gametests pass (2/2, mixin audit 0 failures). Client not launched.
