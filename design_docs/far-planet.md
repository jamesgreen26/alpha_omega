# Far Planet

**Drawing the planet beyond render distance**

Target: Minecraft 1.21.1, NeoForge · Status: plan · Follows **Orbifold Implementation** (`orbifold-implementation.md`), to start once its phases are done. Section numbers prefixed `W` refer to the wrapping plan (`alpha-omega-best-wrapping-plan.md`), `OI` to the orbifold implementation, and `RS` to rotated seams.

---

## 0. Summary

Today nothing is drawn past render distance. When a player climbs, the world should look like a planet. The curvature should be the one the projection gives (W §6), and the renderer should cost little.

Four decisions shape the design:

1. **Only the planet renderer curves.** Loaded chunks are never bent, and no vanilla, Sodium or mod shader is touched. This one path also works under Iris.
2. **Curvature starts at the edge of the drawn chunks, not at an altitude.** The planet is the true sphere with a flat disk inserted under the camera. The disk is the bounding circle of the drawn chunks. On the ground, chunks are flat out to render distance, and past them the planet curves down to a gradual horizon. As the player climbs, the disk shrinks, and the planet becomes the true sphere.
3. **The planet's scale matches the local scale.** Its radius in blocks is σ = 1/λ̄, where λ̄ is the sky speed averaged around the camera and bounded near cone points. The planet looks bigger near cone points, which matches the sky turning slower there.
4. **Data comes from a port of the `origin/LOD` pipeline.** The server scans terrain from the noise without generating chunks, and real chunks override the scan. Tiles stream to a client cache.

The renderer is a single camera-centred clipmap in the player's flat frame. Each vertex finds its place in a 2D chart: its flat position near the chunks, its true position on the planet further out, and a blend of the two in between. It then wraps the chart onto the planet shape: flat inside the disk, curving with radius σ outside it. That one mesh covers the junction with real chunks, the horizon seen from the ground and the whole planet seen from space.

---

## 1. Why this is hard

### 1.1 The planet is small

At k = 4:

| Where | 1/λ (blocks per radian) |
|---|---|
| Spawn | 2,286 |
| Mean | 2,978 |
| 2,400 blocks from N | 3,065 |
| 1,200 from N | 5,912 |
| 600 from N | 11,796 |
| 300 from N | 23,588 |
| 100 from N | 70,762 |

On a sphere of radius 2,300:

- The ground drops 57 blocks below flat at 512 blocks.
- From 100 blocks up, the horizon dips about 17°.
- From eye height, the horizon is only about 86 blocks away.

True curvature everywhere would make the ground unplayable to look at. Curving only past the drawn chunks keeps the playable area flat. From 20 blocks above a 512-block disk, the horizon is about 300 blocks past the chunks' edge and dips only about 3°.

### 1.2 The disk can only shrink once the planet's texels are small

Chunks are never bent (§5), so the flat disk distorts the planet: the planet looks like a sphere with a flat spot under the camera, pushed outward by the disk's radius. The disk has to shrink with altitude for the planet to reach its true shape. The planet's data is a height and a colour per texel, so the disk can only shrink without visible loss once those texels are small on screen. At k = 4 (4-block texels, 1080p, 70° field of view), a texel seen from straight above covers:

| Height above sea | True horizon dip | Texel below the camera |
|---|---|---|
| 300 | 28° | ~11 px |
| 600 | 38° | ~6 px |
| 1,000 | 46° | ~3 px |
| 1,800 | 56° | ~2 px |
| 3,000 | 64° | ~1 px |

So the disk keeps the full render distance until about 1,600 blocks at k = 4 and shrinks over the next ~2,000 (§3.4). At k = 2 and k = 8 those heights halve and double, because texels are k blocks wide.

### 1.3 Near and far use different maps

- Real chunks, image views and nearby distant terrain live in the player's flat frame (OI §6). Near a cone point that frame shows a block and its 180° image both (W §7).
- The planet seen from space is the conformal image of the projection, where each place appears once.

The two agree to first order at the camera. Within 512 blocks of spawn they differ by 1–2%. Near cone points the map is locally z², so they differ completely: a flat circle around a cone point covers the sphere twice. Any renderer that draws both needs a blend zone between them, and around cone points that zone can't be made distortion-free.

### 1.4 λ is zero at the cone points

A planet radius of exactly 1/λ(camera) would be infinite at a cone point. The scale has to be averaged over an area (§3.2).

---

## 2. Data

### 2.1 Port of `origin/LOD`

The unmerged torus-era branch already has a full pipeline. Port it into `planet/data/`:

| Class | Change |
|---|---|
| `LodFormat` | A rectangular tile, a/s × b/2s texels, replaces the torus period. Texels keep height (10 bits) + block (13) + biome (9). |
| `LodScanner` | Corners at texel centres of the symmetric grid (§2.2). Scans the tile rectangle, nearest first by orbifold distance instead of `torusDelta`. |
| `LodChunkSampler` | Downsamples 16×16 to (16/s)². Tile chunks only: band and skirt chunks are copies. |
| `LodTileStore` | Unchanged: revisions, disk mirror, real chunks override the scan. |
| `LodServer` | `Wrap.canonChunk` → `OrbifoldGeometry.canonChunk`; "off torus" → "not a tile chunk". |
| `LodStreamer`, `LodPalette`, `LodTileCodec`, payloads | Unchanged, apart from the manifest. It carries `(sizeFactor, s, tile dims, scanner version)` instead of the period. |
| `LodClientStore`, `LodColors`, `LodPreparer` | Unchanged. Planet mips average heights; levels near the ground keep the tallest column. |
| `LodTexture` | Simplified to one 2D texture, no layers. |
| Tests | Port `LodFormatTest`, `LodTileCodecTest`, `LodTileStoreTest`, `LodGameTests`. |

Noise is already invariant under Γ (OI phase 9), so a scan at canonical points agrees across every seam.

### 2.2 The texel grid

The grid is anchored at the tile corner (−a/2, zN), with spacing s dividing 128. Every element of Γ then maps the grid onto itself, because cone points and fold rows sit on multiples of 128 (W §4). With s = k, the client texture is the same size at every k:

| | k = 2 | k = 4 | k = 8 |
|---|---|---|---|
| Tile columns | 25.6M | 102M | 409M |
| Client texel `s` (default) | 2 | 4 | 8 |
| Client texture | 3840 × 1664 R32UI, ~34 MB VRAM with mips | same | same |
| "High" option, s = k/2 | ~130 MB VRAM | same | same |
| Scan corner spacing | 4 | 8 | 16 |
| Scan corners | ~1.6M | ~1.6M | ~1.6M |
| Scan time | 1–2 min on 4 threads, once per world | same | same |
| First download | 4–8 MB, cached; then revisions and chunk-column deltas only | same | same |

---

## 3. Math

### 3.1 Frames and the foot rotation `M`

**Render space** is the camera-relative flat frame: +X east, +Y up, −Z north, at true block scale.

- The camera's storage position, band included, goes to `HexOrbifoldProjection.project` with no `canon` step. The projection is Γ-invariant.
- A fold adds π to the heading, which is exactly the frame turn diag(−1, 1, −1).

**Planet frame:**

- n̂(φ, ℓ) = (cos φ cos ℓ, sin φ, −cos φ sin ℓ)
- ê_N = ∂n̂/∂φ
- ê_E = (∂n̂/∂ℓ) / cos φ

**`M`** maps planet directions into render space. Its rows, with ψ the heading (the compass bearing of world −Z), are:

- t_X = −sin ψ · ê_N + cos ψ · ê_E
- t_Y = n̂(camera)
- t_Z = −(cos ψ · ê_N + sin ψ · ê_E)

Details:

- `M` is built in double precision on the CPU and uploaded as a float `mat3`.
- At N itself the heading is undefined, so keep the last valid `M`.

### 3.2 Scale σ

- λ̂ is the mean, over 16 azimuths, of angle(f(cam), f(cam + D·eᵢ)) / D, with D = ρ + the blend window (§3.5). It costs 17 projections and is recomputed only after the camera moves 8 blocks.
- σ = 1 / max(λ̂, λ_mean / 4), smoothed over about 0.5 s. It resets on teleport but not on a seam crossing, because λ is Γ-invariant.
- At spawn λ̂ is within 1–2% of λ. Near N, with D = 1,280, σ is about 11,000 (at most ~5× its spawn value).
- **Optional, recommended:** once the disk has shrunk (§3.4), blend σ toward σ₀ = 1/λ_mean as the camera climbs from about 4,000 to 8,000, so the planet stops changing size in space.

### 3.3 The planet's shape: a sphere with a flat disk inserted

The planet is drawn in a 2D **chart** about the camera's foot point. A chart point c, at radius r = |c| in direction ĉ, with terrain height h above sea, goes to:

```
e   = max(r − ρ, 0)                       distance past the disk's edge
xz' = ĉ · ( min(r, ρ) · τ(e) + (σ + h) · sin(e/σ) )
y'  = (σ + h) · cos(e/σ) − σ − A
```

- A is the camera's height above sea level, and ρ is the disk's radius (§3.4).
- Inside the disk (e = 0) this is the flat frame exactly: xz' = c, y' = h − A. Real chunks fill the disk, so the junction with them is exact, with no step and no kink.
- Past the edge, the surface curves down with radius σ. In every direction it is a circle of radius σ tangent to the disk at its edge, so the whole surface is round about the vertical axis through the camera.
- **Self-correcting:** as ρ → 0 the shape becomes the true sphere of radius σ, with the foot point straight down.
- **What the disk costs:** the planet looks like a sphere with a flat spot under the camera, whose rest is pushed outward by ρ. On the ground at render distance 32 that is about 22% (512/2300), but there the far side is hidden anyway. In space the disk has shrunk (§3.4). The camera is always on the shape's axis, so the outline always looks round.
- **Closing the far side:** τ(e) = 1 − smoothstep(0.55πσ, 0.9πσ, e) shrinks the inserted disk back to a point, so the antipode closes. From a camera on the axis, the visible part of any circle ends before e = πσ/2, so the taper is never seen.

### 3.4 The disk's radius ρ

ρ is the circle that bounds the drawn chunks, so every drawn chunk lies on the flat part:

ρ = clamp(ρ_view(A), ρ_min, ρ_max)

- ρ_max is the circumradius of the chunk columns drawn at render distance. Columns inside it that aren't drawn yet show the planet's flat part, which is the same picture.
- ρ_view(A) = ρ_max · (1 − smoothstep(A_s, A_e, A)), with A_s = 0.9·D_s, A_e = 2·D_s, and D_s = s / (ε · pixel angle). D_s is the distance at which a texel covers ε pixels, with ε ≈ 2. At k = 4 on 1080p and 70°: D_s ≈ 1,770, A_s ≈ 1,600, A_e ≈ 3,500.
- ρ_min ≈ 32: a few chunks around the camera always stay real.
- The chunk clip (§5) follows ρ with a little hysteresis, so sections drop out one ring at a time. The planet is already drawn under them, so a dropped ring shows terrain, not sky.

Nothing else depends on altitude. On the ground the disk is the render distance and the planet curves past it. In space the disk is small and the planet is true.

### 3.5 Vertex formulas

These live in one GLSL include (`planet_proj.glsl`), mirrored in Java (`PlanetMath`) for tests. For a clipmap vertex at flat point p, with q = p − camera and terrain height h′ above sea:

**Flat chart (near):** c_flat = q.xz, with height h′. This is the player's frame, with the same copies near cone points that image views show.

**True chart (far):**

1. u = ℘-direction(p) on the GPU, then v = M·u.
2. θ = 2·atan2(|v.xz|, 1 + v.y): the angle from the foot point. It has no cancellation near the foot point and is defined at the antipode.
3. ê = v.xz / |v.xz|: the true bearing.
4. c_true = σθ · ê, the azimuthal-equidistant chart of the true projection about the foot point.
5. Heights are scaled by k_h = clamp(σλ(p), 0.25, 2), with λ(p) from the same series. The surface is then the honest conformal image, and terrain near cone points shrinks as the projection shrinks it.

**Blend:**

- w = smoothstep(ρ + b, ρ + 3b, |q.xz|), with b = clamp(0.3·λ/|∇λ|, 64, 512). λ/|∇λ| comes from a finite difference of `skySpeed` and is roughly the distance to the nearest cone point.
- c = mix(c_flat, c_true, w), h = mix(h′, k_h·h′, w).
- The vertex goes to the shape in §3.3 at (c, h).

Why it holds together:

- Both charts measure distance along the ground from the camera, so they agree to first order. Past the edge, a true point at angle θ lands at distance σθ − ρ beyond it, as its flat counterpart does at |q| − ρ. The blend only has to absorb the conformal distortion: 1–2% within 512 blocks of spawn.
- The blend happens in the 2D chart, never in 3D. Every vertex lies exactly on the planet's shape, and the mix is a continuous function of p on the plane, so the surface is continuous everywhere, cone points included.
- Nothing depends on altitude, so the blend window never moves when the player climbs.

### 3.6 Choosing a copy

Where w > 0.5, a fragment is kept only if its flat point p is the nearest image of itself to the camera:

- Test translations by the 6–12 shortest lattice vectors.
- Test half turns about the 4–9 half-lattice points (N + ½Λ) nearest the midpoint of p and the camera.
- That is about 20 distance tests per fragment.

Points on either side of the cut are equally far from the camera, so they come from the same clipmap level and land on the same chart point. The cut is seamless. Copies being dropped fade out with dithered discard over w ∈ [0.3, 0.7].

### 3.7 Precision

- Float32 at 10⁵ blocks resolves ~0.008 blocks. The direction error is ~6·10⁻⁸ σ, under 0.005 blocks even at σ = 7·10⁴.
- ζ per vertex = ζ_cam (reduced in double on the CPU) + q/a in float. The error is ~10⁻³ blocks.
- The ℘ series in float needs only |n| ≤ 3, since e^{−2π · 0.866 · 3} ≈ 8·10⁻⁸. That is 7 terms, ~21 transcendentals.
- **Depth:** reuse `LodRenderer.withDepthRange`, with far = √((σ + A)² − σ²) + ρ + 512 and near = max(16, half the nearest drawn distance). At far = 8·10⁴ and near = 100, 24-bit depth resolves ~4 blocks at the far end, which is sub-pixel. macOS GL 4.1 has no `glClipControl`, so there is no reverse-Z; none is needed.

---

## 4. Mesh

One mesh, built once, like `LodMesh`: nested square rings in the flat frame with snapped origins. Height and colour come from the texture through a shader-side `canon`.

- **Geometry:** a shared-vertex heightfield, not voxel columns, with 64–96 cells across each ring. The finest cell follows altitude: at least A/100, so the limb has ~40-block segments at A = 8,000.
- **Reach:** the rings reach the circumradius of the Dirichlet domain plus a margin, about 0.55a. Measure the circumradius in phase P0.
- **Seams:** geomorph between levels, and sample mip heights bilinearly.
- **Masking:** keep LOD's mask. Cells under drawn chunks sink, and their walls act as skirts.
- **Culling:** backface culling, since the projection preserves orientation and the far side faces away. Patches are frustum-culled on the CPU by running `PlanetMath` on their corners, with a margin for height.
- **Shader `canon`:** shift by lattice vectors into the strip [zN, zN + b), apply R_S as (x, z) ↦ (a/2 − x, 2zS − z), then wrap x.
- **Fallback** if GPU ℘ misbehaves on a driver: a precomputed direction texture (octahedral RG16, 960 × 416, ~1.6 MB, bicubic).

**Alternatives rejected:**

- **A static sphere quadtree plus a separate junction ring.** Two meshes overlap in the blend zone, which means discard radii, depth bias and z-fighting. The ring needs a CPU ℘ rebuild every 64 blocks, and every data change re-runs ℘ for its patch.
- **A cube-sphere.** It would need an inverse projection to fetch data, and there isn't one. Flat-tile sampling is nearly uniform on the sphere anyway (λ max/mean = 1.33), and only over-samples harmlessly at cone points.

---

## 5. Loaded chunks

- **Never bent.** Vanilla, Sodium and mod shaders are untouched. Raycasts, culling, entities, particles and block entities are unchanged.
- **Clipped to ρ.** Sections, entities and block entities outside the disk are not drawn. This reuses the draw-once clip (`ImageGeometry.homeDraws`, `ImageRenderer.hideDead` and `shows`, `SectionCollectorMixin`) plus one radius predicate. On the ground ρ covers the render distance, so nothing is clipped.
- **Clouds:** vanilla's cloud plane is flat. Fade it out as ρ shrinks, so a flat sheet doesn't hang over the curved planet. A cloud layer on the planet itself is deferred.
- **Iris:** the same path. Only compositing under a pack is unknown (§10).

**Alternatives rejected:**

- **Bending the terrain shaders** (vanilla and Sodium vertex shaders patched at compile time). It keeps more real chunks at mid altitude, but it is fragile with Flywheel, Create, mod shaders and Sodium updates. It also needs culling changes and a separate path for Iris. If mid-altitude views look poor, s = k/2 texels are the cheaper fix.
- **Curvature that starts at a fixed altitude.** The ground then shows no horizon at all, and the onset is a visible change during a climb. Starting at the disk's edge gives a horizon on the ground and needs no altitude schedule.

---

## 6. Drawing, sky and fog

- **Pass:** a `RenderLevelStageEvent.AFTER_SKY` listener (none exists on this branch yet). Draw the clipmap with its own depth range, then clear depth, as LOD does.
  - The planet occludes stars, sun and moon.
  - Real and image terrain always draw over it.
  - Holes left by unloaded chunks show the planet underneath.
- **Lighting:**
  - Normals come from height differences in the local frame (t_X, n̂, t_Z) at p. The projection is conformal, so slopes are unchanged.
  - The sun is `SkyState`'s local sun vector, which is already in render space. A per-fragment dot product gives the right local sun elevation everywhere, so the terminator on the planet matches `LocalSky` automatically.
  - Exposure from `SkyState`, clamped in space so the day side doesn't overexpose against a black sky.
- **Haze:**
  - Mix toward the atmosphere's sky-view texture by (1 − e^{−L/L₀}) × `SpaceFade`.
  - A `RenderFog` push-out, as in LOD, so chunks don't fog out at their edge and vanilla's cylindrical fog doesn't hide ground far below.
- **Limb:**
  - A uniform `LimbDip` in `atmo_sky` and `atmo_skyview`: the angle below horizontal of the shape's horizon, computed from §3.3 on the CPU. It remaps elevation [−LimbDip, π/2] onto [0, π/2], so the horizon glow sits on the planet's horizon, on the ground as well as in space.
  - In space, the planet's own fragments add a thin rim.
- **Other sky pieces:** vanilla's dark lower disc is only drawn below y = 63, so it doesn't conflict.
- **Other LOD mods:** the pass turns itself off when Distant Horizons or Voxy is loaded (LOD's `otherLodActive`).

---

## 7. Budgets

| | Target |
|---|---|
| GPU | ~0.1 ms vertex work, plus 0.1–0.3 ms fragment work for a full-screen planet. Measured with LOD's `GL_TIME_ELAPSED` timer. |
| CPU per frame | Uniforms, ring origins, the mask (once per tick), λ̂ (≤ 40 µs, only after moving). Under 0.2 ms. |
| VRAM | ~34 MB |
| Client RAM and disk | Compressed cache under 10 MB |
| Server | A scan of 1–2 min once per world, on background threads; a store of 15–25 MB on disk |

---

## 8. Phases

Each phase lists its work and when it is done. Gametests run as subsets with `-Pgametests=<Class>[.method]`, and the full suite at the end of each phase. Dev clients are muted.

### Phase P0: Math

**Tasks:** pure Java, no Minecraft types:

- `planet/PlanetFrame`: `M`, σ and λ̂, ρ, the blend window
- `planet/PlanetMath`: Java mirrors of the GLSL (charts, blend, shape, taper)
- a float-emulated ℘
- `planet/DirichletDomain`
- `planet/CanonTexel`

**Done when** unit tests show:

- `M` sends the foot point straight down and world −Z to bearing ψ, at spawn, near N, F, E and W, and in fold images.
- The shape is the flat frame inside ρ, C¹ at the edge, and the true sphere of radius σ at ρ = 0. The antipode closes, and the taper starts beyond what a camera on the axis can see.
- The true chart agrees with the flat chart within 2% at 512 blocks from spawn.
- Float ℘ is within 10⁻⁵ rad of double.
- The texel grid is Γ-symmetric.
- The Dirichlet domain is a fundamental domain (area ab/2, every orbit hit once). Its circumradius is recorded for k = 2, 4 and 8.

### Phase P1: Server data

**Tasks:** the port in §2.1.

**Done when** gametests show:

- every tile texel is filled;
- texels match across every seam type and at the cone points;
- a real-chunk edit patches the store and reaches a mock client as a delta;
- the store size is within budget at k = 2, 4 and 8.

### Phase P2: Client store and the curved horizon

**Tasks:** the client cache, the texture, the clipmap with the flat chart only (w = 0) wrapped onto the shape, the fog push-out, `LimbDip`.

**Done when** `DevScript` screenshots at ground level, at spawn, the east seam, both folds and 300 blocks from N, show:

- distant terrain continuous with the chunks, with no seam or step at the disk's edge;
- a horizon curving down past the chunks, with the atmosphere's glow on it;
- doubled copies near N, as image views show them;
- under 0.5 ms GPU.

### Phase P3: True planet

**Tasks:** GPU ℘, the true chart, the blend, copy selection, the taper, `M` and σ.

**Done when** screenshots from a fixed ρ = ρ_min at A = 3,000 and 8,000, over spawn, N and E, show:

- the tetrahedral layout, with F, E and W 109.47° from N;
- no seams and no doubled surfaces;
- the terminator matching the sun.

### Phase P4: Disk schedule

**Tasks:** ρ(A), the radius predicate in the vanilla and Sodium clips with hysteresis, the cloud fade.

**Done when** climbs through A = 600, 1,600, 2,500, 3,500 and 8,000, with and without Sodium, show:

- no step at the disk's edge at any height;
- no sky through clipped rings;
- the planet settling into a true sphere, with no popping beyond single section rings.

### Phase P5: Atmosphere

**Tasks:** haze, rim, exposure clamp.

**Done when** a climb from the ground to 8,000 is smooth, and day, dusk and night shots look right.

### Phase P6: Compat and polish

**Tasks:**

- Distant Horizons, Voxy and Iris gates.
- F3 lines: σ, ρ, the blend window and GPU ms.
- Client config `alpha_omega-client.toml`: on or off, texel quality, ε, σ blend in space.
- Mixin audit.

**Done when** the full suite and the mixin audit pass with `-PwithSodium`.

```
P0 ─▶ P1 ─▶ P2 ─▶ P3 ─▶ P4 ─▶ P5 ─▶ P6
```

---

## 9. Files

**New:**

- `planet/PlanetFrame`, `planet/PlanetMath`, `planet/DirichletDomain`, `planet/CanonTexel`
- `planet/data/*` (the port)
- `network/Planet*Payload`
- `client/planet/PlanetRenderer`, `PlanetMesh`, `PlanetTexture`, `PlanetClientStore`, `PlanetColors`, `PlanetPreparer`
- Shaders: `include/planet_proj.glsl` (℘, `canon`, charts, blend, shape), `core/planet_terrain.{vsh,fsh,json}`

**Touched:**

- `AtmosphereRenderer`, `atmo_sky.fsh`, `atmo_skyview.fsh` (`LimbDip`)
- `SkyState` (exposure clamp)
- `ImageGeometry`, `SectionCollectorMixin`, `ImageRenderer.hideDead` (the radius predicate)
- vanilla cloud rendering (fade with ρ)
- `AlphaOmegaClient` (registration)
- `DevScript` (ground and altitude shots)

**Reused unchanged:** `HexOrbifoldProjection.project` and `skySpeed`, `OrbifoldGeometry.canon` and `conePoints`, `SpaceFade`, `ShaderPackCheck`.

---

## 10. Risks

| Risk | Where | Mitigation |
|---|---|---|
| Smear in the blend zone near cone points, where the true chart covers the sphere twice | P3 | Inherent to the topology. A window that shrinks toward cone points, dithered fades, extra haze in proportion to \|c_true − c_flat\|/\|q\|. |
| The planet looks inflated by ρ/σ (up to ~22% at render distance 32) before the disk shrinks | P4 | Hidden on the ground, where the far side can't be seen. In space the disk has shrunk. |
| Mid-altitude views (A < A_s) show the planet's coarse texels just past the chunks | P2 | Only past the render distance, where vanilla showed nothing. The s = k/2 option. |
| Popping as the disk shrinks | P4 | The planet is drawn underneath; hysteresis on ρ; whole section rings at a time. |
| GPU ℘ differs between drivers | P3 | The float-emulation test, and the direction-texture fallback. |
| The planet grows up to ~5× near cone points | P3 | The optional blend to σ₀ in space. |
| The far pass doesn't composite under an Iris pack | P6 | Spike early; turn it off under packs if so. |
| Popping when clipmap rings shift at altitude | P2, P3 | Geomorphing and bilinear mip sampling. |
| Exposure and `SpaceFade` fight in space | P5 | Clamp exposure; tune from screenshots. |

---

## 11. Open decisions

| # | Question | Recommendation |
|---|---|---|
| 1 | Should the disk also shrink a little on the ground, for a more honest horizon? | **No.** Keep the full render distance; chunks are what players play in. |
| 2 | Blend σ toward σ₀ in space? | **Yes**, from about 4,000 to 8,000, once the disk has shrunk. |
| 3 | Height scaling on the true chart | **k_h = σλ, clamped to [0.25, 2].** |
| 4 | Default texel size | **s = k**, with s = k/2 as a "high" option (which also halves A_s). |
| 5 | On-screen tolerance ε for shrinking the disk | **~2 px**, configurable. |
| 6 | ρ_min | **~32 blocks** (two chunks). |
