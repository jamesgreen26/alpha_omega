package g_mungus.alpha_omega.mixin.client;

import java.util.concurrent.Future;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The background rebuild of the visible-section graph, so a swap of view areas can wait for the first one. */
@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor {

    @Accessor("fullUpdateTask")
    Future<?> alpha_omega$fullUpdateTask();
}
