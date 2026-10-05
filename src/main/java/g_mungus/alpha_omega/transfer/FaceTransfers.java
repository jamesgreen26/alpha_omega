package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of crossing a seam. Every root entity is checked after it ticks; one that has crossed moves, with its
 * passengers, by the element of {@code Γ} that takes it into its new frame, in the same storage.
 *
 * <p>Players cross on their own client, which keeps the cooldown itself and tells the server
 * ({@link FaceTransferPayload}); the server checks the claim and applies the same element without a teleport.
 *
 * <p>Inactive until phase 5 ({@link FaceTransfer#destination} says nothing crosses). Phase 5 also decides where a
 * player the server finds past the seam (pushed there server-side) is moved by the server.
 */
public final class FaceTransfers {

    /** How far a claimed crossing may be from where the server last saw the player. */
    private static final double CLAIM_SLACK = 8.0;

    /** Memories holding positions in the old frame, which may be wrong after a transfer. */
    private static final List<MemoryModuleType<?>> POSITIONAL_MEMORIES = List.of(
        MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.PATH, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT,
        MemoryModuleType.SECONDARY_JOB_SITE, MemoryModuleType.HIDING_PLACE, MemoryModuleType.NEAREST_BED);

    private FaceTransfers() {
    }

    /** After a root entity ticks: move it (and its passengers) if it has crossed into another frame. */
    public static void afterTick(ServerLevel level, Entity root) {
        if (root.isRemoved() || root.isPassenger() || FaceTransfer.coolingDown(root) || FaceTransfer.carried(root)) return;
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        // Players cross on their own clients.
        if (root.getSelfAndPassengers().anyMatch(entity -> entity instanceof ServerPlayer)) return;
        Motion g = FaceTransfer.destination(geometry, root.getX(), root.getY(), root.getZ());
        if (g == null || !FaceTransfer.roomToCross(root, g)) return;
        transfer(level, root, g, root.position(), null);
    }

    /**
     * Moves an entity and its passengers by {@code g} where they are now, as if they had crossed themselves, with what
     * carries them. A player moves without a teleport: its client moves it along with its carrier.
     */
    public static void carry(ServerLevel level, Entity root, Motion g) {
        float[] look = root instanceof ServerPlayer player ? new float[] {player.getYRot(), player.getXRot()} : null;
        transfer(level, root, g, root.position(), look);
    }

    /**
     * Moves {@code root} and its passengers by {@code g}. {@code rootPos} is where the root is taken to be (a client's
     * claim, or its server position). {@code look} is the claiming player's yaw and pitch when its own client crossed
     * first; without it, players are teleported.
     */
    static void transfer(ServerLevel level, Entity root, Motion g, Vec3 rootPos, float[] look) {
        Transform transform = Transform.of(g);
        for (Entity entity : root.getSelfAndPassengers().toList()) {
            FaceTransfer.startCooldown(entity);
            Vec3 p = transform.position(entity == root ? rootPos : entity.position());
            Vec3 v = transform.vector(entity.getDeltaMovement());
            float[] rotation = entity instanceof ServerPlayer && look != null
                ? FaceTransfer.rotateLook(g, look[0], look[1])
                : FaceTransfer.rotateLook(g, entity.getYRot(), entity.getXRot());
            if (entity instanceof ServerPlayer player) {
                if (look != null) {
                    // The client has already crossed: follow it without a teleport.
                    player.moveTo(p.x, p.y, p.z, rotation[0], rotation[1]);
                    player.setYHeadRot(rotation[0]);
                    player.connection.resetPosition();
                } else {
                    player.connection.teleport(p.x, p.y, p.z, rotation[0], rotation[1]);
                }
                player.setDeltaMovement(v);
                level.getChunkSource().move(player);
            } else {
                entity.moveTo(p.x, p.y, p.z, rotation[0], rotation[1]);
                entity.setYHeadRot(rotation[0]);
                if (entity instanceof LivingEntity living) living.yBodyRot = rotation[0];
                entity.setDeltaMovement(v);
                entity.hasImpulse = true;
                forgetPositions(entity);
            }
        }
    }

    /** Positions an entity remembers are in its old frame: drop them rather than path to the wrong place. */
    private static void forgetPositions(Entity entity) {
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.clearRestriction();
            mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
        }
        if (entity instanceof LivingEntity living) {
            Brain<?> brain = living.getBrain();
            for (MemoryModuleType<?> memory : POSITIONAL_MEMORIES) brain.eraseMemory(memory);
        }
    }

    /**
     * A client says its player crossed. Accepted if it matches the server's view: the player (or the vehicle it
     * drives) is near the claimed position, and that position crosses by the claimed element. Otherwise the player is
     * put back where the server has it.
     */
    public static void handleClaim(ServerPlayer player, FaceTransferPayload claim) {
        ServerLevel level = player.serverLevel();
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return;
        Entity root = player.getRootVehicle();
        if (root != player && root.getControllingPassenger() != player) {
            reject(player, "it does not drive its vehicle");
            return;
        }
        Vec3 claimed = new Vec3(claim.x(), claim.y(), claim.z());
        if (claimed.distanceToSqr(root.position()) > CLAIM_SLACK * CLAIM_SLACK) {
            reject(player, "it is " + Math.sqrt(claimed.distanceToSqr(root.position())) + " blocks from where it claims");
            return;
        }
        Motion g = FaceTransfer.destination(geometry, claimed.x, claimed.y, claimed.z);
        if (g == null || !g.equals(claim.motion())) {
            reject(player, "its position does not cross by " + claim.motion());
            return;
        }
        transfer(level, root, g, claimed, new float[] {claim.yRot(), claim.xRot()});
    }

    private static void reject(ServerPlayer player, String why) {
        AlphaOmegaMod.LOGGER.warn("Rejected transfer from {}: {}", player.getGameProfile().getName(), why);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
