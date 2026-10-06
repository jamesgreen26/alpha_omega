package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Crossing a seam (RS §4), shared by server and client. A position is valid in storage out to the band's depth
 * {@code H} past the tile. Something deep enough past a seam transfers by its position's frame {@code g}, the element of
 * {@code Γ} that takes it into the tile, within the same storage: players once more than {@code C + ½} past (so walking
 * back, they cross at {@code C} on the other side, {@code 2C} of hysteresis), everything else at {@code H}. Both frames
 * show the same world, so a transfer is invisible.
 *
 * <p>An entity that has just crossed waits {@link #COOLDOWN_TICKS} before it may cross again, and a follower may change
 * frame at most once per {@link #FOLLOW_COOLDOWN_TICKS}; an entity its own code moved into another frame is
 * <b>anchored</b> for {@link #ANCHOR_TICKS} and followers leave it alone ({@link TransferCooldown}).
 */
public final class FrameTransfer {

    private FrameTransfer() {
    }

    /** Ticks after crossing before the same entity may cross again. */
    public static final int COOLDOWN_TICKS = 10;
    /** At most one follower change per this many ticks per entity (RS §4.3). */
    public static final int FOLLOW_COOLDOWN_TICKS = 40;
    /** How long an entity its own code moved into another frame is left alone by followers. */
    public static final int ANCHOR_TICKS = 200;
    /** Hysteresis past the claim depth before a player crosses. */
    public static final double PLAYER_HYSTERESIS = 0.5;

    /** Tests for what carries an entity across seams itself, so the entity does not cross on its own. */
    private static final List<Predicate<Entity>> CARRIERS = new CopyOnWriteArrayList<>();

    /** Registers a test for entities carried by something that crosses seams itself (a Sable sub-level they stand on). */
    public static void registerCarrier(Predicate<Entity> carried) {
        CARRIERS.add(carried);
    }

    /**
     * Whether an entity is carried by something that crosses seams itself: it crosses with that, at the same moment,
     * rather than on its own.
     */
    public static boolean carried(Entity entity) {
        for (Predicate<Entity> carrier : CARRIERS) {
            if (carrier.test(entity)) return true;
        }
        return false;
    }

    /** How far past a seam a player goes before it crosses: {@code C + ½}. */
    public static double playerDepth(OrbifoldGeometry geometry) {
        return geometry.claim + PLAYER_HYSTERESIS;
    }

    /** How far past a seam anything else goes before it crosses: {@code H}. */
    public static double otherDepth(OrbifoldGeometry geometry) {
        return geometry.band;
    }

    /** The depth at which {@code root} (with its passengers) crosses: a player's if it carries one. */
    public static double depthFor(OrbifoldGeometry geometry, Entity root) {
        return carriesPlayer(root) ? playerDepth(geometry) : otherDepth(geometry);
    }

    /** Whether a root entity is, or carries, a player. */
    public static boolean carriesPlayer(Entity root) {
        return root instanceof Player || root.getSelfAndPassengers().anyMatch(entity -> entity instanceof Player);
    }

    /**
     * The element a position should transfer by, or null if it stays: its frame, once it is more than {@code depth}
     * past a seam.
     */
    @Nullable
    public static Motion destination(OrbifoldGeometry geometry, double x, double z, double depth) {
        if (geometry.seamDepth(x, z) <= depth) return null;
        if (!geometry.inFootprint((int) Math.floor(x), (int) Math.floor(z))) return null;
        Motion g = geometry.frame(x, z);
        return g.isIdentity() ? null : g;
    }

    /** Whether an entity crossed too recently to cross again. */
    public static boolean coolingDown(Entity entity) {
        return coolingDown(entity, COOLDOWN_TICKS);
    }

    /** Whether an entity changed frame less than {@code ticks} ago. */
    public static boolean coolingDown(Entity entity, int ticks) {
        return entity.tickCount - ((TransferCooldown) entity).alpha_omega$lastTransferTick() < ticks;
    }

    /** Notes that an entity has just crossed. */
    public static void startCooldown(Entity entity) {
        ((TransferCooldown) entity).alpha_omega$setLastTransferTick(entity.tickCount);
    }

    /** Whether an entity's own code has recently put it into a frame, so followers leave it be. */
    public static boolean anchored(Entity entity) {
        return entity.tickCount < ((TransferCooldown) entity).alpha_omega$anchoredUntil();
    }

    public static void anchor(Entity entity) {
        ((TransferCooldown) entity).alpha_omega$setAnchoredUntil(entity.tickCount + ANCHOR_TICKS);
    }

    /**
     * Whether the place an entity moved by {@code g} lands is loaded. Its blocks are the same as where it is (a band
     * copy and its source hold one world), so it fits there exactly when it fits here; only an unloaded destination
     * holds it back, and it carries on in the band until it loads.
     */
    public static boolean destinationLoaded(Entity entity, Motion g) {
        Vec3 p = Transform.of(g).position(entity.position());
        return entity.level().hasChunkAt(BlockPos.containing(p));
    }

    /** A yaw and pitch moved by {@code g}, as {@code {yRot, xRot}}: a turn about Y leaves the pitch alone. */
    public static float[] rotateLook(Motion g, float yRot, float xRot) {
        return new float[] {g.yaw(yRot), xRot};
    }
}
