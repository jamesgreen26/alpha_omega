package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.island.IslandManager;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin implements IslandManager.Holder {

    @Unique
    private IslandManager alpha_omega$islands;

    @Override
    public IslandManager alpha_omega$islands() {
        if (this.alpha_omega$islands == null) this.alpha_omega$islands = new IslandManager((ServerLevel) (Object) this);
        return this.alpha_omega$islands;
    }

    /** End of the level tick: island splits and entity frame checks (shifts run when no entity is mid-update). */
    @Inject(method = "tick", at = @At("TAIL"))
    private void alpha_omega$tickIslands(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        this.alpha_omega$islands().tick();
    }

    /** Respawn positions are block-side state: stored canonically, lifted on use. */
    @ModifyVariable(method = "setDefaultSpawnPos", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonSpawn(BlockPos pos) {
        return Wrap.of((ServerLevel) (Object) this).canon(pos);
    }

    // ---- R4: execution entry points ----

    /** Random ticks, precipitation and lightning all derive their positions from the chunk's corner. */
    @ModifyExpressionValue(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;getMinBlockX()I"))
    private int alpha_omega$liftChunkX(int minX, @Local(argsOnly = true) LevelChunk chunk) {
        return minX + Frames.offsetX(Frames.lapOffset((ServerLevel) (Object) this, chunk.getPos().x, chunk.getPos().z));
    }

    @ModifyExpressionValue(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;getMinBlockZ()I"))
    private int alpha_omega$liftChunkZ(int minZ, @Local(argsOnly = true) LevelChunk chunk) {
        return minZ + Frames.offsetZ(Frames.lapOffset((ServerLevel) (Object) this, chunk.getPos().x, chunk.getPos().z));
    }

    /** Scheduled block and fluid ticks are stored canonically (R1) and run lifted. */
    @ModifyVariable(method = {"tickBlock", "tickFluid"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$liftScheduledTick(BlockPos pos) {
        return Frames.lift((ServerLevel) (Object) this, pos);
    }

    /** Block events (pistons, note blocks, chests) are queued canonically (R1), so images of one event merge... */
    @ModifyVariable(method = "blockEvent", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonBlockEvent(BlockPos pos) {
        return Wrap.of((ServerLevel) (Object) this).canon(pos);
    }

    /** ...and run lifted. */
    @ModifyVariable(method = "doBlockEvent", at = @At("HEAD"), argsOnly = true)
    private BlockEventData alpha_omega$liftBlockEvent(BlockEventData event) {
        BlockPos lifted = Frames.lift((ServerLevel) (Object) this, event.pos());
        return lifted == event.pos() ? event : new BlockEventData(lifted, event.block(), event.paramA(), event.paramB());
    }

    // ---- R5: player proximity ----

    @WrapOperation(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean alpha_omega$particlesNearestImage(BlockPos player, Position pos, double distance, Operation<Boolean> original) {
        return original.call(player, Wrap.of((ServerLevel) (Object) this).nearest(new Vec3(pos.x(), pos.y(), pos.z()), Vec3.atCenterOf(player)), distance);
    }

    /** Explosion packets go to players near any image of the center (the center argument is often canonical). */
    @WrapOperation(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"))
    private double alpha_omega$explosionNearestImage(ServerPlayer player, double x, double y, double z, Operation<Double> original) {
        Wrap wrap = Wrap.of((ServerLevel) (Object) this);
        return original.call(player, wrap.nearest(x, player.getX()), y, wrap.nearest(z, player.getZ()));
    }

    @WrapOperation(method = "destroyBlockProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double alpha_omega$destroyProgressNearestX(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true) BlockPos pos) {
        return Wrap.of((ServerLevel) (Object) this).nearest(original.call(player), pos.getX());
    }

    @WrapOperation(method = "destroyBlockProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double alpha_omega$destroyProgressNearestZ(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true) BlockPos pos) {
        return Wrap.of((ServerLevel) (Object) this).nearest(original.call(player), pos.getZ());
    }

    /**
     * Overrides {@code EntityGetter}'s default with nearest-image distance. Covers despawn checks, spawners, natural
     * spawning and most "is a player near" logic. The other {@code getNearestPlayer} overloads delegate here.
     */
    @Nullable
    public Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        double best = -1.0;
        Player nearest = null;
        for (Player player : ((ServerLevel) (Object) this).players()) {
            if (predicate == null || predicate.test(player)) {
                double d = alpha_omega$distanceSqr(player, x, y, z);
                if ((distance < 0.0 || d < distance * distance) && (best == -1.0 || d < best)) {
                    best = d;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    public boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        for (Player player : ((ServerLevel) (Object) this).players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d = alpha_omega$distanceSqr(player, x, y, z);
                if (distance < 0.0 || d < distance * distance) return true;
            }
        }
        return false;
    }

    @Unique
    private static double alpha_omega$distanceSqr(Entity entity, double x, double y, double z) {
        Wrap wrap = Wrap.of(entity.level());
        double dx = wrap.minDelta(entity.getX(), x);
        double dy = entity.getY() - y;
        double dz = wrap.minDelta(entity.getZ(), z);
        return dx * dx + dy * dy + dz * dz;
    }
}
