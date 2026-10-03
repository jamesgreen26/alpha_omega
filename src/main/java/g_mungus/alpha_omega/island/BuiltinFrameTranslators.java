package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.mixin.server.entity.BeeAccessor;
import g_mungus.alpha_omega.mixin.server.entity.BrainAccessor;
import g_mungus.alpha_omega.mixin.server.entity.EyeOfEnderAccessor;
import g_mungus.alpha_omega.mixin.server.entity.PhantomAccessor;
import g_mungus.alpha_omega.mixin.server.entity.TurtleAccessor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Frame translation for vanilla state held in absolute coordinates (design doc §6.4). */
public final class BuiltinFrameTranslators {

    private BuiltinFrameTranslators() {
    }

    public static void register() {
        // Running goals hold their own targets; stopping them is cheaper and more robust than translating them.
        FrameTranslators.register(Mob.class, (mob, dx, dz) -> {
            mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
            mob.targetSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
        });
        FrameTranslators.register(PathfinderMob.class, (mob, dx, dz) -> {
            if (mob.hasRestriction()) mob.restrictTo(offset(mob.getRestrictCenter(), dx, dz), (int) mob.getRestrictRadius());
        });
        FrameTranslators.register(LivingEntity.class, (entity, dx, dz) -> translateMemories(entity, dx, dz));
        FrameTranslators.register(Bee.class, (bee, dx, dz) -> {
            BeeAccessor accessor = (BeeAccessor) bee;
            accessor.alpha_omega$setHivePos(offset(accessor.alpha_omega$getHivePos(), dx, dz));
            accessor.alpha_omega$setSavedFlowerPos(offset(accessor.alpha_omega$getSavedFlowerPos(), dx, dz));
        });
        FrameTranslators.register(Turtle.class, (turtle, dx, dz) -> {
            TurtleAccessor accessor = (TurtleAccessor) turtle;
            accessor.alpha_omega$setHomePos(offset(accessor.alpha_omega$getHomePos(), dx, dz));
            accessor.alpha_omega$setTravelPos(offset(accessor.alpha_omega$getTravelPos(), dx, dz));
        });
        FrameTranslators.register(Vex.class, (vex, dx, dz) -> vex.setBoundOrigin(offset(vex.getBoundOrigin(), dx, dz)));
        FrameTranslators.register(Phantom.class, (phantom, dx, dz) -> {
            PhantomAccessor accessor = (PhantomAccessor) phantom;
            accessor.alpha_omega$setAnchorPoint(offset(accessor.alpha_omega$getAnchorPoint(), dx, dz));
            accessor.alpha_omega$setMoveTargetPoint(accessor.alpha_omega$getMoveTargetPoint().add(dx, 0, dz));
        });
        FrameTranslators.register(Dolphin.class, (dolphin, dx, dz) -> dolphin.setTreasurePos(offset(dolphin.getTreasurePos(), dx, dz)));
        FrameTranslators.register(EyeOfEnder.class, (eye, dx, dz) -> {
            EyeOfEnderAccessor accessor = (EyeOfEnderAccessor) eye;
            accessor.alpha_omega$setTx(accessor.alpha_omega$getTx() + dx);
            accessor.alpha_omega$setTz(accessor.alpha_omega$getTz() + dz);
        });
        FrameTranslators.register(FallingBlockEntity.class, (block, dx, dz) -> block.setStartPos(offset(block.getStartPos(), dx, dz)));
        // The player's chunk view moves with it, so the shift sends no chunk unloads or reloads.
        FrameTranslators.register(ServerPlayer.class, (player, dx, dz) -> {
            int sx = (int) dx >> 4;
            int sz = (int) dz >> 4;
            SectionPos last = player.getLastSectionPos();
            player.setLastSectionPos(SectionPos.of(last.x() + sx, last.y(), last.z() + sz));
            if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view) {
                player.setChunkTrackingView(ChunkTrackingView.of(new ChunkPos(view.center().x + sx, view.center().z + sz), view.viewDistance()));
            }
        });
    }

    private static BlockPos offset(BlockPos pos, double dx, double dz) {
        return pos == null ? null : pos.offset((int) dx, 0, (int) dz);
    }

    /** Brain memories, translated by value type; paths are dropped and regenerate. */
    private static void translateMemories(LivingEntity entity, double dx, double dz) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = ((BrainAccessor) entity.getBrain()).alpha_omega$getMemories();
        for (Map.Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : memories.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            ExpirableValue<?> memory = entry.getValue().get();
            Object value = memory.getValue();
            if (value instanceof Path) {
                entry.setValue(Optional.empty());
                continue;
            }
            Object translated = translateValue(value, entity, dx, dz);
            if (translated != value) entry.setValue(Optional.of(ExpirableValue.of(translated, memory.getTimeToLive())));
        }
    }

    private static Object translateValue(Object value, LivingEntity entity, double dx, double dz) {
        if (value instanceof BlockPos pos) return offset(pos, dx, dz);
        if (value instanceof Vec3 vec) return vec.add(dx, 0, dz);
        if (value instanceof GlobalPos global) {
            return global.dimension() == entity.level().dimension() ? GlobalPos.of(global.dimension(), offset(global.pos(), dx, dz)) : global;
        }
        if (value instanceof BlockPosTracker tracker) return new BlockPosTracker(offset(tracker.currentBlockPosition(), dx, dz));
        if (value instanceof WalkTarget walk && walk.getTarget() instanceof BlockPosTracker tracker) {
            return new WalkTarget(new BlockPosTracker(offset(tracker.currentBlockPosition(), dx, dz)), walk.getSpeedModifier(), walk.getCloseEnoughDist());
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof BlockPos || first instanceof GlobalPos || first instanceof Vec3) {
                return list.stream().map(element -> translateValue(element, entity, dx, dz)).toList();
            }
        }
        return value;
    }
}
