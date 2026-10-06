package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;

/**
 * Game events (RS §5): an event near an edge also reaches the listeners round each of its images, at the image's
 * position, so sculk sensors, wardens, allays and catalysts hear it from across a seam. Each listener hears an event
 * once, from whichever of its places is nearest: near a cone point the same event can be in range twice.
 *
 * <p>This replaces {@code GameEventDispatcher.post} near the edges with the same steps over the event and its images.
 * Away from the edges it is four comparisons.
 */
public final class GameEventBridge {

    private GameEventBridge() {
    }

    /** One listener's nearest hearing: where the event is for it, and where it is. */
    private record Hearing(Vec3 source, Vec3 listener, double distance) {
    }

    /** Posts an event with its images; false if it is away from the edges (vanilla posts it). */
    public static boolean post(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return false;
        int radius = event.value().notificationRadius();
        List<Motion> images = Images.around(geometry, pos.x, pos.z, radius);
        if (images.isEmpty()) return false;
        BridgeCounters.run(BridgeCounters.Kind.GAME_EVENTS, pos.x, pos.z, radius);
        Map<GameEventListener, Hearing> heard = new LinkedHashMap<>();
        boolean any = visit(level, event, pos, context, radius, heard);
        for (Motion g : images) any |= visit(level, event, new Vec3(g.pointX(pos.x), pos.y, g.pointZ(pos.z)), context, radius, heard);
        List<GameEvent.ListenerInfo> byDistance = new ArrayList<>();
        long viaImage = 0;
        for (Map.Entry<GameEventListener, Hearing> entry : heard.entrySet()) {
            GameEventListener listener = entry.getKey();
            Hearing hearing = entry.getValue();
            if (hearing.source != pos) viaImage++;
            if (listener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                byDistance.add(new GameEvent.ListenerInfo(event, hearing.source, context, listener, hearing.listener));
            } else {
                listener.handleGameEvent(level, event, context, hearing.source);
            }
        }
        Collections.sort(byDistance);
        for (GameEvent.ListenerInfo info : byDistance) info.recipient().handleGameEvent(level, info.gameEvent(), info.context(), info.source());
        BridgeCounters.found(BridgeCounters.Kind.GAME_EVENTS, viaImage);
        if (any) DebugPackets.sendGameEventInfo(level, event, pos);
        return true;
    }

    /** Vanilla's section walk round one place of the event, keeping each listener's nearest hearing. */
    private static boolean visit(ServerLevel level, Holder<GameEvent> event, Vec3 source, GameEvent.Context context, int radius,
        Map<GameEventListener, Hearing> heard) {
        BlockPos at = BlockPos.containing(source);
        int minX = SectionPos.blockToSectionCoord(at.getX() - radius), maxX = SectionPos.blockToSectionCoord(at.getX() + radius);
        int minY = SectionPos.blockToSectionCoord(at.getY() - radius), maxY = SectionPos.blockToSectionCoord(at.getY() + radius);
        int minZ = SectionPos.blockToSectionCoord(at.getZ() - radius), maxZ = SectionPos.blockToSectionCoord(at.getZ() + radius);
        boolean any = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkAccess chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (int y = minY; y <= maxY; y++) {
                    any |= chunk.getListenerRegistry(y).visitInRangeListeners(event, source, context, (listener, listenerPos) -> {
                        double d = listenerPos.distanceToSqr(source);
                        Hearing best = heard.get(listener);
                        if (best == null || d < best.distance) heard.put(listener, new Hearing(source, listenerPos, d));
                    });
                }
            }
        }
        return any;
    }
}
