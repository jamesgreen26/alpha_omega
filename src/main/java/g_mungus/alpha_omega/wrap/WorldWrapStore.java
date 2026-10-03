package g_mungus.alpha_omega.wrap;

import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Decides a world's wrapping as its server starts, before any level is constructed. Wrapping depends on terrain
 * generated to tile, so it is fixed when the world is created: a fresh world takes the requested settings and
 * records them in its folder; an existing world without that record was created without wrapping and stays so.
 */
public final class WorldWrapStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Settings chosen on the Create World screen for the next fresh world (single player). */
    @Nullable
    private static volatile WorldWrapSettings pending;

    private WorldWrapStore() {
    }

    public static void requestForNextWorld(@Nullable WorldWrapSettings settings) {
        pending = settings;
    }

    @Nullable
    public static WorldWrapSettings requested() {
        return pending;
    }

    public static void load(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path file = root.resolve(WorldWrapSettings.FILE_NAME);
        WorldWrapSettings settings;
        try {
            if (server instanceof GameTestServer) {
                // Test worlds are scratch space: always the configured wrapping, never recorded.
                settings = AlphaOmegaConfig.defaults();
            } else if (Files.exists(file)) {
                settings = WorldWrapSettings.read(file);
            } else {
                settings = isFresh(root) ? (pending != null ? pending : AlphaOmegaConfig.defaults()) : WorldWrapSettings.DISABLED;
                settings.write(file);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read or write {}; the world will not wrap", file, e);
            settings = WorldWrapSettings.DISABLED;
        }
        pending = null;
        Wraps.configure(settings);
        if (settings.enabled()) {
            LOGGER.info("World wraps every {} blocks (Nether: {}, End: {})", settings.period(), settings.periodFor(Level.NETHER), settings.periodFor(Level.END));
            checkViewDistance(settings);
        } else {
            LOGGER.info("World does not wrap");
        }
    }

    /** No chunks have been generated yet. */
    private static boolean isFresh(Path root) throws IOException {
        Path region = root.resolve("region");
        if (!Files.isDirectory(region)) return true;
        try (Stream<Path> files = Files.list(region)) {
            return files.noneMatch(path -> path.toString().endsWith(".mca"));
        }
    }

    /**
     * Every "nearest image" decision must be unambiguous (§4.1): a dimension's period must exceed twice the largest
     * view distance (32) plus a margin, or players could see the same terrain on both sides.
     */
    private static void checkViewDistance(WorldWrapSettings settings) {
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            int chunks = settings.periodFor(dimension) >> 4;
            if (chunks != 0 && chunks < 2 * (32 + 4)) {
                LOGGER.warn("{} wraps every {} chunks; view distances above {} may show the world from both sides", dimension.location(), chunks, chunks / 2 - 4);
            }
        }
    }
}
