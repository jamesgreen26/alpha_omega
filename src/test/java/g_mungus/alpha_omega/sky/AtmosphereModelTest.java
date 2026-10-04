package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtmosphereModelTest {

    private static final AtmosphereParams P = AtmosphereParams.EARTH;
    private static final AtmosphereModel MODEL = new AtmosphereModel(P);
    private static final SkyColors COLORS = new SkyColors(MODEL);

    /** A unit direction at an elevation and compass azimuth (north -Z, east +X). */
    private static double[] dir(double elevationDeg, double azimuthDeg) {
        double e = Math.toRadians(elevationDeg);
        double a = Math.toRadians(azimuthDeg);
        return new double[] {Math.cos(e) * Math.sin(a), Math.sin(e), -Math.cos(e) * Math.cos(a)};
    }

    private static double[] dome(double sunElevation, double moon) {
        double[] sun = dir(sunElevation, 90);
        double[] radiance = COLORS.domeRadiance(0.2, sun, moon);
        return SkyColors.display(radiance, COLORS.adaptedExposure(radiance, 1.0));
    }

    @Test
    void transmittanceTableRoundTrips() {
        for (double x = 0.0; x <= 1.0; x += 0.1) {
            for (double y = 0.05; y <= 1.0; y += 0.1) {
                double[] rMu = AtmosphereModel.transmittanceRMu(P, x, y);
                double[] uv = AtmosphereModel.transmittanceUv(P, rMu[0], rMu[1]);
                assertEquals(x, uv[0], 1e-6);
                assertEquals(y, uv[1], 1e-6);
            }
        }
    }

    @Test
    void zenithTransmittanceMatchesAnalyticOpticalDepth() {
        double top = P.topRadius() - P.groundRadius();
        double[] t = new double[3];
        MODEL.transmittance(P.groundRadius(), 1.0, t);
        for (int c = 0; c < 3; c++) {
            double depth = P.rayleighScattering()[c] * P.rayleighScaleHeight() * (1.0 - Math.exp(-top / P.rayleighScaleHeight()))
                + P.mieExtinction() * P.mieScaleHeight() * (1.0 - Math.exp(-top / P.mieScaleHeight()))
                + P.ozoneAbsorption()[c] * P.ozoneHalfWidth();
            assertEquals(Math.exp(-depth), t[c], 0.01 * Math.exp(-depth));
        }
    }

    @Test
    void noonSkyIsVanillaBlue() {
        double[] noon = dome(60, 0.0);
        double[] vanilla = {0x78 / 255.0, 0xA7 / 255.0, 0xFF / 255.0};
        for (int c = 0; c < 3; c++) {
            assertEquals(vanilla[c], noon[c], 0.06, "channel " + c + " of " + noon[0] + " " + noon[1] + " " + noon[2]);
        }
    }

    @Test
    void sunsetIsRedTowardTheSun() {
        double[] sun = dir(2, 90);
        double[] dome = COLORS.domeRadiance(0.2, sun, 0.0);
        double[] toward = SkyColors.display(COLORS.horizonRadiance(0.2, 1, 0, sun, 0.0), COLORS.adaptedExposure(dome, 1.0));
        assertTrue(toward[0] > toward[2] + 0.4, "sunset horizon " + toward[0] + " " + toward[1] + " " + toward[2]);
    }

    @Test
    void skyDarkensAsTheSunSets() {
        double previous = Double.MAX_VALUE;
        for (double elevation = 60; elevation >= -10; elevation -= 2) {
            double luminance = SkyColors.luminance(dome(elevation, 0.0));
            assertTrue(luminance <= previous + 1e-9, "sky brightens at sun elevation " + elevation);
            previous = luminance;
        }
    }

    @Test
    void nightsAreDarkButNotBlackAndStarry() {
        double[] newMoon = dome(-45, 0.0);
        double[] fullMoon = dome(-45, SkyColors.MOON_ILLUMINANCE);
        assertTrue(SkyColors.luminance(newMoon) > 0.01 && SkyColors.luminance(newMoon) < 0.08, "new moon night " + SkyColors.luminance(newMoon));
        assertTrue(SkyColors.luminance(fullMoon) >= SkyColors.luminance(newMoon), "moonlight darkens the sky");
        assertEquals(0.5, SkyColors.starBrightness(newMoon), 1e-9);
        assertEquals(0.0, SkyColors.starBrightness(dome(60, 0.0)), 1e-9);
    }

    @Test
    void cloudsAreWhiteAtNoonAndWarmAtSunset() {
        double[] noon = COLORS.cloudTint(dir(60, 90), 0.0, dome(60, 0.0));
        for (double c : noon) assertTrue(c > 0.97, "noon clouds " + noon[0] + " " + noon[1] + " " + noon[2]);
        double[] sunset = COLORS.cloudTint(dir(4, 90), 0.0, dome(4, 0.0));
        assertTrue(sunset[0] > sunset[2] + 0.15, "sunset clouds " + sunset[0] + " " + sunset[1] + " " + sunset[2]);
    }
}
