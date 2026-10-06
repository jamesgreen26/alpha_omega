package g_mungus.alpha_omega.nether;

import g_mungus.alpha_omega.mixin.nether.NetherPortalBlockInvoker;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Nether portals between orbifold levels ({@code orbifold-implementation.md} phase 10). A portal and its band copies are
 * one portal, so they must link the same way: the entity is first taken to its source frame (the canonical copy of the
 * portal, in the tile), and everything vanilla works out (the scaled target, the relative position in the portal, the
 * motion and the yaw) is worked out there. The scaled target is canonicalised in the destination. The search for an
 * exit portal is vanilla's ({@code PortalForcer}), through the POI bridge, so a portal across a seam from the target is
 * found at its copy on the near side. An arrival in the destination's band is moved to its source frame, the tile.
 */
public final class NetherPortals {

    private NetherPortals() {
    }

    /** The level a Nether portal in {@code from} leads to, as vanilla picks it. */
    @Nullable
    private static ServerLevel target(ServerLevel from) {
        ResourceKey<Level> key = from.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
        return from.getServer().getLevel(key);
    }

    /** Whether a portal in {@code from} links orbifold levels (either end), so {@link #destination} decides, not vanilla. */
    public static boolean handles(ServerLevel from) {
        ServerLevel to = target(from);
        return to != null && (Orbifold.of(from) != null || Orbifold.of(to) != null);
    }

    /**
     * The transition through a Nether portal at {@code portal} in {@code from} ({@link #handles} it), or null when the
     * exit cannot be made, as vanilla returns.
     */
    @Nullable
    public static DimensionTransition destination(ServerLevel from, Entity entity, BlockPos portal) {
        ServerLevel to = target(from);
        if (to == null) return null;
        OrbifoldGeometry fromGeometry = Orbifold.of(from), toGeometry = Orbifold.of(to);

        // The entity, its portal and its motion in their source frame.
        Motion frame = fromGeometry == null || !fromGeometry.inFootprint(entity.getBlockX(), entity.getBlockZ())
            ? Motion.IDENTITY : fromGeometry.frame(entity.getX(), entity.getZ());
        Transform canonical = Transform.of(frame);
        Vec3 position = canonical.position(entity.position());
        Vec3 motion = canonical.vector(entity.getDeltaMovement());
        float yaw = canonical.yaw(entity.getYRot());
        BlockPos entry = canonical.block(portal);

        // The target: scaled, then canonical in the destination.
        boolean toNether = to.dimension() == Level.NETHER;
        WorldBorder border = to.getWorldBorder();
        double scale = DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
        double[] target = canonicalPoint(toGeometry, position.x * scale, position.z * scale);
        BlockPos targetPos = border.clampToBounds(target[0], position.y, target[1]);

        // Vanilla's exit: the nearest portal, or a new one.
        BlockUtil.FoundRectangle exit;
        DimensionTransition.PostDimensionTransition after;
        Optional<BlockPos> found = to.getPortalForcer().findClosestPortalPosition(targetPos, toNether, border);
        BlockState entryState = from.getBlockState(entry);
        if (found.isPresent()) {
            BlockPos at = found.get();
            BlockState state = to.getBlockState(at);
            exit = BlockUtil.getLargestRectangleAround(at, state.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21,
                pos -> to.getBlockState(pos) == state);
            after = DimensionTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(at));
        } else {
            Direction.Axis axis = entryState.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> made = to.getPortalForcer().createPortal(targetPos, axis);
            if (made.isEmpty()) return null;
            exit = made.get();
            after = DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET);
        }

        // Where in the entry portal the entity stands, measured at the canonical copy.
        Direction.Axis axis;
        Vec3 relative;
        if (entryState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            axis = entryState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle entryRect = BlockUtil.getLargestRectangleAround(entry, axis, 21, Direction.Axis.Y, 21,
                pos -> from.getBlockState(pos) == entryState);
            relative = PortalShape.getRelativePosition(entryRect, axis, position, entity.getDimensions(entity.getPose()));
        } else {
            axis = Direction.Axis.X;
            relative = new Vec3(0.5, 0.0, 0.0);
        }
        DimensionTransition transition = NetherPortalBlockInvoker.alpha_omega$createDimensionTransition(to, exit, axis, relative, entity, motion, yaw,
            entity.getXRot(), after);
        return arrive(toGeometry, transition);
    }

    /** A point of the destination taken to its source frame: canonical where it is in the footprint, else clamped into the tile. */
    static double[] canonicalPoint(@Nullable OrbifoldGeometry geometry, double x, double z) {
        if (geometry == null) return new double[] {x, z};
        if (!geometry.inFootprint((int) Math.floor(x), (int) Math.floor(z))) {
            // Only for a Nether made for another size: keep the target in the tile.
            return new double[] {Math.max(geometry.minX, Math.min(geometry.maxX - 1, x)), Math.max(geometry.northRow, Math.min(geometry.southRow - 1, z))};
        }
        Motion g = geometry.frame(x, z);
        return new double[] {g.pointX(x), g.pointZ(z)};
    }

    /** An arrival in the band (an exit portal found at a band copy) moved to its source frame, so it lands in the tile. */
    static DimensionTransition arrive(@Nullable OrbifoldGeometry geometry, DimensionTransition transition) {
        Vec3 pos = transition.pos();
        if (geometry == null || !geometry.inFootprint((int) Math.floor(pos.x), (int) Math.floor(pos.z))) return transition;
        Motion g = geometry.frame(pos.x, pos.z);
        if (g.isIdentity()) return transition;
        Transform t = Transform.of(g);
        return new DimensionTransition(transition.newLevel(), t.position(pos), t.vector(transition.speed()), t.yaw(transition.yRot()),
            transition.xRot(), transition.missingRespawnBlock(), transition.postDimensionTransition());
    }
}
