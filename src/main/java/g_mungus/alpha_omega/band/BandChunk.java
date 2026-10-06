package g_mungus.alpha_omega.band;

import org.jetbrains.annotations.Nullable;

/** Per-chunk band state (implemented on {@code LevelChunk} by a mixin). Server chunks only. */
public interface BandChunk {

    /**
     * The chunks holding copies of this chunk's cells ({@link CopyLinks#NONE} away from the edges, and on the client).
     * Computed once per chunk.
     */
    CopyLinks alpha_omega$links();

    default boolean alpha_omega$linked() {
        return !this.alpha_omega$links().isEmpty();
    }

    /** The chunk's ownership masks and link stamps, created if {@code create}; null if it has none. */
    @Nullable
    BandData alpha_omega$data(boolean create);

    /**
     * Whether this chunk's copies are in step with their sources: always for tile chunks; for band and skirt chunks once
     * filled on promotion ({@link BandFill}). Only a gate fallback leaves a band chunk unfilled.
     */
    boolean alpha_omega$filled();

    void alpha_omega$setFilled(boolean filled);

    /** Whether this chunk was promoted from freshly generated content (rather than loaded as a full chunk). */
    boolean alpha_omega$fresh();

    /** Whether the chunk is in its level (vanilla's {@code loaded}): false once it unloads. */
    boolean alpha_omega$inLevel();
}
