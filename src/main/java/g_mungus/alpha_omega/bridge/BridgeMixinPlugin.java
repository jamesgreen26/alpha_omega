package g_mungus.alpha_omega.bridge;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Dev: {@code -Dalpha_omega.bridges.off=true} (Gradle {@code -PnoBridges}) leaves every bridge mixin out, to see what
 * the bridges change (the bridge gametests should then fail). Accessors stay, so the mod's own code still links.
 */
public final class BridgeMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Boolean.getBoolean("alpha_omega.bridges.off")) return true;
        return mixinClassName.endsWith("Accessor") || mixinClassName.endsWith("Invoker");
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
