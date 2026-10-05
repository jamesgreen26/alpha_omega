package g_mungus.alpha_omega.worldgen.noise;

/** Duck interface on density functions that sample noise: makes their noise invariant for the scale they sample it at. */
public interface InvariantNoiseUser {

    /** Called once per function while its {@code RandomState} is built, before anything samples it. */
    void alpha_omega$makeInvariant(InvariantNoiseSource source);
}
