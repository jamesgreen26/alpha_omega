package g_mungus.alpha_omega.band;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Wires the band to the game: the attachment, late fills, gate bookkeeping, and the gate's violation detectors. */
public final class BandEvents {

    private BandEvents() {
    }

    public static void register(IEventBus modBus) {
        BandData.register(modBus);
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk && unfilled(chunk)) {
                BandCounters.gateViolation("loaded unfilled");
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) forget(level, chunk);
        });
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent event) -> {
            if (unfilled(event.getChunk())) BandCounters.gateViolation("sent unfilled");
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre event) -> {
            if (event.getLevel() instanceof ServerLevel level && Band.geometry(level) != null) {
                BandFill.flush(level);
                BandGate.tick();
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            BandFill.clear();
            BandGate.clear();
        });
    }

    /** A band or skirt chunk that is not filled (only a gate fallback leaves one). */
    public static boolean unfilled(LevelChunk chunk) {
        return !chunk.getLevel().isClientSide && Band.links(chunk).band && !Band.filled(chunk);
    }

    /** An unloading chunk: its copies forget their cached reference to it. */
    private static void forget(ServerLevel level, LevelChunk chunk) {
        for (CopyLinks.Link link : Band.links(chunk).links) {
            LevelChunk copy = level.getChunkSource().getChunkNow(link.chunkX, link.chunkZ);
            if (copy == null) continue;
            for (CopyLinks.Link back : Band.links(copy).links) back.forget(chunk);
        }
    }
}
