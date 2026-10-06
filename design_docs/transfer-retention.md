# Transfer Retention

**Crossing an edge should keep everything that is still in view**

Target: Minecraft 1.21.1, NeoForge · Status: implemented (§6 records what changed from this plan) · Extends **Cube World** §5 and §6 (section numbers below refer to `cube-world.md`).

---

## 0. Summary

`cube-world.md` §6.1 says: "After a transfer, the old face becomes a neighbour and the new one becomes home. The tracked sets barely change, so a transfer streams almost nothing." The client side mostly does this already. The chunk cache is a plain map, and the renderer hands the incoming face's `ViewArea` to vanilla. The server side does not.

During a transfer, the server's view of the player passes through states where the old face, and sometimes every neighbour, is missing. Each missing chunk is forgotten on the client. The server may also unload it, and it comes back later as a full resend. Every resend costs a chunk packet, client lighting and a section recompile. Entities in those chunks are removed and re-added too.

The fix is mostly on the server:

1. **Hand over the view in one step.** Move the player's home and virtual squares together, in the same call that moves the player (§3.1).
2. **Gate per face, not all or nothing.** The "own face loads first" gate must never take away a face that is already loaded (§3.2).
3. **Linger.** Squares that a transfer takes out of view stay tracked and ticketed for a short while. Walking back is then free, and so is walking along the edge (§3.3).
4. **Re-check entities only where the view changed** (§3.4).
5. **Client:** avoid the empty first frames after the area swap, and keep built areas for faces that may come back (§3.5).

Measure first (§2): every claim in §1 that is marked *to confirm* is backed by a counter or a gametest before any fix lands.

---

## 1. What a transfer costs today

### 1.1 Server, tick by tick

A player on face A crosses into B. C and D are the two faces next to both A and B. Before the crossing, the player's view (`CubeTrackingView`) is A's home square plus the virtual squares on B, C and D (`NeighbourViews.STATES`).

| When | What happens | Effect |
|---|---|---|
| Claim handled (packet task, between ticks) | `FaceTransfers.transfer` moves the player, then calls `chunkSource.move(player)` (`FaceTransfers.java:83`). `move` changes section, so vanilla calls `updateChunkTracking`. `ChunkMapMixin` builds the new view from **the old virtuals in `STATES`** (B, C, D seen from A), plus a home square on B. | A is in neither the home square nor any virtual square, so **A's whole square is forgotten**: `ClientboundForgetLevelChunk` for each chunk. The client drops them (`ClientChunkCacheMixin.java:81`), along with their light and entities. |
| Same call | `DistanceManager.removePlayer` on A, `addPlayer` on B. | Nothing holds A's player-ticket square any more. |
| Next tick, chunk source | Ticket levels are recomputed. | A's chunks fall below loaded. Any chunk not held by a generation task is queued for unload and saved. *(To confirm: how many actually unload in one tick.)* |
| Next tick, `LevelTickEvent.Post` | `NeighbourViews.tick` computes the new virtuals (A, C′, D′). They differ from the old ones, so the gate at `NeighbourViews.java:107` asks `homeLoaded`. The real square on B is not the B virtual square (§1.3), so some of it is still loading. | **The gate replaces the virtuals with `List.of()`**: all neighbour tickets are removed and the view shrinks to B's home square. C and D are forgotten too. |
| Some ticks later | B's square finishes loading. The virtuals come back, and tickets are re-added (vanilla's ticket throttling applies only to player tickets, so ours apply at once). | A, C′ and D′ are sent again in full. Chunks that unloaded are read back from disk. |
| Every view change | `NeighbourViews.java:115-119` re-checks every tracked entity in the level against the player. `move` has already done this once (vanilla loops `entityMap` in `ChunkMap.move`). | O(all entities) twice. Entities in A, C and D are removed and then added again on the client. |

There is a second, smaller version of the same problem: the gate drops **every** neighbour whenever **any** virtual centre moves by a chunk while the home square has a chunk still loading. This happens even without a transfer, for example when walking quickly along an edge into new terrain.

### 1.2 Client

| Piece | Today | Cost on transfer |
|---|---|---|
| Chunk cache (`ClientChunkCacheMixin`) | A map. Chunks go only when the server forgets them. | Nothing of its own. It drops whatever §1.1 forgets. |
| Light | Vanilla client light engine, per storage position. | A forgotten chunk has its light removed. On resend, light is reapplied and the sections are marked dirty. |
| Terrain meshes (`NeighbourRenderer.beforeSetupRender`) | Swaps the incoming face's `ViewArea` into vanilla and keeps the outgoing one as a neighbour. C's and D's areas don't move, because `T` is rigid (`T_BC ∘ T_AB = T_AC`). | Good, but undone by §1.1: resent chunks mark their sections dirty, so the meshes are rebuilt anyway. |
| Occlusion graph | `waitAndReset(incoming)` (`NeighbourRenderer.java:102`). | The home face may draw nothing until the first async full update lands. *(To confirm with frame capture.)* |
| Face areas | `AREAS` keeps the opposite-of-home face's area without using it, and creates a fresh area for the new far neighbour. | Fine, but there is no explicit policy (§3.5). |

### 1.3 What the geometry really changes

Even with a perfect hand-over, some chunks leave the view. That is the floor we are aiming for:

- **The old face A** is now seen from the virtual position `U_BA(T_AB p)`, not from `p`. That is a 90° rotation about the edge. For a crossing at height `h` above the face plane, the two points are about `2h` blocks apart. At ground level that is about one chunk; at `h = 100` it is about 12 chunks.
- **The shared neighbours C and D** move from `U_AC(p)` to `U_BC(T_AB p)`, a 90° turn about the cube corner. For a player `s` blocks from the C edge, the shift is about `s·√2`, and it only matters when `s` is within view distance, where only a sliver of C is in view anyway.
- **B's home square** is centred on the real position, not on the old virtual square. They differ by the same `2h`.
- **The far faces swap:** opposite(A) may come into view and opposite(B) may leave. Near an edge with `W = 16` and a typical view distance, neither is usually in view.

So at ground level, a correct hand-over should forget and send only a thin strip. At altitude, lingering (§3.3) covers the difference.

---

## 2. Step 0: measure

Add these before changing behaviour, so each fix has a number attached.

- **Server counters** (in `NeighbourViews`, read by `/cube scan` and by gametests): chunks watched and unwatched per player, using NeoForge's `ChunkWatchEvent.Watch` and `ChunkWatchEvent.UnWatch`; server chunk unloads (`ChunkEvent.Unload`); and the gate's "dropped all neighbours" events.
- **Churn:** a chunk unwatched and then watched again within 200 ticks. This is the main number. The target is **0** for a single crossing at ground level.
- **Client counters** (F3 `Neighbours:` line, or a log line under `-Dalpha_omega.dev.transferStats`): chunk packets received, forgets received and sections compiled, during the 2 seconds after a face change. Also the number of frames after the swap in which vanilla's visible-section list is empty.
- **Gametest `transferKeepsView`** (in `TransferGameTests`), with a mock player using `TestPlayers.receiveChunks`:
  1. Stand near the UP/EAST edge at ground height, and wait until the home face and its neighbours are loaded and sent.
  2. Record the tracked set, the loaded `ChunkHolder`s, and `seenBy` for one entity on each of A, B and C.
  3. Cross with `FaceTransfers.handleClaim`, then run 100 ticks.
  4. Assert:
     - unwatched ⊆ (old view ∖ new view);
     - churn = 0;
     - no server unload of a chunk that is in the new view;
     - each recorded entity stayed in `seenBy` throughout.
  5. Run it a second time at `h = 60`, and a third time crossing back and forth (ping-pong).
- **Dev client:** a `DevScript` that walks across an edge and back, capturing the counters and a screenshot on each of the first 5 frames after the crossing.

Against today's code, this test should fail. That confirms §1.1.

---

## 3. Plan

### 3.1 Hand over the view in one step

`NeighbourViews.State` gains the **home face** that its virtuals were computed for.

- `NeighbourViews.view(...)` is the single path, called from `ChunkMapMixin` inside `updateChunkTracking`. When the player's current face differs from `state.home`, it recomputes the virtuals for the new position on the spot. It adds the new tickets, removes the old ones and stores the new `State` before returning the view.
  - Adding and removing in the same call is safe. `DistanceManager` only propagates levels in `runAllUpdates`, so a chunk that is in both the old and the new set never sees its level dip.
- This covers every way the home face can change: client claims, server transfers, `/tp`, respawn and the teleport fallback. None of them can build a view from stale virtuals any more.
- The only thing `FaceTransfers.transfer` has to do is keep calling `chunkSource.move(player)` after moving the player, which it already does.
- With this change, A is never out of view. Its new virtual square (§1.3) replaces the old home square in the same diff, and the set-based diff in `ChunkTrackingViewMixin` sends only the difference.

**Done when:** `transferKeepsView` passes at ground level, with unwatched equal to the geometric difference.

### 3.2 Gate per face

Rewrite the gate at `NeighbourViews.java:107` so that it only holds back faces that are new:

- If a face was home or virtual in the previous `State`, its virtual is kept and moves freely. That covers A, C and D after a transfer, and every face while walking.
- A face that was in neither waits for `homeLoaded`. That is the far face coming into view, or every face on first join or after a long teleport.
- `homeLoaded` checks the home square only. After a transfer, B's square near the edge is already loaded through the old B virtual square, so the gate opens within a tick or two for any new far face.

`ownFaceLoadsFirst` keeps passing. Add a case to it: a transfer must not drop any neighbour.

**Done when:** the "dropped all neighbours" counter stays at 0 when walking along an edge and when crossing.

### 3.3 Linger

Squares that leave the view because of a home change are kept for a while. This covers the old home square, and any virtual square whose centre jumped by more than a few chunks.

- `State` gains `lingering: List<Lingering(face, center, untilTick)>`.
  - When a square would be dropped by a home change, it moves to `lingering` instead, for `LINGER_TICKS` (proposed: 200).
  - It ends early once the player is more than view distance + 2 chunks from the crossed edge.
  - It ends at once if the player returns: the same square becomes home or virtual again.
- `CubeTrackingView` includes lingering squares, clipped to their face's footprint like virtual squares. Neighbour faces' chunks are tracked as before, so the client keeps them current.
- **Tickets:** a lingering square holds a loading ticket at the same level, but no ticking ticket. The face the player left keeps its blocks and entities but stops simulating at the outer edge, which matches vanilla's behaviour beyond simulation distance.
- Ping-pong along an edge then costs nothing: each crossing turns a lingering square back into a live one.
- **Cost:** at most one extra square per face, only for `LINGER_TICKS`. Make it a common config value (`transferLingerTicks`, 0 disables it).

**Done when:** `transferKeepsView` passes at `h = 60` and in the ping-pong run.

### 3.4 Re-check entities only where the view changed

- Remove the loop over all tracked entities in `NeighbourViews.tick` (`NeighbourViews.java:116-119`).
- Instead, collect the chunks that `ChunkTrackingViewMixin`'s diff added or removed, and re-check (`updatePlayer`) only the entities in those chunks. Look them up through the level's entity section storage.
- `ChunkMap.move` already re-checks every entity against the player, but it does so before the view changes. Those entity checks therefore see the old view: with §3.1, that old view includes both A's home square and the B virtual square, which is still correct for one tick. The targeted re-check after the diff then settles it.
- Range checks already measure from the nearest of the real and virtual positions (`TrackedEntityMixin`). After a crossing, an entity on A is measured from `U_BA`, which at ground level is within about one chunk of where the player was. So it stays tracked, and the client keeps the same entity instance.

**Done when:** `seenBy` holds steady in `transferKeepsView`, and a tick with a view change costs time in proportion to the changed chunks, not to the level's entity count.

### 3.5 Client

The cache and the meshes need no change once the server stops forgetting. Three smaller items:

- **The first frames after a swap.** If frame capture shows empty frames after `waitAndReset`, keep drawing the incoming area's last `VISIBLE` list until the occlusion graph's first full update lands. That list was chosen in home-space coordinates with the same frustum, so it is the right set to draw.
  - Simpler alternative: skip the occlusion graph for one frame and draw every compiled section within view distance.
- **Area policy.** Keep built `ViewArea`s for up to five faces (home plus four neighbours, plus whichever face last left the view). Release a face's area when the server has forgotten all its chunks, rather than when it stops being a neighbour. Walking back over an edge then never recompiles. Today `AREAS` keeps the opposite face's area implicitly; this makes it a rule.
- **Light and dirt.** When §3.1 to §3.3 work, no forgets or resends reach these paths. The client counters in §2 confirm it: the target is 0 sections compiled for A and B in the 2 seconds after a ground-level crossing, other than ones the player's own block changes cause.

---

## 4. Order of work

| # | Step | Done when |
|---|---|---|
| 0 | Counters, `transferKeepsView` (failing), dev script | Numbers for today's behaviour recorded in `progress.md` |
| 1 | Hand-over in `view()` (§3.1) | Ground-level crossing: unwatched = geometric difference, no unloads |
| 2 | Per-face gate (§3.2) | Gate never drops a loaded face; `ownFaceLoadsFirst` passes |
| 3 | Targeted entity re-check (§3.4) | `seenBy` steady; no full `entityMap` loops on view changes |
| 4 | Lingering squares (§3.3) | `h = 60` and ping-pong runs show zero churn |
| 5 | Client first-frame and area policy (§3.5) | No empty frames; 0 recompiles of A/B after a ground crossing |
| 6 | Full gametest suite, and `-PwithC2me` once phase 8 is reached | All green |

Steps 1 and 2 are small and fix most of the cost. They should land together, because either one alone still loses A or the neighbours.

---

## 5. Risks and open questions

- **Player ticket throttling.** Vanilla adds player tickets through a throttler, but removes them promptly. B's chunks beyond the old virtual square therefore arrive at the throttled rate. That is the same as walking, so it is fine. A's chunks are held by our own tickets, which are not throttled.
- **Lingering memory.** With many players crossing edges, lingering squares add up to one square per player per face. They are bounded by `LINGER_TICKS` and the distance cut-off, and they can be turned off in config.
- **The generation backlog** (§5 note in `progress.md`: about 5× the chunks on a fresh world). Retention does not add generation, but the gate change (§3.2) lets far faces start generating as soon as the home square is loaded, as before.
- **C2ME** replaces the chunk system. The hand-over relies on `updateChunkTracking` and `DistanceManager` tickets, which phase 8 already has to verify.
- **Multiple dimensions.** `State.level` already guards this. A change of dimension is not a transfer and keeps the full reset.
- **Teleports to a non-adjacent face.** §3.1 handles these the same way. Nothing is shared, so the gate (§3.2) applies to every face, which is the correct behaviour.

---

## 6. Outcome

Implemented as planned, with these differences.

- **Tickets match vanilla's levels.** The neighbour tickets used `FULL − player view distance`. Vanilla's player tickets put level 31 (entity ticking) on every chunk within the *server* view distance. The mismatch mattered: on a crossing, a chunk handed from vanilla's ticket to ours dropped out of ticking. When it was promoted again, vanilla's `onChunkReadyToSend` sent it to the player again. Neighbour and lingering tickets are now one ticket at `31 − server view distance` per square, which gives every chunk the same level vanilla's ticket would.
- **Squares on the new home face linger too.** Vanilla adds its own player tickets through a throttle, a few ticks late. Dropping the old neighbour square on the face being entered left its chunks without a ticket for those ticks, long enough for their entities to unload to disk.
- **The gate is "overlaps a square the player has"** (§3.2). A neighbour square is carried over if it shares chunks with the player's old home, neighbour or lingering square on that face. Otherwise it waits for `homeLoaded`.
- **Entity range uses the nearer of two positions.** On a neighbouring face, range is measured from the nearer of the player's virtual (unfolded) position and its true position (`T`). High over an edge, the unfolded position is about `2h` blocks off, so an entity a few blocks away went unsent.
- **Client.** After the area swap, the frame waits for the occlusion graph's first background rebuild, so the new face draws in its first frame. Areas of faces that are no longer neighbours are released once the client holds none of their chunks.
- **Counters.** `-PtransferStats` logs client chunk traffic and visible sections after each crossing (`client.TransferStats`). The server-side numbers come from `RetentionGameTests`.

Measured:

| | Before | After |
|---|---|---|
| `crossingKeepsView` (UP→EAST, `h = 4`): chunks forgotten but still in view | 38 | 0 |
| `crossingBackKeepsView` (UP→SOUTH and back, `h = 60`): forgotten but still in view | 90 of 90 | 0 |
| Either test: chunks sent again, server unloads in view, ticks a nearby entity went unsent | ticks unsent: 100 / 140 | all 0 |
| Dev client, crossing back within the linger window at `h ≈ 47` | 606 chunks received (crossing back after the linger window) | 84 received, 0 already held |
| Dev client, sections vanilla drew in the first frame after a crossing | (not measured) | 756–1050 |
