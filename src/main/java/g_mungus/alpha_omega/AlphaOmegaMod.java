package g_mungus.alpha_omega;

import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.command.WrapCommand;
import g_mungus.alpha_omega.compat.sable.SableCompat;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.gametest.CutGameTests;
import g_mungus.alpha_omega.gametest.DimensionGameTests;
import g_mungus.alpha_omega.gametest.FrameGameTests;
import g_mungus.alpha_omega.gametest.IslandGameTests;
import g_mungus.alpha_omega.gametest.ModLoadGameTests;
import g_mungus.alpha_omega.gametest.PolishGameTests;
import g_mungus.alpha_omega.gametest.SableGameTests;
import g_mungus.alpha_omega.gametest.SeamGameTests;
import g_mungus.alpha_omega.gametest.WorldgenGameTests;
import g_mungus.alpha_omega.island.BuiltinFrameTranslators;
import g_mungus.alpha_omega.network.WrapSettingsPayload;
import g_mungus.alpha_omega.network.WrapSettingsTask;
import g_mungus.alpha_omega.wrap.WorldWrapStore;
import g_mungus.alpha_omega.wrap.Wraps;
import java.lang.reflect.Field;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.Config;

@Mod(AlphaOmegaMod.MOD_ID)
public class AlphaOmegaMod {

    public static final String MOD_ID = "alpha_omega";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlphaOmegaMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AlphaOmegaConfig.SPEC);
        modEventBus.addListener(AlphaOmegaMod::registerGameTests);
        modEventBus.addListener(AlphaOmegaMod::registerPayloads);
        modEventBus.addListener(AlphaOmegaMod::registerConfigurationTasks);
        BuiltinFrameTranslators.register();
        if (ModList.get().isLoaded("sable")) SableCompat.init();

        // A world's wrapping is decided before any of its levels is constructed, and forgotten when it stops.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> WorldWrapStore.load(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Wraps.reset());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> WrapCommand.register(event.getDispatcher()));
        if (Boolean.getBoolean("alpha_omega.auditMixins")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> auditMixins());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").configurationToClient(WrapSettingsPayload.TYPE, WrapSettingsPayload.STREAM_CODEC,
            (payload, context) -> Wraps.configure(payload.settings()));
    }

    private static void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new WrapSettingsTask(event.getListener()));
    }

    /**
     * Debug aid for production installs, where dev-only assumptions surface: force-load every target of our mixins so
     * a failing injection shows up at startup rather than whenever its class first loads in play. Other mods' targets
     * are left alone, since some of them cannot load on a dedicated server. Returns the number of failures.
     */
    @SuppressWarnings("unchecked")
    public static int auditMixins() {
        LOGGER.info("Auditing mixins");
        Map<String, Config> configs;
        try {
            // Mixins.getConfigs() only lists configs not yet selected, which by now is none of them.
            Field all = Config.class.getDeclaredField("allConfigs");
            all.setAccessible(true);
            configs = (Map<String, Config>) all.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Mixin audit could not list mixin configs", e);
            return 1;
        }
        int loaded = 0, failures = 0;
        for (Config config : configs.values()) {
            if (!config.getName().startsWith(MOD_ID)) continue;
            for (String target : config.getConfig().getTargets()) {
                try {
                    loaded++;
                    Class.forName(target.replace('/', '.'), false, AlphaOmegaMod.class.getClassLoader());
                } catch (Throwable t) {
                    failures++;
                    LOGGER.error("Mixin audit failed to load {}", target, t);
                }
            }
        }
        LOGGER.info("Mixin audit complete: {} targets, {} failures", loaded, failures);
        return failures;
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ModLoadGameTests.class);
        event.register(SeamGameTests.class);
        event.register(WorldgenGameTests.class);
        event.register(FrameGameTests.class);
        event.register(IslandGameTests.class);
        event.register(CutGameTests.class);
        event.register(DimensionGameTests.class);
        event.register(PolishGameTests.class);
        event.register(SableGameTests.class);
    }
}
