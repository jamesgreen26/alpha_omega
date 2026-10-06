package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.network.FrameTransferAckPayload;
import g_mungus.alpha_omega.network.FrameTransferPayload;
import g_mungus.alpha_omega.network.ServerFrameTransferPayload;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.transfer.FrameTransfer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The local player crosses seams itself, as it moves itself (RS §4.2): once it (or the vehicle it drives) is more than
 * {@code C + ½} past a seam, it moves by its position's frame and tells the server ({@link FrameTransferPayload}).
 * Both frames show the same world, so there is no camera ease and no turn: the client re-expresses its own position,
 * yaw and velocity, and the picture is unchanged.
 *
 * <p>When the server moves the player ({@link ServerFrameTransferPayload}), the client applies that element to its
 * current state, first undoing any claim of its own the server had not handled when it moved it (the server drops
 * those), and acknowledges.
 */
public final class ClientFrameTransfer {

    /** A claim sent and not yet known to be handled: its number, element and when it was sent. */
    private record Claim(int number, Motion motion, long tick) {
    }

    /** How long a claim is kept in case a server-driven transfer has to undo it; the server handles claims at once. */
    private static final int CLAIM_MEMORY_TICKS = 200;

    private static final Deque<Claim> PENDING = new ArrayDeque<>();
    private static int claims;
    private static int seen;
    private static long ticks;

    private ClientFrameTransfer() {
    }

    /** A new connection starts both counts again. */
    public static void reset() {
        CloudFrame.reset();
        PENDING.clear();
        claims = 0;
        seen = 0;
    }

    public static void afterTick(LocalPlayer player) {
        TransferStats.tick();
        ticks++;
        for (Iterator<Claim> it = PENDING.iterator(); it.hasNext(); ) {
            if (ticks - it.next().tick > CLAIM_MEMORY_TICKS) it.remove();
        }
        OrbifoldGeometry geometry = Orbifold.of(player.level());
        if (geometry == null) return;
        Entity root = player.getRootVehicle();
        if ((root == player || root.isControlledByLocalInstance()) && !FrameTransfer.coolingDown(root) && !FrameTransfer.carried(root)) {
            Motion g = FrameTransfer.destination(geometry, root.getX(), root.getZ(), FrameTransfer.playerDepth(geometry));
            if (g != null && FrameTransfer.destinationLoaded(root, g)) cross(player, root, g);
        }
    }

    private static void cross(LocalPlayer player, Entity root, Motion g) {
        Vec3 claimed = root.position();
        float yRot = player.getYRot(), xRot = player.getXRot();
        move(player, root, g);
        claims++;
        PENDING.addLast(new Claim(claims, g, ticks));
        PacketDistributor.sendToServer(new FrameTransferPayload(g, claimed.x, claimed.y, claimed.z, yRot, xRot, claims, seen));
    }

    /** The server moved the player: undo the claims it dropped, apply its element, and say so. */
    public static void serverTransfer(ServerFrameTransferPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Motion total = Motion.IDENTITY;
        // Claims after those the server had handled were made in the frame it moved the player out of: undo them,
        // last first.
        for (Iterator<Claim> it = PENDING.descendingIterator(); it.hasNext(); ) {
            Claim claim = it.next();
            if (claim.number > payload.claims()) total = total.then(claim.motion.inverse());
        }
        PENDING.clear();
        total = total.then(payload.motion());
        seen = payload.index();
        if (!total.isIdentity()) move(player, player.getRootVehicle(), total);
        PacketDistributor.sendToServer(new FrameTransferAckPayload(payload.index()));
    }

    /**
     * What carries the local player crossed a seam (a sub-level it stands on): the player goes with it, as on foot,
     * but without telling the server, which has moved its player with the carrier already.
     */
    public static void carriedAcross(LocalPlayer player, Motion g) {
        if (Orbifold.of(player.level()) == null) return;
        move(player, player, g);
    }

    /** Moves the local player and its root vehicle by {@code g}. */
    private static void move(LocalPlayer player, Entity root, Motion g) {
        Transform transform = Transform.of(g);
        for (Entity entity : root.getSelfAndPassengers().toList()) {
            if (entity != root && entity != player) continue;
            FrameTransfer.startCooldown(entity);
            Vec3 p = transform.position(entity.position());
            Vec3 v = transform.vector(entity.getDeltaMovement());
            float[] rotation = FrameTransfer.rotateLook(g, entity.getYRot(), entity.getXRot());
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
            } else if (!entity.isControlledByLocalInstance()) {
                // A vehicle the server moves: drop any interpolation still heading for the old frame.
                entity.lerpTo(p.x, p.y, p.z, rotation[0], rotation[1], 1);
            }
        }
        CloudFrame.transferred(g);
        TransferStats.faceChanged();
    }
}
