package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Eases the view over an edge (design §5.2). Crossing turns "down" by 90° at once and swings the eye about the feet;
 * the camera starts where the old view really was (the same world-space direction and point, seen in the new face's
 * storage) and settles into the player's new upright view by the time the player is predicted to touch the ground
 * on the new face (from its momentum and gravity), within {@link #MIN_TICKS} to {@link #MAX_TICKS}; once it does
 * touch the ground, the rest goes at the {@link #MIN_TICKS} pace. The sky's daytime eases from the old face's to the
 * new one's alongside ({@code ClientSky}).
 */
public final class FaceCamera {

    /** The shortest ease, for a player already on the ground, and the pace of what is left once it lands. */
    private static final int MIN_TICKS = 10;
    /** The longest ease, for a player that does not land soon (flying, or falling far). */
    private static final int MAX_TICKS = 40;

    private static final Quaternionf offset = new Quaternionf();
    private static Vec3 shift = Vec3.ZERO;
    private static int duration;
    /** How much of the ease is left (1 just after crossing, 0 when done), now and a tick ago. */
    private static float left;
    private static float leftBefore;
    private static CubeFace from;
    private static CubeFace to;

    private FaceCamera() {
    }

    /**
     * Starts easing after the player crossed, with its eye position just before and just after (each in its face's
     * storage). {@code mover} is what carries the player (itself, or its vehicle), already in the new face's storage.
     */
    public static void start(CubeGeometry geometry, CubeFace from, CubeFace to, Vec3 eyeBefore, Vec3 eyeAfter, Entity mover) {
        // Seen from the new face: the true direction of an old view is W·d, the player's new view U·d; start at W·Uᵀ.
        Matrix3f world = columns(from, to, false);
        Matrix3f upright = from.isNeighbour(to) ? columns(from, to, true) : world;
        world.mul(upright.transpose(new Matrix3f())).getNormalizedRotation(offset);
        double[] before = geometry.transform(from, to, eyeBefore.x, eyeBefore.y, eyeBefore.z);
        shift = new Vec3(before[0], before[1], before[2]).subtract(eyeAfter);
        FaceCamera.from = from;
        FaceCamera.to = to;
        duration = Mth.clamp(ticksToLand(mover), MIN_TICKS, MAX_TICKS);
        left = leftBefore = 1.0F;
    }

    private static Matrix3f columns(CubeFace from, CubeFace to, boolean upright) {
        Matrix3f m = new Matrix3f();
        for (int j = 0; j < 3; j++) {
            double[] axis = {j == 0 ? 1 : 0, j == 1 ? 1 : 0, j == 2 ? 1 : 0};
            double[] r = upright ? CubeGeometry.rotateUpright(from, to, axis[0], axis[1], axis[2]) : CubeGeometry.rotate(from, to, axis[0], axis[1], axis[2]);
            m.setColumn(j, (float) r[0], (float) r[1], (float) r[2]);
        }
        return m;
    }

    /**
     * How many ticks until something falling freely from where it is touches the ground, following vanilla's motion
     * in the air (gravity, then drag) and stopping at blocks; {@link #MAX_TICKS} or more if it doesn't land by then.
     */
    private static int ticksToLand(Entity mover) {
        // Flying or gliding, it won't fall as this predicts.
        if (mover instanceof Player player && (player.getAbilities().flying || player.isFallFlying())) return MAX_TICKS;
        Level level = mover.level();
        AABB box = mover.getBoundingBox();
        Vec3 motion = mover.getDeltaMovement();
        double gravity = mover.getGravity();
        double x = motion.x, y = motion.y, z = motion.z;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            if (y <= 0.0 && !level.noCollision(mover, box.move(0.0, Math.min(y, -1e-3), 0.0))) return tick;
            AABB across = box.move(x, 0.0, z);
            if (level.noCollision(mover, across)) box = across;
            else x = z = 0.0;
            AABB up = box.move(0.0, y, 0.0);
            if (level.noCollision(mover, up)) box = up;
            else y = 0.0;
            y = (y - gravity) * 0.98;
            x *= 0.91;
            z *= 0.91;
        }
        return MAX_TICKS;
    }

    /** How many ticks the current (or last) ease lasts. */
    public static int duration() {
        return duration;
    }

    /** Once a tick, after the player moved; {@code mover} is what carries it. On the ground, the ease hurries. */
    public static void tick(Entity mover) {
        leftBefore = left;
        if (left <= 0.0F) return;
        float step = mover.onGround() ? Math.max(1.0F / duration, 1.0F / MIN_TICKS) : 1.0F / duration;
        left = Math.max(0.0F, left - step);
    }

    public static void reset() {
        left = leftBefore = 0.0F;
    }

    /** The face the camera is easing away from, or null when it is not easing. */
    @Nullable
    public static CubeFace from() {
        return easing() ? from : null;
    }

    /** The face the camera is easing onto, or null when it is not easing. */
    @Nullable
    public static CubeFace to() {
        return easing() ? to : null;
    }

    private static boolean easing() {
        return left > 0.0F || leftBefore > 0.0F;
    }

    /** How much of the old view is left, from 1 just after crossing to 0, smoothed. */
    public static float weight(float partialTick) {
        if (!easing()) return 0.0F;
        float t = Mth.clamp(Mth.lerp(partialTick, leftBefore, left), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** The extra rotation to put in front of the camera's, for a weight. */
    public static Quaternionf rotation(float weight) {
        return new Quaternionf().slerp(offset, weight);
    }

    public static Vec3 shift(float weight) {
        return shift.scale(weight);
    }
}
