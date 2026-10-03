package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.network.PacketNormalization;
import g_mungus.alpha_omega.wrap.Wraps;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AlphaOmegaMod.MOD_ID, dist = Dist.CLIENT)
public class AlphaOmegaClient {

    public AlphaOmegaClient() {
        PacketNormalization.setClientNormalizer(ClientboundNormalizer::normalize);
        // A remote server's wrapping only applies while connected; an integrated server resets its own on stop.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (!Minecraft.getInstance().isLocalServer()) Wraps.reset();
        });
    }
}
