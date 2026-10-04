package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.client.sky.ClientSky;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Clocks show the local time where they are held (or framed). */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemProperties$1")
abstract class ClockItemPropertyMixin {

    @WrapOperation(method = "unclampedCall",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$localTime(ClientLevel level, float partialTick, Operation<Float> original,
                                        @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) @Nullable LivingEntity holder) {
        Entity entity = holder != null ? holder : stack.getEntityRepresentation();
        if (entity == null || !ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.at(level, entity.position()).timeOfDay();
    }
}
