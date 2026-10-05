package g_mungus.alpha_omega.block;

import g_mungus.alpha_omega.AlphaOmegaMod;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    private CubeBlocks() {
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
