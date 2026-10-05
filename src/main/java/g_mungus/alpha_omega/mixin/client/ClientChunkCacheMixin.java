package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.FaceChunks;
import g_mungus.alpha_omega.client.NeighbourRenderer;
import g_mungus.alpha_omega.client.TransferStats;
import g_mungus.alpha_omega.compat.sodium.Sodium;
import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeGeometry;
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
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In a cube world the client keeps every chunk the server sends in {@link FaceChunks} (design §6.2), not vanilla's ring
 * around the player: chunks of the neighbouring faces lie thousands of blocks away in storage, and crossing an edge
 * jumps the player there. Chunks still go only when the server forgets them. Only chunks of a face's storage are
 * kept here: others (Sable's plots, far out in the same level) are left to vanilla and whichever mod serves them.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {

    @Shadow
    @Final
    ClientLevel level;

    /** Made on the first chunk of a cube world; read from worker threads too. */
    @Unique
    @Nullable
    private volatile FaceChunks alpha_omega$chunks;

    @Unique
    private boolean alpha_omega$cube() {
        return Cube.of(this.level) != null;
    }

    /** Whether a chunk is one of a face's storage, in a cube world: one this store keeps. */
    @Unique
    private boolean alpha_omega$faceChunk(int x, int z) {
        CubeGeometry geometry = Cube.of(this.level);
        return geometry != null && geometry.faceAtChunk(x, z) != null;
    }

    /** The chunk store for this world's cube, made (on the main thread) when first needed. */
    @Unique
    private FaceChunks alpha_omega$store() {
        CubeGeometry geometry = Cube.of(this.level);
        FaceChunks chunks = this.alpha_omega$chunks;
        if (chunks == null || chunks.geometry != geometry) this.alpha_omega$chunks = chunks = new FaceChunks(geometry);
        return chunks;
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    private void alpha_omega$getChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
        if (!this.alpha_omega$faceChunk(x, z)) return;
        FaceChunks chunks = this.alpha_omega$chunks;
        LevelChunk chunk = chunks == null ? null : chunks.get(x, z);
        if (chunk != null) cir.setReturnValue(chunk);
        else if (!load) cir.setReturnValue(null);
        // Otherwise vanilla's ring has nothing either, and returns its empty chunk.
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replace(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
                                     Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities, CallbackInfoReturnable<LevelChunk> cir) {
        if (!this.alpha_omega$faceChunk(x, z)) return;
        ChunkPos pos = new ChunkPos(x, z);
        FaceChunks chunks = this.alpha_omega$store();
        LevelChunk chunk = chunks.get(x, z);
        TransferStats.chunkReceived(chunk != null);
        if (chunk == null) {
            chunk = new LevelChunk(this.level, pos);
            chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
            chunks.put(x, z, chunk);
        } else {
            chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
        }
        this.level.onChunkLoaded(pos);
        if (Sodium.loaded()) SodiumNeighbours.chunkLoaded(this.level, x, z);
        NeighbourRenderer.chunkChanged(this.level, x, z);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        cir.setReturnValue(chunk);
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$drop(ChunkPos pos, CallbackInfo ci) {
        if (!this.alpha_omega$faceChunk(pos.x, pos.z)) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$store().remove(pos.x, pos.z);
        if (chunk != null) {
            TransferStats.chunkForgotten();
            if (Sodium.loaded()) SodiumNeighbours.chunkDropped(this.level, pos.x, pos.z);
            NeighbourRenderer.chunkChanged(this.level, pos.x, pos.z);
            NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
            this.level.unload(chunk);
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replaceBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!this.alpha_omega$faceChunk(x, z)) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$store().get(x, z);
        if (chunk != null) chunk.replaceBiomes(buffer);
    }

    @Inject(method = "getLoadedChunksCount", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$count(CallbackInfoReturnable<Integer> cir) {
        if (this.alpha_omega$cube()) cir.setReturnValue(this.alpha_omega$store().size());
    }
}
