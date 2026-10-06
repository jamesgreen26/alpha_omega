package g_mungus.alpha_omega.mixin.nether;

import net.minecraft.BlockUtil;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's placement of an arrival in an exit portal, for {@code nether.NetherPortals}. */
@Mixin(NetherPortalBlock.class)
public interface NetherPortalBlockInvoker {

    @Invoker("createDimensionTransition")
    static DimensionTransition alpha_omega$createDimensionTransition(ServerLevel level, BlockUtil.FoundRectangle exit, Direction.Axis axis,
        Vec3 relative, Entity entity, Vec3 motion, float yRot, float xRot, DimensionTransition.PostDimensionTransition after) {
        throw new AssertionError();
    }
}
