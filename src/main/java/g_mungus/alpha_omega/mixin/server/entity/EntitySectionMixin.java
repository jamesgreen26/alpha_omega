package g_mungus.alpha_omega.mixin.server.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * R5 safety net for entity AABB queries: an entity matches if any image of it overlaps the query. Until islands
 * put everything near a player in one frame, block-side code (a lifted hopper, a canonical spawner) and the
 * entities it looks for can be a whole number of laps apart. Within one frame this is exactly vanilla.
 */
@Mixin(EntitySection.class)
abstract class EntitySectionMixin {

    @WrapOperation(method = {
        "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
    },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean alpha_omega$intersectsAnyImage(AABB entity, AABB query, Operation<Boolean> original) {
        if (original.call(entity, query)) return true;
        // Only boxes more than half a world apart can overlap through another image.
        double dx = alpha_omega$lapShift(entity.minX - query.minX);
        double dz = alpha_omega$lapShift(entity.minZ - query.minZ);
        return (dx != 0 || dz != 0) && original.call(entity.move(dx, 0, dz), query);
    }

    @Unique
    private static double alpha_omega$lapShift(double delta) {
        return Math.abs(delta) > Wrap.PERIOD / 2.0 ? -Math.round(delta / Wrap.PERIOD) * (double) Wrap.PERIOD : 0;
    }
}
