package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Which copy of a cell owns it (RS §3.1): the copy where its reactions run and its block entity lives. The tile copy
 * owns by default; a claim moves a cell to a band copy, recorded as a flip bit in both chunks ({@link BandData}).
 *
 * <p>Claims (RS §3.3) are rules on top of this: phase 4 has one, {@link #claim} when a block entity is set at a copy
 * (it owns the cell while the block entity exists) and {@link #release} when it is removed. Phase 8 adds placement
 * claims by calling the same two methods.
 */
public final class Ownership {

    private Ownership() {
    }

    private static int section(LevelChunk chunk, BlockPos pos) {
        return chunk.getSectionIndex(pos.getY());
    }

    /** Whether {@code chunk} (which holds {@code pos}) owns the cell. Allocation-free; chunks away from edges answer at once. */
    public static boolean isOwner(LevelChunk chunk, BlockPos pos) {
        CopyLinks links = Band.links(chunk);
        if (links.isEmpty()) return true;
        BandData data = ((BandChunk) chunk).alpha_omega$data(false);
        boolean flip = data != null && data.flip(section(chunk, pos), BandData.cell(pos.getX(), pos.getY(), pos.getZ()));
        return flip == links.band;
    }

    /**
     * Whether the copy at {@code pos} owns its cell, by position: true away from edges; by the masks if the chunk is
     * loaded; otherwise nominally (the tile copy owns).
     */
    public static boolean isOwner(Level level, BlockPos pos) {
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || !Band.isLinked(geometry, pos.getX(), pos.getZ())) return true;
        LevelChunk chunk = Band.loadedChunk(level, pos);
        if (chunk == null) return geometry.isTile(pos.getX(), pos.getZ());
        return isOwner(chunk, pos);
    }

    /**
     * The link from {@code chunk} to the copy that owns {@code pos}'s cell, or null if {@code chunk} owns it. The owning
     * copy's chunk may be unloaded ({@link CopyLinks.Link#chunk} null): callers drop what they would have forwarded.
     */
    @Nullable
    public static CopyLinks.Link owner(Level level, LevelChunk chunk, BlockPos pos) {
        if (isOwner(chunk, pos)) return null;
        CopyLinks links = Band.links(chunk);
        CopyLinks.Link tile = null, unloaded = null;
        for (CopyLinks.Link link : links.links) {
            if (link.toTile) {
                tile = link;
                continue;
            }
            LevelChunk copy = link.chunk(level);
            if (copy == null) {
                if (unloaded == null) unloaded = link;
                continue;
            }
            BandData data = ((BandChunk) copy).alpha_omega$data(false);
            if (data != null && data.flip(copy.getSectionIndex(pos.getY()), BandData.cell(link.x(pos.getX()), pos.getY(), link.z(pos.getZ())))) return link;
        }
        if (tile != null) {
            // A band copy that does not own: the source owns, unless it is flipped to a copy we could not find loaded.
            LevelChunk source = tile.chunk(level);
            if (source == null || isOwner(source, tile.scratch(pos))) return tile;
            return unloaded != null ? unloaded : tile;
        }
        // A tile copy flipped away: the claimant is unloaded (or the masks disagree, which the copy check reports).
        if (unloaded != null) return unloaded;
        BandCounters.ownershipUnresolved++;
        return null;
    }

    /** The owner's block entity for {@code pos}'s cell when {@code chunk} is not the owner; null otherwise. */
    @Nullable
    public static BlockEntity ownersBlockEntity(Level level, LevelChunk chunk, BlockPos pos, LevelChunk.EntityCreationType type) {
        CopyLinks.Link owner = owner(level, chunk, pos);
        if (owner == null) return null;
        LevelChunk copy = owner.chunk(level);
        BandCounters.blockEntityRedirects++;
        return copy == null ? null : copy.getBlockEntity(owner.map(pos), type);
    }

    // ---- Claims ----

    /**
     * {@code chunk}'s copy of {@code pos} takes the cell: it owns it from now on. Written in every loaded copy, and the
     * stamps bumped, as for a write. Any block entity left at another copy is removed (it should have gone already).
     */
    public static void claim(Level level, LevelChunk chunk, BlockPos pos) {
        CopyLinks links = Band.links(chunk);
        if (links.isEmpty()) return;
        int section = section(chunk, pos), cell = BandData.cell(pos.getX(), pos.getY(), pos.getZ());
        BandData data = ((BandChunk) chunk).alpha_omega$data(true);
        boolean changed = data.setFlip(section, cell, links.band);
        long self = chunk.getPos().toLong();
        for (CopyLinks.Link link : links.links) {
            data.bump(link.key);
            LevelChunk copy = link.chunk(level);
            if (copy == null) continue;
            BandData other = ((BandChunk) copy).alpha_omega$data(true);
            other.bump(self);
            BlockPos at = link.scratch(pos);
            // The source flips away from itself; any other band copy gives up its claim.
            changed |= other.setFlip(section, BandData.cell(at.getX(), at.getY(), at.getZ()), link.toTile);
            BlockEntity stale = copy.getBlockEntities().get(at);
            if (stale != null) {
                BandCounters.staleBlockEntitiesRemoved++;
                copy.removeBlockEntity(at.immutable());
            }
            copy.setUnsaved(true);
        }
        chunk.setUnsaved(true);
        if (changed) BandCounters.claims++;
    }

    /** {@code chunk}'s copy of {@code pos} gives the cell back to its nominal owner, the tile copy. */
    public static void release(Level level, LevelChunk chunk, BlockPos pos) {
        CopyLinks links = Band.links(chunk);
        if (!links.band) return;
        BandData data = ((BandChunk) chunk).alpha_omega$data(false);
        int section = section(chunk, pos), cell = BandData.cell(pos.getX(), pos.getY(), pos.getZ());
        if (data == null || !data.setFlip(section, cell, false)) return;
        long self = chunk.getPos().toLong();
        for (CopyLinks.Link link : links.links) {
            data.bump(link.key);
            LevelChunk copy = link.chunk(level);
            if (copy == null) continue;
            BandData other = ((BandChunk) copy).alpha_omega$data(true);
            other.bump(self);
            if (link.toTile) {
                BlockPos at = link.scratch(pos);
                other.setFlip(section, BandData.cell(at.getX(), at.getY(), at.getZ()), false);
            }
            copy.setUnsaved(true);
        }
        chunk.setUnsaved(true);
        BandCounters.releases++;
    }
}
