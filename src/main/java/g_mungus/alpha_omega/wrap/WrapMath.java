package g_mungus.alpha_omega.wrap;

/**
 * Pure wrapping arithmetic on a single axis. Every other wrapping helper is a thin wrapper over these.
 */
public final class WrapMath {

    private WrapMath() {
    }

    public static int canon(int x, int period) {
        return Math.floorMod(x, period);
    }

    public static int lap(int x, int period) {
        return Math.floorDiv(x, period);
    }

    /** Signed minimal displacement from b to a, in (-period/2, period/2]. */
    public static int minDelta(int a, int b, int period) {
        int d = Math.floorMod(a - b, period);
        return d > period / 2 ? d - period : d;
    }

    /** The image of a nearest to ref. */
    public static int nearestImage(int a, int ref, int period) {
        return ref + minDelta(a, ref, period);
    }

    /** Signed minimal displacement from b to a, in (-period/2, period/2]. */
    public static double minDelta(double a, double b, double period) {
        double d = (a - b) % period;
        if (d < 0) d += period;
        if (d > period / 2) d -= period;
        return d;
    }

    public static double nearestImage(double a, double ref, double period) {
        return ref + minDelta(a, ref, period);
    }
}
