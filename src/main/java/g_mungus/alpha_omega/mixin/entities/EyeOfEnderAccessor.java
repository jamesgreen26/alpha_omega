package g_mungus.alpha_omega.mixin.entities;

import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where an eye of ender is flying. */
@Mixin(EyeOfEnder.class)
public interface EyeOfEnderAccessor {

    @Accessor("tx")
    double alpha_omega$getTx();

    @Accessor("tx")
    void alpha_omega$setTx(double x);

    @Accessor("tz")
    double alpha_omega$getTz();

    @Accessor("tz")
    void alpha_omega$setTz(double z);
}
