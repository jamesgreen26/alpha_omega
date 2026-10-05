package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.FaceChunks;
import g_mungus.alpha_omega.client.ImageRenderer;
import g_mungus.alpha_omega.client.TransferStats;
import g_mungus.alpha_omega.compat.sodium.Sodium;
import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
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
 * In an orbifold world the client can keep every chunk the server sends in {@link FaceChunks}, not vanilla's ring
 * around the player: image views (phase 6) draw chunks far from the camera in storage. Chunks still go only when the
 * server forgets them. Only chunks of the footprint are kept here: others (Sable's plots, far out in the same level)
 * are left to vanilla and whichever mod serves them. Inactive until {@link FaceChunks#forGeometry} gives a store.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {

    @Shadow
    @Final
    ClientLevel level;

    /** Made on the first chunk of an orbifold world; read from worker threads too. */
    @Unique
    @Nullable
    private volatile FaceChunks alpha_omega$chunks;
    @Unique
    @Nullable
    private volatile OrbifoldGeometry alpha_omega$chunksFor;

    /** The chunk store for this world's footprint, made (on the main thread) when first needed; null when there is none. */
    @Unique
    @Nullable
    private FaceChunks alpha_omega$store() {
        OrbifoldGeometry geometry = Orbifold.of(this.level);
        if (geometry == null) return null;
        if (this.alpha_omega$chunksFor != geometry) {
            this.alpha_omega$chunks = FaceChunks.forGeometry(geometry);
            this.alpha_omega$chunksFor = geometry;
        }
        return this.alpha_omega$chunks;
    }

    /** Whether a chunk is one of the footprint's, in an orbifold world: one this store keeps. */
    @Unique
    private boolean alpha_omega$footprintChunk(int x, int z) {
        FaceChunks chunks = this.alpha_omega$store();
        return chunks != null && chunks.contains(x, z);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    private void alpha_omega$getChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
        // Worker threads ask too: read the store as it is, without making one.
        FaceChunks chunks = this.alpha_omega$chunks;
        if (chunks == null || !chunks.contains(x, z) || Orbifold.of(this.level) != this.alpha_omega$chunksFor) return;
        LevelChunk chunk = chunks.get(x, z);
        if (chunk != null) cir.setReturnValue(chunk);
        else if (!load) cir.setReturnValue(null);
        // Otherwise vanilla's ring has nothing either, and returns its empty chunk.
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replace(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
                                     Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities, CallbackInfoReturnable<LevelChunk> cir) {
        if (!this.alpha_omega$footprintChunk(x, z)) return;
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
        ImageRenderer.chunkChanged(this.level, x, z);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        cir.setReturnValue(chunk);
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$drop(ChunkPos pos, CallbackInfo ci) {
        if (!this.alpha_omega$footprintChunk(pos.x, pos.z)) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$store().remove(pos.x, pos.z);
        if (chunk != null) {
            TransferStats.chunkForgotten();
            if (Sodium.loaded()) SodiumNeighbours.chunkDropped(this.level, pos.x, pos.z);
            ImageRenderer.chunkChanged(this.level, pos.x, pos.z);
            NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
            this.level.unload(chunk);
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$replaceBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!this.alpha_omega$footprintChunk(x, z)) return;
        ci.cancel();
        LevelChunk chunk = this.alpha_omega$store().get(x, z);
        if (chunk != null) chunk.replaceBiomes(buffer);
    }

    @Inject(method = "getLoadedChunksCount", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$count(CallbackInfoReturnable<Integer> cir) {
        FaceChunks chunks = this.alpha_omega$store();
        if (chunks != null) cir.setReturnValue(chunks.size());
    }
}
