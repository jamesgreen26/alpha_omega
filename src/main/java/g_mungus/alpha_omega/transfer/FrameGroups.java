package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.band.CopyLinks;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Who shares a frame (RS §4.2–4.3), every {@link #PERIOD} ticks:
 * <ul>
 * <li><b>Player groups.</b> Players near the seams within {@code 2R} of each other in the world are grouped
 * (union-find). A group takes one frame valid for every member ({@link Frames#groupFrame}: each within {@code C} past
 * the seams), preferring the one that moves the fewest. If there is none, nobody moves.</li>
 * <li><b>Followers.</b> A non-player entity near the seams joins the frame of the nearest player within {@code R}, if
 * its position is valid there (within {@code H}, less a margin). At most once per
 * {@link FrameTransfer#FOLLOW_COOLDOWN_TICKS} per entity, and never while it is anchored.</li>
 * </ul>
 * Also the <b>interaction pull</b> ({@link #beforeUse}): a player using a block whose owner is another copy moves, with
 * its group, into the owner's frame first, if that is valid for all of them.
 */
public final class FrameGroups {

    /** Ticks between group and follower checks. */
    public static final int PERIOD = 20;
    /** How far short of {@code H} a follower must land, so it does not cross straight back at {@code H}. */
    public static final double FOLLOW_MARGIN = 2.0;

    /** Per level, the player groups found at the last check (each sorted by id). */
    private static final Map<ServerLevel, List<List<ServerPlayer>>> GROUPS = new WeakHashMap<>();

    private FrameGroups() {
    }

    /** Once per level tick. */
    public static void tick(ServerLevel level) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null || level.getGameTime() % PERIOD != 0) return;
        groups(level, geometry);
        followers(level, geometry);
    }

    /** Players that can take part in a group: alive, not spectating, near a seam. */
    private static List<ServerPlayer> candidates(ServerLevel level, OrbifoldGeometry geometry) {
        double reach = 2.0 * OrbifoldGeometry.INTERACTION_RADIUS;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (geometry.seamDepth(player.getX(), player.getZ()) < -reach) continue;
            players.add(player);
        }
        players.sort(Comparator.comparing(Entity::getUUID));
        return players;
    }

    /** Union-find over players within {@code 2R} of each other in the world. */
    static List<List<ServerPlayer>> findGroups(OrbifoldGeometry geometry, List<ServerPlayer> players) {
        int n = players.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double link = 2.0 * OrbifoldGeometry.INTERACTION_RADIUS;
        for (int i = 0; i < n; i++) {
            Vec3 a = players.get(i).position();
            for (int j = i + 1; j < n; j++) {
                Vec3 b = players.get(j).position();
                if (Frames.distance(geometry, b.x, b.z, a.x, a.z) <= link) parent[find(parent, i)] = find(parent, j);
            }
        }
        List<List<ServerPlayer>> groups = new ArrayList<>();
        Map<Integer, List<ServerPlayer>> byRoot = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(players.get(i));
        groups.addAll(byRoot.values());
        return groups;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void groups(ServerLevel level, OrbifoldGeometry geometry) {
        List<List<ServerPlayer>> groups = findGroups(geometry, candidates(level, geometry));
        GROUPS.put(level, groups);
        for (List<ServerPlayer> group : groups) {
            if (group.size() < 2) continue;
            double[][] positions = new double[group.size()][];
            for (int i = 0; i < group.size(); i++) positions[i] = new double[] {group.get(i).getX(), group.get(i).getZ()};
            Motion[] frame = Frames.groupFrame(geometry, positions, geometry.claim, 2.0 * OrbifoldGeometry.INTERACTION_RADIUS);
            if (frame == null) continue;
            for (int i = 0; i < group.size(); i++) {
                if (frame[i].isIdentity()) continue;
                ServerPlayer player = group.get(i);
                Entity root = player.getRootVehicle();
                if (FrameTransfer.coolingDown(root, FrameTransfer.FOLLOW_COOLDOWN_TICKS) || FrameTransfer.carried(root)
                    || !FrameTransfer.destinationLoaded(root, frame[i])) continue;
                TransferCounters.count(TransferCounters.Kind.GROUP);
                FrameTransfers.transfer(level, root, frame[i], null);
            }
        }
    }

    /** Whether an entity can follow players between frames: not a player or carrying one, not fixed to a block. */
    private static boolean canFollow(Entity entity) {
        return !entity.isRemoved() && !entity.isPassenger() && !(entity instanceof BlockAttachedEntity) && !FrameTransfer.carriesPlayer(entity)
            && !entity.isSpectator();
    }

    private static void followers(ServerLevel level, OrbifoldGeometry geometry) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && geometry.seamDepth(player.getX(), player.getZ()) > -(geometry.band + OrbifoldGeometry.INTERACTION_RADIUS)) {
                players.add(player);
            }
        }
        if (players.isEmpty()) return;
        double limit = geometry.band - FOLLOW_MARGIN;
        double radius = OrbifoldGeometry.INTERACTION_RADIUS;
        List<Entity> movers = new ArrayList<>();
        List<Motion> motions = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player || !canFollow(entity)) continue;
            // Only things near a seam have another expression to join.
            if (geometry.seamDepth(entity.getX(), entity.getZ()) < -limit) continue;
            if (FrameTransfer.coolingDown(entity, FrameTransfer.FOLLOW_COOLDOWN_TICKS) || FrameTransfer.anchored(entity) || FrameTransfer.carried(entity)) {
                continue;
            }
            Frames.Expression best = null;
            double bestDistance = radius * radius;
            for (ServerPlayer player : players) {
                Frames.Expression e = Frames.nearest(geometry, entity.getX(), entity.getZ(), limit, player.getX(), player.getZ());
                double d = e.distanceSqr(player.getX(), player.getZ());
                if (d <= bestDistance) {
                    best = e;
                    bestDistance = d;
                }
            }
            if (best == null || best.motion().isIdentity() || !FrameTransfer.destinationLoaded(entity, best.motion())) continue;
            movers.add(entity);
            motions.add(best.motion());
        }
        Set<Entity> moved = new HashSet<>();
        for (int i = 0; i < movers.size(); i++) {
            Entity entity = movers.get(i);
            if (!moved.add(entity) || entity.isRemoved()) continue;
            TransferCounters.count(TransferCounters.Kind.FOLLOWER);
            FrameTransfers.transfer(level, entity, motions.get(i), null);
        }
    }

    /** The group a player was in at the last check, itself alone if none. */
    public static List<ServerPlayer> groupOf(ServerPlayer player) {
        List<List<ServerPlayer>> groups = GROUPS.get(player.serverLevel());
        if (groups != null) {
            for (List<ServerPlayer> group : groups) {
                if (group.contains(player)) return group;
            }
        }
        return List.of(player);
    }

    /**
     * Before a player uses a block: if another copy of the cell owns it, the player's group moves into the owner's
     * frame first, so menus, range checks and mod packets see one frame. Only if that is valid for every member
     * (within {@code C + ½}, where none of them would cross straight back). Returns the element they moved by, or null.
     */
    @Nullable
    public static Motion beforeUse(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null || !level.isLoaded(pos)) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        CopyLinks.Link owner = Ownership.owner(level, chunk, pos);
        if (owner == null) return null;
        Motion m = owner.motion;
        Transform transform = Transform.of(m);
        List<Entity> roots = new ArrayList<>();
        for (ServerPlayer member : groupOf(player)) {
            if (member.isRemoved() || member.level() != level) continue;
            Entity root = member.getRootVehicle();
            if (roots.contains(root)) continue;
            Vec3 to = transform.position(root.position());
            if (!Frames.valid(geometry, to.x, to.z, FrameTransfer.playerDepth(geometry)) || FrameTransfer.carried(root)
                || !FrameTransfer.destinationLoaded(root, m)) {
                return null;
            }
            roots.add(root);
        }
        for (Entity root : roots) {
            TransferCounters.count(TransferCounters.Kind.PULL);
            FrameTransfers.transfer(level, root, m, null);
        }
        return m;
    }
}
