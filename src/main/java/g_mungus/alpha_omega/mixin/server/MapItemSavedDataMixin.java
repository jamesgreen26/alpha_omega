package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
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

    @ModifyVariable(method = "createFresh", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static double alpha_omega$canonCenterX(double x) {
        return Wrap.canon(x);
    }

    @ModifyVariable(method = "createFresh", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static double alpha_omega$canonCenterZ(double z) {
        return Wrap.canon(z);
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$decorationX(double x) {
        return Wrap.nearest(x, this.centerX);
    }

    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double alpha_omega$decorationZ(double z) {
        return Wrap.nearest(z, this.centerZ);
    }

    @ModifyVariable(method = "toggleBanner", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$bannerNearCenter(BlockPos pos) {
        return Wrap.nearest(pos, new Vec3(this.centerX, 0, this.centerZ));
    }
}
