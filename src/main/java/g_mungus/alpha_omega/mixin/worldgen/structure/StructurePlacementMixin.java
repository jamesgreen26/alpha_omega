package g_mungus.alpha_omega.mixin.worldgen.structure;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Whether a chunk hosts a structure (frequency rolls, exclusion zones, placement) depends only on the canonical chunk. */
@Mixin(StructurePlacement.class)
abstract class StructurePlacementMixin {

    @ModifyVariable(method = "isStructureChunk", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$canonX(int chunkX) {
        return Wrap.canonChunk(chunkX);
    }

    @ModifyVariable(method = "isStructureChunk", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$canonZ(int chunkZ) {
        return Wrap.canonChunk(chunkZ);
    }
}
