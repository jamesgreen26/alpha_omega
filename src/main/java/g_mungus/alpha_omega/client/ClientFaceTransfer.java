package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The local player crosses seams itself, as it moves itself: once it (or the vehicle it drives) is far enough past a
 * seam, it moves by the element of {@code Γ} for its new frame and tells the server. Both frames show the same world,
 * so there is no camera ease: the client just re-expresses its own state.
 *
 * <p>Inactive until phase 5 ({@link FaceTransfer#destination} says nothing crosses).
 */
public final class ClientFaceTransfer {

    private ClientFaceTransfer() {
    }

    public static void afterTick(LocalPlayer player) {
        TransferStats.tick();
        OrbifoldGeometry geometry = Orbifold.of(player.level());
        if (geometry == null) return;
        Entity root = player.getRootVehicle();
        if ((root == player || root.isControlledByLocalInstance()) && !FaceTransfer.coolingDown(root) && !FaceTransfer.carried(root)) {
            Motion g = FaceTransfer.destination(geometry, root.getX(), root.getY(), root.getZ());
            if (g != null && FaceTransfer.roomToCross(root, g)) cross(player, root, g);
        }
    }

    private static void cross(LocalPlayer player, Entity root, Motion g) {
        Vec3 claimed = root.position();
        float yRot = player.getYRot(), xRot = player.getXRot();
        move(player, root, g);
        PacketDistributor.sendToServer(new FaceTransferPayload(g, claimed.x, claimed.y, claimed.z, yRot, xRot));
    }

    /**
     * What carries the local player crossed a seam (a sub-level it stands on): the player goes with it, as on foot,
     * but without telling the server, which has moved its player with the carrier already.
     */
    public static void carriedAcross(LocalPlayer player, Motion g) {
        if (Orbifold.of(player.level()) == null) return;
        move(player, player, g);
    }

    /** Moves the local player (and the vehicle it drives) by {@code g}. */
    private static void move(LocalPlayer player, Entity root, Motion g) {
        Transform transform = Transform.of(g);
        for (Entity entity : root.getSelfAndPassengers().toList()) {
            if (entity != root && entity != player) continue;
            FaceTransfer.startCooldown(entity);
            Vec3 p = transform.position(entity.position());
            Vec3 v = transform.vector(entity.getDeltaMovement());
            float[] rotation = FaceTransfer.rotateLook(g, entity.getYRot(), entity.getXRot());
            entity.setPos(p.x, p.y, p.z);
            entity.setYRot(rotation[0]);
            entity.setXRot(rotation[1]);
            entity.setOldPosAndRot();
            entity.setDeltaMovement(v);
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = living.yBodyRotO = rotation[0];
                living.yHeadRot = living.yHeadRotO = rotation[0];
            }
            if (entity == player) {
                player.yBob = player.yBobO = rotation[0];
                player.xBob = player.xBobO = rotation[1];
            }
        }
        TransferStats.faceChanged();
    }
}
