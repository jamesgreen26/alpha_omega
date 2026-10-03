package g_mungus.alpha_omega.wrap.noise;

import org.jetbrains.annotations.Nullable;

/** Duck interface on {@code NormalNoise}: the periods it was configured with, so conflicting uses can be detected. */
public interface PeriodicNormalNoise {

    @Nullable
    double[] alpha_omega$getPeriods();

    void alpha_omega$setPeriods(double[] periods);
}
