package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Anchoring (RS §4.3): if an entity's own code (or another mod's) moves it into another frame, it is marked anchored
 * for {@link FrameTransfer#ANCHOR_TICKS}, and followers leave it alone. Code that puts an entity next to a block entity
 * is saying which frame it belongs to.
 *
 * <p>A move into another frame is a jump in storage (further than {@link #JUMP}) to somewhere near the same place in
 * the world (within {@link #SAME_PLACE} of an expression of where it was). Transfers themselves are not counted.
 */
public final class FrameAnchors {

    /** A move shorter than this (blocks, horizontally) is ordinary movement. */
    public static final double JUMP = 2.0 * 32.0;
    /** How near the same place in the world the move must land to count as a change of frame. */
    public static final double SAME_PLACE = 16.0;

    private FrameAnchors() {
    }

    public static void beforeSetPos(Entity entity, double x, double z) {
        double dx = x - entity.getX(), dz = z - entity.getZ();
        if (dx * dx + dz * dz < JUMP * JUMP) return;
        if (entity instanceof Player || entity.level() == null || entity.level().isClientSide() || FrameTransfers.moving()) return;
        OrbifoldGeometry geometry = Orbifold.of(entity.level());
        if (geometry == null || entity.tickCount == 0 && entity.getX() == 0.0 && entity.getZ() == 0.0) return;
        if (Frames.crossing(geometry, entity.getX(), entity.getZ(), x, z, SAME_PLACE) != null) {
            FrameTransfer.anchor(entity);
            TransferCounters.count(TransferCounters.Kind.ANCHORED);
        }
    }
}
