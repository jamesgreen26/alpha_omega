package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R4: natural spawning picks its position in the chunk's lifted frame, so mobs appear in the players' frame. */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {

    @ModifyExpressionValue(method = "spawnCategoryForChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;"))
    private static BlockPos alpha_omega$liftSpawnPos(BlockPos pos, @Local(argsOnly = true) ServerLevel level) {
        return Frames.lift(level, pos);
    }

    /** No spawning within 24 blocks of any image of the world spawn. */
    @WrapOperation(method = "isRightDistanceToPlayerAndSpawnPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private static boolean alpha_omega$spawnPointNearestImage(BlockPos spawn, Position pos, double distance, Operation<Boolean> original) {
        return original.call(spawn, Wrap.nearest(new Vec3(pos.x(), pos.y(), pos.z()), Vec3.atCenterOf(spawn)), distance);
    }
}
