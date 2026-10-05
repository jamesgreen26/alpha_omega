package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Eases the view over an edge (design §5.2). Crossing turns "down" by 90° at once and swings the eye about the feet;
 * the camera starts where the old view really was (the same world-space direction and point, seen in the new face's
 * storage) and settles into the player's new upright view over a second. The sky's daytime eases from the old
 * face's to the new one's alongside ({@code ClientSky}).
 */
public final class FaceCamera {

    private static final int DURATION = 20;

    private static final Quaternionf offset = new Quaternionf();
    private static Vec3 shift = Vec3.ZERO;
    private static int ticksLeft;
    private static CubeFace from;
    private static CubeFace to;

    private FaceCamera() {
    }

    /** Starts easing after the player crossed, with its eye position just before and just after (each in its face's storage). */
    public static void start(CubeGeometry geometry, CubeFace from, CubeFace to, Vec3 eyeBefore, Vec3 eyeAfter) {
        // Seen from the new face: the true direction of an old view is W·d, the player's new view U·d; start at W·Uᵀ.
        Matrix3f world = columns(from, to, false);
        Matrix3f upright = from.isNeighbour(to) ? columns(from, to, true) : world;
        world.mul(upright.transpose(new Matrix3f())).getNormalizedRotation(offset);
        double[] before = geometry.transform(from, to, eyeBefore.x, eyeBefore.y, eyeBefore.z);
        shift = new Vec3(before[0], before[1], before[2]).subtract(eyeAfter);
        FaceCamera.from = from;
        FaceCamera.to = to;
        ticksLeft = DURATION;
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

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    public static void reset() {
        ticksLeft = 0;
    }

    /** The face the camera is easing away from, or null when it is not easing. */
    @Nullable
    public static CubeFace from() {
        return ticksLeft > 0 ? from : null;
    }

    /** The face the camera is easing onto, or null when it is not easing. */
    @Nullable
    public static CubeFace to() {
        return ticksLeft > 0 ? to : null;
    }

    /** How much of the old view is left, from 1 just after crossing to 0, smoothed. */
    public static float weight(float partialTick) {
        if (ticksLeft <= 0) return 0.0F;
        float t = Mth.clamp((ticksLeft - partialTick) / DURATION, 0.0F, 1.0F);
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
