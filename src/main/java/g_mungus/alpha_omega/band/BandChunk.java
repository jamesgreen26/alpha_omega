package g_mungus.alpha_omega.band;

/** Per-chunk band state (implemented on {@code LevelChunk} by a mixin). */
public interface BandChunk {

    /**
     * Whether any cell in this chunk has another copy: a band or skirt chunk, or a tile chunk near an edge. Computed
     * once; chunks away from edges answer with one field read.
     */
    boolean alpha_omega$linked();

    /** Whether this band or skirt chunk has been filled from its source since it loaded. Tile chunks are always filled. */
    boolean alpha_omega$filled();

    void alpha_omega$setFilled(boolean filled);
}
