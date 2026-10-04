package g_mungus.alpha_omega.sky;

/**
 * Single scattering plus Hillaire's multiple-scattering approximation, evaluated on the CPU. It builds the same two
 * lookup tables as the GPU sky (transmittance to the top of the atmosphere, and the multiple-scattering transfer) and
 * ray-marches a view ray the same way, so colours computed here (fog, clouds, the vanilla sky colour) match the sky
 * drawn on the GPU.
 *
 * <p>Directions are unit vectors with +Y up; the planet's centre is straight below the viewer. Radiance is for a
 * light of illuminance 1 (the sun; the moon is the same light scaled down, from the opposite side).
 */
public final class AtmosphereModel {

    public static final int TRANSMITTANCE_WIDTH = 256;
    public static final int TRANSMITTANCE_HEIGHT = 64;
    public static final int TRANSMITTANCE_STEPS = 40;
    public static final int MULTI_SCATTERING_SIZE = 32;
    public static final int MULTI_SCATTERING_DIRECTIONS = 8;
    public static final int MULTI_SCATTERING_STEPS = 20;
    public static final int VIEW_STEPS = 30;

    private static final double PI = Math.PI;

    public final AtmosphereParams params;
    private final float[] transmittance = new float[TRANSMITTANCE_WIDTH * TRANSMITTANCE_HEIGHT * 3];
    private final float[] multiScattering = new float[MULTI_SCATTERING_SIZE * MULTI_SCATTERING_SIZE * 3];

    public AtmosphereModel(AtmosphereParams params) {
        this.params = params;
        buildTransmittance();
        buildMultiScattering();
    }

    // ---- Geometry ----

    /** Distance from radius {@code r} along a ray with cosine {@code mu} (to the vertical) to a sphere, or -1. */
    static double distanceToSphere(double r, double mu, double radius, boolean nearest) {
        double discriminant = r * r * (mu * mu - 1.0) + radius * radius;
        if (discriminant < 0.0) return -1.0;
        double root = Math.sqrt(discriminant);
        double far = -r * mu + root;
        double near = -r * mu - root;
        if (nearest) return near >= 0.0 ? near : far >= 0.0 ? far : -1.0;
        return far >= 0.0 ? far : -1.0;
    }

    /** Whether a ray from radius {@code r} with cosine {@code mu} hits the ground. */
    boolean hitsGround(double r, double mu) {
        return mu < 0.0 && r * r * (mu * mu - 1.0) + this.params.groundRadius() * this.params.groundRadius() >= 0.0;
    }

    // ---- Transmittance LUT (Bruneton's parameterisation) ----

    /** Maps (r, mu) to the transmittance table's unit coordinates (x for mu, y for r). */
    public static double[] transmittanceUv(AtmosphereParams p, double r, double mu) {
        double h = Math.sqrt(p.topRadius() * p.topRadius() - p.groundRadius() * p.groundRadius());
        double rho = Math.sqrt(Math.max(0.0, r * r - p.groundRadius() * p.groundRadius()));
        double discriminant = r * r * (mu * mu - 1.0) + p.topRadius() * p.topRadius();
        double d = Math.max(0.0, -r * mu + Math.sqrt(Math.max(0.0, discriminant)));
        double dMin = p.topRadius() - r;
        double dMax = rho + h;
        return new double[] {(d - dMin) / (dMax - dMin), rho / h};
    }

    /** The inverse of {@link #transmittanceUv}: {r, mu}. */
    public static double[] transmittanceRMu(AtmosphereParams p, double x, double y) {
        double h = Math.sqrt(p.topRadius() * p.topRadius() - p.groundRadius() * p.groundRadius());
        double rho = h * y;
        double r = Math.sqrt(rho * rho + p.groundRadius() * p.groundRadius());
        double dMin = p.topRadius() - r;
        double dMax = rho + h;
        double d = dMin + x * (dMax - dMin);
        double mu = d == 0.0 ? 1.0 : (h * h - rho * rho - d * d) / (2.0 * r * d);
        return new double[] {r, Math.max(-1.0, Math.min(1.0, mu))};
    }

    private void buildTransmittance() {
        double[] extinction = new double[3];
        for (int j = 0; j < TRANSMITTANCE_HEIGHT; j++) {
            for (int i = 0; i < TRANSMITTANCE_WIDTH; i++) {
                double[] rMu = transmittanceRMu(this.params, i / (TRANSMITTANCE_WIDTH - 1.0), j / (TRANSMITTANCE_HEIGHT - 1.0));
                double r = rMu[0];
                double mu = rMu[1];
                double length = distanceToSphere(r, mu, this.params.topRadius(), false);
                double dt = Math.max(0.0, length) / TRANSMITTANCE_STEPS;
                double[] depth = new double[3];
                for (int s = 0; s < TRANSMITTANCE_STEPS; s++) {
                    double t = (s + 0.5) * dt;
                    double radius = Math.sqrt(t * t + 2.0 * r * mu * t + r * r);
                    this.params.extinction(radius - this.params.groundRadius(), extinction);
                    for (int c = 0; c < 3; c++) depth[c] += extinction[c] * dt;
                }
                int at = (j * TRANSMITTANCE_WIDTH + i) * 3;
                for (int c = 0; c < 3; c++) this.transmittance[at + c] = (float) Math.exp(-depth[c]);
            }
        }
    }

    /** Transmittance from radius {@code r} toward cosine {@code mu} to the top of the atmosphere (ignoring the ground). */
    public void transmittance(double r, double mu, double[] out) {
        double[] uv = transmittanceUv(this.params, r, mu);
        bilinear(this.transmittance, TRANSMITTANCE_WIDTH, TRANSMITTANCE_HEIGHT, uv[0], uv[1], out);
    }

    /** Transmittance toward a light, zero when the planet is in the way. */
    public void lightTransmittance(double r, double mu, double[] out) {
        if (hitsGround(r, mu)) {
            out[0] = out[1] = out[2] = 0.0;
        } else {
            transmittance(r, mu, out);
        }
    }

    // ---- Multiple scattering LUT ----

    private void buildMultiScattering() {
        AtmosphereParams p = this.params;
        int n = MULTI_SCATTERING_DIRECTIONS;
        double[] sun = new double[3];
        double[] lightT = new double[3];
        double[] scattering = new double[3];
        double[] extinction = new double[3];
        for (int j = 0; j < MULTI_SCATTERING_SIZE; j++) {
            double r = p.groundRadius() + (p.topRadius() - p.groundRadius()) * Math.max(1e-4, j / (MULTI_SCATTERING_SIZE - 1.0));
            for (int i = 0; i < MULTI_SCATTERING_SIZE; i++) {
                double muS = i / (MULTI_SCATTERING_SIZE - 1.0) * 2.0 - 1.0;
                sun[0] = Math.sqrt(Math.max(0.0, 1.0 - muS * muS));
                sun[1] = muS;
                sun[2] = 0.0;
                double[] luminance = new double[3];
                double[] transfer = new double[3];
                for (int a = 0; a < n; a++) {
                    for (int b = 0; b < n; b++) {
                        double theta = 2.0 * PI * (a + 0.5) / n;
                        double phi = Math.acos(1.0 - 2.0 * (b + 0.5) / n);
                        double dx = Math.cos(theta) * Math.sin(phi);
                        double dy = Math.cos(phi);
                        double dz = Math.sin(theta) * Math.sin(phi);
                        boolean ground = hitsGround(r, dy);
                        double length = ground ? distanceToSphere(r, dy, p.groundRadius(), true) : distanceToSphere(r, dy, p.topRadius(), false);
                        double dt = Math.max(0.0, length) / MULTI_SCATTERING_STEPS;
                        double[] throughput = {1.0, 1.0, 1.0};
                        for (int s = 0; s < MULTI_SCATTERING_STEPS; s++) {
                            double t = (s + 0.3) * dt;
                            double px = dx * t;
                            double py = r + dy * t;
                            double pz = dz * t;
                            double radius = Math.sqrt(px * px + py * py + pz * pz);
                            double altitude = radius - p.groundRadius();
                            double muSample = (px * sun[0] + py * sun[1] + pz * sun[2]) / radius;
                            lightTransmittance(radius, muSample, lightT);
                            scattering(altitude, scattering);
                            p.extinction(altitude, extinction);
                            for (int c = 0; c < 3; c++) {
                                double step = Math.exp(-extinction[c] * dt);
                                double sigma = Math.max(extinction[c], 1e-12);
                                double source = lightT[c] * scattering[c] / (4.0 * PI);
                                luminance[c] += throughput[c] * (source - source * step) / sigma;
                                transfer[c] += throughput[c] * (scattering[c] - scattering[c] * step) / sigma;
                                throughput[c] *= step;
                            }
                        }
                        if (ground) {
                            double gx = dx * length;
                            double gy = r + dy * length;
                            double gz = dz * length;
                            double radius = Math.sqrt(gx * gx + gy * gy + gz * gz);
                            double cosine = (gx * sun[0] + gy * sun[1] + gz * sun[2]) / radius;
                            transmittance(radius, cosine, lightT);
                            for (int c = 0; c < 3; c++) {
                                luminance[c] += throughput[c] * lightT[c] * Math.max(0.0, cosine) * p.groundAlbedo() / PI;
                            }
                        }
                    }
                }
                int at = (j * MULTI_SCATTERING_SIZE + i) * 3;
                for (int c = 0; c < 3; c++) {
                    double l2 = luminance[c] / (n * n);
                    double fms = transfer[c] / (n * n);
                    this.multiScattering[at + c] = (float) (l2 / (1.0 - fms));
                }
            }
        }
    }

    /** Multiple-scattered radiance per unit scattering coefficient, at radius {@code r} with the light at cosine {@code muS}. */
    public void multiScattering(double r, double muS, double[] out) {
        double u = Math.max(0.0, Math.min(1.0, muS * 0.5 + 0.5));
        double v = Math.max(0.0, Math.min(1.0, (r - this.params.groundRadius()) / (this.params.topRadius() - this.params.groundRadius())));
        bilinear(this.multiScattering, MULTI_SCATTERING_SIZE, MULTI_SCATTERING_SIZE, u, v, out);
    }

    // ---- Scattering ----

    void scattering(double altitude, double[] out) {
        double rayleigh = this.params.rayleighDensity(altitude);
        double mie = this.params.mieDensity(altitude) * this.params.mieScattering();
        for (int c = 0; c < 3; c++) out[c] = this.params.rayleighScattering()[c] * rayleigh + mie;
    }

    public static double rayleighPhase(double cosine) {
        return 3.0 / (16.0 * PI) * (1.0 + cosine * cosine);
    }

    /** Cornette-Shanks phase function. */
    public static double miePhase(double g, double cosine) {
        double k = 3.0 / (8.0 * PI) * (1.0 - g * g) / (2.0 + g * g);
        return k * (1.0 + cosine * cosine) / Math.pow(1.0 + g * g - 2.0 * g * cosine, 1.5);
    }

    /**
     * Radiance reaching a viewer at {@code altitude} km looking along {@code dir}, from a light of illuminance 1 in
     * direction {@code light}. Rays that hit the ground stop there (the ground itself is not added).
     */
    public void radiance(double altitude, double[] dir, double[] light, double[] out) {
        AtmosphereParams p = this.params;
        double r = p.groundRadius() + Math.max(altitude, 1e-3);
        double mu = dir[1];
        double length = hitsGround(r, mu) ? distanceToSphere(r, mu, p.groundRadius(), true) : distanceToSphere(r, mu, p.topRadius(), false);
        out[0] = out[1] = out[2] = 0.0;
        if (length <= 0.0) return;
        double cosine = dir[0] * light[0] + dir[1] * light[1] + dir[2] * light[2];
        double rayleighPhase = rayleighPhase(cosine);
        double miePhase = miePhase(p.mieG(), cosine);
        double[] lightT = new double[3];
        double[] multi = new double[3];
        double[] extinction = new double[3];
        double[] throughput = {1.0, 1.0, 1.0};
        double dt = length / VIEW_STEPS;
        for (int s = 0; s < VIEW_STEPS; s++) {
            double t = (s + 0.3) * dt;
            double px = dir[0] * t;
            double py = r + dir[1] * t;
            double pz = dir[2] * t;
            double radius = Math.sqrt(px * px + py * py + pz * pz);
            double h = radius - p.groundRadius();
            double muLight = (px * light[0] + py * light[1] + pz * light[2]) / radius;
            lightTransmittance(radius, muLight, lightT);
            multiScattering(radius, muLight, multi);
            double rayleigh = p.rayleighDensity(h);
            double mie = p.mieDensity(h) * p.mieScattering();
            p.extinction(h, extinction);
            for (int c = 0; c < 3; c++) {
                double rayleighScattering = p.rayleighScattering()[c] * rayleigh;
                double source = lightT[c] * (rayleighScattering * rayleighPhase + mie * miePhase) + multi[c] * (rayleighScattering + mie);
                double step = Math.exp(-extinction[c] * dt);
                out[c] += throughput[c] * (source - source * step) / Math.max(extinction[c], 1e-12);
                throughput[c] *= step;
            }
        }
    }

    /** Altitude in km for a block height: the sea sits 200 m up, and each block above it adds a metre. */
    public static double altitude(double y, int seaLevel) {
        return 0.2 + Math.max(0.0, y - seaLevel) / 1000.0;
    }

    private static void bilinear(float[] table, int width, int height, double u, double v, double[] out) {
        double x = Math.max(0.0, Math.min(1.0, u)) * (width - 1);
        double y = Math.max(0.0, Math.min(1.0, v)) * (height - 1);
        int x0 = Math.min((int) x, width - 2);
        int y0 = Math.min((int) y, height - 2);
        double fx = x - x0;
        double fy = y - y0;
        for (int c = 0; c < 3; c++) {
            double a = table[(y0 * width + x0) * 3 + c];
            double b = table[(y0 * width + x0 + 1) * 3 + c];
            double d = table[((y0 + 1) * width + x0) * 3 + c];
            double e = table[((y0 + 1) * width + x0 + 1) * 3 + c];
            out[c] = (a * (1 - fx) + b * fx) * (1 - fy) + (d * (1 - fx) + e * fx) * fy;
        }
    }

    /** The raw tables, row-major RGB, for upload to the GPU. */
    public float[] transmittanceTable() {
        return this.transmittance;
    }

    public float[] multiScatteringTable() {
        return this.multiScattering;
    }
}
