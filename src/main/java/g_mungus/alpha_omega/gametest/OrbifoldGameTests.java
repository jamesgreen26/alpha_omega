package g_mungus.alpha_omega.gametest;

import com.mojang.serialization.JsonOps;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.network.OrbifoldPayload;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.Settings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The gametest world is an orbifold world, its settings survive saving and the trip to clients, and half turns are exact. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrbifoldGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void overworldIsAnOrbifold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbifoldGeometry geometry = Orbifold.of(level);
        helper.assertTrue(geometry != null, "the gametest overworld should be an orbifold world");
        OrbifoldSettings settings = new OrbifoldSettings(geometry.sizeFactor, geometry.bandChunks);
        helper.assertTrue(settings.equals(AlphaOmegaConfig.defaults()), "preset without settings should take the config: " + settings);
        helper.assertTrue(Orbifold.of(level.getServer().getLevel(Level.NETHER)) == null, "the Nether is not an orbifold (until phase 10)");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void settingsSurviveSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbifoldChunkGenerator current = (OrbifoldChunkGenerator) level.getChunkSource().getGenerator();
        OrbifoldChunkGenerator generator = new OrbifoldChunkGenerator(current.getBiomeSource(), current.generatorSettings(), new OrbifoldSettings(8, 6));
        RegistryOps<com.google.gson.JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
        helper.assertTrue(json.toString().contains("\"size_factor\":8") && json.toString().contains("\"band_chunks\":6"),
            "settings are written explicitly: " + json);
        ChunkGenerator decoded = ChunkGenerator.CODEC.parse(ops, json).getOrThrow();
        helper.assertTrue(decoded instanceof OrbifoldChunkGenerator orbifold && orbifold.orbifold().equals(generator.orbifold()), "settings round trip");
        json.getAsJsonObject().addProperty("size_factor", 3);
        helper.assertTrue(ChunkGenerator.CODEC.parse(ops, json).isError(), "a size factor other than 2, 4 or 8 is refused");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geometryReachesClients(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        OrbifoldPayload.STREAM_CODEC.encode(buffer, OrbifoldPayload.of(geometry));
        OrbifoldGeometry received = OrbifoldPayload.STREAM_CODEC.decode(buffer).geometry().orElseThrow();
        helper.assertTrue(received.toString().equals(geometry.toString()), "client geometry matches the server's");
        helper.succeed();
    }

    /** A dedicated server makes an orbifold world unless its properties name another level type, and writes that down. */
    @GameTest(template = TEMPLATE)
    public static void dedicatedServersDefaultToOrbifolds(GameTestHelper helper) {
        helper.assertTrue(levelType(new Properties()).equals(OrbifoldChunkGenerator.PRESET.location().toString()), "no level type should default to an orbifold");
        Properties flat = new Properties();
        flat.setProperty("level-type", "minecraft:flat");
        helper.assertTrue(levelType(flat).equals("minecraft:flat"), "a named level type should be kept");
        helper.succeed();
    }

    /**
     * A half turn is exact on every vanilla block state: turning by {@link Rotation#CLOCKWISE_180} twice gives the state
     * back. The band's copies rely on it (plan §2.2).
     */
    @GameTest(template = TEMPLATE)
    public static void halfTurnTwiceIsIdentity(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                checked++;
                BlockState back = state.rotate(Rotation.CLOCKWISE_180).rotate(Rotation.CLOCKWISE_180);
                if (back != state && wrong.size() < 10) wrong.add(state + " -> " + back);
            }
        }
        helper.assertTrue(checked > 20000, "only " + checked + " vanilla states found");
        helper.assertTrue(wrong.isEmpty(), "half turn twice changed: " + wrong);
        helper.succeed();
    }

    /** The level type a dedicated server reads from some properties, as it writes it back to {@code server.properties}. */
    private static String levelType(Properties properties) {
        try {
            Path file = Files.createTempFile("alpha_omega", ".properties");
            try {
                new DedicatedServerProperties(properties).store(file);
                return Settings.loadFromFile(file).getProperty("level-type");
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
