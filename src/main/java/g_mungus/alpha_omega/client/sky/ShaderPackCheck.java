package g_mungus.alpha_omega.client.sky;

import g_mungus.alpha_omega.AlphaOmegaMod;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** Whether an Iris shader pack is drawing the world (and with it the sky), asked through Iris's API by reflection. */
public final class ShaderPackCheck {

    private static boolean initialised;
    private static Object irisApi;
    private static Method shaderPackInUse;

    private ShaderPackCheck() {
    }

    public static boolean inUse() {
        if (!initialised) {
            initialised = true;
            if (ModList.get().isLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    shaderPackInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    AlphaOmegaMod.LOGGER.warn("Could not ask Iris whether a shader pack is in use", e);
                }
            }
        }
        if (shaderPackInUse == null) return false;
        try {
            return (Boolean) shaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            shaderPackInUse = null;
            return false;
        }
    }
}
