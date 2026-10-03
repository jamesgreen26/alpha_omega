package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * R5: "is this position in a structure" tests the image of the position nearest the structure (structure mob
 * spawns, fortress/monument rules, locating).
 */
@Mixin(StructureManager.class)
abstract class StructureManagerMixin {

    @Shadow
    @Final
    private LevelAccessor level;

    @WrapOperation(method = {"getStructureAt", "structureHasPieceAt"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;isInside(Lnet/minecraft/core/Vec3i;)Z"))
    private boolean alpha_omega$insideNearestImage(BoundingBox box, Vec3i pos, Operation<Boolean> original) {
        Wrap wrap = Wrap.of(this.level);
        BlockPos center = box.getCenter();
        return original.call(box, new BlockPos(wrap.nearestBlock(pos.getX(), center.getX()), pos.getY(), wrap.nearestBlock(pos.getZ(), center.getZ())));
    }
}
