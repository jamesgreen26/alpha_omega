package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.PoiBridge;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** POI queries see records owned by another copy ({@link PoiBridge}). */
@Mixin(PoiManager.class)
abstract class PoiManagerBridgeMixin {

    @Inject(method = "getInSquare", at = @At("RETURN"), cancellable = true)
    private void alpha_omega$withImages(Predicate<Holder<PoiType>> type, BlockPos center, int radius, PoiManager.Occupancy occupancy,
        CallbackInfoReturnable<Stream<PoiRecord>> cir) {
        Stream<PoiRecord> direct = cir.getReturnValue();
        Stream<PoiRecord> all = PoiBridge.withImages((PoiManager) (Object) this, ((SectionStorageBridgeAccessor) this).alpha_omega$heights(), direct, type,
            center, radius, occupancy);
        if (all != direct) cir.setReturnValue(all);
    }

    @ModifyVariable(method = {"exists", "getType", "release", "getFreeTickets"}, at = @At("HEAD"), argsOnly = true, require = 4)
    private BlockPos alpha_omega$atOwner(BlockPos pos) {
        return PoiBridge.owner(((SectionStorageBridgeAccessor) this).alpha_omega$heights(), pos);
    }
}
