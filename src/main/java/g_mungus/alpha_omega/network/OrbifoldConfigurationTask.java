package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Orbifold;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Tells a connecting client the orbifold's shape before it receives any chunk. */
public record OrbifoldConfigurationTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {

    public static final Type TYPE = new Type(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "orbifold"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        sender.accept(OrbifoldPayload.of(server));
        this.listener.finishCurrentTask(TYPE);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
