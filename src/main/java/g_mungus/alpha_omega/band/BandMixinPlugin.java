package g_mungus.alpha_omega.band;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Leaves the reaction detectors (mixins named {@code *DetectorMixin}) out unless {@code -Dalpha_omega.band.detectors=true}
 * (on in gametests), so production pays nothing for them, and the {@code -PcheckCopies} gametest hook out unless asked.
 */
public final class BandMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("DetectorMixin")) return Boolean.getBoolean("alpha_omega.band.detectors");
        if (mixinClassName.endsWith("GameTestInfoBandMixin")) return Boolean.getBoolean("alpha_omega.band.checkCopies");
        return true;
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
