package g_mungus.alpha_omega.gametest;

import com.mojang.serialization.JsonOps;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import g_mungus.alpha_omega.network.CubePayload;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The gametest world is a cube world, and its settings survive saving and the trip to clients. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CubeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void overworldIsACube(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CubeGeometry geometry = Cube.of(level);
        helper.assertTrue(geometry != null, "the gametest overworld should be a cube world");
        helper.assertTrue(geometry.settings.equals(AlphaOmegaConfig.defaults()), "preset without settings should take the config: " + geometry.settings);
        helper.assertTrue(geometry.planeY == level.getSeaLevel(), "face plane at sea level");
        helper.assertTrue(Cube.of(level.getServer().getLevel(Level.NETHER)) == null, "the Nether is not a cube");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void settingsSurviveSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CubeChunkGenerator generator = new CubeChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(),
            ((CubeChunkGenerator) level.getChunkSource().getGenerator()).generatorSettings(), new CubeSettings(7, CubeSettings.SunAxis.POLAR, 3.0));
        RegistryOps<com.google.gson.JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
        helper.assertTrue(json.toString().contains("\"face_chunks\":7") && json.toString().contains("\"horizontal_scale\":3.0"), "settings are written explicitly: " + json);
        ChunkGenerator decoded = ChunkGenerator.CODEC.parse(ops, json).getOrThrow();
        helper.assertTrue(decoded instanceof CubeChunkGenerator cube && cube.cube().equals(generator.cube()), "settings round trip");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geometryReachesClients(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        CubePayload.STREAM_CODEC.encode(buffer, CubePayload.of(geometry));
        CubeGeometry received = CubePayload.STREAM_CODEC.decode(buffer).geometry().orElseThrow();
        helper.assertTrue(received.toString().equals(geometry.toString()) && received.minY == geometry.minY && received.maxY == geometry.maxY
            && received.settings.equals(geometry.settings), "client geometry matches the server's");
        helper.succeed();
    }
}
