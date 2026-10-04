package g_mungus.alpha_omega.client.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.Level;

/**
 * Flying above the build height leaves the sky behind, as in Genesis: the sky, fog and sunrise glow fade linearly to
 * black between the build height and {@link #SPACE_Y}, while the stars brighten to full and rain thins out.
 */
public final class SpaceFade {

    /** The height at which the sky is fully black. */
    public static final double SPACE_Y = 2048.0;

    private SpaceFade() {
    }

    /** How much of the sky is left at height {@code y}: 1 at and below the build height, 0 from {@link #SPACE_Y} up. */
    public static double density(Level level, double y) {
        if (!(level instanceof ClientLevel client) || client.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return 1.0;
        double start = level.getMaxBuildHeight();
        if (y <= start || start >= SPACE_Y) return 1.0;
        return Math.max(0.0, 1.0 - (y - start) / (SPACE_Y - start));
    }

    public static double atCamera(Level level) {
        return density(level, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y);
    }
}
