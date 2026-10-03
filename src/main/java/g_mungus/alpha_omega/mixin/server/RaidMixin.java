package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A raid's center is level-scoped block-side state (§10.3): stored (and saved) canonically, read lifted into the
 * frame of the raid's island so raiders and villagers there agree on it, whatever shifts happen meanwhile.
 */
@Mixin(Raid.class)
abstract class RaidMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private BlockPos center;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void alpha_omega$canonCenter(CallbackInfo ci) {
        this.center = Wrap.of(this.level).canon(this.center);
    }

    @ModifyVariable(method = "setCenter", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonNewCenter(BlockPos pos) {
        return Wrap.of(this.level).canon(pos);
    }

    @ModifyExpressionValue(method = {"tick", "getCenter", "moveRaidCenterToNearbyVillageSection", "updateRaiders", "findRandomSpawnPos"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/raid/Raid;center:Lnet/minecraft/core/BlockPos;"))
    private BlockPos alpha_omega$liftedCenter(BlockPos center) {
        return Frames.lift(this.level, center);
    }
}
