package g_mungus.alpha_omega.mixin.server.entity;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R2 for entity sections: on the server, sections are keyed canonically, so an AABB query (in whatever frame)
 * must visit the canonical sections it covers. Entities themselves keep their lifted positions.
 */
@Mixin(EntitySectionStorage.class)
abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Shadow
    @Final
    private LongSortedSet sectionIds;

    /** Sections carry the storage's period for the entity query bridge. */
    @Inject(method = "createSection", at = @At("RETURN"))
    private void alpha_omega$stampSection(long sectionKey, CallbackInfoReturnable<EntitySection<T>> cir) {
        WrapHolder.set(cir.getReturnValue(), WrapHolder.of(this));
    }

    @Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$forEachWrapped(AABB box, AbortableIterationConsumer<EntitySection<T>> consumer, CallbackInfo ci) {
        Wrap wrap = WrapHolder.of(this);
        // Off-torus sections (other mods' far-away storage) are keyed as they are; vanilla iteration finds them.
        if (!wrap.enabled() || Wrap.offTorus(box.minX) || Wrap.offTorus(box.minZ)) return;
        ci.cancel();

        // Same margins as vanilla.
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0);
        int minY = SectionPos.posToSectionCoord(box.minY - 4.0);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0);
        int maxY = SectionPos.posToSectionCoord(box.maxY + 0.0);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0);
        int n = wrap.chunkPeriod;
        int spanX = Math.min(maxX - minX, n - 1);
        int spanZ = Math.min(maxZ - minZ, n - 1);

        for (int dx = 0; dx <= spanX; dx++) {
            int x = wrap.canonChunk(minX + dx);
            LongIterator it = this.sectionIds.subSet(SectionPos.asLong(x, 0, 0), SectionPos.asLong(x, -1, -1) + 1L).iterator();

            while (it.hasNext()) {
                long key = it.nextLong();
                int y = SectionPos.y(key);
                int z = SectionPos.z(key);
                if (y >= minY && y <= maxY && Math.floorMod(z - minZ, n) <= spanZ) {
                    EntitySection<T> section = this.sections.get(key);
                    if (section != null
                        && !section.isEmpty()
                        && section.getStatus().isAccessible()
                        && consumer.accept(section).shouldAbort()) {
                        return;
                    }
                }
            }
        }
    }
}
