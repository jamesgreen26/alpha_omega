package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 2: block-side code meeting entities across frames. Each test puts the entity side a whole number of laps
 * away from the block side ({@link #LAP_X}, {@link #LAP_Z}), wherever the test area happens to be.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrameGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Tests that place a mock player change how chunks lift, so they run apart from everything else. */
    private static final String PLAYER_BATCH = "alpha_omega_players";
    private static final int W = Wrap.PERIOD;
    private static final int LAP_X = W;
    private static final int LAP_Z = -2 * W;

    /** The hopper searches from its own position; the item lies above an image of it. */
    @GameTest(template = TEMPLATE)
    public static void hopperCollectsItemsAcrossFrames(GameTestHelper helper) {
        BlockPos hopper = new BlockPos(3, 1, 3);
        helper.setBlock(hopper, Blocks.HOPPER);
        Vec3 above = Vec3.atCenterOf(helper.absolutePos(hopper.above())).add(LAP_X, 0, LAP_Z);
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), above.x, above.y, above.z, new ItemStack(Items.DIAMOND)));
        helper.succeedWhen(() -> helper.assertContainerContains(hopper, Items.DIAMOND));
    }

    @GameTest(template = TEMPLATE)
    public static void poiQueriesAnswerInTheCallersFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bell = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.BELL);

        helper.succeedWhen(() -> {
            PoiManager pois = level.getPoiManager();
            Optional<BlockPos> found = pois.findClosest(type -> type.is(PoiTypes.MEETING), origin, 16, PoiManager.Occupancy.ANY);
            helper.assertTrue(found.isPresent() && found.get().equals(bell), "expected bell at " + bell + ", found " + found);
            Optional<BlockPos> image = pois.findClosest(type -> type.is(PoiTypes.MEETING), origin.offset(LAP_X, 0, LAP_Z), 16, PoiManager.Occupancy.ANY);
            helper.assertTrue(image.isPresent() && image.get().equals(bell.offset(LAP_X, 0, LAP_Z)), "query from another image found " + image);

            int before = pois.getFreeTickets(bell);
            Optional<BlockPos> taken = pois.take(type -> type.is(PoiTypes.MEETING), (type, pos) -> true, origin, 16);
            helper.assertTrue(taken.isPresent() && taken.get().equals(bell), "take returned " + taken);
            helper.assertTrue(pois.getFreeTickets(Wrap.canon(bell)) == before - 1, "ticket not taken from the stored record");
            pois.release(taken.get());
            helper.assertTrue(pois.getFreeTickets(bell) == before, "ticket not released");
        });
    }

    /** The vibration happens at an image of a spot next to the sensor. */
    @GameTest(template = TEMPLATE)
    public static void sculkSensorHearsAcrossFrames(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(3, 1, 3);
        helper.setBlock(sensor, Blocks.SCULK_SENSOR);
        Vec3 source = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 3))).add(LAP_X, 0, LAP_Z);
        helper.runAfterDelay(2, () -> helper.getLevel().gameEvent(GameEvent.BLOCK_PLACE, source, GameEvent.Context.of(Blocks.STONE.defaultBlockState())));
        helper.succeedWhen(() -> helper.assertBlockProperty(sensor, SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE));
    }

    @GameTest(template = TEMPLATE)
    public static void mapCentersAreCanonical(GameTestHelper helper) {
        MapItemSavedData map = MapItemSavedData.createFresh(-5000.0, 3.0 * W + 20.0, (byte) 0, false, false, Level.OVERWORLD);
        helper.assertTrue(map.centerX >= 0 && map.centerX < W && map.centerZ >= 0 && map.centerZ < W,
            "map center not canonical: " + map.centerX + ", " + map.centerZ);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH)
    public static void chunksLiftToTheNearestPlayersFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos block = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos playerImage = block.offset(LAP_X, 0, LAP_Z);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(playerImage.getX() + 0.5, playerImage.getY(), playerImage.getZ() + 0.5);

        helper.assertTrue(Frames.lift(level, block).equals(playerImage), "did not lift to the player's frame: " + Frames.lift(level, block));
        helper.assertTrue(Frames.lift(level, Wrap.canon(block)).equals(playerImage), "canonical position did not lift to the player's frame");

        // R5 player proximity, from a position in another frame than the player.
        helper.assertTrue(level.hasNearbyAlivePlayer(block.getX(), block.getY(), block.getZ(), 4.0), "player near an image not detected");
        helper.assertTrue(level.getNearestPlayer(block.getX(), block.getY(), block.getZ(), 4.0, false) == player, "nearest player not found across frames");

        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** A spawner runs for a player near an image of it (its ticker is lifted into the player's frame). */
    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH)
    public static void spawnerSeesPlayerAcrossFrames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawner = new BlockPos(3, 1, 3);
        helper.setBlock(spawner, Blocks.SPAWNER);
        SpawnerBlockEntity blockEntity = helper.getBlockEntity(spawner);
        blockEntity.setEntityId(EntityType.PIG, level.getRandom());

        BlockPos near = helper.absolutePos(spawner).offset(2 + LAP_X, 0, LAP_Z);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);

        helper.runAfterDelay(10, () -> {
            short delay = blockEntity.getSpawner().save(new CompoundTag()).getShort("Delay");
            level.getServer().getPlayerList().remove(player);
            helper.assertTrue(delay < 20, "spawner did not notice the player (delay still " + delay + ")");
            helper.succeed();
        });
    }
}
