package g_mungus.alpha_omega.config;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defaults for orbifold worlds whose preset leaves settings out (dedicated servers, gametests), and how long a crossing
 * keeps the view it left.
 */
public final class AlphaOmegaConfig {

    private static final int DEFAULT_LINGER_TICKS = 200;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> SIZE_FACTOR;
    public static final ModConfigSpec.IntValue BAND_CHUNKS;
    public static final ModConfigSpec.IntValue TRANSFER_LINGER_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Orbifold worlds created with level-type alpha_omega:orbifold. A world keeps what it was created with.");
        SIZE_FACTOR = builder.comment("Size factor k: 2, 4 or 8. The world is 3840·k blocks round east to west.")
            // A mutable list: the check runs on a missing value (null) too, which an immutable list refuses to look up.
            .defineInList("sizeFactor", OrbifoldSettings.DEFAULT.sizeFactor(), new java.util.ArrayList<>(OrbifoldGeometry.SIZE_FACTORS));
        BAND_CHUNKS = builder.comment("Depth of the band of copied blocks past each seam, in chunks.")
            .defineInRange("bandChunks", OrbifoldSettings.DEFAULT.bandChunks(), OrbifoldGeometry.MIN_BAND_CHUNKS, OrbifoldGeometry.MAX_BAND_CHUNKS);
        TRANSFER_LINGER_TICKS = builder.comment("How long, in ticks, chunks a player's crossing of a seam took out of view stay loaded and sent, so",
                "crossing back costs nothing. 0 forgets them at once.")
            .defineInRange("transferLingerTicks", DEFAULT_LINGER_TICKS, 0, 6000);
        SPEC = builder.build();
    }

    private AlphaOmegaConfig() {
    }

    public static int lingerTicks() {
        return SPEC.isLoaded() ? TRANSFER_LINGER_TICKS.get() : DEFAULT_LINGER_TICKS;
    }

    public static OrbifoldSettings defaults() {
        if (!SPEC.isLoaded()) return OrbifoldSettings.DEFAULT;
        return new OrbifoldSettings(SIZE_FACTOR.get(), BAND_CHUNKS.get());
    }
}
