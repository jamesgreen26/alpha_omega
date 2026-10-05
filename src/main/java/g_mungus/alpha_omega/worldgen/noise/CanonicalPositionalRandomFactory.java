package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** A positional random keyed by block position that gives every copy of a block cell the sequence of its canonical cell. */
public final class CanonicalPositionalRandomFactory implements PositionalRandomFactory {

    private final PositionalRandomFactory delegate;
    private final OrbifoldGeometry geometry;

    public CanonicalPositionalRandomFactory(PositionalRandomFactory delegate, OrbifoldGeometry geometry) {
        this.delegate = delegate;
        this.geometry = geometry;
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        if (this.geometry.isTile(x, z)) return this.delegate.at(x, y, z);
        OrbifoldGeometry.Cell cell = this.geometry.canon(x, z);
        return this.delegate.at(cell.x(), y, cell.z());
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
