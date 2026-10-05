package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Entities are tracked by distance from the nearest of a player's real and image positions. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class TrackedEntityMixin {

    @Shadow
    @Final
    Entity entity;

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 alpha_omega$virtualPosition(ServerPlayer player, Operation<Vec3> original) {
        Vec3 pos = original.call(player);
        if (!(this.entity.level() instanceof ServerLevel level)) return pos;
        return NeighbourViews.playerPositionFor(level, pos, this.entity.position());
    }
}
