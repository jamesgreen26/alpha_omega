package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.noise.PeriodicLattice;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Periodic Perlin octave: lattice indices wrap modulo a whole number of cells, and the input is stretched so
 * that number of cells spans exactly the requested period. Unconfigured instances behave exactly as vanilla.
 */
@Mixin(ImprovedNoise.class)
abstract class ImprovedNoiseMixin implements PeriodicLattice {

    @Unique
    private int alpha_omega$cellsX, alpha_omega$cellsY, alpha_omega$cellsZ;
    @Unique
    private double alpha_omega$stretchX = 1.0, alpha_omega$stretchY = 1.0, alpha_omega$stretchZ = 1.0;

    @Shadow
    private int p(int index) {
        throw new AssertionError();
    }

    @Shadow
    private static double gradDot(int hash, double x, double y, double z) {
        throw new AssertionError();
    }

    @Override
    public void alpha_omega$setPeriod(double px, double py, double pz) {
        this.alpha_omega$cellsX = alpha_omega$cells(px);
        this.alpha_omega$cellsY = alpha_omega$cells(py);
        this.alpha_omega$cellsZ = alpha_omega$cells(pz);
        this.alpha_omega$stretchX = px > 0 ? this.alpha_omega$cellsX / px : 1.0;
        this.alpha_omega$stretchY = py > 0 ? this.alpha_omega$cellsY / py : 1.0;
        this.alpha_omega$stretchZ = pz > 0 ? this.alpha_omega$cellsZ / pz : 1.0;
    }

    @Override
    public double alpha_omega$inputPeriodX() {
        return this.alpha_omega$cellsX == 0 ? 0 : this.alpha_omega$cellsX / this.alpha_omega$stretchX;
    }

    @Override
    public double alpha_omega$inputPeriodZ() {
        return this.alpha_omega$cellsZ == 0 ? 0 : this.alpha_omega$cellsZ / this.alpha_omega$stretchZ;
    }

    @Override
    public boolean alpha_omega$samePeriods(PeriodicLattice other) {
        ImprovedNoiseMixin that = (ImprovedNoiseMixin) other;
        return this.alpha_omega$cellsX == that.alpha_omega$cellsX && this.alpha_omega$cellsY == that.alpha_omega$cellsY
            && this.alpha_omega$cellsZ == that.alpha_omega$cellsZ && this.alpha_omega$stretchX == that.alpha_omega$stretchX
            && this.alpha_omega$stretchY == that.alpha_omega$stretchY && this.alpha_omega$stretchZ == that.alpha_omega$stretchZ;
    }

    @Unique
    private static int alpha_omega$cells(double period) {
        return period > 0 ? Math.max(1, (int) Math.round(period)) : 0;
    }

    @ModifyVariable(method = "noise(DDDDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$stretchX(double x) {
        return x * this.alpha_omega$stretchX;
    }

    // y, and the y smear parameters that are in y units.
    @ModifyVariable(method = "noise(DDDDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double alpha_omega$stretchY(double y) {
        return y * this.alpha_omega$stretchY;
    }

    @ModifyVariable(method = "noise(DDDDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private double alpha_omega$stretchYScale(double yScale) {
        return yScale * this.alpha_omega$stretchY;
    }

    @ModifyVariable(method = "noise(DDDDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private double alpha_omega$stretchYMax(double yMax) {
        return yMax * this.alpha_omega$stretchY;
    }

    @ModifyVariable(method = "noise(DDDDD)D", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double alpha_omega$stretchZ(double z) {
        return z * this.alpha_omega$stretchZ;
    }

    /**
     * @author alpha_omega
     * @reason Wrap lattice indices for periodic octaves. This is the innermost worldgen loop, so it is replaced
     * rather than injected into to avoid a callback allocation per sample. Identical to vanilla when unconfigured.
     */
    @Overwrite
    private double sampleAndLerp(int gridX, int gridY, int gridZ, double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        int x0 = alpha_omega$wrap(gridX, this.alpha_omega$cellsX);
        int x1 = alpha_omega$wrap(gridX + 1, this.alpha_omega$cellsX);
        int y0 = alpha_omega$wrap(gridY, this.alpha_omega$cellsY);
        int y1 = alpha_omega$wrap(gridY + 1, this.alpha_omega$cellsY);
        int z0 = alpha_omega$wrap(gridZ, this.alpha_omega$cellsZ);
        int z1 = alpha_omega$wrap(gridZ + 1, this.alpha_omega$cellsZ);
        int i = this.p(x0);
        int j = this.p(x1);
        int k = this.p(i + y0);
        int l = this.p(i + y1);
        int i1 = this.p(j + y0);
        int j1 = this.p(j + y1);
        double d0 = gradDot(this.p(k + z0), deltaX, weirdDeltaY, deltaZ);
        double d1 = gradDot(this.p(i1 + z0), deltaX - 1.0, weirdDeltaY, deltaZ);
        double d2 = gradDot(this.p(l + z0), deltaX, weirdDeltaY - 1.0, deltaZ);
        double d3 = gradDot(this.p(j1 + z0), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ);
        double d4 = gradDot(this.p(k + z1), deltaX, weirdDeltaY, deltaZ - 1.0);
        double d5 = gradDot(this.p(i1 + z1), deltaX - 1.0, weirdDeltaY, deltaZ - 1.0);
        double d6 = gradDot(this.p(l + z1), deltaX, weirdDeltaY - 1.0, deltaZ - 1.0);
        double d7 = gradDot(this.p(j1 + z1), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ - 1.0);
        double d8 = Mth.smoothstep(deltaX);
        double d9 = Mth.smoothstep(deltaY);
        double d10 = Mth.smoothstep(deltaZ);
        return Mth.lerp3(d8, d9, d10, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    @Unique
    private static int alpha_omega$wrap(int index, int cells) {
        return cells == 0 ? index : Math.floorMod(index, cells);
    }
}
