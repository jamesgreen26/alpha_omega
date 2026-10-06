package g_mungus.alpha_omega.mixin.polish;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.item.Maps;
import g_mungus.alpha_omega.orbifold.NearestImages;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Maps ({@link Maps}): centred from the source position; updated from the holder's image nearest the centre. */
@Mixin(MapItem.class)
abstract class MapItemMixin {

    @WrapMethod(method = "create")
    private static ItemStack alpha_omega$centreFromSource(Level level, int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking,
                                                          Operation<ItemStack> original) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return original.call(level, x, z, scale, trackingPosition, unlimitedTracking);
        int[] source = Maps.centreFrom(geometry, x, z);
        return original.call(level, source[0], source[1], scale, trackingPosition, unlimitedTracking);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double alpha_omega$holderX(Entity holder, Operation<Double> original, @Local(argsOnly = true) MapItemSavedData data) {
        double x = original.call(holder);
        OrbifoldGeometry geometry = Orbifold.of(holder.level());
        return geometry == null ? x : NearestImages.nearest(geometry, x, holder.getZ(), data.centerX, data.centerZ)[0];
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double alpha_omega$holderZ(Entity holder, Operation<Double> original, @Local(argsOnly = true) MapItemSavedData data) {
        double z = original.call(holder);
        OrbifoldGeometry geometry = Orbifold.of(holder.level());
        return geometry == null ? z : NearestImages.nearest(geometry, holder.getX(), z, data.centerX, data.centerZ)[1];
    }

    /** Pixels past the stored footprint have nothing to show: an empty chunk, rather than loading one. */
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk alpha_omega$footprintOnly(Level level, int chunkX, int chunkZ, Operation<LevelChunk> original) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null || geometry.inFootprintChunk(chunkX, chunkZ)) return original.call(level, chunkX, chunkZ);
        return new EmptyLevelChunk(level, new ChunkPos(chunkX, chunkZ), level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }
}
