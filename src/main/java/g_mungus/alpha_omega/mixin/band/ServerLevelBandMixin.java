package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandEvents;
import g_mungus.alpha_omega.band.BandReactions;
import g_mungus.alpha_omega.band.BandTicks;
import g_mungus.alpha_omega.band.BandWrites;
import g_mungus.alpha_omega.band.CopyLinks;
import g_mungus.alpha_omega.band.Ownership;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelTicks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Band rules on {@code ServerLevel}: random ticks and precipitation only at owner copies (RS §3.5); block update and
 * block event packets for every copy; POIs registered at the owner; capability caches at copies invalidated with the
 * owner's; and the gate's "ticked while unfilled" detector.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelBandMixin {

    @Shadow
    @Final
    private LevelTicks<Block> blockTicks;
    @Shadow
    @Final
    private LevelTicks<Fluid> fluidTicks;

    @Unique
    private boolean alpha_omega$invalidatingCopies;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$ticksKnowTheirLevel(CallbackInfo ci) {
        ((BandTicks) this.blockTicks).alpha_omega$setLevel((ServerLevel) (Object) this);
        ((BandTicks) this.fluidTicks).alpha_omega$setLevel((ServerLevel) (Object) this);
    }

    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void alpha_omega$tickedUnfilled(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (BandEvents.unfilled(chunk)) BandCounters.gateViolation("ticked unfilled");
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean alpha_omega$ownedRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, @Local(argsOnly = true) LevelChunk chunk) {
        if (Ownership.isOwner(chunk, pos)) return true;
        BandCounters.randomTicksSkipped++;
        return false;
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean alpha_omega$ownedFluidRandomTick(FluidState state, Level level, BlockPos pos, RandomSource random, @Local(argsOnly = true) LevelChunk chunk) {
        if (Ownership.isOwner(chunk, pos)) return true;
        BandCounters.randomTicksSkipped++;
        return false;
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tickPrecipitation(Lnet/minecraft/core/BlockPos;)V"))
    private boolean alpha_omega$ownedPrecipitation(ServerLevel level, BlockPos pos, @Local(argsOnly = true) LevelChunk chunk) {
        if (Ownership.isOwner(chunk, pos)) return true;
        BandCounters.precipitationSkipped++;
        return false;
    }

    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void alpha_omega$sendCopies(BlockPos pos, BlockState old, BlockState state, int flags, CallbackInfo ci) {
        BandWrites.sendCopies((ServerLevel) (Object) this, pos);
    }

    @Inject(method = "onBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$poiAtOwner(BlockPos pos, BlockState old, BlockState state, CallbackInfo ci) {
        if (BandReactions.poiChange((ServerLevel) (Object) this, pos, old, state)) ci.cancel();
    }

    @WrapOperation(method = "runBlockEvents", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"))
    private void alpha_omega$blockEventForCopies(PlayerList players, @Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension,
        Packet<?> packet, Operation<Void> original, @Local BlockEventData event) {
        original.call(players, except, x, y, z, radius, dimension, packet);
        BandWrites.blockEventCopies((ServerLevel) (Object) this, event.pos(), event.block(), event.paramA(), event.paramB());
    }

    @Inject(method = "invalidateCapabilities(Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void alpha_omega$invalidateCopies(BlockPos pos, CallbackInfo ci) {
        if (this.alpha_omega$invalidatingCopies) return;
        ServerLevel self = (ServerLevel) (Object) this;
        LevelChunk chunk = Band.linkedChunk(self, pos);
        if (chunk == null) return;
        this.alpha_omega$invalidatingCopies = true;
        try {
            for (CopyLinks.Link link : Band.links(chunk).links) {
                if (link.chunk(self) != null) self.invalidateCapabilities(link.map(pos));
            }
        } finally {
            this.alpha_omega$invalidatingCopies = false;
        }
    }
}
