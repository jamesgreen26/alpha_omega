package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.network.FrameTransferPayload;
import g_mungus.alpha_omega.network.ServerFrameTransferPayload;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of changing frame (RS §4). A transfer moves a group of entities by an element {@code g} of {@code Γ}
 * within the same storage: positions by {@code g}, yaw and velocity turned with it (so projectiles and items keep
 * their world-space velocity), and the positions they hold by {@link FrameTranslators}. The group is a root vehicle
 * with its passengers (the root decides), and whatever is leashed to them.
 *
 * <p>Root entities are checked after they tick: one carrying no player crosses at {@code H}; a vehicle the server moves
 * with a player aboard crosses at {@code C + ½}. Players moving themselves cross on their own client, which tells the
 * server ({@link FrameTransferPayload}); the server checks the claim and applies the same element without a teleport.
 * The server only moves such a player itself if it finds it well past ({@link #FORCE_SLACK}) without a claim.
 *
 * <p>When the server moves a player (a vehicle, a group, an interaction), it tells the client the element
 * ({@link ServerFrameTransferPayload}) rather than a position, and ignores the client's movement until it has applied
 * it. The two sides count each other's transfers, so a claim the client made in the frame the server has just left is
 * dropped by the server and undone by the client: neither side ever teleports the other.
 */
public final class FrameTransfers {

    /** How far a claimed crossing may be from where the server last saw the player. */
    private static final double CLAIM_SLACK = 8.0;
    /** How far past its crossing depth the server lets a player that moves itself go before moving it. */
    public static final double FORCE_SLACK = 4.0;
    /** Ticks the server waits for a client to apply a server-driven transfer before taking its movement again. */
    private static final int ACK_TIMEOUT_TICKS = 100;

    /** Per player: the two sides' transfer counts (main thread). */
    private static final class PlayerState {
        /** Server-driven transfers sent. */
        int sent;
        /** The last of them the client has applied. */
        int acked;
        /** The client's claims handled so far (accepted, rejected or stale). */
        int claims;
        /** Server tick of the last transfer sent, for the timeout. */
        int sentAt;
    }

    private static final Map<ServerPlayer, PlayerState> PLAYERS = new WeakHashMap<>();
    /** Set while this class moves entities, so their own moves are not taken for mod code anchoring them. */
    private static boolean moving;

    private FrameTransfers() {
    }

    private static PlayerState state(ServerPlayer player) {
        return PLAYERS.computeIfAbsent(player, p -> new PlayerState());
    }

    /** Whether this class is moving entities right now. */
    public static boolean moving() {
        return moving;
    }

    /** How many server-driven transfers a player has been sent: a claim must have seen them all. */
    public static int serverTransfers(ServerPlayer player) {
        return state(player).sent;
    }

    /** Whether a player's client has yet to apply a server-driven transfer: its movement is in a frame it has left. */
    public static boolean awaitingAck(ServerPlayer player) {
        PlayerState state = PLAYERS.get(player);
        if (state == null || state.acked >= state.sent) return false;
        if (player.server.getTickCount() - state.sentAt > ACK_TIMEOUT_TICKS) {
            AlphaOmegaMod.LOGGER.warn("{} did not apply frame transfer {}; taking its movement again", player.getGameProfile().getName(), state.sent);
            state.acked = state.sent;
            return false;
        }
        return true;
    }

    public static void handleAck(ServerPlayer player, int index) {
        PlayerState state = state(player);
        if (index > state.acked && index <= state.sent) state.acked = index;
    }

    /** After a root entity ticks: move it (and its group) if it has crossed far enough past a seam. */
    public static void afterTick(ServerLevel level, Entity root) {
        if (root.isRemoved() || root.isPassenger() || FrameTransfer.coolingDown(root) || FrameTransfer.carried(root)) return;
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        boolean player = FrameTransfer.carriesPlayer(root);
        double depth = player ? FrameTransfer.playerDepth(geometry) : FrameTransfer.otherDepth(geometry);
        // A player moving itself (or driving its vehicle) crosses on its client; the server steps in only well past.
        if (player && selfMoved(root)) depth += FORCE_SLACK;
        if (geometry.seamDepth(root.getX(), root.getZ()) <= depth) return;
        Motion g = FrameTransfer.destination(geometry, root.getX(), root.getZ(), depth);
        if (g == null || !FrameTransfer.destinationLoaded(root, g)) return;
        TransferCounters.count(player ? TransferCounters.Kind.PLAYER_SERVER : TransferCounters.Kind.SEAM);
        transfer(level, root, g, null);
    }

    /** Whether a root's movement comes from a client: a player, or a vehicle a player drives. */
    private static boolean selfMoved(Entity root) {
        return root instanceof ServerPlayer || root.getControllingPassenger() instanceof ServerPlayer;
    }

    /**
     * Moves an entity and its group by {@code g} where they are now, as if they had crossed themselves, with what
     * carries them (a Sable sub-level). Players aboard are moved without telling their clients, which move them along
     * with the carrier themselves.
     */
    public static void carry(ServerLevel level, Entity root, Motion g) {
        transfer(level, root, g, null, true);
    }

    /** Moves {@code root}'s group by {@code g}; any player in it that did not claim it is told. */
    public static void transfer(ServerLevel level, Entity root, Motion g, @Nullable ServerPlayer claimant) {
        transfer(level, root, g, claimant, false);
    }

    private static void transfer(ServerLevel level, Entity root, Motion g, @Nullable ServerPlayer claimant, boolean carried) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        Transform transform = Transform.of(g);
        FrameTranslators.Move move = new FrameTranslators.Move(geometry, g);
        List<Entity> group = group(level, root, g, geometry);
        boolean was = moving;
        moving = true;
        try {
            for (Entity entity : group) {
                FrameTransfer.startCooldown(entity);
                Vec3 p = transform.position(entity.position());
                Vec3 v = transform.vector(entity.getDeltaMovement());
                float[] rotation = FrameTransfer.rotateLook(g, entity.getYRot(), entity.getXRot());
                if (entity instanceof ServerPlayer player) {
                    if (player != claimant && !carried) notify(player, g);
                    player.moveTo(p.x, p.y, p.z, rotation[0], rotation[1]);
                    player.setYHeadRot(rotation[0]);
                    player.connection.resetPosition();
                    player.setDeltaMovement(v);
                } else {
                    Vec3 old = new Vec3(entity.xo, entity.yo, entity.zo);
                    entity.moveTo(p.x, p.y, p.z, rotation[0], rotation[1]);
                    // Interpolation runs from where it was, re-expressed: one step, not a streak across storage.
                    Vec3 o = transform.position(old);
                    entity.xo = entity.xOld = o.x;
                    entity.yo = entity.yOld = o.y;
                    entity.zo = entity.zOld = o.z;
                    entity.setYHeadRot(rotation[0]);
                    if (entity instanceof LivingEntity living) living.yBodyRot = living.yBodyRotO = rotation[0];
                    entity.setDeltaMovement(v);
                    entity.hasImpulse = true;
                }
                FrameTranslators.translate(entity, move);
            }
        } finally {
            moving = was;
        }
        for (Entity entity : group) {
            if (entity instanceof ServerPlayer player) level.getChunkSource().move(player);
        }
        if (!was) {
            for (Entity entity : group) {
                if (entity instanceof ServerPlayer player) FrameGroups.afterPlayerMoved(level, player);
            }
        }
    }

    /**
     * What moves with {@code root}: it and its passengers, then whatever is leashed to any of them, and a fence knot
     * one of them is leashed to (with everything else leashed to it), where the move keeps them valid.
     */
    private static List<Entity> group(ServerLevel level, Entity root, Motion g, OrbifoldGeometry geometry) {
        Set<Entity> group = new LinkedHashSet<>(root.getSelfAndPassengers().toList());
        Transform transform = Transform.of(g);
        List<Entity> queue = new ArrayList<>(group);
        for (int i = 0; i < queue.size(); i++) {
            Entity entity = queue.get(i);
            List<Entity> linked = new ArrayList<>();
            if (entity instanceof Leashable leashable && leashable.getLeashHolder() instanceof LeashFenceKnotEntity knot) linked.add(knot);
            for (Entity near : level.getEntities(entity, entity.getBoundingBox().inflate(12.0),
                other -> other instanceof Leashable leashed && leashed.getLeashHolder() == entity)) {
                linked.add(near.getRootVehicle());
            }
            for (Entity other : linked) {
                if (group.contains(other) || other instanceof ServerPlayer || other.getSelfAndPassengers().anyMatch(e -> e instanceof ServerPlayer)) continue;
                Vec3 to = transform.position(other.position());
                if (!Frames.valid(geometry, to.x, to.z, geometry.band)) continue;
                for (Entity member : other.getSelfAndPassengers().toList()) {
                    if (group.add(member)) queue.add(member);
                }
            }
        }
        return new ArrayList<>(group);
    }

    /** Tells a player's client the server moved it by {@code g}; its movement is ignored until it has applied it. */
    private static void notify(ServerPlayer player, Motion g) {
        PlayerState state = state(player);
        state.sent++;
        state.sentAt = player.server.getTickCount();
        PacketDistributor.sendToPlayer(player, new ServerFrameTransferPayload(g, state.sent, state.claims));
    }

    /**
     * A client says its player crossed. A claim made before the client knew of the server's last transfer is dropped
     * (the client undoes it). Otherwise it is accepted if it matches the server's view: the player (or the vehicle it
     * drives) is near the claimed position, and that position crosses by the claimed element. If not, the player is
     * put back where the server has it.
     */
    public static void handleClaim(ServerPlayer player, FrameTransferPayload claim) {
        ServerLevel level = player.serverLevel();
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        PlayerState state = state(player);
        state.claims = Math.max(state.claims, claim.claim());
        if (claim.seen() < state.sent) {
            TransferCounters.count(TransferCounters.Kind.STALE_CLAIM);
            return;
        }
        Entity root = player.getRootVehicle();
        if (root != player && root.getControllingPassenger() != player) {
            reject(player, "it does not drive its vehicle");
            return;
        }
        Vec3 claimed = new Vec3(claim.x(), claim.y(), claim.z());
        if (claimed.distanceToSqr(root.position()) > CLAIM_SLACK * CLAIM_SLACK) {
            reject(player, "it is " + Math.sqrt(claimed.distanceToSqr(root.position())) + " blocks from where it claims");
            return;
        }
        Motion g = FrameTransfer.destination(geometry, claimed.x, claimed.z, FrameTransfer.playerDepth(geometry));
        if (g == null || !g.equals(claim.motion())) {
            reject(player, "its position does not cross by " + claim.motion());
            return;
        }
        // Where the server has it may lag the claim by a tick's movement: it moves from the claimed position.
        root.setPos(claimed.x, claimed.y, claimed.z);
        player.setYRot(claim.yRot());
        player.setXRot(claim.xRot());
        TransferCounters.count(TransferCounters.Kind.PLAYER_CLAIM);
        transfer(level, root, g, player);
    }

    private static void reject(ServerPlayer player, String why) {
        TransferCounters.count(TransferCounters.Kind.REJECTED_CLAIM);
        AlphaOmegaMod.LOGGER.warn("Rejected frame transfer from {}: {}", player.getGameProfile().getName(), why);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    /** Entities in a box, roots only, for group and follower checks. */
    static List<Entity> roots(ServerLevel level, AABB box) {
        return level.getEntities((Entity) null, box, entity -> !entity.isPassenger());
    }
}
