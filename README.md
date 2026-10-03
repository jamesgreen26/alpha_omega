# Alpha Omega

NeoForge 1.21.1 mod: seamless toroidal world wrapping. Walk far enough east and you come back from the west, with
no teleport, wall or visible seam. See `design_docs/` for the design and `design_docs/implementation-status.md`
for what is implemented.

- World size is chosen on the Create World screen (default 12288 blocks); dedicated servers use
  `config/alpha_omega-common.toml`. The Nether wraps at an eighth of that; the End does not wrap.
- `/wrap info|islands|check|shift` for debugging; `WorldWrap` (in `api`) for other mods.
- Both client and server need the mod. Existing worlds stay unwrapped.

Development:

- `./gradlew build` builds the jar and runs unit tests.
- `./gradlew runClient` launches the game.
- `./gradlew runGameTestServer` runs the gametests.
- `-Dalpha_omega.auditMixins=true` force-loads every mixin target at startup, to catch production-only failures.
