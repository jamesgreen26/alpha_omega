package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapContext;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-level objects with no route back to their level capture the {@link WrapContext} of the level being
 * constructed. Client-side instances are built outside any context and stay unwrapped.
 */
@Mixin({DistanceManager.class, ChunkTracker.class, LevelTicks.class, EntitySectionStorage.class, LightEngine.class,
    LayerLightSectionStorage.class, SectionStorage.class})
abstract class WrapCapturingMixin implements WrapHolder {

    @Unique
    private Wrap alpha_omega$wrap = Wrap.NONE;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$capture(CallbackInfo ci) {
        this.alpha_omega$wrap = WrapContext.current();
    }

    @Override
    public Wrap alpha_omega$wrap() {
        return this.alpha_omega$wrap;
    }

    @Override
    public void alpha_omega$setWrap(Wrap wrap) {
        this.alpha_omega$wrap = wrap;
    }
}
