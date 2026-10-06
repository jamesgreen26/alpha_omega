package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.PlaceBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Structure lookups at a band position look at its source ({@link PlaceBridge#forStructures}). The tag and holder set
 * forms of {@code getStructureWithPieceAt} go through the predicate form.
 */
@Mixin(StructureManager.class)
abstract class StructureManagerBridgeMixin {

    @Shadow
    @Final
    private LevelAccessor level;

    @ModifyVariable(method = {
        "getStructureAt",
        "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
        "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
        "structureHasPieceAt",
        "hasAnyStructureAt",
        "getAllStructuresAt"
    }, at = @At("HEAD"), argsOnly = true, require = 6)
    private BlockPos alpha_omega$atSource(BlockPos pos) {
        return PlaceBridge.forStructures(this.level, pos);
    }
}
