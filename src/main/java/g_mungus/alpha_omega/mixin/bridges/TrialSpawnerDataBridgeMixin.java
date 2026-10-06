package g_mungus.alpha_omega.mixin.bridges;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.bridge.PlayerBridge;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Trial spawners detect players near any image of their block ({@link PlayerBridge#detect}); both of their detections. */
@Mixin(TrialSpawnerData.class)
abstract class TrialSpawnerDataBridgeMixin {

    @WrapOperation(method = "tryDetectPlayers", require = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector;detect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector$EntitySelector;Lnet/minecraft/core/BlockPos;DZ)Ljava/util/List;"))
    private List<UUID> alpha_omega$detectAtImages(PlayerDetector detector, ServerLevel level, PlayerDetector.EntitySelector selector, BlockPos pos, double range,
        boolean lineOfSight, Operation<List<UUID>> original) {
        return PlayerBridge.detect(level, pos, range, at -> original.call(detector, level, selector, at, range, lineOfSight));
    }
}
