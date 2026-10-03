package g_mungus.alpha_omega.mixin.server.entity;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frame-tagged saves (design doc §10.2). An entity's other saved positions (memories, homes, ...) are in its
 * frame, so the save records that frame's lap; {@code Pos} itself is written canonically so external tools see
 * sane coordinates. Loading restores the tagged frame; adding the entity to the level then moves it into its
 * island's frame through the same translation as a shift.
 */
@Mixin(Entity.class)
abstract class EntityMixin {

    @Unique
    private static final String LAP_TAG = "alpha_omega:Lap";

    @Shadow
    public abstract Level level();

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void alpha_omega$tagFrame(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.level() == null || this.level().isClientSide || !tag.contains("Pos", Tag.TAG_LIST)) return;
        ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
        int lapX = Math.floorDiv((int) Math.floor(pos.getDouble(0)), Wrap.PERIOD);
        int lapZ = Math.floorDiv((int) Math.floor(pos.getDouble(2)), Wrap.PERIOD);
        tag.putIntArray(LAP_TAG, new int[] {lapX, lapZ});
        pos.set(0, DoubleTag.valueOf(pos.getDouble(0) - (double) lapX * Wrap.PERIOD));
        pos.set(2, DoubleTag.valueOf(pos.getDouble(2) - (double) lapZ * Wrap.PERIOD));
    }

    @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
    private CompoundTag alpha_omega$restoreFrame(CompoundTag tag) {
        int[] laps = tag.getIntArray(LAP_TAG);
        if (laps.length != 2 || !tag.contains("Pos", Tag.TAG_LIST)) return tag;
        CompoundTag copy = tag.copy();
        ListTag pos = copy.getList("Pos", Tag.TAG_DOUBLE);
        // Pos may have been rewritten since (e.g. a structure template placing the entity): restore the tagged lap
        // relative to wherever it now is, keeping its canonical position.
        double x = pos.getDouble(0);
        double z = pos.getDouble(2);
        pos.set(0, DoubleTag.valueOf(Wrap.canon(x) + (double) laps[0] * Wrap.PERIOD));
        pos.set(2, DoubleTag.valueOf(Wrap.canon(z) + (double) laps[1] * Wrap.PERIOD));
        return copy;
    }
}
