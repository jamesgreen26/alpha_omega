# Alpha Omega

NeoForge 1.21.1 mod: a cube planet. Six faces of `W × W` chunks, each an ordinary flat world with its own gravity
and time zone, joined at the edges. See `design_docs/cube-world.md` for the design and `design_docs/progress.md`
for where it stands.

Development:

- `./gradlew build` builds the jar and runs unit tests.
- `./gradlew runClient` launches the game (`-PwithSable` / `-PwithC2me` add those mods to the dev run).
- `./gradlew runGameTestServer` runs the gametests.
- `-Dalpha_omega.auditMixins=true` force-loads every mixin target at startup, to catch production-only failures.
