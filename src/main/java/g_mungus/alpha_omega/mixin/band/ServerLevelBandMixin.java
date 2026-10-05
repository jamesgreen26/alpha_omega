package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Random ticks and precipitation run only at owner copies (RS §3.5); the tick containers learn their level. */
@Mixin(ServerLevel.class)
abstract class ServerLevelBandMixin {

    @Shadow
    @Final
    private LevelTicks<Block> blockTicks;
    @Shadow
    @Final
    private LevelTicks<Fluid> fluidTicks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$ticksKnowTheirLevel(CallbackInfo ci) {
        ((BandTicks) this.blockTicks).alpha_omega$setLevel((ServerLevel) (Object) this);
        ((BandTicks) this.fluidTicks).alpha_omega$setLevel((ServerLevel) (Object) this);
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean alpha_omega$ownedRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Band.owner(level, pos) == null) return true;
        BandCounters.randomTicksSkipped++;
        return false;
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private boolean alpha_omega$ownedFluidRandomTick(FluidState state, Level level, BlockPos pos, RandomSource random) {
        if (Band.owner(level, pos) == null) return true;
        BandCounters.randomTicksSkipped++;
        return false;
    }

    @WrapWithCondition(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tickPrecipitation(Lnet/minecraft/core/BlockPos;)V"))
    private boolean alpha_omega$ownedPrecipitation(ServerLevel level, BlockPos pos) {
        if (Band.owner(level, pos) == null) return true;
        BandCounters.precipitationSkipped++;
        return false;
    }
}
