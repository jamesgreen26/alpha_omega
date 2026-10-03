package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Maps: centers are stored canonically (R1; the largest map grid, 2048 blocks, divides W), and markers are
 * placed by their nearest-image displacement from the center (R5).
 */
@Mixin(MapItemSavedData.class)
abstract class MapItemSavedDataMixin {

    @Shadow
    @Final
    public int centerX;

    @Shadow
    @Final
    public int centerZ;

    @Shadow
    @Final
    public ResourceKey<Level> dimension;

    @ModifyVariable(method = "createFresh", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static double alpha_omega$canonCenterX(double x, @Local(argsOnly = true) ResourceKey<Level> dimension) {
        return Wrap.of(dimension).canon(x);
    }

    @ModifyVariable(method = "createFresh", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static double alpha_omega$canonCenterZ(double z, @Local(argsOnly = true) ResourceKey<Level> dimension) {
        return Wrap.of(dimension).canon(z);
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$decorationX(double x) {
        return Wrap.of(this.dimension).nearest(x, this.centerX);
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double alpha_omega$decorationZ(double z) {
        return Wrap.of(this.dimension).nearest(z, this.centerZ);
    }

    @ModifyVariable(method = "toggleBanner", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$bannerNearCenter(BlockPos pos) {
        return Wrap.of(this.dimension).nearest(pos, new Vec3(this.centerX, 0, this.centerZ));
    }
}
