package g_mungus.alpha_omega;

import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.gametest.CutGameTests;
import g_mungus.alpha_omega.gametest.FrameGameTests;
import g_mungus.alpha_omega.gametest.IslandGameTests;
import g_mungus.alpha_omega.gametest.ModLoadGameTests;
import g_mungus.alpha_omega.gametest.SeamGameTests;
import g_mungus.alpha_omega.gametest.WorldgenGameTests;
import g_mungus.alpha_omega.island.BuiltinFrameTranslators;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

@Mod(AlphaOmegaMod.MOD_ID)
public class AlphaOmegaMod {

    public static final String MOD_ID = "alpha_omega";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlphaOmegaMod(IEventBus modEventBus) {
        modEventBus.addListener(AlphaOmegaMod::registerGameTests);
        BuiltinFrameTranslators.register();
        if (Boolean.getBoolean("alpha_omega.auditMixins")) {
            NeoForge.EVENT_BUS.addListener(AlphaOmegaMod::auditMixins);
        }
    }

    /**
     * Debug aid for production installs, where dev-only assumptions surface: force-load every mixin target so a
     * failing injection shows up at startup rather than whenever its class first loads in play.
     */
    private static void auditMixins(ServerStartedEvent event) {
        LOGGER.info("Auditing mixins");
        MixinEnvironment.getCurrentEnvironment().audit();
        LOGGER.info("Mixin audit complete");
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ModLoadGameTests.class);
        event.register(SeamGameTests.class);
        event.register(WorldgenGameTests.class);
        event.register(FrameGameTests.class);
        event.register(IslandGameTests.class);
        event.register(CutGameTests.class);
    }
}
