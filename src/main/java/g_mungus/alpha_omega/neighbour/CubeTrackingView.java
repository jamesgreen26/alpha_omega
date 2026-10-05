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
 * Squares a crossing took out of view linger for a while ({@code transfer-retention.md} §3.3), kept the same way.
 */
public record CubeTrackingView(ChunkTrackingView.Positioned home, List<Virtual> virtuals, List<Virtual> lingering, CubeGeometry geometry)
    implements ChunkTrackingView {

    /** A player's virtual position on a neighbouring face: the chunk it would stand in there. */
    public record Virtual(CubeFace face, ChunkPos center) {
    }

    public CubeTrackingView(ChunkTrackingView.Positioned home, List<Virtual> virtuals, CubeGeometry geometry) {
        this(home, virtuals, List.of(), geometry);
    }

    public int viewDistance() {
        return this.home.viewDistance();
    }

    private boolean inSquare(Virtual square, int x, int z, boolean includeOuter) {
        return ChunkTrackingView.isWithinDistance(square.center.x, square.center.z, this.viewDistance(), x, z, includeOuter)
            && this.geometry.faceAtChunk(x, z) == square.face && this.geometry.inFootprint(x, z);
    }

    @Override
    public boolean contains(int x, int z, boolean includeOuter) {
        if (this.home.contains(x, z, includeOuter)) return true;
        for (Virtual virtual : this.virtuals) {
            if (this.inSquare(virtual, x, z, includeOuter)) return true;
        }
        for (Virtual square : this.lingering) {
            if (this.inSquare(square, x, z, includeOuter)) return true;
        }
        return false;
    }

    @Override
    public void forEach(Consumer<ChunkPos> action) {
        this.home.forEach(action);
        this.forEachIn(this.virtuals, action);
        this.forEachIn(this.lingering, action);
    }

    private void forEachIn(List<Virtual> squares, Consumer<ChunkPos> action) {
        int d = this.viewDistance() + 1;
        for (Virtual square : squares) {
            for (int x = square.center.x - d; x <= square.center.x + d; x++) {
                for (int z = square.center.z - d; z <= square.center.z + d; z++) {
                    if (this.inSquare(square, x, z, true)) action.accept(new ChunkPos(x, z));
                }
            }
        }
    }
}
