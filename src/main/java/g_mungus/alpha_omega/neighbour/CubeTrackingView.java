package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/**
 * The chunks a player in a cube world sees (design §6.1): vanilla's square around it on its own face, plus, on each
 * neighbouring face near enough, the same square around its virtual position there, kept to that face's footprint.
 */
public record CubeTrackingView(ChunkTrackingView.Positioned home, List<Virtual> virtuals, CubeGeometry geometry) implements ChunkTrackingView {

    /** A player's virtual position on a neighbouring face: the chunk it would stand in there. */
    public record Virtual(CubeFace face, ChunkPos center) {
    }

    public int viewDistance() {
        return this.home.viewDistance();
    }

    private boolean inVirtual(Virtual virtual, int x, int z, boolean includeOuter) {
        return ChunkTrackingView.isWithinDistance(virtual.center.x, virtual.center.z, this.viewDistance(), x, z, includeOuter)
            && this.geometry.faceAtChunk(x, z) == virtual.face && this.geometry.inFootprint(x, z);
    }

    @Override
    public boolean contains(int x, int z, boolean includeOuter) {
        if (this.home.contains(x, z, includeOuter)) return true;
        for (Virtual virtual : this.virtuals) {
            if (this.inVirtual(virtual, x, z, includeOuter)) return true;
        }
        return false;
    }

    @Override
    public void forEach(Consumer<ChunkPos> action) {
        this.home.forEach(action);
        int d = this.viewDistance() + 1;
        for (Virtual virtual : this.virtuals) {
            for (int x = virtual.center.x - d; x <= virtual.center.x + d; x++) {
                for (int z = virtual.center.z - d; z <= virtual.center.z + d; z++) {
                    if (this.inVirtual(virtual, x, z, true)) action.accept(new ChunkPos(x, z));
                }
            }
        }
    }
}
