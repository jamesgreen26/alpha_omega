package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.c2me.C2meTestOps;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * C2ME compatibility. Each test passes trivially without C2ME; C2ME types stay behind {@link C2meTestOps} so this
 * class loads either way. Run with {@code -PwithC2me}. Periodic worldgen under C2ME's density function compiler is
 * covered by {@link WorldgenGameTests}.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class C2meGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static boolean c2me(GameTestHelper helper) {
        if (ModList.get().isLoaded("c2me")) return true;
        helper.succeed();
        return false;
    }

    /**
     * Chunks on both sides of the seam depend on each other through other images; C2ME's chunk system resolves
     * them to the canonical chunk instead of loading a second copy.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void chunkSystemHasOneHolderPerChunk(GameTestHelper helper) {
        if (!c2me(helper)) return;
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wraps.overworld();
        int cz = wrap.canonChunk(SectionPos.blockToSectionCoord(helper.absolutePos(BlockPos.ZERO).getZ()) + wrap.chunkPeriod / 4);
        int first = wrap.minChunk;
        int last = first + wrap.chunkPeriod - 1;
        level.setChunkForced(first, cz, true);
        level.setChunkForced(last, cz, true);

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getChunkSource().hasChunk(first, cz) && level.getChunkSource().hasChunk(last, cz), "seam chunks not loaded yet");
            int duplicates = C2meTestOps.nonCanonicalHolders(level);
            helper.assertTrue(duplicates == 0, duplicates + " chunk holders are keyed outside the canonical window");
            level.setChunkForced(first, cz, false);
            level.setChunkForced(last, cz, false);
        });
    }
}
