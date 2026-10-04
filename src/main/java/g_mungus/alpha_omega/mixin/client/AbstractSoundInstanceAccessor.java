package g_mungus.alpha_omega.mixin.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {

    @Accessor("x")
    void alpha_omega$setX(double x);

    @Accessor("z")
    void alpha_omega$setZ(double z);
}
