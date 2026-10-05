package g_mungus.alpha_omega.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("level")
    ClientLevel alpha_omega$level();

    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher alpha_omega$sectionRenderDispatcher();

    @Accessor("viewArea")
    net.minecraft.client.renderer.ViewArea alpha_omega$viewArea();

    @Accessor("viewArea")
    void alpha_omega$setViewArea(net.minecraft.client.renderer.ViewArea area);

    @Accessor("sectionOcclusionGraph")
    net.minecraft.client.renderer.SectionOcclusionGraph alpha_omega$sectionOcclusionGraph();

    @Invoker("renderEntity")
    void alpha_omega$renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose, MultiBufferSource buffers);
}
