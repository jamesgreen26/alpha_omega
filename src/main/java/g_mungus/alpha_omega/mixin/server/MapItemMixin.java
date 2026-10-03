package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Map exploration: where the holder is, relative to the map's center, uses the nearest image. */
@Mixin(MapItem.class)
abstract class MapItemMixin {

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double alpha_omega$holderX(Entity holder, Operation<Double> original, @Local(argsOnly = true) MapItemSavedData data) {
        return Wrap.of(data.dimension).nearest(original.call(holder), data.centerX);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double alpha_omega$holderZ(Entity holder, Operation<Double> original, @Local(argsOnly = true) MapItemSavedData data) {
        return Wrap.of(data.dimension).nearest(original.call(holder), data.centerZ);
    }
}
