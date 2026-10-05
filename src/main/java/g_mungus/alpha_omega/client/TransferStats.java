package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.client.sky.ClientSky;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Development aid ({@code -Dalpha_omega.dev.transferStats}, Gradle {@code -PtransferStats}): what crossing an edge
 * costs the client. For two seconds after each change of face it counts chunks received (and of those, chunks it
 * already held), chunks forgotten, how many sections vanilla had to draw in the first frames and the sun's height
 * over the first ticks; then it logs them.
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
    private static final List<String> sunHeights = new ArrayList<>();

    private TransferStats() {
    }

    public static void faceChanged() {
        if (!ENABLED) return;
        if (ticksLeft > 0) report();
        ticksLeft = WINDOW_TICKS;
        received = alreadyHeld = forgotten = 0;
        visibleSections.clear();
        sunHeights.clear();
        sunHeight();
    }

    /** The sun's height over the camera's horizon, as the sky is drawn with it. */
    private static void sunHeight() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && ClientSky.applies(level) && sunHeights.size() < 24) {
            sunHeights.add(String.format("%.2f", ClientSky.atCamera(level).sunY()));
        }
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
        if (ticksLeft <= 0) return;
        sunHeight();
        if (--ticksLeft == 0) report();
    }

    private static void report() {
        AlphaOmegaMod.LOGGER.info("Transfer stats: received {} chunks ({} already held), forgot {}, visible sections in the first frames {}, "
            + "sun height by tick {}", received, alreadyHeld, forgotten, visibleSections, sunHeights);
    }
}
