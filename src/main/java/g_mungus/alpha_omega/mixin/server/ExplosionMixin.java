package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.frame.Frames;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R4: an explosion is block-side code starting to run, so its center is lifted into the frame of its chunk's island.
 * Block entities and other mods often explode at a canonical position; without this, entities in a lifted frame
 * are found by the query but are out of range of the center, so they take no damage and no knockback. Every
 * constructor ends in this one, so explosions built directly by mods are covered too. Client explosions are
 * rebuilt from packets that are already normalized.
 */
@Mixin(Explosion.class)
abstract class ExplosionMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    @Mutable
    private double x;

    @Shadow
    @Final
    @Mutable
    private double z;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Explosion$BlockInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)V",
        at = @At("RETURN"))
    private void alpha_omega$liftCenter(CallbackInfo ci) {
        if (!(this.level instanceof ServerLevel server)) return;
        long offset = Frames.lapOffset(server, SectionPos.blockToSectionCoord(Mth.floor(this.x)), SectionPos.blockToSectionCoord(Mth.floor(this.z)));
        this.x += Frames.offsetX(offset);
        this.z += Frames.offsetZ(offset);
    }
}
