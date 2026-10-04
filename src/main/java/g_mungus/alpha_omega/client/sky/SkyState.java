package g_mungus.alpha_omega.client.sky;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.sky.AtmosphereModel;
import g_mungus.alpha_omega.sky.AtmosphereParams;
import g_mungus.alpha_omega.sky.LocalSky;
import g_mungus.alpha_omega.sky.SkyColors;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

/**
 * The atmosphere as seen from the camera this frame: the light directions, the eye's adaptation (smoothed over about a
 * second), and the colours handed to vanilla's sky, fog, clouds and stars.
 */
public final class SkyState {

    /** Seconds for the eye to adapt most of the way to a new brightness. */
    private static final double ADAPTATION_SECONDS = 1.0;

    private static volatile SkyColors colors;
    private static boolean preparing;

    private static long updatedNanos;
    private static Vec3 updatedAt = Vec3.ZERO;
    private static long updatedDayTime = Long.MIN_VALUE;
    private static double exposure = -1.0;
    private static long exposureNanos;

    private static final double[] sun = new double[3];
    private static double moonIlluminance;
    private static double altitude;
    private static double[] domeDisplay = {0.0, 0.0, 0.0};
    private static double[] cloudTint = {1.0, 1.0, 1.0};
    private static double starBrightness;

    private SkyState() {
    }

    /** Builds the atmosphere's lookup tables off the render thread; until they are ready the sky stays vanilla. */
    public static void prepare() {
        if (preparing) return;
        preparing = true;
        Thread thread = new Thread(() -> {
            long start = System.nanoTime();
            colors = new SkyColors(new AtmosphereModel(AtmosphereParams.EARTH));
            AlphaOmegaMod.LOGGER.info("Atmosphere tables built in {} ms", (System.nanoTime() - start) / 1_000_000);
        }, "Alpha Omega atmosphere");
        thread.setDaemon(true);
        thread.start();
    }

    /** Whether the atmosphere colours this level's sky. */
    public static boolean active(Level level) {
        return level != null && colors != null && SkyClientConfig.ATMOSPHERE.get() && LocalSky.active(level)
            && level instanceof ClientLevel client && client.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
            && !ShaderPackCheck.inUse();
    }

    public static SkyColors colors() {
        return colors;
    }

    /** Recompute the frame's values when time has moved on or the camera has moved. */
    private static void update(ClientLevel level) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long now = Util.getNanos();
        if (now - updatedNanos < 4_000_000L && camera.distanceToSqr(updatedAt) < 1.0 && level.getDayTime() == updatedDayTime) return;
        updatedNanos = now;
        updatedAt = camera;
        updatedDayTime = level.getDayTime();

        LocalSky.Sample sample = LocalSky.sample(level, camera.x, camera.z);
        sun[0] = sample.sunX();
        sun[1] = sample.sunY();
        sun[2] = sample.sunZ();
        moonIlluminance = SkyColors.MOON_ILLUMINANCE * DimensionType.MOON_BRIGHTNESS_PER_PHASE[level.getMoonPhase()];
        altitude = AtmosphereModel.altitude(camera.y, level.getSeaLevel());

        double[] dome = colors.domeRadiance(altitude, sun, moonIlluminance);
        double target = colors.adaptedExposure(dome, SkyClientConfig.EXPOSURE.get());
        if (exposure <= 0.0) {
            exposure = target;
        } else {
            double seconds = Math.min(1.0, (now - exposureNanos) / 1e9);
            double blend = 1.0 - Math.exp(-seconds / ADAPTATION_SECONDS * 3.0);
            exposure = Math.exp(Math.log(exposure) + (Math.log(target) - Math.log(exposure)) * blend);
        }
        exposureNanos = now;
        domeDisplay = SkyColors.display(dome, exposure);
        cloudTint = colors.cloudTint(sun, moonIlluminance, domeDisplay);
        starBrightness = SkyColors.starBrightness(domeDisplay);
    }

    /** The colour vanilla paints the sky dome (before rain and lightning). */
    public static Vec3 skyColor(ClientLevel level) {
        update(level);
        return new Vec3(domeDisplay[0], domeDisplay[1], domeDisplay[2]);
    }

    /** The horizon toward a horizontal look direction: the fog colour (before rain, thunder and render-distance blending). */
    public static double[] fogColor(ClientLevel level, double lookX, double lookZ) {
        update(level);
        return SkyColors.display(colors.horizonRadiance(altitude, lookX, lookZ, sun, moonIlluminance), exposure);
    }

    public static double[] cloudTint(ClientLevel level) {
        update(level);
        return cloudTint;
    }

    public static float starBrightness(ClientLevel level) {
        update(level);
        return (float) starBrightness;
    }

    // ---- For the GPU sky ----

    public static double exposure(ClientLevel level) {
        update(level);
        return exposure;
    }

    public static double[] sun(ClientLevel level) {
        update(level);
        return sun;
    }

    public static double moonIlluminance(ClientLevel level) {
        update(level);
        return moonIlluminance;
    }

    public static double altitude(ClientLevel level) {
        update(level);
        return altitude;
    }
}
