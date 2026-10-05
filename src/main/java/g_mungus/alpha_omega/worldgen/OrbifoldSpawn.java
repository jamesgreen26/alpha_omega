package g_mungus.alpha_omega.worldgen;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * An orbifold world's spawn is found near the geometry's spawn point (the wrapping plan's §4: on the equator, facing
 * north), not where vanilla's climate search would put it, which is usually far outside the tile. Like vanilla, it
 * takes the first chunk, spiralling out, with a standable column; but only as far as {@link #RADIUS} blocks from the
 * spawn point. If there is none, the spawn is the spawn point itself, on the surface.
 */
@EventBusSubscriber(modid = AlphaOmegaMod.MOD_ID)
public final class OrbifoldSpawn {

    /** The world spawn stays within this many blocks of the geometry's spawn point (horizontally). */
    public static final int RADIUS = 64;

    private OrbifoldSpawn() {
    }

    @SubscribeEvent
    static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(level.getChunkSource().getGenerator() instanceof OrbifoldChunkGenerator generator)) return;
        BlockPos spawn = find(level, generator.geometry());
        event.getSettings().setSpawn(spawn, 0.0F);
        event.setCanceled(true);
        AlphaOmegaMod.LOGGER.info("Orbifold world spawn at {}", spawn);
    }

    /** The spawn: vanilla's spiral over chunks, from the spawn point's chunk, keeping only columns within {@link #RADIUS}. */
    public static BlockPos find(ServerLevel level, OrbifoldGeometry geometry) {
        int x0 = geometry.spawnX, z0 = geometry.spawnZ;
        ChunkPos centre = new ChunkPos(x0 >> 4, z0 >> 4);
        int reach = (RADIUS >> 4) + 1;
        for (int ring = 0; ring <= reach; ring++) {
            BlockPos best = null;
            long bestDistance = Long.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos found = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(centre.x + dx, centre.z + dz));
                    if (found == null) continue;
                    long distance = (long) (found.getX() - x0) * (found.getX() - x0) + (long) (found.getZ() - z0) * (found.getZ() - z0);
                    if (distance <= (long) RADIUS * RADIUS && distance < bestDistance) {
                        best = found;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) return best;
        }
        int y = level.getChunk(centre.x, centre.z).getHeight(Heightmap.Types.WORLD_SURFACE, x0 & 15, z0 & 15) + 1;
        return new BlockPos(x0, Math.max(y, level.getMinBuildHeight()), z0);
    }
}
