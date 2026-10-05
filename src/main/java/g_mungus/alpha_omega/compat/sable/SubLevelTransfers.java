package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import g_mungus.alpha_omega.neighbour.ImageViews;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import g_mungus.alpha_omega.transfer.FaceTransfers;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Sub-levels crossing seams. Once a sub-level's pose (the centre of mass) has crossed, it moves by the element of
 * {@code Γ} for its new frame, in the same storage: pose and last pose moved, orientation and velocities turned with
 * it. Entities standing on it, players too, go with it (and do not cross on their own while on it). It crosses once
 * the place it goes to is loaded; jointed sub-levels do not cross yet. Loaded only when Sable is.
 *
 * <p>Inactive until phase 5 ({@link FaceTransfer#destination} says nothing crosses).
 */
public final class SubLevelTransfers {

    /** Ticks after crossing before the same sub-level may cross again. */
    private static final int COOLDOWN_TICKS = FaceTransfer.COOLDOWN_TICKS;

    /** Per level: when each sub-level last crossed (game time). */
    private static final Map<ServerLevel, Map<UUID, Long>> CROSSED = new WeakHashMap<>();

    private SubLevelTransfers() {
    }

    /** After a level's physics tick: moves every sub-level whose pose has crossed into another frame. */
    public static void afterPhysics(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        Map<UUID, Long> crossed = CROSSED.computeIfAbsent(level, l -> new HashMap<>());
        long now = level.getGameTime();
        crossed.values().removeIf(at -> now - at > COOLDOWN_TICKS);
        for (ServerSubLevel subLevel : List.copyOf(container.getAllSubLevels())) {
            if (subLevel.isRemoved() || crossed.containsKey(subLevel.getUniqueId())) continue;
            Vector3d position = subLevel.logicalPose().position();
            Motion g = FaceTransfer.destination(geometry, position.x, position.y, position.z);
            if (g == null) continue;
            // Jointed sub-levels would have to cross together, anchors and all: not yet.
            if (SubLevelHelper.getConnectedChain(subLevel).size() > 1) continue;
            // Until it can carry on there, it carries on here, in the band.
            if (!destinationReady(level, subLevel, g)) continue;
            transfer(level, system, subLevel, g);
            crossed.put(subLevel.getUniqueId(), now);
        }
    }

    /** Keeps the area a sub-level crosses into loaded while it is on its way, and for a while after. */
    private static final TicketType<ChunkPos> ARRIVING = TicketType.create("alpha_omega_sub_level_arriving", Comparator.comparingLong(ChunkPos::toLong), 100);

    /**
     * Whether the place a sub-level would cross to is ready for it: every chunk under its bounds there (and one chunk
     * round, for its motion) loaded and block ticking, as Sable requires of a sub-level's chunks or it saves the
     * sub-level away. Asks for that area to load, if it is not.
     */
    private static boolean destinationReady(ServerLevel level, ServerSubLevel subLevel, Motion g) {
        BoundingBox3dc bounds = subLevel.boundingBox();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double[] p = {g.pointX((corner & 1) == 0 ? bounds.minX() : bounds.maxX()), 0.0, g.pointZ((corner & 4) == 0 ? bounds.minZ() : bounds.maxZ())};
            minX = Math.min(minX, SectionPos.posToSectionCoord(p[0]) - 1);
            maxX = Math.max(maxX, SectionPos.posToSectionCoord(p[0]) + 1);
            minZ = Math.min(minZ, SectionPos.posToSectionCoord(p[2]) - 1);
            maxZ = Math.max(maxZ, SectionPos.posToSectionCoord(p[2]) + 1);
        }
        ChunkPos center = new ChunkPos((minX + maxX) >> 1, (minZ + maxZ) >> 1);
        // Block ticking within distance - 1 of the centre: the whole area.
        int distance = Math.max(maxX - center.x, maxZ - center.z) + 1;
        level.getChunkSource().addRegionTicket(ARRIVING, center, distance, center);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z) || level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static void transfer(ServerLevel level, SubLevelPhysicsSystem system, ServerSubLevel subLevel, Motion g) {
        Matrix3d rotation = SableFrames.rotation(g);
        PhysicsPipeline pipeline = system.getPipeline();
        Vector3d linear = pipeline.getLinearVelocity(subLevel, new Vector3d());
        Vector3d angular = pipeline.getAngularVelocity(subLevel, new Vector3d());
        List<Entity> riders = riders(level, subLevel);

        Pose3d pose = new Pose3d(subLevel.logicalPose());
        SableFrames.transform(g, pose);
        pipeline.resetVelocity(subLevel);
        pipeline.teleport(subLevel, pose.position(), pose.orientation());
        pipeline.addLinearAndAngularVelocity(subLevel, rotation.transform(linear), rotation.transform(angular));
        // The last pose moves too, so everything that lerps from it to the pose (entity carrying, rendering) sees one step.
        SableFrames.transform(g, (Pose3d) subLevel.lastPose());
        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();

        for (Entity rider : riders) {
            FaceTransfers.carry(level, rider, g);
            EntitySubLevelUtil.setOldPosNoMovement(rider);
        }
    }

    /**
     * Entities in world space standing on (carried by) a sub-level. Players among them move here too, without a
     * teleport: their clients move them along with the sub-level once they see it cross ({@code SableClientFrames}),
     * and meanwhile send positions relative to it, which land in the new frame either way.
     */
    private static List<Entity> riders(ServerLevel level, ServerSubLevel subLevel) {
        BoundingBox3dc bounds = subLevel.boundingBox();
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(2.0);
        return level.getEntities((Entity) null, box, entity -> !entity.isPassenger()
            && entity instanceof EntityMovementExtension moving && moving.sable$getTrackingSubLevel() == subLevel);
    }

    /**
     * Whether a player should see a sub-level from one of its image positions: it is within Sable's tracking range of
     * the nearest of them ({@link ImageViews#playerPositionFor}).
     */
    public static boolean nearVirtual(Player player, Vector3dc position) {
        if (!(player.level() instanceof ServerLevel level) || Orbifold.of(level) == null) return false;
        Vec3 at = new Vec3(position.x(), position.y(), position.z());
        Vec3 from = ImageViews.playerPositionFor(level, player.position(), at);
        if (from.equals(player.position())) return false;
        double range = SableConfig.SUB_LEVEL_TRACKING_RANGE.getAsDouble();
        return from.distanceToSqr(at) < range * range;
    }
}
