package g_mungus.alpha_omega.client.sky;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the overworld sky (alpha_omega-sky.toml). */
public final class SkyClientConfig {

    public static final String FILE_NAME = "alpha_omega-sky.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ATMOSPHERE;
    public static final ModConfigSpec.DoubleValue EXPOSURE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("sky");
        ATMOSPHERE = builder.comment("Colour the overworld sky, fog and clouds by a physical atmosphere (Rayleigh, Mie and ozone scattering).",
            "Turned off automatically while an Iris shader pack is in use.").define("atmosphere", true);
        EXPOSURE = builder.comment("Brightness of the atmosphere: 1 matches vanilla's noon sky.").defineInRange("exposure", 1.0, 0.25, 4.0);
        builder.pop();
        SPEC = builder.build();
    }

    private SkyClientConfig() {
    }
}
