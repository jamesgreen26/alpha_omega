package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.mixin.entities.BrainAccessor;
import g_mungus.alpha_omega.mixin.entities.EyeOfEnderAccessor;
import g_mungus.alpha_omega.mixin.entities.PhantomAccessor;
import g_mungus.alpha_omega.mixin.entities.TurtleAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Frame translation for vanilla state held in absolute coordinates (RS §4.4), from main's
 * {@code BuiltinFrameTranslators}, moved by a rigid motion instead of a lap offset. Paths are dropped and recomputed;
 * targets (entities) are kept, so a chasing mob keeps chasing.
 */
public final class BuiltinFrameTranslators {

    private static boolean registered;

    private BuiltinFrameTranslators() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // Paths and running movement goals hold old-frame positions: stop them, and they start again from where the
        // mob is. Target goals hold entities, which are kept.
        FrameTranslators.register(Mob.class, (mob, move) -> {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
            mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
        });
        FrameTranslators.register(PathfinderMob.class, (mob, move) -> {
            if (!mob.hasRestriction()) return;
            BlockPos center = move.block(mob.getRestrictCenter());
            if (center != null) mob.restrictTo(center, (int) mob.getRestrictRadius());
            else mob.clearRestriction();
        });
        FrameTranslators.register(LivingEntity.class, BuiltinFrameTranslators::translateMemories);
        FrameTranslators.register(Bee.class, (bee, move) -> {
            bee.setHivePos(move.block(bee.getHivePos()));
            bee.setSavedFlowerPos(move.block(bee.getSavedFlowerPos()));
        });
        FrameTranslators.register(Turtle.class, (turtle, move) -> {
            TurtleAccessor accessor = (TurtleAccessor) turtle;
            BlockPos home = move.block(accessor.alpha_omega$getHomePos());
            // A turtle always has a home; one the move takes out of storage stays at its source.
            turtle.setHomePos(home != null ? home : source(move, accessor.alpha_omega$getHomePos()));
            BlockPos travel = move.block(accessor.alpha_omega$getTravelPos());
            accessor.alpha_omega$setTravelPos(travel != null ? travel : turtle.blockPosition());
        });
        FrameTranslators.register(Vex.class, (vex, move) -> vex.setBoundOrigin(move.block(vex.getBoundOrigin())));
        FrameTranslators.register(Phantom.class, (phantom, move) -> {
            PhantomAccessor accessor = (PhantomAccessor) phantom;
            BlockPos anchor = move.block(accessor.alpha_omega$getAnchorPoint());
            accessor.alpha_omega$setAnchorPoint(anchor != null ? anchor : phantom.blockPosition().above(5));
            Vec3 target = move.point(accessor.alpha_omega$getMoveTargetPoint());
            accessor.alpha_omega$setMoveTargetPoint(target != null ? target : phantom.position());
        });
        FrameTranslators.register(Dolphin.class, (dolphin, move) -> {
            BlockPos treasure = move.block(dolphin.getTreasurePos());
            dolphin.setTreasurePos(treasure != null ? treasure : BlockPos.ZERO);
        });
        FrameTranslators.register(EyeOfEnder.class, (eye, move) -> {
            EyeOfEnderAccessor accessor = (EyeOfEnderAccessor) eye;
            // Its target is a direction more than a place (a stronghold far off): moved without a validity check.
            Vec3 target = move.transform().position(new Vec3(accessor.alpha_omega$getTx(), 0.0, accessor.alpha_omega$getTz()));
            accessor.alpha_omega$setTx(target.x);
            accessor.alpha_omega$setTz(target.z);
        });
        FrameTranslators.register(FallingBlockEntity.class, (block, move) -> {
            BlockPos start = move.block(block.getStartPos());
            block.setStartPos(start != null ? start : block.blockPosition());
        });
    }

    /** A cell's source in the tile. */
    private static BlockPos source(FrameTranslators.Move move, BlockPos pos) {
        var cell = move.geometry().canon(pos.getX(), pos.getZ());
        return new BlockPos(cell.x(), pos.getY(), cell.z());
    }

    /** Brain memories, translated by value type; paths are dropped and recomputed, invalid positions cleared. */
    private static void translateMemories(LivingEntity entity, FrameTranslators.Move move) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = ((BrainAccessor) entity.getBrain()).alpha_omega$memories();
        for (Map.Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : memories.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            ExpirableValue<?> memory = entry.getValue().get();
            Object value = memory.getValue();
            Object translated = value instanceof Path ? null : translateValue(value, entity, move);
            if (translated == value) continue;
            if (translated == null) entry.setValue(Optional.empty());
            else entry.setValue(Optional.of(memory.canExpire() ? ExpirableValue.of(translated, memory.getTimeToLive()) : ExpirableValue.of(translated)));
        }
    }

    /** A memory's value moved, the same object if it holds no position, or null if it should be cleared. */
    private static Object translateValue(Object value, LivingEntity entity, FrameTranslators.Move move) {
        if (value instanceof BlockPos pos) return move.block(pos);
        if (value instanceof Vec3 vec) return move.point(vec);
        if (value instanceof GlobalPos global) return move.global(global, entity, true);
        if (value instanceof BlockPosTracker tracker) {
            Vec3 moved = move.point(tracker.currentPosition());
            return moved == null ? null : new BlockPosTracker(moved);
        }
        if (value instanceof WalkTarget walk) {
            if (!(walk.getTarget() instanceof BlockPosTracker tracker)) return value;
            Vec3 moved = move.point(tracker.currentPosition());
            return moved == null ? null : new WalkTarget(new BlockPosTracker(moved), walk.getSpeedModifier(), walk.getCloseEnoughDist());
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof BlockPos || first instanceof GlobalPos || first instanceof Vec3) {
                List<Object> moved = new ArrayList<>(list.size());
                for (Object element : list) {
                    Object translated = translateValue(element, entity, move);
                    if (translated != null) moved.add(translated);
                }
                return moved.isEmpty() ? null : List.copyOf(moved);
            }
        }
        return value;
    }
}
