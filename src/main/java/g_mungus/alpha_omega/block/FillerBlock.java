package g_mungus.alpha_omega.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fills the part of a face's storage that another face owns (design §3). Invisible and walk-through, so things
 * crossing the barrier can reach the point where they transfer, but it occludes neighbouring faces and blocks
 * light: the barrier's sides facing the other face are drawn only by that face, never twice.
 */
public class FillerBlock extends Block {

    public FillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Nothing is drawn here, so landing on it (edge filler collides) raises no dust. */
    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState sameState, LivingEntity entity, int particles) {
        return true;
    }

    /** Nor does running on it. */
    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return true;
    }
}
