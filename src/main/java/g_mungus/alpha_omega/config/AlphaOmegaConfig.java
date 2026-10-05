package g_mungus.alpha_omega.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** How long a crossing keeps the view it left. */
public final class AlphaOmegaConfig {

    private static final int DEFAULT_LINGER_TICKS = 200;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue TRANSFER_LINGER_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
}
