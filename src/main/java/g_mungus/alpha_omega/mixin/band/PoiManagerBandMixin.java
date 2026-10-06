package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** POIs are registered at the owner only (RS §3.5): a section scan skips cells its copy does not own. */
@Mixin(PoiManager.class)
abstract class PoiManagerBandMixin {

    @ModifyVariable(method = "updateFromSection", at = @At("HEAD"), argsOnly = true)
    private BiConsumer<BlockPos, Holder<PoiType>> alpha_omega$ownersOnly(BiConsumer<BlockPos, Holder<PoiType>> adder, LevelChunkSection section,
        SectionPos pos) {
        if (!(((SectionStorageAccessor) this).alpha_omega$levelHeightAccessor() instanceof ServerLevel level)) return adder;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || !Band.isLinked(geometry, pos.minBlockX(), pos.minBlockZ())) return adder;
        return (at, type) -> {
            if (Ownership.isOwner(level, at)) {
                adder.accept(at, type);
            } else {
                BandCounters.poiScanSkipped++;
            }
        };
    }
}
