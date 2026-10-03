package g_mungus.alpha_omega;

import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.gametest.ModLoadGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

@Mod(AlphaOmegaMod.MOD_ID)
public class AlphaOmegaMod {

    public static final String MOD_ID = "alpha_omega";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlphaOmegaMod(IEventBus modEventBus) {
        modEventBus.addListener(AlphaOmegaMod::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ModLoadGameTests.class);
    }
}
