package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The two bridges that move a single block position (RS §5):
 * <ul>
 * <li><b>Interaction range</b> ({@code Player.canInteractWithBlock}, under {@code Container.stillValidBlockEntity}): the
 * position is moved to its image nearest the player, so a menu on a block entity owned by another copy (its position
 * is the owner's) stays open while the player is near any copy. A safety net behind the phase 5 interaction pull.</li>
 * <li><b>Structure lookups</b> ({@code StructureManager.getStructureAt}, {@code getStructureWithPieceAt},
 * {@code structureHasPieceAt} and the reference lookups): a band position is looked up at its source, where the
 * structure was generated. Structures never straddle a seam, so nothing else is needed.</li>
 * </ul>
 */
public final class PlaceBridge {

    /** How far round a block an interaction check looks for images: more than any reach. */
    private static final double INTERACTION_REACH = 16.0;

    private PlaceBridge() {
    }

    /** {@code pos}, or its image nearest the player's eyes if that is nearer. */
    public static BlockPos forInteraction(Player player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return pos;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return pos;
        List<Motion> images = Images.around(geometry, pos.getX() + 0.5, pos.getZ() + 0.5, INTERACTION_REACH);
        if (images.isEmpty()) return pos;
        BridgeCounters.run(BridgeCounters.Kind.INTERACTION, pos.getX(), pos.getZ(), INTERACTION_REACH);
        Vec3 eye = player.getEyePosition();
        BlockPos best = pos;
        double bestDistance = new AABB(pos).distanceToSqr(eye);
        for (Motion g : images) {
            BlockPos image = Transform.of(g).block(pos);
            double d = new AABB(image).distanceToSqr(eye);
            if (d < bestDistance) {
                best = image;
                bestDistance = d;
            }
        }
        if (best != pos) BridgeCounters.found(BridgeCounters.Kind.INTERACTION, 1);
        return best;
    }

    /** A band or skirt position's source in the tile; any other position unchanged. Server levels only. */
    public static BlockPos forStructures(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level)) return pos;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || geometry.isTile(pos.getX(), pos.getZ()) || !geometry.inFootprint(pos.getX(), pos.getZ())) return pos;
        OrbifoldGeometry.Cell source = geometry.canon(pos.getX(), pos.getZ());
        BridgeCounters.run(BridgeCounters.Kind.STRUCTURES, pos.getX(), pos.getZ(), 0.0);
        return new BlockPos(source.x(), pos.getY(), source.z());
    }
}
