package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Worldgen sees a small neighborhood around its center chunk. Resolve every position to the image nearest the
 * center, so features and structure pieces may address it through any image (e.g. a structure laid out past
 * {@code W} writing into chunk 0).
 */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {

    @Shadow
    @Final
    private ChunkAccess center;

    @ModifyVariable(method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", "hasChunk(II)Z"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$nearestX(int x) {
        return Wrap.nearestChunk(x, this.center.getPos().x);
    }

    @ModifyVariable(method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", "hasChunk(II)Z"},
        at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$nearestZ(int z) {
        return Wrap.nearestChunk(z, this.center.getPos().z);
    }

    @ModifyVariable(method = "ensureCanWrite", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$nearestPos(BlockPos pos) {
        ChunkPos center = this.center.getPos();
        int x = Wrap.nearestBlock(pos.getX(), center.getMiddleBlockX());
        int z = Wrap.nearestBlock(pos.getZ(), center.getMiddleBlockZ());
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }
}
