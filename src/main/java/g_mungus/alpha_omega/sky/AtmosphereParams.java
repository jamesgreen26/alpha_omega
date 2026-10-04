package g_mungus.alpha_omega.sky;

/**
 * The physical atmosphere (Earth-like, after Hillaire 2020, "A Scalable and Production Ready Sky and Atmosphere
 * Rendering Technique"). Lengths are in kilometres and coefficients per kilometre, in linear RGB. The same values are
 * uploaded to the GPU sky, so CPU (fog, cloud and sky colours) and GPU (the sky itself) agree.
 */
public record AtmosphereParams(
    double groundRadius,
    double topRadius,
    double[] rayleighScattering,
    double rayleighScaleHeight,
    double mieScattering,
    double mieExtinction,
    double mieScaleHeight,
    double mieG,
    double[] ozoneAbsorption,
    double ozoneCenter,
    double ozoneHalfWidth,
    double groundAlbedo) {

    public static final AtmosphereParams EARTH = new AtmosphereParams(
        6360.0, 6460.0,
        new double[] {5.802e-3, 13.558e-3, 33.1e-3}, 8.0,
        3.996e-3, 4.40e-3, 1.2, 0.8,
        new double[] {0.650e-3, 1.881e-3, 0.085e-3}, 25.0, 15.0,
        0.3);

    public double rayleighDensity(double altitude) {
        return Math.exp(-altitude / this.rayleighScaleHeight);
    }

    public double mieDensity(double altitude) {
        return Math.exp(-altitude / this.mieScaleHeight);
    }

    public double ozoneDensity(double altitude) {
        return Math.max(0.0, 1.0 - Math.abs(altitude - this.ozoneCenter) / this.ozoneHalfWidth);
    }

    /** Extinction (out-scattering plus absorption) at an altitude, per channel. */
    public void extinction(double altitude, double[] out) {
        double rayleigh = rayleighDensity(altitude);
        double mie = mieDensity(altitude) * this.mieExtinction;
        double ozone = ozoneDensity(altitude);
        for (int c = 0; c < 3; c++) {
            out[c] = this.rayleighScattering[c] * rayleigh + mie + this.ozoneAbsorption[c] * ozone;
        }
    }
}
