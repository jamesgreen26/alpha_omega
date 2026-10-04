package g_mungus.alpha_omega.api;

import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.island.FrameParticipants;
import g_mungus.alpha_omega.island.FrameTranslators;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Public API for mods working with wrapped worlds. In a wrapped dimension a position has many images, one per lap,
 * all naming the same place. Every method is the identity in a dimension that does not wrap.
 * <ul>
 *   <li>Key any position-indexed storage of your own by {@link #canonical} positions.</li>
 *   <li>Compare or measure between positions with {@link #nearestImage} / {@link #distanceSqr}.</li>
 *   <li>Entities live in lifted frames; if your entity remembers absolute positions, register a
 *       {@link #registerFrameTranslator frame translator} so they move with it.</li>
 * </ul>
 */
public final class WorldWrap {

    private WorldWrap() {
    }

    public static boolean isWrapped(Level level) {
        return Wrap.of(level).enabled();
    }

    /** The world size along each horizontal axis in this dimension, in blocks; 0 if it does not wrap. */
    public static int period(Level level) {
        return Wrap.of(level).period;
    }

    /**
     * The first canonical block coordinate on each horizontal axis: {@code -period / 2} in worlds created since the
     * window was centered on spawn, 0 in older ones. The seam lies at this coordinate.
     */
    public static int minCoordinate(Level level) {
        return Wrap.of(level).minBlock;
    }

    /**
     * The storage address of {@code pos}: its image in the canonical window, which runs from {@link #minCoordinate}
     * for {@link #period} blocks on each horizontal axis.
     */
    public static BlockPos canonical(Level level, BlockPos pos) {
        return Wrap.of(level).canon(pos);
    }

    public static ChunkPos canonical(Level level, ChunkPos pos) {
        return Wrap.of(level).canon(pos);
    }

    public static Vec3 canonical(Level level, Vec3 pos) {
        Wrap wrap = Wrap.of(level);
        return new Vec3(wrap.canon(pos.x), pos.y, wrap.canon(pos.z));
    }

    /** The image of {@code pos} closest to {@code reference}. */
    public static Vec3 nearestImage(Level level, Vec3 pos, Vec3 reference) {
        return Wrap.of(level).nearest(pos, reference);
    }

    public static BlockPos nearestImage(Level level, BlockPos pos, Vec3 reference) {
        return Wrap.of(level).nearest(pos, reference);
    }

    /** Squared distance between the closest images of two positions. */
    public static double distanceSqr(Level level, Vec3 a, Vec3 b) {
        Wrap wrap = Wrap.of(level);
        double dx = wrap.minDelta(a.x, b.x);
        double dy = a.y - b.y;
        double dz = wrap.minDelta(a.z, b.z);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Where block-side code at {@code pos} runs: the image in the frame of the surrounding simulation. */
    public static BlockPos lift(ServerLevel level, BlockPos pos) {
        return Frames.lift(level, pos);
    }

    /** Translates the absolute positions entities of {@code type} hold when they move between frames. */
    public static <T extends Entity> void registerFrameTranslator(Class<T> type, FrameTranslators.FrameTranslator<? super T> translator) {
        FrameTranslators.register(type, translator);
    }

    /** Moves non-entity things that live in lifted frames (moving structures, say) along with the chunks under them. */
    public static void registerFrameParticipant(FrameParticipants.FrameParticipant participant) {
        FrameParticipants.register(participant);
    }

    /**
     * Excludes chunk coordinates {@code [minChunk, maxChunk)} on each horizontal axis from wrapping, for storage kept
     * far outside the world. Call during mod construction.
     */
    public static void excludeFromWrapping(int minChunk, int maxChunk) {
        Wrap.excludeFromTorus(minChunk, maxChunk);
    }
}
