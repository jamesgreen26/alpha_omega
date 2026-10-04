package g_mungus.alpha_omega.mixin.server.time;

import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Brightness with the sky darkened by the local sun rather than the global one. This one override covers monster
 * spawning darkness, sunburn brightness, spiders, endermen, slimes, bats, frosted ice, saplings and grass, since all of
 * them read {@code getMaxLocalRawBrightness(pos)} (directly or through {@code getLightLevelDependentMagicValue}).
 * {@code Level.isDay()} and {@code getSkyDarken()} themselves stay global: the prime meridian on the equator.
 */
@Mixin(Level.class)
abstract class LevelLocalTimeMixin {

    public int getMaxLocalRawBrightness(BlockPos pos) {
        Level level = (Level) (Object) this;
        return level.getMaxLocalRawBrightness(pos, LocalSky.skyDarken(level, pos));
    }
}
