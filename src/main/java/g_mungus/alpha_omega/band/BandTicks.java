package g_mungus.alpha_omega.band;

import net.minecraft.server.level.ServerLevel;

/** The level a {@code LevelTicks} belongs to, so scheduling can look at a cell's copies (set by a mixin). */
public interface BandTicks {

    void alpha_omega$setLevel(ServerLevel level);
}
