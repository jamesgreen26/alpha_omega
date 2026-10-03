package g_mungus.alpha_omega.mixin.worldgen.structure;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Concentric rings (strongholds) are laid out at absolute distances from the origin, with outer rings far beyond
 * the size of a wrapped world. Keep only the innermost ring, at canonical positions.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {

    @ModifyReturnValue(method = "generateRingPositions", at = @At("RETURN"))
    private CompletableFuture<List<ChunkPos>> alpha_omega$innerRingCanonical(CompletableFuture<List<ChunkPos>> positions,
                                                                           @Local(argsOnly = true) ConcentricRingsStructurePlacement placement) {
        return positions.thenApply(list -> list.stream()
            .limit(Math.max(1, placement.spread()))
            .map(Wrap::canon)
            .distinct()
            .toList());
    }
}
