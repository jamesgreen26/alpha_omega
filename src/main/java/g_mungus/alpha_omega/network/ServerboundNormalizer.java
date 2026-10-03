package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.mixin.network.PlayerActionPacketAccessor;
import g_mungus.alpha_omega.mixin.network.ServerboundMovePlayerPacketAccessor;
import g_mungus.alpha_omega.mixin.network.ServerboundMoveVehiclePacketAccessor;
import g_mungus.alpha_omega.mixin.network.UseItemOnPacketAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Normalizes serverbound positions to the nearest image of the server-side player, before validation, so the
 * "moved too quickly" and reach checks stay correct unmodified (design doc §8.3).
 */
final class ServerboundNormalizer {

    private ServerboundNormalizer() {
    }

    static void normalize(Packet<?> packet, ServerPlayer player) {
        switch (packet) {
            case ServerboundMovePlayerPacket p when p.hasPosition() -> {
                ServerboundMovePlayerPacketAccessor a = (ServerboundMovePlayerPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(0), player.getX()));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(0), player.getZ()));
            }
            case ServerboundMoveVehiclePacket p -> {
                Entity vehicle = player.getRootVehicle();
                ServerboundMoveVehiclePacketAccessor a = (ServerboundMoveVehiclePacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), vehicle.getX()));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), vehicle.getZ()));
            }
            case ServerboundUseItemOnPacket p -> {
                BlockHitResult hit = p.getHitResult();
                Vec3 location = Wrap.nearest(hit.getLocation(), player.position());
                BlockPos pos = Wrap.nearest(hit.getBlockPos(), player.position());
                if (location != hit.getLocation() || pos != hit.getBlockPos()) {
                    ((UseItemOnPacketAccessor) p).alpha_omega$setBlockHit(hit.getType() == HitResult.Type.MISS
                        ? BlockHitResult.miss(location, hit.getDirection(), pos)
                        : new BlockHitResult(location, hit.getDirection(), pos, hit.isInside()));
                }
            }
            case ServerboundPlayerActionPacket p ->
                ((PlayerActionPacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), player.position()));
            default -> {
            }
        }
    }
}
