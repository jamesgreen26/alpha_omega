package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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
 * away from the block side ({@link #lapX()}, {@link #lapZ()}), wherever the test area happens to be.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrameGameTests {

    private static Wrap wrap() {
        return Wraps.overworld();
    }

    private static int period() {
        return wrap().period;
    }

    private static int chunks() {
        return wrap().chunkPeriod;
    }

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Tests that place a mock player change how chunks lift, so they run apart from everything else. */
    private static final String PLAYER_BATCH = "alpha_omega_players";
    /** The entity side sits this far from the block side (a method: the period is only known once a world loads). */
    private static int lapX() {
        return period();
    }

    private static int lapZ() {
        return -2 * period();
    }

    /** The hopper searches from its own position; the item lies above an image of it. */
    @GameTest(template = TEMPLATE)
    public static void hopperCollectsItemsAcrossFrames(GameTestHelper helper) {
        BlockPos hopper = new BlockPos(3, 1, 3);
        helper.setBlock(hopper, Blocks.HOPPER);
        Vec3 above = Vec3.atCenterOf(helper.absolutePos(hopper.above())).add(lapX(), 0, lapZ());
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
            Optional<BlockPos> image = pois.findClosest(type -> type.is(PoiTypes.MEETING), origin.offset(lapX(), 0, lapZ()), 16, PoiManager.Occupancy.ANY);
            helper.assertTrue(image.isPresent() && image.get().equals(bell.offset(lapX(), 0, lapZ())), "query from another image found " + image);

            int before = pois.getFreeTickets(bell);
            Optional<BlockPos> taken = pois.take(type -> type.is(PoiTypes.MEETING), (type, pos) -> true, origin, 16);
            helper.assertTrue(taken.isPresent() && taken.get().equals(bell), "take returned " + taken);
            helper.assertTrue(pois.getFreeTickets(wrap().canon(bell)) == before - 1, "ticket not taken from the stored record");
            pois.release(taken.get());
            helper.assertTrue(pois.getFreeTickets(bell) == before, "ticket not released");
        });
    }

    /** The vibration happens at an image of a spot next to the sensor. */
    @GameTest(template = TEMPLATE)
    public static void sculkSensorHearsAcrossFrames(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(3, 1, 3);
        helper.setBlock(sensor, Blocks.SCULK_SENSOR);
        Vec3 source = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 3))).add(lapX(), 0, lapZ());
        helper.runAfterDelay(2, () -> helper.getLevel().gameEvent(GameEvent.BLOCK_PLACE, source, GameEvent.Context.of(Blocks.STONE.defaultBlockState())));
        helper.succeedWhen(() -> helper.assertBlockProperty(sensor, SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE));
    }

    @GameTest(template = TEMPLATE)
    public static void mapCentersAreCanonical(GameTestHelper helper) {
        MapItemSavedData map = MapItemSavedData.createFresh(-5000.0, 3.0 * period() + 20.0, (byte) 0, false, false, Level.OVERWORLD);
        helper.assertTrue(map.centerX >= 0 && map.centerX < period() && map.centerZ >= 0 && map.centerZ < period(),
            "map center not canonical: " + map.centerX + ", " + map.centerZ);
        helper.succeed();
    }

    /**
     * A player arriving at another image of loaded terrain is brought into the terrain's island frame, block-side
     * code there lifts into that same frame, and proximity checks from any image find the player.
     */
    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH)
    public static void playersJoinTheTerrainsFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos block = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos playerImage = block.offset(lapX(), 0, lapZ());
        ServerPlayer player = TestPlayers.mock(helper);
        player.moveTo(playerImage.getX() + 0.5, playerImage.getY(), playerImage.getZ() + 0.5);

        helper.runAfterDelay(1, () -> {
            BlockPos lifted = Frames.lift(level, playerImage);
            helper.assertTrue(wrap().canon(lifted).equals(wrap().canon(block)), "lift changed the canonical position");
            helper.assertTrue(player.blockPosition().equals(lifted), "player " + player.blockPosition() + " not in the terrain's frame " + lifted);

            // R5 player proximity, from an image in another frame than the player.
            helper.assertTrue(level.hasNearbyAlivePlayer(playerImage.getX(), playerImage.getY(), playerImage.getZ(), 4.0), "player near an image not detected");
            helper.assertTrue(level.getNearestPlayer(playerImage.getX(), playerImage.getY(), playerImage.getZ(), 4.0, false) == player, "nearest player not found across frames");

            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * Containers keep their block entity at the canonical position; a player standing next to another image of it
     * can still use it (menus check reach every tick and would close at once otherwise).
     */
    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH)
    public static void containersStayOpenAcrossFrames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = new BlockPos(3, 1, 3);
        BlockPos furnace = new BlockPos(4, 1, 3);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(furnace, Blocks.FURNACE);
        BlockPos stand = helper.absolutePos(new BlockPos(3, 1, 4)).offset(lapX(), 0, lapZ());
        ServerPlayer player = TestPlayers.mock(helper);
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);

        Container chestContainer = helper.getBlockEntity(chest);
        Container furnaceContainer = helper.getBlockEntity(furnace);
        boolean chestValid = chestContainer.stillValid(player);
        boolean furnaceValid = furnaceContainer.stillValid(player);
        boolean reach = player.canInteractWithBlock(helper.absolutePos(chest), 1.0);
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(chestValid, "chest menu would close for a player next to another image of it");
        helper.assertTrue(furnaceValid, "furnace menu would close for a player next to another image of it");
        helper.assertTrue(reach, "player cannot reach a block next to it from another frame");
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

        BlockPos near = helper.absolutePos(spawner).offset(2 + lapX(), 0, lapZ());
        ServerPlayer player = TestPlayers.mock(helper);
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);

        helper.runAfterDelay(10, () -> {
            short delay = blockEntity.getSpawner().save(new CompoundTag()).getShort("Delay");
            level.getServer().getPlayerList().remove(player);
            helper.assertTrue(delay < 20, "spawner did not notice the player (delay still " + delay + ")");
            helper.succeed();
        });
    }
}
