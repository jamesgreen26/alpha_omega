package g_mungus.alpha_omega.api;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * An entity changed frame: the server moved it by an element of {@code Γ} within the same level (see {@link Orbifold}).
 * Its position went from {@code g⁻¹(p)} to {@code p}, its velocity, yaw and held positions were turned and moved with
 * it, and it is the same place in the world as before. Posted on the game bus ({@link NeoForge#EVENT_BUS}), on the
 * server thread, after the move, once for each entity moved: a group (a vehicle with its passengers, leashed mobs)
 * posts one event per member, each naming the group's root. Not cancellable; the client is not told through this.
 *
 * <p>Mods holding absolute positions in their own data (targets, linked blocks) can map them with
 * {@link #motion()}: {@code Orbifold.transform(event.motion(), oldPos)}.
 */
public class FrameTransferEvent extends Event {

    /** Why an entity was moved. */
    public enum Reason {
        /** It went far enough past a seam (into the band) and moved to its source in the tile. */
        SEAM,
        /** A player's client crossed a seam and the server accepted its claim. */
        CLAIM,
        /** It moved into another entity's frame without crossing a seam itself: a player group, a follower, a pull. */
        GROUP,
        /** What carried it (a moving structure) crossed, and it went along. */
        CARRIED
    }

    private final ServerLevel level;
    private final Entity entity;
    private final Entity root;
    private final Motion motion;
    private final Reason reason;

    public FrameTransferEvent(ServerLevel level, Entity entity, Entity root, Motion motion, Reason reason) {
        this.level = level;
        this.entity = entity;
        this.root = root;
        this.motion = motion;
        this.reason = reason;
    }

    public ServerLevel level() {
        return this.level;
    }

    /** The entity moved, now at its new position. */
    public Entity entity() {
        return this.entity;
    }

    /** The root of the group it moved with (itself if it moved alone). */
    public Entity root() {
        return this.root;
    }

    /** The element it moved by: new position = {@code motion} applied to the old one. */
    public Motion motion() {
        return this.motion;
    }

    public Reason reason() {
        return this.reason;
    }

    /** Where the entity was before the move. */
    public Vec3 from() {
        return Orbifold.transform(this.motion.inverse(), this.entity.position());
    }

    /**
     * Posts one event per moved entity. Called by the mod's transfer code once a group has moved; the reason is read
     * from how it was moved and where the root came from.
     */
    public static void post(ServerLevel level, Entity root, List<Entity> group, Motion motion, @Nullable ServerPlayer claimant, boolean carried) {
        OrbifoldGeometry geometry = g_mungus.alpha_omega.orbifold.Orbifold.of(level);
        Reason reason;
        if (carried) {
            reason = Reason.CARRIED;
        } else if (claimant != null) {
            reason = Reason.CLAIM;
        } else {
            Vec3 from = Orbifold.transform(motion.inverse(), root.position());
            boolean crossed = geometry != null && geometry.seamDepth(from.x, from.z) > 0 && geometry.frame(from.x, from.z).equals(motion);
            reason = crossed ? Reason.SEAM : Reason.GROUP;
        }
        for (Entity entity : group) NeoForge.EVENT_BUS.post(new FrameTransferEvent(level, entity, root, motion, reason));
    }
}
