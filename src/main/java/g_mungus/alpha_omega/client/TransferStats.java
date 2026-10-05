package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import java.util.ArrayList;
import java.util.List;

/**
 * Development aid ({@code -Dalpha_omega.dev.transferStats}, Gradle {@code -PtransferStats}): what crossing an edge
 * costs the client. For two seconds after each change of face it counts chunks received (and of those, chunks it
 * already held), chunks forgotten, and how many sections vanilla had to draw in the first frames; then it logs them.
 */
public final class TransferStats {

    private static final boolean ENABLED = Boolean.getBoolean("alpha_omega.dev.transferStats");
    private static final int WINDOW_TICKS = 40;
    private static final int FRAMES = 5;

    private static int ticksLeft;
    private static int received;
    private static int alreadyHeld;
    private static int forgotten;
    private static final List<Integer> visibleSections = new ArrayList<>();

    private TransferStats() {
    }

    public static void faceChanged() {
        if (!ENABLED) return;
        if (ticksLeft > 0) report();
        ticksLeft = WINDOW_TICKS;
        received = alreadyHeld = forgotten = 0;
        visibleSections.clear();
    }

    public static void chunkReceived(boolean held) {
        if (ticksLeft <= 0) return;
        received++;
        if (held) alreadyHeld++;
    }

    public static void chunkForgotten() {
        if (ticksLeft > 0) forgotten++;
    }

    /** Once per frame, after vanilla chose its sections. */
    public static void frame(int sections) {
        if (ticksLeft > 0 && visibleSections.size() < FRAMES) visibleSections.add(sections);
    }

    public static void tick() {
        if (ticksLeft > 0 && --ticksLeft == 0) report();
    }

    private static void report() {
        AlphaOmegaMod.LOGGER.info("Transfer stats: received {} chunks ({} already held), forgot {}, visible sections in the first frames {}",
            received, alreadyHeld, forgotten, visibleSections);
    }
}
