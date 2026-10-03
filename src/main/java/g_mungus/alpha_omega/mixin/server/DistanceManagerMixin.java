package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R1/R2: tickets and player positions are keyed canonically. */
@Mixin(DistanceManager.class)
abstract class DistanceManagerMixin {

    @ModifyVariable(method = {
        "addTicket(JLnet/minecraft/server/level/Ticket;)V",
        "removeTicket(JLnet/minecraft/server/level/Ticket;)V",
        "inEntityTickingRange(J)Z",
        "inBlockTickingRange(J)Z",
        "hasPlayersNearby(J)Z",
        "shouldForceTicks(J)Z",
        "getTicketDebugString(J)Ljava/lang/String;",
    }, at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonKey(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }

    @ModifyVariable(method = {"addPlayer", "removePlayer"}, at = @At("HEAD"), argsOnly = true)
    private SectionPos alpha_omega$canonPlayerSection(SectionPos pos) {
        return Wrap.canon(pos);
    }
}
