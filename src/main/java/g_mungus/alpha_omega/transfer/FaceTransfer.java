package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Crossing an edge (design §5), shared by server and client. Something transfers when its position lies past the
 * diagonal into another face's region by more than {@link #MARGIN}; it lands at the same cube point in that face's
 * storage. Coming back needs the same margin the other way, so walking along an edge does not flip-flop.
 */
public final class FaceTransfer {

    /** How far past the diagonal (across it, in blocks) a position must be before it transfers. */
    public static final double MARGIN = 0.5;

    private FaceTransfer() {
    }

    /** How velocity and orientation cross. */
    public enum Mode {
        /** Turned with the unfold: walking off an edge carries on along the next face. Players, mobs, vehicles. */
        UPRIGHT,
        /** Kept in world space: momentum is real, only gravity changes direction. Items, projectiles, the rest. */
        WORLD
    }

    public static Mode mode(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof VehicleEntity ? Mode.UPRIGHT : Mode.WORLD;
    }

    /** The face a position of {@code face}'s storage should transfer to, or null if it stays. */
    @Nullable
    public static CubeFace destination(CubeGeometry geometry, CubeFace face, double x, double y, double z) {
        CubeFace owner = geometry.ownerAt(face, x, y, z);
        if (owner == face) return null;
        if (owner.isNeighbour(face) && geometry.depthInto(face, owner, x, y, z) < MARGIN) return null;
        return owner;
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
