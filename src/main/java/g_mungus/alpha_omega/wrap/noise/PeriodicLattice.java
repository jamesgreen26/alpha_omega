package g_mungus.alpha_omega.wrap.noise;

/** Duck interface on {@code ImprovedNoise}: makes one octave periodic in its own input coordinates. */
public interface PeriodicLattice {

    /**
     * Each argument is the required period along that axis in the octave's input units, or 0 for no wrapping.
     * A lattice can only repeat after a whole number of cells, so the period is rounded to {@code L >= 1} cells
     * and the input is stretched by {@code L / period} to compensate.
     */
    void alpha_omega$setPeriod(double px, double py, double pz);

    /** The period along x in this octave's input units, or 0 if it does not wrap. */
    double alpha_omega$inputPeriodX();

    /** The period along z in this octave's input units, or 0 if it does not wrap. */
    double alpha_omega$inputPeriodZ();

    /** Whether {@code other} wraps with exactly the same periods (for equality checks that ignore them). */
    boolean alpha_omega$samePeriods(PeriodicLattice other);
}
