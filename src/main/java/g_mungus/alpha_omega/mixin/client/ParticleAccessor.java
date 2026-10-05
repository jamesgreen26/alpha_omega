package g_mungus.alpha_omega.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("x")
    double alpha_omega$x();

    @Accessor("y")
    double alpha_omega$y();

    @Accessor("z")
    double alpha_omega$z();

    @Accessor("xd")
    double alpha_omega$xd();

    @Accessor("yd")
    double alpha_omega$yd();

    @Accessor("zd")
    double alpha_omega$zd();

    @Accessor("xo")
    void alpha_omega$setXo(double x);

    @Accessor("yo")
    void alpha_omega$setYo(double y);

    @Accessor("zo")
    void alpha_omega$setZo(double z);

}
