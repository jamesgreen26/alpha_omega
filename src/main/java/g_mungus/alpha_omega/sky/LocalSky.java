package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

/**
 * The sun as seen from a point on the planet. Over an orbifold world's overworld, {@link PlanetProjection} places each
 * world position on the sphere (a longitude, a latitude and the heading of world -Z) and everything here works from
 * that; elsewhere the sun is vanilla's. Local solar time runs ahead to the east, one full day per turn of longitude,
 * and the sun's path tilts with latitude. There are no seasons (declination 0). Gameplay that depends on daylight asks
 * here instead of the global clock.
 *
 * <p>The projection is invariant under the world's group, so any image of a position (in the band, or a lap away)
 * gives the same sky, turned with it. At spawn every value equals vanilla's.
 *
 * <p>Two kinds of "time" come out of this. The local clock ({@link Sample#clock}) is the day time shifted by the
 * position's time zone, for consumers that read the clock (schedules, the clock item). The equivalent time of day
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
     * The sun at one position. {@code local} is false where vanilla's sun applies. Latitude is in radians and the
     * time zone in turns behind spawn (minus the longitude); the sun's direction is in world axes.
     */
    public record Sample(boolean local, double latitude, double timeZone, double clock, double timeOfDay,
                         double sunX, double sunY, double sunZ, double equivalentTimeOfDay) {

        /** Day number of the local clock. */
        public long day() {
            return (long) Math.floor(this.clock / 24000.0);
        }

        /** Sun altitude above the horizon, in radians. */
        public double altitude() {
            return Math.asin(Math.max(-1.0, Math.min(1.0, this.sunY)));
        }

        /** Sun azimuth in world axes, as a bearing from world −Z (east +X π/2), in radians in [0, 2π). */
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
     * The vanilla time of day that would put the sun at height {@code sunY}: {@code acos(sunY)/2π} after noon,
     * mirrored before it (while the sun is rising: in the geographic east).
     */
    public static double equivalentTimeOfDay(boolean rising, double sunY) {
        double half = Math.acos(Math.max(-1.0, Math.min(1.0, sunY))) / (2.0 * Math.PI);
        return rising ? 1.0 - half : half;
    }

    /** Vanilla's {@code Level.updateSkyBrightness} with the sun height in place of {@code cos(2π·timeOfDay)}. */
    public static int skyDarken(double sunY, float rain, float thunder) {
        double d0 = 1.0 - rain * 5.0F / 16.0;
        double d1 = 1.0 - thunder * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, sunY));
        return (int) ((1.0 - d2 * d0 * d1) * 11.0);
    }

    /** Vanilla's sun for a global day time: on the equator at the prime meridian. */
    public static Sample vanilla(double dayTime) {
        double timeOfDay = timeOfDay(dayTime);
        double hourAngle = 2.0 * Math.PI * timeOfDay;
        return new Sample(false, 0.0, 0.0, dayTime, timeOfDay, -Math.sin(hourAngle), Math.cos(hourAngle), 0.0, timeOfDay);
    }

    /** The full sample at a point on the planet for a global day time. */
    public static Sample sample(double dayTime, Position position) {
        double clock = localClock(dayTime, position.longitude());
        double[] sun = sunDirection(celestialTime(clock, position.latitude()), position.latitude());
        // Turn geographic axes into world axes: world -Z points along the heading.
        double cos = Math.cos(position.heading());
        double sin = Math.sin(position.heading());
        double sunX = sun[0] * cos + sun[2] * sin;
        double sunZ = -sun[0] * sin + sun[2] * cos;
        return new Sample(true, position.latitude(), -position.longitude(), clock, timeOfDay(clock), sunX, sun[1], sunZ,
            equivalentTimeOfDay(sun[0] > 0.0, sun[1]));
    }

    /**
     * The rotation that replaces vanilla's {@code XP(timeOfDay·360°)} in the sky renderer: {@code Ry(ψ)·Rz(-φ)·Rx(H)}
     * for heading ψ (the renderer's own {@code Ry(-90°)} commutes with the heading turn).
     */
    public static Quaternionf celestialRotation(double celestialTime, double latitude, double heading) {
        // Built in double: JOML's float rotations take the cosine from the sine, which loses ~1e-4 near a half turn.
        double turns = celestialTime - Math.floor(celestialTime);
        return new Quaternionf(new Quaterniond().rotateY(heading).rotateZ(-latitude).rotateX(2.0 * Math.PI * turns));
    }

    /** The sky renderer's rotation at a point on the planet for a global day time. */
    public static Quaternionf celestialRotation(double dayTime, Position position) {
        double clock = localClock(dayTime, position.longitude());
        return celestialRotation(celestialTime(clock, position.latitude()), position.latitude(), position.heading());
    }

    /**
     * A sample part way between two: {@code weight} 1 is {@code from}, 0 is {@code to}. The sun's direction blends
     * along the arc between the two, the clock the short way round the day; whether it is local is {@code to}'s.
     */
    public static Sample blend(Sample from, Sample to, double weight) {
        if (weight <= 0.0) return to;
        if (weight >= 1.0) return from;
        double x = to.sunX + (from.sunX - to.sunX) * weight;
        double y = to.sunY + (from.sunY - to.sunY) * weight;
        double z = to.sunZ + (from.sunZ - to.sunZ) * weight;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-6) return weight >= 0.5 ? from : to;
        x /= length;
        y /= length;
        z /= length;
        double ahead = from.clock - to.clock;
        ahead -= 24000.0 * Math.floor(ahead / 24000.0 + 0.5);
        double clock = to.clock + ahead * weight;
        boolean rising = (weight >= 0.5 ? from : to).equivalentTimeOfDay > 0.5;
        return new Sample(to.local, to.latitude + (from.latitude - to.latitude) * weight, to.timeZone + (from.timeZone - to.timeZone) * weight,
            clock, timeOfDay(clock), x, y, z, equivalentTimeOfDay(rising, y));
    }

    /**
     * The mean of points on the planet, averaged on the sphere (so neither the date line nor the poles are an edge), or
     * null when they cancel out. The heading of the result is 0.
     */
    @Nullable
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
     * noon if it never is (near the poles, and at the north pole cone point). In (0, 24000].
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

    /** Whether the local sky applies at all: the overworld, without fixed time. Elsewhere everything is vanilla. */
    public static boolean active(Level level) {
        return level.dimension() == Level.OVERWORLD && !level.dimensionType().hasFixedTime();
    }

    /** Whether positions have their own sun: the local sky applies and the world is an orbifold. */
    public static boolean local(Level level) {
        return active(level) && Orbifold.of(level) != null;
    }

    /** Where a position lies on the planet, or null where the local sky does not apply (vanilla's sun). */
    @Nullable
    public static Position position(Level level, double x, double z) {
        if (!active(level)) return null;
        OrbifoldGeometry geometry = Orbifold.of(level);
        return geometry == null ? null : PlanetProjection.of(geometry).project(x, z);
    }

    /** The sun at a position, or vanilla's when the local sky does not apply. */
    public static Sample sample(Level level, double x, double z) {
        Position position = position(level, x, z);
        return position == null ? vanilla(level.getDayTime()) : sample(level.getDayTime(), position);
    }

    /** The rotation the sky renderer draws the sun, moon and stars with at a position. */
    public static Quaternionf celestialRotation(Level level, double x, double z) {
        Position position = position(level, x, z);
        if (position == null) return com.mojang.math.Axis.XP.rotationDegrees(level.getTimeOfDay(1.0F) * 360.0F);
        return celestialRotation(level.getDayTime(), position);
    }

    /** The local clock at a position, rounded to whole ticks for clock-shaped consumers. */
    public static long localDayTime(Level level, double x, double z) {
        Position position = position(level, x, z);
        if (position == null) return level.getDayTime();
        return level.getDayTime() + Math.round(24000.0 * position.longitude());
    }

    /**
     * Sky darkening at a position, as {@code Level.getSkyDarken()} would be if the whole world shared its sun.
     * Vanilla's own value where the local sky does not apply, so gameplay there is exactly vanilla.
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

    /**
     * Where skipping the night lands: the next first light at the sleepers' mean position on the planet (a mean on the
     * sphere, so neither the date line nor the poles are an edge), as a global day time; at a pole, where the sun never
     * rises far enough, local noon. Null if nobody is sleeping, the sleepers' positions cancel out, or the local sky
     * does not apply (vanilla's rule then).
     */
    @Nullable
    public static Long morningAfterSleep(ServerLevel level) {
        if (!local(level)) return null;
        Position[] sleepers = level.players().stream().filter(ServerPlayer::isSleeping)
            .map(player -> position(level, player.getX(), player.getZ())).toArray(Position[]::new);
        if (sleepers.length == 0) return null;
        Position mean = meanPosition(sleepers);
        if (mean == null) return null;
        return level.getDayTime() + sleepTimeAddition(level.getDayTime(), mean);
    }
}
