package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.neighbour.CubeTrackingView;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla diffs two plain square views cheaply but, for any other view, drops every old chunk and resends every new
 * one. Cube views are diffed as sets, so only the chunks that really change are sent or forgotten.
 */
@Mixin(ChunkTrackingView.class)
interface ChunkTrackingViewMixin {

    @Inject(method = "difference", at = @At("HEAD"), cancellable = true)
    private static void alpha_omega$cubeDifference(ChunkTrackingView old, ChunkTrackingView now, Consumer<ChunkPos> added,
                                                   Consumer<ChunkPos> removed, CallbackInfo ci) {
        if (!(old instanceof CubeTrackingView) && !(now instanceof CubeTrackingView)) return;
        ci.cancel();
        if (old.equals(now)) return;
        LongOpenHashSet before = new LongOpenHashSet();
        old.forEach(pos -> before.add(pos.toLong()));
        LongOpenHashSet after = new LongOpenHashSet();
        now.forEach(pos -> after.add(pos.toLong()));
        before.forEach(pos -> {
            if (!after.contains(pos)) removed.accept(new ChunkPos(pos));
        });
        after.forEach(pos -> {
            if (!before.contains(pos)) added.accept(new ChunkPos(pos));
        });
    }
}
