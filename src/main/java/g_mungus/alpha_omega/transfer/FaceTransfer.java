package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Crossing an edge (design §5), shared by server and client. Something transfers when its position lies past the
 * diagonal into another face's region by more than {@link #MARGIN}; it lands at the same cube point in that face's
 * storage. Coming back needs the same margin the other way, and an entity that has just crossed waits
 * {@link #COOLDOWN_TICKS} before it may cross again, so walking along an edge does not flip-flop.
 */
public final class FaceTransfer {

    /** How far past the diagonal (across it, in blocks) a position must be before it transfers. */
    public static final double MARGIN = 0.5;

    private FaceTransfer() {
    }

    /** How velocity and orientation cross. */
    public enum Mode {
        /**
         * Turned with the unfold: walking off an edge carries on along the next face. The orientation of players, mobs
         * and vehicles, and the velocity of mobs and vehicles.
         */
        UPRIGHT,
        /** Kept in world space: momentum is real, only gravity changes direction. Players' velocity; items, projectiles, the rest. */
        WORLD
    }

    /** How an entity's orientation (look, hitbox) crosses. */
    public static Mode mode(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof VehicleEntity ? Mode.UPRIGHT : Mode.WORLD;
    }

    /**
     * How an entity's velocity crosses. Players keep their momentum in world space, so speed toward an edge carries
     * them up off the next face and its gravity brings them round, a brief orbit; they still land upright. Other
     * upright things turn their velocity with their orientation.
     */
    public static Mode velocityMode(Entity entity) {
        return entity instanceof Player ? Mode.WORLD : mode(entity);
    }

    /** Tests for what carries an entity across edges itself, so the entity does not cross on its own. */
    private static final List<Predicate<Entity>> CARRIERS = new CopyOnWriteArrayList<>();

    /** Registers a test for entities carried by something that crosses edges itself (a Sable sub-level they stand on). */
    public static void registerCarrier(Predicate<Entity> carried) {
        CARRIERS.add(carried);
    }

    /**
     * Whether an entity is carried by something that crosses edges itself: it crosses with that, at the same moment,
     * rather than on its own when its own position passes the diagonal.
     */
    public static boolean carried(Entity entity) {
        for (Predicate<Entity> carrier : CARRIERS) {
            if (carrier.test(entity)) return true;
        }
        return false;
    }

    /** The face a position of {@code face}'s storage should transfer to, or null if it stays. */
    @Nullable
    public static CubeFace destination(CubeGeometry geometry, CubeFace face, double x, double y, double z) {
        CubeFace owner = geometry.ownerAt(face, x, y, z);
        if (owner == face) return null;
        if (owner.isNeighbour(face) && geometry.depthInto(face, owner, x, y, z) < MARGIN) return null;
        return owner;
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

    /** How far past the margin an upright entity may wait for room on the other side before crossing anyway. */
    public static final double WAIT_DEPTH = 2.0;

    /**
     * Whether an entity crossing now would land somewhere it fits. Upright things wait at the diagonal (within
     * {@link #WAIT_DEPTH}) rather than land inside the ground; past that, they cross and vanilla pushes them out.
     */
    public static boolean roomToCross(CubeGeometry geometry, Entity entity, CubeFace from, CubeFace to) {
        if (mode(entity) != Mode.UPRIGHT || !from.isNeighbour(to)) return true;
        if (geometry.depthInto(from, to, entity.getX(), entity.getY(), entity.getZ()) > WAIT_DEPTH) return true;
        double[] p = geometry.transform(from, to, entity.getX(), entity.getY(), entity.getZ());
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(p[0], p[1], p[2]);
        return entity.level().noCollision(entity, box);
    }

    /** A direction (velocity) crossing from one face to another. */
    public static double[] rotate(Mode mode, CubeFace from, CubeFace to, double x, double y, double z) {
        return mode == Mode.UPRIGHT && from.isNeighbour(to) ? CubeGeometry.rotateUpright(from, to, x, y, z) : CubeGeometry.rotate(from, to, x, y, z);
    }

    /** Vanilla's view vector for a pitch and yaw in degrees ({@code Entity.calculateViewVector}). */
    public static double[] look(float xRot, float yRot) {
        double pitch = xRot * Mth.DEG_TO_RAD;
        double yaw = -yRot * Mth.DEG_TO_RAD;
        return new double[] {Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
    }

    /**
     * A yaw and pitch crossing from one face to another, as {@code {yRot, xRot}}. The yaw keeps its winding (it
     * changes by less than a turn) so interpolation does not spin; looking straight up or down keeps the old yaw.
     */
    public static float[] rotateLook(Mode mode, CubeFace from, CubeFace to, float yRot, float xRot) {
        double[] d = look(xRot, yRot);
        double[] r = rotate(mode, from, to, d[0], d[1], d[2]);
        double horizontal = Math.sqrt(r[0] * r[0] + r[2] * r[2]);
        float newX = (float) (Math.atan2(-r[1], horizontal) * Mth.RAD_TO_DEG);
        float newY = horizontal < 1e-6 ? yRot : (float) (Math.atan2(-r[0], r[2]) * Mth.RAD_TO_DEG);
        return new float[] {yRot + Mth.wrapDegrees(newY - yRot), newX};
    }
}
