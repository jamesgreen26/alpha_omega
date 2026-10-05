package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * The sun as seen from a position: over a cube world's overworld, the sun of the face below ({@link CubeSun}); elsewhere
 * vanilla's. Gameplay that depends on daylight asks here instead of the global clock (design §7).
 *
 * <p>Two kinds of "time" come out of this. The local clock ({@link Sample#clock}) is the day time shifted by the face's
 * time zone, for consumers that read the clock (schedules, the clock item). The equivalent time of day
 * ({@link Sample#equivalentTimeOfDay}) is the vanilla time of day that would put the sun at the same height, for
 * consumers that only use {@code cos(2π·timeOfDay)} (sky darkening, star brightness, sky colours).
 */
public final class LocalSky {

    /** Sun height above which a clear sky counts as day ({@code skyDarken < 4}). */
    public static final double DAY_SUN_HEIGHT = (7.0 / 11.0 - 0.5) / 2.0;

    private static final Map<CubeSettings.SunAxis, CubeSun> SUNS = new EnumMap<>(CubeSettings.SunAxis.class);

    static {
        for (CubeSettings.SunAxis axis : CubeSettings.SunAxis.values()) SUNS.put(axis, new CubeSun(axis));
    }

    private LocalSky() {
    }

    /**
     * The sun at one position. {@code face} is null where vanilla's sun applies. Latitude is in radians and the time
     * zone in turns behind UP; the sun's direction is in the face's (storage) axes.
     */
    public record Sample(@Nullable CubeFace face, double latitude, double timeZone, double clock, double timeOfDay,
                         double sunX, double sunY, double sunZ, double equivalentTimeOfDay) {

        /** Day number of the local clock. */
        public long day() {
            return (long) Math.floor(this.clock / 24000.0);
        }

        /** Sun altitude above the horizon, in radians. */
        public double altitude() {
            return Math.asin(Math.max(-1.0, Math.min(1.0, this.sunY)));
        }

        /** Sun azimuth as a compass bearing (north −Z 0, east +X π/2), in radians in [0, 2π). */
        public double azimuth() {
            double azimuth = Math.atan2(this.sunX, -this.sunZ);
            return azimuth < 0.0 ? azimuth + 2.0 * Math.PI : azimuth;
        }
    }

    public static CubeSun sun(CubeSettings.SunAxis axis) {
        return SUNS.get(axis);
    }

    // ---- Pure math ----

    /** Vanilla's eased time of day ({@code DimensionType.timeOfDay}) for a (fractional) clock value. */
    public static double timeOfDay(double clock) {
        double turns = clock / 24000.0 - 0.25;
        double d0 = turns - Math.floor(turns);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (d0 * 2.0 + d1) / 3.0;
    }

    /**
     * The vanilla time of day that would put the sun at height {@code sunY}: {@code acos(sunY)/2π} after noon,
     * mirrored before it.
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
        return new Sample(null, 0.0, 0.0, dayTime, timeOfDay, -Math.sin(hourAngle), Math.cos(hourAngle), 0.0, timeOfDay);
    }

    /** A face's sun for a global day time. */
    public static Sample sample(CubeSun sun, CubeFace face, double dayTime) {
        double hourAngle = CubeSun.hourAngle(dayTime);
        double[] direction = sun.direction(face, hourAngle);
        double clock = sun.localClock(face, dayTime);
        return new Sample(face, sun.latitude(face), sun.timeZone(face), clock, timeOfDay(clock), direction[0], direction[1], direction[2],
            equivalentTimeOfDay(sun.rising(face, hourAngle), direction[1]));
    }

    /**
     * A sample part way between two: {@code weight} 1 is {@code from}, 0 is {@code to}. The sun's direction blends
     * along the arc between the two (each in its own face's axes), the clock the short way round the day; the face is
     * {@code to}'s. Used to ease the sky from one face's daytime to the next when crossing an edge.
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
        return new Sample(to.face, to.latitude + (from.latitude - to.latitude) * weight, to.timeZone + (from.timeZone - to.timeZone) * weight,
            clock, timeOfDay(clock), x, y, z, equivalentTimeOfDay(rising, y));
    }

    // ---- Level adapters ----

    /** Whether the local sky applies at all: the overworld, without fixed time. Elsewhere everything is vanilla. */
    public static boolean active(Level level) {
        return level.dimension() == Level.OVERWORLD && !level.dimensionType().hasFixedTime();
    }

    /** Whether positions have their own sun: the local sky applies and the world is a cube. */
    public static boolean local(Level level) {
        return active(level) && Cube.of(level) != null;
    }

    /** The face whose sun shines at a storage position; UP between faces. Null outside a cube world. */
    @Nullable
    public static CubeFace face(Level level, double x, double z) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return null;
        CubeFace face = geometry.faceAt(x, z);
        return face == null ? CubeFace.UP : face;
    }

    /** The sun at a position, or vanilla's when the local sky does not apply. */
    public static Sample sample(Level level, double x, double z) {
        if (!local(level)) return vanilla(level.getDayTime());
        CubeGeometry geometry = Cube.of(level);
        return sample(sun(geometry.settings.sunAxis()), face(level, x, z), level.getDayTime());
    }

    /** The sun of a given face of a cube world. */
    public static Sample sample(Level level, CubeFace face) {
        return sample(sun(Cube.of(level).settings.sunAxis()), face, level.getDayTime());
    }

    /** The rotation the sky renderer draws the sun, moon and stars with, for the face at a position. */
    public static Quaternionf celestialRotation(Level level, double x, double z) {
        CubeGeometry geometry = Cube.of(level);
        return sun(geometry.settings.sunAxis()).celestialRotation(face(level, x, z), CubeSun.hourAngle(level.getDayTime()));
    }

    /** The local clock at a position, rounded to whole ticks for clock-shaped consumers. */
    public static long localDayTime(Level level, double x, double z) {
        if (!local(level)) return level.getDayTime();
        return Math.round(sample(level, x, z).clock());
    }

    /**
     * Sky darkening at a position, as {@code Level.getSkyDarken()} would be if the whole world shared its sun.
     * Vanilla's own value outside a cube world, so gameplay there is exactly vanilla.
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
     * Where skipping the night lands: the next morning on the face where most sleepers are (ties to the lowest slot),
     * as a global day time. Null if nobody is sleeping or the local sky does not apply.
     */
    @Nullable
    public static Long morningAfterSleep(ServerLevel level) {
        if (!local(level)) return null;
        int[] sleepers = new int[6];
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()) sleepers[face(level, player.getX(), player.getZ()).slot()]++;
        }
        int best = 0;
        for (int slot = 1; slot < 6; slot++) {
            if (sleepers[slot] > sleepers[best]) best = slot;
        }
        if (sleepers[best] == 0) return null;
        return sun(Cube.of(level).settings.sunAxis()).nextMorning(CubeFace.bySlot(best), level.getDayTime());
    }
}
