package g_mungus.alpha_omega.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips core mixins that cannot apply alongside another mod. Each one has a functional replacement in
 * {@code mixin.compat.<modid>}, applied by {@link CompatMixinPlugin}.
 */
public final class ReplacedMixinPlugin implements IMixinConfigPlugin {

    private static final String PACKAGE = "g_mungus.alpha_omega.mixin.";

    /** Core mixin (relative to {@code mixin.}) -> id of the mod whose presence replaces it. */
    private static final Map<String, String> REPLACED = Map.of(
        "server.PlayerListMixin", "sable"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(PACKAGE)) return true;
        String modId = REPLACED.get(mixinClassName.substring(PACKAGE.length()));
        return modId == null || !CompatMixinPlugin.isLoaded(modId);
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
