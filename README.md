# Alpha Omega

NeoForge 1.21.1 mod: seamless toroidal world wrapping. See `design_docs/` for the design.

Status: phase 1 (periodic storage, single implicit frame). The world period is hardcoded in
`wrap/Wrap.java` (`W = 3072` blocks) and applies to every dimension.

- `./gradlew build` builds the jar and runs unit tests.
- `./gradlew runClient` launches the game.
- `./gradlew runGameTestServer` runs the gametests.
