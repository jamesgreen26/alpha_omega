package g_mungus.alpha_omega.config;

import g_mungus.alpha_omega.cube.CubeSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults for cube worlds whose preset leaves settings out (dedicated servers, gametests). */
public final class AlphaOmegaConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue FACE_CHUNKS;
    public static final ModConfigSpec.EnumValue<CubeSettings.SunAxis> SUN_AXIS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Cube worlds created with level-type alpha_omega:cube. A world keeps what it was created with.");
        FACE_CHUNKS = builder.comment("Width of each face, in chunks.")
            .defineInRange("faceChunks", CubeSettings.DEFAULT.faceChunks(), CubeSettings.MIN_FACE_CHUNKS, CubeSettings.MAX_FACE_CHUNKS);
        SUN_AXIS = builder.comment("The axis the sun turns about: DIAGONAL (every face has days) or POLAR (two faces in twilight).")
            .defineEnum("sunAxis", CubeSettings.DEFAULT.sunAxis());
        SPEC = builder.build();
    }

    private AlphaOmegaConfig() {
    }

    public static CubeSettings defaults() {
        if (!SPEC.isLoaded()) return CubeSettings.DEFAULT;
        return new CubeSettings(FACE_CHUNKS.get(), SUN_AXIS.get());
    }
}
