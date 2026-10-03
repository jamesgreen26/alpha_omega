package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.mixin.server.entity.PersistentEntitySectionManagerAccessor;
import g_mungus.alpha_omega.mixin.server.ServerLevelAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySectionStorage;

/**
 * The islands of one {@link ServerLevel} (design doc §5): which loaded chunks form connected regions, and the lap
 * each chunk is lifted by. Fed by chunk load and unload; keeps entities in their island's frame (I4).
 */
public final class IslandManager {

    /** Duck interface on {@code ServerLevel}. */
    public interface Holder {
        IslandManager alpha_omega$islands();
    }

    private final ServerLevel level;
    private final IslandGraph graph = new IslandGraph(Wrap.CHUNK_PERIOD);
    /** Entities whose frame must be checked at the end of the tick (they moved into another section or lap). */
    private final ReferenceLinkedOpenHashSet<Entity> frameChecks = new ReferenceLinkedOpenHashSet<>();
    /** Chunks that joined this tick, whose entities must be brought into the chunk's frame. */
    private final LongLinkedOpenHashSet joinedChunks = new LongLinkedOpenHashSet();

    public IslandManager(ServerLevel level) {
        this.level = level;
    }

    public static IslandManager of(ServerLevel level) {
        return ((Holder) level).alpha_omega$islands();
    }

    public IslandGraph graph() {
        return this.graph;
    }

    /** The lap pair of a chunk (any image), or {@link IslandGraph#ABSENT} if it is not loaded. */
    public long laps(int chunkX, int chunkZ) {
        return this.graph.laps(Wrap.canonChunk(chunkX), Wrap.canonChunk(chunkZ));
    }

    // ---- chunk lifecycle (server thread) ----

    public void onChunkLoaded(ChunkPos pos) {
        int x = Wrap.canonChunk(pos.x);
        int z = Wrap.canonChunk(pos.z);
        this.graph.join(x, z, this::seedLaps, this::playerWeight);
        this.joinedChunks.add(IslandGraph.key(x, z));
    }

    public void onChunkUnloaded(ChunkPos pos) {
        this.graph.leave(Wrap.canonChunk(pos.x), Wrap.canonChunk(pos.z));
    }

    // ---- entities ----

    public void queueFrameCheck(Entity entity) {
        this.frameChecks.add(entity);
    }

    /** End of the server tick: lazy splits, then entities that may have left their frame. */
    public void tick() {
        this.graph.processSplits();

        if (!this.joinedChunks.isEmpty()) {
            EntitySectionStorage<Entity> sections = ((PersistentEntitySectionManagerAccessor) ((ServerLevelAccessor) this.level).alpha_omega$getEntityManager()).alpha_omega$getSectionStorage();
            for (long key : this.joinedChunks) {
                sections.getExistingSectionsInChunk(ChunkPos.asLong(IslandGraph.keyX(key), IslandGraph.keyZ(key)))
                    .forEach(section -> section.getEntities().forEach(this.frameChecks::add));
            }
            this.joinedChunks.clear();
        }

        while (!this.frameChecks.isEmpty()) {
            Entity entity = this.frameChecks.removeFirst();
            if (!entity.isRemoved() && entity.level() == this.level) EntityFrames.reframe(entity);
        }
    }

    // ---- join policy ----

    /**
     * A chunk with no loaded neighbors starts in the frame of whatever caused it to load (§5.8). Tickets do not
     * record their cause, so: the nearest player within loading range, else lap (0, 0).
     */
    private long seedLaps(int x, int z) {
        int range = this.level.getServer().getPlayerList().getViewDistance() + 3;
        ServerPlayer nearest = null;
        int best = Integer.MAX_VALUE;
        for (ServerPlayer player : this.level.players()) {
            int dx = Math.abs(Wrap.minChunkDelta(x, SectionPos.blockToSectionCoord(player.getBlockX())));
            int dz = Math.abs(Wrap.minChunkDelta(z, SectionPos.blockToSectionCoord(player.getBlockZ())));
            int distance = Math.max(dx, dz);
            if (distance <= range && distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) return IslandGraph.packLaps(0, 0);
        int liftedX = Wrap.nearestChunk(x, SectionPos.blockToSectionCoord(nearest.getBlockX()));
        int liftedZ = Wrap.nearestChunk(z, SectionPos.blockToSectionCoord(nearest.getBlockZ()));
        return IslandGraph.packLaps(Math.floorDiv(liftedX, Wrap.CHUNK_PERIOD), Math.floorDiv(liftedZ, Wrap.CHUNK_PERIOD));
    }

    /** Islands with players win joins and merges, so players are never the ones re-framed. */
    private long playerWeight(int island) {
        long players = 0;
        for (ServerPlayer player : this.level.players()) {
            if (this.graph.islandOf(Wrap.canonChunk(player.chunkPosition().x), Wrap.canonChunk(player.chunkPosition().z)) == island) players++;
        }
        return players;
    }
}
