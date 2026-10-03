package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.wrap.Wraps;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

/** Configuration task that tells a connecting client how the world wraps. */
public record WrapSettingsTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {

    public static final Type TYPE = new Type(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "wrap_settings"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(new WrapSettingsPayload(Wraps.settings()));
        this.listener.finishCurrentTask(TYPE);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
