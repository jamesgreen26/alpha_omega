package g_mungus.alpha_omega.mixin.worldgen;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's noise fill over a range of cells, so a cube world's chunk can fill from its noise floor up. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {

    @Invoker("doFill")
    ChunkAccess alpha_omega$doFill(Blender blender, StructureManager structures, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY);
}
