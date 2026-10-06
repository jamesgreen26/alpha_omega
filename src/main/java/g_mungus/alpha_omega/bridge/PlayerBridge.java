package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Player proximity (RS §5): the distance from a point to a player is to the nearest image of the point, so a player
 * standing across a seam from a spawner, a trial spawner, a vault, a sound or a particle is as near as it looks. The
 * packets themselves are not changed: they carry the point in its own frame, and the client plays sounds and particles
 * at whichever place it sees that point nearest the camera (phase 6, {@code NeighbourEffects}).
 *
 * <p>Away from the edges each query costs four comparisons on the point, not per player.
 */
public final class PlayerBridge {

    /** Radius used for queries with no limit (despawning, spawn checks): images further than this are not looked at. */
    private static final double UNLIMITED = 128.0;

    private PlayerBridge() {
    }

    /** The images of the ball of {@code radius} round a point, or an empty list (vanilla) if it is away from the edges. */
    public static List<Motion> images(Level level, double x, double z, double radius) {
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return List.of();
        return Images.around(geometry, x, z, radius < 0.0 ? UNLIMITED : radius);
    }

    /** The squared distance from an entity to the nearest of a point and its images. */
    public static double distanceSqr(Entity entity, List<Motion> images, double x, double y, double z) {
        double best = entity.distanceToSqr(x, y, z);
        for (Motion g : images) best = Math.min(best, entity.distanceToSqr(g.pointX(x), y, g.pointZ(z)));
        return best;
    }

    /** {@code EntityGetter.getNearestPlayer(x, y, z, distance, predicate)} with distances to the nearest image. */
    @Nullable
    public static Player nearest(Level level, List<? extends Player> players, double x, double y, double z, double distance,
        @Nullable Predicate<Entity> predicate) {
        List<Motion> images = images(level, x, z, distance);
        double best = -1.0;
        Player nearest = null;
        boolean byImage = false;
        for (Player player : players) {
            if (predicate != null && !predicate.test(player)) continue;
            double direct = player.distanceToSqr(x, y, z);
            double d = images.isEmpty() ? direct : distanceSqr(player, images, x, y, z);
            if ((distance < 0.0 || d < distance * distance) && (best == -1.0 || d < best)) {
                best = d;
                nearest = player;
                byImage = d < direct;
            }
        }
        if (!images.isEmpty()) {
            BridgeCounters.run(BridgeCounters.Kind.PLAYERS, x, z, distance < 0.0 ? UNLIMITED : distance);
            if (byImage) BridgeCounters.found(BridgeCounters.Kind.PLAYERS, 1);
        }
        return nearest;
    }

    /** {@code EntityGetter.hasNearbyAlivePlayer} with distances to the nearest image. */
    public static boolean anyNear(Level level, List<? extends Player> players, double x, double y, double z, double distance) {
        List<Motion> images = images(level, x, z, distance);
        if (!images.isEmpty()) BridgeCounters.run(BridgeCounters.Kind.PLAYERS, x, z, distance < 0.0 ? UNLIMITED : distance);
        for (Player player : players) {
            if (!EntitySelector.NO_SPECTATORS.test(player) || !EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) continue;
            double direct = player.distanceToSqr(x, y, z);
            double d = images.isEmpty() ? direct : distanceSqr(player, images, x, y, z);
            if (distance < 0.0 || d < distance * distance) {
                if (d < direct) BridgeCounters.found(BridgeCounters.Kind.PLAYERS, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * {@code PlayerList.broadcast(except, x, y, z, radius, dimension, packet)} near an edge: sends to every player within
     * {@code radius} of the point or one of its images. Returns false (vanilla runs) if the point is away from the edges.
     */
    public static boolean broadcast(MinecraftServer server, List<ServerPlayer> players, @Nullable Player except, double x, double y, double z,
        double radius, net.minecraft.resources.ResourceKey<Level> dimension, Packet<?> packet) {
        if (dimension != Level.OVERWORLD) return false;
        ServerLevel level = server.overworld();
        if (level == null) return false;
        List<Motion> images = images(level, x, z, radius);
        if (images.isEmpty()) return false;
        BridgeCounters.run(BridgeCounters.Kind.BROADCAST, x, z, radius);
        long byImage = 0;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == except || player.level().dimension() != dimension) continue;
            double direct = player.distanceToSqr(x, y, z);
            double d = distanceSqr(player, images, x, y, z);
            if (d < radius * radius) {
                player.connection.send(packet);
                if (direct >= radius * radius) byImage++;
            }
        }
        BridgeCounters.found(BridgeCounters.Kind.BROADCAST, byImage);
        return true;
    }

    /** Whether a particle at {@code at} is within {@code radius} of a player's block through an image ({@code ServerLevel.sendParticles}). */
    public static boolean particleNearImage(ServerLevel level, BlockPos playerBlock, Position at, double radius) {
        List<Motion> images = images(level, at.x(), at.z(), radius);
        if (images.isEmpty()) return false;
        BridgeCounters.run(BridgeCounters.Kind.BROADCAST, at.x(), at.z(), radius);
        for (Motion g : images) {
            if (playerBlock.closerToCenterThan(new Vec3(g.pointX(at.x()), at.y(), g.pointZ(at.z())), radius)) {
                BridgeCounters.found(BridgeCounters.Kind.BROADCAST, 1);
                return true;
            }
        }
        return false;
    }

    /** A detector function, as trial spawners and vaults call it. */
    @FunctionalInterface
    public interface Detect {

        List<UUID> at(BlockPos pos);
    }

    /**
     * Trial spawner and vault player detection ({@code PlayerDetector.detect}) at a block and at each of its images: the
     * union, in order. Each image search runs in the image's own frame (range and line of sight in its storage).
     */
    public static List<UUID> detect(ServerLevel level, BlockPos pos, double range, Detect detect) {
        List<UUID> direct = detect.at(pos);
        List<Motion> images = images(level, pos.getX() + 0.5, pos.getZ() + 0.5, range + 1.0);
        if (images.isEmpty()) return direct;
        BridgeCounters.run(BridgeCounters.Kind.PLAYERS, pos.getX() + 0.5, pos.getZ() + 0.5, range);
        Set<UUID> all = new LinkedHashSet<>(direct);
        for (Motion g : images) all.addAll(detect.at(Transform.of(g).block(pos)));
        BridgeCounters.found(BridgeCounters.Kind.PLAYERS, all.size() - direct.size());
        return all.size() == direct.size() ? direct : new ArrayList<>(all);
    }
}
