package g_mungus.alpha_omega.wrap.noise;

/** Duck interface on noise-sampling density functions: configure their noise for the scale they sample at. */
public interface PeriodicNoiseUser {

    void alpha_omega$makePeriodic(PeriodicNoiseSource source);
}
