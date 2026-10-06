package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.GameEventBridge;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Game events near an edge reach listeners round their images too ({@link GameEventBridge}). */
@Mixin(GameEventDispatcher.class)
abstract class GameEventDispatcherBridgeMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "post", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$withImages(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        if (GameEventBridge.post(this.level, event, pos, context)) ci.cancel();
    }
}
