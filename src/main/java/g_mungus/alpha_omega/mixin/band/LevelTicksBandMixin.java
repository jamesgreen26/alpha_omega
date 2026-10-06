package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandTicks;
import g_mungus.alpha_omega.band.CopyLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scheduled ticks run where they were scheduled (RS §3.5); scheduling is skipped if a copy of the cell already has the
 * same block or fluid tick pending.
 */
@Mixin(LevelTicks.class)
abstract class LevelTicksBandMixin<T> implements BandTicks {

    @Unique
    @Nullable
    private ServerLevel alpha_omega$level;

    @Shadow
    public abstract boolean hasScheduledTick(BlockPos pos, T type);

    @Override
    public void alpha_omega$setLevel(ServerLevel level) {
        this.alpha_omega$level = level;
    }

    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$once(ScheduledTick<T> tick, CallbackInfo ci) {
        if (this.alpha_omega$level == null) return;
        LevelChunk chunk = Band.linkedChunk(this.alpha_omega$level, tick.pos());
        if (chunk == null) return;
        for (CopyLinks.Link link : Band.links(chunk).links) {
            if (link.chunk(this.alpha_omega$level) == null) continue;
            if (this.hasScheduledTick(link.scratch(tick.pos()), tick.type())) {
                BandCounters.scheduledTicksDeduped++;
                ci.cancel();
                return;
            }
        }
    }
}
