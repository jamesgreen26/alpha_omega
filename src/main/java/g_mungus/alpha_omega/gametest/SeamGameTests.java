package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.network.PacketNormalization;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 1 behavior: storage is periodic (any image of a position reaches the same data), and chunks on either
 * side of the seam behave as neighbors.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SeamGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int W = Wrap.PERIOD;
    private static final int N = Wrap.CHUNK_PERIOD;

    @GameTest(template = TEMPLATE)
    public static void blocksArePeriodic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));

        level.setBlockAndUpdate(pos.offset(W, 0, -W), Blocks.GOLD_BLOCK.defaultBlockState());

        helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "block not visible at original image");
        helper.assertTrue(level.getBlockState(pos.offset(-2 * W, 0, 3 * W)).is(Blocks.GOLD_BLOCK), "block not visible at distant image");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockEntitiesAreStoredCanonically(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos image = pos.offset(W, 0, W);

        level.setBlockAndUpdate(image, Blocks.CHEST.defaultBlockState());

        BlockEntity atPos = level.getBlockEntity(pos);
        helper.assertTrue(atPos != null, "no block entity at original image");
        helper.assertTrue(atPos == level.getBlockEntity(image), "images resolve to different block entities");
        helper.assertTrue(atPos.getBlockPos().equals(Wrap.canon(pos)), "block entity position is not canonical: " + atPos.getBlockPos());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scheduledTicksAreCanonical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));

        level.scheduleTick(pos.offset(-W, 0, 0), Blocks.REDSTONE_LAMP, 1000);

        helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos, Blocks.REDSTONE_LAMP), "tick not found at original image");
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(Wrap.canon(pos), Blocks.REDSTONE_LAMP), "tick not found at canonical position");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lightIsPeriodic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));

        level.setBlockAndUpdate(pos.offset(0, 0, W), Blocks.GLOWSTONE.defaultBlockState());

        helper.succeedWhen(() -> {
            int light = level.getBrightness(LightLayer.BLOCK, pos.above().offset(-W, 0, 0));
            helper.assertTrue(light == 14, "expected block light 14 above glowstone, got " + light);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void entitySectionsArePeriodic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        pig.setNoAi(true);
        pig.setPos(pig.getX() + W, pig.getY(), pig.getZ() - W);

        List<Pig> found = level.getEntities(EntityType.PIG, new AABB(pig.blockPosition()).inflate(1), p -> p == pig);
        helper.assertTrue(found.size() == 1, "pig not found after moving by a whole lap");
        helper.assertTrue(level.isPositionEntityTicking(pig.blockPosition()), "pig's lifted position is not entity-ticking");
        pig.discard();
        helper.succeed();
    }

    /** Forcing one chunk at the seam loads its neighbors on the far side through ticket propagation. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ticketsPropagateAcrossSeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int cz = farChunkZ(helper);
        level.setChunkForced(0, cz, true);

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getChunkSource().hasChunk(N - 1, cz), "neighbor across seam not loaded");
            helper.assertTrue(level.getChunkSource().hasChunk(-1, cz), "neighbor across seam not reachable by lifted coordinate");
            level.setChunkForced(0, cz, false);
        });
    }

    /** A light source at the last block before the seam lights the first block after it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void lightCrossesSeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int cz = farChunkZ(helper);
        level.setChunkForced(N - 1, cz, true);
        level.setChunkForced(0, cz, true);
        BlockPos source = new BlockPos(W - 1, level.getMaxBuildHeight() - 8, SectionPos.sectionToBlockCoord(cz, 8));
        boolean[] placed = {false};

        helper.succeedWhen(() -> {
            if (!placed[0]) {
                helper.assertTrue(level.getChunkSource().hasChunk(N - 1, cz) && level.getChunkSource().hasChunk(0, cz), "seam chunks not loaded yet");
                level.setBlockAndUpdate(source, Blocks.GLOWSTONE.defaultBlockState());
                placed[0] = true;
            }
            int canonical = level.getBrightness(LightLayer.BLOCK, new BlockPos(0, source.getY(), source.getZ()));
            int lifted = level.getBrightness(LightLayer.BLOCK, new BlockPos(W, source.getY(), source.getZ()));
            helper.assertTrue(canonical == 14 && lifted == 14, "expected light 14 across seam, got " + canonical + " / " + lifted);
            level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
            level.setChunkForced(N - 1, cz, false);
            level.setChunkForced(0, cz, false);
        });
    }

    /** Serverbound positions in any image are rewritten to the image nearest the server-side player. */
    @GameTest(template = TEMPLATE)
    public static void serverboundPositionsAreNormalized(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = player.position();

        ServerboundMovePlayerPacket move = new ServerboundMovePlayerPacket.Pos(at.x + 0.5 - 3 * W, at.y, at.z + W, true);
        PacketNormalization.normalize(move, player.connection);
        helper.assertTrue(move.getX(0) == at.x + 0.5 && move.getZ(0) == at.z, "move not normalized: " + move.getX(0) + ", " + move.getZ(0));

        BlockPos target = player.blockPosition().below();
        ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(target).add(W, 0, -W), Direction.UP, target.offset(W, 0, -W), false), 0);
        PacketNormalization.normalize(use, player.connection);
        helper.assertTrue(use.getHitResult().getBlockPos().equals(target), "use target not normalized: " + use.getHitResult().getBlockPos());
        helper.assertTrue(use.getHitResult().getLocation().equals(Vec3.atCenterOf(target)), "hit location not normalized");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** A chunk row on the seam that nothing else (spawn chunks, the test itself) keeps loaded. */
    private static int farChunkZ(GameTestHelper helper) {
        return Wrap.canonChunk(SectionPos.blockToSectionCoord(helper.absolutePos(BlockPos.ZERO).getZ()) + N / 2);
    }
}
