package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No structure crosses a seam: in an orbifold world a start is kept only if its whole box is inside the tile, clear of every seam. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"))
    private boolean alpha_omega$fitsInTile(StructureStart start, Operation<Boolean> original) {
        if (!original.call(start)) return false;
        return !((Object) this instanceof OrbifoldChunkGenerator orbifold) || orbifold.keepStructure(start.getBoundingBox());
    }
}
