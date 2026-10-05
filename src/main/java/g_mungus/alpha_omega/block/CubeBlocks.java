package g_mungus.alpha_omega.block;

import g_mungus.alpha_omega.AlphaOmegaMod;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CubeBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlphaOmegaMod.MOD_ID);

    public static final DeferredBlock<EdgeAirBlock> EDGE_AIR = BLOCKS.register("edge_air", () -> new EdgeAirBlock(unbreakable()
        .noCollission()));
    // Not noCollission: that would also stop it occluding. Its collision shape is empty instead; forceSolidOn makes
    // fluids, rain and the motion-blocking heightmaps stop at it.
    public static final DeferredBlock<FillerBlock> FILLER = BLOCKS.register("filler", () -> new FillerBlock(unbreakable()
        .forceSolidOn()
        .isSuffocating((state, level, pos) -> false)
        .isViewBlocking((state, level, pos) -> false)));
    // Filler that collides like the neighbour's block at the same cell, in the band under the barrier. Its shape is
    // read on every query, so whatever else vanilla would derive from it is stated outright.
    public static final DeferredBlock<EdgeFillerBlock> EDGE_FILLER = BLOCKS.register("edge_filler", () -> new EdgeFillerBlock(unbreakable()
        .forceSolidOn()
        .dynamicShape()
        .isSuffocating((state, level, pos) -> false)
        .isViewBlocking((state, level, pos) -> false)
        .isRedstoneConductor((state, level, pos) -> false)));

    public static final DeferredBlock<EdgeBedrockBlock> EDGE_BEDROCK = BLOCKS.register("edge_bedrock", () -> new EdgeBedrockBlock(
        BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).noLootTable().isValidSpawn(Blocks::never)));

    private CubeBlocks() {
    }

    /** Whether a state is filler of either kind: a cell another face owns. */
    public static boolean isFiller(BlockState state) {
        return state.is(FILLER.get()) || state.is(EDGE_FILLER.get());
    }

    private static BlockBehaviour.Properties unbreakable() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .pushReaction(PushReaction.BLOCK)
            .isValidSpawn(Blocks::never);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
