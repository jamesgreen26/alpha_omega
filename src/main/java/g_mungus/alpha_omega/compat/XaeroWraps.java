package g_mungus.alpha_omega.compat;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapMath;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Wrapping as Xaero's maps see it. Their map storage is keyed by canonical region, so it loops like the terrain;
 * canonical regions are the ones holding the canonical window, so they line up with the world save's region files
 * (which the world map reads in singleplayer) and lap 0 maps to itself;
 * everything drawn on a map (waypoints, markers) is placed at its image nearest the view; and coordinates shown to the
 * player are canonical, matching F3.
 */
public final class XaeroWraps {

    private static final Pattern COORDINATES = Pattern.compile("X: (-?\\d+)(.*) Z: (-?\\d+)");

    /** Xaero's world map region size, in blocks. */
    public static final int REGION_SIZE = 512;
    /** Map tile chunks (4×4 chunks) per region side. */
    public static final int TILE_CHUNKS_PER_REGION = 8;

    private XaeroWraps() {
    }

    public static Wrap of(ResourceKey<Level> dimension) {
        return dimension == null ? Wrap.NONE : Wrap.of(dimension);
    }

    /** Period in map regions, or 0 when the dimension does not wrap or does not tile whole regions. */
    public static int regionPeriod(Wrap wrap) {
        return wrap.enabled() && wrap.period % REGION_SIZE == 0 ? wrap.period / REGION_SIZE : 0;
    }

    /**
     * First canonical region: the one holding the window's first block. Only the leaf level lines up with the window
     * exactly, and only where it starts on a region boundary (the Overworld's does; a centered 3-region Nether's
     * does not, so half a region on each side of it is not where the world save keeps it).
     */
    public static int regionOrigin(Wrap wrap) {
        return Math.floorDiv(wrap.minBlock, REGION_SIZE);
    }

    /**
     * {@code region} canonicalized for a map level whose regions span {@code 2^level} leaf regions: folded into
     * {@code [regionOrigin, regionOrigin + regionPeriod)} leaves, where that is a whole number of the level's regions.
     */
    public static int canonRegion(int region, int regionPeriod, int regionOrigin, int level) {
        if (regionPeriod == 0) return region;
        int span = 1 << level;
        if (regionPeriod % span != 0 || regionOrigin % span != 0) return region;
        return WrapMath.canon(region, regionPeriod / span, regionOrigin / span);
    }

    /** Map tile chunk (4×4 chunks) canonicalized: folded into the canonical regions. */
    public static int canonTileChunk(int tileChunk, Wrap wrap) {
        int regionPeriod = regionPeriod(wrap);
        if (regionPeriod == 0) return tileChunk;
        return WrapMath.canon(tileChunk, regionPeriod * TILE_CHUNKS_PER_REGION, regionOrigin(wrap) * TILE_CHUNKS_PER_REGION);
    }

    /** Image of map tile chunk {@code tileChunk} nearest {@code ref}. */
    public static int nearestTileChunk(Wrap wrap, int tileChunk, int ref) {
        int regionPeriod = regionPeriod(wrap);
        return regionPeriod == 0 ? tileChunk : WrapMath.nearestImage(tileChunk, ref, regionPeriod * TILE_CHUNKS_PER_REGION);
    }

    /**
     * Image of an element coordinate nearest a view coordinate. Element coordinates are {@code divider} times map
     * coordinates, which wrap with {@code wrap}.
     */
    public static double nearest(Wrap wrap, double element, double divider, double view) {
        if (!wrap.enabled() || divider == 0.0) return element;
        return wrap.nearest(element / divider, view) * divider;
    }

    /**
     * Wrapping for coordinates at a dimension coordinate scale, as far as it can be known: the client's own dimension
     * when the scale is its scale, otherwise none.
     */
    public static Wrap forScale(double coordinateScale) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimensionType().coordinateScale() != coordinateScale) return Wrap.NONE;
        return Wrap.of(level);
    }

    /** Rewrites a {@code "X: <x> ... Z: <z>"} line with canonical coordinates. Other text is returned as is. */
    public static String canonicalCoordinates(String text, Wrap wrap) {
        Matcher matcher = COORDINATES.matcher(text);
        if (!matcher.matches()) return text;
        try {
            int x = wrap.canonBlock(Integer.parseInt(matcher.group(1)));
            int z = wrap.canonBlock(Integer.parseInt(matcher.group(3)));
            return "X: " + x + matcher.group(2) + " Z: " + z;
        } catch (NumberFormatException e) {
            return text;
        }
    }
}
