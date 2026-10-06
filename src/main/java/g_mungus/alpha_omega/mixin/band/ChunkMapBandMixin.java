package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandEvents;
import g_mungus.alpha_omega.band.BandGate;
import g_mungus.alpha_omega.band.BandTicketHolder;
import g_mungus.alpha_omega.band.BandTickets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The promotion gate ({@link BandGate}), paired ticket state, and the gate's "full while unfilled" detector. */
@Mixin(ChunkMap.class)
abstract class ChunkMapBandMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$pairedTickets(CallbackInfo ci) {
        BandTickets.State state = new BandTickets.State(this.level);
        net.minecraft.server.level.DistanceManager distances = ((ChunkMap) (Object) this).getDistanceManager();
        ((BandTicketHolder) distances).alpha_omega$setBandTickets(state);
        ((BandTicketHolder) ((DistanceManagerBandAccessor) distances).alpha_omega$tickingTicketsTracker()).alpha_omega$setBandTickets(state);
    }

    @Inject(method = "applyStep", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$gate(GenerationChunkHolder holder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        CompletableFuture<ChunkAccess> gated = BandGate.gate((ChunkMap) (Object) this, this.level, holder, step, cache);
        if (gated != null) cir.setReturnValue(gated);
    }

    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"))
    private void alpha_omega$fullWhileUnfilled(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        if (!status.isOrAfter(FullChunkStatus.FULL) || Band.geometry(this.level) == null) return;
        LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null && BandEvents.unfilled(chunk)) BandCounters.gateViolation("full unfilled");
    }
}
