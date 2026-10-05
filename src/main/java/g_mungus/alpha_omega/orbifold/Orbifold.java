package g_mungus.alpha_omega.orbifold;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a level's orbifold geometry. Until the orbifold generator exists (phase 1) no level has one, so everything that
 * asks here is inactive and the game runs as vanilla.
 */
public final class Orbifold {

    private Orbifold() {
    }

    @Nullable
    public static OrbifoldGeometry of(Level level) {
        return null;
    }
}
