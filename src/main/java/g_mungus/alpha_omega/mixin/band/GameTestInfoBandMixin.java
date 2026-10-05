package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCheck;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code -PcheckCopies}: every gametest that passes must also leave every loaded band chunk equal to its copies. */
@Mixin(GameTestInfo.class)
abstract class GameTestInfoBandMixin {

    @Shadow
    public abstract ServerLevel getLevel();

    @Shadow
    public abstract void fail(Throwable error);

    @Shadow
    public abstract Throwable getError();

    @Inject(method = "succeed", at = @At("HEAD"))
    private void alpha_omega$checkCopies(CallbackInfo ci) {
        if (!Band.CHECK_COPIES || this.getError() != null || Band.geometry(this.getLevel()) == null) return;
        BandCheck.Result result = BandCheck.checkLoaded(this.getLevel());
        if (!result.clean()) this.fail(new GameTestAssertException("After the test, " + result));
    }
}
