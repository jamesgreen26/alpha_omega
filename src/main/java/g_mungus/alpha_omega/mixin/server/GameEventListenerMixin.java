package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * R5: listeners receive a game event at the image of it nearest themselves. Besides correct directions and travel
 * times, this keeps vibration occlusion checks from tracing a line a whole lap long.
 */
@Mixin(value = {VibrationSystem.Listener.class, SculkCatalystBlockEntity.CatalystListener.class}, targets = "net.minecraft.world.entity.animal.allay.Allay$JukeboxListener")
abstract class GameEventListenerMixin {

    @ModifyVariable(method = "handleGameEvent", at = @At("HEAD"), argsOnly = true)
    private Vec3 alpha_omega$nearestImage(Vec3 eventPos, @Local(argsOnly = true) ServerLevel level) {
        Optional<Vec3> listener = ((GameEventListener) this).getListenerSource().getPosition(level);
        return listener.isPresent() ? Wrap.nearest(eventPos, listener.get()) : eventPos;
    }
}
