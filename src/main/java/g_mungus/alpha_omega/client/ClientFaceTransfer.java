package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The local player crosses edges itself, as it moves itself (design §5.2): once it (or the vehicle it drives) is past
 * the diagonal, it moves to the same cube point in the next face's storage, upright, and tells the server. Any change
 * of face, including one the server makes, eases the camera over.
 */
public final class ClientFaceTransfer {

    private static CubeFace lastFace;
    private static Vec3 lastEye = Vec3.ZERO;

    private ClientFaceTransfer() {
    }

    public static void afterTick(LocalPlayer player) {
        FaceCamera.tick();
        TransferStats.tick();
        CubeGeometry geometry = Cube.of(player.level());
        if (geometry == null) {
            lastFace = null;
            return;
        }
        Entity root = player.getRootVehicle();
        if (root == player || root.isControlledByLocalInstance()) {
            CubeFace face = geometry.faceAt(root.getX(), root.getZ());
            CubeFace to = face == null ? null : FaceTransfer.destination(geometry, face, root.getX(), root.getY(), root.getZ());
            if (to != null && FaceTransfer.roomToCross(geometry, root, face, to)) cross(geometry, player, root, face, to);
        }
        CubeFace now = geometry.faceAt(player.getX(), player.getZ());
        if (lastFace != null && now != null && now != lastFace) {
            FaceCamera.start(geometry, lastFace, now, lastEye, player.getEyePosition());
            TransferStats.faceChanged();
        }
        lastFace = now;
        lastEye = player.getEyePosition();
    }

    private static void cross(CubeGeometry geometry, LocalPlayer player, Entity root, CubeFace from, CubeFace to) {
        Vec3 claimed = root.position();
        float yRot = player.getYRot(), xRot = player.getXRot();
        Vec3 eyeBefore = player.getEyePosition();
        for (Entity entity : root.getSelfAndPassengers().toList()) {
            if (entity != root && entity != player) continue;
            double[] p = geometry.transform(from, to, entity.getX(), entity.getY(), entity.getZ());
            FaceTransfer.Mode mode = FaceTransfer.mode(entity);
            Vec3 motion = entity.getDeltaMovement();
            double[] v = FaceTransfer.rotate(mode, from, to, motion.x, motion.y, motion.z);
            float[] rotation = FaceTransfer.rotateLook(mode, from, to, entity.getYRot(), entity.getXRot());
            entity.setPos(p[0], p[1], p[2]);
            entity.setYRot(rotation[0]);
            entity.setXRot(rotation[1]);
            entity.setOldPosAndRot();
            entity.setDeltaMovement(v[0], v[1], v[2]);
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = living.yBodyRotO = rotation[0];
                living.yHeadRot = living.yHeadRotO = rotation[0];
            }
            if (entity == player) {
                player.yBob = player.yBobO = rotation[0];
                player.xBob = player.xBobO = rotation[1];
            }
        }
        PacketDistributor.sendToServer(new FaceTransferPayload(from.slot(), to.slot(), claimed.x, claimed.y, claimed.z, yRot, xRot));
        FaceCamera.start(geometry, from, to, eyeBefore, player.getEyePosition());
        TransferStats.faceChanged();
        lastFace = to;
        lastEye = player.getEyePosition();
    }
}
