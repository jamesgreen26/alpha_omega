package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.mixin.server.ServerGamePacketListenerImplAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Moving entities between frames (design doc §6.2–6.5). */
public final class EntityFrames {

    private EntityFrames() {
    }

    /**
     * Moves an entity (with its vehicle and passengers) to the image whose chunk matches its island's lift (I4).
     * Does nothing if the chunk is not in an island or the entity is already in frame.
     */
    public static void reframe(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        int chunkX = entity.chunkPosition().x;
        int chunkZ = entity.chunkPosition().z;
        long laps = IslandManager.of(level).laps(chunkX, chunkZ);
        if (laps == IslandGraph.ABSENT) return;
        int dx = (Wrap.canonChunk(chunkX) + IslandGraph.lapX(laps) * Wrap.CHUNK_PERIOD - chunkX) << 4;
        int dz = (Wrap.canonChunk(chunkZ) + IslandGraph.lapZ(laps) * Wrap.CHUNK_PERIOD - chunkZ) << 4;
        if (dx != 0 || dz != 0) translate(entity, dx, dz);
    }

    /** Translates an entity, its vehicle and passengers, and every absolute position they hold, by whole laps. */
    public static void translate(Entity entity, double dx, double dz) {
        entity.getRootVehicle().getSelfAndPassengers().forEach(e -> translateOne(e, dx, dz));
    }

    private static void translateOne(Entity entity, double dx, double dz) {
        entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
        entity.xo += dx;
        entity.zo += dz;
        entity.xOld += dx;
        entity.zOld += dz;
        FrameTranslators.translate(entity, dx, dz);
    }

    /** The generic handling every entity gets (§6.4); type-specific state is handled by {@link FrameTranslators}. */
    static void translateGeneric(Entity entity, double dx, double dz) {
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        }
        if (entity instanceof ServerPlayer player && player.connection != null) {
            ServerGamePacketListenerImplAccessor c = (ServerGamePacketListenerImplAccessor) player.connection;
            c.alpha_omega$setFirstGoodX(c.alpha_omega$getFirstGoodX() + dx);
            c.alpha_omega$setFirstGoodZ(c.alpha_omega$getFirstGoodZ() + dz);
            c.alpha_omega$setLastGoodX(c.alpha_omega$getLastGoodX() + dx);
            c.alpha_omega$setLastGoodZ(c.alpha_omega$getLastGoodZ() + dz);
            c.alpha_omega$setVehicleFirstGoodX(c.alpha_omega$getVehicleFirstGoodX() + dx);
            c.alpha_omega$setVehicleFirstGoodZ(c.alpha_omega$getVehicleFirstGoodZ() + dz);
            c.alpha_omega$setVehicleLastGoodX(c.alpha_omega$getVehicleLastGoodX() + dx);
            c.alpha_omega$setVehicleLastGoodZ(c.alpha_omega$getVehicleLastGoodZ() + dz);
            Vec3 awaiting = c.alpha_omega$getAwaitingPositionFromClient();
            if (awaiting != null) c.alpha_omega$setAwaitingPositionFromClient(awaiting.add(dx, 0, dz));
        }
    }
}
