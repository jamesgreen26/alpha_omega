package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No structure crosses an edge (design §4.5): a start is kept only if it fits wholly inside one face. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"))
    private boolean alpha_omega$fitsOnFace(StructureStart start, Operation<Boolean> original, @Local(argsOnly = true) ChunkAccess chunk) {
        if (!original.call(start)) return false;
        return !((Object) this instanceof CubeChunkGenerator cube) || cube.keepStructure(start.getBoundingBox(), chunk);
    }
}
