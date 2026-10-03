package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.spongepowered.asm.mixin.MixinEnvironment;

@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModLoadGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(AlphaOmegaMod.MOD_ID), "alpha_omega is not in the mod list");
        helper.succeed();
    }

    /** Loads every mixin target, so a stale injection point fails here instead of when the class first loads in play. */
    @GameTest(template = TEMPLATE)
    public static void mixinsApply(GameTestHelper helper) {
        MixinEnvironment.getCurrentEnvironment().audit();
        helper.succeed();
    }
}
