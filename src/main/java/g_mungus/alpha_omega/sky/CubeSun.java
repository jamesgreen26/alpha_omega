package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeSettings;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * The sun over a cube (design §7). It is at infinity and turns in cube space about the world's sun axis, so a face,
 * being flat, sees one sun over its whole surface: one elevation, one time zone per face.
 *
 * <p>The hour angle {@code H} is vanilla's: {@code 2π·timeOfDay(dayTime)}, shared by every face. The sun's cube
 * direction is {@code cos H·e1 − sin H·e2}, where {@code e1} is UP's normal flattened onto the sun's plane (so UP has
 * its noon with vanilla's) and {@code e2 = e1 × axis}. With the polar axis, UP sees exactly vanilla's sun.
 *
 * <p>The sky renderer draws the sun at +Y of a frame rotated by {@code Ry(−90°)·Q}; vanilla's {@code Q} is
 * {@code Rx(H)}. Here {@code Q = Ry(90°)·Mᵀ·C·Rx(H)}, with {@code M} the face's rotation and {@code C} the celestial
 * frame ({@code X} the pole, {@code Y} {@code e1}, {@code Z} {@code −e2}), so stars turn about the true pole.
 */
public final class CubeSun {

    private final double[] axis;
    private final double[] e1;
    private final double[] e2;
    /** Celestial frame to cube space, columns pole, e1, −e2. */
    private final Matrix3f celestial;

    public CubeSun(CubeSettings.SunAxis sunAxis) {
        this.axis = new double[] {sunAxis.x, sunAxis.y, sunAxis.z};
        double[] up = {0.0, 1.0, 0.0};
        double along = dot(up, this.axis);
        double[] flat = {up[0] - along * this.axis[0], up[1] - along * this.axis[1], up[2] - along * this.axis[2]};
        this.e1 = normalize(flat);
        this.e2 = cross(this.e1, this.axis);
        this.celestial = new Matrix3f(
            (float) this.axis[0], (float) this.axis[1], (float) this.axis[2],
            (float) this.e1[0], (float) this.e1[1], (float) this.e1[2],
            (float) -this.e2[0], (float) -this.e2[1], (float) -this.e2[2]);
    }

    /** The hour angle for a (fractional) global day time. */
    public static double hourAngle(double dayTime) {
        return 2.0 * Math.PI * LocalSky.timeOfDay(dayTime);
    }

    /** The sun's direction in cube space. */
    public double[] cubeDirection(double hourAngle) {
        double c = Math.cos(hourAngle), s = Math.sin(hourAngle);
        return new double[] {c * this.e1[0] - s * this.e2[0], c * this.e1[1] - s * this.e2[1], c * this.e1[2] - s * this.e2[2]};
    }

    /** The sun's direction in a face's storage axes: +Y is up, so {@code y} is the sine of its elevation. */
    public double[] direction(CubeFace face, double hourAngle) {
        double[] c = this.cubeDirection(hourAngle);
        return face.toLocal(c[0], c[1], c[2]);
    }

    /** The face's latitude: its normal's angle above the sun's plane, in radians. */
    public double latitude(CubeFace face) {
        return Math.asin(Math.max(-1.0, Math.min(1.0, face.normal(0) * this.axis[0] + face.normal(1) * this.axis[1] + face.normal(2) * this.axis[2])));
    }

    /** The hour angle at which the face has its noon (highest sun), in (−π, π]; 0 for a face at a pole. */
    public double noonHourAngle(CubeFace face) {
        double[] n = {face.normal(0), face.normal(1), face.normal(2)};
        double along1 = dot(n, this.e1), along2 = dot(n, this.e2);
        if (Math.abs(along1) < 1e-9 && Math.abs(along2) < 1e-9) return 0.0;
        return Math.atan2(-along2, along1);
    }

    /** The face's time zone: how far behind UP its noon comes, in turns of vanilla's time of day, in [−0.5, 0.5). */
    public double timeZone(CubeFace face) {
        double turns = this.noonHourAngle(face) / (2.0 * Math.PI);
        return turns - Math.floor(turns + 0.5);
    }

    /** Whether the sun is climbing over the face. */
    public boolean rising(CubeFace face, double hourAngle) {
        return Math.sin(hourAngle - this.noonHourAngle(face)) < 0.0;
    }

    /**
     * The face's local clock: the day time whose vanilla time of day is the face's, so local noon reads 6000 and
     * local midnight 18000. It is the global day time plus a (nearly constant) offset, on the nearest day.
     */
    public double localClock(CubeFace face, double dayTime) {
        double local = LocalSky.timeOfDay(dayTime) - this.timeZone(face);
        double clock = dayTimeOfTimeOfDay(local);
        // Choose the day: the clock runs alongside the global time, offset by the zone (in ticks, roughly).
        double near = dayTime - 24000.0 * this.timeZone(face);
        return clock + 24000.0 * Math.round((near - clock) / 24000.0);
    }

    /** The rotation replacing vanilla's {@code XP(timeOfDay·360°)} in the sky renderer for a face. */
    public Quaternionf celestialRotation(CubeFace face, double hourAngle) {
        Matrix3f faceToLocal = new Matrix3f(
            face.m(0, 0), face.m(0, 1), face.m(0, 2),
            face.m(1, 0), face.m(1, 1), face.m(1, 2),
            face.m(2, 0), face.m(2, 1), face.m(2, 2));
        Matrix3f q = new Matrix3f().rotationY((float) (Math.PI / 2.0))
            .mul(faceToLocal)
            .mul(this.celestial)
            .mul(new Matrix3f().rotationX((float) hourAngle));
        return q.getNormalizedRotation(new Quaternionf());
    }

    /**
     * The first global day time after {@code dayTime} when the face is in daylight ({@link LocalSky#DAY_SUN_HEIGHT})
     * after being out of it: its next morning. A face that never gets that light wakes at its next noon.
     */
    public long nextMorning(CubeFace face, long dayTime) {
        boolean wasDay = this.direction(face, hourAngle(dayTime))[1] > LocalSky.DAY_SUN_HEIGHT;
        for (long t = dayTime + 10; t <= dayTime + 24000; t += 10) {
            boolean day = this.direction(face, hourAngle(t))[1] > LocalSky.DAY_SUN_HEIGHT;
            if (day && !wasDay) return t;
            wasDay = day;
        }
        double best = -2.0;
        long noon = dayTime + 24000;
        for (long t = dayTime + 10; t <= dayTime + 24000; t += 10) {
            double y = this.direction(face, hourAngle(t))[1];
            if (y > best) {
                best = y;
                noon = t;
            }
        }
        return noon;
    }

    /** The day time in [0, 24000) whose vanilla time of day is {@code timeOfDay} (taken mod 1). */
    static double dayTimeOfTimeOfDay(double timeOfDay) {
        double target = timeOfDay - Math.floor(timeOfDay);
        // timeOfDay(t) rises from 0 at t = 6000 through one turn per day: bisect on t in [6000, 30000).
        double lo = 6000.0, hi = 30000.0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            double value = LocalSky.timeOfDay(mid);
            if (mid > 6000.0 && value < 1e-12) value = 1.0;
            if (value < target) lo = mid;
            else hi = mid;
        }
        double t = 0.5 * (lo + hi);
        return t >= 24000.0 ? t - 24000.0 : t;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        double length = Math.sqrt(dot(v, v));
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }
}
