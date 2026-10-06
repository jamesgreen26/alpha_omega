package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.EntityBridge;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity box queries near an edge also search the box's images ({@link EntityBridge}). Server levels only. */
@Mixin(EntitySectionStorage.class)
abstract class EntitySectionStorageBridgeMixin<T extends EntityAccess> implements EntityBridge.Storage {

    @Unique
    @Nullable
    private ServerLevel alpha_omega$level;

    @Shadow
    public abstract void forEachAccessibleNonEmptySection(AABB box, AbortableIterationConsumer<EntitySection<T>> consumer);

    @Override
    public void alpha_omega$setLevel(ServerLevel level) {
        this.alpha_omega$level = level;
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$withImages(AABB box, AbortableIterationConsumer<T> consumer, CallbackInfo ci) {
        List<Motion> images = EntityBridge.images(this.alpha_omega$level, box);
        if (images == null) return;
        ci.cancel();
        EntityBridge.Once<T> once = new EntityBridge.Once<>(consumer);
        this.forEachAccessibleNonEmptySection(box, section -> section.getEntities(box, once::direct));
        for (Motion g : images) {
            if (once.aborted()) break;
            AABB image = Transform.of(g).box(box);
            this.forEachAccessibleNonEmptySection(image, section -> section.getEntities(image, once::image));
        }
        once.done();
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
        at = @At("HEAD"), cancellable = true)
    private <U extends T> void alpha_omega$withImagesTyped(EntityTypeTest<T, U> test, AABB box, AbortableIterationConsumer<U> consumer, CallbackInfo ci) {
        List<Motion> images = EntityBridge.images(this.alpha_omega$level, box);
        if (images == null) return;
        ci.cancel();
        EntityBridge.Once<U> once = new EntityBridge.Once<>(consumer);
        this.forEachAccessibleNonEmptySection(box, section -> section.getEntities(test, box, once::direct));
        for (Motion g : images) {
            if (once.aborted()) break;
            AABB image = Transform.of(g).box(box);
            this.forEachAccessibleNonEmptySection(image, section -> section.getEntities(test, image, once::image));
        }
        once.done();
    }
}
