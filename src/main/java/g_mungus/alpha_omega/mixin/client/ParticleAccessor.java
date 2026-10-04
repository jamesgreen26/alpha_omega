package g_mungus.alpha_omega.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("xo")
    double alpha_omega$getXo();

    @Accessor("xo")
    void alpha_omega$setXo(double xo);

    @Accessor("zo")
    double alpha_omega$getZo();

    @Accessor("zo")
    void alpha_omega$setZo(double zo);
}
