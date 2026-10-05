package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.CubeFace;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Other players and mobs seen crossing an edge stand upright on the new face at once; their models turn from how they
 * really stood over {@link #TICKS} ticks instead, as the player's own view does ({@link FaceCamera}).
 */
public final class EntityTurns {

    private static final int TICKS = 10;

    /** A turn under way: the full rotation (in the new face's storage axes) and the entity tick it started at. */
    private record Turn(Quaternionf full, int start) {
    }

    private static final Map<Entity, Turn> TURNS = new WeakHashMap<>();

    private EntityTurns() {
    }

    /** An entity was just moved from one face's storage to another's. */
    public static void start(Entity entity, CubeFace from, CubeFace to) {
        if (entity instanceof LivingEntity) TURNS.put(entity, new Turn(FaceCamera.crossingTurn(from, to), entity.tickCount));
    }

    /** The extra rotation to draw an entity's model with now, or null when it isn't turning. */
    @Nullable
    public static Quaternionf rotation(Entity entity, float partialTick) {
        Turn turn = TURNS.get(entity);
        if (turn == null) return null;
        float t = (entity.tickCount - turn.start + partialTick) / TICKS;
        if (t >= 1.0F) {
            TURNS.remove(entity);
            return null;
        }
        float left = Mth.clamp(1.0F - t, 0.0F, 1.0F);
        return new Quaternionf().slerp(turn.full, left * left * (3.0F - 2.0F * left));
    }

    public static void clear() {
        TURNS.clear();
    }
}
