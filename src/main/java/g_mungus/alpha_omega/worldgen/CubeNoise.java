package g_mungus.alpha_omega.worldgen;

import com.mojang.serialization.MapCodec;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.worldgen.NoiseAccessor;
import g_mungus.alpha_omega.mixin.worldgen.ShiftAAccessor;
import g_mungus.alpha_omega.mixin.worldgen.ShiftBAccessor;
import g_mungus.alpha_omega.mixin.worldgen.ShiftedNoiseAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Flat world generation on a cube (design §4.2). Vanilla's flat noises (continents, erosion, ridges, climate,
 * jaggedness, and the offsets that warp them) are sampled on the cube's surface ({@link CubeGeometry#surfacePoint}),
 * a 3D point, so they bend over edges without a break: terrain and biomes carry on across, and in the overhang the
 * edge's column extends outward to meet the next face's at the ridge. 3D noises keep their storage coordinates;
 * where the faces meet they disagree, and the barrier pass settles each barrier cell for both faces alike.
 */
public final class CubeNoise {

    private CubeNoise() {
    }

    /**
     * A level's settings with its router sampling flat noises on the cube. The router is rewritten before a
     * {@link net.minecraft.world.level.levelgen.RandomState} is made from it, so the state wires the cube noises like
     * any others, builds the climate sampler biomes use from them, and hands them to whatever compiles the router
     * (C2ME's density function compiler calls them as they are).
     */
    public static NoiseGeneratorSettings onCube(NoiseGeneratorSettings settings, CubeGeometry geometry) {
        return new NoiseGeneratorSettings(settings.noiseSettings(), settings.defaultBlock(), settings.defaultFluid(),
            settings.noiseRouter().mapAll(new Visitor(geometry)), settings.surfaceRule(), settings.spawnTarget(), settings.seaLevel(),
            settings.disableMobGeneration(), settings.aquifersEnabled(), settings.oreVeinsEnabled(), settings.useLegacyRandomSource());
    }

    /** Swaps each flat noise for its cube version. */
    private static final class Visitor implements DensityFunction.Visitor {

        private final CubeGeometry geometry;
        private final Map<DensityFunction, DensityFunction> done = new HashMap<>();

        Visitor(CubeGeometry geometry) {
            this.geometry = geometry;
        }

        @Override
        public DensityFunction apply(DensityFunction function) {
            return this.done.computeIfAbsent(function, this::cube);
        }

        private DensityFunction cube(DensityFunction function) {
            if (function instanceof NoiseAccessor noise && noise.alpha_omega$yScale() == 0.0) {
                return new Flat(this.geometry, noise.alpha_omega$noise(), noise.alpha_omega$xzScale());
            }
            if (function instanceof ShiftedNoiseAccessor shifted && shifted.alpha_omega$yScale() == 0.0) {
                return new FlatShifted(this.geometry, shifted.alpha_omega$shiftX(), shifted.alpha_omega$shiftZ(), shifted.alpha_omega$xzScale(),
                    shifted.alpha_omega$noise());
            }
            if (function instanceof ShiftAAccessor shift) return new FlatShift(this.geometry, shift.alpha_omega$offsetNoise(), false);
            if (function instanceof ShiftBAccessor shift) return new FlatShift(this.geometry, shift.alpha_omega$offsetNoise(), true);
            return function;
        }
    }

    /** The surface point flat noise samples for a column, shrunk sideways by the horizontal scale. */
    static double[] surface(CubeGeometry geometry, DensityFunction.FunctionContext context) {
        double[] s = geometry.surfacePoint(context.blockX(), context.blockZ());
        if (s == null) return null;
        double scale = geometry.settings.horizontalScale();
        if (scale != 1.0) {
            s[0] *= scale;
            s[1] *= scale;
            s[2] *= scale;
        }
        return s;
    }

    /** Never serialised: it only exists inside a running level's router. */
    private static <T extends DensityFunction> KeyDispatchDataCodec<T> unsaved(T function) {
        return KeyDispatchDataCodec.of(MapCodec.unit(function));
    }

    /** A flat noise at the cube surface point of the column, or vanilla's sample between faces. */
    record Flat(CubeGeometry geometry, DensityFunction.NoiseHolder noise, double xzScale) implements DensityFunction {

        @Override
        public double compute(FunctionContext context) {
            double[] s = surface(this.geometry, context);
            if (s == null) return this.noise.getValue(context.blockX() * this.xzScale, 0.0, context.blockZ() * this.xzScale);
            return this.noise.getValue(s[0] * this.xzScale, s[1] * this.xzScale, s[2] * this.xzScale);
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Flat(this.geometry, visitor.visitNoise(this.noise), this.xzScale));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return unsaved(this);
        }
    }

    /**
     * A flat noise warped by two offsets ({@link FlatShift}). The warp runs along the face's own axes, so across an
     * edge its direction turns; the offsets themselves are continuous.
     */
    record FlatShifted(CubeGeometry geometry, DensityFunction shiftX, DensityFunction shiftZ, double xzScale,
                       DensityFunction.NoiseHolder noise) implements DensityFunction {

        @Override
        public double compute(FunctionContext context) {
            double sx = this.shiftX.compute(context);
            double sz = this.shiftZ.compute(context);
            CubeFace face = this.geometry.faceAt(context.blockX(), context.blockZ());
            double[] s = face == null ? null : surface(this.geometry, context);
            if (s == null) return this.noise.getValue(context.blockX() * this.xzScale + sx, 0.0, context.blockZ() * this.xzScale + sz);
            double[] warp = face.toCube(sx, 0.0, sz);
            return this.noise.getValue(s[0] * this.xzScale + warp[0], s[1] * this.xzScale + warp[1], s[2] * this.xzScale + warp[2]);
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new FlatShifted(this.geometry, this.shiftX.mapAll(visitor), this.shiftZ.mapAll(visitor), this.xzScale,
                visitor.visitNoise(this.noise)));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return unsaved(this);
        }
    }

    /** Vanilla's offset noise ({@code shift_a}, or with its axes turned {@code shift_b}) at the cube surface point. */
    record FlatShift(CubeGeometry geometry, DensityFunction.NoiseHolder offsetNoise, boolean turned) implements DensityFunction {

        @Override
        public double compute(FunctionContext context) {
            double[] s = surface(this.geometry, context);
            double x, y, z;
            if (s == null) {
                x = this.turned ? context.blockZ() : context.blockX();
                y = this.turned ? context.blockX() : 0.0;
                z = this.turned ? 0.0 : context.blockZ();
            } else if (this.turned) {
                x = s[2];
                y = s[0];
                z = s[1];
            } else {
                x = s[0];
                y = s[1];
                z = s[2];
            }
            return this.offsetNoise.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new FlatShift(this.geometry, visitor.visitNoise(this.offsetNoise), this.turned));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.offsetNoise.maxValue() * 4.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return unsaved(this);
        }
    }
}
