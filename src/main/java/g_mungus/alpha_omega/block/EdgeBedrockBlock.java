package g_mungus.alpha_omega.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The solid part of the barrier between faces (design §3): bedrock to look at. Every face storing a barrier cell has
 * its own copy; their faces against the cube's surroundings are each drawn by one copy (the others face filler), but
 * the faces along the edge, against open barrier cells, would be drawn by every copy in the same place. Only the
 * primary copy (the one on the lowest slot) draws those.
 */
public class EdgeBedrockBlock extends Block {

    public static final BooleanProperty PRIMARY = BooleanProperty.create("primary");

    public EdgeBedrockBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PRIMARY, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRIMARY);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction direction) {
        return !state.getValue(PRIMARY) && neighbour.getBlock() instanceof EdgeAirBlock || super.skipRendering(state, neighbour, direction);
    }
}
