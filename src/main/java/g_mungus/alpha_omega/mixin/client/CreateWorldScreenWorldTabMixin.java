package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.WorldWrapSettings;
import g_mungus.alpha_omega.wrap.WorldWrapStore;
import java.util.List;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A "World Wrapping" choice on the Create World screen's World tab. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class CreateWorldScreenWorldTabMixin {

    @Unique
    private static final List<Integer> ALPHA_OMEGA_SIZES = List.of(0, 3072, 6144, 12288, 24576, 49152);

    @Unique
    private GridLayout.RowHelper alpha_omega$rows;

    @ModifyExpressionValue(method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"))
    private GridLayout.RowHelper alpha_omega$captureRows(GridLayout.RowHelper rows) {
        this.alpha_omega$rows = rows;
        return rows;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$addWrapButton(CallbackInfo ci) {
        WorldWrapSettings requested = WorldWrapStore.requested();
        int initial = requested != null ? requested.period() : WorldWrapSettings.DEFAULT_PERIOD;
        WorldWrapStore.requestForNextWorld(new WorldWrapSettings(initial, true, false, true));
        CycleButton<Integer> button = CycleButton.<Integer>builder(size -> size == 0
                ? Component.translatable("alpha_omega.wrap.off")
                : Component.translatable("alpha_omega.wrap.size", size))
            .withValues(ALPHA_OMEGA_SIZES)
            .withInitialValue(ALPHA_OMEGA_SIZES.contains(initial) ? initial : WorldWrapSettings.DEFAULT_PERIOD)
            .create(0, 0, 310, 20, Component.translatable("alpha_omega.wrap.label"),
                (b, size) -> WorldWrapStore.requestForNextWorld(new WorldWrapSettings(size, true, false, true)));
        this.alpha_omega$rows.addChild(button, 2);
    }
}
