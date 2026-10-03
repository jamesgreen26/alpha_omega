package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xaero.map.MapWriter;

/**
 * Chunks are written into the map at their canonical tile, whichever image the player sees them at. The chunk itself
 * is still read at the image near the player.
 */
@Mixin(MapWriter.class)
abstract class MapWriterMixin {

    /** Tiles of 4×4 chunks per region side. */
    private static final int TILES_PER_REGION = 8;

    @ModifyVariable(method = "writeChunk", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int alpha_omega$canonTileX(int tileChunkX, @Local(argsOnly = true) Level level) {
        return alpha_omega$canonTile(tileChunkX, level);
    }

    @ModifyVariable(method = "writeChunk", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int alpha_omega$canonTileZ(int tileChunkZ, @Local(argsOnly = true) Level level) {
        return alpha_omega$canonTile(tileChunkZ, level);
    }

    private static int alpha_omega$canonTile(int tile, Level level) {
        int regionPeriod = XaeroWraps.regionPeriod(Wrap.of(level));
        return regionPeriod == 0 ? tile : Math.floorMod(tile, regionPeriod * TILES_PER_REGION);
    }
}
