package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandReplicas;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk packets carry the owners' block entities for cells owned at another copy ({@link BandReplicas}). */
@Mixin(ClientboundLevelChunkPacketData.class)
abstract class ChunkPacketDataBandMixin {

    @Shadow
    @Final
    @SuppressWarnings("rawtypes")
    private List blockEntitiesData;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
    private void alpha_omega$replicas(LevelChunk chunk, CallbackInfo ci) {
        if (!chunk.getLevel().isClientSide && !Band.links(chunk).isEmpty()) BandReplicas.addReplicas(chunk, this.blockEntitiesData);
    }
}
