package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Crossing a seam, shared by server and client. Something transfers when its position lies far enough past a seam; it
 * moves by the element {@code g} of {@code Γ} that takes it back toward the tile, within the same storage. An entity
 * that has just crossed waits {@link #COOLDOWN_TICKS} before it may cross again, so walking along a seam does not
 * flip-flop.
 *
 * <p>Inactive until phase 5 ({@code orbifold-implementation.md}), which decides where things transfer
 * ({@link #destination}) and renames this {@code FrameTransfer}. The cooldown and carriers carry over from v2.
 */
public final class FaceTransfer {

    private FaceTransfer() {
    }

    /** Tests for what carries an entity across seams itself, so the entity does not cross on its own. */
    private static final List<Predicate<Entity>> CARRIERS = new CopyOnWriteArrayList<>();

    /** Registers a test for entities carried by something that crosses seams itself (a Sable sub-level they stand on). */
    public static void registerCarrier(Predicate<Entity> carried) {
        CARRIERS.add(carried);
    }

    /**
     * Whether an entity is carried by something that crosses seams itself: it crosses with that, at the same moment,
     * rather than on its own.
     */
    public static boolean carried(Entity entity) {
        for (Predicate<Entity> carrier : CARRIERS) {
            if (carrier.test(entity)) return true;
        }
        return false;
    }

    /**
     * The element a position should transfer by, or null if it stays. Phase 5 fills this in (players at {@code C + ½}
     * past a seam, others at {@code H}); until then nothing transfers.
     */
    @Nullable
    public static Motion destination(OrbifoldGeometry geometry, double x, double y, double z) {
        return null;
    }

    /** Ticks after crossing before the same entity may cross again. */
    public static final int COOLDOWN_TICKS = 10;

    /** Whether an entity crossed too recently to cross again. */
    public static boolean coolingDown(Entity entity) {
        return entity.tickCount - ((TransferCooldown) entity).alpha_omega$lastTransferTick() < COOLDOWN_TICKS;
    }

    /** Notes that an entity has just crossed. */
    public static void startCooldown(Entity entity) {
        ((TransferCooldown) entity).alpha_omega$setLastTransferTick(entity.tickCount);
    }

    /** Whether an entity moved by {@code g} would land somewhere it fits. */
    public static boolean roomToCross(Entity entity, Motion g) {
        Vec3 p = Transform.of(g).position(entity.position());
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(p.x, p.y, p.z);
        return entity.level().noCollision(entity, box);
    }

    /** A yaw and pitch moved by {@code g}, as {@code {yRot, xRot}}: a turn about Y leaves the pitch alone. */
    public static float[] rotateLook(Motion g, float yRot, float xRot) {
        return new float[] {g.yaw(yRot), xRot};
    }
}
