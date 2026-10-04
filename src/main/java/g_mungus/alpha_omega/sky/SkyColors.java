package g_mungus.alpha_omega.sky;

/**
 * Turns atmosphere radiance into the display colours Minecraft works with: sky (the colour vanilla paints the dome),
 * fog (the horizon in the direction of view), clouds and star brightness.
 *
 * <p>Light comes from the sun (illuminance 1), the moon (opposite the sun, much dimmer, by phase) and a faint airglow
 * so that moonless nights are not pitch black. The eye adapts: exposure rises as the sky darkens, up to a limit. The
 * result is tonemapped on luminance with {@code 1 - exp(-exposure·Y)}, keeping the hue (a channel that would exceed 1
 * scales the colour down rather than bleaching it, so sunsets stay orange), then gamma-encoded. The GPU sky applies
 * the same mapping.
 */
public final class SkyColors {

    /** Exposure for a full daylight sky. */
    public static final double EXPOSURE = 30.0;
    /** How far exposure may rise as the sky darkens (the eye adapting), and how quickly with falling luminance. */
    public static final double MAX_ADAPTATION = 6.0;
    public static final double ADAPTATION_POWER = 0.5;
    /** The moon as a fraction of the sun's illuminance at full moon: far brighter than real, so nights are playable. */
    public static final double MOON_ILLUMINANCE = 1.0 / 2500.0;
    /** Airglow and starlight: radiance added everywhere. */
    public static final double[] AIRGLOW = {1.2e-6, 1.6e-6, 3.6e-6};
    /** Elevation of the samples for the vanilla sky dome colour, and of the horizon for fog. */
    public static final double DOME_ELEVATION = Math.toRadians(50.0);
    public static final double HORIZON_ELEVATION = Math.toRadians(2.0);
    /** Altitude of the cloud layer (y = 192) in km. */
    public static final double CLOUD_ALTITUDE = 0.33;

    public final AtmosphereModel model;
    private final double referenceLuminance;

    public SkyColors(AtmosphereModel model) {
        this.model = model;
        double[] noon = {0.0, Math.sin(Math.toRadians(60.0)), Math.cos(Math.toRadians(60.0))};
        this.referenceLuminance = luminance(domeRadiance(0.2, noon, 0.0));
    }

    public static double luminance(double[] rgb) {
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    /** Radiance along a view direction from the sun, the moon ({@code moonIlluminance}, at the antisolar point) and airglow. */
    public double[] radiance(double altitude, double[] dir, double[] sun, double moonIlluminance) {
        double[] out = new double[3];
        this.model.radiance(altitude, dir, sun, out);
        if (moonIlluminance > 0.0) {
            double[] moon = {-sun[0], -sun[1], -sun[2]};
            double[] moonlit = new double[3];
            this.model.radiance(altitude, dir, moon, moonlit);
            for (int c = 0; c < 3; c++) out[c] += moonIlluminance * moonlit[c];
        }
        for (int c = 0; c < 3; c++) out[c] += AIRGLOW[c];
        return out;
    }

    /** Average radiance at the dome elevation over four azimuths. */
    public double[] domeRadiance(double altitude, double[] sun, double moonIlluminance) {
        double[] sum = new double[3];
        double cos = Math.cos(DOME_ELEVATION);
        double sin = Math.sin(DOME_ELEVATION);
        for (int i = 0; i < 4; i++) {
            double azimuth = i * Math.PI / 2.0 + Math.PI / 4.0;
            double[] sample = radiance(altitude, new double[] {cos * Math.sin(azimuth), sin, -cos * Math.cos(azimuth)}, sun, moonIlluminance);
            for (int c = 0; c < 3; c++) sum[c] += sample[c] / 4.0;
        }
        return sum;
    }

    /** The exposure the eye adapts to for a sky of this dome radiance (before any smoothing over time). */
    public double adaptedExposure(double[] domeRadiance, double bias) {
        double adaptation = Math.pow(this.referenceLuminance / Math.max(luminance(domeRadiance), 1e-12), ADAPTATION_POWER);
        return EXPOSURE * bias * Math.max(1.0, Math.min(MAX_ADAPTATION, adaptation));
    }

    public static double[] display(double[] radiance, double exposure) {
        double y = luminance(radiance);
        if (y <= 0.0) return new double[3];
        double scale = (1.0 - Math.exp(-exposure * y)) / y;
        double[] out = new double[3];
        double max = 0.0;
        for (int c = 0; c < 3; c++) {
            out[c] = Math.max(0.0, radiance[c]) * scale;
            max = Math.max(max, out[c]);
        }
        for (int c = 0; c < 3; c++) out[c] = Math.pow(max > 1.0 ? out[c] / max : out[c], 1.0 / 2.2);
        return out;
    }

    /** The horizon toward a horizontal direction (x, z), slightly raised: the fog colour when looking that way. */
    public double[] horizonRadiance(double altitude, double x, double z, double[] sun, double moonIlluminance) {
        double length = Math.hypot(x, z);
        if (length < 1e-6) {
            x = 1.0;
            z = 0.0;
            length = 1.0;
        }
        double cos = Math.cos(HORIZON_ELEVATION);
        return radiance(altitude, new double[] {cos * x / length, Math.sin(HORIZON_ELEVATION), cos * z / length}, sun, moonIlluminance);
    }

    /**
     * Light on the clouds relative to noon: direct sun (and moon) through the atmosphere, plus light from the sky.
     * White at noon, orange at sunset, dim blue-grey at night.
     */
    public double[] cloudTint(double[] sun, double moonIlluminance, double[] domeDisplay) {
        double r = this.model.params.groundRadius() + CLOUD_ALTITUDE;
        double[] direct = new double[3];
        double[] reference = new double[3];
        this.model.lightTransmittance(r, sun[1], direct);
        this.model.transmittance(r, 1.0, reference);
        double[] moonDirect = new double[3];
        this.model.lightTransmittance(r, -sun[1], moonDirect);
        double sunUp = smoothstep(-0.03, 0.12, sun[1]);
        double moonUp = smoothstep(-0.03, 0.12, -sun[1]);
        double[] tint = new double[3];
        for (int c = 0; c < 3; c++) {
            double light = sunUp * direct[c] / reference[c] + moonUp * Math.min(1.0, moonIlluminance * 300.0) * 0.12 * moonDirect[c] / reference[c];
            tint[c] = Math.min(1.0, 0.85 * light + 0.35 * domeDisplay[c] + 0.06);
        }
        return tint;
    }

    /** Vanilla's star brightness (0.5 at night) fading out as the sky brightens. */
    public static double starBrightness(double[] domeDisplay) {
        double fade = Math.max(0.0, Math.min(1.0, 1.0 - (luminance(domeDisplay) - 0.06) / 0.2));
        return 0.5 * fade * fade;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
