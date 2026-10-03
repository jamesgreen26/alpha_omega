package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R1/R2: scheduled ticks are stored and looked up at canonical positions. */
@Mixin(LevelTicks.class)
abstract class LevelTicksMixin<T> {

    @ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
    private ScheduledTick<T> alpha_omega$canonTick(ScheduledTick<T> tick) {
        BlockPos pos = Wrap.canon(tick.pos());
        return pos == tick.pos() ? tick : new ScheduledTick<>(tick.type(), pos, tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }

    @ModifyVariable(method = {"hasScheduledTick", "willTickThisTick"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonPos(BlockPos pos) {
        return Wrap.canon(pos);
    }
}
