package g_mungus.alpha_omega;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import g_mungus.alpha_omega.command.OrbifoldCommand;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.gametest.GameTestFilter;
import g_mungus.alpha_omega.gametest.GeneratorGameTests;
import g_mungus.alpha_omega.gametest.ModLoadGameTests;
import g_mungus.alpha_omega.gametest.OrbifoldGameTests;
import g_mungus.alpha_omega.gametest.SableGameTests;
import g_mungus.alpha_omega.gametest.TerrainGameTests;
import g_mungus.alpha_omega.neighbour.ImageViews;
import g_mungus.alpha_omega.network.FrameTransferAckPayload;
import g_mungus.alpha_omega.network.FrameTransferPayload;
import g_mungus.alpha_omega.network.ServerFrameTransferPayload;
import g_mungus.alpha_omega.network.OrbifoldConfigurationTask;
import g_mungus.alpha_omega.network.OrbifoldPayload;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.transfer.BuiltinFrameTranslators;
import g_mungus.alpha_omega.transfer.FrameGroups;
import g_mungus.alpha_omega.transfer.FrameTransfers;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import java.lang.reflect.Field;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.Config;

@Mod(AlphaOmegaMod.MOD_ID)
public class AlphaOmegaMod {

    public static final String MOD_ID = "alpha_omega";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Applies a server-driven frame transfer on the client; set by the client entry point, so servers never load it. */
    public static java.util.function.Consumer<ServerFrameTransferPayload> clientFrameTransfer = payload -> {
    };

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    static {
        CHUNK_GENERATORS.register("orbifold", () -> OrbifoldChunkGenerator.CODEC);
    }

    public AlphaOmegaMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AlphaOmegaConfig.SPEC);
        CHUNK_GENERATORS.register(modEventBus);
        modEventBus.addListener(AlphaOmegaMod::registerGameTests);
        modEventBus.addListener(AlphaOmegaMod::registerPayloads);
        modEventBus.addListener((RegisterConfigurationTasksEvent event) -> event.register(new OrbifoldConfigurationTask(event.getListener())));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> OrbifoldCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                ImageViews.tick(level);
                FrameGroups.tick(level);
            }
        });
        BuiltinFrameTranslators.register();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player) FrameGroups.beforeUse(player, event.getPos());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ImageViews.clear());
        g_mungus.alpha_omega.band.BandEvents.register(modEventBus);
        g_mungus.alpha_omega.nether.NetherEvents.register();
        g_mungus.alpha_omega.gametest.TestMultiblock.register(modEventBus);
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
        event.registrar("1").configurationToClient(OrbifoldPayload.TYPE, OrbifoldPayload.STREAM_CODEC,
            (payload, context) -> Orbifold.setClient(payload.geometries()));
        event.registrar("1").playToServer(FrameTransferPayload.TYPE, FrameTransferPayload.STREAM_CODEC,
            (payload, context) -> FrameTransfers.handleClaim((ServerPlayer) context.player(), payload));
        event.registrar("1").playToServer(FrameTransferAckPayload.TYPE, FrameTransferAckPayload.STREAM_CODEC,
            (payload, context) -> FrameTransfers.handleAck((ServerPlayer) context.player(), payload.index()));
        event.registrar("1").playToClient(ServerFrameTransferPayload.TYPE, ServerFrameTransferPayload.STREAM_CODEC,
            (payload, context) -> clientFrameTransfer.accept(payload));
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        GameTestFilter.register(java.util.List.of(ModLoadGameTests.class, OrbifoldGameTests.class, GeneratorGameTests.class, TerrainGameTests.class, SableGameTests.class, g_mungus.alpha_omega.gametest.LocalTimeGameTests.class,
            g_mungus.alpha_omega.gametest.ImageGameTests.class, g_mungus.alpha_omega.gametest.BandGameTests.class,
            g_mungus.alpha_omega.gametest.TransferGameTests.class, g_mungus.alpha_omega.gametest.RetentionGameTests.class,
            g_mungus.alpha_omega.gametest.BridgeGameTests.class, g_mungus.alpha_omega.gametest.ClaimGameTests.class,
            g_mungus.alpha_omega.gametest.PolishGameTests.class, g_mungus.alpha_omega.gametest.NetherGameTests.class), event::register);
    }

}
