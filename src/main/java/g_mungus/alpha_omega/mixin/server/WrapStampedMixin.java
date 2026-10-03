package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Objects created after level construction, stamped with their level's {@link Wrap} where they are created:
 * entity sections (by their storage), worldgen caches (by the generation task), noise chunks (by their noise),
 * and player chunk views (by the chunk map).
 */
@Mixin(value = {EntitySection.class, StaticCache2D.class, NoiseChunk.class}, targets = "net.minecraft.server.level.ChunkTrackingView$Positioned")
abstract class WrapStampedMixin implements WrapHolder {

    @Unique
    private Wrap alpha_omega$wrap = Wrap.NONE;

    @Override
    public Wrap alpha_omega$wrap() {
        return this.alpha_omega$wrap;
    }

    @Override
    public void alpha_omega$setWrap(Wrap wrap) {
        this.alpha_omega$wrap = wrap;
    }
}
