package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.network.FaceTransferPayload;
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
 * Server side of crossing an edge (design §5). Every root entity is checked after it ticks; one that has crossed
 * moves, with its passengers, to the same cube point in the new face's storage.
 *
 * <p>Players cross on their own client, which keeps the cooldown itself and tells the server
 * ({@link FaceTransferPayload}); the server checks the claim and applies the same transform without a teleport. Only
 * a player the server finds well past the diagonal (pushed there server-side, or a client that did not cross) is
 * moved by the server, with a vanilla teleport.
 */
public final class FaceTransfers {

    /** How far past the diagonal the server lets a player go before moving it itself. */
    public static final double FORCE_DEPTH = 3.0;
    /** How far a claimed crossing may be from where the server last saw the player. */
    private static final double CLAIM_SLACK = 8.0;

    /** Memories holding positions in the old face's storage, meaningless after a transfer. */
    private static final List<MemoryModuleType<?>> POSITIONAL_MEMORIES = List.of(
        MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.PATH, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT,
        MemoryModuleType.SECONDARY_JOB_SITE, MemoryModuleType.HIDING_PLACE, MemoryModuleType.NEAREST_BED);

    private FaceTransfers() {
    }

    /** After a root entity ticks: move it (and its passengers) if it has crossed into another face. */
    public static void afterTick(ServerLevel level, Entity root) {
        if (root.isRemoved() || root.isPassenger() || FaceTransfer.coolingDown(root) || FaceTransfer.carried(root)) return;
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return;
        CubeFace face = geometry.faceAt(root.getX(), root.getZ());
        if (face == null) return;
        CubeFace to = FaceTransfer.destination(geometry, face, root.getX(), root.getY(), root.getZ());
        if (to == null) return;
        boolean carriesPlayer = root.getSelfAndPassengers().anyMatch(entity -> entity instanceof ServerPlayer);
        if (carriesPlayer && to.isNeighbour(face) && geometry.depthInto(face, to, root.getX(), root.getY(), root.getZ()) < FORCE_DEPTH) return;
        if (!FaceTransfer.roomToCross(geometry, root, face, to)) return;
        transfer(level, geometry, root, face, to, root.position(), null);
    }

    /**
     * Moves an entity and its passengers to another face where they are now, as if they had crossed themselves, with
     * what carries them. A player moves without a teleport: its client moves it along with its carrier.
     */
    public static void carry(ServerLevel level, CubeGeometry geometry, Entity root, CubeFace from, CubeFace to) {
        float[] look = root instanceof ServerPlayer player ? new float[] {player.getYRot(), player.getXRot()} : null;
        transfer(level, geometry, root, from, to, root.position(), look);
    }

    /**
     * Moves {@code root} and its passengers from one face to another. {@code rootPos} is where the root is taken to
     * be (a client's claim, or its server position). {@code look} is the claiming player's yaw and pitch when its own
     * client crossed first; without it, players are teleported.
     */
    static void transfer(ServerLevel level, CubeGeometry geometry, Entity root, CubeFace from, CubeFace to, Vec3 rootPos, float[] look) {
        for (Entity entity : root.getSelfAndPassengers().toList()) {
            FaceTransfer.startCooldown(entity);
            Vec3 pos = entity == root ? rootPos : entity.position();
            double[] p = geometry.transform(from, to, pos.x, pos.y, pos.z);
            FaceTransfer.Mode mode = FaceTransfer.mode(entity);
            Vec3 motion = entity.getDeltaMovement();
            double[] v = FaceTransfer.rotate(FaceTransfer.velocityMode(entity), from, to, motion.x, motion.y, motion.z);
            float[] rotation = entity instanceof ServerPlayer && look != null
                ? FaceTransfer.rotateLook(mode, from, to, look[0], look[1])
                : FaceTransfer.rotateLook(mode, from, to, entity.getYRot(), entity.getXRot());
            if (entity instanceof ServerPlayer player) {
                if (look != null) {
                    // The client has already crossed: follow it without a teleport.
                    player.moveTo(p[0], p[1], p[2], rotation[0], rotation[1]);
                    player.setYHeadRot(rotation[0]);
                    player.connection.resetPosition();
                } else {
                    player.connection.teleport(p[0], p[1], p[2], rotation[0], rotation[1]);
                }
                player.setDeltaMovement(v[0], v[1], v[2]);
                level.getChunkSource().move(player);
            } else {
                entity.moveTo(p[0], p[1], p[2], rotation[0], rotation[1]);
                entity.setYHeadRot(rotation[0]);
                if (entity instanceof LivingEntity living) living.yBodyRot = rotation[0];
                entity.setDeltaMovement(v[0], v[1], v[2]);
                entity.hasImpulse = true;
                forgetPositions(entity);
            }
        }
    }

    /** Positions an entity remembers are in the old face's storage: drop them rather than path thousands of blocks. */
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
     * drives) is on the claimed face, near the claimed position, and that position has crossed into the claimed face.
     * Otherwise the player is put back where the server has it.
     */
    public static void handleClaim(ServerPlayer player, FaceTransferPayload claim) {
        ServerLevel level = player.serverLevel();
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return;
        Entity root = player.getRootVehicle();
        if (root != player && root.getControllingPassenger() != player) {
            reject(player, "it does not drive its vehicle");
            return;
        }
        CubeFace from = geometry.faceAt(root.getX(), root.getZ());
        Vec3 claimed = new Vec3(claim.x(), claim.y(), claim.z());
        if (from == null || from.slot() != claim.from()) {
            reject(player, "it is not on face " + claim.from());
            return;
        }
        if (claimed.distanceToSqr(root.position()) > CLAIM_SLACK * CLAIM_SLACK) {
            reject(player, "it is " + Math.sqrt(claimed.distanceToSqr(root.position())) + " blocks from where it claims");
            return;
        }
        CubeFace to = FaceTransfer.destination(geometry, from, claimed.x, claimed.y, claimed.z);
        if (to == null || to.slot() != claim.to()) {
            reject(player, "its position does not cross into face " + claim.to());
            return;
        }
        transfer(level, geometry, root, from, to, claimed, new float[] {claim.yRot(), claim.xRot()});
    }

    private static void reject(ServerPlayer player, String why) {
        AlphaOmegaMod.LOGGER.warn("Rejected face transfer from {}: {}", player.getGameProfile().getName(), why);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
