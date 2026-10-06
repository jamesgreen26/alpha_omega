package g_mungus.alpha_omega.mixin.bridges;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.bridge.EntityBridge;
import g_mungus.alpha_omega.bridge.PlayerBridge;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges on {@code ServerLevel}: its entity storage learns its level (for the box query bridge), player proximity is
 * measured to the nearest image ({@link PlayerBridge}), and particles reach players near an image of where they start.
 *
 * <p>{@code getNearestPlayer} and {@code hasNearbyAlivePlayer} are {@code EntityGetter} default methods that no class
 * overrides; they are overridden here, for server levels only, with the same logic plus images.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelBridgeMixin implements EntityGetter {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$storageKnowsItsLevel(CallbackInfo ci) {
        var storage = ((PersistentEntitySectionManagerAccessor) ((ServerLevelEntitiesAccessor) this).alpha_omega$entityManager()).alpha_omega$sectionStorage();
        ((EntityBridge.Storage) storage).alpha_omega$setLevel((ServerLevel) (Object) this);
    }

    @Override
    @Nullable
    public Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        return PlayerBridge.nearest((ServerLevel) (Object) this, this.players(), x, y, z, distance, predicate);
    }

    @Override
    public boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        return PlayerBridge.anyNear((ServerLevel) (Object) this, this.players(), x, y, z, distance);
    }

    @WrapOperation(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean alpha_omega$particlesNearImages(BlockPos player, Position at, double radius, Operation<Boolean> original) {
        return original.call(player, at, radius) || PlayerBridge.particleNearImage((ServerLevel) (Object) this, player, at, radius);
    }
}
