package g_mungus.alpha_omega;

import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.gametest.GameTestFilter;
import g_mungus.alpha_omega.gametest.ModLoadGameTests;
import g_mungus.alpha_omega.gametest.SableGameTests;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.transfer.FaceTransfers;
import java.lang.reflect.Field;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) NeighbourViews.tick(level);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NeighbourViews.clear());
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) g_mungus.alpha_omega.compat.sable.SableCompat.init();
        if (Boolean.getBoolean("alpha_omega.auditMixins")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> auditMixins());
        }
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

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FaceTransferPayload.TYPE, FaceTransferPayload.STREAM_CODEC,
            (payload, context) -> FaceTransfers.handleClaim((ServerPlayer) context.player(), payload));
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        GameTestFilter.register(java.util.List.of(ModLoadGameTests.class, SableGameTests.class), event::register);
    }

}
