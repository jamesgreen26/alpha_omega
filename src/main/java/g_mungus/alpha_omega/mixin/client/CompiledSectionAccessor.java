package g_mungus.alpha_omega.mixin.client;

import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers a compiled section has geometry in, so neighbour sections can be sorted into per-layer draw lists. */
@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface CompiledSectionAccessor {

    @Accessor("hasBlocks")
    Set<RenderType> alpha_omega$hasBlocks();
}
