package g_mungus.alpha_omega.mixin.nether;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.nether.NetherView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A level's view distance never exceeds what its orbifold allows ({@code nether.NetherView}): only a small Nether is limited. */
@Mixin(ChunkMap.class)
abstract class ChunkMapViewMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Unique
    private int alpha_omega$maxView = 32;

    /** In the constructor the level has no chunk source yet: the limit comes from the generator it is built with. */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;setServerViewDistance(I)V"))
    private int alpha_omega$limitFromGenerator(int viewDistance, @Local(argsOnly = true) ChunkGenerator generator) {
        this.alpha_omega$maxView = NetherView.maxViewDistance(this.level.dimension(), generator);
        return viewDistance;
    }

    @ModifyVariable(method = "setServerViewDistance", at = @At("HEAD"), argsOnly = true)
    private int alpha_omega$limit(int viewDistance) {
        return Math.min(viewDistance, this.alpha_omega$maxView);
    }
}
