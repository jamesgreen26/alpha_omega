package g_mungus.alpha_omega.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * For {@code alpha_omega.polish.mixins.json}: a mixin under {@code mixin.polish.compat.<modid>} applies only when that
 * mod is installed, and vanilla's cloud mixin only when Sodium (which draws its own clouds) is not.
 */
public final class PolishMixinPlugin implements IMixinConfigPlugin {

    private static final String COMPAT = "g_mungus.alpha_omega.mixin.polish.compat.";
    private static final String VANILLA_CLOUDS = "g_mungus.alpha_omega.mixin.polish.client.LevelRendererCloudMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(VANILLA_CLOUDS)) return !CompatMixinPlugin.isLoaded("sodium");
        if (!mixinClassName.startsWith(COMPAT)) return true;
        String rest = mixinClassName.substring(COMPAT.length());
        int dot = rest.indexOf('.');
        return dot > 0 && CompatMixinPlugin.isLoaded(rest.substring(0, dot));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
