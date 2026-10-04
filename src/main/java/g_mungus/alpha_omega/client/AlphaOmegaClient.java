package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.client.sky.SkyClientConfig;
import g_mungus.alpha_omega.client.sky.SkyState;
import g_mungus.alpha_omega.network.PacketNormalization;
import g_mungus.alpha_omega.wrap.Wraps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.MixinEnvironment;

@Mod(value = AlphaOmegaMod.MOD_ID, dist = Dist.CLIENT)
public class AlphaOmegaClient {

    private static boolean audited;

    public AlphaOmegaClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SkyClientConfig.SPEC, SkyClientConfig.FILE_NAME);
        SkyState.prepare();
        PacketNormalization.setClientNormalizer(ClientboundNormalizer::normalize);
        // A remote server's wrapping only applies while connected; an integrated server resets its own on stop.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (!Minecraft.getInstance().isLocalServer()) Wraps.reset();
        });
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
