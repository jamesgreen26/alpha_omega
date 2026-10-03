package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.ViewCenter;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Periodic client chunk storage (design doc §9.2): any image of a chunk resolves to the one nearest the view
 * center. Every client-side block lookup routes through here. Unambiguous because the period exceeds the view.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin implements ViewCenter {

    @Shadow
    @Final
    ClientLevel level;

    @Unique
    private volatile int alpha_omega$viewCenterX;
    @Unique
    private volatile int alpha_omega$viewCenterZ;

    @Override
    public int alpha_omega$viewCenterX() {
        return this.alpha_omega$viewCenterX;
    }

    @Override
    public int alpha_omega$viewCenterZ() {
        return this.alpha_omega$viewCenterZ;
    }

    @Inject(method = "updateViewCenter", at = @At("HEAD"))
    private void alpha_omega$trackViewCenter(int x, int z, CallbackInfo ci) {
        this.alpha_omega$viewCenterX = x;
        this.alpha_omega$viewCenterZ = z;
    }

    @ModifyVariable(method = {
        "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        "replaceBiomes",
        "replaceWithPacketData",
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$nearestX(int x) {
        return Wrap.of(this.level).nearestChunk(x, this.alpha_omega$viewCenterX);
    }

    @ModifyVariable(method = {
        "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        "replaceBiomes",
        "replaceWithPacketData",
    }, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$nearestZ(int z) {
        return Wrap.of(this.level).nearestChunk(z, this.alpha_omega$viewCenterZ);
    }

    @ModifyVariable(method = "drop", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$nearestDrop(ChunkPos pos) {
        return Wrap.of(this.level).nearest(pos, this.alpha_omega$viewCenterX, this.alpha_omega$viewCenterZ);
    }
}
