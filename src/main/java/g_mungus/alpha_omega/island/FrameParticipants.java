package g_mungus.alpha_omega.island;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * Things other than entities that live in an island's lifted frame (Sable's sub-levels). They move with the chunks
 * under them when chunks are re-lifted, and cuts avoid them.
 */
public final class FrameParticipants {

    public interface FrameParticipant {
        /**
         * Chunks were re-lifted: {@code lapChanges} maps each one's {@link IslandGraph#key} to its lap change
         * ({@link IslandGraph#packLaps}). Move whatever stands over those chunks by the same number of laps.
         */
        void translate(ServerLevel level, Long2LongMap lapChanges);

        /** Adds, per canonical chunk column along the axis, how much a cut through it would disturb. */
        default void weighColumns(ServerLevel level, boolean xAxis, long[] weights) {
        }
    }

    private static final List<FrameParticipant> PARTICIPANTS = new ArrayList<>();

    private FrameParticipants() {
    }

    public static synchronized void register(FrameParticipant participant) {
        PARTICIPANTS.add(participant);
    }

    static void translate(ServerLevel level, Long2LongMap lapChanges) {
        for (FrameParticipant participant : PARTICIPANTS) participant.translate(level, lapChanges);
    }

    static void weighColumns(ServerLevel level, boolean xAxis, long[] weights) {
        for (FrameParticipant participant : PARTICIPANTS) participant.weighColumns(level, xAxis, weights);
    }
}
