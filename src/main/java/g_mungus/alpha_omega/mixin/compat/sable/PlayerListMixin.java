package g_mungus.alpha_omega.mixin.compat.sable;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.Position;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Replaces {@code server.PlayerListMixin} under Sable, which overwrites {@code broadcast} to measure distance with
 * sub-levels: each listener is moved to its image nearest the source before that check (R5).
 */
@Mixin(value = PlayerList.class, priority = 1100)
abstract class PlayerListMixin {

    @ModifyArg(
        method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;distanceSquaredWithSubLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;DDD)D"),
        index = 1)
    private Position alpha_omega$nearestListener(Level level, Position listener, double x, double y, double z) {
        Wrap wrap = Wrap.of(level);
        return new Vec3(wrap.nearest(listener.x(), x), listener.y(), wrap.nearest(listener.z(), z));
    }
}
