package g_mungus.alpha_omega.config;

import g_mungus.alpha_omega.wrap.WorldWrapSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults for worlds created without the Create World screen (dedicated servers). */
public final class AlphaOmegaConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue WORLD_PERIOD;
    public static final ModConfigSpec.BooleanValue WRAP_NETHER;
    public static final ModConfigSpec.BooleanValue WRAP_END;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Wrapping for newly created worlds. Existing worlds keep what they were created with (alpha_omega.json in the world folder).");
        WORLD_PERIOD = builder
            .comment("World size in blocks: walk this far east to come back from the west. 0 creates unwrapped worlds.",
                "Must be a multiple of 128; multiples of 12288 keep vanilla continent sizes (49152 for Large Biomes).")
            .defineInRange("worldPeriod", WorldWrapSettings.DEFAULT_PERIOD, 0, 3_000_000);
        WRAP_NETHER = builder.comment("Wrap the Nether too, at an eighth of the world size, so portals link one to one.").define("wrapNether", true);
        WRAP_END = builder.comment("Wrap the End at the world size. Its outer islands do not tile, so there will be a visible seam.").define("wrapEnd", false);
        SPEC = builder.build();
    }

    private AlphaOmegaConfig() {
    }

    public static WorldWrapSettings defaults() {
        int period = WORLD_PERIOD.get() / 128 * 128;
        return new WorldWrapSettings(period, WRAP_NETHER.get(), WRAP_END.get(), true);
    }
}
