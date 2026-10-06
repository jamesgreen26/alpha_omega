package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Entities hold positions beyond their own: path targets, memories, homes, anchors. When an entity changes frame, every
 * translator registered for its type moves that state by the same element of {@code Γ} (RS §4.4; main's
 * {@code FrameTranslators}, from lap offsets to rigid motions). A position that is not valid after the move (further
 * past a seam than the band) is cleared; translators get it as null from {@link Move}. Other mods register translators
 * for their entities with {@link #register}.
 */
public final class FrameTranslators {

    /** Moves every position {@code entity} holds by {@code move}. */
    @FunctionalInterface
    public interface FrameTranslator<T extends Entity> {
        void translate(T entity, Move move);
    }

    /**
     * One change of frame: the element, and the positions it takes, each null if not valid afterwards (or null
     * before). Valid means within the band ({@code H}) past the seams.
     */
    public record Move(OrbifoldGeometry geometry, Motion motion) {

        public Transform transform() {
            return Transform.of(this.motion);
        }

        public boolean valid(double x, double z) {
            return Frames.valid(this.geometry, x, z, this.geometry.band);
        }

        @Nullable
        public BlockPos block(@Nullable BlockPos pos) {
            if (pos == null) return null;
            BlockPos moved = this.transform().block(pos);
            return this.valid(moved.getX() + 0.5, moved.getZ() + 0.5) ? moved : null;
        }

        @Nullable
        public Vec3 point(@Nullable Vec3 pos) {
            if (pos == null) return null;
            Vec3 moved = this.transform().position(pos);
            return this.valid(moved.x, moved.z) ? moved : null;
        }

        /** A direction: turned, never cleared. */
        public Vec3 vector(Vec3 vector) {
            return this.transform().vector(vector);
        }

        /**
         * A position held in a level: moved if it is in the moving entity's level. A point of interest (a home, a job
         * site) the move would take past the band is kept at its source in the tile instead of cleared: the POI record
         * lives at one copy, and dropping the memory would leave it claimed by nobody.
         */
        @Nullable
        public GlobalPos global(@Nullable GlobalPos pos, Entity entity, boolean pointOfInterest) {
            if (pos == null || pos.dimension() != entity.level().dimension()) return pos;
            BlockPos moved = this.block(pos.pos());
            if (moved != null) return GlobalPos.of(pos.dimension(), moved);
            if (!pointOfInterest) return null;
            OrbifoldGeometry.Cell source = this.geometry.canon(pos.pos().getX(), pos.pos().getZ());
            return GlobalPos.of(pos.dimension(), new BlockPos(source.x(), pos.pos().getY(), source.z()));
        }
    }

    private record Registration<T extends Entity>(Class<T> type, FrameTranslator<? super T> translator) {
        void apply(Entity entity, Move move) {
            if (this.type.isInstance(entity)) this.translator.translate(this.type.cast(entity), move);
        }
    }

    private static final List<Registration<?>> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private FrameTranslators() {
    }

    public static <T extends Entity> void register(Class<T> type, FrameTranslator<? super T> translator) {
        REGISTRATIONS.add(new Registration<>(type, translator));
    }

    /** Moves what {@code entity} holds by {@code move}, after the entity itself has moved. */
    public static void translate(Entity entity, Move move) {
        for (Registration<?> registration : REGISTRATIONS) registration.apply(entity, move);
    }
}
