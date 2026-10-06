package g_mungus.alpha_omega.mixin.polish;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.alpha_omega.item.Maps;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Map markers ({@link Maps}): each at its image nearest the map's centre, turned with it. */
@Mixin(MapItemSavedData.class)
abstract class MapItemSavedDataMixin {

    @Shadow
    @Final
    public int centerX;

    @Shadow
    @Final
    public int centerZ;

    @WrapMethod(method = "addDecoration")
    private void alpha_omega$nearestImage(Holder<MapDecorationType> type, @Nullable LevelAccessor accessor, String id, double x, double z, double rotation,
                                          @Nullable Component name, Operation<Void> original) {
        Level level = accessor instanceof Level l ? l : accessor instanceof ServerLevelAccessor s ? s.getLevel() : null;
        OrbifoldGeometry geometry = level == null ? null : Orbifold.of(level);
        double[] at = Maps.decoration(geometry, this.centerX, this.centerZ, x, z, rotation);
        original.call(type, accessor, id, at[0], at[1], at[2], name);
    }
}
