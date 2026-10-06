package g_mungus.alpha_omega.api;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.NearestImages;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Alpha Omega's world shape, for other mods: a thin, stable view over the mod's geometry.
 *
 * <h2>The model</h2>
 * An orbifold level is the plane folded up by a group {@code Γ} of motions (translations and half turns about the
 * vertical axis; {@code y} never changes). It is stored once, as the <b>tile</b>, plus a <b>band</b> (and a thin
 * <b>skirt</b> past it) round the tile holding live copies of the tile cells just across each seam. So one block of
 * the world can be stored at several positions: its <b>source</b> in the tile, and up to three <b>copies</b> in the
 * band and skirt. Writing to any of them writes to all.
 * <ul>
 * <li>A stored position's <b>frame</b> is the element of {@code Γ} that takes it to its source: the identity in the
 * tile. Entities in the band live in that frame; they <i>transfer</i> (move by an element of {@code Γ}, with velocity
 * and facing turned along) when they go deep enough, firing {@link FrameTransferEvent}.</li>
 * <li>A {@link Motion} is an element of {@code Γ}: {@code p ↦ p + t} or {@code p ↦ t − p}. Block cells, chunks,
 * points, vectors, yaw, block states and boxes all map through it ({@link #transform(Motion)}).</li>
 * <li>Two positions are the same place when one is an image of the other. To compare positions (distances,
 * directions), first bring one near the other with {@link #nearest(Vec3, Vec3)} or into its frame with
 * {@link #toFrame(Level, Vec3, Vec3)}.</li>
 * </ul>
 *
 * <p>Only levels whose generator is Alpha Omega's are orbifolds: {@link #of(Level)} returns null for every other level
 * (and before a client has heard from the server). Instances are cheap and immutable; methods are safe on any thread.
 * Nothing here changes the world.
 */
public final class Orbifold {

    private final OrbifoldGeometry geometry;

    private Orbifold(OrbifoldGeometry geometry) {
        this.geometry = geometry;
    }

    /** The orbifold a level is, or null if it is an ordinary level. Works on both sides. */
    @Nullable
    public static Orbifold of(Level level) {
        OrbifoldGeometry geometry = g_mungus.alpha_omega.orbifold.Orbifold.of(level);
        return geometry == null ? null : new Orbifold(geometry);
    }

    /** A view over a geometry, for code that already has one (tests, tools); {@link #of(Level)} is the usual way in. */
    public static Orbifold of(OrbifoldGeometry geometry) {
        return new Orbifold(geometry);
    }

    /** Whether a level is an orbifold. */
    public static boolean isOrbifold(Level level) {
        return g_mungus.alpha_omega.orbifold.Orbifold.of(level) != null;
    }

    /** The full geometry: tile bounds, band depth, cone points, generators. Read-only. */
    public OrbifoldGeometry geometry() {
        return this.geometry;
    }

    // ---- Where a position is ----

    /** Whether a block position is in the tile: a source, every place once. */
    public boolean isTile(BlockPos pos) {
        return this.geometry.isTile(pos.getX(), pos.getZ());
    }

    /** Whether a block position is in the band: a live copy of a tile cell across a seam. */
    public boolean isBand(BlockPos pos) {
        return this.geometry.isBand(pos.getX(), pos.getZ());
    }

    /** Whether a block position is in the skirt past the band: a copy kept for light and meshing only. */
    public boolean isSkirt(BlockPos pos) {
        return this.geometry.isSkirt(pos.getX(), pos.getZ());
    }

    /** How far a point is past the nearest seam, in blocks (Chebyshev): negative inside the tile. */
    public double seamDepth(Vec3 pos) {
        return this.geometry.seamDepth(pos.x, pos.z);
    }

    /** The frame of a stored block position: the element taking it to its source (identity in the tile). */
    public Motion frame(BlockPos pos) {
        return this.geometry.frame(pos.getX(), pos.getZ());
    }

    /** The frame of a stored point. */
    public Motion frame(Vec3 pos) {
        return this.geometry.frame(pos.x, pos.z);
    }

    // ---- Sources and copies ----

    /**
     * The source of a stored block position: the tile cell holding the same block. Itself in the tile. Exact within the
     * stored footprint (the tile, band and skirt), which is everywhere anything is stored.
     */
    public BlockPos canon(BlockPos pos) {
        return transform(this.frame(pos), pos);
    }

    /** The point in the tile that is the same place as a stored point. Itself in the tile. */
    public Vec3 canon(Vec3 pos) {
        return transform(this.frame(pos), pos);
    }

    /**
     * Every stored position of the block at {@code pos}: its source first, then its copies in the band and skirt (up to
     * three, near a corner or cone point). {@code pos} itself is among them. Positions outside the stored footprint
     * give just themselves.
     */
    public List<BlockPos> copies(BlockPos pos) {
        if (!this.geometry.inFootprint(pos.getX(), pos.getZ())) return List.of(pos);
        BlockPos source = this.canon(pos);
        List<BlockPos> all = new ArrayList<>(4);
        all.add(source);
        for (OrbifoldGeometry.Cell copy : this.geometry.copies(source.getX(), source.getZ())) {
            all.add(new BlockPos(copy.x(), pos.getY(), copy.z()));
        }
        return all;
    }

    // ---- Relating two positions ----

    /**
     * {@code pos} expressed in the frame of {@code ofFramePos}: its source, carried by the inverse of
     * {@code ofFramePos}'s frame. In the tile that is the source itself; in the band it is the image beside
     * {@code ofFramePos}, as an entity there sees it. Unlike {@link #nearest}, follows the frame, not the distance.
     */
    public Vec3 toFrame(Vec3 pos, Vec3 ofFramePos) {
        return transform(this.frame(ofFramePos).inverse(), this.canon(pos));
    }

    /** {@link #toFrame(Vec3, Vec3)} for a block position. */
    public BlockPos toFrame(BlockPos pos, Vec3 ofFramePos) {
        return transform(this.frame(ofFramePos).inverse(), this.canon(pos));
    }

    /** {@link #toFrame(Vec3, Vec3)} in a level; the position unchanged if the level is not an orbifold. */
    public static Vec3 toFrame(Level level, Vec3 pos, Vec3 ofFramePos) {
        Orbifold orbifold = of(level);
        return orbifold == null ? pos : orbifold.toFrame(pos, ofFramePos);
    }

    /** {@link #toFrame(BlockPos, Vec3)} in a level; the position unchanged if the level is not an orbifold. */
    public static BlockPos toFrame(Level level, BlockPos pos, Vec3 ofFramePos) {
        Orbifold orbifold = of(level);
        return orbifold == null ? pos : orbifold.toFrame(pos, ofFramePos);
    }

    /**
     * The image of {@code pos} nearest to {@code near}, over the whole group (any distance, any number of laps): where
     * to aim from {@code near} to reach {@code pos} the short way. Compasses and maps use this.
     */
    public Vec3 nearest(Vec3 pos, Vec3 near) {
        return transform(this.nearestMotion(pos, near), pos);
    }

    /** {@link #nearest(Vec3, Vec3)} for a block cell, measured from its centre. */
    public BlockPos nearest(BlockPos pos, Vec3 near) {
        return transform(NearestImages.towardCell(this.geometry, pos.getX(), pos.getZ(), near.x, near.z), pos);
    }

    /** The element taking {@code pos} to its image nearest {@code near}. */
    public Motion nearestMotion(Vec3 pos, Vec3 near) {
        return NearestImages.toward(this.geometry, pos.x, pos.z, near.x, near.z);
    }

    /** The horizontal distance between two positions in the world itself: to the nearest image. */
    public double distance(Vec3 a, Vec3 b) {
        return NearestImages.distance(this.geometry, a.x, a.z, b.x, b.z);
    }

    // ---- Moving things by an element ----

    /** An element applied to Minecraft's types (cells, chunks, points, vectors, yaw, states, directions, boxes). */
    public static Transform transform(Motion motion) {
        return Transform.of(motion);
    }

    /** The block cell {@code pos} maps to under {@code motion}. */
    public static BlockPos transform(Motion motion, BlockPos pos) {
        return Transform.of(motion).block(pos);
    }

    /** The point {@code pos} maps to under {@code motion}. */
    public static Vec3 transform(Motion motion, Vec3 pos) {
        return Transform.of(motion).position(pos);
    }

    /** A block state turned with {@code motion} (180° under a half turn). */
    public static BlockState transform(Motion motion, BlockState state) {
        return Transform.of(motion).state(state);
    }

    /** A direction turned with {@code motion}. */
    public static Direction transform(Motion motion, Direction direction) {
        return Transform.of(motion).direction(direction);
    }

    /** A yaw, in degrees, turned with {@code motion}. */
    public static float transformYaw(Motion motion, float yaw) {
        return motion.yaw(yaw);
    }

    /** A direction or velocity turned with {@code motion} (not moved). */
    public static Vec3 transformVector(Motion motion, Vec3 vector) {
        return Transform.of(motion).vector(vector);
    }
}
