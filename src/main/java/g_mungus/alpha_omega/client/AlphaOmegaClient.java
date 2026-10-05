package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.client.sky.AtmosphereRenderer;
import g_mungus.alpha_omega.client.sky.SkyClientConfig;
import g_mungus.alpha_omega.client.sky.SkyState;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.MixinEnvironment;

@Mod(value = AlphaOmegaMod.MOD_ID, dist = Dist.CLIENT)
public class AlphaOmegaClient {

    private static boolean audited;

    public AlphaOmegaClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SkyClientConfig.SPEC, SkyClientConfig.FILE_NAME);
        SkyState.prepare();
        AtmosphereRenderer.register(modBus);
        modBus.addListener((RegisterPresetEditorsEvent event) -> event.register(CubeChunkGenerator.PRESET, CubePresetScreen::create));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> Cube.setClient(null));
        if (Boolean.getBoolean("alpha_omega.auditMixins")) {
            // Debug aid: load every client mixin target once the title screen is up, then optionally quit.
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
                if (!(event.getNewScreen() instanceof TitleScreen) || audited) return;
                audited = true;
                AlphaOmegaMod.LOGGER.info("Auditing mixins (client)");
                MixinEnvironment.getCurrentEnvironment().audit();
                AlphaOmegaMod.LOGGER.info("Mixin audit complete (client)");
                if (Boolean.getBoolean("alpha_omega.quitAfterAudit")) Minecraft.getInstance().stop();
            });
        }
    }
}
