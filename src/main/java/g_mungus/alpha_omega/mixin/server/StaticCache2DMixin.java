package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Worldgen neighborhood caches are built around a center chunk with unrolled neighbor coordinates, but are also
 * queried with the canonical position of a neighbor ({@code holder.getPos()}). Resolve any image to the one in
 * range. Only worldgen uses this class, and always with chunk coordinates.
 */
@Mixin(StaticCache2D.class)
abstract class StaticCache2DMixin {

    @Shadow
    @Final
    private int minX;
    @Shadow
    @Final
    private int minZ;
    @Shadow
    @Final
    private int sizeX;
    @Shadow
    @Final
    private int sizeZ;

    @ModifyVariable(method = {"get", "contains"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$nearestX(int x) {
        return Wrap.nearestChunk(x, this.minX + this.sizeX / 2);
    }

    @ModifyVariable(method = {"get", "contains"}, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$nearestZ(int z) {
        return Wrap.nearestChunk(z, this.minZ + this.sizeZ / 2);
    }
}
