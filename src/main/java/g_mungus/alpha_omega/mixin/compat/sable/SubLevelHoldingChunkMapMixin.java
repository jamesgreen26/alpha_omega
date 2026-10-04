package g_mungus.alpha_omega.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Unloaded sub-levels are held in the world chunk under them, keyed canonically (R2) like the chunks whose loading
 * drives them; a sub-level in any lap unloads with the chunk under it and comes back when it reloads.
 */
@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
abstract class SubLevelHoldingChunkMapMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(method = "moveToUnloaded", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonicalHolding(ChunkPos pos) {
        return Wrap.of(this.level).canon(pos);
    }

    @ModifyExpressionValue(method = "saveAll", at = @At(value = "NEW", target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos alpha_omega$canonicalSaveChunk(ChunkPos pos) {
        return Wrap.of(this.level).canon(pos);
    }

    /** An unloading (canonical) chunk takes with it the sub-levels over any image of it. */
    @WrapOperation(method = "processUnload", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;queryIntersecting(Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"))
    private Iterable<SubLevel> alpha_omega$intersectingAnyImage(SubLevelContainer container, BoundingBox3dc chunk, Operation<Iterable<SubLevel>> original) {
        Wrap wrap = Wrap.of(this.level);
        if (!wrap.enabled()) return original.call(container, chunk);
        List<SubLevel> intersecting = new ArrayList<>();
        for (SubLevel subLevel : container.getAllSubLevels()) {
            BoundingBox3dc bounds = subLevel.boundingBox();
            double dx = wrap.nearest(chunk.minX(), bounds.minX()) - chunk.minX();
            double dz = wrap.nearest(chunk.minZ(), bounds.minZ()) - chunk.minZ();
            BoundingBox3d image = new BoundingBox3d(chunk.minX() + dx, chunk.minY(), chunk.minZ() + dz, chunk.maxX() + dx, chunk.maxY(), chunk.maxZ() + dz);
            if (bounds.intersects(image)) intersecting.add(subLevel);
        }
        return intersecting;
    }
}
