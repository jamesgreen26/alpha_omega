package g_mungus.alpha_omega.mixin.server.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.island.EntityFrames;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server entity manager: canonical section keys and chunk visibility lookups. */
@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin {

    @Shadow
    @Final
    EntitySectionStorage<?> sectionStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$init(CallbackInfo ci) {
        ((WrapFlag) this.sectionStorage).alpha_omega$setWrapped(true);
    }

    /**
     * Every way an entity enters the level (spawn, load from disk, join, respawn, dimension change) ends here:
     * bring it into the frame of its chunk's island first (§6.2).
     */
    @Inject(method = "addEntityWithoutEvent", at = @At("HEAD"))
    private void alpha_omega$enterFrame(EntityAccess entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Entity real) EntityFrames.reframe(real);
    }

    @ModifyExpressionValue(method = "addEntityWithoutEvent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long alpha_omega$canonSection(long sectionKey) {
        return Wrap.canonSectionKey(sectionKey);
    }

    @ModifyVariable(method = "canPositionTick(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonBlock(BlockPos pos) {
        return Wrap.canon(pos);
    }

    @ModifyVariable(method = "canPositionTick(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonChunk(ChunkPos pos) {
        return Wrap.canon(pos);
    }

    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonChunkKey(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }
}
