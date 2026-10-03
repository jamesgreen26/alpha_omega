package g_mungus.alpha_omega.mixin.worldgen.structure;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Structure references: a chunk references every start within 8 chunks whose bounds overlap it. Starts live in
 * canonical chunks, but their pieces may extend past the seam, so overlap is tested against the nearest image,
 * and the referenced start chunk is recorded canonically (R1).
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @ModifyExpressionValue(method = "createReferences",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;asLong(II)J"))
    private long alpha_omega$canonStartChunk(long chunkKey, @Local(argsOnly = true) WorldGenLevel level) {
        return Wrap.of(level).canonChunkKey(chunkKey);
    }

    @WrapOperation(method = "createReferences",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;intersects(IIII)Z"))
    private boolean alpha_omega$intersectsNearestImage(BoundingBox box, int minX, int minZ, int maxX, int maxZ, Operation<Boolean> original,
                                                       @Local(argsOnly = true) WorldGenLevel level) {
        Wrap wrap = Wrap.of(level);
        int dx = wrap.lapOffset(minX, box.getCenter().getX());
        int dz = wrap.lapOffset(minZ, box.getCenter().getZ());
        return original.call(box, minX + dx, minZ + dz, maxX + dx, maxZ + dz);
    }
}
