package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

/**
 * The sun as seen from a point on the planet. {@link PlanetProjection} places each world position on the sphere (a
 * longitude, a latitude and the heading of world -Z); everything here works from that. Local solar time runs ahead to
 * the east, one full day per turn of longitude, and the sun's path tilts with latitude. There are no seasons
 * (declination 0).
 *
 * <p>Everything is periodic in the world size, so any image of a position gives the same sky. At the projection's
 * origin, and whenever the world does not wrap, every value equals vanilla's.
 *
 * <p>Two kinds of "time" come out of this. The local clock ({@link Sample#clock}) is the day time shifted by the time
 * zone, for consumers that read the clock (schedules, the clock item). The equivalent time of day
 * ({@link Sample#equivalentTimeOfDay}) is the vanilla time of day that would put the sun at the same height, for
 * consumers that only use {@code cos(2π·timeOfDay)} (sky darkening, star brightness, sky colours).
 */
public final class LocalSky {

    /** Sun height above which a clear sky counts as day ({@code skyDarken < 4}). */
    public static final double DAY_SUN_HEIGHT = (7.0 / 11.0 - 0.5) / 2.0;

    /** Latitude above which vanilla's easing of the sun's motion fades out, reaching none at the poles. */
    private static final double EASING_FADE_LATITUDE = Math.toRadians(75.0);

    private LocalSky() {
    }

    /**
     * The sun at one position: where it is on the planet, the local time, and the sky's orientation and the sun's
     * direction in world axes. {@code timeOfDay} is the local clock's vanilla time of day; {@code celestialTime} is the
     * sky's rotation (the same, with the easing faded out near the poles).
     */
    public record Sample(double longitude, double latitude, double heading, double clock, double timeOfDay,
                         double celestialTime, double sunX, double sunY, double sunZ, double equivalentTimeOfDay) {

        /** Day number of the local clock. */
        public long day() {
            return (long) Math.floor(this.clock / 24000.0);
        }

        /** Sun altitude above the horizon, in radians. */
        public double altitude() {
            return Math.asin(Math.max(-1.0, Math.min(1.0, this.sunY)));
        }

        /** Sun azimuth in world axes, as a bearing from world -Z (east π/2), in radians in [0, 2π). */
        public double azimuth() {
            double azimuth = Math.atan2(this.sunX, -this.sunZ);
            return azimuth < 0.0 ? azimuth + 2.0 * Math.PI : azimuth;
        }
    }

    // ---- Pure math ----

    /** The local clock: global day time plus the time zone offset, one full day per turn of longitude. */
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
     * The sky's rotation, as a vanilla time of day, for a local clock and latitude. Vanilla eases the sun so that day
     * is longer than night, keyed to the local clock. At a pole every clock meets, so no easing can be right there:
     * it fades out above {@link #EASING_FADE_LATITUDE} and the sky turns evenly at the poles, whatever the projection
     * does to longitude on the way over.
     */
    public static double celestialTime(double clock, double latitude) {
        double even = clock / 24000.0 - 0.25;
        double eased = timeOfDay(clock);
        double t = Math.max(0.0, Math.min(1.0, Math.cos(latitude) / Math.cos(EASING_FADE_LATITUDE)));
        return even + t * t * (3.0 - 2.0 * t) * PlanetProjection.wrapTurns(eased - even);
    }

    /**
     * The sun's direction in geographic axes (east +X, up +Y, north -Z) for a celestial time and latitude. Vanilla's
     * sky rotates by {@code Ry(-90°)·Rx(2π·timeOfDay)}; here the rotation becomes {@code Ry(-90°)·Rz(-φ)·Rx(H)}.
     */
    public static double[] sunDirection(double celestialTime, double latitude) {
        double hourAngle = 2.0 * Math.PI * celestialTime;
        double cosH = Math.cos(hourAngle);
        return new double[] {-Math.sin(hourAngle), Math.cos(latitude) * cosH, Math.sin(latitude) * cosH};
    }

    /**
     * The vanilla time of day that would put the sun at height {@code sunY}: {@code acos(sunY)/2π} in the afternoon,
     * mirrored in the morning (sun in the geographic east, {@code sunEast > 0}). Equals the time of day itself at the
     * equator.
     */
    public static double equivalentTimeOfDay(double sunEast, double sunY) {
        double half = Math.acos(Math.max(-1.0, Math.min(1.0, sunY))) / (2.0 * Math.PI);
        return sunEast > 0.0 ? 1.0 - half : half;
    }

    /** Vanilla's {@code Level.updateSkyBrightness} with the sun height in place of {@code cos(2π·timeOfDay)}. */
    public static int skyDarken(double sunY, float rain, float thunder) {
        double d0 = 1.0 - rain * 5.0F / 16.0;
        double d1 = 1.0 - thunder * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, sunY));
        return (int) ((1.0 - d2 * d0 * d1) * 11.0);
    }

    /** The full sample at a point on the planet for a global day time. */
    public static Sample sample(double dayTime, Position position) {
        double clock = localClock(dayTime, position.longitude());
        double celestialTime = celestialTime(clock, position.latitude());
        double[] sun = sunDirection(celestialTime, position.latitude());
        // Turn geographic axes into world axes: world -Z points along the heading.
        double cos = Math.cos(position.heading());
        double sin = Math.sin(position.heading());
        double sunX = sun[0] * cos + sun[2] * sin;
        double sunZ = -sun[0] * sin + sun[2] * cos;
        return new Sample(position.longitude(), position.latitude(), position.heading(), clock, timeOfDay(clock),
            celestialTime, sunX, sun[1], sunZ, equivalentTimeOfDay(sun[0], sun[1]));
    }

    /**
     * The rotation that replaces vanilla's {@code XP(timeOfDay·360°)} in the sky renderer: {@code Ry(ψ)·Rz(-φ)·Rx(H)}
     * for heading ψ (the renderer's own {@code Ry(-90°)} commutes with the heading turn).
     */
    public static Quaternionf celestialRotation(double celestialTime, double latitude, double heading) {
        return new Quaternionf().rotateY((float) heading).rotateZ((float) -latitude)
            .rotateX((float) (2.0 * Math.PI * celestialTime));
    }

    public static Quaternionf celestialRotation(Sample sample) {
        return celestialRotation(sample.celestialTime(), sample.latitude(), sample.heading());
    }

    /**
     * The mean of points on the planet, averaged on the sphere (so neither the date line nor the poles are an edge), or
     * null when they cancel out. The heading of the result is 0.
     */
    public static Position meanPosition(Position[] positions) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Position position : positions) {
            double lon = 2.0 * Math.PI * position.longitude();
            x += Math.cos(position.latitude()) * Math.cos(lon);
            y += Math.cos(position.latitude()) * Math.sin(lon);
            z += Math.sin(position.latitude());
        }
        double horizontal = Math.hypot(x, y);
        if (Math.hypot(horizontal, z) < 1e-6 * Math.max(1, positions.length)) return null;
        double longitude = horizontal < 1e-9 ? 0.0 : PlanetProjection.wrapTurns(Math.atan2(y, x) / (2.0 * Math.PI));
        return new Position(longitude, Math.atan2(z, horizontal), 0.0);
    }

    /**
     * Ticks to add to the global day time when sleepers at this point skip the night: to the next local clock 0
     * (vanilla's morning), or later that morning if the sun is not yet high enough there to count as day, or local
     * noon if it never is (near the poles). In (0, 24000].
     */
    public static long sleepTimeAddition(long dayTime, Position position) {
        long local = dayTime + Math.round(24000.0 * position.longitude());
        // In the evening, wake at the coming local 0; after it (still dark at high latitudes), wait for daylight.
        long toMorning = Math.floorMod(local, 24000L) >= 12000L ? Math.floorMod(-local, 24000L) : 0L;
        long toNoonToday = Math.floorMod(6000L - (local + toMorning), 24000L);
        for (long add = toMorning; add <= toMorning + toNoonToday; add += 10L) {
            if (sample(dayTime + add, position).sunY > DAY_SUN_HEIGHT) return add == 0L ? 24000L : add;
        }
        long toNoon = Math.floorMod(6000L - local, 24000L);
        return toNoon == 0L ? 24000L : toNoon;
    }

    // ---- Level adapters ----

    /** Whether the local sky applies: the overworld, without fixed time. Elsewhere everything is vanilla. */
    public static boolean active(Level level) {
        return level.dimension() == Level.OVERWORLD && !level.dimensionType().hasFixedTime();
    }

    /** Where a position lies on the planet, or the origin (vanilla) when the local sky does not apply. */
    public static Position position(Level level, double x, double z) {
        int period = Wrap.of(level).period;
        if (!active(level) || period <= 0) return Position.ORIGIN;
        return PlanetProjection.project(lapFraction(x, period), lapFraction(z, period));
    }

    /** A coordinate as a fraction of a lap, in [0, 1). */
    public static double lapFraction(double coordinate, int period) {
        double turns = coordinate / period;
        return turns - Math.floor(turns);
    }

    /** The sun at a position, or at the origin (vanilla) when the local sky does not apply. */
    public static Sample sample(Level level, double x, double z) {
        return sample(level.getDayTime(), position(level, x, z));
    }

    /** The local clock at a position, rounded to whole ticks for clock-shaped consumers. */
    public static long localDayTime(Level level, double x, double z) {
        return level.getDayTime() + Math.round(24000.0 * position(level, x, z).longitude());
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

    public static boolean isDay(Entity entity) {
        return isDay(entity.level(), entity.getX(), entity.getZ());
    }

    public static boolean isNight(Entity entity) {
        return isNight(entity.level(), entity.getX(), entity.getZ());
    }

    /** The local clock at an entity's position, for clock-shaped consumers such as villager schedules. */
    public static long localDayTime(Entity entity) {
        return localDayTime(entity.level(), entity.getX(), entity.getZ());
    }
}
