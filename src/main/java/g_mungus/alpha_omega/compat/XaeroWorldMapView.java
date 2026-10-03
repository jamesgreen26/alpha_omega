package g_mungus.alpha_omega.compat;

import g_mungus.alpha_omega.wrap.Wrap;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/** The world map's viewed dimension. Only loaded when Xaero's World Map is installed. */
public final class XaeroWorldMapView {

    private XaeroWorldMapView() {
    }

    public static Wrap wrap(MapWorld mapWorld) {
        if (mapWorld == null) return Wrap.NONE;
        MapDimension dimension = mapWorld.getCurrentDimension();
        return dimension == null ? Wrap.NONE : XaeroWraps.of(dimension.getDimId());
    }

    public static Wrap wrap() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) return Wrap.NONE;
        MapProcessor processor = session.getMapProcessor();
        return processor == null ? Wrap.NONE : wrap(processor.getMapWorld());
    }
}
