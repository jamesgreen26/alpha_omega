package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Entity box queries (RS §5): {@code EntitySectionStorage.getEntities(AABB, …)}, which every {@code Level.getEntities*}
 * goes through. A box near an edge also looks in each of its images, so an entity stored in another frame but present
 * in the box is found: pressure plates, hoppers, tripwires, detector rails and chest opener counts see it. Each entity
 * is reported once, at its own (stored) position.
 *
 * <p>Away from the edges this is a field read and four comparisons per query.
 */
public final class EntityBridge {

    private EntityBridge() {
    }

    /** A storage's level, set by the server level that owns it. */
    public interface Storage {

        void alpha_omega$setLevel(ServerLevel level);
    }

    /** The images to search for a query box, or null to run the query as vanilla does. */
    @Nullable
    public static List<Motion> images(@Nullable ServerLevel level, AABB box) {
        if (level == null) return null;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || !Images.near(geometry, box.minX, box.minZ, box.maxX, box.maxZ)) return null;
        List<Motion> images = Images.of(geometry, box.minX, box.minZ, box.maxX, box.maxZ);
        if (images.isEmpty()) return null;
        BridgeCounters.run(BridgeCounters.Kind.ENTITIES, box.minX, box.minZ, box.maxX, box.maxZ);
        return images;
    }

    /**
     * Passes entities on to a query's consumer at most once each, remembering whether the consumer asked to stop. The
     * box's own sections go through {@link #direct}, its images' through {@link #image}.
     */
    public static final class Once<T> {

        private final AbortableIterationConsumer<T> consumer;
        private final Set<T> seen = new ReferenceOpenHashSet<>();
        private boolean aborted;
        private long found;

        public Once(AbortableIterationConsumer<T> consumer) {
            this.consumer = consumer;
        }

        public AbortableIterationConsumer.Continuation direct(T entity) {
            this.seen.add(entity);
            return this.pass(entity);
        }

        public AbortableIterationConsumer.Continuation image(T entity) {
            if (!this.seen.add(entity)) return AbortableIterationConsumer.Continuation.CONTINUE;
            this.found++;
            return this.pass(entity);
        }

        private AbortableIterationConsumer.Continuation pass(T entity) {
            AbortableIterationConsumer.Continuation next = this.consumer.accept(entity);
            if (next.shouldAbort()) this.aborted = true;
            return next;
        }

        public boolean aborted() {
            return this.aborted;
        }

        public void done() {
            BridgeCounters.found(BridgeCounters.Kind.ENTITIES, this.found);
        }
    }

    /**
     * Whether a push between two entities should be dropped: they were found through an image, so in storage they are
     * far apart and vanilla's push (which normalises their storage offset) would shove both along it. Entities that
     * touch in the same frame are never this far apart.
     */
    public static boolean pushAcrossFrames(Entity self, Entity other) {
        if (self.distanceToSqr(other) < 32.0 * 32.0 || !(self.level() instanceof ServerLevel level) || Band.geometry(level) == null) return false;
        BridgeCounters.run(BridgeCounters.Kind.PUSHES, self.getX(), self.getZ(), 0.0);
        return true;
    }
}
