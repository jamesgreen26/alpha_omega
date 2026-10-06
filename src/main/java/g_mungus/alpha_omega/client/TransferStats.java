package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.client.sky.ClientSky;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * Development aid ({@code -Dalpha_omega.dev.transferStats}, Gradle {@code -PtransferStats}): what crossing a seam
 * costs the client. For two seconds after each crossing it counts chunks received (and of those, chunks it already
 * held), chunks forgotten, and for the first frames how many sections vanilla chose to draw, how many of those were
 * compiled, and how many the outgoing area drew for it during the hand-over ({@code ImageRenderer}); also the sun's
 * height over the first ticks and how many frames the hand-over lasted. Then it logs them.
 */
public final class TransferStats {

    private static final boolean ENABLED = Boolean.getBoolean("alpha_omega.dev.transferStats");
    private static final int WINDOW_TICKS = 40;
    private static final int FRAMES = 8;

    private static int ticksLeft;
    private static int received;
    private static int alreadyHeld;
    private static int forgotten;
    private static int handoverFrames = -1;
    private static final List<String> frames = new ArrayList<>();
    private static final List<String> sunHeights = new ArrayList<>();

    private TransferStats() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void faceChanged() {
        if (!ENABLED) return;
        if (ticksLeft > 0) report();
        ticksLeft = WINDOW_TICKS;
        received = alreadyHeld = forgotten = 0;
        handoverFrames = -1;
        frames.clear();
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

    /** Vanilla's area was swapped (a crossing, or a teleport by an element): starts a window if a crossing did not. */
    public static void swapped() {
        if (ENABLED && ticksLeft <= 0) faceChanged();
    }

    public static void chunkReceived(boolean held) {
        if (ticksLeft <= 0) return;
        received++;
        if (held) alreadyHeld++;
    }

    public static void chunkForgotten() {
        if (ticksLeft > 0) forgotten++;
    }

    /** Once per frame, after vanilla and the images chose their sections: {@code visible/compiled+handed over}. */
    public static void frame(List<SectionRenderDispatcher.RenderSection> visible) {
        if (ticksLeft <= 0 || frames.size() >= FRAMES) return;
        long compiled = visible.stream().filter(section -> section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED).count();
        frames.add(visible.size() + "/" + compiled + "+" + ImageRenderer.handoverDrawn());
    }

    public static void handoverEnded(int frameCount) {
        if (ENABLED) {
            handoverFrames = frameCount;
            AlphaOmegaMod.LOGGER.info("Transfer stats: hand-over ended after {} frames", frameCount);
        }
    }

    public static void tick() {
        if (ticksLeft <= 0) return;
        sunHeight();
        if (--ticksLeft == 0) report();
    }

    private static void report() {
        AlphaOmegaMod.LOGGER.info("Transfer stats: received {} chunks ({} already held), forgot {}, first frames (vanilla visible/compiled+handed over) {}, "
            + "hand-over frames {}, sun height by tick {}", received, alreadyHeld, forgotten, frames, handoverFrames, sunHeights);
    }
}
