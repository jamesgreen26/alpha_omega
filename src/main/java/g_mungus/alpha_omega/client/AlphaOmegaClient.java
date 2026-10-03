package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.network.PacketNormalization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = AlphaOmegaMod.MOD_ID, dist = Dist.CLIENT)
public class AlphaOmegaClient {

    public AlphaOmegaClient() {
        PacketNormalization.setClientNormalizer(ClientboundNormalizer::normalize);
    }
}
