# Local Sky and Atmosphere

The overworld is a torus, but it should feel like a planet. Two features make it so:

- **Time zones and latitude.** X is longitude: local solar time runs a full day per lap, ahead to the east. Z walks along a meridian, a full circle per lap: the sun's path tilts steadily as you travel north or south, over a pole and down the far side of the planet, and round again. Stars wheel about a celestial pole that rises with latitude, and near the poles the sun circles the horizon. Gameplay follows the local sun, not a global clock.
- **A physical atmosphere.** Sky, fog and cloud colours come from Rayleigh, Mie and ozone scattering (Hillaire 2020, *A Scalable and Production Ready Sky and Atmosphere Rendering Technique*), so sunsets redden, twilight turns violet and the horizon pales.

Both apply to the overworld only, and not to dimensions with fixed time. An unwrapped world keeps the vanilla sun path (the atmosphere still applies).

## 1. The planet's coordinates (`sky/LocalSky`)

Everything is periodic in the world size `W`, so any image of a position has the same sky, and nothing needs laps.

- **Longitude**, in turns: `lon = frac(x/W + 0.5) − 0.5`. It is 0 at `x = 0`. The date line is half a lap away, which is the seam in centered worlds.
- **Local clock**: `dayTime + 24000·lon`. East is ahead, so the sun rises toward +X, as in vanilla.
- **Meridian angle** `θ = 2π·(−z/W)`, a full circle per lap. It is 0 at `z = 0` (the equator), 90° a quarter lap toward −Z (the north pole), 180° half a lap away (the equator on the far side of the planet) and −90° at three quarters (the south pole). The observer's up direction tilts by θ about the east-west axis, so walking north or south turns the sky steadily the same way, over the poles and round. (An earlier version used a triangle wave of latitude, which made the sky zig-zag back at each pole.)
- **Geographic latitude and longitude** are `asin(sin θ)` and the time zone plus half a turn on the far side (`cos θ < 0`). On the far side, +X is geographic west and −Z south, so the sun rises in −X there.
- **Local clock**, for clock-shaped consumers, gains 12 hours on the far side. It jumps by 12 hours when crossing a pole, as on a real globe where every time zone meets; the sun itself moves continuously.
- **No seasons**: the declination is 0 (every day is an equinox).
- **Sun direction.** `sun = (−sin H, cos θ·cos H, sin θ·cos H)`, with +X east on the near side, +Y up and −Z toward the north pole. The sky renderer's `XP(timeOfDay·360°)` becomes `Rz(−θ)·Rx(H)`, which tilts the frame the sun, moon and stars are drawn in.
- **Hour angle** `H`. With `c = dayTime + 24000·lon`, `H = 2π·(tod(c) + w·Δ)`, where `tod` is vanilla's eased time of day, `Δ = tod(c + 12000) + ½ − tod(c)` (wrapped) and `w = (1 − cos θ)/2`. At the equator this is vanilla exactly; at the far equator it is vanilla's easing about far-side noon, so both sides keep vanilla's longer days. Near-side noon stays at `c = 6000` and far-side noon at `c = 18000` at every θ.
- **Equivalent time of day** `t_eq`. This is the vanilla time of day that puts the sun at the same height: `acos(sunY)/2π`, mirrored while the sun rises. Every vanilla consumer that only uses `cos(2π·t)` (sky darkening, star brightness, sky and cloud brightness, the daylight detector) gets the right value at any latitude when given `t_eq`. At the equator `t_eq` is the time of day itself.
- **Sky darkening at a position** is vanilla's `updateSkyBrightness` with `sunY` in place of `cos(2π·t)`. Rain and thunder stay global. `isDay` is `skyDarken < 4`, which needs `sunY > 0.068` (about 3.9°). Above about 86° latitude the sun never gets that high, so it is always night there for gameplay, by design.

Two notions of local time are used:

- **Clock-shaped** consumers read the local clock: villager schedules and the clock item.
- **Curve-shaped** consumers read `t_eq`.

Day counts (moon phase, restocks, patrol start, regional difficulty) stay global.

## 2. Gameplay (`mixin/server/time/`)

Global `Level.isDay()`, `getSkyDarken()` and `getDayTime()` are unchanged. They describe the prime meridian on the equator, so other mods see vanilla. The vanilla consumers that have a position read the local sun instead:

| Consumer | How |
|---|---|
| Brightness: monster spawn darkness, sunburn brightness, spiders, endermen, slimes, bats, frosted ice, saplings, grass, the HUD | `Level` overrides `getMaxLocalRawBrightness(pos)` to use the local sky darkening |
| Undead burning, endermen fleeing daylight | `isDay()` at the mob |
| Beds | `isDay()` at the bed (NeoForge's lambda in `startSleepInBed`), waking at the sleeper (`Player.tick`) |
| Night skip | Global time advances to the first daylight at the sleepers' circular-mean time zone and meridian angle: local clock 0 if the sun is up there by then, otherwise later that morning, otherwise local noon. The result still passes through NeoForge's `SleepFinishedTimeEvent`. Sleepers far apart in longitude may wake at someone's dusk. |
| Phantoms | The global night gate is skipped; each player's spawn check requires local night |
| Patrols | The global day gate is skipped; the chosen player must be in local daylight |
| Daylight detectors | Local darkening and `t_eq` at the detector |
| Villager schedules | `Brain` remembers its owner (from its last tick) and shifts the schedule's day time by the owner's time zone; new villager brains start on the villager's local clock |
| Bees and hives, foxes (ambient, shelter, sleep), drowned (targets, beach, water, swimming up), `FleeSunGoal`, `RestrictSunGoal`, village goals, iron golem flowers, turtle egg hatching | `isDay()` / `isNight()` / time of day at the entity or block |

Not converted: wandering trader invisibility, cat morning gifts, village sieges and the `time_check` loot predicate. These are rare, and they use the prime meridian.

## 3. Client sky (`mixin/client/`, `client/sky/`)

- **Celestial rotation.** In `LevelRendererSkyMixin`, the fifth `Axis.rotationDegrees` in `renderSky` (the `XP(timeOfDay·360)`) becomes `Rz(−θ)·Rx(H)`. Sun, moon and stars share that frame. The sun and moon quads tilt with it (at 45° latitude the square sun rises as a diamond).
- **Lightmap, star, sky and cloud brightness** follow `t_eq` at the camera (`ClientLevelSkyMixin`). Vanilla's sunrise glow and fog tint point at the sun's azimuth.
- **F3** shows `Sun: hh:mm day d, lat, lon, alt, az`. `/wrap time` reports the same on the server, and `/wrap time set <ticks>` sets global time so that the local clock here reads `ticks`.

### 3.1 Atmosphere

- **The model** is `sky/AtmosphereModel` and `sky/AtmosphereParams`: Earth-like and pure Java.
  - A 256×64 transmittance table (Bruneton's parameterisation).
  - A 32×32 multiple-scattering transfer table (Hillaire's isotropic approximation).
  - A 30-step view-ray march.
  - The sea is 200 m up, and each block above sea level adds a metre.
- **Light** (`sky/SkyColors`):
  - The sun has illuminance 1.
  - The moon sits at the antisolar point at 1/2500 of the sun's illuminance (far brighter than reality, so nights are playable), scaled by vanilla's moon phase brightness.
  - A small airglow radiance is added everywhere.
- **Display.**
  - **Exposure** adapts as the sky darkens, by `(Y_noon / Y)^0.5`, up to 6×, smoothed over about a second.
  - **Tonemapping** is `1 − exp(−e·Y)` on luminance. It keeps the hue: a channel above 1 scales the colour down rather than bleaching it.
  - **Gamma** is 2.2 at the end.
  - Calibrated this way, noon at 50° elevation is (0.50, 0.69, 0.99) against vanilla's plains sky of (0.47, 0.65, 1.00), and the noon horizon is (0.78, 0.92, 1.0) against vanilla's fog of (0.75, 0.85, 1.0).
- **Where the colours go.**
  - **Vanilla's sky colour** (the biome sample in `getSkyColor`) becomes the atmosphere's average at 50° elevation, with day dimming held at noon. Vanilla's rain, thunder and lightning still tint it.
  - **Fog** is the horizon (+2°) toward the horizontal look direction, injected just before vanilla's rain darkening in `FogRenderer.setupColor`. It is blended toward the sky at short render distances as vanilla does. Void darkness, mob effects, night vision and `ViewportEvent.ComputeFogColor` then apply. Water, lava and powder snow fog are untouched.
  - **Clouds** are tinted by the sun (and moon) transmittance at y = 192 plus sky light: white at noon, orange at sunset, blue-grey at night.
  - **Stars** fade with the dome's displayed luminance.
  - **Vanilla's sunrise fan** is off.
- **GPU sky** (`client/sky/AtmosphereRenderer`, shaders `atmo_*` and `include/atmosphere.glsl`).
  - The CPU tables are uploaded once as float textures, so CPU and GPU share one model.
  - Each frame, a 192×108 pass fills Hillaire's sky-view table: azimuth from the sun (finer toward it) by elevation (finer at the horizon), for sun plus moon plus airglow.
  - A full-screen pass then replaces vanilla's sky dome draw. It looks up the table per pixel, tonemaps with the CPU's exposure, applies vanilla's weather tints, and fades into the fog colour near the horizon.
  - Sun, moon, stars and the dark lower disc stay vanilla, drawn over it.
  - A screenshot pixel matched the CPU model to three decimal places.
- **When it applies.** The atmosphere is used in the overworld when `alpha_omega-sky.toml` has `atmosphere = true`, the sky type is normal, and no Iris shader pack is in use. Underwater, the vanilla dome is drawn.

## 4. Verification

- **Unit tests.**
  - `LocalSkyTest`: the meridian loop, geographic folding over the poles, the sun moving smoothly while walking over a pole, the far equator 12 hours apart with vanilla day lengths, periodicity, the equator matching vanilla's rotation, the celestial quaternion, east sunrise, south noon, the poles, sky darkening thresholds, the circular mean across the date line, and the sleep target (always in daylight, and vanilla at the equator).
  - `AtmosphereModelTest`: the table parameterisation round-trips, zenith transmittance against analytic optical depth, noon against vanilla blue, red sunsets, monotonic dusk, dark-but-starry nights, cloud tints.
- **Gametests** (`LocalTimeGameTests`, one batch each). These run on a force-loaded platform at the date line, where local time is 12 hours from global: brightness, zombies burning and not burning, daylight detectors, villagers resting, and beds refused by day, accepted by night, then skipping to local morning.
- **In game.** Walking north from the equator over the pole to the far equator at a fixed time, screenshots every 22.5° show the sun sinking toward the pole, on the horizon there, then night with the moon climbing on the far side. Star trails were fitted from screenshots: the stars rotate about the screen centre when looking at the pole, at 45° and at 80°. Sky screenshots cover noon, sunset, dusk, midnight, rain and underwater.

## 5. Compatibility

- **Iris shader packs** own the sky, so the atmosphere turns itself off. Packs still place the sun by vanilla's global sun angle, and the time-zone rotation does not reach them, so with a pack the drawn sun can disagree with the local sun used for gameplay. Fixing that would need an optional compat mixin into Iris's celestial uniforms.
- **Other mods** that read global `isDay`, `getSkyDarken` or `getDayTime` see the prime meridian. Mods can call `LocalSky.isDay(level, pos)` / `LocalSky.localDayTime(level, x)` for the local values.
- **Distant terrain (the LOD branch)** reads vanilla's fog colour through its shader's `FogColor`, so it picks up the atmosphere's fog unchanged. Its client config uses `alpha_omega-client.toml`; the sky's is a separate file, `alpha_omega-sky.toml`, so the two do not collide.
