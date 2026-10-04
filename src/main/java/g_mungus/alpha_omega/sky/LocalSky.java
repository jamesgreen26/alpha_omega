package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

/**
 * The sun as seen from a point on the torus. X is longitude: local solar time runs ahead to the east, one full day
 * per lap. Z is latitude, a triangle wave: the equator at z = 0, the north pole a quarter lap toward -Z, the equator
 * again half a lap away and the south pole at three quarters. There are no seasons (declination 0).
 *
 * <p>Everything is periodic in the world size, so any image of a position gives the same sky. At the equator, and
 * whenever the world does not wrap, every value equals vanilla's.
 *
 * <p>Two kinds of "time" come out of this. The local clock ({@link Sample#clock}) is the day time shifted by the time
 * zone, for consumers that read the clock (schedules, the clock item). The equivalent time of day
 * ({@link Sample#equivalentTimeOfDay}) is the vanilla time of day that would put the sun at the same height, for
 * consumers that only use {@code cos(2π·timeOfDay)} (sky darkening, star brightness, sky colours).
 */
public final class LocalSky {

    /** Sun height above which a clear sky counts as day ({@code skyDarken < 4}). */
    public static final double DAY_SUN_HEIGHT = (7.0 / 11.0 - 0.5) / 2.0;

    private LocalSky() {
    }

    /** The sun at one position: local time, coordinates on the planet and the sun's direction in world axes. */
    public record Sample(double longitude, double latitude, double clock, double timeOfDay, double sunX, double sunY,
                         double sunZ, double equivalentTimeOfDay) {

        /** Day number of the local clock. */
        public long day() {
            return (long) Math.floor(this.clock / 24000.0);
        }

        /** Sun altitude above the horizon, in radians. */
        public double altitude() {
            return Math.asin(Math.max(-1.0, Math.min(1.0, this.sunY)));
        }

        /** Sun azimuth as a compass bearing (north 0, east π/2), in radians in [0, 2π). */
        public double azimuth() {
            double azimuth = Math.atan2(this.sunX, -this.sunZ);
            return azimuth < 0.0 ? azimuth + 2.0 * Math.PI : azimuth;
        }
    }

    // ---- Pure math ----

    /** Longitude in turns, in [-0.5, 0.5); 0 at x = 0, the date line half a lap away. */
    public static double longitude(double x, int period) {
        if (period <= 0) return 0.0;
        double turns = x / period + 0.5;
        return turns - Math.floor(turns) - 0.5;
    }

    /** Latitude in radians, in [-π/2, π/2]: a triangle wave of z with the north pole a quarter lap toward -Z. */
    public static double latitude(double z, int period) {
        if (period <= 0) return 0.0;
        double turns = -z / period;
        double u = turns - Math.floor(turns);
        double wave = u <= 0.25 ? 4.0 * u : u <= 0.75 ? 2.0 - 4.0 * u : 4.0 * u - 4.0;
        return wave * (Math.PI / 2.0);
    }

    /** The local clock: global day time plus the time zone offset, one full day per lap of longitude. */
    public static double localClock(double dayTime, double longitude) {
        return dayTime + 24000.0 * longitude;
    }

    /** Vanilla's eased time of day ({@code DimensionType.timeOfDay}) for a (fractional) clock value. */
    public static double timeOfDay(double clock) {
        double turns = clock / 24000.0 - 0.25;
        double d0 = turns - Math.floor(turns);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (d0 * 2.0 + d1) / 3.0;
    }

    /**
     * The sun's direction in world axes (east +X, up +Y, north -Z) for a time of day and latitude. Vanilla's sky
     * rotates by {@code Ry(-90°)·Rx(2π·timeOfDay)}; here the rotation becomes {@code Ry(-90°)·Rz(-φ)·Rx(H)}.
     */
    public static double[] sunDirection(double timeOfDay, double latitude) {
        double hourAngle = 2.0 * Math.PI * timeOfDay;
        double cosH = Math.cos(hourAngle);
        return new double[] {-Math.sin(hourAngle), Math.cos(latitude) * cosH, Math.sin(latitude) * cosH};
    }

    /**
     * The vanilla time of day that would put the sun at height {@code sunY}: {@code acos(sunY)/2π} in the afternoon,
     * mirrored in the morning (sun in the east). Equals the time of day itself at the equator.
     */
    public static double equivalentTimeOfDay(double sunX, double sunY) {
        double half = Math.acos(Math.max(-1.0, Math.min(1.0, sunY))) / (2.0 * Math.PI);
        return sunX > 0.0 ? 1.0 - half : half;
    }

    /** Vanilla's {@code Level.updateSkyBrightness} with the sun height in place of {@code cos(2π·timeOfDay)}. */
    public static int skyDarken(double sunY, float rain, float thunder) {
        double d0 = 1.0 - rain * 5.0F / 16.0;
        double d1 = 1.0 - thunder * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, sunY));
        return (int) ((1.0 - d2 * d0 * d1) * 11.0);
    }

    /** The full sample at a longitude and latitude for a global day time. */
    public static Sample sample(double dayTime, double longitude, double latitude) {
        double clock = localClock(dayTime, longitude);
        double timeOfDay = timeOfDay(clock);
        double[] sun = sunDirection(timeOfDay, latitude);
        return new Sample(longitude, latitude, clock, timeOfDay, sun[0], sun[1], sun[2], equivalentTimeOfDay(sun[0], sun[1]));
    }

    /** The rotation that replaces vanilla's {@code XP(timeOfDay·360°)} in the sky renderer: {@code Rz(-φ)·Rx(H)}. */
    public static Quaternionf celestialRotation(double timeOfDay, double latitude) {
        return new Quaternionf().rotateZ((float) -latitude).rotateX((float) (2.0 * Math.PI * timeOfDay));
    }

    /** Circular mean of longitudes (in turns), or NaN when they cancel out. */
    public static double meanLongitude(double[] longitudes) {
        double sin = 0.0;
        double cos = 0.0;
        for (double longitude : longitudes) {
            sin += Math.sin(2.0 * Math.PI * longitude);
            cos += Math.cos(2.0 * Math.PI * longitude);
        }
        if (Math.hypot(sin, cos) < 1e-6 * Math.max(1, longitudes.length)) return Double.NaN;
        return Math.atan2(sin, cos) / (2.0 * Math.PI);
    }

    /**
     * Ticks to add to the global day time when sleepers at this longitude and latitude skip the night: to the next
     * local clock 0 (vanilla's morning), or later that morning if the sun is not yet high enough there to count as
     * day, or local noon if it never is (near the poles). In (0, 24000].
     */
    public static long sleepTimeAddition(long dayTime, double longitude, double latitude) {
        long offset = Math.round(24000.0 * longitude);
        long local = dayTime + offset;
        // In the evening, wake at the coming local 0; after it (still dark at high latitudes), wait for daylight.
        long toMorning = Math.floorMod(local, 24000L) >= 12000L ? Math.floorMod(-local, 24000L) : 0L;
        long toNoonToday = Math.floorMod(6000L - (local + toMorning), 24000L);
        for (long add = toMorning; add <= toMorning + toNoonToday; add += 10L) {
            double[] sun = sunDirection(timeOfDay(local + add), latitude);
            if (sun[1] > DAY_SUN_HEIGHT) return add == 0L ? 24000L : add;
        }
        long toNoon = Math.floorMod(6000L - local, 24000L);
        return toNoon == 0L ? 24000L : toNoon;
    }

    // ---- Level adapters ----

    /** Whether the local sky applies: the overworld, without fixed time. Elsewhere everything is vanilla. */
    public static boolean active(Level level) {
        return level.dimension() == Level.OVERWORLD && !level.dimensionType().hasFixedTime();
    }

    /** The sun at a position, or at the prime meridian on the equator (vanilla) when the local sky does not apply. */
    public static Sample sample(Level level, double x, double z) {
        if (!active(level)) return sample(level.getDayTime(), 0.0, 0.0);
        int period = Wrap.of(level).period;
        return sample(level.getDayTime(), longitude(x, period), latitude(z, period));
    }

    /** The local clock at a block x, rounded to whole ticks for clock-shaped consumers. */
    public static long localDayTime(Level level, double x) {
        if (!active(level)) return level.getDayTime();
        return level.getDayTime() + Math.round(24000.0 * longitude(x, Wrap.of(level).period));
    }

    /** Whether positions have their own sun: the local sky applies and the world wraps. */
    public static boolean local(Level level) {
        return active(level) && Wrap.of(level).enabled();
    }

    /**
     * Sky darkening at a position, as {@code Level.getSkyDarken()} would be if the whole world shared its sun.
     * Vanilla's own value when the world does not wrap, so gameplay there is exactly vanilla.
     */
    public static int skyDarken(Level level, double x, double z) {
        if (!local(level)) return level.getSkyDarken();
        return skyDarken(sample(level, x, z).sunY, level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
    }

    public static int skyDarken(Level level, BlockPos pos) {
        return skyDarken(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** {@code Level.isDay()} at a position. */
    public static boolean isDay(Level level, double x, double z) {
        if (!local(level)) return level.isDay();
        return skyDarken(level, x, z) < 4;
    }

    public static boolean isDay(Level level, BlockPos pos) {
        return isDay(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** {@code Level.isNight()} at a position. */
    public static boolean isNight(Level level, double x, double z) {
        if (!local(level)) return level.isNight();
        return !isDay(level, x, z);
    }

    public static boolean isNight(Level level, BlockPos pos) {
        return isNight(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }
}
