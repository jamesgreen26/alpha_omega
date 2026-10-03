package g_mungus.alpha_omega.mixin.worldgen.structure;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Placing a structure into a chunk: move the chunk's box into the structure's frame (the image nearest the
 * structure), so pieces that cross the seam intersect it. Writes then go through the periodic worldgen region.
 */
@Mixin(StructureStart.class)
abstract class StructureStartMixin {

    @Shadow
    public abstract BoundingBox getBoundingBox();

    @Shadow
    public abstract boolean isValid();

    @ModifyVariable(method = "placeInChunk", at = @At("HEAD"), argsOnly = true)
    private BoundingBox alpha_omega$intoStructureFrame(BoundingBox chunkBox, @Local(argsOnly = true) WorldGenLevel level) {
        if (!this.isValid()) return chunkBox;
        Wrap wrap = Wrap.of(level);
        int dx = wrap.lapOffset(chunkBox.getCenter().getX(), this.getBoundingBox().getCenter().getX());
        int dz = wrap.lapOffset(chunkBox.getCenter().getZ(), this.getBoundingBox().getCenter().getZ());
        return dx == 0 && dz == 0 ? chunkBox : chunkBox.moved(dx, 0, dz);
    }

    @ModifyVariable(method = "placeInChunk", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$intoStructureFrame(ChunkPos chunkPos, @Local(argsOnly = true) WorldGenLevel level) {
        if (!this.isValid()) return chunkPos;
        Wrap wrap = Wrap.of(level);
        int x = wrap.nearestChunk(chunkPos.x, this.getBoundingBox().getCenter().getX() >> 4);
        int z = wrap.nearestChunk(chunkPos.z, this.getBoundingBox().getCenter().getZ() >> 4);
        return x == chunkPos.x && z == chunkPos.z ? chunkPos : new ChunkPos(x, z);
    }
}
