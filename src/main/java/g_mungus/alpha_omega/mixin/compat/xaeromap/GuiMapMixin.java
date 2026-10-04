package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.compat.XaeroWorldMapView;
import g_mungus.alpha_omega.compat.XaeroWraps;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapMath;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.region.LeveledRegion;
import xaero.map.region.texture.RegionTexture;
import xaero.map.world.MapDimension;

/**
 * Coordinates shown on the world map are canonical, like F3. The map itself keeps lifted coordinates so it scrolls
 * continuously; only the text changes.
 */
@Mixin(GuiMap.class)
abstract class GuiMapMixin {

    /** The region (lifted, at the level drawn) whose textures are being drawn; set as the map looks it up. */
    @Unique
    private int alpha_omega$drawnX;
    @Unique
    private int alpha_omega$drawnZ;
    @Unique
    private int alpha_omega$drawnLevel;

    /**
     * Zoomed out, a region's place may be kept in other canonical regions (the world is not a whole number of the
     * level's regions, or the canonical window starts part way into one). Remember which region is drawn, and if
     * the lookup finds nothing there, offer any region holding part of it: its textures are fetched from wherever
     * they are kept (below).
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/MapProcessor;getLeveledRegion(IIII)Lxaero/map/region/LeveledRegion;"))
    private LeveledRegion<?> alpha_omega$rememberDrawnRegion(MapProcessor processor, int caveLayer, int x, int z, int level, Operation<LeveledRegion<?>> original) {
        this.alpha_omega$drawnX = x;
        this.alpha_omega$drawnZ = z;
        this.alpha_omega$drawnLevel = level;
        LeveledRegion<?> region = original.call(processor, caveLayer, x, z, level);
        Wrap wrap = XaeroWorldMapView.wrap();
        int period = XaeroWraps.regionPeriod(wrap);
        if (region != null || period == 0 || level == 0) return region;
        int origin = XaeroWraps.regionOrigin(wrap);
        MapDimension dimension = processor.getMapWorld().getCurrentDimension();
        if (dimension == null) return null;
        int side = 1 << level;
        for (int i = 0; i < side; i++) {
            int leafX = WrapMath.canon(x * side + i, period, origin);
            for (int j = 0; j < side; j++) {
                int leafZ = WrapMath.canon(z * side + j, period, origin);
                LeveledRegion<?> holder = dimension.getLayeredMapRegions().get(caveLayer, leafX >> level, leafZ >> level, level);
                if (holder != null) return holder;
            }
        }
        return null;
    }

    /** A texture of the drawn region, from the canonical region keeping that place. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lxaero/map/region/LeveledRegion;getTexture(II)Lxaero/map/region/texture/RegionTexture;"))
    private RegionTexture<?> alpha_omega$textureFromItsRegion(LeveledRegion<?> region, int x, int z, Operation<RegionTexture<?>> original) {
        int perRegion = XaeroWraps.TEXTURES_PER_REGION;
        return this.alpha_omega$texture(region, this.alpha_omega$drawnLevel,
            this.alpha_omega$drawnX * perRegion + x, this.alpha_omega$drawnZ * perRegion + z, original);
    }

    /**
     * While a region's own textures load, the map draws part of its top-level region's texture, picked by the drawn
     * region's position: fetch it from the top-level region keeping that place.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 2,
        target = "Lxaero/map/region/LeveledRegion;getTexture(II)Lxaero/map/region/texture/RegionTexture;"))
    private RegionTexture<?> alpha_omega$rootTextureFromItsRegion(LeveledRegion<?> root, int x, int z, Operation<RegionTexture<?>> original) {
        int perRegion = XaeroWraps.TEXTURES_PER_REGION;
        int levelDiff = 3 - this.alpha_omega$drawnLevel;
        int rootX = (this.alpha_omega$drawnX >> levelDiff) * perRegion + x;
        int rootZ = (this.alpha_omega$drawnZ >> levelDiff) * perRegion + z;
        return this.alpha_omega$texture(root, 3, rootX, rootZ, original);
    }

    /** Texture {@code (textureX, textureZ)}, numbered across {@code level}, from the region keeping it. */
    @Unique
    private RegionTexture<?> alpha_omega$texture(LeveledRegion<?> region, int level, int textureX, int textureZ, Operation<RegionTexture<?>> original) {
        int perRegion = XaeroWraps.TEXTURES_PER_REGION;
        MapDimension dimension = region.getDim();
        Wrap wrap = dimension == null ? Wrap.NONE : XaeroWraps.of(dimension.getDimId());
        int period = XaeroWraps.regionPeriod(wrap);
        if (period == 0 || region.getLevel() != level) {
            return original.call(region, Math.floorMod(textureX, perRegion), Math.floorMod(textureZ, perRegion));
        }
        int origin = XaeroWraps.regionOrigin(wrap);
        int keptX = XaeroWraps.canonTexture(textureX, period, origin, level);
        int keptZ = XaeroWraps.canonTexture(textureZ, period, origin, level);
        int regionX = Math.floorDiv(keptX, perRegion);
        int regionZ = Math.floorDiv(keptZ, perRegion);
        LeveledRegion<?> holder = regionX == region.getRegionX() && regionZ == region.getRegionZ()
            ? region : dimension.getLayeredMapRegions().get(region.getCaveLayer(), regionX, regionZ, level);
        return holder == null ? null : original.call(holder, Math.floorMod(keptX, perRegion), Math.floorMod(keptZ, perRegion));
    }

    /** The cursor coordinates line at the top of the screen. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/graphics/MapRenderHelper;drawCenteredStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFFFFLcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
        index = 2)
    private String alpha_omega$canonicalCursor(String text) {
        Wrap wrap = XaeroWorldMapView.wrap();
        return wrap.enabled() ? XaeroWraps.canonicalCoordinates(text, wrap) : text;
    }

    /** The coordinates line in the right-click menu. */
    @WrapOperation(method = "getRightClickOptions", at = @At(value = "INVOKE",
        target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String alpha_omega$canonicalRightClick(String format, Object[] args, Operation<String> original) {
        Wrap wrap = XaeroWorldMapView.wrap();
        if (wrap.enabled() && format.startsWith("X: %1$d") && args.length >= 3 && args[0] instanceof Integer x && args[2] instanceof Integer z) {
            args = Arrays.copyOf(args, args.length);
            args[0] = wrap.canonBlock(x);
            args[2] = wrap.canonBlock(z);
        }
        return original.call(format, args);
    }
}
