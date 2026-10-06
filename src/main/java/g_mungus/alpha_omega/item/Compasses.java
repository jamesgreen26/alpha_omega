package g_mungus.alpha_omega.item;

import g_mungus.alpha_omega.api.Orbifold;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Compasses on an orbifold: every compass keeps vanilla's target (spawn, a lodestone, the last death), but aims at the
 * image of it nearest the holder. A target across a seam, or stored in the band, is then the short way off, and a
 * holder in the band (in another frame) is pointed the way it sees the world. Used by the client's compass needle
 * ({@code CompassItemPropertyFunctionMixin}); common so the server can check it.
 */
public final class Compasses {

    private Compasses() {
    }

    /** The image of {@code target} nearest {@code holder}, or {@code target} itself outside an orbifold. */
    public static BlockPos aim(Level level, BlockPos target, Vec3 holder) {
        Orbifold orbifold = Orbifold.of(level);
        return orbifold == null ? target : orbifold.nearest(target, holder);
    }

    /** A compass target, moved to its image nearest the holder when it is in the holder's level. */
    @Nullable
    public static GlobalPos aim(Entity holder, @Nullable GlobalPos target) {
        if (target == null || target.dimension() != holder.level().dimension()) return target;
        BlockPos aimed = aim(holder.level(), target.pos(), holder.position());
        return aimed.equals(target.pos()) ? target : GlobalPos.of(target.dimension(), aimed);
    }

    /** Vanilla's needle angle, in turns from east toward south, from a holder to the nearest image of a target. */
    public static double angle(Entity holder, BlockPos target) {
        Vec3 to = Vec3.atCenterOf(aim(holder.level(), target, holder.position()));
        return Math.atan2(to.z - holder.getZ(), to.x - holder.getX()) / (Math.PI * 2);
    }
}
