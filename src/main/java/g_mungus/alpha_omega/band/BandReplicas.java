package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.AlphaOmegaMod;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Client block entity replicas (RS §3.4): the client of a non-owner copy gets the owner's block entity, as a read-only
 * replica at the copy's position, the way a client gets every block entity. A chunk packet for a chunk with copies
 * carries, for each cell owned elsewhere, the owner's update tag at the copy's position; block entity data packets are
 * re-addressed per copy in the chunk holder hook.
 */
public final class BandReplicas {

    @Nullable
    private static final Constructor<?> INFO = infoConstructor();

    private BandReplicas() {
    }

    @Nullable
    private static Constructor<?> infoConstructor() {
        try {
            Class<?> type = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo");
            Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class, BlockEntityType.class, CompoundTag.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AlphaOmegaMod.LOGGER.error("Band block entity replicas are off: no chunk packet block entity constructor", e);
            return null;
        }
    }

    /** Adds the owners' block entities for {@code chunk}'s non-owned cells to a chunk packet's block entity list. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addReplicas(LevelChunk chunk, List list) {
        if (INFO == null || chunk.getLevel().isClientSide) return;
        CopyLinks links = Band.links(chunk);
        for (CopyLinks.Link link : links.links) {
            LevelChunk copy = link.chunk(chunk.getLevel());
            if (copy == null) continue;
            for (Map.Entry<BlockPos, BlockEntity> entry : copy.getBlockEntities().entrySet()) {
                BlockEntity entity = entry.getValue();
                if (entity.isRemoved()) continue;
                BlockPos here = link.back(entry.getKey());
                if (chunk.getBlockEntities().containsKey(here) || Ownership.isOwner(chunk, here)) continue;
                // Only cells whose owner is this copy (not a third copy the link merely passes through).
                if (!Ownership.isOwner(copy, entry.getKey())) continue;
                try {
                    CompoundTag tag = entity.getUpdateTag(chunk.getLevel().registryAccess());
                    list.add(INFO.newInstance((here.getX() & 15) << 4 | (here.getZ() & 15), here.getY(), entity.getType(), tag.isEmpty() ? null : tag));
                    BandCounters.replicasSent++;
                } catch (ReflectiveOperationException e) {
                    AlphaOmegaMod.LOGGER.warn("Could not add a block entity replica at {}", here, e);
                }
            }
        }
    }
}
