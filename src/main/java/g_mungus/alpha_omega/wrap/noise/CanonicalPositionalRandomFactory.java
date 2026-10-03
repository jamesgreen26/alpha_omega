package g_mungus.alpha_omega.wrap.noise;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** A positional random keyed by block position that gives every image of a block the same sequence. */
public final class CanonicalPositionalRandomFactory implements PositionalRandomFactory {

    private final PositionalRandomFactory delegate;

    public CanonicalPositionalRandomFactory(PositionalRandomFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        return this.delegate.at(Wrap.canonBlock(x), y, Wrap.canonBlock(z));
    }

    @Override
    public RandomSource fromHashOf(String name) {
        return this.delegate.fromHashOf(name);
    }

    @Override
    public RandomSource fromSeed(long seed) {
        return this.delegate.fromSeed(seed);
    }

    @Override
    public void parityConfigString(StringBuilder builder) {
        this.delegate.parityConfigString(builder);
    }
}
