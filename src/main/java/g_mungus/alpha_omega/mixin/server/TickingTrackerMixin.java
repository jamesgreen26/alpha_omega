package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TickingTracker.class)
abstract class TickingTrackerMixin {

    @ModifyVariable(method = {
        "addTicket(JLnet/minecraft/server/level/Ticket;)V",
        "removeTicket(JLnet/minecraft/server/level/Ticket;)V",
        "getTicketDebugString(J)Ljava/lang/String;",
    }, at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonKey(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }

    @ModifyVariable(method = "getLevel(Lnet/minecraft/world/level/ChunkPos;)I", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonPos(ChunkPos pos) {
        return Wrap.canon(pos);
    }
}
