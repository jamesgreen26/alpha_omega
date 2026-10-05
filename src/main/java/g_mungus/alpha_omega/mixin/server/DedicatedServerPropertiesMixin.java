package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import java.util.function.Function;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A dedicated server whose {@code server.properties} names no {@code level-type} makes a Cube World, and writes that
 * into the file; one that names a type keeps it.
 */
@Mixin(DedicatedServerProperties.class)
abstract class DedicatedServerPropertiesMixin {

    @ModifyArg(method = "<init>", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/dedicated/DedicatedServerProperties;get(Ljava/lang/String;Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object alpha_omega$cubeByDefault(String key, Function<String, ?> parse, Object fallback) {
        return "level-type".equals(key) ? CubeChunkGenerator.PRESET.location().toString() : fallback;
    }
}
