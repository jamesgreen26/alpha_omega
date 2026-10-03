package g_mungus.alpha_omega.mixin.server.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
abstract class PersistentEntitySectionManagerCallbackMixin {

    @Shadow
    @Final
    private EntityAccess entity;

    @Shadow
    private long currentSectionKey;

    @Unique
    private long alpha_omega$lastLaps = Long.MIN_VALUE;

    /**
     * Moving into another canonical section, or into another lap (crossing the seam, being teleported), may leave
     * the entity outside its island's frame (I4, §6.3). Check it at the end of the tick.
     */
    @Inject(method = "onMove", at = @At("HEAD"))
    private void alpha_omega$checkFrame(CallbackInfo ci) {
        if (!(this.entity instanceof Entity real) || !(real.level() instanceof ServerLevel level)) return;
        BlockPos pos = real.blockPosition();
        long laps = ((long) Math.floorDiv(pos.getX(), Wrap.PERIOD) << 32) | (Math.floorDiv(pos.getZ(), Wrap.PERIOD) & 0xFFFFFFFFL);
        boolean lapChanged = laps != this.alpha_omega$lastLaps;
        this.alpha_omega$lastLaps = laps;
        if (lapChanged || Wrap.canonSectionKey(SectionPos.asLong(pos)) != this.currentSectionKey) {
            IslandManager.of(level).queueFrameCheck(real);
        }
    }

    /** Crossing the seam is not a section change unless the canonical section changes. */
    @ModifyExpressionValue(method = "onMove",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long alpha_omega$canonSection(long sectionKey) {
        return Wrap.canonSectionKey(sectionKey);
    }
}
