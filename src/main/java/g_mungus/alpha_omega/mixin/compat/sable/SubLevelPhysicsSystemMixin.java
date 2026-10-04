package g_mungus.alpha_omega.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import g_mungus.alpha_omega.frame.Frames;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Terrain reaches physics at the lifted sections sub-levels collide with, but block changes arrive from canonical
 * storage: lift each change into its island's frame (R4) so it updates the section physics knows.
 */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
abstract class SubLevelPhysicsSystemMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(method = "handleBlockChange", at = @At("HEAD"), argsOnly = true)
    private SectionPos alpha_omega$liftChange(SectionPos section) {
        long offset = Frames.lapOffset(this.level, section.x(), section.z());
        if (offset == 0) return section;
        return SectionPos.of(section.x() + (Frames.offsetX(offset) >> 4), section.y(), section.z() + (Frames.offsetZ(offset) >> 4));
    }
}
