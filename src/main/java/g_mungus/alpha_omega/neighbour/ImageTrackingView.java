package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/**
 * The chunks a player in an orbifold world sees: vanilla's square around it, plus, for each image it has, the same
 * square around its image position, kept to live storage ({@link ImageViews#shows}). Squares that left the view all at
 * once linger for a while ({@code transfer-retention.md} §3.3), kept the same way.
 */
public record ImageTrackingView(ChunkTrackingView.Positioned home, List<Virtual> virtuals, List<Virtual> lingering, OrbifoldGeometry geometry)
    implements ChunkTrackingView {

    /** A player's image position for an image {@code g}: the chunk {@code g⁻¹(pos)} is in (the identity for a lingering square of its own). */
    public record Virtual(Motion image, ChunkPos center) {
    }

    public ImageTrackingView(ChunkTrackingView.Positioned home, List<Virtual> virtuals, OrbifoldGeometry geometry) {
        this(home, virtuals, List.of(), geometry);
    }

    public int viewDistance() {
        return this.home.viewDistance();
    }

    private boolean inSquare(Virtual square, int x, int z, boolean includeOuter) {
        return ChunkTrackingView.isWithinDistance(square.center.x, square.center.z, this.viewDistance(), x, z, includeOuter)
            && ImageViews.shows(this.geometry, square, x, z);
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
