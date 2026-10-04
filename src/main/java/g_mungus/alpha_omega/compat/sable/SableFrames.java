package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import g_mungus.alpha_omega.island.FrameParticipants;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/**
 * Sub-levels live in the lifted frame of the island under them, like entities: when its chunks are re-lifted (an
 * island shift, recentering, a cut relayout) a sub-level moves by the same laps, pose and physics body together.
 * Entities standing on it are moved by their own frame handling.
 */
public final class SableFrames implements FrameParticipants.FrameParticipant {

    /** A cut through a sub-level splits it between frames; avoid one about as hard as a player. */
    private static final long SUB_LEVEL_WEIGHT = 1L << 32;

    @Override
    public void translate(ServerLevel level, Long2LongMap lapChanges) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        Wrap wrap = Wrap.of(level);
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            Vector3d position = subLevel.logicalPose().position();
            long key = IslandGraph.key(wrap.canonChunk(SectionPos.posToSectionCoord(position.x)), wrap.canonChunk(SectionPos.posToSectionCoord(position.z)));
            if (!lapChanges.containsKey(key)) continue;
            long laps = lapChanges.get(key);
            move(container, subLevel, (double) IslandGraph.lapX(laps) * wrap.period, (double) IslandGraph.lapZ(laps) * wrap.period);
        }
    }

    private static void move(ServerSubLevelContainer container, ServerSubLevel subLevel, double dx, double dz) {
        if (dx == 0 && dz == 0) return;
        Pose3d pose = subLevel.logicalPose();
        Vector3d moved = new Vector3d(pose.position()).add(dx, 0, dz);
        container.physicsSystem().getPipeline().teleport(subLevel, moved, pose.orientation());
        ((Pose3d) subLevel.lastPose()).position().add(dx, 0, dz);
        // Twice, so the last bounds move too.
        subLevel.updateBoundingBox();
        subLevel.updateBoundingBox();
    }

    @Override
    public void weighColumns(ServerLevel level, boolean xAxis, long[] weights) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        Wrap wrap = Wrap.of(level);
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            BoundingBox3dc bounds = subLevel.boundingBox();
            int min = SectionPos.posToSectionCoord(xAxis ? bounds.minX() : bounds.minZ());
            int max = SectionPos.posToSectionCoord(xAxis ? bounds.maxX() : bounds.maxZ());
            for (int chunk = min; chunk <= max && chunk - min < weights.length; chunk++) {
                weights[wrap.canonChunk(chunk)] += SUB_LEVEL_WEIGHT;
            }
        }
    }
}
