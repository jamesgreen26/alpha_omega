package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.cube.Cube;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In a cube world the client keeps every chunk the server sends in a map (design §6.2), not vanilla's ring around the
 * player: chunks of the neighbouring faces lie thousands of blocks away in storage, and crossing an edge jumps the
 * player there. Chunks still go only when the server forgets them.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {

    @Shadow
    @Final
    ClientLevel level;

    @Unique
    private final Map<Long, LevelChunk> alpha_omega$chunks = new ConcurrentHashMap<>();

    @Unique
    private boolean alpha_omega$cube() {
        return Cube.of(this.level) != null;
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    private void alpha_omega$getChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
        if (!this.alpha_omega$cube()) return;
        LevelChunk chunk = this.alpha_omega$chunks.get(ChunkPos.asLong(x, z));
        if (chunk != null) cir.setReturnValue(chunk);
        else if (!load) cir.setReturnValue(null);
        // Otherwise vanilla's ring has nothing either, and returns its empty chunk.
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replace(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
                                     Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities, CallbackInfoReturnable<LevelChunk> cir) {
        if (!this.alpha_omega$cube()) return;
        ChunkPos pos = new ChunkPos(x, z);
        LevelChunk chunk = this.alpha_omega$chunks.get(pos.toLong());
        if (chunk == null) {
            chunk = new LevelChunk(this.level, pos);
            chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
            this.alpha_omega$chunks.put(pos.toLong(), chunk);
        } else {
            chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
        }
        this.level.onChunkLoaded(pos);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        cir.setReturnValue(chunk);
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$drop(ChunkPos pos, CallbackInfo ci) {
        if (!this.alpha_omega$cube()) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$chunks.remove(pos.toLong());
        if (chunk != null) {
            NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
            this.level.unload(chunk);
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replaceBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!this.alpha_omega$cube()) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$chunks.get(ChunkPos.asLong(x, z));
        if (chunk != null) chunk.replaceBiomes(buffer);
    }

    @Inject(method = "getLoadedChunksCount", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$count(CallbackInfoReturnable<Integer> cir) {
        if (this.alpha_omega$cube()) cir.setReturnValue(this.alpha_omega$chunks.size());
    }
}
