package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R5: entity tracking range uses the nearest-image displacement between player and entity. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class ChunkMapTrackedEntityMixin {

    @ModifyExpressionValue(method = "updatePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 alpha_omega$wrapDisplacement(Vec3 d) {
        return new Vec3(Wrap.minDelta(d.x, 0), d.y, Wrap.minDelta(d.z, 0));
    }
}
