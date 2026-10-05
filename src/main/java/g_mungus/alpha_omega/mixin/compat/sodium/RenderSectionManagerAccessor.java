package g_mungus.alpha_omega.mixin.compat.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.util.ArrayDeque;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSectionManager.class)
public interface RenderSectionManagerAccessor {

    @Accessor("sectionByPosition")
    Long2ReferenceMap<RenderSection> alpha_omega$sections();

    @Accessor("taskLists")
    Map<TaskQueueType, ArrayDeque<RenderSection>> alpha_omega$taskLists();

    @Accessor("chunkRenderer")
    ChunkRenderer alpha_omega$chunkRenderer();

    @Accessor("sortBehavior")
    SortBehavior alpha_omega$sortBehavior();
}
