package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapContext;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Levels are constructed inside their dimension's {@link WrapContext}. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @WrapOperation(method = "createLevels", at = @At(value = "NEW", target = "net/minecraft/server/level/ServerLevel"))
    private ServerLevel alpha_omega$constructInContext(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage,
                                                       ServerLevelData data, ResourceKey<Level> dimension, LevelStem stem,
                                                       ChunkProgressListener progress, boolean debug, long seed,
                                                       List<CustomSpawner> spawners, boolean tickTime, RandomSequences sequences,
                                                       Operation<ServerLevel> original) {
        return WrapContext.with(Wrap.of(dimension),
            () -> original.call(server, executor, storage, data, dimension, stem, progress, debug, seed, spawners, tickTime, sequences));
    }
}
