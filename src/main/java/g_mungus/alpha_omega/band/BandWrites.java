package g_mungus.alpha_omega.band;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * State is shared (RS §3.2): a write to one copy of a cell is applied to every loaded copy, turned. The mirrored write
 * updates sections, heightmaps and light, but runs no {@code onPlace}/{@code onRemove}, creates no block entity and
 * sends no neighbour updates (those reach the owner by forwarding, {@link BandReactions}). Packets go out for every
 * copy. Server thread only.
 */
public final class BandWrites {

    /** True while a mirrored write runs: its chunk write must not mirror again or run block callbacks. */
    private static boolean mirroring;
    /** True while block update packets are being sent for copies, so they are not sent for the copies' copies. */
    private static boolean sendingCopies;

    private BandWrites() {
    }

    public static boolean mirroring() {
        return mirroring;
    }

    /** Runs {@code action} as a mirrored write (no callbacks, no block entity creation, no further mirroring). */
    public static void asMirror(Runnable action) {
        boolean was = mirroring;
        mirroring = true;
        try {
            action.run();
        } finally {
            mirroring = was;
        }
    }

    /**
     * Called from {@code LevelChunk.setBlockState} right after the section changed at {@code pos}, before the block's
     * own callbacks run, so writes those callbacks make land in the copies after this one.
     */
    public static void mirror(Level level, LevelChunk chunk, BlockPos pos, BlockState state, boolean moving) {
        if (mirroring || !Band.filled(chunk)) return;
        CopyLinks links = Band.links(chunk);
        BandData data = ((BandChunk) chunk).alpha_omega$data(true);
        long self = chunk.getPos().toLong();
        for (CopyLinks.Link link : links.links) {
            data.bump(link.key);
            LevelChunk copy = link.chunk(level);
            if (copy == null || !Band.filled(copy)) {
                BandCounters.mirrorsMissed++;
                continue;
            }
            ((BandChunk) copy).alpha_omega$data(true).bump(self);
            mirroring = true;
            try {
                copy.setBlockState(link.scratch(pos), Band.turn(state, link.turned), moving);
            } finally {
                mirroring = false;
            }
            BandCounters.mirroredWrites++;
        }
    }

    /**
     * After a (non-mirrored) chunk write: a block entity left at a copy whose block no longer allows it is removed. The
     * write at {@code pos} ran the block's {@code onRemove}, which removes the owner's block entity through the
     * redirect; this catches blocks whose {@code onRemove} does not.
     */
    public static void afterWrite(Level level, LevelChunk chunk, BlockPos pos) {
        if (mirroring) return;
        for (CopyLinks.Link link : Band.links(chunk).links) {
            LevelChunk copy = link.chunk(level);
            if (copy == null) continue;
            BlockPos at = link.scratch(pos);
            BlockEntity entity = copy.getBlockEntities().get(at);
            if (entity != null && !entity.getType().isValid(copy.getBlockState(at))) {
                BandCounters.staleBlockEntitiesRemoved++;
                copy.removeBlockEntity(at.immutable());
            }
        }
    }

    /**
     * After {@code ServerLevel.sendBlockUpdated} at {@code pos}: the copies' chunks send their block update too (and,
     * for a block with a block entity, the owner's block entity data at the copy's position, see the chunk holder hook).
     */
    public static void sendCopies(ServerLevel level, BlockPos pos) {
        if (sendingCopies) return;
        LevelChunk chunk = Band.linkedChunk(level, pos);
        if (chunk == null) return;
        sendingCopies = true;
        try {
            for (CopyLinks.Link link : Band.links(chunk).links) {
                if (link.chunk(level) == null) continue;
                level.getChunkSource().blockChanged(link.scratch(pos));
                BandCounters.copyPackets++;
            }
        } finally {
            sendingCopies = false;
        }
    }

    /** After a block event ran and was sent round {@code pos}: the same event's packet round every loaded copy. */
    public static void blockEventCopies(ServerLevel level, BlockPos pos, Block block, int a, int b) {
        LevelChunk chunk = Band.linkedChunk(level, pos);
        if (chunk == null) return;
        for (CopyLinks.Link link : Band.links(chunk).links) {
            if (link.chunk(level) == null) continue;
            BlockPos at = link.map(pos);
            int param = b;
            // Pistons and bells pass a direction.
            if (link.turned && (block instanceof PistonBaseBlock || block instanceof BellBlock) && b >= 0 && b < 6) {
                param = Band.turn(Direction.from3DDataValue(b), true).get3DDataValue();
            }
            level.getServer().getPlayerList().broadcast(null, at.getX(), at.getY(), at.getZ(), 64.0, level.dimension(),
                new ClientboundBlockEventPacket(at, block, a, param));
            BandCounters.copyPackets++;
        }
    }
}
